package fr.univ_amu.iut.commun.model;

/// Coordonnées géographiques (WGS84) d'un lieu, exprimées en degrés décimaux.
///
/// Type-valeur du socle `commun`, utilisé par le port [CoordonneesPoint] pour transporter la
/// localisation d'un point d'écoute vers les features qui en ont besoin (ex. `passage`, pour le
/// pré-remplissage météo) sans les faire dépendre de la feature `sites` qui détient la donnée.
///
/// @param latitude latitude en degrés décimaux (positif vers le nord)
/// @param longitude longitude en degrés décimaux (positif vers l'est)
public record PositionGeo(double latitude, double longitude) {}
