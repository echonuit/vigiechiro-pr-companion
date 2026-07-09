package fr.univ_amu.iut.commun.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
                donnees.add(new DonneeVigieChiro(titre, observations(donnee)));
            }
        }
        return donnees;
    }

    /// Observations (détections) d'une donnée : chaque entrée porte au minimum un taxon Tadarida.
    private static List<ObservationVigieChiro> observations(JsonObject donnee) {
        List<ObservationVigieChiro> observations = new ArrayList<>();
        JsonElement brut = donnee.get("observations");
        if (brut == null || !brut.isJsonArray()) {
            return observations;
        }
        for (JsonElement element : brut.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obs = element.getAsJsonObject();
            String taxon = codeTaxon(obs, "tadarida_taxon");
            if (taxon == null) {
                continue; // une observation sans taxon Tadarida n'est pas exploitable
            }
            observations.add(new ObservationVigieChiro(
                    taxon,
                    nombre(obs, "tadarida_probabilite"),
                    nombre(obs, "frequence_mediane"),
                    nombre(obs, "temps_debut"),
                    nombre(obs, "temps_fin"),
                    premierTaxonAutre(obs),
                    codeTaxon(obs, "observateur_taxon"),
                    nombre(obs, "observateur_probabilite")));
        }
        return observations;
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
