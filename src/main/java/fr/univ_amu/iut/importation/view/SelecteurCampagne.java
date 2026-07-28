package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.importation.viewmodel.RattachementImportViewModel;
import fr.univ_amu.iut.passage.model.Campagne;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;

/// Câblage de la liste déroulante **Campagne** de l'assistant d'import (#2631).
///
/// Extrait d'[ImportationController] parce que le portail qualité l'a demandé : ce contrôleur est au
/// plafond de lignes, et une quinzaine de plus le faisait déborder. L'extraction suit le patron des
/// autres collaborateurs de vue du dépôt (`LibelleRetour.installer`, `ValidationFormulaire`) : une
/// classe, un verbe, aucun état.
///
/// **Présentationnel pur** : il relie des contrôles à un ViewModel, il ne décide de rien.
final class SelecteurCampagne {

    private SelecteurCampagne() {}

    /// Installe la liste déroulante, ou **efface la ligne** si la fonctionnalité `campagne` est coupée.
    ///
    /// Le point délicat est le signal de **choix explicite** : `setOnAction` ne se déclenche que sur une
    /// action de l'utilisateur, jamais sur une écriture programmée. C'est ce qui permet à la proposition
    /// automatique de renoncer dès que l'utilisateur a tranché lui-même - un écouteur de valeur, lui,
    /// verrait passer les deux à l'identique et prendrait la proposition pour un choix.
    static void installer(HBox ligne, ComboBox<Campagne> combo, RattachementImportViewModel rattachement) {
        boolean activee = rattachement.campagneActivee();
        ligne.setVisible(activee);
        ligne.setManaged(activee);
        if (!activee) {
            return;
        }
        rattachement.chargerCampagnes();
        combo.setItems(rattachement.campagnesProposees());
        combo.setConverter(Convertisseurs.depuis(Campagne::nom));
        combo.valueProperty().bindBidirectional(rattachement.campagneSelectionneeProperty());
        combo.setOnAction(evenement -> rattachement.marquerCampagneChoisie());
    }
}
