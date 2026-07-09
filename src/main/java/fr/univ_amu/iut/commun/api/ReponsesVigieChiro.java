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

/// Lecture des réponses JSON de l'API VigieChiro (backend Eve) vers les records du paquet `commun.api`.
///
/// Séparé de [ClientVigieChiro] (qui ne porte que le **transport HTTP**) : ces méthodes sont **pures**
/// (`String` → record), **tolérantes** (JSON illisible / champ absent → vide, jamais d'exception) et
/// testables sur des réponses figées, sans réseau.
final class ReponsesVigieChiro {

    /// Clé de l'identifiant MongoDB, commune à tous les documents Eve (`_id`).
    private static final String CLE_ID = "_id";

    /// Numéro de carré = **6 chiffres isolés** dans le titre du site (ex. « …-130711 »).
    private static final Pattern CARRE = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");

    private ReponsesVigieChiro() {}

    /// Profil depuis `GET /moi` : vide si JSON illisible ou sans `_id`.
    static Optional<ProfilVigieChiro> profil(String corps) {
        try {
            JsonObject objet = JsonParser.parseString(corps).getAsJsonObject();
            String id = texte(objet, CLE_ID);
            if (id == null) {
                return Optional.empty();
            }
            return Optional.of(new ProfilVigieChiro(id, texte(objet, "pseudo"), texte(objet, "role")));
        } catch (RuntimeException illisible) {
            return Optional.empty();
        }
    }

    /// Taxons depuis `GET /taxons/liste` : éléments sans `_id`/`libelle_court` ignorés, illisible → vide.
    static List<TaxonVigieChiro> taxons(String corps) {
        List<TaxonVigieChiro> taxons = new ArrayList<>();
        for (JsonElement element : items(corps)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject objet = element.getAsJsonObject();
            String id = texte(objet, CLE_ID);
            String court = texte(objet, "libelle_court");
            if (id != null && court != null) {
                taxons.add(new TaxonVigieChiro(id, court, texte(objet, "libelle_long")));
            }
        }
        return taxons;
    }

    /// Sites (dédupliqués par `_id`) depuis `GET /moi/participations` : chaque participation embarque son
    /// `site`. Un site atteint par une participation est **verrouillé** (le dépôt exige un site
    /// verrouillé, #142) ; le numéro de carré est extrait du titre, les points des `localites`. Tolérant.
    static List<SiteVigieChiro> sitesDepuisParticipations(String corps) {
        Map<String, SiteVigieChiro> parId = new LinkedHashMap<>();
        for (JsonElement element : items(corps)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonElement siteBrut = element.getAsJsonObject().get("site");
            if (siteBrut == null || !siteBrut.isJsonObject()) {
                continue;
            }
            JsonObject site = siteBrut.getAsJsonObject();
            String id = texte(site, CLE_ID);
            if (id == null || parId.containsKey(id)) {
                continue;
            }
            String titre = texte(site, "titre");
            parId.put(id, new SiteVigieChiro(id, titre, true, carreDepuisTitre(titre), lirePoints(site)));
        }
        return List.copyOf(parId.values());
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
            String nom = texte(localite, "nom");
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

    /// Éléments d'une réponse de liste Eve : le tableau `_items` (réponses paginées) ou le corps
    /// lui-même s'il est déjà un tableau JSON. Corps illisible / forme inattendue → tableau vide.
    /// Package-visible : partagé par les autres lecteurs du paquet (ex. [DonneesVigieChiro]).
    static JsonArray items(String corps) {
        try {
            JsonElement racine = JsonParser.parseString(corps);
            if (racine.isJsonArray()) {
                return racine.getAsJsonArray();
            }
            if (racine.isJsonObject()) {
                JsonElement items = racine.getAsJsonObject().get("_items");
                if (items != null && items.isJsonArray()) {
                    return items.getAsJsonArray();
                }
            }
        } catch (RuntimeException illisible) {
            // corps non-JSON : on retombe sur un tableau vide (dégradation propre).
        }
        return new JsonArray();
    }

    /// Valeur texte de la clé `cle`, ou `null` si absente / nulle. Package-visible : partagée par les
    /// autres lecteurs du paquet (ex. [DonneesVigieChiro]).
    static String texte(JsonObject objet, String cle) {
        JsonElement element = objet.get(cle);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }
}
