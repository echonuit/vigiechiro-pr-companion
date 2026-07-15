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

    /// Nombre **total** d'éléments annoncé par une collection paginée Eve (`_meta.total`), ou `0` si le
    /// champ est absent ou le corps illisible. Permet de connaître d'avance le nombre de pages pour une
    /// progression déterminée (#1534). Tolérant, comme le reste de ce lecteur.
    static int total(String corps) {
        try {
            JsonObject meta = JsonParser.parseString(corps).getAsJsonObject().getAsJsonObject("_meta");
            return meta != null && meta.has("total") ? meta.get("total").getAsInt() : 0;
        } catch (RuntimeException illisible) {
            return 0;
        }
    }

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

    /// Numéro du carré STOC **le plus proche** depuis `GET /grille_stoc/cercle` (#733) : le serveur
    /// interroge la grille avec un `$near`, dont MongoDB rend les résultats **triés par distance
    /// croissante**. Le premier élément est donc le carré de la position demandée ; les suivants sont ses
    /// voisins, dont nous n'avons que faire.
    ///
    /// On ne lit **que** le `numero`, jamais le `centre` : la plateforme mélange les conventions de
    /// coordonnées (les localités d'un site sont stockées `[lat, lon]`, à rebours du GeoJSON, cf.
    /// `ParticipationsVigieChiro#coordonnees`), et rien n'oblige à trancher ce débat pour lire un numéro.
    ///
    /// Aucun carré (mer, hors de France) ou corps illisible → vide : ce n'est pas une erreur, c'est une
    /// réponse.
    static Optional<String> numeroCarreStoc(String corps) {
        for (JsonElement element : items(corps)) {
            if (element.isJsonObject()) {
                String numero = texte(element.getAsJsonObject(), "numero");
                if (numero != null && !numero.isBlank()) {
                    return Optional.of(numero);
                }
            }
        }
        return Optional.empty();
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
