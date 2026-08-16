package fr.univ_amu.iut.commun.model;

/// Identité d'un point d'écoute exposée par la couche `commun`, pour les features qui **ne peuvent pas
/// dépendre de `sites`** (acyclicité ArchUnit) mais doivent rattacher un point à sa participation VigieChiro :
/// son **code** de localité (champ `point` de l'API, ex. `Z41`) et l'**id du site** local (pour retrouver
/// l'objectid VigieChiro du site via `LienVigieChiro.ENTITE_SITE`). Transporté par le port [ReferentielPoint]
/// (pendant de [PositionGeo] pour [CoordonneesPoint]).
///
/// Depuis #3854, l'identité porte aussi le **numéro de carré**. Il ne sert pas à déposer - le dépôt passe
/// par l'objectid du lien - mais à **conseiller** quand ce lien manque : sans le numéro, le refus ne peut
/// pas demander à la plateforme si ce carré y existe, et retombe sur un conseil générique.
///
/// @param code code de la localité (`PointDEcoute.code`)
/// @param idSite id du site local auquel le point appartient
/// @param numeroCarre numéro du carré de ce site, ou `null` si l'implémentation ne le connaît pas
public record InfosPoint(String code, Long idSite, String numeroCarre) {

    /// Identité sans le carré : pour les appelants qui n'ont besoin que de déposer.
    public InfosPoint(String code, Long idSite) {
        this(code, idSite, null);
    }
}
