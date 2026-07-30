package fr.univ_amu.iut.commun.api;

import java.util.List;

/// Site VigieChiro rattaché à l'observateur, **dérivé de ses participations** (`GET /moi/participations`
/// → `site` embarqué, #728/#718). Un observateur *participe* à des sites régionaux dont il n'est pas le
/// propriétaire : ces sites n'apparaissent pas dans `/moi/sites` (qui filtre sur le propriétaire), d'où
/// la source « participations ».
///
/// Sert au **rapprochement** (id, titre, verrouillé), à l'**import** (numéro de carré, points) et à la
/// **propriété** du carré ([#observateur], #2525).
///
/// @param id identifiant VigieChiro (`_id`, 24 caractères hexadécimaux)
/// @param titre titre du site (ex. `"Vigiechiro - Point Fixe-130711"`), base du rapprochement local
/// @param verrouille `true` si le dépôt est possible : **toujours vrai** pour un site atteint via une
///     participation (créer une participation exige un site verrouillé, cf. #142). ⚠️ **Faux ami** : ce
///     champ ne dit **rien** de la propriété du carré : c'est [#observateur] qui la porte.
/// @param numeroCarre numéro de carré (6 chiffres) extrait du titre, ou `null` si absent
/// @param observateur identifiant plateforme du **propriétaire** du carré (champ `observateur` du site,
///     #2525), ou `null` si absent du JSON. Comparé à l'id du profil connecté, il dit si le carré est
///     celui d'un tiers, donc si les nuits qu'on y réalise sont des participations *opportunistes*
/// @param points localités du site (points d'écoute), éventuellement vide
public record SiteVigieChiro(
        String id,
        String titre,
        boolean verrouille,
        String numeroCarre,
        String observateur,
        List<PointVigieChiro> points) {

    /// Vue minimale (sans carré, propriétaire ni points) : pour le rapprochement/badge, ou les tests qui
    /// n'ont pas besoin du détail d'import.
    public SiteVigieChiro(String id, String titre, boolean verrouille) {
        this(id, titre, verrouille, null, null, List.of());
    }

    /// Le carré appartient-il à **quelqu'un d'autre** que l'observateur `idProfilConnecte` ? Faux si
    /// l'un des deux identifiants est inconnu : sans preuve du contraire, on ne présume pas un tiers.
    public boolean appartientAUnTiers(String idProfilConnecte) {
        return observateur != null && idProfilConnecte != null && !observateur.equals(idProfilConnecte);
    }
}
