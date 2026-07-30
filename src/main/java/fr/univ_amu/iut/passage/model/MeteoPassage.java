package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.JsonSimple;
import java.util.Map;

/// Accès typé aux **données météo optionnelles** d'un passage (#106, étendu aux données demandées au
/// dépôt VigieChiro), portées par la colonne JSON `passage.weather_data` ([Passage#donneesMeteo]).
///
/// Les grandeurs vivent chacune sous une clé de l'**objet JSON** : `tempDebut`, `tempFin`, `vent`,
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
    /// nuageuse) : chaque grandeur absente ou illisible vaut `null`. Tolérant : ne lève jamais. Le vent
    /// et la couverture sont lus comme **catégories** ([Vent] / [CouvertureNuageuse]) ; une ancienne
    /// valeur **chiffrée** (km/h, %) est ramenée à sa catégorie (compatibilité ascendante).
    public static MeteoReleve lire(String donneesMeteo) {
        Map<String, String> champs = ObjetJson.lire(donneesMeteo);
        return new MeteoReleve(
                lireDouble(champs, CLE_TEMPERATURE),
                lireDouble(champs, CLE_TEMPERATURE_FIN),
                lireVent(champs.get(CLE_VENT)),
                lireCouverture(champs.get(CLE_COUVERTURE)));
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
        poserTexte(
                champs, CLE_VENT, releve.vent() == null ? null : releve.vent().name());
        poserTexte(
                champs,
                CLE_COUVERTURE,
                releve.couvertureNuageuse() == null
                        ? null
                        : releve.couvertureNuageuse().name());
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
        return nombreFiniOuNull(champs.get(cle));
    }

    /// Lit une force de **vent** depuis un jeton stocké : d'abord comme catégorie ([Vent#depuisTexte]),
    /// sinon comme ancienne **vitesse chiffrée** (km/h) ramenée à sa catégorie ([Vent#depuisVitesse]),
    /// pour relire un `weather_data` d'avant la catégorisation. `null` si absent/illisible.
    private static Vent lireVent(String jeton) {
        String texte = texteJson(jeton);
        Vent categorie = Vent.depuisTexte(texte);
        if (categorie != null) {
            return categorie;
        }
        Double vitesse = nombreFiniOuNull(texte);
        return vitesse == null ? null : Vent.depuisVitesse(vitesse);
    }

    /// Lit une **couverture nuageuse** depuis un jeton stocké : d'abord comme tranche
    /// ([CouvertureNuageuse#depuisTexte]), sinon comme ancien **pourcentage chiffré** ramené à sa tranche
    /// ([CouvertureNuageuse#depuisPourcentage]). `null` si absent/illisible.
    private static CouvertureNuageuse lireCouverture(String jeton) {
        String texte = texteJson(jeton);
        CouvertureNuageuse tranche = CouvertureNuageuse.depuisTexte(texte);
        if (tranche != null) {
            return tranche;
        }
        Double pourcentage = nombreFiniOuNull(texte);
        return pourcentage == null ? null : CouvertureNuageuse.depuisPourcentage(pourcentage);
    }

    /// Ramène un jeton de valeur JSON à sa **chaîne** : retire les guillemets encadrants (grandeur
    /// catégorielle stockée sous son `name()`), ou renvoie le jeton nu tel quel (ancien nombre `12.4`).
    private static String texteJson(String jeton) {
        if (jeton == null) {
            return null;
        }
        String t = jeton.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    /// Parse un jeton en nombre **fini**, ou `null` si absent, illisible ou non fini (jamais de
    /// `NaN`/`Infini` remonté).
    private static Double nombreFiniOuNull(String brut) {
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

    /// Pose (ou efface si `null`) un jeton **chaîne JSON** (entre guillemets, échappé) dans la map : sert
    /// aux grandeurs catégorielles (vent, couverture), stockées sous leur `name()`.
    private static void poserTexte(Map<String, String> champs, String cle, String valeur) {
        if (valeur == null) {
            champs.remove(cle);
        } else {
            champs.put(cle, "\"" + JsonSimple.echapper(valeur) + "\"");
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
