package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
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
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.approvaltests.Approvals;
import org.approvaltests.reporters.QuietReporter;
import org.approvaltests.reporters.UseReporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Test d'intégration de la commande `exporter-vu` : on sème en base un passage avec sa
/// session, une séquence d'écoute, un jeu de résultats d'identification et deux observations (une
/// validée, une non touchée), puis on invoque la CLI. Le fichier `_Vu.csv` produit (par
/// délégation à `ServiceValidation`, l'export canonique réinjectable de la feature
/// `validation`) est figé par **approval testing** (golden
/// `CliExportVuTest.exporter_vu_produit_le_csv_vu.approved.txt`).
///
/// [QuietReporter] : aucun outil graphique en cas d'écart (CI / sans affichage). C'est le
/// *test cible* du flux d'export (le format du CSV est par ailleurs couvert au niveau de la
/// feature `validation`).
@UseReporter(QuietReporter.class)
class CliExportVuTest {

    private static final String ID_USER = "u-vu";
    private static final String SERIE = "1925492";
    private static final String NOM_SEQUENCE = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_203922_000.wav";

    @TempDir
    Path workspace;

    private Injector injecteur;
    private Cli cli;
    private Long idPassage;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer(); // crée le schéma + sème les taxons

        // Chaîne FK : utilisateur -> site -> point -> enregistreur -> passage -> session -> original
        // -> séquence -> résultats -> observations.
        injecteur.getInstance(UtilisateurDao.class).insert(new Utilisateur(ID_USER, "Testeuse"));
        Site site = injecteur
                .getInstance(SiteDao.class)
                .insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-05-31", ID_USER));
        PointDEcoute point =
                injecteur.getInstance(PointDao.class).insert(new PointDEcoute(null, "Z1", 43.5, 5.4, null, site.id()));
        injecteur.getInstance(EnregistreurDao.class).insert(new Enregistreur(SERIE, "V1.01, T4.1", null));
        Passage passage = injecteur
                .getInstance(PassageDao.class)
                .insert(new Passage(
                        null,
                        2,
                        2026,
                        "2026-04-22",
                        "20:25:00",
                        "07:47:00",
                        null,
                        StatutWorkflow.DEPOSE,
                        null,
                        null,
                        null,
                        "2026-05-30T10:00",
                        point.id(),
                        SERIE));
        idPassage = passage.id();
        SessionDEnregistrement session = injecteur
                .getInstance(SessionDao.class)
                .insert(new SessionDEnregistrement(null, "/ws/Car640380-2026-Pass2-Z1", 0L, 0L, idPassage));
        EnregistrementOriginal original = injecteur
                .getInstance(EnregistrementOriginalDao.class)
                .insert(new EnregistrementOriginal(
                        null,
                        "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_203922.wav",
                        "/ws/.../bruts/orig.wav",
                        1.5,
                        384000,
                        null,
                        session.id()));
        SequenceDEcoute sequence = injecteur
                .getInstance(SequenceDao.class)
                .insert(new SequenceDEcoute(
                        null,
                        NOM_SEQUENCE,
                        original.id(),
                        0,
                        0.0,
                        5.0,
                        "/ws/.../transformes/seq.wav",
                        false,
                        session.id()));
        ResultatsIdentification resultats = injecteur
                .getInstance(ResultatsIdentificationDao.class)
                .insert(new ResultatsIdentification(
                        null, "/ws/.../transformes/obs.csv", "Vu", "2026-05-30T11:00", idPassage));

        ObservationDao observationDao = injecteur.getInstance(ObservationDao.class);
        observationDao.insert(new Observation(
                null,
                sequence.id(),
                0.5,
                2.5,
                45,
                "Pippip",
                0.98,
                null,
                "Pippip",
                1.0,
                "vue",
                false,
                ModeValidation.MANUEL,
                resultats.id()));
        observationDao.insert(new Observation(
                null,
                sequence.id(),
                1.0,
                3.0,
                38000,
                "Nyclei",
                0.42,
                null,
                null,
                null,
                null,
                false,
                ModeValidation.NON_VALIDE,
                resultats.id()));
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("exporter-vu : écrit le _Vu.csv canonique, code de sortie 0 (golden)")
    void exporter_vu_produit_le_csv_vu() throws IOException {
        Path sortieCsv = workspace.resolve("passage_Vu.csv");
        ByteArrayOutputStream tampon = new ByteArrayOutputStream();

        int code = cli.executer(
                new String[] {"exporter-vu", "--passage", String.valueOf(idPassage), "--sortie", sortieCsv.toString()},
                new PrintStream(tampon, true, StandardCharsets.UTF_8),
                new PrintStream(tampon, true, StandardCharsets.UTF_8));

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(tampon.toString(StandardCharsets.UTF_8)).contains("Export Vu écrit");
        assertThat(Files.exists(sortieCsv)).isTrue();

        Approvals.verify(Files.readString(sortieCsv, StandardCharsets.UTF_8));
    }
}
