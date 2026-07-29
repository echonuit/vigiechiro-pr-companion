package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Invocation de bout en bout de la commande `rattraper-communes` (#2791) : code de sortie et bilan
/// affiché. Même bootstrap que les autres tests CLI (workspace surchargé vers un `@TempDir`).
///
/// **Aucun réseau** : les cas exercés (base vide, point sans GPS) n'ont rien à résoudre, donc le
/// résolveur réel n'est jamais sollicité. La résolution elle-même est couverte au niveau service
/// (`ServiceCommunesTest`, résolveur en lambda) - un point géolocalisé appellerait ici la vraie
/// API Géo, ce qu'un test ne fait pas.
class CliRattraperCommunesTest {

    @TempDir
    Path workspace;

    private Injector injecteur;
    private Cli cli;
    private ByteArrayOutputStream tamponSortie;
    private PrintStream sortie;
    private PrintStream erreur;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
        tamponSortie = new ByteArrayOutputStream();
        sortie = new PrintStream(tamponSortie, true, StandardCharsets.UTF_8);
        erreur = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("rattraper-communes : base vide, rien à rattraper, exit 0")
    void base_vide() {
        int code = cli.executer(new String[] {"rattraper-communes"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(tamponSortie.toString(StandardCharsets.UTF_8)).contains("rien à rattraper");
    }

    @Test
    @DisplayName("rattraper-communes : un point sans GPS reste hors du rattrapage, exit 0")
    void point_sans_gps() {
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "130711", null, Protocole.STANDARD, null, "2026-05-01", "u-1"));
        new PointDao(source).insert(new PointDEcoute(null, "A1", null, null, null, site.id()));

        int code = cli.executer(new String[] {"rattraper-communes"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(tamponSortie.toString(StandardCharsets.UTF_8)).contains("rien à rattraper");
    }
}
