package fr.univ_amu.iut.commun.model;

import java.util.Map;
import java.util.Optional;

/// Situe un **carré** dans une région administrative, pour choisir la déclinaison régionale du
/// référentiel d'activité (#2351).
///
/// **Les deux premiers chiffres du numéro de carré sont le département.** Ce n'est écrit nulle part
/// dans le format lui-même : c'est une propriété du numérotage Vigie-Chiro, confirmée par le porteur du
/// produit et tracée par l'ADR 2351. Une déduction tirée de quelques exemples n'aurait pas suffi — une
/// région devinée de travers change le verdict d'activité en silence.
///
/// Les libellés de région rendus ici sont ceux du **référentiel embarqué**, pas les libellés
/// administratifs officiels : le référentiel écrit `Grand-Est` (avec trait d'union) et
/// `Nouvelle Aquitaine` (sans). Ce sont des **clés de jointure**, pas du texte d'affichage — les
/// corriger orthographiquement les rendrait introuvables. Une garde de test vérifie que chacune existe
/// réellement dans la ressource.
public final class RegionDuCarre {

    /// Département → région, France métropolitaine. La Corse porte `20` dans un numéro de carré, qui
    /// n'est fait que de chiffres, là où le code officiel se décline en `2A`/`2B`.
    private static final Map<String, String> PAR_DEPARTEMENT = construire();

    private RegionDuCarre() {}

    /// La région du carré, ou **vide** si le numéro ne permet pas de conclure : nul, trop court, ou
    /// département inconnu (outre-mer, saisie erronée). Le référentiel retombe alors sur `national`,
    /// ce qui est une lecture plus large mais jamais fausse.
    public static Optional<String> pour(String numeroCarre) {
        if (numeroCarre == null || numeroCarre.length() < 2) {
            return Optional.empty();
        }
        return Optional.ofNullable(PAR_DEPARTEMENT.get(numeroCarre.substring(0, 2)));
    }

    /// Toutes les régions que ce tableau peut produire : sert à la garde qui les confronte au
    /// référentiel embarqué.
    public static java.util.Set<String> regionsConnues() {
        return java.util.Set.copyOf(PAR_DEPARTEMENT.values());
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
