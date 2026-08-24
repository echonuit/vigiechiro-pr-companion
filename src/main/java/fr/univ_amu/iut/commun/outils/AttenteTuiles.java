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

/// Attente des tuiles OpenStreetMap avant une capture comportant une carte, née deux fois : carte
/// des points d'écoute (#152), modale GPS (#153).
///
/// Les tuiles arrivent en arrière-plan et se peignent sur le fil JavaFX ; sans attente la capture
/// fige la carte avant le fond. D'où une **boucle imbriquée** ([Platform#enterNestedEventLoop]) qui
/// observe l'**état du graphe** plutôt qu'un délai fixe (#3068), le délai n'étant plus qu'un
/// plafond ; la variabilité résiduelle des tuiles est assumée (`dev-docs/captures.md`). À passer
/// comme `preparation` de [ApercuFx#capturerApresPreparation] : hors de ce cadre, un `new Stage()`
/// ultérieur échoue sous la Headless Platform.
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
    /// Le **rythme** ne peut pas venir du fil JavaFX : il est bloqué dans la boucle imbriquée, et sous
    /// la *Headless Platform* aucune pulsation ne le réveille - une [javafx.animation.Timeline] n'y
    /// tique jamais, et l'essai a produit un interblocage franc. La **mesure**, elle, ne peut pas
    /// venir d'un fil de veille : le graphe de scène n'est pas thread-safe. D'où le partage - un fil
    /// de veille compte le temps, et fait exécuter chaque observation sur le fil JavaFX par
    /// `runLater`, que la boucle imbriquée dépile.
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
