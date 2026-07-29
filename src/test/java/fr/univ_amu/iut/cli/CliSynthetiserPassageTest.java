package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Test d'intégration de la commande `synthetiser-passage` (#2351) : sur une base **vide mais migrée**,
/// la commande se **câble** (l'injecteur CLI résout `ServiceSynthese` et le référentiel embarqué) et
/// produit une sortie exploitable même sans contact.
///
/// Ce que ce test protège, c'est le **câblage** et le **contrat de sortie**. Le contenu du tableau est
/// couvert au niveau du formateur pur ([fr.univ_amu.iut.analyse.model.ExportSyntheseCsvTest]), et le
/// comportement sur le vrai jar l'est par `src/test/bats/cli.bats` — ici, on veut une défaillance
/// **précise et rapide** le jour où l'injection casse.
class CliSynthetiserPassageTest {

    @TempDir
    Path workspace;

    private Cli cli;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Injector injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private int executer(ByteArrayOutputStream tampon, String... args) {
        PrintStream flux = new PrintStream(tampon, true, StandardCharsets.UTF_8);
        return cli.executer(args, flux, flux);
    }

    @Test
    @DisplayName("synthetiser-passage : le CSV emporte l'avertissement et la citation, code 0")
    void csv_emporte_son_contexte() {
        ByteArrayOutputStream tampon = new ByteArrayOutputStream();

        int code = executer(tampon, "synthetiser-passage", "--passage", "1");

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(tampon.toString(StandardCharsets.UTF_8))
                .as("un CSV quitte l'application : ce qui n'y est pas écrit ne prévient plus personne")
                .contains("Bas Y.")
                .contains("n'est pas un niveau d'enjeu de conservation");
    }

    @Test
    @DisplayName("synthetiser-passage : une nuit sans contact donne les en-têtes, pas un fichier vide")
    void nuit_sans_contact_ecrit_les_en_tetes() {
        ByteArrayOutputStream tampon = new ByteArrayOutputStream();

        int code = executer(tampon, "synthetiser-passage", "--passage", "1");

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(tampon.toString(StandardCharsets.UTF_8)).contains("Code espèce", "Activité", "Q98");
    }

    @Test
    @DisplayName("synthetiser-passage --sortie : écrit le fichier et annonce ce qu'il contient")
    void sortie_ecrit_le_fichier() throws Exception {
        Path sortie = workspace.resolve("synthese.csv");
        ByteArrayOutputStream tampon = new ByteArrayOutputStream();

        int code = executer(tampon, "synthetiser-passage", "--passage", "1", "--sortie", sortie.toString());

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(tampon.toString(StandardCharsets.UTF_8)).contains("Synthèse exportée");
        assertThat(Files.readString(sortie, StandardCharsets.UTF_8)).contains("Bas Y.");
    }

    @Test
    @DisplayName("synthetiser-passage --format json : le contexte est un objet à part")
    void json_porte_le_contexte_a_part() {
        ByteArrayOutputStream tampon = new ByteArrayOutputStream();

        int code = executer(tampon, "synthetiser-passage", "--passage", "1", "--format", "json");

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(tampon.toString(StandardCharsets.UTF_8))
                .as("le format change, l'obligation de citer ne change pas")
                .contains("\"contexte\"")
                .contains("\"source\"")
                .contains("\"avertissement\"");
    }

    @Test
    @DisplayName("synthetiser-passage : un format inconnu se refuse, code 2")
    void format_inconnu_refuse() {
        ByteArrayOutputStream tampon = new ByteArrayOutputStream();

        int code = executer(tampon, "synthetiser-passage", "--passage", "1", "--format", "xml");

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(tampon.toString(StandardCharsets.UTF_8)).contains("Format non pris en charge");
    }
}
