package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.ResultatVerification;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.lot.model.VerificationCoherence;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ReleveClimatique;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests du moteur {@link VerificationCoherence} sur une base SQLite jetable ({@code @TempDir} +
 * {@link MigrationSchema}), exercé de bout en bout sur la vraie chaîne d'entités (site → point →
 * passage → session → originaux / séquences / journal / relevé).
 *
 * <p>Chaque test construit le graphe à l'aide des petites fabriques privées puis assert le {@link
 * ResultatVerification} produit. Le préfixe conforme est calculé via {@link Prefixe} (carré 040962,
 * point A1, passage 1, année 2026).
 */
class VerificationCoherenceTest {

  private static final String ID_USER = "u-1";
  private static final String SERIE = "1925492";
  private static final Prefixe PREFIXE = new Prefixe("040962", 2026, 1, "A1");
  private static final String NOM_ORIGINAL =
      PREFIXE.nommerOriginal("PaRecPR" + SERIE + "_20260620_213000.wav");

  @TempDir Path dossier;
  private VerificationCoherence verification;
  private PassageDao passageDao;
  private SessionDao sessionDao;
  private EnregistrementOriginalDao originalDao;
  private SequenceDao sequenceDao;
  private JournalDuCapteurDao journalDao;
  private ReleveClimatiqueDao releveDao;
  private Long idPoint;

