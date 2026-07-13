package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Invocation de bout en bout de la commande `retro-empreintes` (#1299) : code de sortie, bilan
/// affiché, lignes réellement renseignées en base. Même bootstrap que les autres tests CLI
/// (workspace surchargé vers un `@TempDir`, graphe semé via les DAO de l'injecteur applicatif).
class CliRetroEmpreintesTest {

    private static final String SERIE = "1925492";

    @TempDir
    Path workspace;

    private Injector injecteur;
    private Cli cli;
    private ByteArrayOutputStream tamponSortie;
    private PrintStream sortie;
    private PrintStream erreur;
    private SequenceDao sequenceDao;
    private long idSequencePresente;
    private long idSequenceAbsente;

    @BeforeEach
    void preparer() throws IOException {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
        tamponSortie = new ByteArrayOutputStream();
        sortie = new PrintStream(tamponSortie, true, StandardCharsets.UTF_8);
        erreur = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        semerPassageAvecSequences();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("retro-empreintes : succès (0), bilan affiché, la séquence au fichier présent est renseignée")
    void retro_empreintes_renseigne_les_lignes() {
        int code = cli.executer(new String[] {"retro-empreintes"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        String texte = tamponSortie.toString(StandardCharsets.UTF_8);
        assertThat(texte).contains("1 renseignée(s)").contains("1 sans fichier");
        assertThat(sequenceDao.findById(idSequencePresente).orElseThrow().empreinte())
                .isNotNull();
        assertThat(sequenceDao.findById(idSequenceAbsente).orElseThrow().empreinte())
                .as("le fichier est parti : la ligne reste explicitement sans empreinte")
                .isNull();
    }

    /// Un passage, une session, un original, et deux séquences sans empreinte : une dont le fichier
    /// existe, une dont le fichier est parti.
    private void semerPassageAvecSequences() throws IOException {
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "040962", null, Protocole.STANDARD, null, "2026-05-01", "u-1"));
        Long idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, null, null));
        Long idPassage = new PassageDao(source)
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-06-20",
                        "21:30:00",
                        "05:15:00",
                        null,
                        StatutWorkflow.IMPORTE,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE))
                .id();
        Long idSession = new SessionDao(source)
                .insert(new SessionDEnregistrement(null, workspace.resolve("session") + "", null, null, idPassage))
                .id();
        Long idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(
                        null, "orig.wav", workspace.resolve("bruts-partis") + "/orig.wav", null, null, null, idSession))
                .id();
        sequenceDao = new SequenceDao(source);
        Path transformes = Files.createDirectories(workspace.resolve("session").resolve("transformes"));
        Path present = Files.write(transformes.resolve("seq_000.wav"), new byte[1_000]);
        idSequencePresente = sequenceDao
                .insert(new SequenceDEcoute(
                        null, "seq_000.wav", idOriginal, 0, 0.0, 5.0, present.toString(), false, idSession))
                .id();
        idSequenceAbsente = sequenceDao
                .insert(new SequenceDEcoute(
                        null,
                        "seq_001.wav",
                        idOriginal,
                        1,
                        5.0,
                        5.0,
                        transformes.resolve("seq_001.wav").toString(),
                        false,
                        idSession))
                .id();
    }
}
