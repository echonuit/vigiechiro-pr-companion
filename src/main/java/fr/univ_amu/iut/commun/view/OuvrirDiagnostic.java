package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.ContextePassage;

/// Contrat de navigation inter-feature : « ouvrir l'écran de diagnostic matériel (M-Diagnostic)
/// d'un passage ».
///
/// Défini dans le socle (`commun.view`) pour permettre à `passage` (M-Passage) d'ouvrir le
/// diagnostic **sans dépendre de la feature `diagnostic`** — qui dépend déjà de `passage`
/// (`ServiceDiagnostic` lit les DAO passage), donc une dépendance directe formerait un cycle. La
/// feature `diagnostic` en fournit l'implémentation (bindée par son module). Même esprit que
/// [OuvrirVerification] et [OuvrirPassage].
public interface OuvrirDiagnostic {

    /// Ouvre l'écran de diagnostic matériel du passage décrit par `passage` (identité + contexte site,
    /// pour le fil d'Ariane).
    void ouvrir(ContextePassage passage);
}
