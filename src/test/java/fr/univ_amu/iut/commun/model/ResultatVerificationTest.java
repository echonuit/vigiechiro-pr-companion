package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Alerte.Niveau;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests purs (sans base) des types de résultat de vérification partagés {@link Alerte} et {@link
 * ResultatVerification}. Aucune dépendance JavaFX ni JDBC : logique métier nue.
 */
class ResultatVerificationTest {

  @Test
  @DisplayName("ok() ne contient aucune alerte et est conforme et non bloquant")
  void ok_est_conforme() {
    ResultatVerification resultat = ResultatVerification.ok();

    assertThat(resultat.alertes()).isEmpty();
    assertThat(resultat.estConforme()).isTrue();
    assertThat(resultat.estBloquant()).isFalse();
  }

  @Test
  @DisplayName("Une alerte soft n'est pas bloquante mais rend le résultat non conforme")
  void alerte_soft() {
    ResultatVerification resultat = ResultatVerification.de(Alerte.soft("hors fenêtre R3"));

    assertThat(resultat.estConforme()).isFalse();
    assertThat(resultat.estBloquant()).isFalse();
    assertThat(resultat.messages()).containsExactly("hors fenêtre R3");
  }

  @Test
  @DisplayName("Une alerte bloquante rend estBloquant() vrai")
  void alerte_bloquante() {
    ResultatVerification resultat =
        ResultatVerification.de(Alerte.bloquante("À jeter non déposable"));

    assertThat(resultat.estBloquant()).isTrue();
    assertThat(resultat.alertesBloquantes()).hasSize(1);
  }

  @Test
  @DisplayName("avec() accumule les alertes sans muter l'instance d'origine")
  void avec_est_immuable() {
    ResultatVerification base = ResultatVerification.ok();

    ResultatVerification enrichi = base.avec(Alerte.soft("a")).avec(Alerte.bloquante("b"));

    assertThat(base.estConforme()).as("l'instance d'origine reste vide").isTrue();
    assertThat(enrichi.messages()).containsExactly("a", "b");
    assertThat(enrichi.estBloquant()).isTrue();
  }

  @Test
  @DisplayName("Les factory positionnent le bon niveau et estBloquante()")
  void factories_alerte() {
    assertThat(Alerte.soft("x").niveau()).isEqualTo(Niveau.SOFT);
    assertThat(Alerte.soft("x").estBloquante()).isFalse();
    assertThat(Alerte.bloquante("y").niveau()).isEqualTo(Niveau.BLOQUANT);
    assertThat(Alerte.bloquante("y").estBloquante()).isTrue();
  }
}
