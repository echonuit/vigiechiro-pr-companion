package fr.univ_amu.iut.commun.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import fr.univ_amu.iut.commun.model.Certitude;
import java.util.List;
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
    /// `point`** : la localité identifie la participation, elle ne se modifie pas depuis l'app. La
    /// concurrence est gérée côté client par l'en-tête `If-Match` (l'`_etag`), pas par le corps.
    static String miseAJourParticipation(ParticipationADeposer participation) {
        JsonObject corps = GSON.toJsonTree(participation).getAsJsonObject();
        corps.remove("point");
        return GSON.toJson(corps);
    }

    /// Corps de `POST /fichiers` (déclaration d'un fichier, upload simple). Le **mime n'est pas envoyé** :
    /// l'API le déduit de l'extension du titre ; il est fourni ensuite au `PUT` S3 (`Content-Type`).
    ///
    /// `lien_participation` est **indispensable** (#984) : sans lui, le fichier monte bien sur S3 mais reste
    /// **orphelin** (rattaché à aucune participation), et le traitement serveur (`compute`) « n'extrait 0
    /// fichier ». C'est le champ que pose le front web à chaque déclaration.
    static String fichier(String titre, String lienParticipation) {
        return fichier(titre, lienParticipation, false);
    }

    /// Variante avec le drapeau `multipart` explicite (#2354) : à `true`, l'API ouvre un **upload
    /// multipart** (la réponse ne porte plus d'URL unique ; les URL viennent partie par partie via
    /// [#demandePartie]) ; à `false`, l'upload simple d'origine (URL unique dans la réponse).
    static String fichier(String titre, String lienParticipation, boolean multipart) {
        return GSON.toJson(Map.of("titre", titre, "multipart", multipart, "lien_participation", lienParticipation));
    }

    /// Corps de `PUT /fichiers/#id/multipart` (#2354) : demande l'URL S3 signée de la **partie**
    /// `partNumber`. Une URL par partie, obtenue juste avant de la téléverser.
    static String demandePartie(int partNumber) {
        return GSON.toJson(Map.of("part_number", partNumber));
    }

    /// Corps de finalisation `POST /fichiers/#id` : aucun champ requis (accusé de fin d'upload).
    static String finalisation() {
        return CORPS_VIDE;
    }

    /// Corps de finalisation d'un **multipart** (#2354) : la liste ordonnée des parties déposées
    /// (`{parts: [{part_number, etag}, …]}`), pour que S3 recolle l'objet.
    static String finalisationMultipart(List<PartieDeposee> parties) {
        return GSON.toJson(Map.of("parts", parties));
    }

    /// Corps de `PATCH /donnees/#id/observations/#index` (#723, contrat #1203) : le taxon observateur en
    /// **objectid** (mapping `vigiechiro_link`) et la certitude en jeton `SUR|PROBABLE|POSSIBLE`. Les deux
    /// champs vont toujours ensemble : le serveur exige la probabilité dès que le taxon est envoyé
    /// (`422` sinon).
    static String correction(String objectidTaxon, Certitude certitude) {
        return GSON.toJson(Map.of("observateur_taxon", objectidTaxon, "observateur_probabilite", certitude.jeton()));
    }

    /// Corps de `PUT /donnees/#id/observations/#indice/messages` (#1418) : un seul champ, `message`.
    ///
    /// Le serveur **refuse (422)** tout ce qui n'est pas une chaîne, et **ajoute** le message par `$push` :
    /// il n'y a ni remplacement, ni route de suppression. Ce corps est donc celui d'une écriture
    /// **définitive** : l'appelant doit l'avoir fait confirmer.
    static String message(String texte) {
        return GSON.toJson(Map.of("message", texte));
    }

    /// Corps de `POST /participations/#id/compute` (lancement du traitement, #984) : aucun champ requis,
    /// le serveur déclenche le pipeline sur les fichiers déjà déposés.
    static String traitement() {
        return CORPS_VIDE;
    }

    /// Corps JSON vide partagé par les écritures « accusé » sans champ (finalisation, lancement).
    private static final String CORPS_VIDE = "{}";
}
