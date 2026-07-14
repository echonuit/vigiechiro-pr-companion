package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.ModeValidation;
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
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Les trois gestes de **marquage** en ligne de commande (#1311) : `marquer-douteux`,
/// `marquer-reference`, `poser-certitude`.
///
/// Le cas qui compte le plus est le dernier : **la certitude ne se devine pas**. Ni depuis la probabilité
/// de Tadarida, ni du fait qu'on ait validé. Elle est **vide par défaut**, et la commande **force** à
/// choisir - poser une valeur, ou l'effacer. Une certitude inventée par l'application serait pire que pas
/// de certitude du tout : un naturaliste la lira comme la parole de l'observateur.
class CliMarquageRevueTest {

    @TempDir
    Path workspaceDir;

    private Injector injecteur;
    private Cli cli;
    private SourceDeDonnees source;
    private ByteArrayOutputStream tamponSortie;
    private ByteArrayOutputStream tamponErreur;
    private PrintStream sortie;
    private PrintStream erreur;

    private long idPassage;
    private long idA;
    private long idB;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspaceDir.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
        source = injecteur.getInstance(SourceDeDonnees.class);
        tamponSortie = new ByteArrayOutputStream();
        tamponErreur = new ByteArrayOutputStream();
        sortie = new PrintStream(tamponSortie, true, StandardCharsets.UTF_8);
        erreur = new PrintStream(tamponErreur, true, StandardCharsets.UTF_8);
        semer();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("marquer-douteux pose le drapeau, --retirer le baisse : le geste est réversible")
    void douteux_aller_et_retour() {
        cli.executer(new String[] {"marquer-douteux", "--observation", String.valueOf(idA)}, sortie, erreur);
        assertThat(observation(idA).douteux()).isTrue();

        cli.executer(
                new String[] {"marquer-douteux", "--retirer", "--observation", String.valueOf(idA)}, sortie, erreur);
        assertThat(observation(idA).douteux()).isFalse();
    }

    @Test
    @DisplayName("marquer-reference verse dans la bibliothèque, --retirer l'en sort")
    void reference_aller_et_retour() {
        cli.executer(new String[] {"marquer-reference", "--observation", String.valueOf(idB)}, sortie, erreur);
        assertThat(observation(idB).reference()).isTrue();

        cli.executer(
                new String[] {"marquer-reference", "--retirer", "--observation", String.valueOf(idB)}, sortie, erreur);
        assertThat(observation(idB).reference()).isFalse();
    }

    @Test
    @DisplayName("poser-certitude déclare le jugement de l'observateur, --effacer le retire")
    void certitude_posee_puis_effacee() {
        int code = cli.executer(
                new String[] {"poser-certitude", "--certitude", "PROBABLE", "--observation", idA + "," + idB},
                sortie,
                erreur);

        assertThat(code).isZero();
        assertThat(observation(idA).certitudeObservateur()).isEqualTo(Certitude.PROBABLE);
        assertThat(observation(idB).certitudeObservateur()).isEqualTo(Certitude.PROBABLE);

        cli.executer(
                new String[] {"poser-certitude", "--effacer", "--observation", String.valueOf(idA)}, sortie, erreur);
        assertThat(observation(idA).certitudeObservateur())
                .as("effacer ramène à « non renseignée », localement c'est permis")
                .isNull();
    }

    @Test
    @DisplayName("LE CAS QUI COMPTE : sans --certitude ni --effacer, la commande REFUSE — une certitude ne"
            + " se devine pas, et un défaut silencieux mettrait un jugement qu'on n'a pas porté")
    void certitude_ne_se_devine_pas() {
        int code = cli.executer(new String[] {"poser-certitude", "--observation", String.valueOf(idA)}, sortie, erreur);

        assertThat(code).isNotZero();
        assertThat(observation(idA).certitudeObservateur())
                .as("rien ne doit avoir été posé : la commande n'a pas choisi à la place de l'observateur")
                .isNull();
    }

    @Test
    @DisplayName("Le marquage respecte le même garde-fou : un passage entier sans filtre exige --confirmer")
    void marquage_du_passage_entier_exige_confirmer() {
        int code =
                cli.executer(new String[] {"marquer-douteux", "--passage", String.valueOf(idPassage)}, sortie, erreur);

        assertThat(code).isNotZero();
        assertThat(tamponErreur.toString(StandardCharsets.UTF_8)).contains("--confirmer");
        assertThat(observation(idA).douteux()).isFalse();
        assertThat(observation(idB).douteux()).isFalse();
    }

    private Observation observation(long idObservation) {
        return new ObservationDao(source).findById(idObservation).orElseThrow();
    }

    private void semer() {
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "130711", "Test", Protocole.STANDARD, null, "2026-01-01", "u-1"));
        Long idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "Z41", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur("1925492", null, null));
        idPassage = new PassageDao(source)
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-07-03",
                        "22:00",
                        "06:00",
                        null,
                        StatutWorkflow.IMPORTE,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        "1925492"))
                .id();
        Long idSession = new SessionDao(source)
                .insert(new SessionDEnregistrement(null, "/ws/session", null, null, idPassage))
                .id();
        Long idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(null, "brut.wav", "/ws/brut.wav", 5.0, 384000, null, idSession))
                .id();

        idA = observation(idSession, idOriginal, 0);
        idB = observation(idSession, idOriginal, 1);
    }

    private long observation(Long idSession, Long idOriginal, int rang) {
        Long idSequence = new SequenceDao(source)
                .insert(new SequenceDEcoute(
                        null, "seq" + rang + ".wav", idOriginal, rang, 0.0, 5.0, "/ws/seq.wav", false, idSession))
                .id();
        return new ObservationDao(source)
                .insert(new Observation(
                        null,
                        idSequence,
                        0.1,
                        0.4,
                        45,
                        "Pipkuh",
                        0.9,
                        null,
                        null,
                        null,
                        null,
                        false,
                        ModeValidation.NON_VALIDE,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null))
                .id();
    }
}
