package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.ContextePassage;

/// Contrat de navigation inter-feature : « ouvrir l'écran de vérification (M-Qualification) pour un
/// passage donné ».
///
/// Défini dans le socle (`commun.view`) pour **briser le cycle** entre features : l'écran appelant
/// (M-Passage, feature `passage`) dépend de cette interface du socle, et la feature `qualification`
/// en fournit l'implémentation (`NavigationQualification`, bindée par `QualificationModule`). On a
/// donc `passage → commun` et `qualification → commun`, jamais `passage → qualification` (qui
/// formerait un cycle avec `qualification → passage`, déjà présent via les DAO).
///
/// Même esprit que [ActiviteAccueil] : inversion de dépendance par le socle.
public interface OuvrirVerification {

    /// Ouvre l'écran de vérification par échantillonnage du passage décrit par `passage` (identité +
    /// contexte site, pour le fil d'Ariane).
    void ouvrir(ContextePassage passage);
}
