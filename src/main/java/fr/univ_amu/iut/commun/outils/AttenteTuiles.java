package fr.univ_amu.iut.commun.outils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.stage.Window;

/// Attente des tuiles OpenStreetMap avant une capture d'écran comportant une carte.
///
/// Le besoin est né deux fois de façon indépendante, sur la carte des points d'écoute (#152) puis
/// sur la modale de saisie GPS (#153), et a produit deux fois le même code. La provenance est
/// conservée ici parce qu'elle dit ce que l'attente sert : n'importe quelle capture affichant un
/// fond cartographique, quel que soit l'écran.
///
/// Les tuiles se téléchargent en arrière-plan (réseau) puis se peignent sur le fil JavaFX. Sans
/// attente, la capture fige la carte avant l'arrivée du fond. On pompe donc les évènements FX par une
/// **boucle d'évènements imbriquée** ([Platform#enterNestedEventLoop]) : bloquer le fil JavaFX
/// empêcherait justement le peinturage qu'on attend.
///
/// ## Pourquoi une condition, et non plus un délai (#3068)
///
/// Cette classe attendait **six secondes fixes**, puis photographiait quoi qu'il arrive. C'était une
/// course contre le réseau, et le dépôt en payait le prix : quatre aperçus de carte changeaient d'un
/// build à l'autre **sans qu'aucun code ne change**, parce qu'un nombre différent de tuiles avait eu le
/// temps d'arriver.
///
/// Rien ne l'atténuait au fil des exécutions : Gluon Maps cache ses tuiles dans un répertoire
/// **temporaire propre à chaque JVM** (`/tmp/.gluonmapsNNNN`). Chaque run repart donc d'un cache vide
/// et rejoue la course en entier - ce qui explique aussi pourquoi le défaut est **intermittent** :
/// selon le réseau, la course se gagne ou se perd.
///
/// ⚠️ **Cela ne rend pas les captures identiques au bit près**, et c'est mesuré : après ce correctif,
/// les quatre aperçus de carte varient toujours d'un run à l'autre, sur **0,34 %** des pixels, en
/// suivant les tracés fins des routes. Porter la quiétude exigée de 0,75 s à 3 s n'y change rien : ce
/// sont les **tuiles elles-mêmes** qui diffèrent, et aucune attente ne corrigera cela.
///
/// **C'est assumé** (#3068). Le déterminisme est une règle sur ce que le **produit rend** ; les tuiles
/// sont une entrée **extérieure** au dépôt. Ces captures valent parce qu'elles montrent une *vraie*
/// carte - figer la source les rendrait plus stables et moins vraies. Détail dans `dev-docs/captures.md`.
///
/// Ce qui est corrigé ici est donc réel mais **distinct** de la variabilité : la course contre le réseau
/// se perdait au hasard, et une capture pouvait partir avec des tuiles **manquantes**, ce qui n'est pas
/// une nuance de rendu mais un fond absent. Gain annexe net : 8 captures en **10 s** au lieu de 48.
///
/// L'attente observe désormais l'**état du graphe de scène** : elle rend la main quand plus aucune
/// image ne se charge et que leur nombre a cessé de bouger. Le délai n'est plus qu'un **plafond**.
///
/// **Best-effort, comme avant** : hors-ligne, le plafond s'écoule et la capture reste lisible (carrés,
/// points ou marqueurs sur fond clair), seul le fond photographique manque. Aucun échec n'est levé.
///
/// À passer comme `preparation` de [ApercuFx#capturerApresPreparation] : cette méthode montre le
/// Stage **avant** d'exécuter l'attente, ce qui rend la boucle imbriquée sans danger. Appelée hors
/// de ce cadre, elle laisserait le toolkit dans un état où un `new Stage()` ultérieur échoue sous la
/// Headless Platform de JavaFX 26 (même défaut que celui documenté sur [AttenteAudio]).
public final class AttenteTuiles {

    /// **Plafond**, en millisecondes : au-delà, on photographie ce qu'on a. C'est le cas hors-ligne, où
    /// aucune tuile n'arrivera jamais et où attendre davantage ne rendrait pas la capture meilleure.
    private static final long PLAFOND_MS = 20_000;

    /// Intervalle entre deux observations du graphe de scène.
    private static final long PAS_MS = 250;

    /// Nombre d'observations **consécutives** identiques exigées avant de conclure. Une seule ne
    /// suffirait pas : entre deux tuiles, le graphe est momentanément au repos alors que la suivante
    /// est déjà demandée.
    private static final int OBSERVATIONS_STABLES = 3;

