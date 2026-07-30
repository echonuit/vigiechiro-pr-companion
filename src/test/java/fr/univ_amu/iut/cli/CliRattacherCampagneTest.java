package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.fixture.SortieCapturee;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Invocation de bout en bout de `rattacher-campagne` (#2355). On sème un passage et une campagne, puis
/// on vérifie le rattachement et le détachement (via `PassageDao`, source de vérité).
class CliRattacherCampagneTest {

    @TempDir
    Path workspace;

    private Cli cli;
    private PassageDao passageDao;
    private long idPassage;
    private long idCampagne;

    private final SortieCapturee capture = new SortieCapturee();
    private final PrintStream sortie = capture.sortie();
    private final PrintStream erreur = capture.erreur();

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Injector injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        passageDao = new PassageDao(source);
        idPassage = JeuDeDonneesPassage.dans(source)
                .carre("640380")
                .point("A1")
                .nuit(1, 2026, "2026-06-20")
                .statut(StatutWorkflow.DEPOSE)
                .verdict(Verdict.OK)
                .semer()
                .idPassage();
        idCampagne = injecteur
                .getInstance(ServiceCampagne.class)
                .creerCampagne("Suivi ENS", 2026, null)
                .id();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("rattacher-campagne relie le passage à la campagne")
    void rattacher() {
        int code = cli.executer(
                new String[] {
                    "rattacher-campagne",
                    "--passage",
                    String.valueOf(idPassage),
                    "--campagne",
                    String.valueOf(idCampagne)
                },
                sortie,
                erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(capture.texte()).contains("rattaché");
        assertThat(passageDao.findById(idPassage).orElseThrow().idCampagne()).isEqualTo(idCampagne);
    }

    @Test
    @DisplayName("rattacher-campagne sans --campagne détache le passage")
    void detacher() {
        cli.executer(
                new String[] {
                    "rattacher-campagne",
                    "--passage",
                    String.valueOf(idPassage),
                    "--campagne",
                    String.valueOf(idCampagne)
                },
                sortie,
                erreur);

        int code = cli.executer(
                new String[] {"rattacher-campagne", "--passage", String.valueOf(idPassage)}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(passageDao.findById(idPassage).orElseThrow().idCampagne()).isNull();
    }
}
