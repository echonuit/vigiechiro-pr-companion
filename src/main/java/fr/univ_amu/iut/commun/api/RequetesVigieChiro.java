package fr.univ_amu.iut.commun.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.util.Map;

/// Construction des **corps JSON des écritures** VigieChiro (#142) : pendant en écriture de
/// [ReponsesVigieChiro]. Fonctions **pures** (`record` / paramètres → `String`), testables sans réseau.
///
/// Gson en `snake_case` (`dateDebut` → `date_debut`, …) et **champs `null` omis** (défaut Gson) : un
/// `numero` ou `commentaire` absent ne pollue pas le corps envoyé au backend Eve.
final class RequetesVigieChiro {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private RequetesVigieChiro() {}

    /// Corps de `POST /sites/#id/participations` (création de participation).
    static String participation(ParticipationADeposer participation) {
        return GSON.toJson(participation);
    }

    /// Corps d'une **mise à jour partielle** de participation (`PATCH /participations/#id`) : on n'émet que
    /// les métadonnées **synchronisables** (dates, météo, configuration ; champs `null` omis) et on **retire
    /// `point`** — la localité identifie la participation, elle ne se modifie pas depuis l'app. La
    /// concurrence est gérée côté client par l'en-tête `If-Match` (l'`_etag`), pas par le corps.
    static String miseAJourParticipation(ParticipationADeposer participation) {
        JsonObject corps = GSON.toJsonTree(participation).getAsJsonObject();
        corps.remove("point");
        return GSON.toJson(corps);
    }

    /// Corps de `POST /fichiers` (déclaration d'un fichier, upload simple). Le **mime n'est pas envoyé** :
    /// l'API le déduit de l'extension du titre ; il est fourni ensuite au `PUT` S3 (`Content-Type`).
    static String fichier(String titre) {
        return GSON.toJson(Map.of("titre", titre, "multipart", false));
    }

    /// Corps de finalisation `POST /fichiers/#id` : aucun champ requis (accusé de fin d'upload).
    static String finalisation() {
        return CORPS_VIDE;
    }

    /// Corps de `POST /participations/#id/compute` (lancement du traitement, #984) : aucun champ requis,
    /// le serveur déclenche le pipeline sur les fichiers déjà déposés.
    static String traitement() {
        return CORPS_VIDE;
    }

    /// Corps JSON vide partagé par les écritures « accusé » sans champ (finalisation, lancement).
    private static final String CORPS_VIDE = "{}";
}
