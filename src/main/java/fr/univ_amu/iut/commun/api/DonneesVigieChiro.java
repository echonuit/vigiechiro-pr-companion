package fr.univ_amu.iut.commun.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.univ_amu.iut.commun.model.CertitudeObservateur;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/// Lecture des **résultats Tadarida** JSON (`GET /participations/#id/donnees`, #719, axe 4.2) vers les
/// records [DonneeVigieChiro] / [ObservationVigieChiro].
///
/// Séparé de [ReponsesVigieChiro] (identité / taxons / sites) pour garder chaque lecteur cohésif et
/// petit ; réutilise ses helpers bas niveau [ReponsesVigieChiro#items] / [ReponsesVigieChiro#texte].
/// Fonctions **pures** et **tolérantes** (JSON illisible / champ absent → vide, jamais d'exception).
final class DonneesVigieChiro {

    private DonneesVigieChiro() {}

    /// Données (fichiers + observations Tadarida) d'**une page** de la réponse. Tolérant : donnée sans
    /// `titre` ou observation sans taxon Tadarida ignorée ; corps illisible → liste vide. La pagination
    /// (parcours des pages) est gérée par le client.
    static List<DonneeVigieChiro> donnees(String corps) {
        List<DonneeVigieChiro> donnees = new ArrayList<>();
        for (JsonElement element : ReponsesVigieChiro.items(corps)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject donnee = element.getAsJsonObject();
            String titre = ReponsesVigieChiro.texte(donnee, "titre");
            if (titre != null) {
                donnees.add(new DonneeVigieChiro(ReponsesVigieChiro.texte(donnee, "_id"), titre, observations(donnee)));
            }
        }
        return donnees;
    }

    /// Observations (détections) d'une donnée : chaque entrée porte au minimum un taxon Tadarida.
    /// L'**indice brut** du tableau JSON est capturé sur chaque observation (#1139) : c'est
    /// l'identifiant positionnel du `PATCH` serveur (contrat #1203), et il doit rester exact même
    /// quand une entrée inexploitable est ignorée (la position dans la liste résultante ne fait pas
    /// foi). La certitude (`observateur_probabilite`) est un **jeton** `SUR|PROBABLE|POSSIBLE`, lu en
    /// tolérance par [CertitudeObservateur#depuisTexte].
    ///
    /// Depuis #1417, on lit aussi ce que le parseur laissait tomber : l'avis du **validateur**
    /// (`validateur_taxon` / `validateur_probabilite`, mêmes formes que les champs observateur) et le
    /// **fil de discussion** (`messages`). Ces champs arrivaient déjà dans la réponse — il n'y a ni appel
    /// ni route supplémentaires (spike de #724).
    private static List<ObservationVigieChiro> observations(JsonObject donnee) {
        List<ObservationVigieChiro> observations = new ArrayList<>();
        JsonElement brut = donnee.get("observations");
        if (brut == null || !brut.isJsonArray()) {
            return observations;
        }
        JsonArray tableau = brut.getAsJsonArray();
        for (int indice = 0; indice < tableau.size(); indice++) {
            JsonElement element = tableau.get(indice);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obs = element.getAsJsonObject();
            String taxon = codeTaxon(obs, "tadarida_taxon");
            if (taxon == null) {
                continue; // une observation sans taxon Tadarida n'est pas exploitable
            }
            observations.add(new ObservationVigieChiro(
                    indice,
                    taxon,
                    nombre(obs, "tadarida_probabilite"),
                    nombre(obs, "frequence_mediane"),
                    nombre(obs, "temps_debut"),
                    nombre(obs, "temps_fin"),
                    premierTaxonAutre(obs),
                    codeTaxon(obs, "observateur_taxon"),
                    CertitudeObservateur.depuisTexte(ReponsesVigieChiro.texte(obs, "observateur_probabilite")),
                    codeTaxon(obs, "validateur_taxon"),
                    CertitudeObservateur.depuisTexte(ReponsesVigieChiro.texte(obs, "validateur_probabilite")),
                    messages(obs)));
        }
        return observations;
    }

    /// **Fil de discussion** d'une observation (`messages`, #1417) : sous-document déjà présent dans la
    /// charge utile, dans l'ordre du serveur (l'ajout se fait par `$push`, donc l'ordre du tableau est
    /// l'ordre chronologique). Une entrée **sans texte** n'est pas un message : elle est ignorée. L'auteur
    /// est un identifiant plateforme, jamais un nom (cf. [MessageVigieChiro]).
    private static List<MessageVigieChiro> messages(JsonObject obs) {
        List<MessageVigieChiro> fil = new ArrayList<>();
        JsonElement brut = obs.get("messages");
        if (brut == null || !brut.isJsonArray()) {
            return fil;
        }
        for (JsonElement element : brut.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject message = element.getAsJsonObject();
            String texte = ReponsesVigieChiro.texte(message, "message");
            if (texte != null) {
                fil.add(new MessageVigieChiro(
                        reference(message, "auteur"), texte, date(ReponsesVigieChiro.texte(message, "date"))));
            }
        }
        return fil;
    }

    /// Identifiant d'une relation Eve : soit l'objectid **brut** (`"auteur": "5f3a…"`), soit l'objet
    /// **résolu** quand le serveur a suivi la relation (`"auteur": {"_id": "5f3a…", …}`). Les deux formes
    /// existent selon les projections ; on accepte les deux plutôt que de dépendre de l'humeur du serveur.
    private static String reference(JsonObject objet, String cle) {
        JsonElement valeur = objet.get(cle);
        if (valeur == null || valeur.isJsonNull()) {
            return null;
        }
        return valeur.isJsonObject()
                ? ReponsesVigieChiro.texte(valeur.getAsJsonObject(), "_id")
                : ReponsesVigieChiro.texte(objet, cle);
    }

    /// Date d'un message, au format RFC 1123 des dates Eve (ex. `Sat, 12 Jul 2026 21:00:00 GMT`).
    /// **Tolérant** : absente ou illisible → `null`. Un fil daté à moitié reste un fil lisible ; échouer
    /// ici ferait perdre le message entier pour une virgule de format.
    private static Instant date(String texte) {
        if (texte == null || texte.isBlank()) {
            return null;
        }
        try {
            return Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(texte.trim()));
        } catch (RuntimeException formatInattendu) {
            return null;
        }
    }

    /// `libelle_court` du taxon imbriqué à la clé `cle` (ex. `"tadarida_taxon"`), ou `null`.
    private static String codeTaxon(JsonObject objet, String cle) {
        JsonElement taxon = objet.get(cle);
        return taxon != null && taxon.isJsonObject()
                ? ReponsesVigieChiro.texte(taxon.getAsJsonObject(), "libelle_court")
                : null;
    }

    /// Code de la première alternative Tadarida (`tadarida_taxon_autre[0].taxon.libelle_court`), ou `null`.
    private static String premierTaxonAutre(JsonObject obs) {
        JsonElement autres = obs.get("tadarida_taxon_autre");
        if (autres == null || !autres.isJsonArray() || autres.getAsJsonArray().isEmpty()) {
            return null;
        }
        JsonElement premier = autres.getAsJsonArray().get(0);
        return premier.isJsonObject() ? codeTaxon(premier.getAsJsonObject(), "taxon") : null;
    }

    /// Nombre (`Double`) de la clé `cle`, ou `null` si absent / non numérique.
    private static Double nombre(JsonObject objet, String cle) {
        JsonElement element = objet.get(cle);
        try {
            return element == null || element.isJsonNull() ? null : element.getAsDouble();
        } catch (RuntimeException pasUnNombre) {
            return null;
        }
    }
}
