package fr.univ_amu.iut.commun.view;

import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;

/// Comportements communs des **fenêtres modales** de l'application.
///
/// **Fermeture par Échap (#1505).** `docs/raccourcis-clavier.md` promet « Échap : fermer la fenêtre
/// ouverte (modale) », mais aucune modale ne l'implémentait : l'écart était transverse (connexion,
/// point, site, rattachement, sélection d'écoute…). Plutôt que de recopier un gestionnaire dans
/// chaque façade de navigation, on pose ici **un seul** patron, appelé à la création de chaque Stage
/// modal.
///
/// **Croissance du contenu ([#suivreLaCroissance]).** Même histoire : une modale dimensionnée à
/// l'ouverture ne suit pas ce qui paraît ensuite. Chaque modale s'en tirait pour son seul cas connu,
/// et laissait tomber les autres.
public final class Modales {

    private Modales() {}

    /// La fenêtre qui porte `noeud`, ou `null` tant qu'il n'est attaché à aucune scène.
    ///
    /// C'est le propriétaire à donner à une modale, et le calcul était recopié dans chaque écran qui en
    /// ouvre une. Il est ici nommé une fois, avec sa **garde** : appeler `getScene().getWindow()` sur un
    /// noeud pas encore attaché lève une `NullPointerException`, ce qui a poussé plusieurs écrans à
    /// renoncer purement et simplement au propriétaire.
    ///
    /// Rendre `null` est un résultat **normal**, pas un échec : l'écran peut ne pas encore être
    /// affiché quand on construit son notificateur. C'est pourquoi le propriétaire se demande **au
    /// moment d'ouvrir**, par un `Supplier`, et non une fois pour toutes à la construction.
    public static Window fenetreDe(Node noeud) {
        if (noeud == null || noeud.getScene() == null) {
            return null;
        }
        return noeud.getScene().getWindow();
    }

    /// Pose `modale` au centre de son propriétaire, une fois qu'elle connaît sa taille.
    ///
    /// **Le produit place lui-même ses modales.** Sans cela le placement est celui du gestionnaire
    /// de fenêtres, et il n'est le même nulle part : la même modale, au même commit, s'ouvrait centrée
    /// sur un poste et en (0, 0) barre de titre hors champ en intégration continue. Un utilisateur n'a
    /// pas plus de garantie qu'un runner.
    ///
    /// **Au `setOnShown`, et non avant `show()`.** Avant l'affichage `getWidth()` rend `NaN` :
    /// centrer sur une largeur inconnue pose la fenêtre n'importe où sans rien signaler. Et
    /// `setOnShown` n'admet qu'un seul gestionnaire, donc cet appel remplace celui posé avant lui.
    ///
    /// Ne vaut que pour un [Stage]. Un [javafx.scene.control.Dialog] n'en est pas un et n'en a pas
    /// besoin, `ModalesCentrageTest` mesurant que JavaFX le centre sur son propriétaire ; ce qui lui
    /// manque parfois est le **propriétaire** (#4092, #4074).
    public static void centrerSur(Stage modale, Window proprietaire) {
        Objects.requireNonNull(modale, "modale");
        if (proprietaire == null) {
            return;
        }
        modale.setOnShown(evenement -> {
            double largeur = modale.getWidth();
            double hauteur = modale.getHeight();
            if (Double.isNaN(largeur) || Double.isNaN(hauteur)) {
                return;
            }
            modale.setX(proprietaire.getX() + (proprietaire.getWidth() - largeur) / 2);
            modale.setY(proprietaire.getY() + (proprietaire.getHeight() - hauteur) / 2);
        });
    }

    /// Installe la fermeture par la touche **Échap** sur une fenêtre modale.
    ///
    /// Le gestionnaire est posé sur le Stage (phase de **bulle**) : il ne se déclenche que si aucun
    /// nœud focalisé n'a consommé la touche avant (une liste déroulante ouverte, par exemple, ferme
    /// d'abord sa popup avec Échap sans fermer la modale). Sans consommation, Échap ferme la fenêtre,
    /// exactement comme le bouton « Annuler ».
    ///
    /// @param modale la fenêtre modale à équiper (doit déjà porter une scène)
    public static void fermerParEchap(Stage modale) {
        Objects.requireNonNull(modale, "modale");
        modale.addEventHandler(KeyEvent.KEY_PRESSED, evenement -> {
            if (evenement.getCode() == KeyCode.ESCAPE) {
                modale.close();
                evenement.consume();
            }
        });
    }

    /// Fait suivre à la fenêtre la **croissance** de son contenu.
    ///
    /// Une modale est dimensionnée à son ouverture, sur le contenu visible **à cet instant**. Ce qui
    /// paraît ensuite (#1534) agrandit la mise en page sans agrandir la fenêtre, et le bas passe sous
    /// la ligne de flottaison.
    ///
    /// L'ajustement se fait par `sizeToScene()` : la fenêtre est refaite à la taille de son contenu, et
    /// une fenêtre agrandie à la main y perd sa taille. **Ne la remplacez jamais** par le maximum de
    /// l'ancienne et de la nouvelle : `setWidth`/`setHeight` figent le Stage en dimensionnement
    /// explicite, définitivement, et le Stage du harnais TestFX est partagé par tout un fork, ce que
    /// l'ADR 4134 détaille.
    ///
    /// **Déclarez chaque révélation**, ou la propriété qui la pilote. Une zone non déclarée fait
    /// déborder le contenu pendant tout le transitoire, puis tout se recale d'un coup : l'utilisateur
    /// voit un sursaut qu'aucune capture ne montre, une capture photographiant un état stabilisé.
    /// Trouvé trois fois (#3453), et aucun garde ne le vérifie : séparer « surveillé directement » de
    /// « surveillé par son moteur » demanderait d'analyser les liaisons JavaFX, et une règle plus
    /// grossière rendrait la loupe bruyante que l'ADR 3479 écarte.
    ///
    /// @param racine la racine de la modale, celle que porte la scène
    /// @param revelations les propriétés dont un changement fait paraître du contenu
    public static void suivreLaCroissance(Region racine, ObservableValue<?>... revelations) {
        Objects.requireNonNull(racine, "racine");
        for (ObservableValue<?> revelation : revelations) {
            revelation.addListener((observable, avant, apres) -> Platform.runLater(() -> agrandirAuBesoin(racine)));
        }
    }

    /// Ajuste la fenêtre à son contenu. Différé d'un tour de boucle par [#suivreLaCroissance] : un libellé
    /// enroulé n'a de hauteur qu'une fois sa largeur connue, donc après la passe de mise en page qui suit
    /// la révélation.
    private static void agrandirAuBesoin(Region racine) {
        if (racine.getScene() == null || !(racine.getScene().getWindow() instanceof Stage modale)) {
            return;
        }
        modale.sizeToScene();
    }
}
