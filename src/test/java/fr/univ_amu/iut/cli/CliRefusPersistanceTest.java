package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.fixture.SortieCapturee;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Ce qu'un **refus** de la couche persistance vaut en ligne de commande (#3146).
///
/// Le dépôt a une convention, écrite dans `Restaurer` lui-même (#2294) : **2** dit « j'ai refusé,
/// l'état local est intact », **1** dit « j'ai échoué en route », qui laisse l'état incertain. Un
/// script qui enchaîne ne peut agir que s'il sait lequel des deux s'est produit.
///
/// Les refus de la persistance sortaient en **1**, avec une pile. Ils sont pourtant émis **avant**
/// toute écriture : c'est exactement le cas que le code 2 décrit.
class CliRefusPersistanceTest {

    @TempDir
    Path workspace;

    private final SortieCapturee capture = new SortieCapturee();
    private Cli cli;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Injector injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
    }

    @Test
    @DisplayName("restaurer un fichier qui n'est pas une base : refus, code 2")
    void fichier_illisible_est_un_refus() throws IOException {
        Path faux = Files.writeString(workspace.resolve("faux.db"), "ceci n'est pas une base SQLite");

        int code = cli.executer(
                new String[] {"restaurer", faux.toString(), "--confirmer"}, capture.sortie(), capture.erreur());

        assertThat(code)
                .as("rien n'a été touché : un script doit pouvoir le distinguer d'un échec en route")
                .isEqualTo(Cli.CODE_REFUS);
        assertThat(capture.texteErreur())
                .as("un refus s'annonce comme un refus")
                .startsWith("Refus :");
    }

    @Test
    @DisplayName("le message du refus dit ce qui ne va pas, sans jargon d'exception")
    void le_refus_dit_ce_qui_ne_va_pas() throws IOException {
        Path faux = Files.writeString(workspace.resolve("faux.db"), "ceci n'est pas une base SQLite");

        cli.executer(new String[] {"restaurer", faux.toString(), "--confirmer"}, capture.sortie(), capture.erreur());

        assertThat(capture.texteErreur())
                .as("c'est la seule ligne que l'utilisateur lira : elle doit nommer le fichier fautif")
                .contains("illisible")
                .contains("faux.db");
    }

    // La PILE, elle, ne se voit pas d'ici : elle est écrite par le journal, pas par le flux d'erreur.
    // C'est l'E2E `bats`, qui lance un vrai processus et voit les deux, qui la surveille
    // (`cli-sauvegarde.bats`). Un test in-process qui prétendrait la vérifier serait vert quoi qu'il
    // arrive.
}
