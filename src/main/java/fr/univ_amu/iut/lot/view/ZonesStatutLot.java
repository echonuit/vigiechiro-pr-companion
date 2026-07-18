package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.FormatsLot;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import java.util.function.Supplier;

/// Calcule les **3 zones de la barre de statut** de M-Lot (#693 / #823), extrait de [LotController] pour
/// la cohésion (seuil NCSS du contrôleur). **Pur** : lit les propriétés des ViewModels + le contexte
/// courant, sans effet de bord.
///
/// Zone gauche = contexte du passage ; zone centre = statut + récapitulatif ; zone droite = **état
/// vivant**, une seule info par priorité décroissante : dépôt en cours (#982) > génération (#769) >
/// espace disque insuffisant > bilan des archives au repos (#805).
final class ZonesStatutLot {

    private final LotViewModel viewModel;
    private final DepotViewModel depotViewModel;
    private final Supplier<ContextePassage> contexte;

    /// `contexte` est fourni en [Supplier] car le contexte courant du contrôleur change à chaque
    /// `ouvrirSur` : on lit toujours la valeur du moment au calcul.
    ZonesStatutLot(LotViewModel viewModel, DepotViewModel depotViewModel, Supplier<ContextePassage> contexte) {
        this.viewModel = viewModel;
        this.depotViewModel = depotViewModel;
        this.contexte = contexte;
    }

    ZonesStatut calculer() {
        return new ZonesStatut(contexteGauche(), centreStatutRecap(), droiteEtatVivant());
    }

    private String contexteGauche() {
        ContextePassage courant = contexte.get();
        return courant == null ? "" : courant.identiteStatut();
    }

    private String centreStatutRecap() {
        String statut = viewModel.statutProperty().get();
        String recap = viewModel.recapProperty().get();
        if (recap == null || recap.isBlank()) {
            return statut;
        }
        return statut == null || statut.isBlank() ? recap : statut + " · " + recap;
    }

    private String droiteEtatVivant() {
        // Le lancement passe AVANT le téléversement : les deux allument `enCours`, mais un lancement n'a
        // pas de compteur d'archives - annoncer « n/N déposées » pendant un simple appel serait faux.
        if (depotViewModel.lancementEnCoursProperty().get()) {
            return FormatsLot.libelleLancementEnCours();
        }
        if (depotViewModel.enCoursProperty().get()) {
            var suivi = depotViewModel.suiviLignes();
            return FormatsLot.libelleDepotEnCours(
                    suivi.deposeesProperty().get(),
                    suivi.enCoursProperty().get(),
                    suivi.echecsProperty().get(),
                    suivi.totalProperty().get());
        }
        if (viewModel.generationEnCoursProperty().get()) {
            return viewModel.progression().messageProperty().get();
        }
        if (!viewModel.espaceDepotSuffisantProperty().get()) {
            return viewModel.raisonEspaceInsuffisantProperty().get();
        }
        return FormatsLot.bilanArchives(viewModel.suiviLignes().lignes());
    }
}
