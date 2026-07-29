package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Table partagée département → région (#2791) : les deux décodages (numéro de carré, code INSEE)
/// doivent lire **la même** table, normalisation corse comprise. L'équivalence avec le référentiel
/// embarqué reste gardée par `RegionDuCarreTest#les_regions_joignent_le_referentiel`.
class RegionsFrancaisesTest {

    @Test
    @DisplayName("Un département métropolitain trouve sa région")
    void departement_metropolitain() {
        assertThat(RegionsFrancaises.pourDepartement("13")).contains("Provence-Alpes-Cote dAzur");
        assertThat(RegionsFrancaises.pourDepartement("67")).contains("Grand-Est");
        assertThat(RegionsFrancaises.pourDepartement("44")).contains("Pays de la Loire");
    }

    @Test
    @DisplayName("Les codes INSEE corses 2A/2B rejoignent la clé 20 du numérotage carré")
    void normalisation_corse() {
        assertThat(RegionsFrancaises.pourDepartement("2A")).contains("Corse");
        assertThat(RegionsFrancaises.pourDepartement("2B")).contains("Corse");
        assertThat(RegionsFrancaises.pourDepartement("20")).contains("Corse");
    }

    @Test
    @DisplayName("L'outre-mer et les codes illisibles renvoient vide, jamais une région fausse")
    void hors_table() {
        assertThat(RegionsFrancaises.pourDepartement("974")).isEmpty();
        assertThat(RegionsFrancaises.pourDepartement("97")).isEmpty();
        assertThat(RegionsFrancaises.pourDepartement(null)).isEmpty();
        assertThat(RegionsFrancaises.pourDepartement("")).isEmpty();
        assertThat(RegionsFrancaises.pourDepartement("9")).isEmpty();
    }

    @Test
    @DisplayName("RegionDuCarre lit la même table : aucune divergence possible entre les deux chaînes")
    void meme_table_que_le_carre() {
        assertThat(RegionDuCarre.regionsConnues()).isEqualTo(RegionsFrancaises.regionsConnues());
        assertThat(RegionDuCarre.pour("130711")).isEqualTo(RegionsFrancaises.pourDepartement("13"));
    }
}
