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
    /// On ne lit **que** le `numero`, jamais le `centre` : le numéro suffit, et rien n'oblige à lire le
    /// reste pour l'obtenir.
    ///
    /// Ce fut d'abord une abstention faute de savoir : la plateforme mélange les conventions, les
    /// localités d'un site étant stockées `[lat, lon]` à rebours du GeoJSON (cf.
    /// `ParticipationsVigieChiro#coordonnees`). La mesure du 2026-08-26 a tranché pour cet endpoint
    /// (#4576). Interrogée à `lat=44.4467, lng=6.2981`, la grille rend un centre de type `Point` dont
    /// les `coordinates` valent `[6.293767361, 44.44544392]` : la longitude d'abord, donc l'ordre
    /// **`[lon, lat]`**, celui de GeoJSON. L'autre lecture placerait ce centre à 5 626 km.
    ///
    /// L'abstention reste, elle est désormais un **choix** et non une ignorance.
    ///
    /// Aucun carré (mer, hors de France) ou corps illisible → vide : ce n'est pas une erreur, c'est une
    /// réponse.
    static Optional<String> numeroCarreStoc(String corps) {
        for (JsonElement element : items(corps)) {
            if (element.isJsonObject()) {
                String numero = texte(element.getAsJsonObject(), "numero");
                if (numero != null && !numero.isBlank()) {
                    return Optional.of(surSixChiffres(numero));
                }
            }
        }
        return Optional.empty();
    }

    /// Le numéro de carré **sur six chiffres**, département en tête, forme qu'impose la règle métier R1 et
    /// que respecte le catalogue des sites.
    ///
    /// La grille, elle, ampute le zéro de gauche des départements 01 à 09. Mesuré le 2026-08-26 sur la
    /// position `44.44674980384396, 6.298116860416506` (#4576) : `/grille_stoc/cercle` rend `40110` quand
    /// `GET /sites?q=040110` trouve « Vigiechiro - Point Fixe-040110 » et que `GET /sites?q=40110` ne
    /// trouve rien.
    ///
    /// Le rembourrage vit **ici**, au point unique où ce numéro entre dans l'application, et non chez
    /// celui qui compare : réparé là-bas, le défaut resterait entier pour le lecteur suivant.
    ///
    /// Un numéro plus long que six chiffres passe **tel quel**. Ce n'est pas à cette lecture d'inventer ce
    /// qu'il faudrait en faire.
    ///
    /// Écrit sans conditionnelle à dessein. La forme `length() >= 6 ? numero : ...` portait un mutant
    /// **équivalent** : muter la borne en `> 6` rend `"0".repeat(0) + numero`, soit le même résultat, et
    /// aucun test ne pouvait le tuer. Le `Math.max` dit la même chose sans borne à muter, et protège du
    /// `repeat` négatif qu'un numéro plus long provoquerait.
    private static String surSixChiffres(String numero) {
        return "0".repeat(Math.max(0, 6 - numero.length())) + numero;
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