  @BeforeEach
  void preparer() {
    SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
    new MigrationSchema(source).migrer();
    new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
    SiteDao siteDao = new SiteDao(source);
    PointDao pointDao = new PointDao(source);
    Site site =
        siteDao.insert(
            new Site(null, "040962", "Étang", Protocole.STANDARD, null, "2026-05-01", ID_USER));
    idPoint = pointDao.insert(new PointDEcoute(null, "A1", null, null, null, site.id())).id();
    new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));

    passageDao = new PassageDao(source);
    sessionDao = new SessionDao(source);
    originalDao = new EnregistrementOriginalDao(source);
    sequenceDao = new SequenceDao(source);
    journalDao = new JournalDuCapteurDao(source);
    releveDao = new ReleveClimatiqueDao(source);

    verification =
        new VerificationCoherence(
            siteDao, pointDao, sessionDao, originalDao, sequenceDao, journalDao, releveDao);
  }

  // --- Fabriques de graphe ---

  private Passage creerPassage(Verdict verdict) {
    return passageDao.insert(
        new Passage(
            null,
            1,
            2026,
            "2026-06-20",
            "21:30:00",
            "05:15:00",
            null,
            StatutWorkflow.VERIFIE,
            verdict,
            null,
            null,
            null,
            idPoint,
            SERIE));
  }

  private Long creerSession(Long idPassage) {
    return sessionDao
        .insert(
            new SessionDEnregistrement(
                null,
                dossier.resolve(PREFIXE.nomDossierSession()).toString(),
                null,
                4096L,
                idPassage))
        .id();
  }

  private Long creerOriginal(Long idSession, String nom) {
    return originalDao
        .insert(
            new EnregistrementOriginal(null, nom, "bruts/" + nom, 12.0, 384000, null, idSession))
        .id();
  }

  private void creerSequence(Long idSession, Long idOriginal, String nom, int index) {
    sequenceDao.insert(
        new SequenceDEcoute(
            null, nom, idOriginal, index, index * 5.0, 5.0, "transformes/" + nom, true, idSession));
  }

  private void creerJournal(Long idSession) {
    journalDao.insert(new JournalDuCapteur(null, "LogPR" + SERIE + ".txt", null, null, idSession));
  }

  private void creerReleve(Long idSession) {
    releveDao.insert(new ReleveClimatique(null, "PaRecPR" + SERIE + "_THLog.csv", null, idSession));
  }

  /** Construit une session entièrement cohérente (originaux + séquences préfixés + journal). */
  private Long creerSessionCoherente(Long idPassage) {
    Long idSession = creerSession(idPassage);
    Long idOriginal = creerOriginal(idSession, NOM_ORIGINAL);
    creerSequence(idSession, idOriginal, PREFIXE.nommerSequence(NOM_ORIGINAL, 0), 0);
    creerSequence(idSession, idOriginal, PREFIXE.nommerSequence(NOM_ORIGINAL, 1), 1);
    creerJournal(idSession);
    return idSession;
  }

  // --- Tests ---

  @Test
  @DisplayName("Passage entièrement cohérent (journal + relevé) : aucun avertissement")
  void passage_coherent_est_conforme() {
    Passage passage = creerPassage(Verdict.OK);
    Long idSession = creerSessionCoherente(passage.id());
    creerReleve(idSession);

    ResultatVerification resultat = verification.verifier(passage);

    assertThat(resultat.estConforme()).isTrue();
    assertThat(resultat.estBloquant()).isFalse();
  }

  @Test
  @DisplayName("R14 : un passage « À jeter » produit une alerte bloquante")
  void verdict_a_jeter_bloque() {
    Passage passage = creerPassage(Verdict.A_JETER);
    Long idSession = creerSessionCoherente(passage.id());
    creerReleve(idSession);

    ResultatVerification resultat = verification.verifier(passage);

    assertThat(resultat.estBloquant()).isTrue();
    assertThat(resultat.messages()).anyMatch(m -> m.contains("À jeter"));
  }

  @Test
  @DisplayName("Échec si les originaux ne sont pas transformés (aucune séquence) : bloquant R10")
  void non_transforme_bloque() {
    Passage passage = creerPassage(Verdict.OK);
    Long idSession = creerSession(passage.id());
    creerOriginal(idSession, NOM_ORIGINAL); // aucun appel à creerSequence
    creerJournal(idSession);

    ResultatVerification resultat = verification.verifier(passage);

    assertThat(resultat.estBloquant()).isTrue();
    assertThat(resultat.messages()).anyMatch(m -> m.contains("séquence"));
  }

  @Test
  @DisplayName("Échec si un original n'a aucune séquence dérivée (transformation partielle)")
  void transformation_partielle_bloque() {
    Passage passage = creerPassage(Verdict.OK);
    Long idSession = creerSession(passage.id());
    Long idOriginal1 = creerOriginal(idSession, NOM_ORIGINAL);
    String nom2 = PREFIXE.nommerOriginal("PaRecPR" + SERIE + "_20260620_220000.wav");
    creerOriginal(idSession, nom2); // second original sans séquence
    creerSequence(idSession, idOriginal1, PREFIXE.nommerSequence(NOM_ORIGINAL, 0), 0);
    creerJournal(idSession);

    ResultatVerification resultat = verification.verifier(passage);

    assertThat(resultat.estBloquant()).isTrue();
    assertThat(resultat.messages()).anyMatch(m -> m.contains("transformé"));
  }

  @Test
  @DisplayName("Échec si le préfixe R6/R7/R8 n'est pas appliqué sur une séquence : bloquant")
  void prefixe_non_conforme_bloque() {
    Passage passage = creerPassage(Verdict.OK);
    Long idSession = creerSession(passage.id());
    Long idOriginal = creerOriginal(idSession, NOM_ORIGINAL);
    creerSequence(idSession, idOriginal, "sequence_sans_prefixe_000.wav", 0);
    creerJournal(idSession);

    ResultatVerification resultat = verification.verifier(passage);

    assertThat(resultat.estBloquant()).isTrue();
    assertThat(resultat.messages()).anyMatch(m -> m.contains(PREFIXE.prefixeFichier()));
  }

  @Test
  @DisplayName("Échec si le journal du capteur est absent : bloquant")
  void journal_absent_bloque() {
    Passage passage = creerPassage(Verdict.OK);
    Long idSession = creerSession(passage.id());
    Long idOriginal = creerOriginal(idSession, NOM_ORIGINAL);
    creerSequence(idSession, idOriginal, PREFIXE.nommerSequence(NOM_ORIGINAL, 0), 0);
    // pas de journal

    ResultatVerification resultat = verification.verifier(passage);

    assertThat(resultat.estBloquant()).isTrue();
    assertThat(resultat.messages()).anyMatch(m -> m.contains("Journal"));
  }

  @Test
  @DisplayName("Relevé climatique absent (R20) : alerte soft, non bloquante")
  void releve_absent_est_soft() {
    Passage passage = creerPassage(Verdict.OK);
    creerSessionCoherente(passage.id()); // journal présent, pas de relevé

    ResultatVerification resultat = verification.verifier(passage);

    assertThat(resultat.estBloquant()).as("relevé absent = soft, jamais bloquant").isFalse();
    assertThat(resultat.estConforme()).isFalse();
    assertThat(resultat.messages()).anyMatch(m -> m.contains("Relevé climatique"));
  }

  @Test
  @DisplayName("Échec si aucune session n'est rattachée au passage : bloquant")
  void session_absente_bloque() {
    Passage passage = creerPassage(Verdict.OK); // pas de session créée

    ResultatVerification resultat = verification.verifier(passage);

    assertThat(resultat.estBloquant()).isTrue();
    assertThat(resultat.messages()).anyMatch(m -> m.contains("session"));
  }
}
