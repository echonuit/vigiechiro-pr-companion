package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Commune;
import fr.univ_amu.iut.commun.model.Completude;
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
    @DisplayName("#3092 : --gravite ne garde que les constats de ce niveau")
    void audit_filtre_par_gravite() {
        semerJournalManquant();

        int code = cli.executer(new String[] {"audit-coherence", "--gravite", "INFO"}, sortie, erreur);

        assertThat(texteSortie())
                .as("le constat DISQUE_MANQUANT est une ERREUR : --gravite INFO doit l'écarter")
                .doesNotContain("DISQUE_MANQUANT");
        assertThat(code)
                .as("le code de sortie juge le WORKSPACE, pas la sélection : filtrer l'affichage ne rend"
                        + " pas la base saine, exactement comme le verdict de l'écran reste calculé sur"
                        + " l'audit entier")
                .isEqualTo(Cli.CODE_ERREUR_EXECUTION);
    }

    @Test
    @DisplayName("#3092 : --categorie ne garde que cette nature de constat")
    void audit_filtre_par_categorie() {
        semerJournalManquant();

        int code = cli.executer(
                new String[] {"audit-coherence", "--categorie", "DISQUE_MANQUANT", "--json"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteSortie()).contains("DISQUE_MANQUANT");
    }

    @Test
    @DisplayName("#3092 : un filtre qui ne retient rien le dit, plutôt que de paraître sain")
    void audit_filtre_vide_le_dit() {
        // Sans cette phrase, « audit-coherence --gravite SUCCES » sur une base abîmée afficherait le
        // message « aucun écart détecté » : le filtre ferait passer un workspace cassé pour sain.
        semerJournalManquant();

        cli.executer(new String[] {"audit-coherence", "--gravite", "SUCCES"}, sortie, erreur);

        assertThat(texteSortie())
                .doesNotContain("aucun écart détecté")
                .contains("Aucun constat ne correspond aux filtres");
    }

    @Test
    @DisplayName("#3258 : un filtre qui masque une PARTIE le dit, et dit ce que juge le code de sortie")
    void audit_filtre_partiel_le_dit() {
        // Le cas que #3092 n'avait pas couvert : le filtre ne retient pas RIEN, il retient MOINS. La
        // ligne de résumé comptait alors l'audit entier sous une liste réduite - on lisait une ligne
        // puis « 2 constat(s) : 1 erreur(s) », et on cherchait la seconde. Pire, le code de sortie 1
        // n'était rattaché à rien de visible.
        semerJournalManquant();
        semerPointDeDepartementDivergent();

        int code = cli.executer(new String[] {"audit-coherence", "--gravite", "INFO"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texteSortie())
                .as("afficher zéro erreur en rendant 1 est correct, mais SEULEMENT si la sortie l'explique")
                .contains("constat(s) affiché(s) sur")
                .contains("L'audit entier compte")
                .contains("c'est lui que juge le code de sortie");
    }

    @Test
    @DisplayName("#3258 : sans filtre, la ligne de résumé ne change pas d'un mot")
    void audit_sans_filtre_resume_inchange() {
        semerJournalManquant();

        cli.executer(new String[] {"audit-coherence"}, sortie, erreur);

        assertThat(texteSortie())
                .as("la divulgation ne concerne que le cas filtré : l'appel nu garde sa formulation")
                .contains("constat(s) : ")
                .doesNotContain("affiché(s) sur");
    }

    @Test
    @DisplayName("#3258 : --contient cherche la cible ET le détail, casse et accents ignorés")
    void audit_filtre_par_recherche() {
        semerJournalManquant();
        semerPointDeDepartementDivergent();

        // « aix » sans majuscule ni tiret doit trouver « Aix-en-Provence », qui n'est que dans le détail.
        cli.executer(new String[] {"audit-coherence", "--contient", "aix"}, sortie, erreur);

        assertThat(texteSortie()).contains("DEPARTEMENT_DIVERGENT").doesNotContain("DISQUE_MANQUANT");
    }

    @Test
    @DisplayName("#3258 : --contient ne répond pas sur les colonnes qui ont leur propre option")
    void audit_recherche_ignore_les_colonnes_a_option() {
        semerJournalManquant();

        // « erreur » est la GRAVITÉ du constat, pas un mot de sa cible ni de son détail. Le faire
        // répondre ici doublerait --gravite, en moins précis.
        cli.executer(new String[] {"audit-coherence", "--contient", "erreur"}, sortie, erreur);

        assertThat(texteSortie()).contains("Aucun constat ne correspond aux filtres");
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

    /// L'utilisateur porteur des sites, posé **une seule fois** (#3258).
    ///
    /// Les deux fabriques le créaient chacune de leur côté, ce qui tenait tant qu'aucun test ne les
    /// appelait toutes les deux : la clé unique de `user` refuse le second INSERT. Or les cas qui
    /// éprouvent un filtre PARTIEL ont besoin des deux - une vraie erreur ET un constat informatif dans
    /// la même base, sans quoi il n'y a rien à masquer.
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
                .insert(new JournalDuCapteur(null, cheminManquant, null, null, Completude.INCONNUE, idSession));
    }
}
