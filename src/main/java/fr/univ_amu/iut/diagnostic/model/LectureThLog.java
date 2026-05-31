package fr.univ_amu.iut.diagnostic.model;

import fr.univ_amu.iut.commun.model.LecteurCsv;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// Lecture du relevé climatique brut `PaRecPR<sn>_THLog.csv` (C10) en une série de
/// [MesureClimatique], via l'utilitaire partagé [LecteurCsv] (séparateur tabulation).
///
/// Pourquoi relire le fichier plutôt que la colonne JSON `climate_log.measurements` ? L'import
/// persiste aujourd'hui le relevé en ne renseignant que son `file_path` (la colonne
/// `measurements` reste `null`, cf. `ServiceImport`). La source canonique de la série est donc le
/// THLog lui-même, exactement l'option « relecture brute via `LecteurCsv` » du cahier des
/// charges.
///
/// Format observé (entête comprise) :
///
/// ```
/// Date        Hour       Temperature  Humidity
/// 22/04/2026  16:02:21   +23.9        64
/// ```
///
/// Tolérant (R19) : une ligne mal formée (journal circulaire tronqué, champ illisible) est
/// **ignorée** sans interrompre la lecture. Déterministe : l'ordre du fichier est préservé.
public final class LectureThLog {

  private static final DateTimeFormatter DATE_THLOG =
      DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);
  private static final DateTimeFormatter HEURE_THLOG =
      DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

  private LectureThLog() {}

  /// Lit le THLog pointé par `fichier`. Renvoie une liste vide si le chemin est `null`,
  /// inexistant ou illisible (le relevé existe peut-être en base mais son fichier a disparu).
  public static List<MesureClimatique> lire(Path fichier) {
    if (fichier == null || !Files.isReadable(fichier)) {
      return List.of();
    }
    return analyser(new LecteurCsv('\t').lireSansEntete(fichier));
  }

  /// Variante sur contenu déjà chargé (utile aux tests), entête comprise.
  public static List<MesureClimatique> lireContenu(String contenu) {
    if (contenu == null) {
      return List.of();
    }
    return analyser(new LecteurCsv('\t').lireSansEntete(contenu));
  }

  private static List<MesureClimatique> analyser(List<List<String>> lignes) {
    List<MesureClimatique> mesures = new ArrayList<>();
    for (List<String> ligne : lignes) {
      MesureClimatique mesure = parserLigne(ligne);
      if (mesure != null) {
        mesures.add(mesure);
      }
    }
    return List.copyOf(mesures);
  }

  private static MesureClimatique parserLigne(List<String> ligne) {
    if (ligne.size() < 4) {
      return null;
    }
    try {
      LocalDate date = LocalDate.parse(ligne.get(0).strip(), DATE_THLOG);
      LocalTime heure = LocalTime.parse(ligne.get(1).strip(), HEURE_THLOG);
      double temperature = Double.parseDouble(ligne.get(2).strip());
      int humidite = Integer.parseInt(ligne.get(3).strip());
      return new MesureClimatique(date, heure, temperature, humidite);
    } catch (RuntimeException malForme) {
      return null; // ligne illisible : ignorée (R19)
    }
  }
}
