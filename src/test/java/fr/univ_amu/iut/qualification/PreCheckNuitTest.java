package fr.univ_amu.iut.qualification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.ResultatVerification;
import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import fr.univ_amu.iut.qualification.model.PreCheckNuit.Diagnostic;
import fr.univ_amu.iut.qualification.model.PreCheckNuit.Feu;
import fr.univ_amu.iut.qualification.model.PreCheckNuit.Mesures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests purs (sans base) du moteur {@link PreCheckNuit} : on alimente directement les {@link
 * Mesures} et on vérifie les trois feux ainsi que la conversion en {@link ResultatVerification}.
 */
class PreCheckNuitTest {

  private final PreCheckNuit preCheck = new PreCheckNuit();

  @Test
  @DisplayName("Nuit saine : trois feux verts, résultat conforme et non bloquant")
  void nuit_saine_tout_au_vert() {
    Diagnostic diagnostic = preCheck.evaluer(new Mesures(200, 0, 0, false));

    assertThat(diagnostic.couvertureHoraire()).isEqualTo(Feu.VERT);
    assertThat(diagnostic.nombreFichiers()).isEqualTo(Feu.VERT);
    assertThat(diagnostic.coherenceRenommage()).isEqualTo(Feu.VERT);
    assertThat(diagnostic.toutAuVert()).isTrue();
    assertThat(diagnostic.versResultatVerification().estConforme()).isTrue();
  }

  @Test
  @DisplayName("Nombre de fichiers : 0 → rouge, creux → orange, suffisant → vert")
  void feu_nombre_de_fichiers() {
    assertThat(preCheck.evaluer(new Mesures(0, 0, 0, false)).nombreFichiers()).isEqualTo(Feu.ROUGE);
    assertThat(preCheck.evaluer(new Mesures(30, 0, 0, false)).nombreFichiers())
        .isEqualTo(Feu.ORANGE);
    assertThat(preCheck.evaluer(new Mesures(120, 0, 0, false)).nombreFichiers())
        .isEqualTo(Feu.VERT);
  }

  @Test
  @DisplayName("Cohérence du renommage : un seul fichier divergent → rouge")
  void feu_renommage() {
    assertThat(preCheck.evaluer(new Mesures(120, 0, 0, false)).coherenceRenommage())
        .isEqualTo(Feu.VERT);
    assertThat(preCheck.evaluer(new Mesures(120, 1, 0, false)).coherenceRenommage())
        .isEqualTo(Feu.ROUGE);
  }

  @Test
  @DisplayName("Couverture horaire : écart > 30 min → orange, moitié de nuit manquante → rouge")
  void feu_couverture() {
    assertThat(preCheck.evaluer(new Mesures(120, 0, 20, false)).couvertureHoraire())
        .isEqualTo(Feu.VERT);
    assertThat(preCheck.evaluer(new Mesures(120, 0, 45, false)).couvertureHoraire())
        .isEqualTo(Feu.ORANGE);
    assertThat(preCheck.evaluer(new Mesures(120, 0, 400, true)).couvertureHoraire())
        .isEqualTo(Feu.ROUGE);
  }

  @Test
  @DisplayName("Conversion ResultatVerification : feux non verts → alertes soft, jamais bloquantes")
  void conversion_en_resultat_verification() {
    // Couverture orange + renommage rouge + nombre vert ⇒ deux alertes (consultatives).
    Diagnostic diagnostic = preCheck.evaluer(new Mesures(120, 2, 60, false));

    ResultatVerification resultat = diagnostic.versResultatVerification();
    assertThat(resultat.estConforme()).isFalse();
    assertThat(resultat.estBloquant()).as("le pré-check est consultatif (R13)").isFalse();
    assertThat(resultat.messages()).hasSize(2);
  }

  @Test
  @DisplayName("Des mesures négatives sont refusées")
  void mesures_negatives_refusees() {
    assertThatThrownBy(() -> new Mesures(-1, 0, 0, false))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
