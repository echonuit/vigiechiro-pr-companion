package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.persistence.InventaireSauvegardes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.testfx.util.WaitForAsyncUtils;

/// Contenu de la fenêtre « quelle sauvegarde restaurer ? » (#3197).
///
/// Ce qui s'éprouve ici est ce que l'utilisateur **voit avant de choisir** : une ligne par sauvegarde
/// avec sa date et sa taille, et le **total** - là où il n'avait jusqu'ici qu'un sélecteur de fichiers
/// natif, sans date, sans taille, sans idée de ce que l'ensemble occupait.
///
/// La fenêtre elle-même ([ChoixSauvegardeJavaFx]) n'est pas montée : son `showAndWait()` figerait le
/// test headless. C'est précisément pourquoi le contenu en est séparé.
@ExtendWith(ApplicationExtension.class)
class ContenuChoixSauvegardeTest {

    private static final List<InventaireSauvegardes.Entree> TROIS = List.of(
            new InventaireSauvegardes.Entree(
                    "vigiechiro-sauvegarde-20260801-101500.db",
                    Instant.parse("2026-08-01T10:15:00Z"),
                    3L * 1024 * 1024,
                    InventaireSauvegardes.Nature.BASE),
            new InventaireSauvegardes.Entree(
                    "vigiechiro-avant-migration-V39.db",
                    Instant.parse("2026-07-02T08:00:00Z"),
                    5L * 1024 * 1024,
                    InventaireSauvegardes.Nature.FILET_MIGRATION),
            new InventaireSauvegardes.Entree(
                    "vigiechiro-sauvegarde-complete-20260610-090000",
                    Instant.parse("2026-06-10T09:00:00Z"),
                    12L * 1024 * 1024,
                    InventaireSauvegardes.Nature.COMPLETE));

    private final List<InventaireSauvegardes.Entree> restaurees = new ArrayList<>();
    private final AtomicBoolean parcouru = new AtomicBoolean();
    private final AtomicBoolean annule = new AtomicBoolean();

    private ContenuChoixSauvegarde contenu;

    @Start
    void demarrer(Stage stage) {
        contenu = new ContenuChoixSauvegarde(TROIS, restaurees::add, () -> parcouru.set(true), () -> annule.set(true));
        stage.setScene(new Scene(contenu.racine()));
        stage.show();
    }

    @Test
    @DisplayName("Une ligne par sauvegarde, et le total de ce qui est montré")
    void liste_et_total(FxRobot robot) {
        TableView<?> table = robot.lookup("#" + ContenuChoixSauvegarde.ID_TABLE).queryAs(TableView.class);
        assertThat(table.getItems()).hasSize(3);

        Label total = robot.lookup("#" + ContenuChoixSauvegarde.ID_TOTAL).queryAs(Label.class);
        assertThat(total.getText())
                .as("3 + 5 + 12 Mo : sans les sauvegardes complètes, qui sont des DOSSIERS, ce total mentirait")
                .contains("3 sauvegarde(s)")
                .contains("20 Mo");
    }

    @Test
    @DisplayName("Le filet de migration est nommé pour ce qu'il est, pas par sa constante")
    void natures_lisibles(FxRobot robot) {
        TableView<?> table = robot.lookup("#" + ContenuChoixSauvegarde.ID_TABLE).queryAs(TableView.class);
        List<String> natures = table.getColumns().stream()
                .filter(colonne -> "Nature".equals(colonne.getText()))
                .findFirst()
                .map(colonne -> table.getItems().stream()
                        .map(ligne -> String.valueOf(
                                colonne.getCellObservableValue(table.getItems().indexOf(ligne))
                                        .getValue()))
                        .toList())
                .orElseThrow();

        assertThat(natures).containsExactly("Sauvegarde", "Filet de migration", "Complète (avec audio)");
    }

    @Test
    @DisplayName("Restaurer est inactif tant que rien n'est sélectionné, et rend la ligne choisie")
    void restaurer_exige_une_selection(FxRobot robot) {
        Button restaurer =
                robot.lookup("#" + ContenuChoixSauvegarde.ID_RESTAURER).queryAs(Button.class);
        assertThat(restaurer.isDisabled())
                .as("rien de sélectionné, rien à restaurer : l'affordance dit ce que le clic exige")
                .isTrue();

        TableView<?> table = robot.lookup("#" + ContenuChoixSauvegarde.ID_TABLE).queryAs(TableView.class);
        robot.interact(() -> table.getSelectionModel().select(1));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(restaurer.isDisabled()).isFalse();

        robot.clickOn("#" + ContenuChoixSauvegarde.ID_RESTAURER);
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(restaurees)
                .extracting(InventaireSauvegardes.Entree::nom)
                .containsExactly("vigiechiro-avant-migration-V39.db");
    }

    @Test
    @DisplayName("« Parcourir… » reste : la liste ne retire pas la navigation libre")
    void parcourir_reste_possible(FxRobot robot) {
        robot.clickOn("#" + ContenuChoixSauvegarde.ID_PARCOURIR);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(parcouru)
                .as("une sauvegarde rangée sur un disque externe doit rester atteignable")
                .isTrue();
        assertThat(annule).isFalse();
    }
}
