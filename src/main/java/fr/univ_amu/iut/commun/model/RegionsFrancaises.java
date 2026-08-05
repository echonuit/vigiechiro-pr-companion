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

    /// Ce code désigne-t-il un **département** ? (#3298)
    ///
    /// La table sert ici d'annuaire plutôt que de convertisseur : elle porte les 95 départements
    /// métropolitains, et sait donc dire qu'un `00`, un `98` ou un `99` n'en est pas un. C'est ce que
    /// [RegionDuCarre#departement] demande pour ne pas prendre le préfixe d'un carré d'outre-mer pour un
    /// numéro de département.
    ///
    /// ⚠️ **Métropole seulement.** Un vrai département d'outre-mer (`971`…`976`) rend `false` ici, parce
    /// qu'il n'a pas de déclinaison régionale dans le référentiel d'activité et n'entre donc pas dans
    /// cette table. Ce n'est pas gênant pour l'usage prévu - c'est le préfixe d'un **numéro de carré**
    /// qu'on lui soumet, or ces numéros sont faits de chiffres et ne portent jamais `971` sur deux
    /// caractères. Y soumettre un code INSEE serait un contresens.
    public static boolean estUnDepartement(String code) {
        return code != null && code.length() >= 2 && PAR_DEPARTEMENT.containsKey(normaliser(code));
    }

    /// Deux écritures de département désignent-elles **le même** ? (#2848)
    ///
    /// Le produit lit un département de deux façons - par le numéro d'un carré ([RegionDuCarre], deux
    /// chiffres) et par un code INSEE ([Commune#departement], deux ou trois caractères) - et ces deux
    /// écritures ne se comparent pas à l'égalité de chaînes :
    ///
    /// - **Corse** : un carré porte `20`, l'INSEE écrit `2A` ou `2B`. Le numéro ne dit **pas lequel des
    ///   deux**, et la table n'est indexée que sur `20` ;
    /// - **outre-mer** : un carré porte `97`, l'INSEE écrit `971`, `972`, `974`… Là non plus le numéro
    ///   ne dit pas lequel.
    ///
    /// Dans ces deux cas la méthode rend `true`, et c'est une **abstention**, pas une équivalence : deux
    /// lectures qu'on ne sait pas départager ne sont pas une divergence à signaler. Le seul écart qu'elle
    /// affirme est celui qu'elle sait démontrer.
    ///
    /// Un code nul ou trop court rend `false` : il n'y a rien à confronter, et l'appelant écarte le cas
    /// avant d'appeler plutôt que de lire une réponse dans une absence.
    public static boolean memeDepartement(String a, String b) {
        if (a == null || b == null || a.length() < 2 || b.length() < 2) {
            return false;
        }
        String gauche = normaliser(a);
        String droite = normaliser(b);
        // On ne compare que ce que les DEUX écritures disent : `97` face à `971` s'arrête à `97`, et
        // deux codes de même longueur se comparent en entier. Écrite avec un ternaire sur la plus
        // courte, cette ligne produisait un mutant équivalent - deux façons de dire la même chose.
        int communes = Math.min(gauche.length(), droite.length());
        return gauche.regionMatches(true, 0, droite, 0, communes);
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
