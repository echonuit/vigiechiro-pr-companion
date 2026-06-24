package fr.univ_amu.iut.passage.model;

import java.util.Map;

/// Accès typé à la **donnée météo optionnelle** d'un passage (#106), portée par la colonne JSON
/// `passage.weather_data` ([Passage#donneesMeteo], documentée comme structure JSON).
///
/// La **température en début de nuit** (°C) y vit sous la clé **`tempDebut`** (convention déjà présente
/// dans le schéma de test), au sein d'un **objet JSON** : la lecture extrait cette clé et l'écriture la
/// met à jour **en préservant les autres clés météo** ([ObjetJson]), pour ne pas écraser la colonne.
/// `null` signifie « non renseignée » : jamais bloquant.
///
/// Robustesse : on n'accepte que des valeurs **finies** (ni `NaN` ni `±Infinity`), en lecture comme en
/// saisie, pour ne jamais stocker un JSON invalide ni afficher `NaN °C`.
///
/// Objet utilitaire pur (aucune dépendance JavaFX ni JDBC) : la mise en forme d'affichage (unité,
/// virgule décimale) relève de la couche `viewmodel`.
public final class MeteoPassage {

    /// Clé de la température en début de nuit dans l'objet JSON `weather_data`.
    private static final String CLE_TEMPERATURE = "tempDebut";

    private MeteoPassage() {}

    /// Température en début de nuit (°C) lue depuis l'objet `donneesMeteo`, ou `null` si la clé est
    /// absente, vide ou illisible (on ne lève jamais : une donnée optionnelle absente ne doit pas casser
    /// l'affichage). Une valeur non finie en base est ignorée (→ `null`).
    public static Double temperatureDebutNuit(String donneesMeteo) {
        String brut = ObjetJson.lire(donneesMeteo).get(CLE_TEMPERATURE);
        if (brut == null) {
            return null;
        }
        try {
            double valeur = Double.parseDouble(brut);
            return Double.isFinite(valeur) ? valeur : null;
        } catch (NumberFormatException illisible) {
            return null;
        }
    }

    /// Met à jour la température dans `donneesMeteoExistant` (un objet JSON, éventuellement `null`) en
    /// **préservant les autres clés** : `null` efface la clé `tempDebut` ; sinon elle est posée (format
    /// indépendant de la locale). Renvoie le nouvel objet JSON, ou `null` s'il devient vide.
    ///
    /// @throws IllegalArgumentException si `temperature` n'est pas finie (NaN/Infini)
    public static String definir(String donneesMeteoExistant, Double temperature) {
        Map<String, String> champs = ObjetJson.lire(donneesMeteoExistant);
        if (temperature == null) {
            champs.remove(CLE_TEMPERATURE);
        } else {
            if (!Double.isFinite(temperature)) {
                throw new IllegalArgumentException("Température non finie (NaN/Infini) refusée : " + temperature);
            }
            champs.put(CLE_TEMPERATURE, Double.toString(temperature));
        }
        return ObjetJson.ecrire(champs);
    }

    /// Lit une **saisie utilisateur** de température : vide → `null` (efface la donnée optionnelle) ;
    /// sinon nombre **fini** (virgule ou point acceptés). **Lève** `NumberFormatException` si la saisie
    /// n'est pas un nombre fini (NaN/Infini inclus), pour que l'IHM puisse signaler l'erreur
    /// (contrairement à [#temperatureDebutNuit] qui, elle, est tolérante en lecture de la base).
    public static Double lireSaisie(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            return null;
        }
        double valeur = Double.parseDouble(saisie.trim().replace(',', '.'));
        if (!Double.isFinite(valeur)) {
            throw new NumberFormatException("Température non finie : " + saisie);
        }
        return valeur;
    }
}
