package fr.univ_amu.iut.commun.model;

import java.util.List;

/// **Port** (inversion de dépendance `passage` → `sites`) donnant les **codes des points d'écoute d'un
/// carré**, sans dépendre de la feature `sites` : `sites → passage` existe déjà (`ServiceSites` lit
/// `PassageDao`), donc le sens inverse formerait un cycle, interdit par ArchUnit.
///
/// Troisième de la famille posée pour ce cycle précis, après [CoordonneesPoint] et [ReferentielPoint],
/// et lu dans l'autre sens : d'un **carré vers ses points**, là où [ReferentielPoint] va d'un point vers
/// son site. Même montage - la feature consommatrice pose un défaut no-op, `sites` fournit
/// l'implémentation réelle par un `OptionalBinder`.
///
/// ## Pourquoi le numéro de carré, et pas un identifiant de site
///
/// L'écran qui en a besoin - « Modifier le passage » - ne détient ni l'id du point, ni celui du site :
/// sa navigation lui transmet un **numéro de carré** et un **code de point**, et `DetailPassage` n'en
/// porte pas davantage. Se caler sur un `idSite` obligerait à le faire descendre par toute la chaîne de
/// navigation, pour un besoin qu'un seul écran a.
///
/// Le carré identifie bien un site : R5 impose son unicité par utilisateur.
@FunctionalInterface
public interface PointsDuCarre {

    /// Codes des points d'écoute du carré `numeroCarre`, dans l'ordre d'affichage de l'écran des sites.
    /// **Liste vide** si le carré est inconnu, ou si aucune implémentation n'est branchée (no-op).
    List<String> codes(String numeroCarre);
}
