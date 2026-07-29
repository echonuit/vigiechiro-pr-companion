package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de [ClasseBadge], source unique de la classe CSS d'un badge de statut / verdict d'un passage
/// (partagée par `ColonneBadge` côté vue et `LignePassage` côté viewmodel). Garantit que les deux couches
/// produisent la même chaîne et que le verdict absent retombe sur « à vérifier ».
class ClasseBadgeTest {

    @Test
    @DisplayName("pour(StatutWorkflow) dérive badge-statut-<nom> (casse indépendante de la locale)")
    void classe_du_statut() {
        assertThat(ClasseBadge.pour(StatutWorkflow.TRANSFORME)).isEqualTo("badge-statut-transforme");
        assertThat(ClasseBadge.pour(StatutWorkflow.PRET_A_DEPOSER)).isEqualTo("badge-statut-pret_a_deposer");
        assertThat(ClasseBadge.pour(StatutWorkflow.DEPOSE)).isEqualTo("badge-statut-depose");
    }

    @Test
    @DisplayName("pour(Verdict) dérive badge-verdict-<nom> ; absent = à vérifier")
    void classe_du_verdict() {
        assertThat(ClasseBadge.pour(Verdict.OK)).isEqualTo("badge-verdict-ok");
        assertThat(ClasseBadge.pour(Verdict.A_JETER)).isEqualTo("badge-verdict-a_jeter");
        assertThat(ClasseBadge.pour((Verdict) null)).isEqualTo("badge-verdict-a_verifier");
    }

    @Test
    @DisplayName("#2774 : chaque statut a sa règle CSS - une classe dérivée sans règle s'affiche sans style")
    void chaque_statut_a_sa_regle_css() throws IOException {
        // La classe se dérive du nom de la valeur : ajouter un statut suffit à produire une classe, mais
        // pas la règle qui l'habille. Le badge s'afficherait alors nu, et rien ne rougirait - un défaut
        // qui ne se voit qu'en regardant l'écran. Ce test le fait rougir.
        String feuille = Files.readString(
                Path.of("src/main/java/fr/univ_amu/iut/commun/view/design.css"), StandardCharsets.UTF_8);
        for (StatutWorkflow statut : StatutWorkflow.values()) {
            assertThat(feuille)
                    .as("design.css doit porter une règle pour le statut « %s »", statut.libelle())
                    .contains("." + ClasseBadge.pour(statut) + " {");
        }
    }
}
