package fr.univ_amu.iut.multisite.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.multisite.model.FiltresMultisite;
import fr.univ_amu.iut.multisite.model.SavedView;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de la **modale des vues sauvegardées** de M-Multisite : chargement du
/// FXML (controller instancié par défaut), branchement sur un [MultisiteViewModel] (service mocké),
/// vérification de la liste, de l'enregistrement et de l'application d'une vue. Pas de base de
/// données.
@ExtendWith(ApplicationExtension.class)
class ModaleVuesViewTest {

    private ServiceMultisite service;
    private MultisiteViewModel viewModel;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceMultisite.class);
        when(service.listerPassages(anyString(), any(), any())).thenReturn(List.of());
        when(service.listerVues())
                .thenReturn(List.of(new SavedView(1L, "Déposés 2026", "{}"), new SavedView(2L, "Douteux", "{}")));
        viewModel = new MultisiteViewModel(service, "u-1");

        FXMLLoader loader = new FXMLLoader(ModaleVuesController.class.getResource("ModaleVues.fxml"));
        Parent vue = loader.load();
        ModaleVuesController controleur = loader.getController();
        controleur.demarrer(viewModel);
        stage.setScene(new Scene(vue));
        stage.show();
    }

    @Test
    @DisplayName("La modale liste les vues sauvegardées")
    void liste_les_vues(FxRobot robot) {
        ListView<?> liste = robot.lookup("#listeVues").queryAs(ListView.class);
        assertThat(liste.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("Enregistrer crée une vue avec le nom saisi")
    void enregistrer_cree_une_vue(FxRobot robot) {
        robot.clickOn("#champNom").write("Ma vue");
        robot.clickOn("#boutonEnregistrer");

        verify(service).enregistrerVue(eq("Ma vue"), any(FiltresMultisite.class));
    }

    @Test
    @DisplayName("Appliquer une vue sélectionnée rejoue ses filtres sur le ViewModel partagé")
    void appliquer_rejoue_les_filtres(FxRobot robot) {
        when(service.chargerVue(1L)).thenReturn(new FiltresMultisite("777", null, null, 2025));
        ListView<?> liste = robot.lookup("#listeVues").queryAs(ListView.class);
        robot.interact(() -> liste.getSelectionModel().select(0));

        robot.clickOn("#boutonAppliquer");

        assertThat(viewModel.filtreNumeroCarreProperty().get()).isEqualTo("777");
        assertThat(viewModel.filtreAnneeProperty().get()).isEqualTo(2025);
    }
}
