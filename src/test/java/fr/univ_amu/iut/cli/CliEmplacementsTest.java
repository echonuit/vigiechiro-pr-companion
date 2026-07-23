package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
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

/// Parité CLI de l'onglet « Emplacements » (#1038, passe 2 du cycle de chantier) : la commande
/// `emplacements` voit, définit (avec sonde) et réinitialise où vivent le dossier de travail et la base.
///
/// Isolation : l'injecteur est composé avec `vigiechiro.workspace` posé (pour ne pas lire une vraie
/// base), puis la propriété est **effacée** - sinon elle masquerait la configuration écrite
/// (`Workspace.resolu()` donne la priorité à la propriété). La commande lit alors bien la config, elle
/// aussi détournée vers un dossier jetable via `vigiechiro.config`.
class CliEmplacementsTest {

    @TempDir
    Path workspace;

    @TempDir
    Path config;

    @TempDir
    Path choix;

    private Cli cli;
    private ByteArrayOutputStream tamponSortie;
    private ByteArrayOutputStream tamponErreur;
    private PrintStream sortie;
    private PrintStream erreur;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        System.setProperty("vigiechiro.config", config.toString());
        Injector injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        System.clearProperty("vigiechiro.workspace"); // la commande doit lire la config, pas la surcharge
        tamponSortie = new ByteArrayOutputStream();
        sortie = new PrintStream(tamponSortie, true, StandardCharsets.UTF_8);
        tamponErreur = new ByteArrayOutputStream();
        erreur = new PrintStream(tamponErreur, true, StandardCharsets.UTF_8);
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
        System.clearProperty("vigiechiro.config");
    }

    private String texteSortie() {
        return tamponSortie.toString(StandardCharsets.UTF_8);
    }

    private String texteErreur() {
        return tamponErreur.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("emplacements : sans configuration, affiche les défauts (non personnalisés)")
    void afficher_les_defauts() {
        int code = cli.executer(new String[] {"emplacements"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie())
                .contains("Emplacements (effet au prochain démarrage)")
                .contains("Dossier de travail", "Base de données")
                .contains("Personnalisés", "non (défauts)")
                .contains("Défaut travail", "Défaut base")
                .contains("VigieChiro-Companion");
    }

    @Test
    @DisplayName("emplacements --definir-* : sonde, écrit, et un affichage suivant reflète le choix")
    void definir_puis_afficher_reflete_le_choix() {
        Path travail = choix.resolve("nuits");
        Path base = choix.resolve("coffre");

        int codeDefinir = cli.executer(
                new String[] {"emplacements", "--definir-travail", travail.toString(), "--definir-base", base.toString()
                },
                sortie,
                erreur);

        assertThat(codeDefinir).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie())
                .contains("Emplacements enregistrés", "le pointeur change, pas les données")
                .as("le compte rendu rappelle les deux chemins retenus")
                .contains(
                        travail.toAbsolutePath().toString(),
                        base.resolve("vigiechiro.db").toAbsolutePath().toString());

        // Un affichage suivant reflète bien ce qui a été écrit.
        cli.executer(new String[] {"emplacements"}, sortie, erreur);
        assertThat(texteSortie())
                .contains("oui")
                .contains(travail.toAbsolutePath().toString())
                .contains(base.resolve("vigiechiro.db").toAbsolutePath().toString());
    }

    @Test
    @DisplayName("emplacements --definir-base <fichier> : refusé par la sonde, code d'erreur, rien d'écrit")
    void definir_un_fichier_est_refuse() throws Exception {
        Path fichier = Files.createFile(choix.resolve("pas-un-dossier"));

        int code = cli.executer(new String[] {"emplacements", "--definir-base", fichier.toString()}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteErreur()).contains("inutilisable", "un fichier, pas un dossier");
        // Rien n'a été écrit : un affichage suivant reste « non (défauts) ».
        cli.executer(new String[] {"emplacements"}, sortie, erreur);
        assertThat(texteSortie()).contains("non (défauts)");
    }

    @Test
    @DisplayName("emplacements --definir-travail <fichier> : la branche travail refuse aussi")
    void definir_travail_fichier_est_refuse() throws Exception {
        Path fichier = Files.createFile(choix.resolve("travail-pas-un-dossier"));

        int code = cli.executer(new String[] {"emplacements", "--definir-travail", fichier.toString()}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteErreur()).contains("dossier de travail", "un fichier, pas un dossier");
    }

    @Test
    @DisplayName("emplacements --reinitialiser : efface le choix précédent")
    void reinitialiser_efface_le_choix() {
        cli.executer(
                new String[] {
                    "emplacements", "--definir-travail", choix.resolve("nuits").toString()
                },
                sortie,
                erreur);

        int code = cli.executer(new String[] {"emplacements", "--reinitialiser"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("rétablis par défaut");
        cli.executer(new String[] {"emplacements"}, sortie, erreur);
        assertThat(texteSortie()).contains("non (défauts)");
    }

    @Test
    @DisplayName("emplacements --reinitialiser --definir-travail : combinaison refusée")
    void reinitialiser_et_definir_sont_exclusifs() {
        int code = cli.executer(
                new String[] {"emplacements", "--reinitialiser", "--definir-travail", choix.toString()},
                sortie,
                erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteErreur()).contains("ne se combine pas");
    }

    @Test
    @DisplayName("emplacements --json : sortie machine avec le drapeau personnalise")
    void afficher_en_json() {
        int code = cli.executer(new String[] {"emplacements", "--json"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("\"personnalise\"").contains("\"espaceDeTravail\"");
    }
}
