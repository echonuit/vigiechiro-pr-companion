package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.ExportVuCsv;
import fr.univ_amu.iut.validation.model.LigneObservation;
import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ParserCsvTadarida;
import fr.univ_amu.iut.validation.model.ResultatParseTadarida;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.VueValidation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests de bout en bout du [ServiceValidation] sur une base SQLite jetable (`@TempDir` +
/// [MigrationSchema]), avec les vrais DAO des features `validation` et `passage` et une
/// [HorlogeFigee]. Couvre l'import en masse, R15 (validation), R16 (correction), R24
/// (`validation_mode` persisté), R18 (modes inventaire / activité), R17 (export `_Vu`) et le
/// round-trip import → export → reparse.
///
/// Les taxons sont déjà semés par `V02` (Pippip, Nyclei, noise…) : le jeu de test n'utilise que
/// des codes semés et des séquences semées à la main, pour rester autonome.
class ServiceValidationTest {

  private static final String ID_USER = "u-1";

  @TempDir Path dossier;

  private ObservationDao observationDao;
  private ResultatsIdentificationDao resultatsDao;
  private ServiceValidation service;
  private final ParserCsvTadarida parser = new ParserCsvTadarida();

  private long idPassage;
  private long idSession;

  @BeforeEach
  void preparer() {
    SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
    new MigrationSchema(source).migrer();
    new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));

    SiteDao siteDao = new SiteDao(source);
    PointDao pointDao = new PointDao(source);
    PassageDao passageDao = new PassageDao(source);
    SessionDao sessionDao = new SessionDao(source);
    EnregistrementOriginalDao originalDao = new EnregistrementOriginalDao(source);
    SequenceDao sequenceDao = new SequenceDao(source);
    EnregistreurDao enregistreurDao = new EnregistreurDao(source);
    TaxonDao taxonDao = new TaxonDao(source);
    observationDao = new ObservationDao(source);
    resultatsDao = new ResultatsIdentificationDao(source);

    Site site =
        siteDao.insert(
            new Site(null, "640380", "Test", Protocole.STANDARD, null, "2026-04-01", ID_USER));
    PointDEcoute point = pointDao.insert(new PointDEcoute(null, "Z1", null, null, null, site.id()));
    enregistreurDao.insert(new Enregistreur("1925492", "V1.01", null));
    Passage passage =
        passageDao.insert(
            new Passage(
                null,
                2,
                2026,
                "2026-04-22",
                "20:00:00",
                "06:00:00",
                null,
                StatutWorkflow.DEPOSE,
                null,
                null,
                null,
                null,
                point.id(),
                "1925492"));
    idPassage = passage.id();
    SessionDEnregistrement session =
        sessionDao.insert(new SessionDEnregistrement(null, "/ws/session", null, null, idPassage));
    idSession = session.id();

    // Trois séquences en base, nommées comme la base (avec extension .wav).
    insererSequence(originalDao, sequenceDao, "seqA");
    insererSequence(originalDao, sequenceDao, "seqB");
    insererSequence(originalDao, sequenceDao, "seqC");

    service =
        new ServiceValidation(
            resultatsDao,
            observationDao,
            taxonDao,
            sessionDao,
            sequenceDao,
            parser,
            new ExportVuCsv(),
            new UniteDeTravail(source),
            new HorlogeFigee(LocalDate.of(2026, 5, 31)));
  }

  private void insererSequence(
      EnregistrementOriginalDao originalDao, SequenceDao sequenceDao, String base) {
    EnregistrementOriginal original =
        originalDao.insert(
            new EnregistrementOriginal(
                null, base + ".wav", "/ws/bruts/" + base + ".wav", 5.0, 384000, null, idSession));
    sequenceDao.insert(
        new SequenceDEcoute(
            null,
            base + "_000.wav",
            original.id(),
            0,
            0.0,
            5.0,
            "/ws/transformes/" + base + "_000.wav",
            false,
            idSession));
  }

  /// Écrit un petit CSV Brut (tout guillemeté) dans le `@TempDir` et renvoie son chemin.
  private Path ecrireBrut() {
    String contenu =
        guillemets(
                "nom du fichier",
                "temps_debut",
                "temps_fin",
                "frequence_mediane",
                "tadarida_taxon",
                "tadarida_probabilite",
                "tadarida_taxon_autre",
                "observateur_taxon",
                "observateur_probabilite",
                "validateur_taxon",
                "validateur_probabilite")
            + guillemets("seqA_000", "0.3", "3.9", "153", "noise", "0.93", "", "", "", "", "")
            + guillemets("seqA_000", "0.4", "4.1", "45", "Pippip", "0.80", "Nyclei", "", "", "", "")
            + guillemets("seqB_000", "1.0", "2.0", "40", "Pippip", "0.60", "", "", "", "", "")
            + guillemets("seqC_000", "0.0", "5.0", "30", "Nyclei", "0.70", "", "", "", "", "");
    Path fichier = dossier.resolve("entree_observations.csv");
    try {
      Files.writeString(fichier, contenu, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return fichier;
  }

  private static String guillemets(String... champs) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < champs.length; i++) {
      if (i > 0) {
        sb.append(';');
      }
      sb.append('"').append(champs[i]).append('"');
    }
    return sb.append('\n').toString();
  }

  private Observation observation(String taxonTadarida) {
    return observationDao.findByResults(premiereImportation()).stream()
        .filter(o -> o.taxonTadarida().equals(taxonTadarida))
        .findFirst()
        .orElseThrow();
  }

  private Long premiereImportation() {
    return resultatsDao.findByPassage(idPassage).orElseThrow().id();
  }

  @Test
  @DisplayName(
      "Import : crée les résultats (format Brut, date de l'horloge) et insère les 4 observations")
  void importe_les_observations_en_masse() {
    ResultatsIdentification resultats = service.importer(idPassage, ecrireBrut());

    assertThat(resultats.id()).isNotNull();
    assertThat(resultats.formatDetecte()).isEqualTo("Brut");
    assertThat(resultats.dateImport()).isEqualTo("2026-05-31T00:00");
    assertThat(resultats.idPassage()).isEqualTo(idPassage);

    List<Observation> observations = observationDao.findByResults(resultats.id());
    assertThat(observations).hasSize(4);
    assertThat(observations)
        .extracting(Observation::taxonTadarida)
        .containsExactlyInAnyOrder("noise", "Pippip", "Pippip", "Nyclei");
    // La 2e proposition « Nyclei » (seule, semée) est conservée comme FK.
    assertThat(observations).filteredOn(o -> "Nyclei".equals(o.taxonAutreTadarida())).hasSize(1);
  }

  @Test
  @DisplayName("Import : chaque observation est raccrochée à sa séquence par le nom de fichier")
  void raccroche_les_observations_aux_sequences() {
    Long idResultats = service.importer(idPassage, ecrireBrut()).id();

    // 4 observations réparties sur 3 séquences distinctes (seqA porte 2 lignes).
    assertThat(observationDao.findByResults(idResultats))
        .extracting(Observation::idSequence)
        .doesNotContainNull();
  }

  @Test
  @DisplayName(
      "Import atomique : un second import (passage_id unique) échoue sans altérer le premier")
  void import_second_echec_preserve_le_premier() {
    Long idResultats1 = service.importer(idPassage, ecrireBrut()).id();
    int nbObs1 = observationDao.findByResults(idResultats1).size();

    // Le 2e import insère d'abord le jeu de résultats : passage_id étant unique, l'écriture échoue
    // et la transaction est annulée (rollback) sans laisser de jeu vide durable.
    assertThatThrownBy(() -> service.importer(idPassage, ecrireBrut()))
        .isInstanceOf(DataAccessException.class);

    assertThat(resultatsDao.findByPassage(idPassage).orElseThrow().id()).isEqualTo(idResultats1);
    assertThat(observationDao.findByResults(idResultats1)).hasSize(nbObs1);
  }

  @Test
  @DisplayName("Import refusé (dur) si une séquence du CSV n'existe pas en base")
  void import_refuse_si_sequence_absente() {
    String contenu =
        guillemets("nom du fichier", "tadarida_taxon")
            + guillemets("sequence_inconnue_000", "Pippip");
    Path fichier = dossier.resolve("inconnue.csv");
    try {
      Files.writeString(fichier, contenu, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    assertThatThrownBy(() -> service.importer(idPassage, fichier))
        .isInstanceOf(RegleMetierException.class)
        .hasMessageContaining("Séquence");
    assertThat(resultatsDao.findByPassage(idPassage))
        .as("rien ne doit être écrit si l'import est refusé")
        .isEmpty();
  }

  @Test
  @DisplayName("Import refusé (dur) si un taxon Tadarida n'est pas semé")
  void import_refuse_si_taxon_inconnu() {
    String contenu =
        guillemets("nom du fichier", "tadarida_taxon") + guillemets("seqA_000", "Tetvir");
    Path fichier = dossier.resolve("taxon_inconnu.csv");
    try {
      Files.writeString(fichier, contenu, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    assertThatThrownBy(() -> service.importer(idPassage, fichier))
        .isInstanceOf(RegleMetierException.class)
        .hasMessageContaining("Taxon Tadarida inconnu");
  }

  @Test
  @DisplayName(
      "R15 : valider fixe taxon observateur = taxon Tadarida, prob renseignée, mode manuel")
  void r15_valide_une_observation() {
    service.importer(idPassage, ecrireBrut());
    Observation noise = observation("noise");

    Observation validee = service.valider(noise.id());

    assertThat(validee.taxonObservateur()).isEqualTo("noise");
    assertThat(validee.probObservateur()).isEqualTo(0.93);
    assertThat(validee.modeValidation()).isEqualTo(ModeValidation.MANUEL);
    assertThat(service.statut(validee)).isEqualTo(StatutObservation.VALIDEE);

    Observation relue = observationDao.findById(noise.id()).orElseThrow();
    assertThat(relue.modeValidation()).as("R24 : manuel persisté").isEqualTo(ModeValidation.MANUEL);
    assertThat(relue.taxonObservateur()).isEqualTo("noise");
  }

  @Test
  @DisplayName("R16 : corriger fixe un taxon observateur différent → statut CORRIGEE")
  void r16_corrige_une_observation() {
    service.importer(idPassage, ecrireBrut());
    Observation noise = observation("noise");

    Observation corrigee = service.corriger(noise.id(), "Pippip", 0.99);

    assertThat(corrigee.taxonObservateur()).isEqualTo("Pippip");
    assertThat(corrigee.taxonTadarida()).isEqualTo("noise");
    assertThat(corrigee.modeValidation()).isEqualTo(ModeValidation.MANUEL);
    assertThat(service.statut(corrigee)).isEqualTo(StatutObservation.CORRIGEE);
  }

  @Test
  @DisplayName("Corriger vers un taxon inconnu est refusé (dur)")
  void corriger_taxon_inconnu_refuse() {
    service.importer(idPassage, ecrireBrut());
    Observation noise = observation("noise");

    assertThatThrownBy(() -> service.corriger(noise.id(), "ZZZZZZ", 0.5))
        .isInstanceOf(RegleMetierException.class);
  }

  @Test
  @DisplayName(
      "R18 inventaire : valider une espèce propage en auto aux autres détections non touchées")
  void r18_mode_inventaire_propage_en_auto() {
    Long idResultats = service.importer(idPassage, ecrireBrut()).id();
    Observation unPippip =
        observationDao.findByResults(idResultats).stream()
            .filter(o -> o.taxonTadarida().equals("Pippip"))
            .findFirst()
            .orElseThrow();

    List<Observation> affectees = service.validerSelonMode(unPippip.id(), ModeRevue.INVENTAIRE);

    assertThat(affectees).as("le pivot manuel + l'autre Pippip propagé").hasSize(2);
    List<Observation> pippips =
        observationDao.findByResults(idResultats).stream()
            .filter(o -> o.taxonTadarida().equals("Pippip"))
            .toList();
    assertThat(pippips)
        .allSatisfy(o -> assertThat(o.taxonObservateur()).isEqualTo("Pippip"))
        .extracting(Observation::modeValidation)
        .containsExactlyInAnyOrder(ModeValidation.MANUEL, ModeValidation.AUTO);

    // Les autres espèces (noise, Nyclei) ne sont pas touchées par la propagation.
    assertThat(observation("noise").modeValidation()).isEqualTo(ModeValidation.NON_VALIDE);
    assertThat(observation("Nyclei").modeValidation()).isEqualTo(ModeValidation.NON_VALIDE);
  }

  @Test
  @DisplayName("R18 activité : valider ne propage pas, seule l'observation visée change")
  void r18_mode_activite_ne_propage_pas() {
    Long idResultats = service.importer(idPassage, ecrireBrut()).id();
    Observation unPippip =
        observationDao.findByResults(idResultats).stream()
            .filter(o -> o.taxonTadarida().equals("Pippip"))
            .findFirst()
            .orElseThrow();

    List<Observation> affectees = service.validerSelonMode(unPippip.id(), ModeRevue.ACTIVITE);

    assertThat(affectees).hasSize(1);
    long valides =
        observationDao.findByResults(idResultats).stream()
            .filter(o -> o.taxonObservateur() != null)
            .count();
    assertThat(valides).as("une seule observation validée").isEqualTo(1);
  }

  @Test
  @DisplayName("R17 : l'export _Vu conserve les colonnes Tadarida des lignes non touchées")
  void r17_export_conserve_les_non_touchees() {
    Long idResultats = service.importer(idPassage, ecrireBrut()).id();
    Observation noise = observation("noise");
    service.valider(noise.id()); // seule cette ligne est touchée

    String csv = service.exporterVersChaine(idResultats, false);
    ResultatParseTadarida reparse = parser.parser(csv);

    // La ligne noise est validée → observateur = noise.
    assertThat(reparse.lignes())
        .anySatisfy(
            l -> {
              assertThat(l.taxonTadarida()).isEqualTo("noise");
              assertThat(l.taxonObservateur()).isEqualTo("noise");
            });
    // Les lignes non touchées (Nyclei) conservent leur taxon Tadarida et n'ont pas d'observateur.
    assertThat(reparse.lignes())
        .filteredOn(l -> "Nyclei".equals(l.taxonTadarida()))
        .allSatisfy(l -> assertThat(l.taxonObservateur()).isNull());
  }

  @Test
  @DisplayName("Round-trip : import → exporter(_Vu) → reparse redonne les mêmes lignes")
  void round_trip_import_export_reparse() {
    Long idResultats = service.importer(idPassage, ecrireBrut()).id();

    Path vu = service.exporter(idResultats, dossier.resolve("sortie_Vu.csv"), false);
    ResultatParseTadarida reparse = parser.parser(vu);

    List<LigneObservation> attendues = service.lignesAExporter(idResultats);
    assertThat(reparse.lignes()).isEqualTo(attendues);
    assertThat(reparse.lignes()).hasSize(4);
  }

  @Test
  @DisplayName("Import refusé si le passage n'a pas de session d'enregistrement")
  void import_refuse_sans_session() {
    // Un passage sans session : on en fabrique un en pointant un idPassage inexistant.
    assertThatThrownBy(() -> service.importer(999_999L, ecrireBrut()))
        .isInstanceOf(RegleMetierException.class)
        .hasMessageContaining("session");
  }

  @Test
  @DisplayName("chargerValidation : le jeu de résultats importé + ses observations (statut dérivé)")
  void charger_validation_avec_resultats() {
    long idResultats = service.importer(idPassage, ecrireBrut()).id();

    VueValidation vue = service.chargerValidation(idPassage);

    assertThat(vue.idResultats()).isEqualTo(idResultats);
    assertThat(vue.observations()).isNotEmpty();
    assertThat(vue.observations())
        .allSatisfy(o -> assertThat(o.statut()).isEqualTo(StatutObservation.NON_TOUCHEE));
  }

  @Test
  @DisplayName("chargerValidation : vue vide tant qu'aucun CSV n'a été importé pour le passage")
  void charger_validation_sans_resultats() {
    VueValidation vue = service.chargerValidation(idPassage);

    assertThat(vue.idResultats()).isNull();
    assertThat(vue.observations()).isEmpty();
  }
}
