package fr.univ_amu.iut.commun.view.carte;

/// Emprise géographique rectangulaire d'un carré (en degrés WGS84). Bornes sud/ouest (`latMin`/`lonMin`)
/// et nord/est (`latMax`/`lonMax`). Type **pur** (aucune dépendance IHM), donc directement testable.
///
/// L'emprise peut venir de plusieurs sources (cf. [FournisseurEmpriseCarre]) : repli autour des points
/// d'écoute, ou — à terme — le carroyage officiel Vigie-Chiro. Le carré Vigie-Chiro fait 2 km de côté,
/// mais son **numéro n'encode pas de coordonnées** (6 chiffres = département + identifiant local), d'où
/// le recours à un fournisseur d'emprise plutôt qu'à un décodage.
public record EmpriseCarre(double latMin, double lonMin, double latMax, double lonMax) {

    public EmpriseCarre {
        if (latMin > latMax || lonMin > lonMax) {
            throw new IllegalArgumentException("Emprise invalide : min > max (lat " + latMin + ".." + latMax + ", lon "
                    + lonMin + ".." + lonMax + ")");
        }
    }

    /// `true` si le point `(lat, lon)` est dans l'emprise (bornes incluses).
    public boolean contient(double lat, double lon) {
        return lat >= latMin && lat <= latMax && lon >= lonMin && lon <= lonMax;
    }

    /// Latitude du centre de l'emprise.
    public double latCentre() {
        return (latMin + latMax) / 2.0;
    }

    /// Longitude du centre de l'emprise.
    public double lonCentre() {
        return (lonMin + lonMax) / 2.0;
    }
}
