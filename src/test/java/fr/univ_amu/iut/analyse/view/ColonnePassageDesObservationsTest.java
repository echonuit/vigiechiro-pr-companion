package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// La colonne « Passage » du détail d'une espèce (#5901) : ce qu'elle **dessine**, et dans quel ordre
/// elle **trie**.
///
/// Elle affichait `2026-06-22 · n°1`, la date telle que la base la stocke. Le lot 31 (#5761) avait mis
/// en français les dates seules, pas celles prises dans un libellé composé. La forme ISO avait pourtant
/// une vertu : triée comme un texte, elle l'est aussi dans le temps. Ce banc tient donc les deux à la
/// fois, la date lue en français et le tri resté chronologique, sur une table montée et dessinée.
@ExtendWith(ApplicationExtension.class)
class ColonnePassageDesObservationsTest {

    private TableView<ObservationEspece> table;
    private TableColumn<ObservationEspece, PassageObserve> passage;

    @Start
    void start(Stage stage) {
        passage = new TableColumn<>("Passage");
        passage.setPrefWidth(200);
        table = new TableView<>();
        table.getColumns().add(passage);
        ColonnesAnalyse.observations(
                new ColonnesAnalyse.Observations(
                        passage,
                        new TableColumn<>("Carré"),
                        new TableColumn<>("Richesse"),
                        new TableColumn<>("Point"),
                        new TableColumn<>("Commune"),
                        new TableColumn<>("Tadarida"),
                        new TableColumn<>("Observateur"),
                        new TableColumn<>("Statut")),
                carre -> "");
        // Trois nuits de deux mois : en français et triées comme un texte, le 1er juillet passerait
        // avant le 22 juin. Deux passages la même nuit départagent le numéro.
        table.setItems(FXCollections.observableArrayList(
                observation(3, "2026-07-01"), observation(2, "2026-06-22"), observation(1, "2026-06-22")));
        // La fenêtre reste ajustable pour la classe suivante du fork (ADR 4475).
        FenetreAjustable.poserHabillee(stage, table, 320, 240);
        FenetreAjustable.afficher(stage);
    }

    private static ObservationEspece observation(int numeroPassage, String date) {
        return new ObservationEspece(
                numeroPassage,
                numeroPassage,
                numeroPassage,
                numeroPassage,
                2026,
                date,
                "640380",
                "A1",
                "Étang",
                "Pippip",
                0.9,
                null,
                null,
                StatutObservation.values()[0],
                "Ahetze");
    }

    private List<String> textesDessines() {
        return Attente.surLeFil(
                () -> {
                    table.applyCss();
                    table.layout();
                    return table.lookupAll(".table-cell").stream()
                            .map(noeud -> ((TableCell<?, ?>) noeud).getText())
                            .filter(texte -> texte != null && !texte.isBlank())
                            .toList();
                },
                "lire les cellules dessinées de la colonne « Passage »",
                5_000L);
    }

    @Test
    @DisplayName("#5901 : la cellule « Passage » dessine sa date en français")
    void la_cellule_dessine_sa_date_en_francais() {
        assertThat(textesDessines())
                .containsExactly("01/07/2026 · n°3", "22/06/2026 · n°2", "22/06/2026 · n°1")
                .noneMatch(texte -> texte.contains("2026-"));
    }

    @Test
    @DisplayName("#5901 : triée, la colonne range les passages dans le temps, puis par numéro")
    void triee_la_colonne_range_les_passages_dans_le_temps() {
        Attente.surLeFil(
                () -> {
                    table.getSortOrder().setAll(List.of(passage));
                    table.sort();
                    return true;
                },
                "trier la table sur la colonne « Passage »",
                5_000L);

        assertThat(textesDessines())
                .as("le 22 juin avant le 1er juillet, et le passage 1 avant le 2 la même nuit")
                .containsExactly("22/06/2026 · n°1", "22/06/2026 · n°2", "01/07/2026 · n°3");
    }
}
