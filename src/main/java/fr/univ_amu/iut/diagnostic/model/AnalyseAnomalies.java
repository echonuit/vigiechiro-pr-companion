package fr.univ_amu.iut.diagnostic.model;

import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/// Analyse des anomalies et évènements techniques d'une nuit (R19), à partir de l'état déjà
/// persisté du [JournalDuCapteur] (feature `passage`). Classe **pure** (aucun DAO, aucune IO,
/// aucun JavaFX) : elle ne fait que **relire et classer** ce qui est présent dans les colonnes
/// JSON `parsed_events` / `detected_anomalies` — pas de re-parsing du fichier `LogPR`.
///
/// **R19 (journal circulaire)** : la SD pleine efface les entrées anciennes. L'analyse n'essaie
/// jamais de reconstituer les pertes : elle expose la liste brute des anomalies présentes et
/// propose un classement indicatif par familles (réveils non programmés, erreurs SD,
/// redémarrages, batterie). Une anomalie peut relever de plusieurs familles, chaque filtre est
/// indépendant.
public final class AnalyseAnomalies {

  private final List<String> anomalies;
  private final List<String> evenements;

  /// @param anomalies anomalies détectées (déjà dés-sérialisées), jamais `null`
  /// @param evenements évènements remarquables (déjà dés-sérialisés), jamais `null`
  public AnalyseAnomalies(List<String> anomalies, List<String> evenements) {
    this.anomalies = List.copyOf(Objects.requireNonNull(anomalies, "anomalies"));
    this.evenements = List.copyOf(Objects.requireNonNull(evenements, "evenements"));
  }

  /// Analyse vide : aucun journal exploitable (R19, entrées perdues ou journal absent).
  public static AnalyseAnomalies vide() {
    return new AnalyseAnomalies(List.of(), List.of());
  }

  /// Construit l'analyse en relisant les colonnes JSON du journal persisté (`detected_anomalies`
  /// et `parsed_events`), via [LectureJsonTableau] (réciproque de `JsonSimple.tableau` utilisé à
  /// l'import).
  public static AnalyseAnomalies depuisJournal(JournalDuCapteur journal) {
    Objects.requireNonNull(journal, "journal");
    return new AnalyseAnomalies(
        LectureJsonTableau.lire(journal.anomaliesDetectees()),
        LectureJsonTableau.lire(journal.evenementsParses()));
  }

  /// Toutes les anomalies présentes, dans l'ordre du journal (R19 : ce qui est présent).
  public List<String> anomalies() {
    return anomalies;
  }

  /// Évènements remarquables présents (démarrages, mises en veille, réveils…).
  public List<String> evenements() {
    return evenements;
  }

  /// `true` si au moins une anomalie est présente.
  public boolean aDesAnomalies() {
    return !anomalies.isEmpty();
  }

  /// Réveils non programmés (firmware `Wakeup` hors alarme).
  public List<String> reveilsNonProgrammes() {
    return filtrer(
        a ->
            a.contains("réveil non programmé")
                || a.contains("reveil non programme")
                || a.contains("wakeup"));
  }

  /// Erreurs liées à la carte SD (écriture/lecture en échec).
  public List<String> erreursSD() {
    return filtrer(a -> a.contains("sd") && estErreur(a));
  }

  /// Redémarrages inattendus de l'enregistreur.
  public List<String> redemarrages() {
    return filtrer(
        a ->
            a.contains("redémarr")
                || a.contains("redemarr")
                || a.contains("reboot")
                || a.contains("restart"));
  }

  /// Alertes liées à la batterie (niveau faible/critique).
  public List<String> alertesBatterie() {
    return filtrer(a -> a.contains("batterie") || a.contains("battery") || a.contains("bat."));
  }

  private static boolean estErreur(String minuscule) {
    return minuscule.contains("erreur")
        || minuscule.contains("error")
        || minuscule.contains("échec")
        || minuscule.contains("echec")
        || minuscule.contains("fail");
  }

  /// Filtre les anomalies sur un prédicat appliqué à leur forme minuscule (Locale.ROOT).
  private List<String> filtrer(Predicate<String> surMinuscule) {
    return anomalies.stream().filter(a -> surMinuscule.test(a.toLowerCase(Locale.ROOT))).toList();
  }
}
