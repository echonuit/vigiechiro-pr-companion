package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Completude;
import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.importation.model.NuitDetectee;
import fr.univ_amu.iut.importation.viewmodel.NuitVM;
import java.time.LocalDate;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.TableCell;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Vérifie le texte dessiné dans les cellules : leur valeur peut être entière malgré une ellipse.
@ExtendWith(ApplicationExtension.class)
class TableNuitsTest {

    @Test
    void les_trois_completudes_restent_lisibles() {
        LocalDate date = LocalDate.of(2026, 7, 3);
        var nuits = FXCollections.<NuitVM>observableArrayList();
        for (Completude etat : Completude.values()) {
            nuits.add(new NuitVM(new NuitDetectee(
                    date, date.atTime(21, 0), date.plusDays(1).atTime(6, 0), List.of(), etat, null, List.of())));
        }
        var table = TableNuits.creer(nuits);
        StackPane racine = new StackPane(table);
        Scene scene = Habillage.scene(racine);
        racine.resize(1020, 180);
        scene.getRoot().applyCss();
        scene.getRoot().layout();

        var cellules = table.lookupAll(".table-cell").stream()
                .filter(TableCell.class::isInstance)
                .map(TableCell.class::cast)
                .filter(cellule -> cellule.getStyleClass().contains("badge") && !cellule.isEmpty())
                .toList();
        assertThat(cellules)
                .extracting(cellule -> cellule.getText())
                .containsExactlyInAnyOrder("complète", "incomplète", "complétude inconnue");
        for (var cellule : cellules) {
            Text rendu = (Text) cellule.lookup(".text");
            assertThat(rendu.getText())
                    .as("le badge « %s » doit rester lisible sans survol", cellule.getText())
                    .isEqualTo(cellule.getText());
            double largeurDisponible = cellule.getWidth()
                    - cellule.getInsets().getLeft()
                    - cellule.getInsets().getRight();
            assertThat(rendu.getLayoutBounds().getWidth())
                    .as("le texte du badge doit tenir entre les marges de la cellule")
                    .isLessThanOrEqualTo(largeurDisponible);
        }
    }
}
