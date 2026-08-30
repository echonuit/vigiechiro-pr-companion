package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.FormatsLot;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import javafx.beans.value.ObservableValue;

/// Calcule les **3 zones de la barre de statut** de M-Lot (#693 / #823), extrait de [LotController] pour
/// la cohésion (seuil NCSS du contrôleur). **Pur** : lit les propriétés des ViewModels + le contexte
/// courant, sans effet de bord.
///
/// Zone gauche = contexte du passage ; zone centre = statut + récapitulatif ; zone droite = **état
/// vivant**, une seule info par priorité décroissante : lancement d'analyse > dépôt en cours (#982) >
/// génération (#769) > espace disque insuffisant > bilan des archives au repos (#805).
final class ZonesStatutLot {

    private final LotViewModel viewModel;
    private final DepotViewModel depotViewModel;
    private final ObservableValue<ContextePassage> contexte;

    /// `contexte` est une [ObservableValue] et non un `Supplier` (#3548) : le contexte change à chaque
    /// `ouvrirSur`, et un `Supplier` permet bien de lire la valeur du moment mais n'annonce jamais
    /// qu'elle a changé. Le binding qui appelle [#calculer] doit pouvoir le **déclarer**.
    ZonesStatutLot(LotViewModel viewModel, DepotViewModel depotViewModel, ObservableValue<ContextePassage> contexte) {
        this.viewModel = viewModel;
        this.depotViewModel = depotViewModel;
        this.contexte = contexte;
    }

    ZonesStatut calculer() {
        return new ZonesStatut(contexteGauche(), centreStatutRecap(), droiteEtatVivant());
    }

    private String contexteGauche() {
        ContextePassage courant = contexte.getValue();
        return courant == null ? "" : courant.identiteStatut();
    }

    /// La sortie anticipée « récapitulatif vide » est **défensive** et le restera : elle n'existe que pour
    /// l'instant qui sépare les deux écritures de [LotViewModel#appliquer], où le statut est déjà posé et
    /// le récapitulatif pas encore. Sans elle, cet instant afficherait « Prêt à déposer · ».
    ///
    /// Aucun état **stable** ne l'emprunte, et c'est un relevé, pas une intuition : les quatre sites
    /// d'écriture ont été lus (#3739). `recap` ne vaut jamais le vide en dehors de `reinitialiser()`, qui
    /// vide `statut` deux lignes plus haut, et `recapLisible` produit toujours « N séquences · volume ».
    ///
    /// D'où un mutant PIT qui **survivra** ici (`return statut` → `return ""`) : il est équivalent dans
    /// tout état atteignable. Le couvrir demanderait de fabriquer un état que le ViewModel ne sait pas
    /// tenir, ce qui ne garderait rien.
    private String centreStatutRecap() {
        String statut = viewModel.statutProperty().get();
        String recap = viewModel.recapProperty().get();
        if (recap == null || recap.isBlank()) {
            return statut;
        }
        return statut == null || statut.isBlank() ? recap : statut + " · " + recap;
    }

    private String droiteEtatVivant() {
        // La réconciliation passe AVANT tout le reste (#4631) : quand elle n'a pas pu lire, des archives
        // déjà déposées vont repartir, et l'utilisateur doit le savoir AVANT d'attendre le dépôt, pas
        // après. Annoncer « 3/12 déposées » par-dessus lui cacherait la seule chose sur laquelle il peut
        // encore agir.
        String reconciliation =
                depotViewModel.suiviLignes().reconciliationImpossibleProperty().get();
        if (!reconciliation.isEmpty()) {
            return reconciliation;
        }
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
            return viewModel.raisonEspaceInsuffisantAbregeeProperty().get();
        }
        return FormatsLot.bilanArchives(viewModel.suiviLignes().lignes());
    }
}
