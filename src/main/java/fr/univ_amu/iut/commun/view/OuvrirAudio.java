package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.SourceObservations;

/// Contrat socle d'**ouverture de la vue audio unifiée** (« Sons & validation ») sur un ensemble
/// d'observations décrit par une [SourceObservations] (un passage, un lot, une espèce, le corpus de
/// référence).
///
/// Même esprit que les autres contrats `Ouvrir*` : le socle inverse la dépendance. La feature `audio`
/// en fournit l'implémentation (`NavigationAudio`) et la lie dans son module ; les features qui
/// alimentent la vue (accueil pour les références, M-Passage, analyse, multisite) l'injectent pour
/// ouvrir l'écran **sans dépendre** de `audio.view` (le graphe de slices reste acyclique).
///
/// Généralise l'historique [OuvrirValidation] (un passage) : à terme `OuvrirValidation` déléguera à ce
/// contrat avec une source `ParPassage`, sans casser ses appelants.
public interface OuvrirAudio {

    /// Ouvre la vue audio sur l'ensemble décrit par `source`.
    void ouvrir(SourceObservations source);

    /// Ouvre la vue audio sur `source`, **pré-focalisée** sur l'observation `idObservationCible` si elle
    /// est non nulle : la ligne correspondante est sélectionnée à l'ouverture (ce qui déclenche l'écoute
    /// de sa séquence). Par défaut, la cible est ignorée (les doublures de test n'ont pas à la gérer).
    default void ouvrir(SourceObservations source, Long idObservationCible) {
        ouvrir(source);
    }
}
