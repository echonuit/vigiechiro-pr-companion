package fr.univ_amu.iut.diagnostic.model;

import java.util.ArrayList;
import java.util.List;

/// Lecture d'un **tableau JSON de chaînes** (`["a","b"]`), exacte réciproque de
/// [JsonSimple#tableau(java.util.List)].
///
/// L'utilitaire partagé [JsonSimple] (paquet `commun.model`) ne sait que **sérialiser** ; il
/// n'expose aucun parseur (et `commun/**` est gelé). La feature `diagnostic` doit pourtant
/// **relire** les colonnes `TEXT` JSON écrites à l'import (`sensor_log.parsed_events` et
/// `sensor_log.detected_anomalies`, alimentées via `JsonSimple.tableau`). Ce lecteur vit donc
/// dans la feature, en miroir documenté de [JsonSimple] : il dés-échappe les séquences produites
/// par [JsonSimple#echapper(String)] (guillemet, antislash, retours chariot/ligne, tabulation et
/// échappements Unicode des caractères de contrôle).
///
/// Tolérant par construction (R19, journal circulaire) : une entrée `null`, vide, `"null"` ou
/// `"[]"` donne une liste vide ; un contenu mal formé est lu au mieux sans lever d'exception.
public final class LectureJsonTableau {

  private LectureJsonTableau() {}

  /// Lit un tableau JSON de chaînes et renvoie ses éléments dés-échappés, dans l'ordre.
  ///
  /// @param json le texte JSON (ex. `["Réveil","Erreur SD"]`), éventuellement `null`
  /// @return la liste des chaînes (jamais `null`, immuable)
  public static List<String> lire(String json) {
    List<String> resultat = new ArrayList<>();
    if (json == null) {
      return List.copyOf(resultat);
    }
    String t = json.strip();
    if (t.isEmpty() || t.equals("null") || t.equals("[]") || t.charAt(0) != '[') {
      return List.copyOf(resultat);
    }

    int n = t.length();
    int i = 1; // on saute le crochet ouvrant
    while (i < n) {
      char c = t.charAt(i);
      if (c == ']') {
        break;
      }
      if (c != '"') {
        i++; // séparateur, espace ou jeton inattendu : ignoré
        continue;
      }
      // Début d'une chaîne : on lit jusqu'au guillemet fermant en dés-échappant.
      StringBuilder courant = new StringBuilder();
      i++; // guillemet ouvrant
      while (i < n) {
        char d = t.charAt(i);
        if (d == '"') {
          i++; // guillemet fermant
          break;
        }
        if (d == '\\' && i + 1 < n) {
          char e = t.charAt(i + 1);
          switch (e) {
            case '"' -> courant.append('"');
            case '\\' -> courant.append('\\');
            case '/' -> courant.append('/');
            case 'n' -> courant.append('\n');
            case 'r' -> courant.append('\r');
            case 't' -> courant.append('\t');
            case 'b' -> courant.append('\b');
            case 'f' -> courant.append('\f');
            case 'u' -> {
              if (i + 6 <= n) {
                courant.append((char) Integer.parseInt(t.substring(i + 2, i + 6), 16));
                i += 4; // les 4 hexa ; le +2 commun consomme la sequence echappee
              }
            }
            default -> courant.append(e);
          }
          i += 2;
        } else {
          courant.append(d);
          i++;
        }
      }
      resultat.add(courant.toString());
    }
    return List.copyOf(resultat);
  }
}
