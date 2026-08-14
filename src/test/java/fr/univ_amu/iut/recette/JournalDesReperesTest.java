package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Éprouve le fichier de repères lui-même : ce que [ReperesDeSeanceTest] ne montre pas.
class JournalDesReperesTest {

    @Test
    @DisplayName("Le journal crée son dossier plutôt que d'échouer dessus")
    void le_journal_cree_son_dossier(@TempDir Path dossier) {
        // Le profil l'envoie sous `target/recette-filmee/`, qui n'existe pas au premier lancement.
        Path journal = dossier.resolve("pas/encore/la/reperes.tsv");

        JournalDesReperes.vers(journal);

        assertThat(journal).exists();
    }

    @Test
    @DisplayName("Rouvrir un journal ne réécrit pas son en-tête")
    void rouvrir_ne_reecrit_pas_l_entete(@TempDir Path dossier) throws IOException {
        // Une séance peut ouvrir le journal plusieurs fois - surefire forke, l'extension se
        // reconstruit. Un en-tête par ouverture ferait des lignes de commentaire au milieu des
        // données, que le montage aurait à filtrer en plus d'être trompeuses à la lecture.
        Path journal = dossier.resolve("reperes.tsv");

        JournalDesReperes.vers(journal).note(JournalDesReperes.Borne.DEBUT, "A.un", List.of("S1-01"), 1_000L);
        JournalDesReperes.vers(journal).note(JournalDesReperes.Borne.FIN, "A.un", List.of("S1-01"), 2_000L);

        assertThat(Files.readAllLines(journal).stream().filter(l -> l.startsWith("#")))
                .hasSize(1);
    }

    @Test
    @DisplayName("Une borne s'écrit en quatre colonnes séparées par des tabulations")
    void une_borne_s_ecrit_en_quatre_colonnes(@TempDir Path dossier) throws IOException {
        Path journal = dossier.resolve("reperes.tsv");

        JournalDesReperes.vers(journal)
                .note(
                        JournalDesReperes.Borne.DEBUT,
                        "MaClasse.ma_methode",
                        List.of("S1-04", "S1-26"),
                        1_755_188_400_123L);

        assertThat(Files.readAllLines(journal))
                .last()
                .isEqualTo("1755188400123\tdebut\tMaClasse.ma_methode\tS1-04,S1-26");
    }

    @Test
    @DisplayName("Sans propriété, il n'y a pas de journal du tout")
    void sans_propriete_il_n_y_a_pas_de_journal() {
        System.clearProperty(JournalDesReperes.PROPRIETE);

        assertThat(JournalDesReperes.depuisLaPropriete()).isEmpty();
    }

    @Test
    @DisplayName("Une propriété vide vaut une propriété absente")
    void une_propriete_vide_vaut_une_propriete_absente() {
        // Le `pom.xml` déclare la propriété vide par défaut, et Maven la transmet telle quelle :
        // sans ce cas, un `mvn test` ordinaire créerait un journal dans un fichier sans nom.
        System.setProperty(JournalDesReperes.PROPRIETE, "  ");
        try {
            assertThat(JournalDesReperes.depuisLaPropriete()).isEmpty();
        } finally {
            System.clearProperty(JournalDesReperes.PROPRIETE);
        }
    }
}
