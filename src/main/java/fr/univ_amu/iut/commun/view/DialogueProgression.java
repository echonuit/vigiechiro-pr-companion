package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/// Suivi d'opération longue **dans sa propre fenêtre** (#1597) : une modale portant un titre, une barre
/// de progression déterminée, un libellé d'étape (avec ETA) et un bouton « Annuler ».
///
/// Là où [IndicateurOccupation] pose un voile opaque (« ça travaille »), ce suivi **dit où on en est** et
/// **laisse renoncer** - ce qu'attend une opération de plusieurs dizaines de secondes (réactivation avec
/// ancrage #1571, import des observations #1622, synchronisation #2558).
///
/// **Quand la préférer à [PanneauProgression]** : quand le geste ne part pas d'une modale. Lancée depuis
/// une modale, une fenêtre en produirait une seconde par-dessus la première, pour un seul geste (#2642).
///
/// Tout ce qui ne dépend pas de la fenêtre - le contenu, l'orchestration - vit dans [SuiviProgression].
public final class DialogueProgression extends SuiviProgression {

    public DialogueProgression(ExecuteurTache executeur) {
        super(executeur);
    }

    @Override
    protected Runnable presenter(Window proprietaire, String titre, VBox contenu, JetonAnnulation jeton) {
        Stage modale = new Stage();
        modale.initOwner(proprietaire);
        modale.initModality(Modality.WINDOW_MODAL);
        modale.setTitle(titre);
        modale.setScene(new Scene(contenu));
        // Fermer la fenêtre = renoncer : on demande l'annulation plutôt que de laisser le travail orphelin.
        modale.setOnCloseRequest(evenement -> jeton.annuler());
        modale.show();
        return modale::close;
    }
}
