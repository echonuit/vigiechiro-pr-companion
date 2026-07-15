package fr.univ_amu.iut.commun.view;

import java.util.Objects;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

/// Comportements communs des **fenêtres modales** de l'application.
///
/// **Fermeture par Échap (#1505).** `docs/raccourcis-clavier.md` promet « Échap : fermer la fenêtre
/// ouverte (modale) », mais aucune modale ne l'implémentait : l'écart était transverse (connexion,
/// point, site, rattachement, sélection d'écoute…). Plutôt que de recopier un gestionnaire dans
/// chaque façade de navigation, on pose ici **un seul** patron, appelé à la création de chaque Stage
/// modal.
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
}
