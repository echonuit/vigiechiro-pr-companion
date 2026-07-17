package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.cli.Cli;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.approvaltests.Approvals;
import org.approvaltests.reporters.QuietReporter;
import org.approvaltests.reporters.UseReporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Recette « CLI-golden » : le **premier rejeu déterministe headless** du parcours d'import. On génère
/// une carte SD depuis sa spec (générateur déterministe), on l'importe **via la CLI réelle**
/// (`vigiechiro importer`), et on compare la **sortie** à un golden ApprovalTests.
///
/// La seule ligne non déterministe de la sortie (le chemin absolu du rapport CSV, sous le workspace
/// `@TempDir`) est masquée avant comparaison ; tout le reste (quadruplet, statut, comptages, noms
/// préfixés du rapport) est stable d'une exécution à l'autre. L'horloge n'est jamais consultée car
/// `--annee` et `--passage` sont fournis.
@UseReporter(QuietReporter.class)
class RecetteImportCliGoldenTest {

    private static final String ID_USER = "u-recette";
    private static final String CARRE = "640380";
    private static final String POINT = "Z1";

    @TempDir
    private Path racine;

    private Cli cli;
    private Long idPoint;

    @BeforeEach
    void preparer() throws IOException {
        System.setProperty("vigiechiro.workspace", racine.resolve("ws").toString());
        Injector injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();

        injecteur.getInstance(UtilisateurDao.class).insert(new Utilisateur(ID_USER, "Testeur recette"));
        Site site = injecteur
                .getInstance(SiteDao.class)
                .insert(new Site(null, CARRE, "Site recette", Protocole.STANDARD, null, "2026-05-31", ID_USER));
        PointDEcoute point =
                injecteur.getInstance(PointDao.class).insert(new PointDEcoute(null, POINT, 43.5, 5.4, null, site.id()));
        idPoint = point.id();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    void import_cli_produit_la_sortie_attendue() throws IOException {
        SpecCarteSd spec = new LecteurSpec().lire(Path.of("recette", "fixtures", "spec", "sd-nominale.yaml"));
        Path sd = racine.resolve("sd-nominale");
        new GenerateurCartesSD().genererVers(spec, sd);

        ByteArrayOutputStream tampon = new ByteArrayOutputStream();
        int code = cli.executer(
                new String[] {
                    "importer",
                    "--source",
                    sd.toString(),
                    "--point",
                    String.valueOf(idPoint),
                    "--annee",
                    "2026",
                    "--passage",
                    "2"
                },
                new PrintStream(tampon, true, StandardCharsets.UTF_8),
                new PrintStream(tampon, true, StandardCharsets.UTF_8));

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        Approvals.verify(sortieStable(tampon.toString(StandardCharsets.UTF_8)));
    }

    /// Masque la seule ligne non déterministe (chemin absolu du rapport sous le `@TempDir`) pour que la
    /// sortie soit rejouable à l'identique.
    private static String sortieStable(String sortie) {
        return sortie.replaceAll("(?m)^(  Rapport CSV : ).*$", "$1<workspace>/rapport-import.csv");
    }
}
