package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le type [Commune] (#2791) : le code INSEE fait foi, département et région s'en dérivent -
/// y compris les cas où l'INSEE et le numérotage des carrés divergent (Corse, outre-mer).
class CommuneTest {

    @Test
    @DisplayName("Le département est porté par les deux premiers caractères du code INSEE")
    void departement_metropole() {
        assertThat(new Commune("Aix-en-Provence", "13001").departement()).isEqualTo("13");
        assertThat(new Commune("Strasbourg", "67482").departement()).isEqualTo("67");
    }

    @Test
    @DisplayName("La Corse garde ses codes INSEE 2A/2B, l'outre-mer ses trois caractères")
    void departements_particuliers() {
        assertThat(new Commune("Ajaccio", "2A004").departement()).isEqualTo("2A");
        assertThat(new Commune("Bastia", "2B033").departement()).isEqualTo("2B");
        assertThat(new Commune("Saint-Denis", "97411").departement()).isEqualTo("974");
    }

    @Test
    @DisplayName("La région se dérive via la table partagée, clés de jointure du référentiel")
    void region_derivee() {
        assertThat(new Commune("Aix-en-Provence", "13001").region()).contains("Provence-Alpes-Cote dAzur");
        assertThat(new Commune("Ajaccio", "2A004").region()).contains("Corse");
        assertThat(new Commune("Saint-Denis", "97411").region())
                .as("hors métropole : vide, jamais faux")
                .isEmpty();
    }

    @Test
    @DisplayName("Nom vide ou code INSEE illisible sont refusés à la construction")
    void construction_refusee() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Commune(" ", "13001"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Commune("Aix-en-Provence", ""));
        assertThatIllegalArgumentException().isThrownBy(() -> new Commune("Aix-en-Provence", "1"));
    }
}
