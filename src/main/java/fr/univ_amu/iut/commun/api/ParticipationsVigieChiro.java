package fr.univ_amu.iut.commun.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Lecture des réponses de `GET /moi/participations` (extrait de [ReponsesVigieChiro] pour ne pas l'enfler
/// en God Class, comme [DonneesVigieChiro] l'a été pour `/donnees`). Deux projections d'un même corps :
/// les **sites** rattachés (dérivés des participations, #718) et les **participations** elles-mêmes (axe
/// 4.2, rattachement manuel). Fonctions pures et tolérantes ; réutilise les helpers `items` / `texte` de
/// [ReponsesVigieChiro].
final class ParticipationsVigieChiro {

    /// Clé de l'identifiant MongoDB (`_id`).
    private static final String CLE_ID = "_id";

    /// Numéro de carré = **6 chiffres isolés** dans le titre du site (ex. « …-130711 »).
    private static final Pattern CARRE = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");

    private ParticipationsVigieChiro() {}

    /// Sites (dédupliqués par `_id`) : chaque participation embarque son `site`. Un site atteint par une
    /// participation est **verrouillé** (le dépôt exige un site verrouillé, #142) ; le numéro de carré est
    /// extrait du titre, les points des `localites`. Tolérant.
    static List<SiteVigieChiro> sites(String corps) {
        Map<String, SiteVigieChiro> parId = new LinkedHashMap<>();
        for (JsonElement element : ReponsesVigieChiro.items(corps)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonElement siteBrut = element.getAsJsonObject().get("site");
            if (siteBrut == null || !siteBrut.isJsonObject()) {
                continue;
            }
            JsonObject site = siteBrut.getAsJsonObject();
            String id = ReponsesVigieChiro.texte(site, CLE_ID);
            if (id == null || parId.containsKey(id)) {
                continue;
            }
            String titre = ReponsesVigieChiro.texte(site, "titre");
            parId.put(id, new SiteVigieChiro(id, titre, true, carreDepuisTitre(titre), lirePoints(site)));
        }
        return List.copyOf(parId.values());
    }

    /// Participations : `_id` + localité + date + titre du site, pour le **rattachement manuel** d'un
    /// passage à une participation existante. Élément sans `_id` ignoré ; corps illisible → liste vide.
    static List<ParticipationVigieChiro> participations(String corps) {
        List<ParticipationVigieChiro> participations = new ArrayList<>();
        for (JsonElement element : ReponsesVigieChiro.items(corps)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject objet = element.getAsJsonObject();
            String id = ReponsesVigieChiro.texte(objet, CLE_ID);
            if (id == null) {
                continue;
            }
            JsonElement site = objet.get("site");
            String siteTitre = site != null && site.isJsonObject()
                    ? ReponsesVigieChiro.texte(site.getAsJsonObject(), "titre")
                    : null;
            participations.add(new ParticipationVigieChiro(
                    id,
                    ReponsesVigieChiro.texte(objet, "point"),
                    ReponsesVigieChiro.texte(objet, "date_debut"),
                    siteTitre));
        }
        return participations;
    }

    /// Numéro de carré (6 chiffres isolés) extrait du titre du site, ou `null` s'il n'y en a pas.
    private static String carreDepuisTitre(String titre) {
        if (titre == null) {
            return null;
        }
        Matcher chiffres = CARRE.matcher(titre);
        return chiffres.find() ? chiffres.group() : null;
    }

    /// Points d'écoute d'un site à partir de ses `localites` (nom + coordonnées). Localités malformées
    /// ignorées.
    private static List<PointVigieChiro> lirePoints(JsonObject site) {
        List<PointVigieChiro> points = new ArrayList<>();
        JsonElement localites = site.get("localites");
        if (localites == null || !localites.isJsonArray()) {
            return points;
        }
        for (JsonElement element : localites.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject localite = element.getAsJsonObject();
            String nom = ReponsesVigieChiro.texte(localite, "nom");
            double[] coord = coordonnees(localite);
            if (nom != null && coord != null) {
                points.add(new PointVigieChiro(nom, coord[0], coord[1]));
            }
        }
        return points;
    }

    /// Coordonnées `[latitude, longitude]` d'une localité (`geometries.geometries[0].coordinates`).
    /// ⚠️ VigieChiro stocke l'ordre **[lat, lon]** (et non le [lon, lat] GeoJSON). Malformé → `null`.
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
