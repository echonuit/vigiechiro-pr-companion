package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.commun.view.BandeauRetour;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/// Câble **ce que l'écran dit quand il n'a rien à montrer, ou quand une opération vient de se terminer** :
/// l'indice d'état vide, le bandeau de retour d'opération, et le bandeau « passage archivé / audio
/// partiel ».
///
/// Extrait de [SonsValidationController], comme `MenuCertitude`, `PanneauDiscussion` et
/// [SelectionTableAudio] : ce contrôleur est au plafond de `NcssCount`. Les trois messages forment un
/// tout — ils occupent la même zone de l'écran et leur séparation est justement ce que #795 a établi,
/// pour qu'une erreur d'import ne soit plus noyée dans le placeholder gris de l'état vide.
final class MessagesEcranAudio {

    private MessagesEcranAudio() {}

    /// Installe les trois messages.
    ///
    /// @param lblVide placeholder gris superposé à la table, réservé au seul « aucune observation… »
    /// @param bandeauRetour conteneur du retour d'opération (import / export / valider / corriger)
    /// @param lblRetour libellé du retour d'opération
    /// @param btnFermerRetour croix de fermeture du retour
    /// @param lblBandeauArchive bandeau « passage archivé / audio partiel n/total », masqué quand tout est là
    static void installer(
            Label lblVide,
            HBox bandeauRetour,
            Label lblRetour,
            Button btnFermerRetour,
            Label lblBandeauArchive,
            AudioViewModel viewModel) {
        BooleanBinding listeVide = Bindings.isEmpty(viewModel.observationsFiltrees());
        lblVide.textProperty().bind(viewModel.messageProperty());
        lblVide.visibleProperty().bind(listeVide);
        lblVide.managedProperty().bind(listeVide);

        // Libellé, visibilité, couleur de sévérité et croix de fermeture : décorrélés de l'état vide pour
        // qu'une erreur d'import ne soit plus noyée dans le placeholder gris (#795).
        BandeauRetour.installer(
                bandeauRetour, lblRetour, btnFermerRetour, viewModel.retourProperty(), viewModel::effacerRetour);

        BooleanBinding bandeauPresent = viewModel.bandeauArchiveProperty().isNotEmpty();
        lblBandeauArchive.textProperty().bind(viewModel.bandeauArchiveProperty());
        lblBandeauArchive.visibleProperty().bind(bandeauPresent);
        lblBandeauArchive.managedProperty().bind(bandeauPresent);
    }
}
