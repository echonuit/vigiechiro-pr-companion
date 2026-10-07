package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Ce que [ColonneAbregeable] pose, et dans quel ordre de dépendance (#5113) : la marque ne va pas
/// sans l'infobulle qui rend le texte entier.
@ExtendWith(ApplicationExtension.class)
class ColonneAbregeableTest {

    private static final String TITRE = "Fichier";
    private static final String LONG = "Car640380-2026-Pass2-A1-PaRecPR1925492_20260622_202500_000.wav";

    private TableView<String> table;

    @Start
    void demarrer(Stage fenetre) {
        TableColumn<String, String> colonne = new TableColumn<>(TITRE);
        colonne.setCellValueFactory(ligne -> new ReadOnlyStringWrapper(ligne.getValue()));
        colonne.setPrefWidth(120);
        ColonneAbregeable.assumer(colonne);
        table = new TableView<>(FXCollections.observableArrayList(LONG, ""));
        table.getColumns().add(colonne);
        FenetreAjustable.poserHabillee(fenetre, new StackPane(table), 320, 240);
        FenetreAjustable.afficher(fenetre);
    }

    @Test
    @DisplayName("#5113 : une cellule renseignée est marquée et rend son texte entier au survol")
    void une_cellule_renseignee_se_relit_au_survol() {
        InfobullesDeColonne.seRelisentAuSurvol(table, TITRE, LONG);
    }

    @Test
    @DisplayName("#5113 : une cellule sans texte ne porte pas d'infobulle, ni la ligne vide ni les suivantes")
    void une_cellule_sans_texte_ne_porte_pas_d_infobulle() {
        assertThat(InfobullesDeColonne.lire(table, TITRE))
                .filteredOn(cellule -> !cellule.renseignee())
                .as("la ligne au texte vide et les lignes sans donnée sous elle")
                .hasSizeGreaterThan(1)
                .allSatisfy(cellule -> assertThat(cellule.infobulle()).isNull());
    }
}
