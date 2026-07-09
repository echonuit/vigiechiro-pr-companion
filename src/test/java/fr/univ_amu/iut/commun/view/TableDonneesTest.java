package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.control.TableView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Tests du configurateur [TableDonnees] (#690) : hauteur de ligne fixe + classe `table-donnees`,
/// appliquées de façon idempotente. [ApplicationExtension] initialise le toolkit JavaFX (construction de
/// nœuds) ; aucune scène affichée.
@ExtendWith(ApplicationExtension.class)
class TableDonneesTest {

    @Test
    @DisplayName("uniformiser pose la classe table-donnees (idempotent)")
    void uniformiser_pose_la_classe() {
        TableView<Object> table = new TableView<>();
        TableDonnees.uniformiser(table);
        assertThat(table.getStyleClass()).containsOnlyOnce("table-donnees");

        // Idempotent : un second appel ne duplique pas la classe.
        TableDonnees.uniformiser(table);
        assertThat(table.getStyleClass()).containsOnlyOnce("table-donnees");
    }
}
