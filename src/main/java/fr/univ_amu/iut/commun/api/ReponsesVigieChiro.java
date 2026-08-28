package fr.univ_amu.iut.commun.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.univ_amu.iut.commun.model.NumeroDeCarre;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Lecture des réponses JSON de l'API VigieChiro (backend Eve) vers les records du paquet `commun.api`.
///
/// Séparé de [ClientVigieChiro] (qui ne porte que le **transport HTTP**) : ces méthodes sont **pures**
/// (`String` → record), **tolérantes** (JSON illisible / champ absent → vide, jamais d'exception) et
/// testables sur des réponses figées, sans réseau.
final class ReponsesVigieChiro {

    private static final Logger LOG = Logger.getLogger(ReponsesVigieChiro.class.getName());

    /// Clé de l'identifiant MongoDB, commune à tous les documents Eve (`_id`).
    private static final String CLE_ID = "_id";
    private static final String CLE_URL_SIGNEE = "s3_signed_url";

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
    /// On ne lit **que** le `numero`, jamais le `centre` : le numéro suffit. Ce fut d'abord une abstention
    /// faute de savoir quelle convention la plateforme emploie là ; #4576 l'a mesurée et
    /// `dev-docs/api-vigiechiro.md` la porte. L'abstention est donc un **choix**.
    ///
    /// Aucun carré (mer, hors de France) ou corps illisible → vide : ce n'est pas une erreur, c'est une
    /// réponse.
    static Optional<String> numeroCarreStoc(String corps) {
        for (JsonElement element : items(corps)) {
            if (element.isJsonObject()) {
                String numero = texte(element.getAsJsonObject(), "numero");
                if (numero != null && !numero.isBlank()) {
                    return Optional.of(NumeroDeCarre.surSixChiffres(numero));
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

    /// Les localités d'un site **non interprétées**, avec l'`_etag` du site (#3458).
    ///
    /// Rien n'est projeté ici, et c'est le point : ces localités seront **renvoyées telles quelles** lors
    /// d'une publication, qui remplace la liste entière. Tout champ qu'on lirait à moitié serait effacé
    /// pour toutes les autres localités du site.
    ///
    /// Vide si l'`_etag` manque : sans lui, on ne saurait pas voir que le site a bougé, et publier
    /// reviendrait à écraser à l'aveugle.
    static Optional<LocalitesDuSite> localitesDuSite(String corps) {
        try {
            JsonObject objet = JsonParser.parseString(corps).getAsJsonObject();
            String etag = texte(objet, "_etag");
            if (etag == null) {
                return Optional.empty();
            }
            JsonElement localites = objet.get("localites");
            JsonArray brutes =
                    localites != null && localites.isJsonArray() ? localites.getAsJsonArray() : new JsonArray();
            return Optional.of(new LocalitesDuSite(etag, brutes));
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
            String url = texte(objet, CLE_URL_SIGNEE);
            return id != null && url != null ? Optional.of(new FichierSigne(id, url)) : Optional.empty();
        } catch (RuntimeException illisible) {
            return Optional.empty();
        }
    }

    /// L'URL S3 signée d'une **partie** multipart (#2354) : la réponse de `PUT /fichiers/{id}/multipart`
    /// ne porte que `s3_signed_url` (pas d'`_id`, le fichier existe déjà).
    static Optional<String> urlDePartie(String corps) {
        try {
            JsonObject objet = JsonParser.parseString(corps).getAsJsonObject();
            return Optional.ofNullable(texte(objet, CLE_URL_SIGNEE));
        } catch (RuntimeException illisible) {
            return Optional.empty();
        }
    }

    /// URL S3 **signée** renvoyée par `GET /fichiers/{id}/acces` (champ `s3_signed_url`), à télécharger
    /// **sans** en-tête d'authentification (la signature de l'URL est l'authentification). Vide si le
    /// champ est absent ou le corps illisible. Générique : le journal (#1132) comme le CSV d'observations
    /// (#1565) et le repli audio (#1244) passent tous par la même route `acces`.
    static Optional<String> urlSignee(String corps) {
        try {
            JsonObject objet = JsonParser.parseString(corps).getAsJsonObject();
            return Optional.ofNullable(texte(objet, CLE_URL_SIGNEE));
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
            LOG.log(Level.WARNING, illisible, () -> "Corps de réponse non-JSON : tableau vide rendu");
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
