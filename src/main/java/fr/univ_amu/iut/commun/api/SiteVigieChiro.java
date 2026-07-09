package fr.univ_amu.iut.commun.api;

import java.util.List;

/// Site VigieChiro rattaché à l'observateur, **dérivé de ses participations** (`GET /moi/participations`
/// → `site` embarqué, #728/#718). Un observateur *participe* à des sites régionaux dont il n'est pas le
/// propriétaire : ces sites n'apparaissent pas dans `/moi/sites` (qui filtre sur le propriétaire), d'où
/// la source « participations ».
///
/// Sert au **rapprochement** (id, titre, verrouillé) et à l'**import** (numéro de carré, points).
///
/// @param id identifiant VigieChiro (`_id`, 24 caractères hexadécimaux)
/// @param titre titre du site (ex. `"Vigiechiro - Point Fixe-130711"`), base du rapprochement local
/// @param verrouille `true` si le dépôt est possible : **toujours vrai** pour un site atteint via une
///     participation (créer une participation exige un site verrouillé, cf. #142)
/// @param numeroCarre numéro de carré (6 chiffres) extrait du titre, ou `null` si absent
/// @param points localités du site (points d'écoute), éventuellement vide
public record SiteVigieChiro(
        String id, String titre, boolean verrouille, String numeroCarre, List<PointVigieChiro> points) {

    /// Vue minimale (sans carré ni points) : pour le rapprochement/badge, ou les tests qui n'ont pas
    /// besoin du détail d'import.
    public SiteVigieChiro(String id, String titre, boolean verrouille) {
        this(id, titre, verrouille, null, List.of());
    }
}
