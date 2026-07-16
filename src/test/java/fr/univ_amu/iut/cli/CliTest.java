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

    /// Exécute une commande avec un flux de sortie neuf et renvoie sa sortie standard (élaguée) : sert à
    /// capturer l'identifiant écrit par `creer-site`/`ajouter-point` sans mêler plusieurs sorties.
    private String executerSortie(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        cli.executer(args, new PrintStream(out, true, StandardCharsets.UTF_8), erreur);
        return out.toString(StandardCharsets.UTF_8).strip();
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
    @DisplayName("statut-passage sans --passage : argument manquant, code 2")
    void statut_passage_argument_manquant() {
        int code = cli.executer(new String[] {"statut-passage"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(texteErreur()).contains("--passage");
    }

    @Test
    @DisplayName("statut-passage sur un passage introuvable : échec métier (1), lecture seule")
    void statut_passage_introuvable_echoue() {
        int code = cli.executer(new String[] {"statut-passage", "--passage", "999"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteErreur()).contains("Échec").contains("introuvable");
    }

    @Test
    @DisplayName("lister-sites sur une base vide : succès (0) et message explicite")
    void lister_sites_base_vide() {
        int code = cli.executer(new String[] {"lister-sites"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("Aucun site enregistré.");
    }

    @Test
    @DisplayName("lister-sites --json sur une base vide : tableau JSON vide, succès (0)")
    void lister_sites_json_base_vide() {
        int code = cli.executer(new String[] {"lister-sites", "--json"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie().strip()).isEqualTo("[]");
    }

    @Test
    @DisplayName("Provisionner : creer-site puis ajouter-point écrivent des ids, retrouvés par lister-sites")
    void provisionner_site_point_et_lister() {
        String idSite = executerSortie("creer-site", "--carre", "640380", "--nom", "Aix centre");
        assertThat(idSite).matches("\\d+");

        String idPoint =
                executerSortie("ajouter-point", "--site", idSite, "--code", "A1", "--lat", "43.5", "--lon", "5.4");
        assertThat(idPoint).matches("\\d+");

        int code = cli.executer(new String[] {"lister-sites"}, sortie, erreur);
        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie())
                .contains("640380")
                .contains("Aix centre")
                .contains("A1")
                .contains("(43.50000, 5.40000)");
    }

    @Test
    @DisplayName("Site sans point : « (aucun point) » en texte, champ point à null en JSON")
    void lister_sites_site_sans_point() {
        executerSortie("creer-site", "--carre", "640381");

        int codeTexte = cli.executer(new String[] {"lister-sites"}, sortie, erreur);
        assertThat(codeTexte).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("640381").contains("(aucun point)");

        String json = executerSortie("lister-sites", "--json");
        assertThat(json).contains("\"carre\": \"640381\"").contains("\"point\": null");
    }

    @Test
    @DisplayName("creer-site avec un carré mal formé : échec métier (1)")
    void creer_site_carre_invalide_echoue() {
        int code = cli.executer(new String[] {"creer-site", "--carre", "pas-un-carre"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteErreur()).contains("Échec");
    }

    @Test
    @DisplayName("ajouter-point sur un site introuvable : échec métier (1)")
    void ajouter_point_site_introuvable_echoue() {
        int code = cli.executer(new String[] {"ajouter-point", "--site", "999", "--code", "A1"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteErreur()).contains("Échec").contains("Site introuvable");
    }

    @Test
    @DisplayName("importer-tadarida sans --csv : argument manquant, code 2")
    void importer_tadarida_argument_manquant() {
        int code = cli.executer(new String[] {"importer-tadarida", "--passage", "1"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(texteErreur()).contains("--csv");
    }

    @Test
    @DisplayName("importer-tadarida avec un CSV introuvable : erreur d'usage (code 2)")
    void importer_tadarida_csv_introuvable() {
        Path absent = workspace.resolve("nexiste-pas.csv");

        int code = cli.executer(
                new String[] {"importer-tadarida", "--passage", "1", "--csv", absent.toString()}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(texteErreur()).contains("introuvable");
    }

    @Test
    @DisplayName("importer-tadarida sur un passage sans nuit importée : échec métier (1)")
    void importer_tadarida_passage_sans_session_echoue() throws Exception {
        Path csv = workspace.resolve("observations.csv");
        try (var flux = getClass().getResourceAsStream("/validation/observations_brut.csv")) {
            Files.copy(flux, csv);
        }

        int code = cli.executer(
                new String[] {"importer-tadarida", "--passage", "999", "--csv", csv.toString()}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteErreur()).contains("Échec").contains("session");
    }

    @Test
    @DisplayName("qualifier avec un verdict inconnu : erreur d'usage (code 2)")
    void qualifier_verdict_inconnu() {
        int code = cli.executer(new String[] {"qualifier", "--passage", "1", "--verdict", "peut-etre"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(texteErreur()).contains("Verdict inconnu");
    }

    @Test
    @DisplayName("qualifier sur un passage introuvable : échec métier (1)")
    void qualifier_passage_introuvable() {
        int code = cli.executer(new String[] {"qualifier", "--passage", "999", "--verdict", "ok"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteErreur()).contains("Échec").contains("introuvable");
    }

    @Test
    @DisplayName("lister-selection sans sélection d'écoute : message clair (code 0)")
    void lister_selection_sans_selection() {
        String texte = executerSortie("lister-selection", "--passage", "1");
        assertThat(texte).contains("Aucune sélection").contains("#1");
    }

    @Test
    @DisplayName("lister-selection --json expose le verdict final proposé et le tableau des séquences")
    void lister_selection_json() {
        String texte = executerSortie("lister-selection", "--passage", "1", "--json");
        assertThat(texte)
                .contains("\"passage\"")
                .contains("\"verdictFinalPropose\"")
                .contains("\"sequences\"");
    }

    @Test
    @DisplayName("qualifier-fichier sans sélection d'écoute : échec métier (1)")
    void qualifier_fichier_sans_selection() {
        int code = cli.executer(
                new String[] {"qualifier-fichier", "--passage", "1", "--sequence", "1", "--verdict", "bon"},
                sortie,
                erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteErreur()).contains("Échec").contains("sélection");
    }

    @Test
    @DisplayName("qualifier-fichier avec un verdict par fichier inconnu : erreur d'usage (code 2)")
    void qualifier_fichier_verdict_inconnu() {
        int code = cli.executer(
                new String[] {"qualifier-fichier", "--passage", "1", "--sequence", "1", "--verdict", "bof"},
                sortie,
                erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(texteErreur()).contains("Verdict de fichier inconnu");
    }

    @Test
    @DisplayName("pre-check sur un passage introuvable : échec métier (1)")
    void precheck_passage_introuvable() {
        int code = cli.executer(new String[] {"pre-check", "--passage", "999"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteErreur()).contains("Échec").contains("introuvable");
    }

    @Test
    @DisplayName("constituer-selection sur un passage introuvable : échec métier (1)")
    void constituer_selection_passage_introuvable() {
        int code = cli.executer(new String[] {"constituer-selection", "--passage", "999"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteErreur()).contains("Échec").contains("introuvable");
    }

    @Test
    @DisplayName("constituer-selection avec une méthode inconnue : erreur d'usage (code 2)")
    void constituer_selection_methode_inconnue() {
        int code = cli.executer(
                new String[] {"constituer-selection", "--passage", "1", "--methode", "magique"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(texteErreur()).contains("Méthode inconnue");
    }

    @Test
    @DisplayName("deposer sur un passage introuvable : échec métier (1)")
    void deposer_passage_introuvable() {
        int code = cli.executer(new String[] {"deposer", "--passage", "999"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteErreur()).contains("Échec");
    }

    @Test
    @DisplayName("deposer sans --passage : argument manquant (code 2)")
    void deposer_argument_manquant() {
        int code = cli.executer(new String[] {"deposer"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(texteErreur()).contains("--passage");
    }

    @Test
    @DisplayName("exporter-observations sans --sortie : argument manquant (code 2)")
    void exporter_observations_argument_manquant() {
        int code = cli.executer(new String[] {"exporter-observations", "--passage", "1"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(texteErreur()).contains("--sortie");
    }

    @Test
    @DisplayName("exporter-observations sur un passage sans observation : CSV d'en-têtes seuls, succès (0)")
    void exporter_observations_csv_entetes() throws Exception {
        Path cible = workspace.resolve("observations.csv");

        int code = cli.executer(
                new String[] {"exporter-observations", "--passage", "999", "--sortie", cible.toString()},
                sortie,
                erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(Files.exists(cible)).isTrue();
        assertThat(Files.readString(cible)).contains("Carré").contains("Taxon Tadarida");
        assertThat(texteSortie()).contains("0 ligne(s)");
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
