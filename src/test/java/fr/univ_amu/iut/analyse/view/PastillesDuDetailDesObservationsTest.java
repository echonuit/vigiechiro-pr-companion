package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import fr.univ_amu.iut.analyse.model.ServiceAnalyse;
import fr.univ_amu.iut.analyse.viewmodel.AnalyseViewModel;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.view.PastillesEntieres;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.Arrays;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// La colonne « Statut » du détail des observations montre chacun de ses libellés en entier (#6101).
///
/// Le panneau est chargé depuis son FXML, donc avec la largeur que l'écran déclare, et les trois
/// statuts de revue sont semés.
@ExtendWith(ApplicationExtension.class)
class PastillesDuDetailDesObservationsTest {

    private static final String FXML = "DetailObservations.fxml";

    private TableView<ObservationEspece> table;

    @Start
    void demarrer(Stage fenetre) throws Exception {
        FXMLLoader chargeur = new FXMLLoader(DetailObservationsController.class.getResource(FXML));
        Parent panneau = chargeur.load();
        DetailObservationsController controleur = chargeur.getController();
        controleur.installer(
                new AnalyseViewModel(mock(ServiceAnalyse.class), "u-1"),
                carre -> "",
                observation -> {},
                observation -> {},
                () -> {});
        table = controleur.table();
        table.setItems(FXCollections.observableArrayList(Arrays.stream(StatutObservation.values())
                .map(PastillesDuDetailDesObservationsTest::observation)
                .toList()));
        FenetreAjustable.poserHabillee(fenetre, panneau, 1300, 400);
        FenetreAjustable.afficher(fenetre);
    }

    private static ObservationEspece observation(StatutObservation statut) {
        int rang = statut.ordinal() + 1;
        return new ObservationEspece(
                rang,
                rang,
                rang,
                rang,
                2026,
                "2026-06-22",
                "640380",
                "A1",
                "Étang",
                "Pippip",
                0.9,
                null,
                null,
                statut,
                "Ahetze");
    }

    @Test
    @DisplayName("#6101 : la colonne « Statut » montre les trois statuts de revue en entier")
    void le_statut_est_entier() {
        TableColumn<ObservationEspece, ?> statut = table.getColumns().stream()
                .filter(colonne -> "Statut".equals(colonne.getText()))
                .findFirst()
                .orElseThrow();

        assertThat(PastillesEntieres.libellesEntiers(table, statut, FXML))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(StatutObservation.values())
                        .map(FormatAnalyse::libelleStatut)
                        .toList());
    }
}
