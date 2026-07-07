package fr.univ_amu.iut.cli;

import java.util.List;
import java.util.Map;

/// Écrivain **JSON minimal** pour la sortie `--json` du CLI (#614) : un tableau d'objets plats
/// (clé → chaîne / nombre / booléen / `null`). Volontairement **sans bibliothèque tierce** (le CLI reste
/// léger, cf. socle) ; suffisant pour des enregistrements tabulaires (passages, détail d'un passage…).
///
/// Les clés préservent leur ordre d'insertion (utiliser une `LinkedHashMap`). Les chaînes sont échappées
/// selon JSON (guillemets, antislash, caractères de contrôle).
public final class FormatJson {

    private FormatJson() {}

    /// Sérialise une **liste d'objets** (chaque objet = map ordonnée `clé → valeur`) en tableau JSON
    /// indenté de deux espaces. Les valeurs acceptées : `String`, `Number`, `Boolean`, ou `null`.
    public static String tableau(List<? extends Map<String, ?>> objets) {
        if (objets.isEmpty()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < objets.size(); i++) {
            json.append("  ").append(objet(objets.get(i)));
            json.append(i < objets.size() - 1 ? ",\n" : "\n");
        }
        return json.append(']').toString();
    }

    /// Sérialise un **objet plat unique** (map ordonnée `clé → valeur`) en objet JSON `{ ... }` sur une
    /// seule ligne. Sortie naturelle pour l'inspection d'une **ressource unique** (`statut-passage --json`),
    /// là où [#tableau] convient aux collections. Valeurs acceptées : `String`, `Number`, `Boolean`, `null`.
    public static String objet(Map<String, ?> champs) {
        StringBuilder json = new StringBuilder("{");
        boolean premier = true;
        for (Map.Entry<String, ?> champ : champs.entrySet()) {
            if (!premier) {
                json.append(", ");
            }
            premier = false;
            json.append(chaine(champ.getKey())).append(": ").append(valeur(champ.getValue()));
        }
        return json.append('}').toString();
    }

    private static String valeur(Object valeur) {
        if (valeur == null) {
            return "null";
        }
        if (valeur instanceof Number || valeur instanceof Boolean) {
            return valeur.toString();
        }
        return chaine(valeur.toString());
    }

    private static String chaine(String texte) {
        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        return json.append('"').toString();
    }
}
