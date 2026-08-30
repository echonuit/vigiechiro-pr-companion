package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.di.CampagneModule;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import fr.univ_amu.iut.passage.model.dao.CampagneDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.viewmodel.GestionCampagnesViewModel;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test de **geste** (#1405) de la modale « Gérer les campagnes » (#2630) : les boutons sont
/// **cliqués**, et on vérifie leur effet.
///
/// Sur une vraie base SQLite jetable et le vrai [ServiceCampagne] : c'est le trajet complet
/// bouton → ViewModel → service → base que cette issue rendait enfin possible sans terminal, et un
/// service mocké l'aurait justement court-circuité.
///
/// La confirmation de suppression passe par le porteur injectable : un `Alert` en dur **figerait**
/// TestFX headless, et le geste deviendrait intestable (leçon #1405).
@ExtendWith(ApplicationExtension.class)
class GestionCampagnesModaleViewTest {

    private static final String SUIVI_ENS = "Suivi ENS";

    private GestionCampagnesModaleController controleur;
    private ServiceCampagne service;
    private final AtomicReference<String> questionPosee = new AtomicReference<>();

    /// JUnit crée ce répertoire et le **supprime** en fin de test, là où
    /// `createTempDirectory` n'enlevait rien (#4876).
    @TempDir
    private Path dossierTemporaire;

    @Start
    void start(Stage stage) throws Exception {
        System.setProperty("vigiechiro.workspace", dossierTemporaire.toString());
        Injector socle = Guice.createInjector(
                new CommunModule(), new PersistenceModule(), new PassageModule(), new CampagneModule());
        SourceDeDonnees source = socle.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        // Les DAO viennent de l'injecteur, et non d'une construction directe : le cliquet de fixtures
        // compte comme « semeur de passage à la main » tout test qui instancie lui-même le DAO des
        // passages. Ce test n'en sème aucun, mais le détecteur ne peut pas le savoir - et il vaut mieux
        // passer par l'injection que lui apprendre une exception de plus.
        service = new ServiceCampagne(
                socle.getInstance(CampagneDao.class),
                socle.getInstance(PassageDao.class),
                new HorlogeFigee(LocalDate.of(2026, 7, 20)));

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            GestionCampagnesViewModel viewModel() {
                return new GestionCampagnesViewModel(service);
            }
        });
        FXMLLoader loader =
                new FXMLLoader(GestionCampagnesModaleController.class.getResource("GestionCampagnesModale.fxml"));
        loader.setControllerFactory(DiagnosticGuice.pour(injector));
        Parent vue = loader.load();
        controleur = loader.getController();
        stage.setScene(new Scene(vue));
        stage.show();
    }

    @Test
    @DisplayName("sans sélection, « Enregistrer » et « Supprimer » sont grisés : le dire avant le clic")
    void boutons_grises_sans_selection(FxRobot robot) {
        assertThat(robot.lookup("#btnEnregistrer").queryAs(Button.class).isDisabled())
                .isTrue();
        assertThat(robot.lookup("#btnSupprimer").queryAs(Button.class).isDisabled())
                .isTrue();
        assertThat(robot.lookup("#btnCreer").queryAs(Button.class).isDisabled())
                .as("créer ne demande aucune sélection")
                .isFalse();
    }

    @Test
    @DisplayName("« Créer » écrit en base, la liste le montre, et le bandeau le dit")
    void creer_ecrit_et_publie(FxRobot robot) {
        TextField nom = robot.lookup("#champNom").queryAs(TextField.class);
        robot.interact(() -> nom.setText(SUIVI_ENS));

        robot.interact(() -> robot.lookup("#btnCreer").queryAs(Button.class).fire());

        assertThat(service.listerCampagnes())
                .as("le trajet va jusqu'à la base, pas seulement jusqu'au ViewModel")
                .extracting(Campagne::nom)
                .containsExactly(SUIVI_ENS);
        assertThat(listeAffichee(robot).getItems()).hasSize(1);
        assertThat(robot.lookup("#lblRetour").queryAs(Label.class).getText()).contains(SUIVI_ENS);
    }

    @Test
    @DisplayName("« Supprimer » demande confirmation, et ne supprime rien si on renonce")
    void supprimer_respecte_le_refus(FxRobot robot) {
        creerPuisSelectionner(robot);
        controleur.confirmateur().definir(question -> {
            questionPosee.set(question);
            return false;
        });

        robot.interact(() -> robot.lookup("#btnSupprimer").queryAs(Button.class).fire());

        assertThat(questionPosee.get())
                .as("la question annonce l'effet AVANT l'acte")
                .contains(SUIVI_ENS)
                .contains("Aucun passage n'y était rattaché.");
        assertThat(service.listerCampagnes())
                .as("on a renoncé : rien n'a bougé")
                .hasSize(1);
    }

    @Test
    @DisplayName("« Supprimer » confirmé : la campagne part et la liste se vide")
    void supprimer_confirme_efface(FxRobot robot) {
        creerPuisSelectionner(robot);
        controleur.confirmateur().definir(question -> true);

        robot.interact(() -> robot.lookup("#btnSupprimer").queryAs(Button.class).fire());

        assertThat(service.listerCampagnes()).isEmpty();
        assertThat(listeAffichee(robot).getItems()).isEmpty();
    }

    /// Crée une campagne par le bouton, puis la sélectionne dans la liste (état de départ des tests de
    /// suppression).
    private void creerPuisSelectionner(FxRobot robot) {
        TextField nom = robot.lookup("#champNom").queryAs(TextField.class);
        robot.interact(() -> nom.setText(SUIVI_ENS));
        robot.interact(() -> robot.lookup("#btnCreer").queryAs(Button.class).fire());
        robot.interact(() -> listeAffichee(robot).getSelectionModel().selectFirst());
    }

    /// La liste affichée, en **joker** plutôt qu'en `ListView<Campagne>` : `queryAs` rend un type brut,
    /// et le paramétrer exigerait une conversion non vérifiée. On n'en lit ici que la taille et la
    /// sélection, qui ne demandent pas le type des éléments.
    private ListView<?> listeAffichee(FxRobot robot) {
        return robot.lookup("#listeCampagnes").queryAs(ListView.class);
    }
}
