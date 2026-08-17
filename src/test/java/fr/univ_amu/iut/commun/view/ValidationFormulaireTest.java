package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.recette.CasDeRecette;
import java.lang.ref.WeakReference;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
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
    @DisplayName("#3647 : le marqueur suit encore la donnée après un passage du ramasse-miettes")
    void marquer_invalide_survit_au_ramasse_miettes() {
        TextField champ = new TextField();
        SimpleIntegerProperty annee = new SimpleIntegerProperty(0);
        BooleanBinding valide = Bindings.createBooleanBinding(() -> annee.get() >= 1000, annee);

        // La forme exacte des appels du produit : l'expression passée est **neuve**, et l'appelant ne la
        // garde pas. Le test précédent passait une `SimpleBooleanProperty` qu'il retenait lui-même : il ne
        // pouvait donc pas voir ce défaut, et ne l'a pas vu pendant des mois.
        ValidationFormulaire.marquerInvalide(champ, valide.not());
        assertThat(champ.getStyleClass())
                .as("année à 0 : le champ est marqué invalide au câblage")
                .contains(ValidationFormulaire.CLASSE_CHAMP_INVALIDE);

        forcerLeRamasseMiettes();
        annee.set(2026);

        assertThat(champ.getStyleClass())
                .as("l'année est devenue valide : sans retenir l'expression, l'écouteur a été collecté"
                        + " et le champ reste rouge sur une saisie correcte (#3647)")
                .doesNotContain(ValidationFormulaire.CLASSE_CHAMP_INVALIDE);
    }

    /// Fait tourner le ramasse-miettes **pour de bon**.
    ///
    /// ⚠️ `System.gc()` n'est qu'une **suggestion** : un test qui suppose qu'il a tourné ne prouve rien
    /// et passerait au vert sur le code défectueux. On pose donc un témoin faible et on insiste jusqu'à
    /// ce qu'il soit réellement collecté, puis on l'affirme.
    private static void forcerLeRamasseMiettes() {
        WeakReference<Object> temoin = new WeakReference<>(new Object());
        for (int essai = 0; essai < 200 && temoin.get() != null; essai++) {
            System.gc();
        }
        assertThat(temoin.get())
                .as("le ramasse-miettes n'a pas tourné : ce test ne prouverait rien")
                .isNull();
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
