package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.api.ResultatEcriture;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.DecompteAudio;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.ServiceConditionsPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.ServiceRattachement;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import fr.univ_amu.iut.passage.viewmodel.RattachementViewModel;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX de la **décision de fermeture** de la modale « Modifier le passage »
/// (#1839) : « Appliquer » enregistre localement **puis** envoie vers VigieChiro, et ne referme la
/// fenêtre que si cet envoi n'a rien à reprocher.
///
/// C'est le point exact où l'échec redevenait invisible avant #1839 : fermer sur un refus emporte le
/// message avec la fenêtre, et l'utilisateur croit son envoi passé. Les autres tests de la modale
/// tournent **hors connexion** (passerelle absente), cas où l'envoi est réputé sans reproche ; il faut
/// donc une passerelle **présente et refusante** pour exercer l'autre branche - d'où cette fixture
/// distincte plutôt qu'un aménagement de celle de [RattachementModaleViewTest], dont trois tests
/// dépendent de l'absence de passerelle.
@ExtendWith(ApplicationExtension.class)
class RattachementModaleFermetureViewTest {

    private final SynchronisationParticipation synchronisation = mock(SynchronisationParticipation.class);
    private RattachementModaleController controleur;
    private Stage fenetre;

    @Start
    void start(Stage stage) throws Exception {
        this.fenetre = stage;
        ServicePassage service = mock(ServicePassage.class);
        when(service.detailPassage(anyLong()))
                .thenReturn(new DetailPassage(
                        1,
                        2026,
                        "2026-06-20",
                        "21:00:00",
                        "05:00:00",
                        "1925492",
                        StatutWorkflow.TRANSFORME,
                        Verdict.OK,
                        null,
                        0L,
                        0L,
                        30,
                        0.0,
                        null,
                        new DecompteAudio(0, 0)));
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            RattachementViewModel viewModel() {
                var propositions = mock(fr.univ_amu.iut.passage.model.PropositionsEnregistreur.class);
                when(propositions.pour(org.mockito.ArgumentMatchers.any())).thenReturn(List.of("1925492"));
                return new RattachementViewModel(
                        service,
                        mock(ServiceRattachement.class),
                        mock(ServiceConditionsPassage.class),
                        propositions,
                        Optional.of(synchronisation));
            }
        });
        FXMLLoader loader = new FXMLLoader(RattachementModaleController.class.getResource("RattachementModale.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        controleur.demarrer(7L, "040962", "A1", () -> {});
        stage.setScene(new Scene(vue));
        stage.show();
    }

    @Test
    @DisplayName("#1839 : VigieChiro refuse l'envoi → la modale RESTE ouverte, la cause à l'écran")
    void refus_laisse_la_modale_ouverte(FxRobot robot) {
        when(synchronisation.pousserVers(7L)).thenReturn(ResultatEcriture.echouee("422 champ invalide"));

        robot.clickOn("#boutonAppliquer");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(fenetre.isShowing())
                .as("fermer sur un refus emporterait le message : l'échec redeviendrait invisible")
                .isTrue();
        Label message = robot.lookup("#messageErreur").queryAs(Label.class);
        assertThat(message.getText()).contains("refusé").contains("422 champ invalide");
    }

    @Test
    @DisplayName("#1839 : envoi accepté → la modale se ferme (le geste va jusqu'au bout)")
    void succes_ferme_la_modale(FxRobot robot) {
        when(synchronisation.pousserVers(7L)).thenReturn(ResultatEcriture.reussie("abc123"));

        robot.clickOn("#boutonAppliquer");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(fenetre.isShowing())
                .as("rien à reprocher à l'envoi : la modale n'a plus de raison de retenir l'utilisateur")
                .isFalse();
    }
}
