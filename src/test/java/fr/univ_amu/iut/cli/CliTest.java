package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.cli.model.RegistrePassages;
import fr.univ_amu.iut.validation.model.ServiceValidation;
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

/// Tests d'invocation de la [Cli] : dispatch des sous-commandes, codes de sortie et comptes
/// rendus, sur une base SQLite jetable. Le workspace est surchargé vers un `@TempDir` via la
/// propriété système `vigiechiro.workspace` (même mécanisme que `RacineInjecteurTest`),
/// puis la CLI est branchée sur l'injecteur applicatif complet [Cli#injecteurApplicatif()]
/// (socle + features + `CliModule`).
class CliTest {

    @TempDir
    Path workspace;

    private Injector injecteur;
    private Cli cli;
    private ByteArrayOutputStream tamponSortie;
    private ByteArrayOutputStream tamponErreur;
    private PrintStream sortie;
    private PrintStream erreur;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        tamponSortie = new ByteArrayOutputStream();
        tamponErreur = new ByteArrayOutputStream();
        sortie = new PrintStream(tamponSortie, true, StandardCharsets.UTF_8);
        erreur = new PrintStream(tamponErreur, true, StandardCharsets.UTF_8);
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private String texteSortie() {
        return tamponSortie.toString(StandardCharsets.UTF_8);
    }

    private String texteErreur() {
        return tamponErreur.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Sans argument : affiche l'aide et sort en succès (0)")
    void sans_argument_affiche_l_aide() {
        int code = cli.executer(new String[0], sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("Usage").contains("lister-passages").contains("importer");
    }

    @Test
    @DisplayName("Commande inconnue : code de sortie « mauvaise invocation » (2)")
    void commande_inconnue_code_2() {
        int code = cli.executer(new String[] {"fais-le-cafe"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(texteErreur()).contains("Commande inconnue");
    }

    @Test
    @DisplayName("lister-passages sur une base vide : succès (0) et message explicite")
    void lister_passages_base_vide() {
        int code = cli.executer(new String[] {"lister-passages"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("Aucun passage enregistré.");
    }

    @Test
    @DisplayName("lister-passages --json sur une base vide : tableau JSON vide, succès (0)")
    void lister_passages_json_base_vide() {
        int code = cli.executer(new String[] {"lister-passages", "--json"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie().strip()).isEqualTo("[]");
    }

    @Test
    @DisplayName("--help : affiche l'aide générée et sort en succès (0)")
    void aide_via_option_help() {
        int code = cli.executer(new String[] {"--help"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("Usage").contains("lister-passages").contains("Codes de sortie");
    }

    @Test
    @DisplayName("exporter-vu sans --sortie : argument manquant, code 2, rien écrit")
    void exporter_vu_argument_manquant() {
        int code = cli.executer(new String[] {"exporter-vu", "--passage", "1"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(texteErreur()).contains("--sortie");
    }

    @Test
    @DisplayName("exporter-vu sans résultats importés : échec métier (1), fichier non créé")
    void exporter_vu_sans_resultats_echoue() {
        Path cible = workspace.resolve("passage1_Vu.csv");

        int code = cli.executer(
                new String[] {"exporter-vu", "--passage", "1", "--sortie", cible.toString()}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteErreur()).contains("Aucun résultat Tadarida");
        assertThat(Files.exists(cible)).isFalse();
    }

    @Test
    @DisplayName("exporter-lot sur un passage introuvable : échec métier (1)")
    void exporter_lot_passage_introuvable_echoue() {
        int code = cli.executer(new String[] {"exporter-lot", "--passage", "999"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteErreur()).contains("Échec");
    }

    @Test
    @DisplayName("L'injecteur résout l'aide CLI (RegistrePassages) et le service délégué (ServiceValidation)")
    void injecteur_resout_les_collaborateurs() {
        assertThat(injecteur.getInstance(RegistrePassages.class)).isNotNull(); // fourni par CliModule
        assertThat(injecteur.getInstance(ServiceValidation.class)).isNotNull(); // délégué d'exporter-vu
    }
}
