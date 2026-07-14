package fr.univ_amu.iut.qualification.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// La modale **« Personnaliser la sélection d'écoute »** (R12), cliquée pour de vrai (#1431).
///
/// Ce geste **efface la progression d'écoute** de l'observateur - un travail qu'il a fait séquence par
/// séquence. Il n'était vérifié **nulle part** : c'était un `Dialog<ButtonType>` bâti à la main, terminé
/// par un `showAndWait`, qui fige un test headless.
///
/// C'est la **passe 1** de la clôture de #1431 qui l'a trouvé : je l'avais bien inventorié, et jamais
/// traité. Il souffrait des trois mêmes défauts que la modale de site (#1454) - geste injouable,
/// validation dans la vue, **capture de documentation reconstruite à la main** (donc capable de dériver
/// du vrai écran sans que rien ne le signale).
@ExtendWith(ApplicationExtension.class)
class ModaleSelectionViewTest {

    private SelectionEcouteViewModel viewModel;
    private ModaleSelectionController controleur;

    @Start
    void start(Stage stage) throws Exception {
        viewModel = mock(SelectionEcouteViewModel.class);
        // La modale ouvre sur l'état COURANT de la sélection : c'est ce qu'elle doit pré-sélectionner.
        when(viewModel.methodeProperty())
                .thenReturn(new SimpleObjectProperty<>(MethodeSelection.REPARTITION_TEMPORELLE));
        when(viewModel.tailleProperty()).thenReturn(new SimpleIntegerProperty(20));
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            SelectionEcouteViewModel viewModel() {
                return viewModel;
            }
        });
        FXMLLoader loader = new FXMLLoader(ModaleSelectionController.class.getResource("ModaleSelection.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        stage.setScene(new Scene(vue));
        stage.show();
    }

    private Button boutonRegenerer(FxRobot robot) {
        return robot.lookup("#boutonRegenerer").queryAs(Button.class);
    }

    @Test
    @DisplayName("#1431 : la modale ouvre sur l'état courant de la sélection")
    void ouvre_sur_l_etat_courant(FxRobot robot) {
        robot.interact(() -> controleur.demarrer());

        assertThat(robot.lookup("#choixReparti").queryAs(RadioButton.class).isSelected())
                .as("la méthode courante est RéparTemporel : c'est elle qui doit être pré-sélectionnée")
                .isTrue();
        assertThat(robot.lookup("#curseurTaille").queryAs(Slider.class).getValue())
                .isEqualTo(20.0);
    }

    @Test
    @DisplayName("#1431 : « Régénérer » applique le brouillon et reconstruit la sélection")
    void regenerer_applique_et_reconstruit(FxRobot robot) {
        robot.interact(() -> controleur.demarrer());

        robot.interact(() -> {
            robot.lookup("#choixAleatoire").queryAs(RadioButton.class).setSelected(true);
            robot.lookup("#curseurTaille").queryAs(Slider.class).setValue(25);
        });
        robot.interact(() -> boutonRegenerer(robot).fire());

        assertThat(viewModel.methodeProperty().get()).isEqualTo(MethodeSelection.ALEATOIRE);
        assertThat(viewModel.tailleProperty().get()).isEqualTo(25);
        verify(viewModel).regenerer();
    }

    @Test
    @DisplayName("#1431 : « Annuler » ne touche à rien : le brouillon est jeté, la progression est intacte")
    void annuler_ne_touche_a_rien(FxRobot robot) {
        robot.interact(() -> controleur.demarrer());

        robot.interact(() -> {
            robot.lookup("#choixAleatoire").queryAs(RadioButton.class).setSelected(true);
            robot.lookup("#curseurTaille").queryAs(Slider.class).setValue(25);
        });
        robot.interact(
                () -> robot.lookup(".bouton-secondaire").queryAs(Button.class).fire());

        // Régénérer EFFACE la progression d'écoute. Renoncer doit donc vraiment ne rien faire - y compris
        // ne pas appliquer en douce la méthode ou la taille qu'on avait bougées dans le formulaire.
        verify(viewModel, never()).regenerer();
        assertThat(viewModel.methodeProperty().get())
                .as("le brouillon ne doit pas fuir vers le ViewModel")
                .isEqualTo(MethodeSelection.REPARTITION_TEMPORELLE);
        assertThat(viewModel.tailleProperty().get()).isEqualTo(20);
    }
}
