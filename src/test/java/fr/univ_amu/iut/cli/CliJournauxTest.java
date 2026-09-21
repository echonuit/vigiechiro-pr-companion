package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.SortieCapturee;
import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le chemin annoncé doit désigner les journaux du workspace de cette invocation.
class CliJournauxTest {
    @TempDir
    Path workspace;

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    void un_incident_indique_les_journaux() throws SQLException {
        Cli cli = cliAvecTableSitesAbsente();
        SortieCapturee capture = new SortieCapturee();

        int code = cli.executer(new String[] {"lister-sites"}, capture.sortie(), capture.erreur());

        assertThat(code).isEqualTo(1);
        assertThat(capture.texteErreur()).contains(workspace.resolve("logs").toString());
        assertThat(capture.texte()).isEmpty();
    }

    @Test
    void un_workspace_invalide_ne_masque_pas_l_incident() throws SQLException {
        Cli cli = cliAvecTableSitesAbsente();
        System.setProperty("vigiechiro.workspace", "\0");
        SortieCapturee capture = new SortieCapturee();

        int code = cli.executer(new String[] {"lister-sites"}, capture.sortie(), capture.erreur());

        assertThat(code).isEqualTo(1);
        assertThat(capture.texteErreur())
                .contains("monitoring_site", "Journaux indisponibles")
                .doesNotContain("InvalidPathException", "at fr.");
        assertThat(capture.texte()).isEmpty();
    }

    private Cli cliAvecTableSitesAbsente() throws SQLException {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        var injecteur = Cli.injecteurApplicatif();
        injecteur
                .getInstance(fr.univ_amu.iut.commun.persistence.MigrationSchema.class)
                .migrer();
        try (var connexion = injecteur.getInstance(SourceDeDonnees.class).getConnection();
                var requete = connexion.createStatement()) {
            requete.execute("DROP TABLE monitoring_site");
        }
        return new Cli(injecteur);
    }

    @Test
    void les_refus_et_erreurs_d_usage_ne_renvoient_pas_aux_journaux() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Cli cli = Cli.applicative();
        for (String[] arguments : new String[][] {
            {"creer-site", "--carre", "invalide"}, {"statut-passage"}, {"statut-passage", "--passage", "999"}
        }) {
            SortieCapturee capture = new SortieCapturee();
            assertThat(cli.executer(arguments, capture.sortie(), capture.erreur()))
                    .isEqualTo(2);
            assertThat(capture.texteErreur())
                    .doesNotContain(workspace.resolve("logs").toString());
        }
    }
}
