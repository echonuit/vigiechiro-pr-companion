package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.recette.CasDeRecette;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Tests du composant partagé [ValidationFormulaire] (#790). [ApplicationExtension] initialise le toolkit
/// JavaFX (construction des nœuds) ; aucune scène affichée.
@ExtendWith(ApplicationExtension.class)
class ValidationFormulaireTest {

    @Test
    @DisplayName("gaterBouton lie l'état désactivé du bouton à la validité (vrai → actif, faux → grisé)")
    @CasDeRecette("S1-13")
    void gater_bouton_suit_la_validite() {
        DialogPane pane = new DialogPane();
        ButtonType valider = new ButtonType("Valider", ButtonType.OK.getButtonData());
        pane.getButtonTypes().addAll(valider, ButtonType.CANCEL);
        SimpleBooleanProperty valide = new SimpleBooleanProperty(false);

        ValidationFormulaire.gaterBouton(pane, valider, valide);

        assertThat(pane.lookupButton(valider).isDisabled())
                .as("invalide → grisé")
                .isTrue();
        valide.set(true);
        assertThat(pane.lookupButton(valider).isDisabled()).as("valide → actif").isFalse();
    }

    @Test
    @DisplayName("marquerInvalide ajoute/retire la classe champ-invalide selon l'état, réactivement")
    @CasDeRecette("S1-13")
    void marquer_invalide_bascule_la_classe() {
        TextField champ = new TextField();
        SimpleBooleanProperty invalide = new SimpleBooleanProperty(true);

        ValidationFormulaire.marquerInvalide(champ, invalide);
        assertThat(champ.getStyleClass()).contains(ValidationFormulaire.CLASSE_CHAMP_INVALIDE);

        invalide.set(false);
        assertThat(champ.getStyleClass()).doesNotContain(ValidationFormulaire.CLASSE_CHAMP_INVALIDE);

        // Idempotent : repasser invalide n'ajoute la classe qu'une fois.
        invalide.set(true);
        assertThat(champ.getStyleClass())
                .filteredOn(ValidationFormulaire.CLASSE_CHAMP_INVALIDE::equals)
                .hasSize(1);
    }

    @Test
    @DisplayName("appliquerStyles charge les feuilles partagées (palette + design) sur le DialogPane")
    void appliquer_styles_charge_les_feuilles() {
        DialogPane pane = new DialogPane();
        ValidationFormulaire.appliquerStyles(pane);
        assertThat(pane.getStylesheets())
                .anySatisfy(url -> assertThat(url).endsWith("palette.css"))
                .anySatisfy(url -> assertThat(url).endsWith("design.css"));
    }
}
