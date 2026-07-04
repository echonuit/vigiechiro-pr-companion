package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Tests du composant réutilisable [GestionnaireColonnes] : réordonnancement des colonnes (cœur du
/// glisser-déposer), tolérance aux colonnes non gérées, et panneau (liste + cases de visibilité liées).
@ExtendWith(ApplicationExtension.class)
class GestionnaireColonnesTest {

    private StackPane racine;

    @Start
    void start(Stage stage) {
        racine = new StackPane();
        stage.setScene(new Scene(racine, 320, 260));
        stage.show();
    }

    private static TableView<String> tableAvec(String... entetes) {
        TableView<String> table = new TableView<>();
        for (String entete : entetes) {
            table.getColumns().add(new TableColumn<>(entete));
        }
        return table;
    }

    @Test
    @DisplayName("appliquerOrdre réordonne les colonnes gérées selon la séquence fournie")
    void applique_ordre(FxRobot robot) {
        AtomicReference<TableView<String>> ref = new AtomicReference<>();
        robot.interact(() -> {
            TableView<String> table = tableAvec("A", "B", "C");
            GestionnaireColonnes.appliquerOrdre(
                    table,
                    List.of(
                            table.getColumns().get(2),
                            table.getColumns().get(0),
                            table.getColumns().get(1)));
            ref.set(table);
        });
        assertThat(ref.get().getColumns()).extracting(TableColumn::getText).containsExactly("C", "A", "B");
    }

    @Test
    @DisplayName("appliquerOrdre laisse les colonnes non gérées à leur place")
    void applique_ordre_sous_ensemble(FxRobot robot) {
        AtomicReference<TableView<String>> ref = new AtomicReference<>();
        robot.interact(() -> {
            TableView<String> table = tableAvec("A", "B", "C", "D");
            // Gérées : A (index 0) et C (index 2), réordonnées en [C, A] → C reste en 0, A en 2, B et D fixes.
            GestionnaireColonnes.appliquerOrdre(
                    table, List.of(table.getColumns().get(2), table.getColumns().get(0)));
            ref.set(table);
        });
        assertThat(ref.get().getColumns()).extracting(TableColumn::getText).containsExactly("C", "B", "A", "D");
    }

    @Test
    @DisplayName("deplacer glisse un item et répercute l'ordre sur la table (sans effet hors bornes)")
    void deplacer_reordonne(FxRobot robot) {
        AtomicReference<TableView<String>> ref = new AtomicReference<>();
        robot.interact(() -> {
            TableView<String> table = tableAvec("A", "B", "C");
            ListView<GestionnaireColonnes.Colonne> liste = new ListView<>();
            for (TableColumn<String, ?> col : table.getColumns()) {
                liste.getItems().add(new GestionnaireColonnes.Colonne(col, col.getText(), false));
            }
            GestionnaireColonnes.deplacer(table, liste, 5, 0); // hors bornes : sans effet
            GestionnaireColonnes.deplacer(table, liste, 0, 2); // A passe en fin
            ref.set(table);
        });
        assertThat(ref.get().getColumns()).extracting(TableColumn::getText).containsExactly("B", "C", "A");
    }

    @Test
    @DisplayName("Le panneau liste les colonnes ; une case décochée masque sa colonne, la verrouillée est figée")
    void panneau_cases_visibilite(FxRobot robot) {
        AtomicReference<TableColumn<String, ?>> colB = new AtomicReference<>();
        robot.interact(() -> {
            TableView<String> table = tableAvec("A", "B", "C");
            colB.set(table.getColumns().get(1));
            VBox panneau = GestionnaireColonnes.construirePanneau(
                    table,
                    List.of(
                            new GestionnaireColonnes.Colonne(table.getColumns().get(0), "A", true),
                            new GestionnaireColonnes.Colonne(table.getColumns().get(1), "B", false),
                            new GestionnaireColonnes.Colonne(table.getColumns().get(2), "C", false)));
            racine.getChildren().setAll(panneau);
        });

        CheckBox caseA = robot.lookup("A").queryAs(CheckBox.class);
        CheckBox caseB = robot.lookup("B").queryAs(CheckBox.class);
        assertThat(caseA.isDisabled())
                .as("colonne d'identité : visibilité verrouillée")
                .isTrue();
        assertThat(colB.get().isVisible()).isTrue();

        robot.interact(() -> caseB.setSelected(false));
        assertThat(colB.get().isVisible())
                .as("décocher masque la colonne (liaison bidirectionnelle)")
                .isFalse();
    }
}
