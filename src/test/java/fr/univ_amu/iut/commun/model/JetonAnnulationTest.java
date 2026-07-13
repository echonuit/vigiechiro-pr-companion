package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Jeton d'annulation coopérative du socle (#1252) : le drapeau posé par l'IHM est consultable par le
/// travail selon les deux styles (exception via `leverSiAnnule`, retour partiel via `estAnnule`).
class JetonAnnulationTest {

    @Test
    @DisplayName("au repos : pas annulé, leverSiAnnule ne fait rien")
    void au_repos_ne_leve_pas() {
        JetonAnnulation jeton = new JetonAnnulation();

        assertThat(jeton.estAnnule()).isFalse();
        assertThatCode(jeton::leverSiAnnule).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("annulé : estAnnule passe à vrai (idempotent) et leverSiAnnule lève OperationAnnuleeException")
    void annule_leve_au_point_de_controle() {
        JetonAnnulation jeton = new JetonAnnulation();

        jeton.annuler();
        jeton.annuler();

        assertThat(jeton.estAnnule()).isTrue();
        assertThatExceptionOfType(OperationAnnuleeException.class).isThrownBy(jeton::leverSiAnnule);
    }

    @Test
    @DisplayName("le jeton neutre n'est jamais annulé (appels sans annulation)")
    void jeton_neutre_jamais_annule() {
        JetonAnnulation neutre = JetonAnnulation.neutre();

        assertThat(neutre.estAnnule()).isFalse();
        assertThatCode(neutre::leverSiAnnule).doesNotThrowAnyException();
    }
}
