package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;
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
import fr.univ_amu.iut.commun.model.ClasseActivite;
import fr.univ_amu.iut.commun.model.ConfianceReferentiel;
import fr.univ_amu.iut.commun.model.ContexteActivite;
import fr.univ_amu.iut.commun.model.SaisonActivite;
import fr.univ_amu.iut.commun.model.SeuilsActivite;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration de l'écran **Synthèse de la nuit** (#2351) : chargement du FXML via Guice avec un
/// [ServiceSynthese] mocké, et surtout ce que l'écran **affirme en permanence** — le référentiel employé,
/// l'avertissement et la citation.
///
/// Ces trois là ne sont pas décoratifs : la source est libre d'usage **avec citation obligatoire**, et
/// une classe d'activité sans sa mise en garde se lit comme un verdict.
@ExtendWith(ApplicationExtension.class)
class SyntheseViewTest {

    private ServiceSynthese service;
    private SyntheseController controleur;

    private static LigneSynthese ligne(String taxon, String groupe, int contacts, ClasseActivite classe) {
        SeuilsActivite seuils =
                new SeuilsActivite(10, 100, 1000, 9000, ConfianceReferentiel.TRES_BONNE, "national", "toutes");
        return new LigneSynthese(
                taxon,
                taxon + " (nom)",
                groupe,
                contacts,
                contacts,
                Optional.ofNullable(classe),
                classe == null ? Optional.empty() : Optional.of(seuils),
                classe != null);
    }

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceSynthese.class);
        when(service.referentielDisponible()).thenReturn(true);
        when(service.milieuxDisponibles()).thenReturn(List.of("Foret", "Urbain"));
        when(service.contexte(anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ContexteActivite(
                        Optional.of(SaisonActivite.ETE), Optional.of("Occitanie"), Optional.empty()));
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

    @Test
    @DisplayName("L'avertissement et la citation sont AFFICHÉS, en permanence")
    void avertissement_et_citation_permanents(FxRobot robot) {
        // La source est libre d'usage AVEC citation obligatoire, et une classe d'activité sans sa mise en
        // garde se lit comme un verdict. Ni l'un ni l'autre n'est repliable.
        Label avertissement = robot.lookup("#lblAvertissement").queryAs(Label.class);
        Label citation = robot.lookup("#lblCitation").queryAs(Label.class);

        assertThat(avertissement.getText()).contains("n'est pas un niveau d'enjeu de conservation");
        assertThat(avertissement.isVisible()).isTrue();
        assertThat(citation.getText())
                .as("le crédit à la source doit être lisible à l'écran, pas seulement dans le code")
                .contains("Bas Y.", "2020", "Muséum national d'Histoire naturelle");
        assertThat(citation.isVisible()).isTrue();
    }

    @Test
    @DisplayName("Le référentiel employé est NOMMÉ : une classe dont on ignore la référence est un oracle")
    void referentiel_nomme(FxRobot robot) {
        robot.interact(() -> controleur.ouvrirSur(new fr.univ_amu.iut.commun.viewmodel.ContextePassage(
                1L, 3, new fr.univ_amu.iut.commun.viewmodel.ContexteSite("640380", "A1", "Étang"))));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(robot.lookup("#lblReferentiel").queryAs(Label.class).getText())
                .contains("region Occitanie", "Été");
    }

    @Test
    @DisplayName("Le tableau affiche les espèces, avec la classe et ses quantiles à côté")
    void tableau_avec_classe_et_quantiles(FxRobot robot) {
        when(service.pour(
                        anyLong(),
                        org.mockito.ArgumentMatchers.anyBoolean(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        ligne("Pipkuh", "Chiroptères", 150, ClasseActivite.FORTE),
                        ligne("Tetvir", "Orthoptères et cigales", 12, null)));
        robot.interact(() -> controleur.ouvrirSur(new fr.univ_amu.iut.commun.viewmodel.ContextePassage(
                1L, 3, new fr.univ_amu.iut.commun.viewmodel.ContexteSite("640380", "A1", "Étang"))));
        WaitForAsyncUtils.waitForFxEvents();

        TableView<?> table = robot.lookup("#tableSynthese").queryAs(TableView.class);
        assertThat(table.getItems()).hasSize(2);
        assertThat(robot.lookup("Forte").tryQuery()).as("la classe s'affiche").isPresent();
        assertThat(robot.lookup("Q25 = 10 · Q75 = 100 · Q98 = 1000").tryQuery())
                .as("les quantiles l'accompagnent : une classe seule est un verdict")
                .isPresent();
        assertThat(robot.lookup("Non couvert par le référentiel").tryQuery())
                .as("un orthoptère le DIT, plutôt que de laisser une cellule vide")
                .isPresent();
    }

    @Test
    @DisplayName("La bascule « validées seulement » est offerte et pilote le recalcul")
    void bascule_validees(FxRobot robot) {
        CheckBox bascule = robot.lookup("#chkValideesSeulement").queryAs(CheckBox.class);
        assertThat(bascule.isSelected()).as("on part de la lecture complète").isFalse();

        robot.clickOn(bascule);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(bascule.isSelected()).isTrue();
    }
}
