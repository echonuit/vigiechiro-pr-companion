package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie l'encodage de l'URL de recherche GBIF et sa tolérance à l'absence de nom latin.
class LienGbifTest {

    private final LienGbif gbif = new LienGbif();

    @Test
    @DisplayName("Le nom latin est encodé dans la requête de recherche")
    void encode_le_nom_latin() {
        assertThat(gbif.lienPourNomLatin("Pipistrellus pipistrellus"))
                .contains("https://www.gbif.org/species/search?q=Pipistrellus+pipistrellus");
    }

    @Test
    @DisplayName("Un nom latin partiel (cf., sp.) reste une requête valide")
    void tolere_les_noms_partiels() {
        assertThat(gbif.lienPourNomLatin("Myotis cf. myotis"))
                .contains("https://www.gbif.org/species/search?q=Myotis+cf.+myotis");
    }

    @Test
    @DisplayName("Un nom latin absent ou vide ne donne aucun lien")
    void aucun_lien_sans_nom_latin() {
        assertThat(gbif.lienPourNomLatin(null)).isEmpty();
        assertThat(gbif.lienPourNomLatin("   ")).isEmpty();
    }
}
