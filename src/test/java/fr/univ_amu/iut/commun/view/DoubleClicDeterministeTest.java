package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
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
import org.testfx.util.WaitForAsyncUtils;

/// Ce que le helper de double-clic doit tenir, et que les trois copies recopiées ne tenaient pas
/// toutes (#4554).
@ExtendWith(ApplicationExtension.class)
class DoubleClicDeterministeTest {

    private static final int LIGNES = 60;
    private final AtomicReference<String> ouverte = new AtomicReference<>();

    @Start
    void demarrer(Stage scene) {
        TableView<String> table = new TableView<>(FXCollections.observableArrayList(
                IntStream.range(0, LIGNES).mapToObj(i -> "ligne-" + i).toList()));
        table.setId("tableEssai");
        TableColumn<String, String> colonne = new TableColumn<>("valeur");
        colonne.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()));
        table.getColumns().add(colonne);
        // Une colonne dont le TEXTE AFFICHÉ n'est pas la valeur portée par l'item, comme la colonne
        // de date de M-Multisite qui rend « 21/06/2026 » depuis une date ISO (#4019). C'est sur ce
        // texte-là que se pose le curseur de la personne qui double-clique, donc c'est lui qu'un
        // helper doit savoir viser.
        TableColumn<String, String> formatee = new TableColumn<>("rendu");
        formatee.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()));
        formatee.setCellFactory(c -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String valeur, boolean vide) {
                super.updateItem(valeur, vide);
                setText(vide || valeur == null ? null : "n°" + valeur.replace("ligne-", ""));
            }
        });
        table.getColumns().add(formatee);
        table.setRowFactory(t -> {
            javafx.scene.control.TableRow<String> rangee = new javafx.scene.control.TableRow<>();
            rangee.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !rangee.isEmpty()) {
                    ouverte.set(rangee.getItem());
                }
            });
            return rangee;
        });
        // Une fenêtre volontairement courte : la table ne construit que les lignes visibles.
        scene.setScene(new Scene(new StackPane(table), 320, 200));
        scene.show();
    }

    @Test
    @DisplayName("#4554 : une ligne VISIBLE se double-clique par son index")
    void ligne_visible(FxRobot robot) {
        WaitForAsyncUtils.waitForFxEvents();
        DoubleClicDeterministe.surLigne(robot, "#tableEssai", 1);
        assertThat(ouverte.get()).isEqualTo("ligne-1");
    }

    @Test
    @DisplayName("#4554 : une ligne HORS du cadre se double-clique aussi, la table défilant d'abord")
    void ligne_hors_du_cadre(FxRobot robot) {
        WaitForAsyncUtils.waitForFxEvents();
        // Les `TableRow` sont virtualisés : seules les lignes visibles existent comme noeuds. Sans
        // défilement préalable, la ligne 45 n'est pas « introuvable », elle n'est pas construite -
        // et le message « aucune ligne d'index 45 » se lit pourtant comme une absence de donnée.
        // C'est le défaut de #4016, que deux des trois copies recopiées ne tenaient pas.
        DoubleClicDeterministe.surLigne(robot, "#tableEssai", 45);
        assertThat(ouverte.get()).isEqualTo("ligne-45");
    }

    @Test
    @DisplayName("#4554 : une ligne se double-clique par son CONTENU, sans que le test connaisse sa place")
    void ligne_par_contenu(FxRobot robot) {
        WaitForAsyncUtils.waitForFxEvents();
        // Sept des onze sites d'appel nomment une donnée - « 640001 », une date - et non une
        // position. Leur faire calculer un index exposerait un ordre de tri qu'ils n'ont pas à
        // connaître, et qui les casserait au premier changement de tri.
        DoubleClicDeterministe.surLigneContenant(robot, "#tableEssai", "ligne-45");
        assertThat(ouverte.get()).isEqualTo("ligne-45");
    }

    @Test
    @DisplayName("#4554 : une ligne se vise par ce qui est AFFICHÉ, pas par la valeur sous-jacente")
    void ligne_par_texte_affiche(FxRobot robot) {
        WaitForAsyncUtils.waitForFxEvents();
        // « n°12 » n'existe dans aucun item : c'est ce que la colonne REND. Comparer la valeur
        // portée par l'item ferait manquer la ligne, et c'est ce qui a fait rougir
        // MultisiteViewTest, dont la colonne de date affiche autre chose que ce qu'elle porte.
        DoubleClicDeterministe.surLigneContenant(robot, "#tableEssai", "n°12");
        assertThat(ouverte.get()).isEqualTo("ligne-12");
    }

    @Test
    @DisplayName("#4554 : viser un contenu absent dit ce qu'on cherchait, pas « index -1 »")
    void contenu_absent(FxRobot robot) {
        WaitForAsyncUtils.waitForFxEvents();
        // Un refus qui dit ce qui manque, article A13. « aucune ligne d'index -1 » ferait chercher
        // un défaut de placement là où la donnée est simplement absente.
        assertThatThrownBy(() -> DoubleClicDeterministe.surLigneContenant(robot, "#tableEssai", "ligne-999"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ligne-999");
    }
}
