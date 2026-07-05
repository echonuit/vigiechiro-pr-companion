package fr.univ_amu.iut.passage.model;

import java.util.Map;

/// Accès typé aux **données météo optionnelles** d'un passage (#106, étendu aux données demandées au
/// dépôt VigieChiro), portées par la colonne JSON `passage.weather_data` ([Passage#donneesMeteo]).
///
/// Les grandeurs vivent chacune sous une clé de l'**objet JSON** — `tempDebut`, `tempFin`, `vent`,
/// `couvertureNuageuse` ; lecture et écriture **préservent les autres clés** ([ObjetJson]), pour ne
/// jamais écraser la colonne. Chaque grandeur est **indépendamment optionnelle** (`null` = non
/// renseignée) : jamais bloquant.
///
/// Robustesse : on n'accepte que des valeurs **finies** (ni `NaN` ni `±Infinity`), en lecture comme en
/// saisie, pour ne jamais stocker un JSON invalide ni afficher `NaN`.
///
/// Objet utilitaire pur (aucune dépendance JavaFX ni JDBC) : la mise en forme d'affichage (unité,
/// virgule décimale) relève de la couche `viewmodel`.
public final class MeteoPassage {

    /// Clés des grandeurs météo dans l'objet JSON `weather_data`.
    private static final String CLE_TEMPERATURE = "tempDebut";

    private static final String CLE_TEMPERATURE_FIN = "tempFin";
    private static final String CLE_VENT = "vent";
    private static final String CLE_COUVERTURE = "couvertureNuageuse";

    private MeteoPassage() {}

    /// Température en début de nuit (°C), ou `null` si absente/illisible. Raccourci sur [#lire] pour le
    /// seul héritage #106 (nombreux appelants existants) ; utiliser [#lire] pour le relevé complet.
    public static Double temperatureDebutNuit(String donneesMeteo) {
        return lireDouble(ObjetJson.lire(donneesMeteo), CLE_TEMPERATURE);
    }

    /// Relevé météo **complet** lu depuis `donneesMeteo` (température début/fin, vent, couverture
    /// nuageuse) : chaque grandeur absente ou illisible vaut `null`. Tolérant : ne lève jamais.
    public static MeteoReleve lire(String donneesMeteo) {
        Map<String, String> champs = ObjetJson.lire(donneesMeteo);
        return new MeteoReleve(
                lireDouble(champs, CLE_TEMPERATURE),
                lireDouble(champs, CLE_TEMPERATURE_FIN),
                lireDouble(champs, CLE_VENT),
                lireDouble(champs, CLE_COUVERTURE));
    }

    /// Met à jour la **température de début de nuit** dans `donneesMeteoExistant` en **préservant les
    /// autres clés** : `null` efface la clé. Renvoie le nouvel objet JSON, ou `null` s'il devient vide.
    ///
    /// @throws IllegalArgumentException si `temperature` n'est pas finie (NaN/Infini)
    public static String definir(String donneesMeteoExistant, Double temperature) {
        Map<String, String> champs = ObjetJson.lire(donneesMeteoExistant);
        poser(champs, CLE_TEMPERATURE, temperature);
        return ObjetJson.ecrire(champs);
    }

    /// Écrit un **relevé complet** dans `donneesMeteoExistant` en **préservant les clés inconnues** :
    /// chaque grandeur `null` efface sa clé, chaque grandeur renseignée la (re)pose. Renvoie le nouvel
    /// objet JSON, ou `null` s'il devient vide.
    ///
    /// @throws IllegalArgumentException si une grandeur n'est pas finie (NaN/Infini)
    public static String definirReleve(String donneesMeteoExistant, MeteoReleve releve) {
        Map<String, String> champs = ObjetJson.lire(donneesMeteoExistant);
        poser(champs, CLE_TEMPERATURE, releve.temperatureDebutNuit());
        poser(champs, CLE_TEMPERATURE_FIN, releve.temperatureFinNuit());
        poser(champs, CLE_VENT, releve.vent());
        poser(champs, CLE_COUVERTURE, releve.couvertureNuageuse());
        return ObjetJson.ecrire(champs);
    }

    /// Lit une **saisie utilisateur** numérique (température, vent, couverture…) : vide → `null` ; sinon
    /// nombre **fini** (virgule ou point acceptés). **Lève** `NumberFormatException` si la saisie n'est
    /// pas un nombre fini, pour que l'IHM signale l'erreur (contrairement aux lectures de base,
    /// tolérantes comme [#lire]).
    public static Double lireSaisie(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            return null;
        }
        double valeur = Double.parseDouble(saisie.trim().replace(',', '.'));
        if (!Double.isFinite(valeur)) {
            throw new NumberFormatException("Valeur non finie : " + saisie);
        }
        return valeur;
    }

    /// Lecture tolérante d'une grandeur numérique dans une map de jetons JSON : `null` si absente, vide,
    /// illisible ou non finie (jamais de `NaN`/`Infini` remonté).
    private static Double lireDouble(Map<String, String> champs, String cle) {
        String brut = champs.get(cle);
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

    /// Pose (ou efface si `null`) une grandeur dans la map de jetons JSON, en refusant les valeurs non
    /// finies (format indépendant de la locale).
    private static void poser(Map<String, String> champs, String cle, Double valeur) {
        if (valeur == null) {
            champs.remove(cle);
        } else {
            if (!Double.isFinite(valeur)) {
                throw new IllegalArgumentException("Valeur météo non finie (NaN/Infini) refusée : " + valeur);
            }
            champs.put(cle, Double.toString(valeur));
        }
    }
}
