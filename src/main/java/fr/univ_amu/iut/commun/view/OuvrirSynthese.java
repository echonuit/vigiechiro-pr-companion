package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.ContextePassage;

/// Contrat de navigation inter-feature : « ouvrir l'écran **Synthèse de la nuit** (M-Synthese) d'un
/// passage » (#2351, lot 1 du chantier #2348).
///
/// Défini dans le socle pour que `passage` ouvre la synthèse **sans dépendre de la feature qui
/// l'implémente**, l'écran vit dans `analyse`, qui dépend déjà de `passage` : une dépendance directe
/// formerait un cycle. Même montage que [OuvrirActivite].
public interface OuvrirSynthese {

    /// Ouvre la synthèse du passage décrit par `passage`. La **nuit biologique** : dont se déduit la
    /// saison du référentiel, est lue des enregistrements du passage, pas passée en paramètre : elle est
    /// dans la donnée, et l'appelant n'a pas à la connaître.
    void ouvrir(ContextePassage passage);
}
