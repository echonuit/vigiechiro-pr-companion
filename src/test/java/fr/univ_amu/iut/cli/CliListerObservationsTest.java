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

/// `lister-observations` (#1311) : la **surface de découverte** de la revue.
///
/// Ce qu'elle protège avant tout : **les identifiants sortent**. Sans eux, `discussion --observation <id>`
/// (livrée en #1418) et tous les gestes de revue à venir sont aveugles - il fallait ouvrir la base SQLite
/// à la main pour savoir quoi leur passer.
///
/// Et le contrat qui rend les gestes par filtre sûrs : ce que cette commande **montre** est exactement ce
/// qu'un geste avec les **mêmes filtres** toucherait, parce que c'est le même code qui choisit.
class CliListerObservationsTest {

    @TempDir
    Path workspaceDir;

    private Injector injecteur;
    private Cli cli;
    private ByteArrayOutputStream tamponSortie;
    private PrintStream sortie;
    private PrintStream erreur;

    private long idNonTouchee;
    private long idValidee;
    private long idCorrigeeDouteuse;
    private long idPassage;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspaceDir.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
        tamponSortie = new ByteArrayOutputStream();
        sortie = new PrintStream(tamponSortie, true, StandardCharsets.UTF_8);
        erreur = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        semer();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private String sortieTexte() {
        return tamponSortie.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Sans filtre : les trois observations sortent, AVEC leur identifiant — c'est tout l'objet"
            + " de la commande")
    void liste_avec_les_identifiants() {
        int code = cli.executer(
                new String[] {"lister-observations", "--passage", String.valueOf(idPassage)}, sortie, erreur);

        assertThat(code).isZero();
        assertThat(sortieTexte())
                .contains(String.valueOf(idNonTouchee))
                .contains(String.valueOf(idValidee))
                .contains(String.valueOf(idCorrigeeDouteuse))
                .contains("3 observation(s)");
    }

    @Test
    @DisplayName("--statut NON_TOUCHEE ne garde que les non revues")
    void filtre_par_statut() {
        cli.executer(
                new String[] {"lister-observations", "--passage", String.valueOf(idPassage), "--statut", "NON_TOUCHEE"},
                sortie,
                erreur);

        assertThat(sortieTexte()).contains("1 observation(s)").contains(String.valueOf(idNonTouchee));
        assertThat(sortieTexte()).doesNotContain("Nyclei");
    }

    @Test
    @DisplayName("--douteux ne garde que les douteuses ; SANS l'option, elles sont toutes là (le drapeau"
            + " absent ne veut pas dire « non douteuse »)")
    void filtre_douteux_est_ternaire() {
        cli.executer(
                new String[] {"lister-observations", "--passage", String.valueOf(idPassage), "--douteux"},
                sortie,
                erreur);

        assertThat(sortieTexte())
                .contains("1 observation(s)")
                .contains(String.valueOf(idCorrigeeDouteuse))
                .contains("douteux");

        tamponSortie.reset();
        cli.executer(new String[] {"lister-observations", "--passage", String.valueOf(idPassage)}, sortie, erreur);
        assertThat(sortieTexte())
                .as("sans --douteux, on veut LES DEUX, pas « seulement les non-douteuses »")
                .contains("3 observation(s)");
    }

    @Test
    @DisplayName("--json émet les identifiants et les trois avis, exploitables en script")
    void sortie_json() {
        int code = cli.executer(
                new String[] {"lister-observations", "--passage", String.valueOf(idPassage), "--json"}, sortie, erreur);

        assertThat(code).isZero();
        assertThat(sortieTexte())
                .contains("\"id\":")
                .contains("\"taxonTadarida\":")
                .contains("\"taxonValidateur\":")
                .contains("\"certitude\":")
                .contains("\"messages\":");
    }

    @Test
    @DisplayName("Filtre qui ne retient rien : la commande le DIT, sans faire croire au vide du passage")
    void aucun_resultat_se_dit() {
        int code = cli.executer(
                new String[] {"lister-observations", "--passage", String.valueOf(idPassage), "--taxon", "Rhihip"},
                sortie,
                erreur);

        assertThat(code).isZero();
        assertThat(sortieTexte()).contains("Aucune observation ne correspond");
    }

    /// Un passage et trois observations qui couvrent les trois statuts : une non revue, une validée (le
    /// taxon de l'observateur est celui de Tadarida), une corrigée **et** douteuse.
    private void semer() {
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "130711", "Test", Protocole.STANDARD, null, "2026-01-01", "u-1"));
        Long idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "Z41", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur("1925492", null, null));
        Passage passage = new PassageDao(source)
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
                        "1925492"));
        idPassage = passage.id();
        Long idSession = new SessionDao(source)
                .insert(new SessionDEnregistrement(null, "/ws/session", null, null, idPassage))
                .id();
        Long idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(null, "brut.wav", "/ws/brut.wav", 5.0, 384000, null, idSession))
                .id();

        idNonTouchee = observation(source, idSession, idOriginal, 0, "Pipkuh", null, ModeValidation.NON_VALIDE, false);
        idValidee = observation(source, idSession, idOriginal, 1, "Nyclei", "Nyclei", ModeValidation.MANUEL, false);
        idCorrigeeDouteuse =
                observation(source, idSession, idOriginal, 2, "Pipkuh", "Pippip", ModeValidation.MANUEL, true);
    }

    private long observation(
            SourceDeDonnees source,
            Long idSession,
            Long idOriginal,
            int rang,
            String taxonTadarida,
            String taxonObservateur,
            ModeValidation mode,
            boolean douteux) {
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
                        taxonTadarida,
                        0.9,
                        null,
                        taxonObservateur,
                        taxonObservateur != null ? 1.0 : null,
                        null,
                        false,
                        mode,
                        null,
                        douteux,
                        null,
                        null,
                        taxonObservateur != null ? Certitude.PROBABLE : null,
                        null,
                        null))
                .id();
    }
}
