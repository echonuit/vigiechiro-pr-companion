package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import fr.univ_amu.iut.passage.model.dao.CampagneDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.PassageOpportunisteDao;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Invocation de bout en bout de `solde-saison` (#2356) sur l'injecteur applicatif complet. La commande
/// est *user-scoped* : on **fige d'abord** `idUtilisateurCourant` (le provider crée l'utilisateur local),
/// puis on sème sous cet identifiant, pour que la commande retrouve bien la topologie.
///
/// Topologie de la saison 2026 (reste à faire **indépendant de la date du jour**, pour un test stable) :
///
/// ```
///   640001 / A1 : P1 Déposé/OK          P2 Déposé/OK          → rien
///   640001 / A2 : P1 Déposé/OK          P2 Prêt à déposer/OK  → Téléverser la nuit du 21/08
///   640002 / B1 : P1 Vérifié/À jeter    P2 absent             → Refaire le 1er passage
/// ```
class CliSoldeSaisonTest {

    @TempDir
    Path workspace;

    private Cli cli;
    private Injector injecteur;
    private SourceDeDonnees source;
    private String idUser;
    private ByteArrayOutputStream tamponSortie;
    private ByteArrayOutputStream tamponErreur;
    private PrintStream sortie;
    private PrintStream erreur;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
        source = injecteur.getInstance(SourceDeDonnees.class);
        idUser = injecteur.getInstance(Key.get(String.class, Names.named("idUtilisateurCourant")));

        semer(source, idUser, "640001", "A1", 1, "2026-06-20", StatutWorkflow.DEPOSE, Verdict.OK);
        semer(source, idUser, "640001", "A1", 2, "2026-08-20", StatutWorkflow.DEPOSE, Verdict.OK);
        semer(source, idUser, "640001", "A2", 1, "2026-06-21", StatutWorkflow.DEPOSE, Verdict.OK);
        semer(source, idUser, "640001", "A2", 2, "2026-08-21", StatutWorkflow.PRET_A_DEPOSER, Verdict.OK);
        semer(source, idUser, "640002", "B1", 1, "2026-06-23", StatutWorkflow.VERIFIE, Verdict.A_JETER);

        tamponSortie = new ByteArrayOutputStream();
        tamponErreur = new ByteArrayOutputStream();
        sortie = new PrintStream(tamponSortie, true, StandardCharsets.UTF_8);
        erreur = new PrintStream(tamponErreur, true, StandardCharsets.UTF_8);
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private static long semer(
            SourceDeDonnees source,
            String idUser,
            String carre,
            String point,
            int numero,
            String date,
            StatutWorkflow statut,
            Verdict verdict) {
        return JeuDeDonneesPassage.dans(source)
                .utilisateur(idUser)
                .carre(carre)
                .nomSite("Site " + carre)
                .point(point)
                .nuit(numero, 2026, date)
                .statut(statut)
                .verdict(verdict)
                .semer()
                .idPassage();
    }

    private String texteSortie() {
        return tamponSortie.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("--format csv : en-tête et une ligne par point, avec l'action restante")
    void format_csv() {
        int code = cli.executer(new String[] {"solde-saison", "--annee", "2026", "--format", "csv"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        String csv = texteSortie();
        assertThat(csv).contains("Reste à faire");
        assertThat(csv).contains("640001").contains("A2").contains("Téléverser la nuit du 21/08");
        assertThat(csv).contains("Refaire le 1er passage");
    }

    @Test
    @DisplayName("--format json : champs stables, action restante par point")
    void format_json() {
        int code = cli.executer(new String[] {"solde-saison", "--annee", "2026", "--format", "json"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        String json = texteSortie();
        assertThat(json).contains("\"point\"").contains("\"resteAFaire\"");
        assertThat(json).contains("Téléverser la nuit du 21/08").contains("Refaire le 1er passage");
    }

    @Test
    @DisplayName("format texte (défaut) : résumé décompté et action par point")
    void format_texte_par_defaut() {
        int code = cli.executer(new String[] {"solde-saison", "--annee", "2026"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        String texte = texteSortie();
        assertThat(texte).contains("Solde de la saison 2026");
        assertThat(texte).contains("3 point(s) suivi(s)").contains("4/6 passage(s) fait(s)");
        assertThat(texte).contains("Téléverser la nuit du 21/08").contains("Refaire le 1er passage");
    }

    @Test
    @DisplayName("--format inconnu : refus (2) et message sur la sortie d'erreur")
    void format_inconnu_code_2() {
        int code = cli.executer(new String[] {"solde-saison", "--annee", "2026", "--format", "xml"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(tamponErreur.toString(StandardCharsets.UTF_8)).contains("Format inconnu");
    }

    @Test
    @DisplayName("sans --annee : utilise la saison courante, succès (0)")
    void sans_annee_saison_courante() {
        int code = cli.executer(new String[] {"solde-saison", "--format", "csv"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
    }

    @Test
    @DisplayName("#2525 : une nuit opportuniste est signalée dans les trois formats")
    void opportuniste_signale_dans_les_trois_formats() {
        long id = semer(source, idUser, "640005", "E1", 1, "2026-06-25", StatutWorkflow.DEPOSE, Verdict.OK);
        injecteur.getInstance(PassageOpportunisteDao.class).marquer(id);

        // La nuit opportuniste ne figure PAS dans les colonnes de passage : elle a la sienne (#2525).
        assertThat(cli.executer(new String[] {"solde-saison", "--annee", "2026", "--format", "csv"}, sortie, erreur))
                .isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("Hors protocole").contains("640005").contains("opportuniste 25/06");

        tamponSortie.reset();
        cli.executer(new String[] {"solde-saison", "--annee", "2026", "--format", "texte"}, sortie, erreur);
        assertThat(texteSortie()).contains("[hors protocole : opportuniste 25/06]");

        tamponSortie.reset();
        cli.executer(new String[] {"solde-saison", "--annee", "2026", "--format", "json"}, sortie, erreur);
        assertThat(texteSortie()).contains("\"horsProtocole\"");
    }

    @Test
    @DisplayName("#2355 : --campagne ne garde que les points de la campagne demandée")
    void filtre_par_campagne() {
        long id = semer(source, idUser, "640005", "E1", 1, "2026-06-25", StatutWorkflow.DEPOSE, Verdict.OK);
        ServiceCampagne campagnes = new ServiceCampagne(
                new CampagneDao(source),
                injecteur.getInstance(PassageDao.class),
                new HorlogeFigee(LocalDate.of(2026, 7, 20)));
        campagnes.rattacherPassage(
                id, campagnes.creerCampagne("Suivi ENS", 2026, null).id());

        int code = cli.executer(
                new String[] {"solde-saison", "--annee", "2026", "--campagne", "ens", "--format", "csv"},
                sortie,
                erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie())
                .as("fragment insensible à la casse : seul le point rattaché ressort")
                .contains("640005")
                .doesNotContain("640001");
    }
}
