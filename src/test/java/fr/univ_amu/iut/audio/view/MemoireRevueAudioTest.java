package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.SortType;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Mémoire de session du **tri** de la table audio (#484) : le tri d'une ouverture est restitué à la
/// suivante. On simule une fermeture (retrait de la table de la scène) puis une réouverture (nouvelle table).
@ExtendWith(ApplicationExtension.class)
class MemoireRevueAudioTest {

    private final MemoireRevueAudio memoire = new MemoireRevueAudio();
    private VBox racine;

    @Start
    void start(Stage stage) {
        racine = new VBox();
        stage.setScene(new Scene(racine, 300, 200));
        stage.show();
    }

    private static TableView<LigneObservationAudio> tableAvecColonnes() {
        TableView<LigneObservationAudio> table = new TableView<>();
        table.getColumns().add(new TableColumn<>("Date"));
        table.getColumns().add(new TableColumn<>("Heure"));
        return table;
    }

    @Test
    @DisplayName("Le tri d'une ouverture (colonne + sens) est restitué à la réouverture")
    void memorise_et_restaure_le_tri(FxRobot robot) {
        // Ouverture 1 : trier par « Heure » décroissant, puis fermer (retrait de la scène → mémorisation).
        TableView<LigneObservationAudio> premiere = tableAvecColonnes();
        robot.interact(() -> {
            racine.getChildren().add(premiere);
            memoire.installer(premiere, null);
            TableColumn<LigneObservationAudio, ?> heure = premiere.getColumns().get(1);
            heure.setSortType(SortType.DESCENDING);
            premiere.getSortOrder().add(heure);
        });
        robot.interact(() -> racine.getChildren().remove(premiere));

        // Ouverture 2 : nouvelle table (comme un rechargement de la vue) → le tri mémorisé est réappliqué.
        TableView<LigneObservationAudio> seconde = tableAvecColonnes();
        robot.interact(() -> {
            racine.getChildren().add(seconde);
            memoire.installer(seconde, null);
        });

        assertThat(seconde.getSortOrder()).extracting(TableColumn::getText).containsExactly("Heure");
        assertThat(seconde.getColumns().get(1).getSortType()).isEqualTo(SortType.DESCENDING);
    }
}
