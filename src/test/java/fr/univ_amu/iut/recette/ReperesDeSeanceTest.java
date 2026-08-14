package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.testkit.engine.EngineTestKit;

/// Éprouve l'extension en la faisant **exécuter par le moteur JUnit** (#3774).
///
/// ## Pourquoi pas un contexte simulé
///
/// Un `ExtensionContext` simulé aurait prouvé que le code appelé écrit bien. Il n'aurait rien dit du
/// **câblage**, c'est-à-dire de la seule chose qui casse en silence : une extension que le moteur
/// n'appelle pas produit un journal vide, et un journal vide ressemble à une séance sans cas.
///
/// Le moteur exécute donc de vraies classes d'exemple, avec de vraies annotations, et on relit le
/// fichier qu'il en reste.
class ReperesDeSeanceTest {

    private static final String NOM = "reperes.tsv";

    @Test
    @DisplayName("Les bornes encadrent le test annoté, et lui seul")
    void les_bornes_encadrent_le_test_annote(@TempDir Path dossier) throws IOException {
        Path journal = dossier.resolve(NOM);

        jouer(SeanceExemple.class, journal);

        List<String> lignes = lignesUtiles(journal);
        assertThat(lignes)
                .as("un test sans citation n'a pas de place dans l'index : il ne montre aucun cas")
                .hasSize(2);
        assertThat(lignes.stream().map(ReperesDeSeanceTest::sansInstant))
                .containsExactly(
                        colonnes("debut", "SeanceExemple.avec_cas", "S1-02"),
                        colonnes("fin", "SeanceExemple.avec_cas", "S1-02"));
    }

    @Test
    @DisplayName("Les instants sont ceux de l'horloge murale, la seule que le montage sache lire")
    void les_instants_sont_ceux_de_l_horloge_murale(@TempDir Path dossier) throws IOException {
        // ⚠️ L'assertion qui compte : un `nanoTime` passerait tous les autres tests de ce fichier -
        // deux lignes, dans l'ordre, avec les bonnes colonnes - et rendrait le montage incapable de
        // se raccrocher à quoi que ce soit. Le décalage ne se verrait pas : il produirait des
        // extraits plausibles pris au mauvais endroit.
        Path journal = dossier.resolve(NOM);

        jouer(SeanceExemple.class, journal);

        List<String> lignes = lignesUtiles(journal);
        long debut = instant(lignes.get(0));
        long fin = instant(lignes.get(1));

        assertThat(debut).isCloseTo(System.currentTimeMillis(), within(60_000L));
        assertThat(fin).isGreaterThanOrEqualTo(debut);
    }

    @Test
    @DisplayName("Un test qui cite plusieurs cas les consigne tous")
    void un_test_qui_cite_plusieurs_cas_les_consigne_tous(@TempDir Path dossier) throws IOException {
        Path journal = dossier.resolve(NOM);

        jouer(SeanceMulticas.class, journal);

        assertThat(lignesUtiles(journal).stream().map(ReperesDeSeanceTest::sansInstant))
                .containsExactly(
                        colonnes("debut", "SeanceMulticas.deux_cas", "S1-04,S1-26"),
                        colonnes("fin", "SeanceMulticas.deux_cas", "S1-04,S1-26"));
    }

    @Test
    @DisplayName("Sans la propriété, aucun fichier n'est écrit")
    void sans_la_propriete_rien_n_est_ecrit(@TempDir Path dossier) {
        // Le cas du `mvn test` ordinaire : l'extension peut être chargée, elle ne coûte rien.
        Path journal = dossier.resolve(NOM);
        System.clearProperty(JournalDesReperes.PROPRIETE);

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(SeanceExemple.class))
                .execute();

        assertThat(journal).doesNotExist();
    }

    // ----------------------------------------------------------------------------------------

    private static void jouer(Class<?> seance, Path journal) {
        System.setProperty(JournalDesReperes.PROPRIETE, journal.toString());
        try {
            EngineTestKit.engine("junit-jupiter").selectors(selectClass(seance)).execute();
        } finally {
            System.clearProperty(JournalDesReperes.PROPRIETE);
        }
    }

    private static List<String> lignesUtiles(Path journal) throws IOException {
        return Files.readAllLines(journal).stream()
                .filter(ligne -> !ligne.startsWith("#"))
                .toList();
    }

    /// La ligne sans sa première colonne : l'instant est éprouvé à part, il ne peut pas se comparer
    /// à une valeur écrite d'avance.
    private static String sansInstant(String ligne) {
        return ligne.substring(ligne.indexOf('\t') + 1);
    }

    private static String colonnes(String borne, String test, String cas) {
        return borne + "\t" + test + "\t" + cas;
    }

    private static long instant(String ligne) {
        return Long.parseLong(ligne.split("\t")[0]);
    }

    // ----------------------------------------------------------------------------------------
    // Les séances d'exemple. Imbriquées et statiques : le moteur les exécute sur demande, et
    // surefire ne les ramasse pas (leur nom ne finit pas par Test).
    //
    // ⚠️ Elles portent de VRAIS identifiants de cas, sans quoi le premier devoir du garde de
    // correspondance - « tout identifiant cité existe » - les refuserait. Elles ne couvrent
    // évidemment rien, d'où @FixtureDeRecette, qui les retire du recensement. Que la seconde cite
    // S1-26, un cas perceptif, n'est pas un hasard : retirer l'exclusion fait alors rougir le
    // build tout de suite, plutôt que de gonfler l'index en silence.
    // ----------------------------------------------------------------------------------------

    @FixtureDeRecette
    @ExtendWith(ReperesDeSeance.class)
    static class SeanceExemple {

        @Test
        @CasDeRecette("S1-02")
        void avec_cas() {
            // Rien à faire : ce qui est éprouvé, c'est ce que l'extension écrit autour.
        }

        @Test
        void sans_cas() {
            // Volontairement non annoté.
        }
    }

    @FixtureDeRecette
    @ExtendWith(ReperesDeSeance.class)
    static class SeanceMulticas {

        @Test
        @CasDeRecette({"S1-04", "S1-26"})
        void deux_cas() {
            // Rien à faire.
        }
    }
}
