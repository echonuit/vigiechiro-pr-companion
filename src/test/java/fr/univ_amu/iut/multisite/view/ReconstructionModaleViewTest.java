package fr.univ_amu.iut.multisite.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.multisite.viewmodel.ReconstructionViewModel;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import fr.univ_amu.iut.passage.model.RapportReconstruction;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de la modale **« Reconstruire un passage manquant »** (#1396) : service
/// bouchonné, aucune base. On vérifie que les nuits manquantes s'affichent, que le bouton refuse celle
/// dont le **point d'écoute est inconnu ici** (en disant pourquoi), et que la reconstruction publie un
/// compte rendu **qui énonce ses lacunes**.
@ExtendWith(ApplicationExtension.class)
class ReconstructionModaleViewTest {

    private static final ParticipationOrpheline CONNUE =
            new ParticipationOrpheline("p-connue", "130711", "Z41", "2026-07-03T22:00:00+02:00", true);

    /// Nuit dont le point n'existe pas sur cette machine : reconstruire la rattacherait au mauvais point.
    private static final ParticipationOrpheline INCONNUE =
            new ParticipationOrpheline("p-inconnue", "999999", "A9", "2026-07-05T22:00:00+02:00", false);

    private ServiceReconstructionPassages service;
    private final AtomicBoolean appelantRafraichi = new AtomicBoolean(false);
    private Stage stage;

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        service = mock(ServiceReconstructionPassages.class);
        when(service.orphelines()).thenReturn(List.of(CONNUE, INCONNUE));
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            ReconstructionViewModel viewModel() {
                return new ReconstructionViewModel(Optional.of(service));
            }
        });
        FXMLLoader loader =
                new FXMLLoader(ReconstructionModaleController.class.getResource("ReconstructionModale.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        ReconstructionModaleController controleur = loader.getController();
        controleur.demarrer(() -> appelantRafraichi.set(true));
        stage.setScene(new Scene(vue));
        // Comme NavigationMultisite : le rafraîchissement de l'appelant joue à TOUTE fermeture (#1647).
        stage.setOnHidden(evenement -> controleur.rafraichirSiReconstruit());
        stage.show();
    }

    @Test
    @DisplayName("Les nuits déposées sur VigieChiro et absentes d'ici sont listées")
    void liste_les_nuits_manquantes(FxRobot robot) {
        TableView<ParticipationOrpheline> table =
                robot.lookup("#tableOrphelines").queryTableView();

        assertThat(table.getItems()).containsExactly(CONNUE, INCONNUE);
        Label message = robot.lookup("#lblMessage").queryAs(Label.class);
        assertThat(message.getText()).contains("2 nuit(s)");
    }

    @Test
    @DisplayName("Sans sélection, le bouton est bloqué et l'enveloppe dit quoi faire")
    void sans_selection_le_bouton_est_bloque(FxRobot robot) {
        Button reconstruire = robot.lookup("#boutonReconstruire").queryButton();

        assertThat(reconstruire.isDisabled()).isTrue();
    }

    @Test
    @DisplayName("Nuit dont le point est inconnu ici : reconstruction refusée (donnée fausse évitée)")
    void point_inconnu_bloque_la_reconstruction(FxRobot robot) {
        TableView<ParticipationOrpheline> table =
                robot.lookup("#tableOrphelines").queryTableView();

        robot.interact(() -> table.getSelectionModel().select(INCONNUE));

        assertThat(robot.lookup("#boutonReconstruire").queryButton().isDisabled())
                .as("rattacher une nuit au mauvais point serait pire que de ne rien faire")
                .isTrue();
    }

    @Test
    @DisplayName("Reconstruire : compte rendu avec les lacunes, nuit retirée, écran appelant rafraîchi")
    void reconstruire_publie_le_compte_rendu_et_rafraichit(FxRobot robot) {
        when(service.reconstruire(eq(CONNUE), any(), any()))
                .thenReturn(new RapportReconstruction(12L, 56, 132, RapportReconstruction.lacunesConnues()));
        TableView<ParticipationOrpheline> table =
                robot.lookup("#tableOrphelines").queryTableView();
        robot.interact(() -> table.getSelectionModel().select(CONNUE));

        robot.interact(() -> robot.lookup("#boutonReconstruire").queryButton().fire());

        Label compteRendu = robot.lookup("#lblCompteRendu").queryAs(Label.class);
        assertThat(compteRendu.getText())
                .contains("56 séquence(s)")
                .contains("132 observation(s)")
                .contains("Aucun fichier audio")
                .contains("Réactiver ce passage");
        assertThat(table.getItems()).as("la nuit reconstruite ne manque plus").containsExactly(INCONNUE);

        robot.interact(() -> robot.lookup("#boutonFermer").queryButton().fire());
        assertThat(appelantRafraichi)
                .as("la table des passages se recharge : la nuit rapatriée y apparaît")
                .isTrue();
    }

    @Test
    @DisplayName("#1647 : fermer par la croix (pas le bouton « Fermer ») rafraîchit aussi l'écran appelant")
    void fermer_par_la_croix_rafraichit_aussi(FxRobot robot) {
        when(service.reconstruire(eq(CONNUE), any(), any()))
                .thenReturn(new RapportReconstruction(12L, 56, 132, RapportReconstruction.lacunesConnues()));
        TableView<ParticipationOrpheline> table =
                robot.lookup("#tableOrphelines").queryTableView();
        robot.interact(() -> table.getSelectionModel().select(CONNUE));
        robot.interact(() -> robot.lookup("#boutonReconstruire").queryButton().fire());

        robot.interact(stage::close); // fermeture par la croix / le système, pas le bouton « Fermer »

        assertThat(appelantRafraichi)
                .as("le rafraîchissement de la table ne doit pas dépendre du bouton « Fermer » (#1647)")
                .isTrue();
    }

    @Test
    @DisplayName(
            "#1708 « Reconstruire tout » : hydrate le lot, compte rendu (reconstruites + ignorées), appelant rafraîchi")
    void reconstruire_tout_en_lot(FxRobot robot) {
        when(service.reconstruire(eq(CONNUE), any(), any()))
                .thenReturn(new RapportReconstruction(1L, 10, 20, RapportReconstruction.lacunesConnues()));
        when(service.reconstruire(eq(INCONNUE), any(), any()))
                .thenThrow(new RegleMetierException("Le point d'écoute n'existe pas localement."));

        Button reconstruireTout = robot.lookup("#boutonReconstruireTout").queryButton();
        assertThat(reconstruireTout.getText())
                .as("le bouton annonce le nombre de nuits à reconstruire")
                .contains("2");
        robot.interact(reconstruireTout::fire);

        assertThat(robot.lookup("#lblCompteRendu").queryAs(Label.class).getText())
                .as("le lot reconstruit la nuit située et ignore celle au point inconnu")
                .contains("1 nuit(s) reconstruite(s)")
                .contains("1 nuit(s) ignorée(s)");
        robot.interact(() -> robot.lookup("#boutonFermer").queryButton().fire());
        assertThat(appelantRafraichi)
                .as("au moins une nuit reconstruite : la table des passages se recharge")
                .isTrue();
    }

    @Test
    @DisplayName("Un refus du service devient un message dans la modale, pas une exception muette")
    void refus_devient_un_message(FxRobot robot) {
        when(service.reconstruire(eq(CONNUE), any(), any()))
                .thenThrow(new RegleMetierException("VigieChiro ne renvoie aucune donnée : analyse non terminée."));
        TableView<ParticipationOrpheline> table =
                robot.lookup("#tableOrphelines").queryTableView();
        robot.interact(() -> table.getSelectionModel().select(CONNUE));

        robot.interact(() -> robot.lookup("#boutonReconstruire").queryButton().fire());

        Label erreur = robot.lookup("#lblErreur").queryAs(Label.class);
        assertThat(erreur.getText()).contains("analyse non terminée");
        assertThat(robot.lookup("#lblMessage").queryAs(Label.class).getText())
                .as("le constat n'est pas un incident : il ne vire pas au rouge")
                .contains("2 nuit(s)");
        assertThat(robot.lookup("#boutonReconstruire").queryButton().isDisabled())
                .as("bouton relâché après l'échec : l'utilisateur peut réessayer")
                .isFalse();
    }

    @Test
    @DisplayName("Annuler : la modale revient à un état neutre, aucun passage créé (pas d'erreur rouge)")
    void annulation_revient_a_un_etat_neutre(FxRobot robot) {
        when(service.reconstruire(eq(CONNUE), any(), any())).thenThrow(new OperationAnnuleeException());
        TableView<ParticipationOrpheline> table =
                robot.lookup("#tableOrphelines").queryTableView();
        robot.interact(() -> table.getSelectionModel().select(CONNUE));

        robot.interact(() -> robot.lookup("#boutonReconstruire").queryButton().fire());

        assertThat(robot.lookup("#lblErreur").queryAs(Label.class).getText())
                .as("une annulation n'est pas une erreur : rien en rouge")
                .isEmpty();
        assertThat(robot.lookup("#lblMessage").queryAs(Label.class).getText()).contains("annulée");
        assertThat(table.getItems())
                .as("rien n'a été créé : la nuit manque toujours")
                .containsExactly(CONNUE, INCONNUE);
        assertThat(robot.lookup("#boutonReconstruire").queryButton().isDisabled())
                .as("bouton relâché : on peut réessayer")
                .isFalse();
    }
}
