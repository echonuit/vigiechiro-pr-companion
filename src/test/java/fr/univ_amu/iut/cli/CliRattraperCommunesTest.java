package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.fixture.SortieCapturee;
import java.io.PrintStream;
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

    private final SortieCapturee capture = new SortieCapturee();
    private final PrintStream sortie = capture.sortie();
    private final PrintStream erreur = capture.erreur();

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
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
        assertThat(capture.texte()).contains("rien à rattraper");
    }

    @Test
    @DisplayName("rattraper-communes : un point sans GPS reste hors du rattrapage, exit 0")
    void point_sans_gps() {
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        // Sans `position(...)` : la fixture laisse le GPS nul, ce qui est précisément le cas éprouvé ici.
        JeuDeDonneesPassage.dans(source)
                .utilisateur("u-1")
                .carre("130711")
                .point("A1")
                .semerSiteEtPoint();

        int code = cli.executer(new String[] {"rattraper-communes"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(capture.texte()).contains("rien à rattraper");
    }
}
