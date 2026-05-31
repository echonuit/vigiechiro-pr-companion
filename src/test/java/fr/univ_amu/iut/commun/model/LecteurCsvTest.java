package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LecteurCsvTest {

  @Test
  @DisplayName("CSV Tadarida « Brut » : champs entre guillemets, séparateur point-virgule")
  void lire_format_brut_avec_guillemets() {
    String brut =
        "\"nom du fichier\";\"tadarida_taxon\";\"tadarida_probabilite\"\n"
            + "\"Car640380-...-000\";\"noise\";\"0.93\"\n";

    List<List<String>> lignes = new LecteurCsv().lire(brut);

    assertThat(lignes).hasSize(2);
    assertThat(lignes.get(0))
        .containsExactly("nom du fichier", "tadarida_taxon", "tadarida_probabilite");
    assertThat(lignes.get(1)).containsExactly("Car640380-...-000", "noise", "0.93");
  }

  @Test
  @DisplayName("CSV Tadarida « Vu » : champs nus, guillemet littéral doublé ré-assemblé")
  void lire_format_vu_sans_guillemets() {
    // En « Vu », les champs vides de Tadarida sont émis comme un guillemet littéral : """" → "
    String vu = "noise;0.93;\"\"\"\"\n";

    List<List<String>> lignes = new LecteurCsv().lire(vu);

    assertThat(lignes).hasSize(1);
    assertThat(lignes.get(0)).containsExactly("noise", "0.93", "\"");
  }

  @Test
  @DisplayName("THLog : séparateur tabulation configurable")
  void lire_thlog_tabulation() {
    String thlog = "Date\tHour\tTemperature\tHumidity\n22/04/2026\t16:02:21\t+23.9\t64\n";

    List<List<String>> lignes = new LecteurCsv('\t').lire(thlog);

    assertThat(lignes.get(0)).containsExactly("Date", "Hour", "Temperature", "Humidity");
    assertThat(lignes.get(1)).containsExactly("22/04/2026", "16:02:21", "+23.9", "64");
  }

  @Test
  @DisplayName("Le séparateur encadré de guillemets ne coupe pas le champ")
  void separateur_dans_guillemets_est_preserve() {
    List<List<String>> lignes = new LecteurCsv().lire("\"a;b\";c\n");

    assertThat(lignes.get(0)).containsExactly("a;b", "c");
  }

  @Test
  @DisplayName("lireSansEntete retire la première ligne")
  void lire_sans_entete() {
    String csv = "col1;col2\nv1;v2\nv3;v4\n";

    assertThat(new LecteurCsv().lireSansEntete(csv))
        .containsExactly(List.of("v1", "v2"), List.of("v3", "v4"));
  }

  @Test
  @DisplayName("Une dernière ligne sans saut de ligne final est lue, une chaîne vide donne 0 ligne")
  void fins_de_lignes() {
    assertThat(new LecteurCsv().lire("a;b")).containsExactly(List.of("a", "b"));
    assertThat(new LecteurCsv().lire("")).isEmpty();
    // \r\n est reconnu comme une seule fin de ligne
    assertThat(new LecteurCsv().lire("a;b\r\nc;d\r\n")).hasSize(2);
  }
}
