package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.commun.view.PanneauCompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
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
/// C'est ici, et pas dans le ViewModel, que le compte rendu prend son **action suivante**. Le ViewModel
/// publie le fait (bilan + plan) ; l'écran seul sait où mènent ses boutons - et « Lancer la
/// participation » est l'étape ④, que rien ne désignait à la fin du geste qui la rend possible.
final class CompteRenduDepotUI {

    private CompteRenduDepotUI() {}

    /// Câble la bande sur la fin de dépôt. `lancerParticipation` est l'étape ④ : proposée **seulement**
    /// quand tout est en ligne, parce qu'inviter à lancer l'analyse d'une nuit incomplète ferait analyser
    /// une nuit incomplète - et la plateforme ne relance pas sans perdre ce qu'elle a déjà produit.
    static void cabler(VBox zone, DepotViewModel depot, Runnable lancerParticipation) {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(depot, "depot");
        Objects.requireNonNull(lancerParticipation, "lancerParticipation");
        PanneauCompteRendu bande = new PanneauCompteRendu();
        zone.getChildren().setAll(bande);
        depot.finDepotProperty()
                .addListener((observable, avant, fin) -> afficher(zone, bande, fin, lancerParticipation));
        afficher(zone, bande, depot.finDepotProperty().get(), lancerParticipation);
    }

    private static void afficher(
            VBox zone, PanneauCompteRendu bande, DepotViewModel.FinDepot fin, Runnable lancerParticipation) {
        if (fin != null) {
            bande.afficher(CompteRenduChiffreDepot.de(fin.bilan(), fin.plan(), actions(fin, lancerParticipation)));
        }
        zone.setVisible(fin != null);
        zone.setManaged(fin != null);
    }

    /// L'action suivante, quand il y en a une. Un dépôt incomplet n'en propose aucune : la suite y est
    /// « Reprendre le dépôt », un bouton déjà présent à l'étape 3 - et le compte rendu ne double pas un
    /// bouton qui est sous les yeux.
    private static List<CompteRenduChiffre.Action> actions(DepotViewModel.FinDepot fin, Runnable lancerParticipation) {
        boolean toutEnLigne = fin.bilan().echecs().isEmpty()
                && !fin.plan().interrompu()
                && fin.plan().enLigne() >= fin.plan().unitesDuPlan();
        return toutEnLigne
                ? List.of(new CompteRenduChiffre.Action("Lancer la participation", true, lancerParticipation))
                : List.of();
    }
}
