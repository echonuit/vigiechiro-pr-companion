package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.analyse.model.LigneSynthese;
import fr.univ_amu.iut.analyse.model.ServiceSynthese;
import fr.univ_amu.iut.analyse.viewmodel.SyntheseViewModel;
import fr.univ_amu.iut.commun.model.ContexteActivite;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// L'écran **Synthèse de la nuit** quand le référentiel d'activité est **absent** (#2351).
///
/// C'est un critère d'acceptation du lot, et il n'était couvert par rien : le code existait, mais rien
/// ne le tenait. Le comportement attendu n'est pas d'échouer ni d'afficher des cases blanches — une
/// colonne vide se lirait comme une donnée manquante. L'écran **retire** ce qu'il ne peut plus fonder
/// et le dit en toutes lettres ; le tableau de comptages, lui, reste entier et exploitable.
///
/// Classe séparée de [SyntheseViewTest] parce que la disponibilité du référentiel se décide **au
/// chargement** de la vue : c'est un autre montage, pas une autre assertion.
@ExtendWith(ApplicationExtension.class)
class SyntheseSansReferentielViewTest {

    private SyntheseController controleur;

    @Start
    void start(Stage stage) throws Exception {
        ServiceSynthese service = mock(ServiceSynthese.class);
        when(service.referentielDisponible()).thenReturn(false);
        when(service.contexte(anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(ContexteActivite.NATIONAL);
        when(service.pour(
                        anyLong(),
                        anyBoolean(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new LigneSynthese(
                        "Pipkuh",
                        "Pipistrelle de Kuhl",
                        "Chiroptères",
                        718,
                        402,
                        Optional.empty(),
                        Optional.empty(),
                        false)));
        OuvrirSite ouvrirSite = mock(OuvrirSite.class);
        OuvrirPassage ouvrirPassage = mock(OuvrirPassage.class);
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            SyntheseViewModel viewModel() {
                return new SyntheseViewModel(service);
            }

            @Provides
            OuvrirSite ouvrirSite() {
                return ouvrirSite;
            }

            @Provides
            OuvrirPassage ouvrirPassage() {
                return ouvrirPassage;
            }
        });
        FXMLLoader loader = new FXMLLoader(SyntheseController.class.getResource("Synthese.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        stage.setScene(new Scene(vue, 1100, 640));
        stage.show();
    }

    private void ouvrir(FxRobot robot) {
        robot.interact(
                () -> controleur.ouvrirSur(new ContextePassage(1L, 3, new ContexteSite("640380", "A1", "Étang"))));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("Les colonnes d'activité sont RETIRÉES, pas laissées vides")
    void colonnes_retirees(FxRobot robot) {
        ouvrir(robot);

        TableView<?> table = robot.lookup("#tableSynthese").queryAs(TableView.class);
        TableColumn<?, ?> activite = table.getColumns().stream()
                .filter(colonne -> "Activité".equals(colonne.getText()))
                .findFirst()
                .orElseThrow();
        TableColumn<?, ?> seuils = table.getColumns().stream()
                .filter(colonne -> "Seuils retenus".equals(colonne.getText()))
                .findFirst()
                .orElseThrow();

        assertThat(activite.isVisible())
                .as("une colonne blanche se lirait comme une donnée manquante")
                .isFalse();
        assertThat(seuils.isVisible()).isFalse();
    }

    @Test
    @DisplayName("L'écran DIT que le référentiel manque, et que le tableau reste exploitable")
    void indisponibilite_annoncee(FxRobot robot) {
        ouvrir(robot);

        assertThat(robot.lookup("#lblReferentiel").queryAs(Label.class).getText())
                .contains("Référentiel d'activité indisponible", "reste exploitable");
    }

    @Test
    @DisplayName("Le sélecteur de milieu disparaît : choisir une déclinaison ne mènerait à rien")
    void selecteur_de_milieu_retire(FxRobot robot) {
        ouvrir(robot);

        ChoiceBox<?> milieu = robot.lookup("#cbMilieu").queryAs(ChoiceBox.class);
        assertThat(milieu.isVisible()).isFalse();
        assertThat(milieu.isManaged())
                .as("retiré de la mise en page, pas seulement invisible : sinon il laisse un trou")
                .isFalse();
    }

    @Test
    @DisplayName("Les comptages, eux, restent affichés : ce qui est mesuré ne dépend pas du référentiel")
    void comptages_conserves(FxRobot robot) {
        ouvrir(robot);

        TableView<?> table = robot.lookup("#tableSynthese").queryAs(TableView.class);
        assertThat(table.getItems()).hasSize(1);
        assertThat(robot.lookup("718").tryQuery())
                .as("le nombre de contacts est une mesure, pas une interprétation")
                .isPresent();
    }
}
