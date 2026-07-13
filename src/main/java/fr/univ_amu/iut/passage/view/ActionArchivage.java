package fr.univ_amu.iut.passage.view;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.passage.model.ServiceArchivagePassage;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.util.Objects;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;

/// Action IHM « Archiver ce passage » (#1300), extraite de [PassageController] (pur câblage, PMD
/// GodClass) : compose la confirmation (le gain, ce qu'on garde, ce qu'on perd, la condition de
/// retour), déclenche l'archivage via le ViewModel, recharge l'écran puis présente le bilan.
final class ActionArchivage {

    private final PassageViewModel viewModel;
    private final ConfirmateurModifiable confirmateur;
    private final Runnable recharger;

    /// @param viewModel ViewModel de M-Passage (porte l'archivage et l'annonce)
    /// @param confirmateur porteur de confirmation partagé de l'écran (stub déterministe en test)
    /// @param recharger rejeu de l'ouverture de l'écran après archivage (volumes à 0, bouton grisé)
    ActionArchivage(PassageViewModel viewModel, ConfirmateurModifiable confirmateur, Runnable recharger) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.confirmateur = Objects.requireNonNull(confirmateur, "confirmateur");
        this.recharger = Objects.requireNonNull(recharger, "recharger");
    }

    /// Confirme puis archive : purge l'audio du passage (séquences et bruts) en gardant observations
    /// et validations consultables. Un refus métier (passage non déposé) est présenté sans quitter
    /// l'écran.
    void archiver() {
        if (!confirmateur.confirmer(messageConfirmation(viewModel.volumeArchivable()))) {
            return;
        }
        try {
            ServiceArchivagePassage.BilanArchivage bilan = viewModel.archiver();
            recharger.run();
            alerte(
                    AlertType.INFORMATION,
                    "Passage archivé",
                    Formats.octetsLisibles(bilan.octetsLiberes())
                            + " libéré(s). Les observations et validations restent consultables ;"
                            + " réimportez les fichiers d'origine pour réécouter.");
        } catch (RegleMetierException refus) {
            alerte(AlertType.WARNING, "Archivage impossible", refus.getMessage());
        }
    }

    /// Message de confirmation : le gain, ce qui est gardé, ce qui est perdu, et la condition de
    /// retour. Mentionne la capture d'empreintes quand des séquences n'en ont pas encore (#1299) :
    /// c'est elle qui garantira l'identité des fichiers réimportés.
    private String messageConfirmation(long recuperable) {
        StringBuilder message = new StringBuilder("Archiver ce passage et libérer environ "
                + Formats.octetsLisibles(recuperable)
                + " ? L'audio (séquences et bruts) sera supprimé du disque ; les observations, les"
                + " validations, le journal et le relevé restent consultables. Pour réécouter un jour,"
                + " il faudra réimporter les mêmes fichiers : la plateforme VigieChiro ne rend pas"
                + " l'audio d'un dépôt ZIP, sans eux la perte est définitive.");
        int sansEmpreinte = viewModel.sequencesSansEmpreinte();
        if (sansEmpreinte > 0) {
            message.append("\n\nL'empreinte de ")
                    .append(sansEmpreinte)
                    .append(" séquence(s) sera capturée avant la suppression, pour reconnaître à coup"
                            + " sûr les fichiers réimportés.");
        }
        return message.toString();
    }

    private static void alerte(AlertType type, String entete, String message) {
        Alert alerte = new Alert(type, message, ButtonType.OK);
        alerte.setHeaderText(entete);
        alerte.showAndWait();
    }
}
