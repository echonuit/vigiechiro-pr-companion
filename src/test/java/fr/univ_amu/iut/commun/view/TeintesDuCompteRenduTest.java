package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Teinte;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Chaque teinte a sa couleur** (#5720). [PanneauCompteRendu] déduit la classe d'un segment du nom
/// de sa teinte, `cr-seg-<teinte>` : une teinte ajoutée sans règle dans `design.css` se peindrait sans
/// fond, et rien d'autre ne le dirait.
class TeintesDuCompteRenduTest {

    @Test
    @DisplayName("#5720 : design.css porte une règle .cr-seg-<teinte> pour chaque teinte")
    void chaque_teinte_a_sa_regle() throws IOException {
        String css;
        try (InputStream flux = PanneauCompteRendu.class.getResourceAsStream("design.css")) {
            assertThat(flux)
                    .as("design.css est sur le classpath, à côté de PanneauCompteRendu")
                    .isNotNull();
            css = new String(flux.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(Arrays.stream(Teinte.values())
                        .map(teinte -> ".cr-seg-" + teinte.name().toLowerCase(Locale.ROOT) + " {")
                        .toList())
                .allSatisfy(regle -> assertThat(css).contains(regle));
    }
}
