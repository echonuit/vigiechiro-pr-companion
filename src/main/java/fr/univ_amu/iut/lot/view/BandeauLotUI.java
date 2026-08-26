package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.commun.view.BandeauRetour;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/// Câble le **bandeau de retour unique** de l'écran de dépôt (ADR 0023, #1890), alimenté par les deux
/// ViewModels de l'écran.
///
/// Pourquoi un seul bandeau et non un par ViewModel : les actions du lot sont réparties sur quatre
/// sections (préparer, générer, marquer déposé, libérer l'espace), donc aucun ancrage ne les rapproche
/// toutes. Un endroit unique et prévisible, en tête de flux, vaut mieux qu'un libellé par section dont
/// certains resteraient loin de leur bouton. Le libellé précédent vivait en **bas** d'un écran qui
/// déborde de la zone visible : qui agissait au milieu ne voyait jamais son compte rendu.
///
/// Les deux canaux se fondent dans une propriété relais qui retient le **dernier arrivé** : deux
/// opérations ne se concluent jamais en même temps, et la plus récente est celle que l'utilisateur
/// vient de déclencher. Les ViewModels continuent de s'ignorer l'un l'autre.
///
/// Le [LotController] reste du **pur câblage**, au même titre que devant [EtapeDeposerUI].
final class BandeauLotUI {

    private BandeauLotUI() {}

    static void cabler(HBox bandeau, Label libelle, Button fermer, LotViewModel lot, DepotViewModel depot) {
        ObjectProperty<RetourOperation> dernier = new SimpleObjectProperty<>(RetourOperation.AUCUN);
        lot.retourProperty().addListener((observable, avant, apres) -> dernier.set(apres));
        depot.retourProperty().addListener((observable, avant, apres) -> dernier.set(apres));
        BandeauRetour.installer(bandeau, libelle, fermer, dernier, () -> {
            lot.effacerRetour();
            depot.effacerRetour();
        });
    }
}
