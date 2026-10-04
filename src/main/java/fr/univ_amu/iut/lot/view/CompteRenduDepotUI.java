package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.commun.view.PanneauCompteRendu;
import fr.univ_amu.iut.lot.viewmodel.CompteRenduChiffreDepot;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import java.util.List;
import java.util.Objects;
import javafx.scene.layout.VBox;

/// Câblage du **compte rendu de fin de dépôt** (#2653) sous l'étape 3.
///
/// Sœur d'[EtapeTeleverserUI], extraite pour la même raison : le contrôleur du lot est au plafond de
/// taille que le portail qualité lui accorde.
///
/// Le compte rendu ne porte **aucun bouton** (#5676). Il nomme la prochaine étape, « lancer la
/// participation », que seule l'étape 4 offre : deux boutons pour un même geste laissaient
/// l'utilisateur se demander lequel cliquer. Un dépôt incomplet n'en proposait déjà aucun, sa suite
/// « Reprendre le dépôt » étant sous les yeux à l'étape 3.
final class CompteRenduDepotUI {

    private CompteRenduDepotUI() {}

    /// Câble la bande sur la fin de dépôt.
    static void cabler(VBox zone, DepotViewModel depot) {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(depot, "depot");
        PanneauCompteRendu bande = new PanneauCompteRendu();
        zone.getChildren().setAll(bande);
        depot.finDepotProperty().addListener((observable, avant, fin) -> afficher(zone, bande, fin));
        afficher(zone, bande, depot.finDepotProperty().get());
    }

    private static void afficher(VBox zone, PanneauCompteRendu bande, DepotViewModel.FinDepot fin) {
        if (fin != null) {
            bande.afficher(CompteRenduChiffreDepot.de(fin.bilan(), fin.plan(), List.of()));
        }
        zone.setVisible(fin != null);
        zone.setManaged(fin != null);
    }
}
