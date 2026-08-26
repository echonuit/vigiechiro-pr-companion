package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import java.util.Objects;
import java.util.function.Supplier;

/// Ce dont la sous-vue « Téléverser » a besoin, regroupé (#2745).
///
/// Un Parameter-Object plutôt qu'une liste de huit arguments : c'est la règle du dépôt sur l'arité des
/// constructions, et une signature de huit paramètres du même genre (deux ViewModels, deux ports,
/// deux fonctions) se remplit un jour dans le désordre sans que rien ne le dise.
///
/// Ces appuis **viennent du parent**, jamais d'une injection propre à la sous-vue : les ViewModel du
/// dépôt sont non-singleton, et se les injecter en donnerait de nouveaux, vides (ADR 2745, gardé par
/// `DecisionsRespecteesTest#une_sous_vue_ne_s_injecte_pas_son_modele`).
///
/// @param viewModel état du lot (chemin de dépôt, suivi d'archives, statut)
/// @param depotViewModel état du téléversement (lignes, en-cours, bilan)
/// @param executeur exécuteur de tâches longues, et son relais vers le fil JavaFX
/// @param depotColonnes disposition persistée des colonnes, par écran et par table
/// @param ouvreurDeLien port d'ouverture d'un dossier dans le gestionnaire de fichiers
/// @param confirmateur porteur de confirmation, **partagé avec le parent** dont les tests le remplacent
/// @param idPassage passage courant, lu à chaque geste (le contexte change sans recharger la vue)
/// @param lancerParticipation étape ④, offerte par le compte rendu de fin de dépôt (#2653)
record AppuisTeleversement(
        LotViewModel viewModel,
        DepotViewModel depotViewModel,
        ExecuteurTache executeur,
        DepotDispositionColonnes depotColonnes,
        OuvreurDeLien ouvreurDeLien,
        ConfirmateurModifiable confirmateur,
        Supplier<Long> idPassage,
        Runnable lancerParticipation) {

    AppuisTeleversement {
        Objects.requireNonNull(viewModel, "viewModel");
        Objects.requireNonNull(depotViewModel, "depotViewModel");
        Objects.requireNonNull(executeur, "executeur");
        Objects.requireNonNull(depotColonnes, "depotColonnes");
        Objects.requireNonNull(ouvreurDeLien, "ouvreurDeLien");
        Objects.requireNonNull(confirmateur, "confirmateur");
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(lancerParticipation, "lancerParticipation");
    }
}
