package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.fixture.SortieCapturee;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// `situer-carre` (#4660) : la facette CLI du geste « Situer », comblée à la passe 2 de la clôture du
/// chantier #4573.
///
/// Les positions sont celles mesurées le 2026-08-27 contre le serveur réel, et servent d'oracle ici
/// comme dans les tests du modèle.
class CliSituerCarreTest {

    /// Position à 374,9 m du centre de `040110`, donc loin de tout bord.
    private static final String INTERIEURE = "44.44674980384396, 6.298116860416506";

    /// Milieu du côté commun à `040110` et `040111` : deux centres à 997,7 m chacun.
    private static final String FRONTALIERE = "44.444990, 6.306335";

    @TempDir
    Path workspaceDir;

    private Cli cli;

    private final SortieCapturee capture = new SortieCapturee();
    private final PrintStream sortie = capture.sortie();
    private final PrintStream erreur = capture.erreur();

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspaceDir.toString());
        var injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("une position intérieure écrit SON numéro, sur six chiffres, et rend 0")
    void position_interieure_ecrit_le_numero() {
        int code = cli.executer(new String[] {"situer-carre", "--position", INTERIEURE}, sortie, erreur);

        assertThat(code).isZero();
        assertThat(capture.texte()).contains("040110");
    }

    @Test
    @DisplayName("sur une frontière, les DEUX candidats sortent, et le code n'est pas 0")
    void frontiere_ecrit_les_candidats() {
        int code = cli.executer(new String[] {"situer-carre", "--position", FRONTALIERE}, sortie, erreur);

        // Un script doit pouvoir distinguer « voici ton carré » de « choisis » sans lire la prose.
        assertThat(code).isNotZero();
        // Les deux numéros sur la sortie STANDARD, le motif sur l'erreur : un script récupère les
        // candidats sans avoir à écarter une phrase.
        assertThat(capture.texte()).contains("040110").contains("040111");
        assertThat(capture.texteErreur()).contains("frontière");
    }

    @Test
    @DisplayName("hors métropole : rien à écrire, et le motif le dit comme une réponse")
    void hors_metropole_le_dit() {
        int code = cli.executer(new String[] {"situer-carre", "--position", "45.0, -20.0"}, sortie, erreur);

        assertThat(code).isNotZero();
        // Le motif part sur l'ERREUR, la sortie standard restant vide : un script qui lit `stdout`
        // n'a rien à écarter, et l'absence de ligne est déjà la réponse.
        assertThat(capture.texte()).isEmpty();
        assertThat(capture.texteErreur()).contains("métropolitaine");
    }

    @Test
    @DisplayName("un texte illisible rend un code d'USAGE, distinct d'une position sans carré")
    void texte_illisible_rend_un_code_d_usage() {
        int code = cli.executer(new String[] {"situer-carre", "--position", "mon jardin"}, sortie, erreur);

        assertThat(code).isEqualTo(picocli.CommandLine.ExitCode.USAGE);
        assertThat(capture.texte()).isEmpty();
        assertThat(capture.texteErreur()).contains("deux nombres");
    }
}
