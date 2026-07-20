package fr.univ_amu.iut.maj.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NumeroDeVersionTest {

    @Test
    @DisplayName("2.10.0 est plus récent que 2.9.0, ce qu'une comparaison de chaînes rate")
    void compareParNombreEtNonParTexte() {
        NumeroDeVersion neuf = NumeroDeVersion.lire("2.9.0").orElseThrow();
        NumeroDeVersion dix = NumeroDeVersion.lire("2.10.0").orElseThrow();

        // C'est LA raison d'être de ce type : "2.9.0".compareTo("2.10.0") est positif, donc une
        // comparaison de chaînes conclurait que 2.9.0 est plus récent - et tairait la notification
        // exactement au moment où elle devient utile.
        assertThat(dix).isGreaterThan(neuf);
        assertThat("2.10.0".compareTo("2.9.0")).as("le piège que ce type évite").isNegative();
    }

    @Test
    @DisplayName("le préfixe v des tags est accepté")
    void accepteLePrefixeDesTags() {
        assertThat(NumeroDeVersion.lire("v2.22.0")).contains(new NumeroDeVersion(2, 22, 0));
        assertThat(NumeroDeVersion.lire("2.22.0")).contains(new NumeroDeVersion(2, 22, 0));
    }

    @Test
    @DisplayName("ce qui n'est pas exactement un numéro n'est pas lu")
    void refuseCeQuiNEstPasUnNumero() {
        // « 1.0-SNAPSHOT » est la valeur réelle du manifeste hors release : la lire comme 1.0.0
        // ferait annoncer une mise à jour à chaque développeur, à chaque lancement.
        assertThat(NumeroDeVersion.lire("1.0-SNAPSHOT")).isEmpty();
        assertThat(NumeroDeVersion.lire("2.22.0-rc.1"))
                .as("une pré-version doit se taire plutôt que d'être proposée")
                .isEmpty();
        assertThat(NumeroDeVersion.lire("2.22")).isEmpty();
        assertThat(NumeroDeVersion.lire("version de développement")).isEmpty();
        assertThat(NumeroDeVersion.lire("")).isEmpty();
        assertThat(NumeroDeVersion.lire(null)).isEmpty();
    }

    @Test
    @DisplayName("deux versions égales ne se départagent pas")
    void egalitesNeSeDepartagentPas() {
        assertThat(NumeroDeVersion.lire("2.22.0").orElseThrow())
                .isEqualByComparingTo(NumeroDeVersion.lire("v2.22.0").orElseThrow());
    }

    @Test
    @DisplayName("l'ordre porte d'abord sur le majeur, puis le mineur, puis le correctif")
    void ordreLexicographiqueSurLesTroisNombres() {
        assertThat(NumeroDeVersion.lire("3.0.0").orElseThrow())
                .isGreaterThan(NumeroDeVersion.lire("2.99.99").orElseThrow());
        assertThat(NumeroDeVersion.lire("2.22.1").orElseThrow())
                .isGreaterThan(NumeroDeVersion.lire("2.22.0").orElseThrow());
    }
}
