package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.analyse.view.NavigationAnalyse;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import org.testfx.util.WaitForAsyncUtils;

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

    /// Retenu par le semis : #3840 revalide **cette** séquence depuis la vue audio.
    private long idSequenceSemee;

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
    void analyse_ecouter_ouvre_la_validation_ciblee(FxRobot robot) throws TimeoutException {
        NavigationViewModel navigation = injector.getInstance(NavigationViewModel.class);

        // 1) Ouvrir l'écran transverse (navigation socle réelle). L'inventaire est chargé hors du fil
        // JavaFX (AnalyseController.chargerObservations, occupation.occuper) : sans cette attente, la
        // table est encore vide quand l'assertion tombe, un échec qui ne se produit que sur une machine
        // lente, donc en CI.
        robot.interact(() -> injector.getInstance(NavigationAnalyse.class).ouvrir());
        assertThat(navigation.getVueCourante()).isEqualTo("analyse");
        TableView<?> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !especes.getItems().isEmpty());
        assertThat(especes.getItems()).hasSize(1);

        // 2) Sélectionner l'espèce → son détail liste l'observation seedée (chargé en direct sur le fil
        // JavaFX par le listener de sélection, donc synchrone : pas d'attente nécessaire ici).
        robot.interact(() -> especes.getSelectionModel().select(0));
        TableView<?> observations = robot.lookup("#tableObservations").queryAs(TableView.class);
        assertThat(observations.getItems()).hasSize(1);

        // 3) Sélectionner la détection puis « Écouter / valider ».
        robot.interact(() -> observations.getSelectionModel().select(0));
        robot.interact(robot.lookup("#boutonEcouter").queryAs(Button.class)::fire);

        // 4) On est sur la vue audio unifiée, pré-focalisée sur la bonne observation. L'ouverture
        // (SonsValidationController.ouvrirSur) est elle aussi asynchrone (occupation.occuper) : la
        // sélection de la ligne cible n'est posée que dans le callback de succès.
        assertThat(navigation.getVueCourante()).isEqualTo("audio");
        TableView<?> tableValidation = robot.lookup("#tableObservations").queryAs(TableView.class);
        WaitForAsyncUtils.waitFor(
                5, TimeUnit.SECONDS, () -> tableValidation.getSelectionModel().getSelectedItem() != null);
        Object selection = tableValidation.getSelectionModel().getSelectedItem();
        assertThat(selection).isInstanceOf(LigneObservationAudio.class);
        assertThat(((LigneObservationAudio) selection).idObservation()).isEqualTo(idObservation);
    }

    @Test
    @DisplayName("#3840 : un taxon remplacé depuis l'audio est relu au retour sur Analyse")
    void un_taxon_remplace_est_relu_au_retour(FxRobot robot) throws TimeoutException {
        NavigationViewModel navigation = injector.getInstance(NavigationViewModel.class);
        Navigateur navigateur = injector.getInstance(Navigateur.class);

        robot.interact(() -> injector.getInstance(NavigationAnalyse.class).ouvrir());
        TableView<EspeceAgregee> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !especes.getItems().isEmpty());
        assertThat(especes.getItems())
                .extracting(EspeceAgregee::code)
                .as("l'inventaire de départ")
                .containsExactly("Pippip");

        // Ouvrir la vue audio EMPILE (NavigationAudio) : c'est ce qui rend un retour possible, et donc ce
        // qui rend l'affirmation de ce test vraie. Sans empilement, il n'y aurait rien à prouver.
        robot.interact(() -> especes.getSelectionModel().select(0));
        TableView<?> observations = robot.lookup("#tableObservations").queryAs(TableView.class);
        robot.interact(() -> observations.getSelectionModel().select(0));
        robot.interact(robot.lookup("#boutonEcouter").queryAs(Button.class)::fire);
        assertThat(navigation.getVueCourante()).isEqualTo("audio");

        // Le geste de l'écran audio, pris au niveau du modèle : `ActionsRevueAudio` appelle exactement
        // ceci. L'IHM de la revue est couverte par le test voisin ; ce qui est en jeu ici est la
        // FRAÎCHEUR de l'écran qu'on a quitté.
        //
        // **Deux validations, et c'est tout le sujet.** Une observation « manuelle » est celle dont
        // `results_id` est nul : la séquence semée en portant un, la PREMIÈRE validation en *insère* une
        // nouvelle - et cette écriture-là, structurelle, annonce. Seule la SECONDE remplace un taxon par
        // un `update`, et c'est celle-là qui ne signale rien. Ma première version de ce test n'en faisait
        // qu'une et prouvait donc le contraire de ce qu'elle affirmait.
        ValidationManuelle validation = injector.getInstance(ValidationManuelle.class);
        robot.interact(() -> validation.valider(idSequenceSemee, "Nyclei"));

        // **Cette attente est le test.** L'insertion annonce, et Analyse - toujours dans l'historique,
        // donc toujours abonnée - se recharge en tâche de fond. Sans attendre que ce rechargement ait
        // abouti, le remplacement qui suit tombe pendant la lecture et se fait relire **par elle** : le
        // test passe alors sans que le retour y soit pour rien, et reste vert même en retirant
        // `RafraichirAuRetour` d'Analyse. Je l'ai constaté sur ma première version, en la mutant.
        WaitForAsyncUtils.waitFor(
                5,
                TimeUnit.SECONDS,
                () -> especes.getItems().stream().anyMatch(espece -> "Nyclei".equals(espece.code())));

        // Le remplacement, seul, une fois l'annonce précédente entièrement consommée. Celui-ci ne signale
        // rien : plus personne ne préviendra Analyse tant qu'on n'y sera pas revenu.
        robot.interact(() -> validation.valider(idSequenceSemee, "Barbar"));

        robot.interact(navigateur::revenir);
        assertThat(navigation.getVueCourante()).isEqualTo("analyse");

        // Le cœur de #3840. Ce remplacement n'annonce RIEN : il passe par un `update`, et aucun des quatre
        // comptes de l'accueil ne bouge (ADR 3537). C'est `RafraichirAuRetour` qui rattrape, et lui seul.
        // Analyse lit le taxon RETENU, `COALESCE(taxon_observer, taxon_tadarida)` : l'espèce change donc.
        TableView<EspeceAgregee> apresRetour = robot.lookup("#tableEspeces").queryAs(TableView.class);
        WaitForAsyncUtils.waitFor(
                5, TimeUnit.SECONDS, () -> apresRetour.getItems().size() > 1);
        assertThat(apresRetour.getItems())
                .extracting(EspeceAgregee::code)
                .as("l'écran quitté a relu sa donnée au retour. « Nyclei » signifierait qu'il en est resté"
                        + " à la seule écriture qui ait annoncé, et manqué le remplacement qui a suivi")
                .containsExactlyInAnyOrder("Pippip", "Barbar");
    }

    /// Sème un site/point/passage/séquence/résultats puis une observation validée (Pipistrelle), et
    /// renvoie l'id de l'observation. Pas d'import : insertion directe (la chaîne FK n'a pas de DAO ici).
    private long seeder(SourceDeDonnees source) throws SQLException {
        long idSequence;
        long idResultats;
        try (Connection cx = source.getConnection()) {
            // Site, point et enregistreur viennent de la fixture, avec le passage.
            // Le SQL d'origine écrivait le protocole « Point fixe standard », un libellé que
            // `Protocole` ne connaît plus (il stocke « PointFixeStandard ») : la ligne était illisible par
            // `SiteDao`, et ce test ne relisait jamais le site.
            long idPassage = JeuDeDonneesPassage.dans(source)
                    .utilisateur(ID_USER)
                    .carre("640380")
                    .nomSite("Étang de la Tuilière")
                    .point("A1")
                    .enregistreur("SN-1")
                    .nuit(1, 2026, "2026-06-20")
                    .heures("21:00", "05:00")
                    .statut(StatutWorkflow.IMPORTE)
                    .semerPassage()
                    .idPassage();
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
                45,
                "Pippip",
                0.9,
                null,
                "Pippip",
                0.95,
                null,
                false,
                ModeValidation.MANUEL,
                idResultats,
                false,
                null,
                null,
                null,
                null,
                null);
        idSequenceSemee = idSequence;
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
