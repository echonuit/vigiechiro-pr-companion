package fr.univ_amu.iut.multisite.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.view.PastillesEntieres;
import fr.univ_amu.iut.multisite.model.EtatAnalyse;
import fr.univ_amu.iut.multisite.model.LignePassage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

/// Les trois colonnes à pastille de « Carte & passages » montrent chacun de leurs libellés en
/// entier (#6101).
///
/// Le panneau est chargé depuis son FXML, donc avec les largeurs que l'écran déclare, et chaque
/// énumération est semée en entier : le plus long libellé n'est pas deviné, il est parmi eux.
@ExtendWith(ApplicationExtension.class)
class PastillesDesPassagesTest {

    private static final String FXML = "PanneauPassages.fxml";

    private TableView<LignePassage> table;

    @Start
    void demarrer(Stage fenetre) throws Exception {
        FXMLLoader chargeur = new FXMLLoader(PanneauPassagesController.class.getResource(FXML));
        Parent panneau = chargeur.load();
        PanneauPassagesController controleur = chargeur.getController();
        controleur.installer(FXCollections.observableArrayList(lignes()));
        table = controleur.table();
        FenetreAjustable.poserHabillee(fenetre, panneau, 1400, 500);
        FenetreAjustable.afficher(fenetre);
    }

    /// Une ligne par statut, chacune avec un verdict et un état d'analyse différents : les trois
    /// énumérations sont parcourues en entier, la plus longue des trois fixant le nombre de lignes.
    private static List<LignePassage> lignes() {
        StatutWorkflow[] statuts = StatutWorkflow.values();
        Verdict[] verdicts = Verdict.values();
        EtatAnalyse[] etats = EtatAnalyse.values();
        List<LignePassage> lignes = new ArrayList<>();
        int nombre = Math.max(statuts.length, Math.max(verdicts.length, etats.length));
        for (int i = 0; i < nombre; i++) {
            lignes.add(new LignePassage(
                    (long) i,
                    "640380",
                    "A1",
                    2026,
                    i + 1,
                    "2026-06-21",
                    statuts[i % statuts.length],
                    verdicts[i % verdicts.length],
                    etats[i % etats.length],
                    null,
                    null,
                    "Ahetze",
                    "Étang"));
        }
        return lignes;
    }

    private TableColumn<LignePassage, ?> colonne(String titre) {
        return table.getColumns().stream()
                .filter(colonne -> titre.equals(colonne.getText()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("#6101 : la colonne « Statut » montre les sept statuts en entier")
    void le_statut_est_entier() {
        assertThat(PastillesEntieres.libellesEntiers(table, colonne("Statut"), FXML))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(StatutWorkflow.values())
                        .map(StatutWorkflow::libelle)
                        .toList());
    }

    @Test
    @DisplayName("#6101 : la colonne « Verdict » montre les quatre verdicts en entier")
    void le_verdict_est_entier() {
        assertThat(PastillesEntieres.libellesEntiers(table, colonne("Verdict"), FXML))
                .containsAll(
                        Arrays.stream(Verdict.values()).map(Verdict::libelle).toList());
    }

    @Test
    @DisplayName("#6101 : la colonne « Analyse » montre chaque état qui porte un libellé en entier")
    void l_analyse_est_entiere() {
        assertThat(PastillesEntieres.libellesEntiers(table, colonne("Analyse"), FXML))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(EtatAnalyse.values())
                        .map(EtatAnalyse::libelle)
                        .filter(libelle -> !libelle.isEmpty())
                        .toList());
    }
}
