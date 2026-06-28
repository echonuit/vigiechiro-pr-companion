package fr.univ_amu.iut.analyse.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.analyse.di.AnalyseModule;
import fr.univ_amu.iut.analyse.view.AnalyseController;
import fr.univ_amu.iut.analyse.viewmodel.Regroupement;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.sites.di.SitesModule;
import fr.univ_amu.iut.validation.di.ValidationModule;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'écran transverse **« Espèces & observations »** (feature `analyse`) en PNG, en deux états :
/// inventaire **par espèce** (par défaut) et **par carré** (richesse spécifique). Seede une base SQLite
/// temporaire avec un site, un passage et quelques observations (statuts variés) pour peupler la table.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureAnalyse {

    private static final String ID_UTILISATEUR = "demo-analyse";

    /// Codes de taxons (semés par `V02`) utilisés dans les observations de démonstration.
    private static final String PIPISTRELLE = "Pippip";

    private static final String NOCTULE = "Nyclei";

    private static final String MOLOSSE = "Tadten";

    private CaptureAnalyse() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException | IOException | SQLException probleme) {
                erreur.set(probleme);
            } finally {
                fini.countDown();
            }
        });
        fini.await();
        Platform.exit();
        if (erreur.get() != null) {
            erreur.get().printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void capturer() throws IOException, SQLException {
        Path workspace = Files.createTempDirectory("vc-capture-analyse");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = Guice.createInjector(
                new CommunModule(),
                new PersistenceModule(),
                new SitesModule(),
                new PassageModule(),
                new ValidationModule(),
                new AnalyseModule());
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        seeder(injecteur, source);

        rendre(injecteur, null, sortie.resolve("apercu-analyse.png"));
        rendre(injecteur, Regroupement.PAR_CARRE, sortie.resolve("apercu-analyse-carre.png"));

        System.out.println("Apercus ecrits dans " + sortie.toAbsolutePath());
    }

    /// Charge `Analyse.fxml`, applique éventuellement un regroupement (`Par carré`), puis rend l'écran.
    private static void rendre(Injector injecteur, Regroupement regroupement, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(AnalyseController.class.getResource("Analyse.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        if (regroupement != null && vue.lookup("#choixRegroupement") instanceof ComboBox<?> combo) {
            @SuppressWarnings("unchecked")
            ComboBox<Regroupement> choix = (ComboBox<Regroupement>) combo;
            choix.getSelectionModel().select(regroupement);
        }
        ApercuFx.enregistrerPng(new Scene(vue, 1080, 600), fichier);
    }

    /// Seede l'utilisateur courant, un site/point, un passage et sa séquence, puis quelques observations
    /// (Pipistrelle commune validée ×2, Noctule de Leisler non touchée ×2, sérotine corrigée) pour
    /// peupler l'inventaire (3 espèces, richesse 3 sur le carré).
    private static void seeder(Injector injecteur, SourceDeDonnees source) throws SQLException {
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        long idSequence;
        long idResultats;
        try (Connection cx = source.getConnection()) {
            long idSite = insererCle(
                    cx,
                    "INSERT INTO monitoring_site(square_number, friendly_name, protocol, created_at, user_id)"
                            + " VALUES ('640380', 'Étang de la Tuilière', 'Point fixe standard', '2026-05-01', ?)",
                    ID_UTILISATEUR);
            long idPoint = insererCle(cx, "INSERT INTO listening_point(code, site_id) VALUES ('A1', ?)", idSite);
            executer(cx, "INSERT INTO recorder(serial_number) VALUES ('SN-1')");
            long idPassage = insererCle(
                    cx,
                    "INSERT INTO passage(passage_number, year, recording_date, start_time, end_time,"
                            + " workflow_status, point_id, recorder_id)"
                            + " VALUES (2, 2026, '2026-06-22', '21:00', '05:00', 'Vérifié', ?, 'SN-1')",
                    idPoint);
            long idSession =
                    insererCle(cx, "INSERT INTO recording_session(root_path, passage_id) VALUES ('/ws', ?)", idPassage);
            long idOriginal = insererCle(
                    cx,
                    "INSERT INTO original_recording(file_name, file_path, session_id) VALUES ('a.wav', '/ws/a.wav', ?)",
                    idSession);
            idSequence = insererCle(
                    cx,
                    "INSERT INTO listening_sequence(file_name, original_recording_id, file_path, session_id)"
                            + " VALUES ('a_000.wav', ?, '/ws/a_000.wav', ?)",
                    idOriginal,
                    idSession);
            idResultats = insererCle(
                    cx,
                    "INSERT INTO identification_results(file_path, detected_format, imported_at, passage_id)"
                            + " VALUES ('/ws/obs.csv', 'Vu', '2026-06-23', ?)",
                    idPassage);
        }
        ObservationDao observations = injecteur.getInstance(ObservationDao.class);
        observations.insererTout(java.util.List.of(
                validee(PIPISTRELLE, idSequence, idResultats),
                validee(PIPISTRELLE, idSequence, idResultats),
                nonTouchee(NOCTULE, idSequence, idResultats),
                nonTouchee(NOCTULE, idSequence, idResultats),
                corrigee(NOCTULE, MOLOSSE, idSequence, idResultats)));
    }

    private static Observation validee(String code, long idSequence, long idResultats) {
        return new Observation(
                null,
                idSequence,
                0.5,
                3.0,
                45000,
                code,
                0.9,
                null,
                code,
                0.95,
                null,
                false,
                ModeValidation.MANUEL,
                idResultats);
    }

    private static Observation nonTouchee(String codeTadarida, long idSequence, long idResultats) {
        return new Observation(
                null,
                idSequence,
                null,
                null,
                null,
                codeTadarida,
                0.7,
                null,
                null,
                null,
                null,
                false,
                ModeValidation.NON_VALIDE,
                idResultats);
    }

    private static Observation corrigee(
            String codeTadarida, String codeObservateur, long idSequence, long idResultats) {
        return new Observation(
                null,
                idSequence,
                null,
                null,
                null,
                codeTadarida,
                0.6,
                null,
                codeObservateur,
                0.8,
                null,
                false,
                ModeValidation.MANUEL,
                idResultats);
    }

    private static long insererCle(Connection cx, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = cx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
            try (ResultSet cles = ps.getGeneratedKeys()) {
                cles.next();
                return cles.getLong(1);
            }
        }
    }

    private static void executer(Connection cx, String sql) throws SQLException {
        try (Statement st = cx.createStatement()) {
            st.execute(sql);
        }
    }
}
