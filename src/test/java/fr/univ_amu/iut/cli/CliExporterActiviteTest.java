package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.fixture.SortieCapturee;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Test d'intégration de la commande `exporter-activite` : sur une base **vide mais migrée**, la commande
/// se **câble** (l'injecteur CLI résout `ServiceActivite`, feature `analyse` toujours active, sans le flag
/// `activite-nuit`), écrit les **en-têtes seuls** d'un passage sans contact et sort en succès. Vérifie aussi
/// le **refus** d'une tranche hors des trois pas et d'un format autre que csv (code 2). Le contenu et le
/// rattachement à la nuit biologique sont couverts, eux, au niveau du formateur pur (`ExportActiviteCsvTest`).
class CliExporterActiviteTest {

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

    private int executer(SortieCapturee capture, String... args) {
        return cli.executer(args, capture.sortie(), capture.erreur());
    }

    @Test
    @DisplayName("exporter-activite : passage sans contact, écrit les en-têtes, code 0")
    void exporter_activite_ecrit_les_en_tetes_sur_un_passage_sans_contact() throws Exception {
        Path sortie = workspace.resolve("activite.csv");
        SortieCapturee tampon = new SortieCapturee();

        int code = executer(
                tampon, "exporter-activite", "--passage", "1", "--sortie", sortie.toString(), "--tranche", "30");

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(tampon.tout()).contains("Activité exportée");
        assertThat(Files.exists(sortie)).isTrue();
        assertThat(Files.readString(sortie, StandardCharsets.UTF_8))
                .startsWith(
                        "\uFEFFCarré;Point;Nuit;Code espèce;Nom espèce;Groupe;Début tranche;Tranche (min);Contacts");
    }

    @Test
    @DisplayName("exporter-activite --tout : couvre tous les passages, code 0 (#2613)")
    void exporter_activite_couvre_la_vue_transverse() throws Exception {
        Path sortie = workspace.resolve("activite-tout.csv");
        SortieCapturee tampon = new SortieCapturee();

        int code = executer(tampon, "exporter-activite", "--tout", "--sortie", sortie.toString());

        assertThat(code)
                .as("la vue transverse de l'écran a désormais son équivalent en ligne de commande")
                .isEqualTo(Cli.CODE_SUCCES);
        assertThat(Files.exists(sortie)).isTrue();
    }

    @Test
    @DisplayName("exporter-activite : --passage et --tout s'excluent, code 2 (#2613)")
    void exporter_activite_refuse_les_deux_portees() {
        SortieCapturee tampon = new SortieCapturee();

        int code = executer(
                tampon,
                "exporter-activite",
                "--passage",
                "1",
                "--tout",
                "--sortie",
                workspace.resolve("a.csv").toString());

        assertThat(code)
                .as("une portée ambiguë se refuse plutôt que de se trancher en silence")
                .isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
    }

    @Test
    @DisplayName("exporter-activite : tranche hors des trois pas refusée, code 2")
    void exporter_activite_refuse_une_tranche_invalide() {
        Path sortie = workspace.resolve("activite.csv");
        SortieCapturee tampon = new SortieCapturee();

        int code = executer(
                tampon, "exporter-activite", "--passage", "1", "--sortie", sortie.toString(), "--tranche", "99");

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(tampon.tout()).contains("Tranche invalide");
    }

    @Test
    @DisplayName("exporter-activite : format autre que csv refusé, code 2")
    void exporter_activite_refuse_un_format_non_csv() {
        Path sortie = workspace.resolve("activite.json");
        SortieCapturee tampon = new SortieCapturee();

        int code = executer(
                tampon, "exporter-activite", "--passage", "1", "--sortie", sortie.toString(), "--format", "json");

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(tampon.tout()).contains("Format non pris en charge");
    }

    @Test
    @DisplayName("#3269 : sur une portée sans contact, --lieu ne prétend pas que le lieu manque")
    void base_vide_ne_se_lit_pas_comme_un_lieu_absent() throws Exception {
        // `--lieu` DÉSIGNE, donc refuse un lieu absent des lignes (ADR 3082). Sans garde, sur une base
        // sans aucun contact il refusait TOUT lieu en code 2, avec « Lieux présents : aucun » - mettant
        // en cause une valeur qui n'y était pour rien. Défaut relevé à la passe 7 de la clôture des
        // suites de #3092, sur le même patron que `lister-passages` et `lister-especes`.
        Path sortie = workspace.resolve("activite-lieu.csv");
        SortieCapturee tampon = new SortieCapturee();

        int code = executer(tampon, "exporter-activite", "--tout", "--sortie", sortie.toString(), "--lieu", "640380");

        assertThat(code)
                .as("un export vide est un résultat valide, pas une erreur d'invocation")
                .isEqualTo(Cli.CODE_SUCCES);
        assertThat(tampon.tout()).doesNotContain("Lieux présents");
        assertThat(Files.exists(sortie))
                .as("le CSV garde ses en-têtes : un script qui en attend un en reçoit un")
                .isTrue();
    }
}
