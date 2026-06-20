package fr.univ_amu.iut.sites.viewmodel;

/// Construit l'URL OpenStreetMap d'un point GPS. Fonction **pure** (aucune dépendance IHM),
/// donc directement testable.
///
/// L'URL centre la carte sur le point et y pose un marqueur (`mlat`/`mlon`). Le séparateur
/// décimal est toujours le point (`Double.toString`), indépendamment de la locale système.
public final class LienCarte {

    private LienCarte() {}

    /// Niveau de zoom OSM par défaut (échelle « localité », adapté à un point d'écoute).
    private static final int ZOOM = 16;

    /// URL OpenStreetMap centrée sur `(latitude, longitude)`, marqueur inclus.
    public static String osm(double latitude, double longitude) {
        return "https://www.openstreetmap.org/?mlat=" + latitude + "&mlon=" + longitude + "#map=" + ZOOM + "/"
                + latitude + "/" + longitude;
    }
}
