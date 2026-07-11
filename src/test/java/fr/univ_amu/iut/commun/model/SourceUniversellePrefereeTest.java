package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie que la source universelle suit la préférence (relue à chaque appel) : Wikipédia quand le
/// choix est `true`, GBIF sinon.
class SourceUniversellePrefereeTest {

    @Test
    @DisplayName("Préférence Wikipédia : le lien pointe vers Wikipédia FR")
    void preference_wikipedia_delegue_a_wikipedia() {
        SourceUniversellePreferee source = new SourceUniversellePreferee(() -> true);
        assertThat(source.lienPourNomLatin("Pipistrellus pipistrellus"))
                .contains("https://fr.wikipedia.org/wiki/Pipistrellus_pipistrellus");
    }

    @Test
    @DisplayName("Préférence par défaut (GBIF) : le lien pointe vers GBIF")
    void preference_defaut_delegue_a_gbif() {
        SourceUniversellePreferee source = new SourceUniversellePreferee(() -> false);
        assertThat(source.lienPourNomLatin("Pipistrellus pipistrellus"))
                .contains("https://www.gbif.org/species/search?q=Pipistrellus+pipistrellus");
    }

    @Test
    @DisplayName("Le choix est relu à chaque appel (bascule sans reconstruction)")
    void choix_relu_a_chaque_appel() {
        boolean[] prefereWikipedia = {false};
        SourceUniversellePreferee source = new SourceUniversellePreferee(() -> prefereWikipedia[0]);

        assertThat(source.lienPourNomLatin("Nyctalus noctula"))
                .hasValueSatisfying(url -> assertThat(url).contains("gbif.org"));
        prefereWikipedia[0] = true;
        assertThat(source.lienPourNomLatin("Nyctalus noctula"))
                .hasValueSatisfying(url -> assertThat(url).contains("fr.wikipedia.org"));
    }
}
