package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.ContextePassage;

/// Contrat socle d'**ouverture des séquences non identifiées** d'un passage : les enregistrements présents
/// sur disque mais **sans observation Tadarida**, à écouter (en vue d'une validation manuelle) dans la vue
/// « Sons & validation ».
///
/// Même esprit que [OuvrirValidation] : le socle inverse la dépendance. La feature `validation` en fournit
/// l'implémentation (`NavigationNonIdentifies`, qui délègue à [OuvrirAudio]) ; la feature `passage`
/// (M-Passage) l'injecte pour ouvrir l'écran **sans dépendre** de `audio.view` / `validation.view` (le
/// graphe de slices reste acyclique).
public interface OuvrirNonIdentifies {

    /// Ouvre la vue audio sur les **séquences non identifiées** du passage décrit par `passage` (identité +
    /// contexte site, pour le fil d'Ariane et le retour au passage).
    void ouvrir(ContextePassage passage);
}
