package fr.univ_amu.iut.importation.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Inspecte un dossier de carte SD <b>en lecture seule</b> (parcours P2, première étape). Il ne
 * <b>jamais rien écrire</b> : conformément à R9, la source reste intacte ; la copie protégée vient
 * ensuite, séparément ({@link CopieProtegee}).
 *
 * <p>Trois informations sont collectées :
 *
 * <ul>
 *   <li>le <b>journal du capteur</b> {@code LogPR<n>.txt}, parsé via {@link AnalyseurLogPR} (n° de
 *       série, paramètres d'acquisition, anomalies) ;
 *   <li>les <b>enregistrements originaux</b> WAV présents (à la racine, ou dans un sous-dossier
 *       {@code bruts/} si la source est déjà organisée comme une session R22) ;
 *   <li>le <b>relevé climatique</b> {@code *_THLog.csv}, éventuellement absent (R20) ;
 *   <li>l'<b>état de nommage</b> ({@link EtatNommage}) : fichiers encore bruts (R7) ou déjà
 *       préfixés (R6).
 * </ul>
 */
public class InspecteurDossier {

  private static final String SOUS_DOSSIER_BRUTS = "bruts";

  private final AnalyseurLogPR analyseurLog;

  public InspecteurDossier(AnalyseurLogPR analyseurLog) {
    this.analyseurLog = Objects.requireNonNull(analyseurLog, "analyseurLog");
  }

  /**
   * Inspecte {@code dossierSource} sans le modifier.
   *
   * @throws IllegalArgumentException si le chemin n'existe pas ou n'est pas un dossier
   */
  public RapportInspection inspecter(Path dossierSource) {
    Objects.requireNonNull(dossierSource, "dossierSource");
    if (!Files.isDirectory(dossierSource)) {
      throw new IllegalArgumentException("Dossier source introuvable : " + dossierSource);
    }
    Path cheminJournal = trouverPremier(dossierSource, this::estJournalLog);
    JournalParse journal = cheminJournal == null ? null : analyseurLog.analyser(cheminJournal);
    Path releve = trouverPremier(dossierSource, this::estReleveClimatique);
    List<Path> originaux = listerOriginaux(dossierSource);
    EtatNommage etat = determinerEtatNommage(originaux);
    return new RapportInspection(dossierSource, cheminJournal, journal, releve, originaux, etat);
  }

  /** Enregistrements originaux : dans {@code bruts/} si présent, sinon à la racine du dossier. */
  private List<Path> listerOriginaux(Path dossierSource) {
    Path bruts = dossierSource.resolve(SOUS_DOSSIER_BRUTS);
    Path ou = Files.isDirectory(bruts) ? bruts : dossierSource;
    try (Stream<Path> flux = Files.list(ou)) {
      return flux.filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".wav"))
          .sorted(Comparator.comparing(p -> p.getFileName().toString()))
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Lecture du dossier impossible : " + ou, e);
    }
  }

  /**
   * Détermine l'état de nommage des originaux : aucun fichier {@link EtatNommage#VIDE} ; tous
   * préfixés {@code Car...} {@link EtatNommage#PREFIXE} ; au moins un fichier brut {@link
   * EtatNommage#BRUT}.
   */
  private EtatNommage determinerEtatNommage(List<Path> originaux) {
    if (originaux.isEmpty()) {
      return EtatNommage.VIDE;
    }
    boolean tousPrefixes =
        originaux.stream().allMatch(p -> p.getFileName().toString().startsWith("Car"));
    return tousPrefixes ? EtatNommage.PREFIXE : EtatNommage.BRUT;
  }

  private boolean estJournalLog(String nom) {
    return nom.matches("(?i)LogPR\\d+\\.txt");
  }

  private boolean estReleveClimatique(String nom) {
    return nom.toUpperCase().contains("THLOG") && nom.toLowerCase().endsWith(".csv");
  }

  private Path trouverPremier(Path dossier, java.util.function.Predicate<String> nomAccepte) {
    try (Stream<Path> flux = Files.list(dossier)) {
      return flux.filter(Files::isRegularFile)
          .filter(p -> nomAccepte.test(p.getFileName().toString()))
          .sorted(Comparator.comparing(p -> p.getFileName().toString()))
          .findFirst()
          .orElse(null);
    } catch (IOException e) {
      throw new UncheckedIOException("Lecture du dossier impossible : " + dossier, e);
    }
  }
}