    private AttenteTuiles() {}

    /// Attend que les tuiles soient arrivées, ou que le plafond soit atteint, en laissant tourner le
    /// fil JavaFX. À appeler **sur le thread JavaFX**.
    ///
    /// ## Deux contraintes qui se contredisent, et comment elles se concilient
    ///
    /// Le **rythme** ne peut pas venir du fil JavaFX : celui-ci est bloqué dans la boucle imbriquée, et
    /// sous la *Headless Platform* aucune pulsation d'animation ne le réveille. Une [javafx.animation.Timeline]
    /// n'y tique **jamais** : essayée, elle a produit un interblocage franc, la capture ne rendant plus
    /// jamais la main.
    ///
    /// La **mesure**, elle, ne peut pas venir d'un fil de veille : le graphe de scène n'est pas
    /// thread-safe, et le lire depuis l'extérieur reviendrait à échantillonner une structure que le fil
    /// JavaFX est en train de modifier.
    ///
    /// D'où ce partage : un fil de veille **compte le temps**, et fait exécuter chaque observation
    /// **sur le fil JavaFX** par `runLater` - que la boucle imbriquée dépile, c'est justement son rôle.
    public static void attendre() {
        Object cle = new Object();
        Thread veilleur = Thread.ofVirtual().unstarted(() -> {
            long debut = System.nanoTime();
            int stables = 0;
            int precedent = -1;
            boolean filMuet = false;
            while (!filMuet
                    && stables < OBSERVATIONS_STABLES
                    && (System.nanoTime() - debut) / 1_000_000 < PLAFOND_MS
                    && dormir()) {
                int[] mesure = mesurerSurLeFilFx();
                filMuet = mesure.length == 0;
                if (!filMuet) {
                    stables = mesure[1] == 0 && mesure[0] == precedent ? stables + 1 : 0;
                    precedent = mesure[0];
                }
            }
            // Sortie unique, quelle que soit la raison : stabilisation, plafond, ou fil muet. Hors-ligne,
            // c'est le plafond qui parle, et la capture reste lisible - contrat best-effort inchangé.
            Platform.runLater(() -> Platform.exitNestedEventLoop(cle, null));
        });
        veilleur.start();
        Platform.enterNestedEventLoop(cle);
    }

    /// Dort un pas d'observation. Rend `false` si l'attente a été interrompue, ce qui arrête la boucle
    /// sans avoir besoin d'un `break` de plus.
    private static boolean dormir() {
        try {
            Thread.sleep(PAS_MS);
            return true;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /// Fait exécuter [#mesurer()] **sur le fil JavaFX** et rend son résultat, ou un tableau **vide** si ce
    /// fil n'a pas répondu dans le temps imparti - un vide qui se lit « je n'ai pas pu mesurer », et non
    /// « il n'y a rien ». La distinction compte : conclure « zéro image » ferait croire à la stabilité.
    private static int[] mesurerSurLeFilFx() {
        FutureTask<int[]> mesure = new FutureTask<>(AttenteTuiles::mesurer);
        Platform.runLater(mesure);
        try {
            return mesure.get(PAS_MS * 4, TimeUnit.MILLISECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return new int[0];
        } catch (ExecutionException | TimeoutException _) {
            return new int[0];
        }
    }

    /// `[nombre d'images, nombre encore en cours de chargement]` dans la fenêtre affichée.
    ///
    /// Les deux comptes sont nécessaires et disent des choses différentes : le **nombre** croît tant que
    /// des tuiles arrivent (la vue n'est ajoutée qu'à la réception), et le **chargement en cours**
    /// signale une image déjà posée mais pas encore peinte. Ne surveiller que l'un des deux laisserait
    /// passer la moitié des cas.
    private static int[] mesurer() {
        Window fenetre =
                Window.getWindows().isEmpty() ? null : Window.getWindows().get(0);
        Scene scene = fenetre == null ? null : fenetre.getScene();
        if (scene == null) {
            return new int[] {0, 0};
        }
        int total = 0;
        int enCours = 0;
        Deque<Node> pile = new ArrayDeque<>();
        pile.add(scene.getRoot());
        while (!pile.isEmpty()) {
            Node noeud = pile.pop();
            if (noeud instanceof ImageView vue && vue.getImage() != null) {
                total++;
                if (vue.getImage().getProgress() < 1.0 && !vue.getImage().isError()) {
                    enCours++;
                }
            }
            if (noeud instanceof Parent parent) {
                pile.addAll(parent.getChildrenUnmodifiable());
            }
        }
        return new int[] {total, enCours};
    }
}
