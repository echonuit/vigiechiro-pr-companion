package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie la construction déterministe (sans réseau) des liens de fiche espèce : priorité PNA pour les
/// chiroptères connus, repli sur la source universelle par nom latin, rien pour les pseudo-taxons.
class ConstructeurLienEspeceTest {

    private final ConstructeurLienEspece constructeur = new ConstructeurLienEspece();

    @Test
    @DisplayName("Un chiroptère de la table renvoie l'URL exacte de sa fiche PNA")
    void chiroptere_connu_renvoie_la_fiche_pna() {
        EspeceIdentifiee pipistrelle =
                new EspeceIdentifiee("Pippip", "Pipistrellus pipistrellus", "Pipistrelle commune");
        assertThat(constructeur.lienFiche(pipistrelle))
                .contains("https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/pipistrelle-commune/");

        EspeceIdentifiee grandRhino = new EspeceIdentifiee("Rhifer", "Rhinolophus ferrumequinum", "Grand Rhinolophe");
        assertThat(constructeur.lienFiche(grandRhino))
                .contains("https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/grand-rhinolophe/");
    }

    @Test
    @DisplayName("Un taxon hors PNA (oiseau) mais avec nom latin retombe sur GBIF")
    void taxon_hors_pna_retombe_sur_la_source_universelle() {
        EspeceIdentifiee courlis = new EspeceIdentifiee("Numarq", "Numenius arquata", "Courlis cendré");
        assertThat(constructeur.lienFiche(courlis)).contains("https://www.gbif.org/species/search?q=Numenius+arquata");
    }

    @Test
    @DisplayName("Un chiroptère sans fiche PNA mais avec nom latin retombe aussi sur GBIF")
    void chiroptere_sans_fiche_pna_retombe_sur_gbif() {
        EspeceIdentifiee myotisGrandeTaille =
                new EspeceIdentifiee("MyoGT", "Myotis cf. myotis", "Murin de grande taille");
        assertThat(constructeur.lienFiche(myotisGrandeTaille))
                .hasValueSatisfying(url -> assertThat(url).startsWith("https://www.gbif.org/species/search?q="));
    }

    @Test
    @DisplayName("Les pseudo-taxons (noise, piaf) et l'entrée nulle ne donnent aucun lien")
    void pseudo_taxons_et_null_ne_donnent_aucun_lien() {
        assertThat(constructeur.lienFiche(new EspeceIdentifiee("noise", null, null)))
                .isEmpty();
        assertThat(constructeur.lienFiche(new EspeceIdentifiee("piaf", null, null)))
                .isEmpty();
        assertThat(constructeur.lienFiche(null)).isEmpty();
    }

    @Test
    @DisplayName("La source universelle est interchangeable et n'est consultée que hors PNA")
    void source_universelle_interchangeable_et_seulement_hors_pna() {
        SourceUniverselle fausseSource = nomLatin -> Optional.of("URL-DE-TEST");
        ConstructeurLienEspece avecFausseSource = new ConstructeurLienEspece(fausseSource);

        // Hors PNA : la source de repli est bien consultée.
        assertThat(avecFausseSource.lienFiche(new EspeceIdentifiee("Numarq", "Numenius arquata", "Courlis cendré")))
                .contains("URL-DE-TEST");

        // Chiroptère PNA : la fiche spécialisée l'emporte, la source de repli n'est pas utilisée.
        assertThat(avecFausseSource.lienFiche(
                        new EspeceIdentifiee("Pippip", "Pipistrellus pipistrellus", "Pipistrelle commune")))
                .contains("https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/pipistrelle-commune/");
    }
}
