package fr.univ_amu.iut.commun.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Ce qu'on lit d'un **site** VigieChiro, quelle que soit la porte par laquelle il arrive : son numéro
/// de carré, et ses points d'écoute.
///
/// Deux ressources rendent des sites - `/moi/participations` (site embarqué dans chaque participation)
/// et `/sites` (le catalogue). Elles n'ont ni la même enveloppe ni les mêmes règles de verrouillage,
/// mais elles décrivent le **même** objet. Les deux pièges de cette lecture vivent donc ici, une seule
/// fois : le carré se devine du titre, et les coordonnées sont dans l'ordre **[lat, lon]**.
final class LocalitesVigieChiro {

    /// Numéro de carré : **six chiffres isolés** dans le titre du site (`Vigiechiro - Point Fixe-130711`).
    /// La négation autour évite d'attraper six chiffres pris dans un nombre plus long.
    private static final Pattern CARRE = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");

    private LocalitesVigieChiro() {}

    /// Numéro de carré déduit du titre, `null` si le titre n'en porte pas.
    static String carreDepuisTitre(String titre) {
        if (titre == null) {
            return null;
        }
        Matcher chiffres = CARRE.matcher(titre);
        return chiffres.find() ? chiffres.group() : null;
    }

    /// Points d'écoute d'un site à partir de ses `localites` (nom + coordonnées). Localités malformées
    /// ignorées : un point sans nom ou sans position ne se place ni sur une carte ni dans un filtre.
    static List<PointVigieChiro> lirePoints(JsonObject site) {
        List<PointVigieChiro> points = new ArrayList<>();
        JsonElement localites = site.get("localites");
        if (localites == null || !localites.isJsonArray()) {
            return points;
        }
        for (JsonElement element : localites.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            PointVigieChiro point = lireUnPoint(element.getAsJsonObject());
            if (point != null) {
                points.add(point);
            }
        }
        return points;
    }

    /// Une localité en [PointVigieChiro], ou `null` si elle n'a ni nom lisible ni position lisible.
    ///
    /// Extrait de [#lirePoints] pour que la **publication** puisse relire la position d'une localité
    /// homonyme (#3458) : le nom seul ne dit pas que c'est le même point.
    static PointVigieChiro lireUnPoint(JsonObject localite) {
        String nom = ReponsesVigieChiro.texte(localite, "nom");
        double[] coord = coordonnees(localite);
        return nom != null && coord != null ? new PointVigieChiro(nom, coord[0], coord[1]) : null;
    }

    /// Coordonnées `[latitude, longitude]` d'une localité (`geometries.geometries[0].coordinates`).
    /// VigieChiro stocke l'ordre **[lat, lon]** (et non le [lon, lat] GeoJSON). Malformé → `null`.
    private static double[] coordonnees(JsonObject localite) {
        try {
            JsonArray geometries = localite.getAsJsonObject("geometries").getAsJsonArray("geometries");
            JsonArray coordonnees = geometries.get(0).getAsJsonObject().getAsJsonArray("coordinates");
            return new double[] {
                coordonnees.get(0).getAsDouble(), coordonnees.get(1).getAsDouble()
            };
        } catch (RuntimeException malforme) {
            return null;
        }
    }
}
