package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.JsonSimple;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Lecture/écriture minimale d'un **objet JSON plat** (`{"clé":valeur,…}`) pour manipuler la colonne
/// `passage.weather_data` (#106) **sans perdre les clés inconnues** : on lit la clé voulue, on la met à
/// jour, puis on réécrit l'objet en préservant le reste.
///
/// Les valeurs sont conservées comme **jetons bruts** (un nombre reste un nombre, une chaîne reste
/// entre guillemets), pour ne pas altérer le format. **Tolérant** : `null`, chaîne vide ou contenu non
/// objet → objet vide. (Le module ne `requires` aucune bibliothèque JSON ; pendant longtemps seule
/// l'écriture existait, cf. [JsonSimple] — d'où ce lecteur minimal, suffisant pour des objets plats.)
final class ObjetJson {

    /// Paire `"clé": valeur` de premier niveau : la valeur est soit une chaîne entre guillemets (gérant
    /// les `"` échappés), soit un jeton sans virgule/accolade (nombre, booléen, `null`).
    private static final Pattern PAIRE =
            Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\"|[^,}\\s]+)");

    private ObjetJson() {}

    /// Lit l'objet en une map ordonnée `clé → jeton de valeur brut` ; map vide si `json` n'est pas un
    /// objet exploitable.
    static Map<String, String> lire(String json) {
        Map<String, String> champs = new LinkedHashMap<>();
        if (json == null) {
            return champs;
        }
        String contenu = json.trim();
        if (!contenu.startsWith("{") || !contenu.endsWith("}")) {
            return champs;
        }
        Matcher paire = PAIRE.matcher(contenu);
        while (paire.find()) {
            champs.put(paire.group(1), paire.group(2));
        }
        return champs;
    }

    /// Réécrit l'objet, ou `null` s'il est vide (colonne effacée). Clés échappées, valeurs réémises
    /// telles quelles (jetons JSON bruts).
    static String ecrire(Map<String, String> champs) {
        if (champs.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        boolean premier = true;
        for (Map.Entry<String, String> champ : champs.entrySet()) {
            if (!premier) {
                sb.append(',');
            }
            sb.append('"')
                    .append(JsonSimple.echapper(champ.getKey()))
                    .append("\":")
                    .append(champ.getValue());
            premier = false;
        }
        return sb.append('}').toString();
    }
}
