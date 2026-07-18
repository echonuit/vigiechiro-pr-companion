package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.analyse.viewmodel.Regroupement;
import fr.univ_amu.iut.commun.view.DescripteurColonnes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Scene;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test du sélecteur de colonnes extrait de l'analyse : câblage des clic droit + ☰ sur les trois tables, et
/// aller-retour de l'adaptateur des vues mémorisées (une entrée de map par table).
@ExtendWith(ApplicationExtension.class)
class SelecteurColonnesAnalyseTest {

    private StackPane racine;

    @Start
    void start(Stage stage) {
        racine = new StackPane();
        stage.setScene(new Scene(racine, 300, 200));
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
    @DisplayName("installer : les trois tables reçoivent « Colonnes… » au clic droit ; le ☰ le porte une fois")
    void installer_cable_les_trois_tables_et_le_menu(FxRobot robot) {
        MenuButton menu = new MenuButton("☰");
        AtomicReference<SelecteurColonnesAnalyse> ref = new AtomicReference<>();
        AtomicReference<TableView<String>> esp = new AtomicReference<>();
        AtomicReference<TableView<String>> car = new AtomicReference<>();
        AtomicReference<TableView<String>> obs = new AtomicReference<>();
        robot.interact(() -> {
            esp.set(tableAvec("Espèce", "Détections"));
            car.set(tableAvec("Carré", "Richesse"));
            obs.set(tableAvec("Passage", "Statut"));
            SelecteurColonnesAnalyse selecteur =
                    new SelecteurColonnesAnalyse(esp.get(), car.get(), obs.get(), menu, () -> Regroupement.PAR_ESPECE);
            selecteur.installer(List.of(new MenuItem("Fiche de l'espèce")), List.of(new MenuItem("Fiche de l'espèce")));
            ref.set(selecteur);
        });

        assertThat(dernier(esp.get())).isEqualTo("Colonnes…");
        assertThat(dernier(car.get())).isEqualTo("Colonnes…");
        assertThat(dernier(obs.get())).isEqualTo("Colonnes…");
        assertThat(menu.getItems())
                .filteredOn(i -> "Colonnes…".equals(i.getText()))
                .hasSize(1);
    }

    @Test
    @DisplayName("adaptateur : décrit les trois tables et rejoue ordre + visibilité")
    void adaptateur_decrit_et_restaure_les_trois_tables(FxRobot robot) {
        AtomicReference<TableView<String>> espRef = new AtomicReference<>();
        AtomicReference<Map<String, DescripteurColonnes>> capture = new AtomicReference<>();
        AtomicReference<SelecteurColonnesAnalyse> selRef = new AtomicReference<>();
        robot.interact(() -> {
            TableView<String> esp = tableAvec("Espèce", "Détections", "Période");
            TableView<String> car = tableAvec("Carré", "Richesse");
            TableView<String> obs = tableAvec("Passage", "Statut");
            SelecteurColonnesAnalyse selecteur =
                    new SelecteurColonnesAnalyse(esp, car, obs, new MenuButton("☰"), () -> Regroupement.PAR_ESPECE);
            selecteur.installer(List.of(new MenuItem("Fiche")), List.of(new MenuItem("Fiche obs")));
            // Modifie l'inventaire par espèce : masque « Détections », ordre Période, Espèce, Détections.
            esp.getColumns().get(1).setVisible(false);
            esp.getColumns()
                    .setAll(
                            esp.getColumns().get(2),
                            esp.getColumns().get(0),
                            esp.getColumns().get(1));
            capture.set(selecteur.adaptateur().decrire());
            espRef.set(esp);
            selRef.set(selecteur);
        });

        assertThat(capture.get().keySet()).containsExactlyInAnyOrder("especes", "carres", "observations");

        robot.interact(() -> {
            TableView<String> esp = espRef.get();
            // Remet à plat, puis rejoue la capture.
            esp.getColumns().forEach(c -> c.setVisible(true));
            esp.getColumns()
                    .setAll(
                            esp.getColumns().get(1),
                            esp.getColumns().get(2),
                            esp.getColumns().get(0));
            selRef.get().adaptateur().restaurer(capture.get());
        });

        assertThat(espRef.get().getColumns())
                .extracting(TableColumn::getText)
                .containsExactly("Période", "Espèce", "Détections");
        assertThat(espRef.get().getColumns().get(2).isVisible())
                .as("« Détections » redevient masquée")
                .isFalse();
        assertThat(espRef.get().getColumns().get(1).isVisible())
                .as("« Espèce » (identité) reste affichée")
                .isTrue();
    }

    private static String dernier(TableView<?> table) {
        var items = table.getContextMenu().getItems();
        return items.get(items.size() - 1).getText();
    }
}
