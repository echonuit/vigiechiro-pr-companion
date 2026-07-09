package fr.univ_amu.iut.commun.api;

/// Localité (point d'écoute) d'un site **VigieChiro** (#718, axe 3), extraite des `localites` d'un site.
///
/// @param code nom de la localité (ex. `"Z41"`), réutilisé comme code de point local
/// @param latitude latitude WGS84 (degrés décimaux)
/// @param longitude longitude WGS84 (degrés décimaux)
///
/// ⚠️ Les coordonnées VigieChiro sont stockées dans l'ordre **`[latitude, longitude]`** (et non le
/// `[longitude, latitude]` du standard GeoJSON) : le parsing lit `coordinates[0]` = latitude.
public record PointVigieChiro(String code, double latitude, double longitude) {}
