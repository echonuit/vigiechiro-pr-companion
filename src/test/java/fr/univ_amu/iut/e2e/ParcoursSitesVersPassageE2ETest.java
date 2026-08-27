package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.fixture.JournalDeCapteur;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// **Test E2E de parcours (P-Sites, entrée réelle)** : le chemin d'entrée n°1 d'un utilisateur,
/// piloté **uniquement par des clics réels** sur le vrai chrome, sans raccourci de navigation :
///
/// `Tableau de bord` → carte **« Mes sites »** → `M-Sites` → carte **« Carré 640380 »**
/// → `M-Site-detail` → **double-clic** sur la ligne de passage → `M-Passage`.
///
/// Contrairement au fil rouge `ParcoursDepotE2ETest` (qui entre dans M-Passage via le contrat socle
/// `OuvrirPassage` pour aller droit au workflow), ce parcours **traverse réellement** les écrans
/// d'accueil et de détail des sites : il vérifie que les cartes cliquables et le double-clic de
/// ligne câblent bien la navigation `accueil → sites → site-detail → passage`.
///
/// L'utilisateur seedé est l'unique en base, donc `idUtilisateurCourant` (singleton, premier
/// utilisateur) le désigne : le site qu'on lui rattache apparaît bien sur M-Sites.
@ExtendWith(ApplicationExtension.class)
class ParcoursSitesVersPassageE2ETest {

    private static final String ID_USER = "u-e2e-sites";
    private static final String SERIE = "1925492";
    private static final int FREQUENCE_WAV = 384_000;
    private static final int TRAMES = 576_000;
    private static final String CARRE = "640380";
    /// La nuit **telle que la colonne la rend** depuis #4019 : la date se lit en français, et
    /// c'est ce texte-là que vise le double-clic. La forme ISO ne désigne plus aucun nœud.
    private static final String DATE_NUIT = "22/04/2026"; // cellule date du tableau (unique à l'écran)
    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-sites");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        // Un seul utilisateur en base → idUtilisateurCourant (singleton) le désigne ; le site qu'on
        // crée pour lui sera donc bien listé sur M-Sites.
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur E2E"));
        ServiceSites sites = injector.getInstance(ServiceSites.class);
        Site site = sites.creerSite(CARRE, "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        PointDEcoute point = sites.ajouterPoint(site.id(), "A1", 43.4010, -1.5740, null);
        Path sd = creerNuitSynthetique(workspace.resolve("sd"));
        injector.getInstance(ServiceImport.class).importer(sd, point.id(), new Prefixe(CARRE, 2026, 1, "A1"));

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        FenetreAjustable.poser(stage, racine, 1280, 860);
        FenetreAjustable.afficher(stage);
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Entrée réelle : tableau de bord → Mes sites → détail → double-clic → M-Passage")
    void accueil_vers_passage_par_clics(FxRobot robot) throws TimeoutException {
        NavigationViewModel navigation = injector.getInstance(NavigationViewModel.class);
        assertThat(navigation.getVueCourante()).isEqualTo("accueil");

        // 1) Carte « Mes sites » du tableau de bord → écran M-Sites.
        // Attendre que la carte soit VISIBLE, pas seulement présente. Les cartes d'accueil sont
        // peuplées section par section : celles de la seconde section sont posées en dernier, et un
        // clic immédiat les rate sous charge - « returned 1 nodes, but no nodes were visible ». Sept
        // occurrences en deux jours, toujours sur une carte tardive, jamais sur la première (#3823).
        // C'est le motif « interact puis assertion immédiate » que #3717 avait audité.
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> robot.lookup("Mes sites").queryAll().stream().anyMatch(Node::isVisible));
        robot.clickOn("Mes sites");
        assertThat(navigation.getVueCourante()).isEqualTo("sites");

        // 2) Carte du site (« Carré 640380 ») → écran M-Site-detail. Depuis le déport #1212, les
        // cartes se chargent hors du fil JavaFX (vrai exécuteur asynchrone ici) : attendre leur rendu.
        WaitForAsyncUtils.waitFor(
                5,
                TimeUnit.SECONDS,
                () -> robot.lookup("Carré " + CARRE).tryQuery().isPresent());
        robot.clickOn("Carré " + CARRE);
        assertThat(navigation.getVueCourante()).isEqualTo("site-detail");
        TableView<?> passages = robot.lookup("#tablePassages").queryAs(TableView.class);
        assertThat(passages.getItems()).hasSize(1);

        // 3) Double-clic sur la ligne de passage (cellule date) → drill-down vers M-Passage.
        doubleClicVersPassage(robot, navigation);
        assertThat(navigation.getVueCourante()).isEqualTo("passage");
        assertThat(robot.lookup("#boutonVerifier").queryAs(Button.class)).isNotNull();
    }

    /// Double-clic « robuste » de drill-down vers M-Passage. Sous charge (suite complète, forks
    /// parallèles), TestFX peut ne pas enregistrer le double-clic ou naviguer avec un léger différé,
    /// laissant l'écran sur la vue intermédiaire (« site-detail ») quand l'assertion tombe. On attend donc
    /// que la navigation aboutisse réellement, avec quelques réessais ; l'assertion de l'appelant tranche
    /// clairement si, malgré tout, on n'y est pas.
    private static void doubleClicVersPassage(FxRobot robot, NavigationViewModel navigation) {
        // Le vrai geste, et non `DoubleClicDeterministe` (#4554) : un parcours E2E éprouve le
        // chemin que l'utilisateur emprunte, robot et placement compris. Contourner le clic
        // rendrait ce test stable en lui retirant ce qu'il sert à attraper.
        for (int essai = 1; essai <= 3; essai++) {
            robot.doubleClickOn(DATE_NUIT);
            try {
                WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> "passage".equals(navigation.getVueCourante()));
                return;
            } catch (TimeoutException reessai) {
                // Navigation pas encore aboutie : on retente (le double-clic n'a peut-être pas « pris »).
            }
        }
    }

    private static Path creerNuitSynthetique(Path sd) throws Exception {
        Files.createDirectories(sd);
        // Journal et releve par la fixture (#2868) : le trace complet d'une nuit, la ou ce test
        // n'avait besoin que d'un journal valide. Il n'affirme rien sur son contenu.
        JournalDeCapteur.ecrire(sd, SERIE, LocalDate.of(2026, 4, 22));
        JournalDeCapteur.ecrireReleve(sd, SERIE);
        byte[] pcm = new byte[TRAMES * 2];
        for (int i = 0; i < TRAMES; i++) {
            short e = (short) (((i * 41) % 1000) - 500);
            pcm[2 * i] = (byte) (e & 0xFF);
            pcm[2 * i + 1] = (byte) ((e >> 8) & 0xFF);
        }
        // Writer de production (#2864) : memes octets, et c'est le format que l'application
        // saura relire.
        FichierWav.ecrire(
                sd.resolve("PaRecPR" + SERIE + "_20260422_203922.wav"), 1, FREQUENCE_WAV, 16, pcm, 0, pcm.length);
        return sd;
    }
}
