package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie la construction de l'URL d'article Wikipédia FR (titre = binôme latin, espaces → underscores)
/// et sa tolérance à l'absence de nom latin.
class LienWikipediaFrTest {

    private final LienWikipediaFr wikipedia = new LienWikipediaFr();

    @Test
    @DisplayName("Le nom latin devient le titre d'article (espaces → underscores)")
    void nom_latin_devient_titre_article() {
        assertThat(wikipedia.lienPourNomLatin("Pipistrellus pipistrellus"))
                .contains("https://fr.wikipedia.org/wiki/Pipistrellus_pipistrellus");
    }

    @Test
    @DisplayName("Un nom latin absent ou vide ne donne aucun lien")
    void aucun_lien_sans_nom_latin() {
        assertThat(wikipedia.lienPourNomLatin(null)).isEmpty();
        assertThat(wikipedia.lienPourNomLatin("  ")).isEmpty();
    }
}
