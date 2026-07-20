package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.IconesSeverite;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation.Severite;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kordamp.ikonli.javafx.FontIcon;
import org.testfx.framework.junit5.ApplicationExtension;

/// Une ligne d'inspection dit sa présence par une **icône** et une **couleur**, plus par un glyphe
/// écrit dans le texte (#2099, ADR 0035).
///
/// Ce test existe parce que le défaut est passé : à la première écriture, `lier` recevait un
/// `ObservableValue<Boolean>` obtenu par `propriete.asObject()`. L'enveloppe n'étant retenue par
/// personne, elle était collectée et l'écouteur ne se déclenchait plus - un journal **présent**
/// s'affichait avec le triangle de l'absence. La compilation passait, aucun test ne regardait l'icône,
/// et c'est une capture ouverte qui l'a montré.
@ExtendWith(ApplicationExtension.class)
class DetailInspectionTest {

    private Label label;
    private FontIcon icone;
    private SimpleBooleanProperty present;
    private SimpleStringProperty texte;

    @BeforeEach
    void monterLesNoeuds() {
        label = new Label();
        icone = new FontIcon();
        present = new SimpleBooleanProperty(false);
        texte = new SimpleStringProperty("Aucun journal LogPR");
    }

    private String glypheAffiche() {
        return icone.getIconLiteral();
    }

    @Test
    @DisplayName("Absent : triangle ambre ; présent : coche verte")
    void l_icone_et_la_classe_suivent_la_presence() {
        DetailInspection.lier(label, icone, present, texte);

        assertThat(glypheAffiche()).isEqualTo(IconesSeverite.glyphe(Severite.AVERTISSEMENT));
        assertThat(label.getStyleClass()).containsExactlyInAnyOrder("insp-detail", "insp-absent");

        present.set(true);
        texte.set("Journal du capteur : PR n° 1925492");

        assertThat(glypheAffiche()).isEqualTo(IconesSeverite.glyphe(Severite.SUCCES));
        assertThat(label.getStyleClass()).containsExactlyInAnyOrder("insp-detail", "insp-ok");
    }

    @Test
    @DisplayName("Le changement APRÈS liaison est celui qui comptait : l'écouteur doit survivre")
    void l_ecouteur_survit_a_l_appel() {
        DetailInspection.lier(label, icone, present, texte);

        // Le bug d'origine ne se voyait pas au premier rendu : l'icône initiale était juste, et c'est
        // le second passage qui restait figé. Forcer une collecte rend le test représentatif de ce que
        // fait la machine virtuelle entre l'ouverture de l'écran et la fin de l'inspection.
        System.gc();
        present.set(true);

        assertThat(glypheAffiche())
                .as("une enveloppe non retenue serait collectée, et l'icône ne bougerait plus")
                .isEqualTo(IconesSeverite.glyphe(Severite.SUCCES));
    }

    @Test
    @DisplayName("Toujours présent : la coche, sans branche « absent »")
    void toujours_present() {
        DetailInspection.lierPresent(label, icone, texte);

        assertThat(glypheAffiche()).isEqualTo(IconesSeverite.glyphe(Severite.SUCCES));
        assertThat(label.getStyleClass()).containsExactlyInAnyOrder("insp-detail", "insp-ok");
    }

    @Test
    @DisplayName("Le texte ne porte plus de marqueur : il est dans l'icône")
    void le_texte_ne_porte_plus_de_glyphe() {
        DetailInspection.lier(label, icone, present, texte);
        present.set(true);
        texte.set("Journal du capteur : PR n° 1925492");

        assertThat(label.getText()).doesNotContain("✓", "⚠");
    }
}
