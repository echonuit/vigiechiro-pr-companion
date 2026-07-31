package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie que les clés du référentiel se **lisent** avant d'être montrées, et surtout que la table
/// d'affichage reste **complète** quand le référentiel évolue.
///
/// Le défaut d'origine (#3049) n'était pas une faute de frappe : c'était une couche manquante. Les
/// clés (`Provence-Alpes-Cote dAzur`, `Foret`) remontaient telles quelles à l'écran, à l'export CSV et
/// au JSON. Personne ne l'avait vu parce que la démonstration se situait en Corse, dont le nom ne porte
/// ni accent ni apostrophe.
class LibellesReferentielTest {

    private static Set<String> declinaisonsDuReferentielEmbarque(String prefixe) throws IOException {
        try (InputStream flux = ReferentielActivite.class.getResourceAsStream("referentiel-activite.csv");
                InputStreamReader lecteur = new InputStreamReader(flux, StandardCharsets.UTF_8)) {
            return new java.io.BufferedReader(lecteur)
                    .lines()
                    .filter(l -> !l.startsWith("#"))
                    .map(l -> l.split(";"))
                    .filter(c -> c.length >= 2 && c[1].startsWith(prefixe))
                    .map(c -> c[1].substring(prefixe.length()))
                    .collect(Collectors.toSet());
        }
    }

    @Test
    @DisplayName("#3049 : la région qui a révélé le défaut se lit avec ses accents et son apostrophe")
    void la_region_se_lit() {
        assertThat(LibellesReferentiel.region("Provence-Alpes-Cote dAzur")).isEqualTo("Provence-Alpes-Côte d'Azur");
        assertThat(LibellesReferentiel.region("Ile-de-France")).isEqualTo("Île-de-France");
        assertThat(LibellesReferentiel.region("Bourgogne-Franche-Comte")).isEqualTo("Bourgogne-Franche-Comté");
    }

    @Test
    @DisplayName("#3049 : un milieu composé se lit comme une mosaïque, pas comme un nom propre")
    void le_milieu_se_lit() {
        assertThat(LibellesReferentiel.milieu("Foret")).isEqualTo("Forêt");
        assertThat(LibellesReferentiel.milieu("Riviere")).isEqualTo("Rivière");
        assertThat(LibellesReferentiel.milieu("Agricole-Foret")).isEqualTo("Agricole et forêt");
    }

    @Test
    @DisplayName("#3049 : toute région du référentiel embarqué est connue de la table")
    void aucune_region_du_referentiel_ne_manque() throws IOException {
        Set<String> regions = declinaisonsDuReferentielEmbarque("region:");

        assertThat(regions)
                .as("le référentiel doit livrer des régions, sinon ce test ne vérifie rien")
                .isNotEmpty();
        assertThat(LibellesReferentiel.clesRegions())
                .as("une région absente de la table s'afficherait avec sa clé brute : c'est le défaut corrigé ici")
                .containsAll(regions);
    }

    @Test
    @DisplayName("#3049 : tout milieu du référentiel embarqué est connu de la table")
    void aucun_milieu_du_referentiel_ne_manque() throws IOException {
        Set<String> milieux = declinaisonsDuReferentielEmbarque("habitat:");

        assertThat(milieux).as("le référentiel doit livrer des milieux").isNotEmpty();
        assertThat(LibellesReferentiel.clesMilieux()).containsAll(milieux);
    }

    @Test
    @DisplayName("#3049 : une clé inconnue se rend telle quelle, plutôt qu'en case vide")
    void une_cle_inconnue_passe() {
        assertThat(LibellesReferentiel.region("region:Mars")).isEqualTo("region:Mars");
        assertThat(LibellesReferentiel.milieu("Cratere")).isEqualTo("Cratere");
        assertThat(LibellesReferentiel.region(null)).isNull();
    }
}
