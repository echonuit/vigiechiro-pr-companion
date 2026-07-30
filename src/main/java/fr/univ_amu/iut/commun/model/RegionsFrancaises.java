package fr.univ_amu.iut.commun.model;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// Table **département → région**, France métropolitaine : la connaissance géographique posée par
/// l'ADR 2351 pour situer un carré, généralisée ici (#2791) pour servir aussi la chaîne
/// `point → commune → département → région`. Une seule table, deux décodages d'entrée :
/// [RegionDuCarre] lit les deux premiers chiffres d'un numéro de carré, [Commune] dérive le
/// département d'un code INSEE.
///
/// Les libellés de région rendus ici sont ceux du **référentiel embarqué**, pas les libellés
/// administratifs officiels : le référentiel écrit `Grand-Est` (avec trait d'union) et
/// `Nouvelle Aquitaine` (sans). Ce sont des **clés de jointure**, pas du texte d'affichage : les
/// corriger orthographiquement les rendrait introuvables. La garde `RegionDuCarreTest`
/// vérifie que chacune existe réellement dans la ressource.
///
/// **Normalisation Corse** : la table est indexée sur `20` (le numérotage des carrés, fait de
/// chiffres seuls) ; les codes INSEE `2A`/`2B` y sont ramenés. L'outre-mer (`97x`) n'a pas de
/// déclinaison régionale dans le référentiel : il renvoie **vide**, et le référentiel retombe sur
/// `national` : lecture plus large mais jamais fausse.
public final class RegionsFrancaises {

    /// Département → région. La Corse porte la clé `20` (numérotage carré), cf. normalisation.
    private static final Map<String, String> PAR_DEPARTEMENT = construire();

    private RegionsFrancaises() {}

    /// La région du département `code` (`"01"`…`"95"`, `"20"`, `"2A"`, `"2B"`), ou **vide** si le
    /// code ne permet pas de conclure : nul, trop court, outre-mer ou inconnu.
    public static Optional<String> pourDepartement(String code) {
        if (code == null || code.length() < 2) {
            return Optional.empty();
        }
        return Optional.ofNullable(PAR_DEPARTEMENT.get(normaliser(code)));
    }

    /// Toutes les régions que cette table peut produire : sert à la garde qui les confronte au
    /// référentiel embarqué.
    public static Set<String> regionsConnues() {
        return Set.copyOf(PAR_DEPARTEMENT.values());
    }

    /// Ramène les codes INSEE corses (`2A`/`2B`) sur la clé `20` de la table.
    private static String normaliser(String code) {
        if ("2A".equalsIgnoreCase(code) || "2B".equalsIgnoreCase(code)) {
            return "20";
        }
        return code;
    }

    private static Map<String, String> construire() {
        Map<String, String> table = new java.util.HashMap<>();
        poser(table, "Auvergne-Rhone-Alpes", "01", "03", "07", "15", "26", "38", "42", "43", "63", "69", "73", "74");
        poser(table, "Bourgogne-Franche-Comte", "21", "25", "39", "58", "70", "71", "89", "90");
        poser(table, "Bretagne", "22", "29", "35", "56");
        poser(table, "Centre-Val de Loire", "18", "28", "36", "37", "41", "45");
        poser(table, "Corse", "20");
        poser(table, "Grand-Est", "08", "10", "51", "52", "54", "55", "57", "67", "68", "88");
        poser(table, "Hauts-de-France", "02", "59", "60", "62", "80");
        poser(table, "Ile-de-France", "75", "77", "78", "91", "92", "93", "94", "95");
        poser(table, "Normandie", "14", "27", "50", "61", "76");
        poser(table, "Nouvelle Aquitaine", "16", "17", "19", "23", "24", "33", "40", "47", "64", "79", "86", "87");
        poser(table, "Occitanie", "09", "11", "12", "30", "31", "32", "34", "46", "48", "65", "66", "81", "82");
        poser(table, "Pays de la Loire", "44", "49", "53", "72", "85");
        poser(table, "Provence-Alpes-Cote dAzur", "04", "05", "06", "13", "83", "84");
        return Map.copyOf(table);
    }

    private static void poser(Map<String, String> table, String region, String... departements) {
        for (String departement : departements) {
            table.put(departement, region);
        }
    }
}
