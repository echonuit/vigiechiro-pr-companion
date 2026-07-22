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
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Invocation de bout en bout de `supprimer-passage` (#2278).
///
/// **Ce que ces tests protègent.** La commande détruit ; sa sûreté ne tient pas à une modale mais à un
/// **drapeau**. Les chemins qui comptent sont donc les chemins **non nominaux** - refus sans drapeau,
/// passage déposé, passage introuvable - et l'invariant qui les traverse : *quand la commande refuse,
/// la base est intacte*. Un refus qui aurait déjà supprimé serait pire que pas de refus du tout.
class CliSupprimerPassageTest {

    private static final String SERIE = "PR1925492";

    @TempDir
    Path workspace;

    private Injector injecteur;
    private Cli cli;
    private ByteArrayOutputStream tamponSortie;
    private ByteArrayOutputStream tamponErreur;
    private PrintStream sortie;
    private PrintStream erreur;
    private PassageDao passageDao;
    private Long idPoint;
    private long idPassage;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
        tamponSortie = new ByteArrayOutputStream();
        tamponErreur = new ByteArrayOutputStream();
        sortie = new PrintStream(tamponSortie, true, StandardCharsets.UTF_8);
        erreur = new PrintStream(tamponErreur, true, StandardCharsets.UTF_8);
        idPassage = semerUnPassage(StatutWorkflow.IMPORTE);
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private String texteSortie() {
        return tamponSortie.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Sans --confirmer : la perte est chiffrée, RIEN n'est supprimé, sortie 2")
    void refus_sans_confirmer_ne_touche_a_rien() {
        int code = cli.executer(
                new String[] {"supprimer-passage", "--passage", String.valueOf(idPassage)}, sortie, erreur);

        assertThat(code)
                .as("2 arrête un script qui enchaînerait, sans le confondre avec un échec (1)")
                .isEqualTo(2);
        assertThat(passageDao.findById(idPassage))
                .as("un refus qui aurait déjà supprimé serait pire que pas de refus du tout")
                .isPresent();
        assertThat(texteSortie())
                .as("le drapeau ne dispense pas d'informer : il déplace le moment où l'on décide")
                .contains("Suppression DÉFINITIVE")
                .contains("2 séquence(s)");
        assertThat(tamponErreur.toString(StandardCharsets.UTF_8)).contains("--confirmer");
    }

    @Test
    @DisplayName("Un passage DÉPOSÉ est refusé par le métier (1), même avec --confirmer")
    void passage_depose_refuse_meme_confirme() {
        long idDepose = semerUnPassage(StatutWorkflow.DEPOSE);

        int code = cli.executer(
                new String[] {"supprimer-passage", "--passage", String.valueOf(idDepose), "--confirmer"},
                sortie,
                erreur);

        assertThat(code)
                .as("le drapeau atteste d'une intention, il ne lève pas une règle métier")
                .isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(passageDao.findById(idDepose)).isPresent();
    }

    @Test
    @DisplayName("Un passage introuvable échoue (1) sans rien détruire")
    void passage_introuvable_echoue() {
        int code =
                cli.executer(new String[] {"supprimer-passage", "--passage", "999999", "--confirmer"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(passageDao.findAll()).as("la base reste intacte").hasSize(1);
    }

    @Test
    @DisplayName("Avec --confirmer : le passage et sa nuit disparaissent, sortie 0")
    void confirme_supprime_le_passage() {
        int code = cli.executer(
                new String[] {"supprimer-passage", "--passage", String.valueOf(idPassage), "--confirmer"},
                sortie,
                erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(passageDao.findById(idPassage)).isEmpty();
        assertThat(texteSortie()).contains("supprimé");
    }

    /// Un passage complet : session, original et deux séquences, pour que la perte annoncée soit chiffrée
    /// sur autre chose que zéro.
    private long semerUnPassage(StatutWorkflow statut) {
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        if (idPoint == null) {
            new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
            new EnregistreurDao(source).insert(new Enregistreur(SERIE, null, null));
            Site site = new SiteDao(source)
                    .insert(new Site(null, "040962", null, Protocole.STANDARD, null, "2026-05-01", "u-1"));
            idPoint = new PointDao(source)
                    .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                    .id();
            passageDao = new PassageDao(source);
        }
        int numero = passageDao.findAll().size() + 1;
        Long id = passageDao
                .insert(new Passage(
                        null,
                        numero,
                        2026,
                        "2026-06-20",
                        "21:30:00",
                        "05:15:00",
                        null,
                        statut,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE))
                .id();
        Long idSession = new SessionDao(source)
                .insert(new SessionDEnregistrement(null, workspace.resolve("session-" + id) + "", null, null, id))
                .id();
        Long idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(null, "orig.wav", "/tmp/orig.wav", null, null, null, idSession))
                .id();
        SequenceDao sequenceDao = new SequenceDao(source);
        for (int i = 0; i < 2; i++) {
            sequenceDao.insert(new SequenceDEcoute(
                    null,
                    "seq_00" + i + ".wav",
                    idOriginal,
                    i,
                    i * 5.0,
                    5.0,
                    "/tmp/seq" + i + ".wav",
                    false,
                    idSession));
        }
        return id;
    }
}
