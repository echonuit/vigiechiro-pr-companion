package fr.univ_amu.iut.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.JsonSimple;
import fr.univ_amu.iut.diagnostic.model.LectureJsonTableau;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de [LectureJsonTableau], le lecteur de tableau JSON de chaînes utilisé par le
/// diagnostic. On vérifie surtout qu'il est la **réciproque exacte** de `JsonSimple.tableau` (le
/// format d'écriture de l'import), y compris sur les caractères échappés.
class LectureJsonTableauTest {

  @Test
  @DisplayName("Aller-retour JsonSimple.tableau → LectureJsonTableau (avec échappements)")
  void aller_retour_avec_echappements() {
    List<String> origine =
        List.of(
            "Réveil non programmé : Wakeup",
            "Erreur SD : \"écriture\" échouée",
            "ligne1\nligne2\ttab",
            "antislash \\ et fin");

    String json = JsonSimple.tableau(origine);

    assertThat(LectureJsonTableau.lire(json)).containsExactlyElementsOf(origine);
  }

  @Test
  @DisplayName("Tableau vide, null et blanc donnent une liste vide (R19, tolérant)")
  void cas_vides() {
    assertThat(LectureJsonTableau.lire("[]")).isEmpty();
    assertThat(LectureJsonTableau.lire(null)).isEmpty();
    assertThat(LectureJsonTableau.lire("   ")).isEmpty();
    assertThat(LectureJsonTableau.lire("null")).isEmpty();
  }

  @Test
  @DisplayName("Chaîne vide préservée dans le tableau")
  void chaine_vide_preservee() {
    assertThat(LectureJsonTableau.lire(JsonSimple.tableau(List.of("")))).containsExactly("");
  }

  @Test
  @DisplayName("Lit un tableau simple à plusieurs éléments")
  void plusieurs_elements() {
    assertThat(LectureJsonTableau.lire("[\"a\",\"b\",\"c\"]")).containsExactly("a", "b", "c");
  }
}
