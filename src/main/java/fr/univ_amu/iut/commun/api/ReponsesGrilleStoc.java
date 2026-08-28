package fr.univ_amu.iut.commun.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.univ_amu.iut.commun.model.CarreCandidat;
import fr.univ_amu.iut.commun.model.ConversionGeographique;
import fr.univ_amu.iut.commun.model.NumeroDeCarre;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// Lecture des réponses de `GET /grille_stoc/cercle` **avec leur géométrie** (#4610).
///
/// Séparée de [ReponsesVigieChiro], qui lit les mêmes corps sans jamais ouvrir le `centre` : là-bas le
/// numéro suffit. Ici il ne suffit plus, et la classe voisine était déjà à la limite que le cliquet 4617
/// surveille. Une lecture qui a besoin de la géométrie a assez de matière pour vivre à part.
final class ReponsesGrilleStoc {

    private static final String CENTRE = "centre";

    private static final String COORDONNEES = "coordinates";

    private ReponsesGrilleStoc() {}

    /// Tous les carrés que la grille rend pour une position, **avec leur distance** à cette position.
    ///
    /// Le `centre` est un Point GeoJSON, donc **`[lon, lat]`** (#4576, `dev-docs/api-vigiechiro.md`).
    ///
    /// L'ordre du serveur n'est **pas** repris : son `$near` ne garantit rien à distance égale, et c'est
    /// le cas qui nous occupe - deux mailles à 997,7 m chacune au milieu d'un côté, mesuré le
    /// 2026-08-27. Le tri est le nôtre, par numéro à égalité, donc **stable**. Un élément sans numéro ou
    /// sans centre lisible est écarté.
    static List<CarreCandidat> carresProches(String corps, double latitude, double longitude) {
        List<CarreCandidat> candidats = new ArrayList<>();
        for (JsonElement element : ReponsesVigieChiro.items(corps)) {
            if (element.isJsonObject()) {
                ajouterSiLisible(candidats, element.getAsJsonObject(), latitude, longitude);
            }
        }
        candidats.sort(Comparator.comparingDouble(CarreCandidat::distanceMetres).thenComparing(CarreCandidat::numero));
        return List.copyOf(candidats);
    }

    private static void ajouterSiLisible(
            List<CarreCandidat> candidats, JsonObject objet, double latitude, double longitude) {
        String numero = ReponsesVigieChiro.texte(objet, "numero");
        double[] centre = centreGeoJson(objet);
        if (numero != null && !numero.isBlank() && centre != null) {
            candidats.add(new CarreCandidat(
                    NumeroDeCarre.surSixChiffres(numero),
                    ConversionGeographique.distanceMetres(latitude, longitude, centre[0], centre[1])));
        }
    }

    /// Latitude et longitude du `centre`, ou `null` si le champ manque ou n'a pas la forme attendue.
    private static double[] centreGeoJson(JsonObject objet) {
        if (!objet.has(CENTRE) || !objet.get(CENTRE).isJsonObject()) {
            return null;
        }
        JsonElement coordonnees = objet.getAsJsonObject(CENTRE).get(COORDONNEES);
        if (coordonnees == null
                || !coordonnees.isJsonArray()
                || coordonnees.getAsJsonArray().size() < 2) {
            return null;
        }
        try {
            // [lon, lat] : l'ordre GeoJSON, à rebours de celui des localités d'un site.
            return new double[] {
                coordonnees.getAsJsonArray().get(1).getAsDouble(),
                coordonnees.getAsJsonArray().get(0).getAsDouble()
            };
        } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException illisible) {
            return null;
        }
    }
}
