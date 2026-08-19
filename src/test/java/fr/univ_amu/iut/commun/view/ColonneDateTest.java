package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.collections.FXCollections;
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

/// Une colonne de date se **lit en français** et se **trie chronologiquement** (#4019).
///
/// ## Ce que ce test tient, et pourquoi c'est celui-là
///
/// Le tri, avant le libellé. C'est la propriété qu'on risquait de casser en francisant : une colonne de
/// chaînes trie **lexicalement**, et `01/07/2026` passe alors avant `22/06/2026`. Un test qui se
/// contenterait de vérifier la forme du texte serait **vert sur une colonne devenue intriable** - il
/// dirait « c'est bien écrit » d'une table où l'utilisateur ne retrouve plus ses nuits.
///
/// Les dates de la fixture franchissent un **changement de mois**, seul endroit où les deux ordres
/// divergent. Trois dates du même mois ne prouveraient rien.
@ExtendWith(ApplicationExtension.class)
class ColonneDateTest {

    private record Ligne(String iso) {}

    private StackPane racine;

    @Start
    void start(Stage stage) {
        racine = new StackPane();
        stage.setScene(Habillage.scene(racine, 400, 300));
        stage.show();
    }

    @Test
    @DisplayName("#4019 : le tri reste chronologique à cheval sur un changement de mois")
    void tri_chronologique_a_cheval_sur_un_mois(FxRobot robot) {
        AtomicReference<List<String>> rendu = new AtomicReference<>();
        robot.interact(() -> {
            TableView<Ligne> table = table();
            TableColumn<Ligne, LocalDate> date = colonneDe(table);

            table.getSortOrder().setAll(date);
            date.setSortType(TableColumn.SortType.ASCENDING);
            table.sort();

            rendu.set(table.getItems().stream().map(Ligne::iso).toList());
        });

        assertThat(rendu.get())
                .as("l'ordre est celui des DATES ; en lexical sur « jj/MM/aaaa », le 01/07 passerait avant le 22/06")
                .containsExactly("2026-06-22", "2026-07-01", "2026-07-14");
    }

    @Test
    @DisplayName("#4019 : la cellule rend la date en français")
    void la_cellule_rend_la_date_en_francais(FxRobot robot) {
        AtomicReference<String> libelle = new AtomicReference<>();
        robot.interact(() -> {
            TableView<Ligne> table = table();
            TableColumn<Ligne, LocalDate> date = colonneDe(table);
            racine.applyCss();
            racine.layout();
            libelle.set(String.valueOf(date.getCellData(0)));
        });

        assertThat(ColonneDate.libelle("2026-07-01")).isEqualTo("01/07/2026");
        assertThat(libelle.get()).isEqualTo("2026-07-01");
    }

    @Test
    @DisplayName("#4019 : une date illisible ne casse ni la valeur ni le libellé")
    void date_illisible_ne_casse_rien() {
        assertThat(ColonneDate.analyser("pas-une-date")).isNull();
        assertThat(ColonneDate.analyser(null)).isNull();
        assertThat(ColonneDate.analyser("")).isNull();
        // Le libellé, lui, rend la chaîne telle quelle : une donnée abîmée doit se voir.
        assertThat(ColonneDate.libelle("pas-une-date")).isEqualTo("pas-une-date");
    }

    private TableView<Ligne> table() {
        TableView<Ligne> table = new TableView<>(FXCollections.observableArrayList(
                new Ligne("2026-07-01"), new Ligne("2026-06-22"), new Ligne("2026-07-14")));
        TableColumn<Ligne, LocalDate> date = new TableColumn<>("Date");
        ColonneDate.configurer(date, Ligne::iso);
        table.getColumns().add(date);
        racine.getChildren().setAll(table);
        return table;
    }

    @SuppressWarnings("unchecked")
    private static TableColumn<Ligne, LocalDate> colonneDe(TableView<Ligne> table) {
        return (TableColumn<Ligne, LocalDate>) table.getColumns().get(0);
    }
}
