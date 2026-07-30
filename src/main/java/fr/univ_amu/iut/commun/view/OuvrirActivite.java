package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.ContextePassage;

/// Contrat de navigation inter-feature : « ouvrir l'écran **Activité de la nuit** (M-Activite) d'un
/// passage » (#2352, lot 2 du chantier #2348).
///
/// Défini dans le socle (`commun.view`) pour que `passage` (M-Passage) ouvre l'activité **sans dépendre
/// de la feature qui l'implémente** : l'écran vit dans `analyse`, qui dépend déjà de `passage`, donc une
/// dépendance directe formerait un cycle. La feature `analyse` en fournit l'implémentation (bindée par
/// son module `ActiviteModule`). Même esprit que [OuvrirDiagnostic] et [OuvrirValidation].
public interface OuvrirActivite {

    /// Ouvre l'écran d'activité du passage décrit par `passage` (identité + contexte site, pour le fil
    /// d'Ariane).
    void ouvrir(ContextePassage passage);
}
