package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.commun.model.CertitudeObservateur;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import javafx.beans.binding.Bindings;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.StackPane;

/// Câblage du menu « Certitude » de la barre d'actions (#1139) : la déclaration manuelle
/// `Sûr | Probable | Possible` de l'observateur sur la sélection, en miroir de la « Confiance
/// observateur » du site VigieChiro (vide par défaut, jamais préremplie ; c'est la valeur exigée avec
/// le taxon pour pousser une correction, #723). Un item par certitude (raccourcis clavier `1/2/3`,
/// cf. [RevueClavier]) + « Effacer » (possible localement, contrairement à l'API).
///
/// Classe dédiée (pas un bloc de plus dans [SonsValidationController], seuil de God Class), sur le
/// modèle de [ColonnesAudio]/[ActionsRevueAudio] : le contrôleur injecte les nœuds FXML et les passe
/// ici pour câblage.
final class MenuCertitude {

    private MenuCertitude() {}

    /// Installe les items du menu et son activation : comme Référence/Douteux, la certitude ne
    /// s'applique qu'à une **observation** (une séquence non identifiée n'en porte pas) ; le tooltip
    /// d'explication du blocage est posé sur l'enveloppe (#789, un contrôle désactivé n'en affiche pas).
    static void installer(
            MenuButton menu, StackPane enveloppe, AudioViewModel viewModel, ActionsSelectionAudio actions) {
        for (CertitudeObservateur certitude : CertitudeObservateur.values()) {
            menu.getItems().add(item(certitude.libelle(), certitude, actions));
        }
        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(item("Effacer la certitude", null, actions));

        menu.disableProperty()
                .bind(viewModel.etatSelection().avecObservationProperty().not());
        IndicateurBlocage.expliquer(
                enveloppe,
                Bindings.when(viewModel.etatSelection().avecObservationProperty())
                        .then("Déclarer votre certitude (Sûr, Probable, Possible) sur la sélection :"
                                + " c'est la « confiance observateur » du site VigieChiro, exigée pour"
                                + " publier une correction.")
                        .otherwise("Réservé à une observation : validez ou corrigez d'abord la ligne."));
    }

    private static MenuItem item(String libelle, CertitudeObservateur certitude, ActionsSelectionAudio actions) {
        MenuItem item = new MenuItem(libelle);
        item.setOnAction(evenement -> actions.poserCertitude(certitude));
        return item;
    }
}
