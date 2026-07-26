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

    private int executer(ByteArrayOutputStream tampon, String... args) {
        PrintStream flux = new PrintStream(tampon, true, StandardCharsets.UTF_8);
        return cli.executer(args, flux, flux);
    }

    @Test
    @DisplayName("exporter-activite : passage sans contact, écrit les en-têtes, code 0")
    void exporter_activite_ecrit_les_en_tetes_sur_un_passage_sans_contact() throws Exception {
        Path sortie = workspace.resolve("activite.csv");
        ByteArrayOutputStream tampon = new ByteArrayOutputStream();

        int code = executer(
                tampon, "exporter-activite", "--passage", "1", "--sortie", sortie.toString(), "--tranche", "30");

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(tampon.toString(StandardCharsets.UTF_8)).contains("Activité exportée");
        assertThat(Files.exists(sortie)).isTrue();
        assertThat(Files.readString(sortie, StandardCharsets.UTF_8))
                .startsWith("\uFEFFPassage;Nuit;Code espèce;Nom espèce;Groupe;Début tranche;Tranche (min);Contacts");
    }

    @Test
    @DisplayName("exporter-activite : tranche hors des trois pas refusée, code 2")
    void exporter_activite_refuse_une_tranche_invalide() {
        Path sortie = workspace.resolve("activite.csv");
        ByteArrayOutputStream tampon = new ByteArrayOutputStream();

        int code = executer(
                tampon, "exporter-activite", "--passage", "1", "--sortie", sortie.toString(), "--tranche", "99");

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(tampon.toString(StandardCharsets.UTF_8)).contains("Tranche invalide");
    }

    @Test
    @DisplayName("exporter-activite : format autre que csv refusé, code 2")
    void exporter_activite_refuse_un_format_non_csv() {
        Path sortie = workspace.resolve("activite.json");
        ByteArrayOutputStream tampon = new ByteArrayOutputStream();

        int code = executer(
                tampon, "exporter-activite", "--passage", "1", "--sortie", sortie.toString(), "--format", "json");

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(tampon.toString(StandardCharsets.UTF_8)).contains("Format non pris en charge");
    }
}
