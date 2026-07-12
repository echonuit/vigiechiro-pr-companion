package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
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

/// Invocation de bout en bout de la commande `audit-coherence` : code de sortie (0 sain, 1 si au moins
/// une erreur), rendu texte et JSON. Le workspace est surchargé vers un `@TempDir` (propriété
/// `vigiechiro.workspace`) et le graphe est semé via les DAO de l'injecteur applicatif (même
/// [fr.univ_amu.iut.commun.persistence.SourceDeDonnees] que la CLI).
class CliAuditTest {

    private static final String SERIE = "1925492";

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

    private String texteSortie() {
        return tamponSortie.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("audit-coherence sur une base vide : succès (0) et message « aucun écart »")
    void audit_base_vide_succes() {
        int code = cli.executer(new String[] {"audit-coherence"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("aucun écart");
    }

    @Test
    @DisplayName("audit-coherence --json sur une base vide : tableau JSON vide, succès (0)")
    void audit_base_vide_json() {
        int code = cli.executer(new String[] {"audit-coherence", "--json"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie().strip()).isEqualTo("[]");
    }

    @Test
    @DisplayName("Fichier attendu absent : code d'échec (1), constat DISQUE_MANQUANT en texte")
    void audit_erreur_code_1_texte() {
        semerOriginalManquant();

        int code = cli.executer(new String[] {"audit-coherence"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteSortie()).contains("DISQUE_MANQUANT");
    }

    @Test
    @DisplayName("Fichier attendu absent : --json contient la gravité ERREUR")
    void audit_erreur_json() {
        semerOriginalManquant();

        int code = cli.executer(new String[] {"audit-coherence", "--json"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteSortie()).contains("ERREUR").contains("DISQUE_MANQUANT");
    }

    @Test
    @DisplayName("audit-coherence --online sur une base vide : succès (0), aucun appel réseau (aucun passage)")
    void audit_online_base_vide() {
        int code = cli.executer(new String[] {"audit-coherence", "--online"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("aucun écart");
    }

    /// Sème un passage dont l'original pointe vers un fichier sous le workspace mais **absent** : le seul
    /// écart attendu est une erreur `DISQUE_MANQUANT`.
    private void semerOriginalManquant() {
        injecteur.getInstance(UtilisateurDao.class).insert(new Utilisateur("u-1", "Testeur"));
        Site site = injecteur
                .getInstance(SiteDao.class)
                .insert(new Site(null, "040962", "Étang", Protocole.STANDARD, null, "2026-05-01", "u-1"));
        Long idPoint = injecteur
                .getInstance(PointDao.class)
                .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        injecteur.getInstance(EnregistreurDao.class).insert(new Enregistreur(SERIE, "V1.01", null));
        Long idPassage = injecteur
                .getInstance(PassageDao.class)
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-06-20",
                        "21:30:00",
                        "05:15:00",
                        null,
                        StatutWorkflow.VERIFIE,
                        Verdict.OK,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE))
                .id();
        Prefixe prefixe = new Prefixe("040962", 2026, 1, "A1");
        Path racineSession = workspace.resolve(prefixe.nomDossierSession());
        Long idSession = injecteur
                .getInstance(SessionDao.class)
                .insert(new SessionDEnregistrement(null, racineSession.toString(), 4096L, 4096L, idPassage))
                .id();
        String nomOriginal = prefixe.nommerOriginal("PaRecPR" + SERIE + "_20260620_213000.wav");
        String cheminManquant =
                racineSession.resolve("bruts").resolve(nomOriginal).toString();
        injecteur
                .getInstance(EnregistrementOriginalDao.class)
                .insert(new EnregistrementOriginal(null, nomOriginal, cheminManquant, 12.0, 384_000, null, idSession));
    }
}
