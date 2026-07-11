package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie la résolution GBIF (recherche → fiche) sans réseau : la fonction « nom latin → clé » est un
/// faux. La détection de l'URL, l'extraction du nom et la construction de la fiche sont ainsi testées
/// hors ligne.
class ResolveurFicheGbifTest {

    @Test
    @DisplayName("Une URL non GBIF (PNA, Wikipédia) passe inchangée")
    void url_non_gbif_inchangee() {
        ResolveurFicheGbif resolveur = new ResolveurFicheGbif(nom -> Optional.of(1L));

        String pna = "https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/pipistrelle-commune/";
        assertThat(resolveur.resoudre(pna)).isEqualTo(pna);

        String wiki = "https://fr.wikipedia.org/wiki/Tettigonia_viridissima";
        assertThat(resolveur.resoudre(wiki)).isEqualTo(wiki);
    }

    @Test
    @DisplayName("URL de recherche GBIF → URL de fiche /species/{clé}, avec le nom latin décodé")
    void recherche_gbif_devient_fiche() {
        AtomicReference<String> nomRecu = new AtomicReference<>();
        ResolveurFicheGbif resolveur = new ResolveurFicheGbif(nom -> {
            nomRecu.set(nom);
            return Optional.of(1716087L);
        });

        String recherche = "https://www.gbif.org/species/search?q=Tettigonia+viridissima";
        assertThat(resolveur.resoudre(recherche)).isEqualTo("https://www.gbif.org/species/1716087");
        assertThat(nomRecu.get()).as("le nom latin est décodé avant résolution").isEqualTo("Tettigonia viridissima");
    }

    @Test
    @DisplayName("Nom non résolu (aucune correspondance) → repli sur l'URL de recherche")
    void non_resolu_repli_sur_recherche() {
        ResolveurFicheGbif resolveur = new ResolveurFicheGbif(nom -> Optional.empty());

        String recherche = "https://www.gbif.org/species/search?q=Bidon+inconnu";
        assertThat(resolveur.resoudre(recherche)).isEqualTo(recherche);
    }
}
