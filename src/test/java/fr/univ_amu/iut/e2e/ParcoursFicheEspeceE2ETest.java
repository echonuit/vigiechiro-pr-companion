package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurFiche;
import fr.univ_amu.iut.commun.view.ExecuteurFicheSynchrone;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// **Test E2E** du parcours « Fiche de l'espèce » (#844) : depuis le tableau de bord, ouvrir
/// **Sons & validation** (source `References`), sélectionner un son de référence chiroptère, puis
/// déclencher **☰ → Fiche de l'espèce** ; on vérifie que l'ouverture de lien reçoit bien l'URL de la
/// fiche PNA de l'espèce.
///
/// Injecteur applicatif réel, avec deux surcharges ciblées : un faux [OuvreurDeLien] (enregistre l'URL,
/// aucun navigateur) et l'[ExecuteurFicheSynchrone] (résolution + ouverture synchrones → déterministe,
/// sans réseau — l'URL PNA est directe).
@ExtendWith(ApplicationExtension.class)
class ParcoursFicheEspeceE2ETest {

    private Injector injector;
    private final List<String> urlsOuvertes = new ArrayList<>();

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-fiche");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector =
                Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(new AbstractModule() {
                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        return urlsOuvertes::add;
                    }

                    @Override
                    protected void configure() {
                        bind(ExecuteurFiche.class).to(ExecuteurFicheSynchrone.class);
                    }
                }));
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        semerReferenceChiroptere(source);

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
    @DisplayName("Sons & validation → ☰ Fiche de l'espèce ouvre la fiche PNA de l'espèce sélectionnée")
    void fiche_espece_ouvre_la_fiche_pna(FxRobot robot) {
        NavigationViewModel navigation = injector.getInstance(NavigationViewModel.class);
        assertThat(navigation.getVueCourante()).isEqualTo("accueil");

        robot.clickOn("Sons & validation");
        assertThat(navigation.getVueCourante()).isEqualTo("audio");

        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        assertThat(table.getItems()).as("le son de référence Pippip est listé").isNotEmpty();
        robot.interact(() -> table.getSelectionModel().select(0));

        MenuButton menu = robot.lookup("#menuActions").queryAs(MenuButton.class);
        MenuItem fiche = menu.getItems().stream()
                .filter(item -> item.getText() != null && item.getText().startsWith("Fiche de l'espèce"))
                .findFirst()
                .orElseThrow();
        assertThat(fiche.isDisable()).isFalse();
        assertThat(fiche.getText()).isEqualTo("Fiche de l'espèce (Pipistrelle commune)");

        robot.interact(fiche::fire);

        assertThat(urlsOuvertes)
                .containsExactly(
                        "https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/pipistrelle-commune/");
    }

    /// Sème un son de **référence** identifié Pippip (Pipistrelle commune) pour l'unique utilisateur local
    /// (l'app prend le premier utilisateur comme utilisateur courant) : site → point → passage → session →
    /// original → séquence → observation `is_reference`.
    private static void semerReferenceChiroptere(SourceDeDonnees source) {
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement()) {
            st.execute("INSERT INTO user(local_id, display_name) VALUES ('u-1', 'Testeur E2E')");
            st.execute("INSERT INTO monitoring_site(square_number, protocol, created_at, user_id)"
                    + " VALUES ('640380', 'Point fixe standard', '2026-05-01', 'u-1')");
            st.execute("INSERT INTO listening_point(code, site_id)"
                    + " VALUES ('A1', (SELECT id FROM monitoring_site WHERE square_number = '640380'))");
            st.execute("INSERT INTO recorder(serial_number) VALUES ('SN-1')");
            st.execute("INSERT INTO passage(passage_number, year, recording_date, start_time, end_time,"
                    + " workflow_status, point_id, recorder_id)"
                    + " VALUES (1, 2026, '2026-06-20', '21:00', '05:00', 'Importé',"
                    + " (SELECT id FROM listening_point WHERE code = 'A1'), 'SN-1')");
            st.execute("INSERT INTO recording_session(root_path, passage_id)"
                    + " VALUES ('/ws', (SELECT id FROM passage WHERE passage_number = 1))");
            st.execute(
                    "INSERT INTO original_recording(file_name, file_path, session_id)"
                            + " VALUES ('a.wav', '/ws/bruts/a.wav', (SELECT id FROM recording_session WHERE root_path = '/ws'))");
            st.execute("INSERT INTO listening_sequence(file_name, original_recording_id, file_path, session_id)"
                    + " VALUES ('a_000.wav', (SELECT id FROM original_recording WHERE file_name = 'a.wav'),"
                    + " '/ws/transformes/a_000.wav', (SELECT id FROM recording_session WHERE root_path = '/ws'))");
            st.execute("INSERT INTO observation(sequence_id, taxon_tadarida, prob_tadarida, is_reference)"
                    + " VALUES ((SELECT id FROM listening_sequence WHERE file_name = 'a_000.wav'), 'Pippip', 0.9, 1)");
        } catch (SQLException echec) {
            throw new IllegalStateException("Semis E2E impossible", echec);
        }
    }
}
