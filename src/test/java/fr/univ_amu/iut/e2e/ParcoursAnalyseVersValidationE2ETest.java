package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.analyse.view.NavigationAnalyse;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// **Test E2E de parcours** : depuis la vue transverse **« Espèces & observations »** (`analyse`), on
/// sélectionne une espèce puis une de ses détections et on déclenche **« Écouter / valider »** ; le
/// câblage inter-écran réel (`analyse → OuvrirAudio → SonsValidationController`) doit ouvrir la **vue
/// audio unifiée** sur **toute l'espèce** (source `ParEspece`) **pré-focalisée** sur cette observation.
/// Sur le vrai chrome et le vrai injecteur, base seedée directement (un utilisateur, un passage, une
/// observation).
@ExtendWith(ApplicationExtension.class)
class ParcoursAnalyseVersValidationE2ETest {

    private static final String ID_USER = "u-e2e-analyse";

    private Injector injector;
    private long idObservation;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-analyse");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur E2E"));
        idObservation = seeder(source);

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1280, 860));
        stage.show();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Analyse → « Écouter / valider » ouvre la vue audio pré-focalisée sur l'observation")
    void analyse_ecouter_ouvre_la_validation_ciblee(FxRobot robot) {
        NavigationViewModel navigation = injector.getInstance(NavigationViewModel.class);

        // 1) Ouvrir l'écran transverse (navigation socle réelle).
        robot.interact(() -> injector.getInstance(NavigationAnalyse.class).ouvrir());
        assertThat(navigation.getVueCourante()).isEqualTo("analyse");

        // 2) Sélectionner l'espèce → son détail liste l'observation seedée.
        TableView<?> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        assertThat(especes.getItems()).hasSize(1);
        robot.interact(() -> especes.getSelectionModel().select(0));
        TableView<?> observations = robot.lookup("#tableObservations").queryAs(TableView.class);
        assertThat(observations.getItems()).hasSize(1);

        // 3) Sélectionner la détection puis « Écouter / valider ».
        robot.interact(() -> observations.getSelectionModel().select(0));
        robot.interact(robot.lookup("#boutonEcouter").queryAs(Button.class)::fire);

        // 4) On est sur la vue audio unifiée, pré-focalisée sur la bonne observation.
        assertThat(navigation.getVueCourante()).isEqualTo("audio");
        TableView<?> tableValidation = robot.lookup("#tableObservations").queryAs(TableView.class);
        Object selection = tableValidation.getSelectionModel().getSelectedItem();
        assertThat(selection).isInstanceOf(LigneObservationAudio.class);
        assertThat(((LigneObservationAudio) selection).idObservation()).isEqualTo(idObservation);
    }

    /// Sème un site/point/passage/séquence/résultats puis une observation validée (Pipistrelle), et
    /// renvoie l'id de l'observation. Pas d'import : insertion directe (la chaîne FK n'a pas de DAO ici).
    private long seeder(SourceDeDonnees source) throws SQLException {
        long idSequence;
        long idResultats;
        try (Connection cx = source.getConnection()) {
            long idSite = cle(
                    cx,
                    "INSERT INTO monitoring_site(square_number, friendly_name, protocol, created_at, user_id)"
                            + " VALUES ('640380', 'Étang de la Tuilière', 'Point fixe standard', '2026-05-01', ?)",
                    ID_USER);
            long idPoint = cle(cx, "INSERT INTO listening_point(code, site_id) VALUES ('A1', ?)", idSite);
            executer(cx, "INSERT INTO recorder(serial_number) VALUES ('SN-1')");
            long idPassage = cle(
                    cx,
                    "INSERT INTO passage(passage_number, year, recording_date, start_time, end_time,"
                            + " workflow_status, point_id, recorder_id)"
                            + " VALUES (1, 2026, '2026-06-20', '21:00', '05:00', 'Importé', ?, 'SN-1')",
                    idPoint);
            long idSession =
                    cle(cx, "INSERT INTO recording_session(root_path, passage_id) VALUES ('/ws', ?)", idPassage);
            long idOriginal = cle(
                    cx,
                    "INSERT INTO original_recording(file_name, file_path, session_id) VALUES ('a.wav', '/ws/a.wav', ?)",
                    idSession);
            idSequence = cle(
                    cx,
                    "INSERT INTO listening_sequence(file_name, original_recording_id, file_path, session_id)"
                            + " VALUES ('a_000.wav', ?, '/ws/a_000.wav', ?)",
                    idOriginal,
                    idSession);
            idResultats = cle(
                    cx,
                    "INSERT INTO identification_results(file_path, detected_format, imported_at, passage_id)"
                            + " VALUES ('/ws/obs.csv', 'Vu', '2026-06-21', ?)",
                    idPassage);
        }
        Observation validee = new Observation(
                null,
                idSequence,
                0.5,
                3.0,
                45000,
                "Pippip",
                0.9,
                null,
                "Pippip",
                0.95,
                null,
                false,
                ModeValidation.MANUEL,
                idResultats);
        return injector.getInstance(ObservationDao.class).insert(validee).id();
    }

    private static long cle(Connection cx, String sql, Object... params) throws SQLException {
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
