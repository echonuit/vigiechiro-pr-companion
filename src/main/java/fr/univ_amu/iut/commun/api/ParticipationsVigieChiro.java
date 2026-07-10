package fr.univ_amu.iut.commun.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /// Vue **détaillée** d'une participation depuis `GET /participations/#id` (axe 4) : `_id` + `_etag`
    /// (pour un PATCH `If-Match`) + dates + météo + configuration + état du traitement Tadarida. Vide si le
    /// corps est illisible ou sans `_id`. Tolérant.
    static Optional<ParticipationDetail> detail(String corps) {
        try {
            JsonObject objet = JsonParser.parseString(corps).getAsJsonObject();
            String id = ReponsesVigieChiro.texte(objet, CLE_ID);
            if (id == null) {
                return Optional.empty();
            }
            return Optional.of(new ParticipationDetail(
                    id,
                    ReponsesVigieChiro.texte(objet, "_etag"),
                    ReponsesVigieChiro.texte(objet, "point"),
                    ReponsesVigieChiro.texte(objet, "date_debut"),
                    ReponsesVigieChiro.texte(objet, "date_fin"),
                    meteo(objet),
                    configuration(objet),
                    etatTraitement(objet)));
        } catch (RuntimeException illisible) {
            return Optional.empty();
        }
    }

    /// Bloc météo (`meteo.vent` / `meteo.couverture`), ou `null` si le sous-objet est absent.
    private static MeteoDepot meteo(JsonObject participation) {
        JsonElement brut = participation.get("meteo");
        if (brut == null || !brut.isJsonObject()) {
            return null;
        }
        JsonObject meteo = brut.getAsJsonObject();
        return new MeteoDepot(ReponsesVigieChiro.texte(meteo, "vent"), ReponsesVigieChiro.texte(meteo, "couverture"));
    }

    /// Dictionnaire `configuration` (clés → valeurs texte), jamais `null` (vide si absent). Les valeurs
    /// non primitives sont ignorées (tolérant).
    private static Map<String, String> configuration(JsonObject participation) {
        Map<String, String> config = new LinkedHashMap<>();
        JsonElement brut = participation.get("configuration");
        if (brut != null && brut.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entree : brut.getAsJsonObject().entrySet()) {
                if (entree.getValue().isJsonPrimitive()) {
                    config.put(entree.getKey(), entree.getValue().getAsString());
                }
            }
        }
        return config;
    }

    /// État du traitement Tadarida (`traitement.etat`, ex. `FINI`), ou `null`.
    private static String etatTraitement(JsonObject participation) {
        JsonElement brut = participation.get("traitement");
        return brut != null && brut.isJsonObject() ? ReponsesVigieChiro.texte(brut.getAsJsonObject(), "etat") : null;
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
