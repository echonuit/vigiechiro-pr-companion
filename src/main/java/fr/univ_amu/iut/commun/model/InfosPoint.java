package fr.univ_amu.iut.commun.model;

/// Identité d'un point d'écoute exposée par la couche `commun`, pour les features qui **ne peuvent pas
/// dépendre de `sites`** (acyclicité ArchUnit) mais doivent rattacher un point à sa participation VigieChiro :
/// son **code** de localité (champ `point` de l'API, ex. `Z41`) et l'**id du site** local (pour retrouver
/// l'objectid VigieChiro du site via `LienVigieChiro.ENTITE_SITE`). Transporté par le port [ReferentielPoint]
/// (pendant de [PositionGeo] pour [CoordonneesPoint]).
///
/// @param code code de la localité (`PointDEcoute.code`)
/// @param idSite id du site local auquel le point appartient
public record InfosPoint(String code, Long idSite) {}
