package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie la dérivation **carré → région** (#2351) et, surtout, qu'elle **joint réellement** le
/// référentiel embarqué.
///
/// C'est le second test qui compte. Un libellé de région mal orthographié ne casse rien : il ne trouve
/// simplement aucune ligne, et le référentiel retombe sur `national` : silencieusement, en donnant une
/// classe plus large et personne pour s'en apercevoir. La garde confronte donc chaque région produite à
/// ce que la ressource porte vraiment.
class RegionDuCarreTest {

    @Test
    @DisplayName("Les deux premiers chiffres du carré sont le département")
    void deux_premiers_chiffres() {
        assertThat(RegionDuCarre.pour("640380")).contains("Nouvelle Aquitaine");
        assertThat(RegionDuCarre.pour("130711")).contains("Provence-Alpes-Cote dAzur");
        assertThat(RegionDuCarre.pour("770123")).contains("Ile-de-France");
        assertThat(RegionDuCarre.pour("200001"))
                .as("la Corse porte 20 dans un numéro de carré, qui n'est fait que de chiffres")
                .contains("Corse");
    }

    @Test
    @DisplayName("Un carré qu'on ne sait pas situer ne produit pas de région")
    void carre_illisible() {
        // Retomber sur `national` est une lecture plus large mais jamais fausse ; inventer une région
        // le serait.
        assertThat(RegionDuCarre.pour(null)).isEmpty();
        assertThat(RegionDuCarre.pour("6")).isEmpty();
        assertThat(RegionDuCarre.pour("970123"))
                .as("outre-mer : hors référentiel métropolitain")
                .isEmpty();
        assertThat(RegionDuCarre.pour("00xxxx")).isEmpty();
    }

    @Test
    @DisplayName("#2848 : le département du carré s'obtient seul, sans passer par la région")
    void departement_seul() {
        // Rendu public par #2848 : l'audit confronte cette lecture à celle de la commune, et la fiche
        // site la réécrivait en `substring(0, 2)` pour son compte.
        assertThat(RegionDuCarre.departement("640380")).contains("64");
        assertThat(RegionDuCarre.departement("200001"))
                .as("le numérotage carré écrit la Corse « 20 », là où l'INSEE écrit 2A/2B")
                .contains("20");
        assertThat(RegionDuCarre.departement("970123"))
                .as("outre-mer : le département se lit, même si aucune région ne s'en déduit")
                .contains("97");
        assertThat(RegionDuCarre.departement(null)).isEmpty();
        assertThat(RegionDuCarre.departement("6")).isEmpty();
        assertThat(RegionDuCarre.departement("")).isEmpty();
    }

    @Test
    @DisplayName("Les 95 départements métropolitains sont couverts, sans trou")
    void couverture_complete() {
        for (int departement = 1; departement <= 95; departement++) {
            String numero = String.format("%02d0000", departement);
            assertThat(RegionDuCarre.pour(numero))
                    .as("département %02d sans région", departement)
                    .isPresent();
        }
    }

    @Test
    @DisplayName("CHAQUE région produite existe dans le référentiel embarqué (sinon elle ne joint rien)")
    void les_regions_joignent_le_referentiel() {
        Set<String> declinaisonsReelles = ReferentielActivite.embarque().declinaisons().stream()
                .filter(d -> d.startsWith("region:"))
                .map(d -> d.substring("region:".length()))
                .collect(Collectors.toSet());

        assertThat(declinaisonsReelles)
                .as("le référentiel doit porter des déclinaisons régionales")
                .isNotEmpty();
        assertThat(declinaisonsReelles)
                .as("région produite par la dérivation mais absente du référentiel : elle ne joindra rien, "
                        + "et la comparaison retombera sur « national » sans le dire")
                .containsAll(RegionDuCarre.regionsConnues());
    }
}
