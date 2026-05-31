package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EcrivainCsvTest {

  @Test
  @DisplayName("Format « Brut » : tous les champs sont encadrés de guillemets")
  void ecrire_brut_tout_guillemete() {
    String sortie =
        EcrivainCsv.avecGuillemets()
            .versChaine(List.of(List.of("noise", "0.93"), List.of("piaf", "0.95")));

    assertThat(sortie).isEqualTo("\"noise\";\"0.93\"\n\"piaf\";\"0.95\"\n");
  }

  @Test
  @DisplayName("Format minimal : guillemets seulement si le champ contient séparateur ou guillemet")
  void ecrire_minimal_guillemete_au_besoin() {
    String sortie = EcrivainCsv.minimal().versChaine(List.of(List.of("a;b", "c\"d", "simple")));

    assertThat(sortie).isEqualTo("\"a;b\";\"c\"\"d\";simple\n");
  }

  @Test
  @DisplayName("Un champ null est écrit comme une chaîne vide")
  void champ_null_devient_vide() {
    String sortie = EcrivainCsv.minimal().versChaine(List.of(Arrays.asList("x", null, "y")));

    assertThat(sortie).isEqualTo("x;;y\n");
  }

  @Test
  @DisplayName("Aller-retour déterministe écriture → lecture (format Brut)")
  void aller_retour_brut() {
    List<List<String>> donnees = List.of(List.of("nom", "taxon"), List.of("f-1", "noise"));

    String csv = EcrivainCsv.avecGuillemets().versChaine(donnees);

    assertThat(new LecteurCsv().lire(csv)).isEqualTo(donnees);
  }

  @Test
  @DisplayName("ecrire(Path) crée le fichier UTF-8 relisible par LecteurCsv")
  void ecrire_fichier(@TempDir Path dossier) {
    Path cible = dossier.resolve("exports/sortie.csv");
    List<List<String>> donnees = List.of(List.of("col1", "col2"), List.of("é", "a;b"));

    EcrivainCsv.minimal().ecrire(cible, donnees);

    assertThat(new LecteurCsv().lire(cible)).isEqualTo(donnees);
  }
}
