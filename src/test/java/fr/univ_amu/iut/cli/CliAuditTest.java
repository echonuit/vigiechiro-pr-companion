package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Commune;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.fixture.SortieCapturee;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.PrintStream;
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

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";

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

    private String texteSortie() {
        return capture.texte();
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
        semerJournalManquant();

        int code = cli.executer(new String[] {"audit-coherence"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteSortie()).contains("DISQUE_MANQUANT");
    }

    @Test
    @DisplayName("Fichier attendu absent : --json contient la gravité ERREUR")
    void audit_erreur_json() {
        semerJournalManquant();

        int code = cli.executer(new String[] {"audit-coherence", "--json"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteSortie()).contains("ERREUR").contains("DISQUE_MANQUANT");
    }

    @Test
    @DisplayName("#2848 : le département divergent sort de la CLI, et n'y fait PAS échouer la commande")
    void departement_divergent_sort_sans_faire_echouer() {
        semerPointDeDepartementDivergent();

        int code = cli.executer(new String[] {"audit-coherence"}, sortie, erreur);

        assertThat(code)
                .as("le chevauchement de département est un cas NORMAL en bord de carré : faire "
                        + "rendre 1 casserait tous les scripts qui appellent cette commande")
                .isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("DEPARTEMENT_DIVERGENT").contains("Départements 13 (commune) et 84 (carré)");
    }

    @Test
    @DisplayName("#2848 : --json porte le constat, sa gravité INFO et sa cible « carré / point »")
    void departement_divergent_json() {
        semerPointDeDepartementDivergent();

        int code = cli.executer(new String[] {"audit-coherence", "--json"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie())
                .as("l'écran et la commande lisent le MÊME rapport : ce qui s'affiche d'un côté doit "
                        + "se scripter de l'autre")
                .contains("DEPARTEMENT_DIVERGENT")
                .contains("INFO")
                .contains("840962 / A1");
    }

    @Test
    @DisplayName("#3258 : --categorie ne garde que cette nature de constat")
    void filtre_categorie() {
        semerJournalManquant();
        semerPointDeDepartementDivergent();

        cli.executer(new String[] {"audit-coherence", "--categorie", "DEPARTEMENT_DIVERGENT"}, sortie, erreur);

        assertThat(texteSortie()).contains("DEPARTEMENT_DIVERGENT").doesNotContain("DISQUE_MANQUANT");
    }

    @Test
    @DisplayName("#3258 : le CODE DE SORTIE décrit ce qui a été imprimé, pas la base entière")
    void le_code_de_sortie_suit_le_filtre() {
        // La base porte une vraie erreur (fichier absent) ET un constat informatif. Filtrer sur le
        // second n'imprime aucune erreur : rendre 1 mentirait à qui lit la sortie, et rendrait l'option
        // inutilisable en script - or c'est son seul usage.
        semerJournalManquant();
        semerPointDeDepartementDivergent();

        assertThat(cli.executer(new String[] {"audit-coherence"}, sortie, erreur))
                .as("sans filtre, l'erreur est bien là")
                .isEqualTo(Cli.CODE_ERREUR_EXECUTION);

        SortieCapturee filtree = new SortieCapturee();
        assertThat(cli.executer(
                        new String[] {"audit-coherence", "--gravite", "INFO"}, filtree.sortie(), filtree.erreur()))
                .as("filtré sur les infos, plus aucune erreur n'est imprimée : le code suit")
                .isEqualTo(Cli.CODE_SUCCES);
    }

    @Test
    @DisplayName("#3258 : --contient cherche la cible ET le détail, casse et accents ignorés")
    void filtre_recherche() {
        semerPointDeDepartementDivergent();

        // « aix » sans majuscule ni tiret doit trouver « Aix-en-Provence » du détail.
        cli.executer(new String[] {"audit-coherence", "--contient", "aix"}, sortie, erreur);

        assertThat(texteSortie()).contains("DEPARTEMENT_DIVERGENT");
    }

    @Test
    @DisplayName("#3258 : une valeur valide qui ne correspond à rien rend VIDE, elle ne refuse pas")
    void filtre_sans_correspondance() {
        semerPointDeDepartementDivergent();

        int code = cli.executer(new String[] {"audit-coherence", "--categorie", "DISQUE_ORPHELIN"}, sortie, erreur);

        assertThat(code)
                .as("un critère qui QUALIFIE rend vide sans refuser (ADR 3082) : l'ensemble vide est "
                        + "une réponse, pas une faute de frappe")
                .isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("aucun écart");
    }

    @Test
    @DisplayName("#3258 : une gravité hors liste est une erreur d'USAGE, refusée par picocli")
    void gravite_inconnue_refusee() {
        int code = cli.executer(new String[] {"audit-coherence", "--gravite", "CATASTROPHE"}, sortie, erreur);

        assertThat(code)
                .as("la valeur n'existe pas dans l'énumération : c'est la frappe qui est fautive, pas "
                        + "la base - et le refus arrive avant que l'audit ne tourne")
                .isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
    }

    /// L'utilisateur porteur des sites, posé **une seule fois** (#3258).
    ///
    /// Les deux fabriques le créaient chacune de leur côté, ce qui allait tant qu'aucun test ne les
    /// appelait toutes les deux : la clé unique de `user` refuse le second INSERT. Or le cas qui décide
    /// du code de sortie a besoin des DEUX - une vraie erreur ET un constat informatif dans la même base.
    private void utilisateur() {
        UtilisateurDao dao = injecteur.getInstance(UtilisateurDao.class);
        if (dao.findById(ID_USER).isEmpty()) {
            dao.insert(new Utilisateur(ID_USER, "Testeur"));
        }
    }

    /// Sème un point dont les **deux lectures** du département se contredisent : le carré `840962` le
    /// place dans le Vaucluse, sa commune dans les Bouches-du-Rhône. Aucun passage, aucun fichier : ce
    /// constat ne dépend que de la topologie.
    private void semerPointDeDepartementDivergent() {
        utilisateur();
        Site site = injecteur
                .getInstance(SiteDao.class)
                .insert(new Site(null, "840962", "Étang", Protocole.STANDARD, null, "2026-05-01", ID_USER));
        Long idPoint = injecteur
                .getInstance(PointDao.class)
                .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        injecteur.getInstance(PointCommuneDao.class).definir(idPoint, new Commune("Aix-en-Provence", "13001"));
    }

    /// Sème un passage dont le journal du capteur pointe vers un fichier sous le workspace mais
    /// **absent** : le seul écart attendu est une erreur `DISQUE_MANQUANT`. Les bruts ne conviennent
    /// plus : ce sont des copies optionnelles, dont l'absence est silencieuse (ADR 0048).
    private void semerJournalManquant() {
        utilisateur();
        Site site = injecteur
                .getInstance(SiteDao.class)
                .insert(new Site(null, "040962", "Étang", Protocole.STANDARD, null, "2026-05-01", ID_USER));
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
                        SERIE,
                        null))
                .id();
        Prefixe prefixe = new Prefixe("040962", 2026, 1, "A1");
        Path racineSession = workspace.resolve(prefixe.nomDossierSession());
        Long idSession = injecteur
                .getInstance(SessionDao.class)
                .insert(new SessionDEnregistrement(null, racineSession.toString(), 4096L, 4096L, idPassage))
                .id();
        String cheminManquant = racineSession.resolve("LogPR" + SERIE + ".txt").toString();
        injecteur
                .getInstance(JournalDuCapteurDao.class)
                .insert(new JournalDuCapteur(null, cheminManquant, null, null, idSession));
    }
}
