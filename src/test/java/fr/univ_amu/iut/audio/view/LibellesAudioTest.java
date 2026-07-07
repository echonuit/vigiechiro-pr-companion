package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.Taxon;
import javafx.util.StringConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests unitaires des libellés/convertisseurs des `ComboBox` de la vue audio, extraits de
/// [SonsValidationController] : fonctions pures, aucune scène JavaFX à monter.
class LibellesAudioTest {

    @Test
    @DisplayName("taxon : « CODE (Nom) » avec nom vernaculaire, code seul quand il manque ou est blanc")
    void taxon_formate_code_et_nom() {
        assertThat(LibellesAudio.taxon(new Taxon("Pippip", "Pipistrellus pipistrellus", "Pipistrelle commune", 1L)))
                .isEqualTo("Pippip (Pipistrelle commune)");
        assertThat(LibellesAudio.taxon(new Taxon("Pippip", "Pipistrellus pipistrellus", null, 1L)))
                .isEqualTo("Pippip");
        assertThat(LibellesAudio.taxon(new Taxon("Pippip", "Pipistrellus pipistrellus", "  ", 1L)))
                .isEqualTo("Pippip");
    }

    @Test
    @DisplayName("mode : un libellé lisible pour chaque mode de revue")
    void mode_libelle_lisible() {
        assertThat(LibellesAudio.mode(ModeRevue.ACTIVITE)).contains("Activité");
        assertThat(LibellesAudio.mode(ModeRevue.INVENTAIRE)).contains("Inventaire");
    }

    @Test
    @DisplayName("converter : affiche via la fonction fournie ; fromString reste neutre (combos non éditables)")
    void converter_affiche_et_ignore_la_saisie() {
        StringConverter<Integer> convertisseur = LibellesAudio.converter(n -> "n=" + n);

        assertThat(convertisseur.toString(7)).isEqualTo("n=7");
        assertThat(convertisseur.fromString("n=7")).isNull();
    }
}
