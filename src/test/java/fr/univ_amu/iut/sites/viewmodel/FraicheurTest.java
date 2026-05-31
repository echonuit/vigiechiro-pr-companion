package fr.univ_amu.iut.sites.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests unitaires du calcul de fraîcheur d'un site (logique de présentation pure, sans IHM).
class FraicheurTest {

  private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 5, 31);

  @Test
  @DisplayName("Moins de 7 jours → frais (badge vert)")
  void recent_est_frais() {
    assertThat(Fraicheur.depuis(AUJOURDHUI.minusDays(2), AUJOURDHUI)).isEqualTo(Fraicheur.FRAIS);
    assertThat(Fraicheur.depuis(AUJOURDHUI, AUJOURDHUI)).isEqualTo(Fraicheur.FRAIS);
  }

  @Test
  @DisplayName("Entre 7 et 30 jours → tiède (badge orange)")
  void intermediaire_est_tiede() {
    assertThat(Fraicheur.depuis(AUJOURDHUI.minusDays(7), AUJOURDHUI)).isEqualTo(Fraicheur.TIEDE);
    assertThat(Fraicheur.depuis(AUJOURDHUI.minusDays(30), AUJOURDHUI)).isEqualTo(Fraicheur.TIEDE);
  }

  @Test
  @DisplayName("Plus de 30 jours → froid (badge gris)")
  void ancien_est_froid() {
    assertThat(Fraicheur.depuis(AUJOURDHUI.minusDays(31), AUJOURDHUI)).isEqualTo(Fraicheur.FROID);
  }

  @Test
  @DisplayName("Aucun passage (date nulle) → froid")
  void aucun_passage_est_froid() {
    assertThat(Fraicheur.depuis(null, AUJOURDHUI)).isEqualTo(Fraicheur.FROID);
  }

  @Test
  @DisplayName("Chaque niveau porte une classe CSS distincte")
  void chaque_niveau_a_sa_classe_css() {
    assertThat(Fraicheur.FRAIS.classeBadge()).isEqualTo("badge-frais");
    assertThat(Fraicheur.TIEDE.classeBadge()).isEqualTo("badge-tiede");
    assertThat(Fraicheur.FROID.classeBadge()).isEqualTo("badge-froid");
  }
}
