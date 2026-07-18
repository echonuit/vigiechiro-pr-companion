package fr.univ_amu.iut.commun.view;

import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

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
    /// Une modale est dimensionnée à son ouverture, sur le contenu visible **à cet instant**. Tout ce qui
    /// paraît ensuite - une seconde barre de phase qui se révèle, un compte rendu de fin - agrandit la mise
    /// en page sans agrandir la fenêtre, et le bas passe sous la ligne de flottaison. Le compte rendu de la
    /// reconstruction en avait souffert le premier (#1534) ; chaque modale s'était alors rattrapée pour son
    /// seul cas connu, si bien que la réactivation poussait toujours ses **boutons** hors de la fenêtre dès
    /// que la barre d'ancrage paraissait.
    ///
    /// L'ajustement se fait par `sizeToScene()`, donc la fenêtre est **refaite à la taille de son
    /// contenu** : une fenêtre que l'utilisateur aurait agrandie à la main y perd sa taille. Une première
    /// version gardait le maximum entre l'ancienne taille et la nouvelle, pour ne jamais rétrécir. Elle a
    /// été retirée : `setWidth`/`setHeight` font passer un Stage en dimensionnement **explicite**, et il
    /// cesse alors définitivement de s'ajuster à ses scènes suivantes. Sans effet pour une modale, que
    /// l'on jette après usage - mais le Stage du harnais TestFX, lui, est **partagé par toutes les classes
    /// de test d'un même fork** : figé à 600 px de large, il faisait échouer les classes suivantes sur des
    /// noeuds « invisibles », très loin de leur cause et seulement selon l'ordre d'exécution.
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
