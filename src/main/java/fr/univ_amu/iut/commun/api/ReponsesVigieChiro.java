package fr.univ_amu.iut.commun.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Lecture des réponses JSON de l'API VigieChiro (backend Eve) vers les records du paquet `commun.api`.
///
/// Séparé de [ClientVigieChiro] (qui ne porte que le **transport HTTP**) : ces méthodes sont **pures**
/// (`String` → record), **tolérantes** (JSON illisible / champ absent → vide, jamais d'exception) et
/// testables sur des réponses figées, sans réseau.
final class ReponsesVigieChiro {

    /// Clé de l'identifiant MongoDB, commune à tous les documents Eve (`_id`).
    private static final String CLE_ID = "_id";

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

    /// Identifiant du document **créé** par une écriture Eve (`POST` renvoyant le document), ou vide si le
    /// corps est illisible ou sans `_id`. Sert à récupérer l'id d'une participation créée (#142).
    static Optional<String> idCree(String corps) {
        try {
            return Optional.ofNullable(texte(JsonParser.parseString(corps).getAsJsonObject(), CLE_ID));
        } catch (RuntimeException illisible) {
            return Optional.empty();
        }
    }

    /// Fichier signé depuis `POST /fichiers` (#142) : `_id` + `s3_signed_url` (URL S3 pré-signée pour le
    /// `PUT`). Vide si l'un des deux manque ou si le corps est illisible.
    static Optional<FichierSigne> fichierSigne(String corps) {
        try {
            JsonObject objet = JsonParser.parseString(corps).getAsJsonObject();
            String id = texte(objet, CLE_ID);
            String url = texte(objet, "s3_signed_url");
            return id != null && url != null ? Optional.of(new FichierSigne(id, url)) : Optional.empty();
        } catch (RuntimeException illisible) {
            return Optional.empty();
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
