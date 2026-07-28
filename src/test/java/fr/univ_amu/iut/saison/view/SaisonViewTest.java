package fr.univ_amu.iut.saison.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.saison.model.CasePassage;
import fr.univ_amu.iut.saison.model.LigneSaison;
import fr.univ_amu.iut.saison.model.ServiceSoldeSaison;
import fr.univ_amu.iut.saison.model.SoldeSaison;
import fr.univ_amu.iut.saison.viewmodel.SaisonViewModel;
import java.time.LocalDate;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test TestFX de l'écran **M-Saison** : le FXML est chargé avec un injecteur de test (ViewModel sur un
/// [ServiceSoldeSaison] simulé, contrats d'ouverture mockés), monté headless. On vérifie l'affichage
/// d'une ligne par point et le routage du double-clic (passage présent vs carré du point).
@ExtendWith(ApplicationExtension.class)
class SaisonViewTest {

    private OuvrirPassage ouvrirPassage;
    private OuvrirSite ouvrirSite;

    @Start
    void demarrer(Stage stage) throws Exception {
        ouvrirPassage = mock(OuvrirPassage.class);
        ouvrirSite = mock(OuvrirSite.class);
        ServiceSoldeSaison service = mock(ServiceSoldeSaison.class);
        SoldeSaison solde = new SoldeSaison(
                2026,
                LocalDate.of(2026, 7, 20),
                List.of(
                        new LigneSaison(
                                "640001",
                                "A1",
                                1L,
                                new CasePassage(
                                        42L, StatutWorkflow.DEPOSE, Verdict.OK, LocalDate.of(2026, 6, 20), false, null),
                                CasePassage.absente(),
                                List.of(),
                                "Poser l'enregistreur avant le 30/09"),
                        new LigneSaison(
                                "640002",
                                "B1",
                                2L,
                                CasePassage.absente(),
                                CasePassage.absente(),
                                List.of(),
                                "Poser l'enregistreur avant le 31/07"),
                        // #2525 : la nuit opportuniste ne prend PAS la place du passage 1 protocolaire,
                        // qui reste manquant — elle vit dans la colonne « Hors protocole ».
                        new LigneSaison(
                                "640003",
                                "C1",
                                3L,
                                CasePassage.absente(),
                                CasePassage.absente(),
                                List.of(new CasePassage(
                                        99L, StatutWorkflow.DEPOSE, Verdict.OK, LocalDate.of(2026, 6, 25), true, null)),
                                "Poser l'enregistreur avant le 31/07")));
        when(service.soldeCourant(anyString(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(solde);
        when(service.soldePour(anyString(), anyInt())).thenReturn(solde);

        Injector injecteur = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(OuvrirPassage.class).toInstance(ouvrirPassage);
                bind(OuvrirSite.class).toInstance(ouvrirSite);
            }

            @Provides
            SaisonViewModel viewModel() {
                return new SaisonViewModel(service, "u-test");
            }
        });

        FXMLLoader loader = new FXMLLoader(SaisonController.class.getResource("Saison.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        stage.setScene(new Scene(vue, 1000, 600));
        stage.show();
    }

    @Test
    @DisplayName("une ligne par point suivi")
    void une_ligne_par_point(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSaison").queryAs(TableView.class);
        assertThat(table.getItems()).hasSize(3);
    }

    @Test
    @DisplayName("#2525 : une nuit opportuniste s'affiche en pastille « hors protocole »")
    void case_opportuniste_pastille(FxRobot robot) {
        Labeled pastille = robot.lookup(".badge-opportuniste").queryAs(Labeled.class);
        assertThat(pastille.getText()).contains("Opportuniste");
    }

    @Test
    @DisplayName("double-clic sur un point avec passage ouvre le passage concerné")
    void double_clic_ouvre_le_passage(FxRobot robot) {
        robot.doubleClickOn("640001");
        WaitForAsyncUtils.waitForFxEvents();
        verify(ouvrirPassage).ouvrir(eq(42L), any(ContexteSite.class));
    }

    @Test
    @DisplayName("double-clic sur un point sans passage ouvre le carré du point")
    void double_clic_sans_passage_ouvre_le_carre(FxRobot robot) {
        robot.doubleClickOn("640002");
        WaitForAsyncUtils.waitForFxEvents();
        verify(ouvrirSite).ouvrirDetail("640002");
    }

    @Test
    @DisplayName("#2610 : aucune campagne à proposer, le sélecteur est retiré de la mise en page")
    void selecteur_campagne_efface_sans_campagne(FxRobot robot) {
        // `setVisible(false)` seul laisserait un trou dans la barre : c'est `managed` qui retire le
        // contrôle du calcul de mise en page. Vérifier les deux, sinon on ne teste que la moitié.
        assertThat(robot.lookup("#choixCampagne").queryAs(ComboBox.class).isVisible())
                .isFalse();
        assertThat(robot.lookup("#choixCampagne").queryAs(ComboBox.class).isManaged())
                .isFalse();
        assertThat(robot.lookup("#lblCampagne").queryAs(Label.class).isManaged())
                .isFalse();
    }
}
