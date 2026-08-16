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
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.fixture.JournalDeCapteur;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.multisite.view.NavigationMultisite;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// **Test E2E de parcours (P5)** : la **vue agrégée M-Multisite** et son **drill-down** vers
/// **M-Passage**, sur le vrai chrome. On ouvre l'écran multi-sites (navigation réelle), on vérifie
/// que le passage importé y figure, puis un **double-clic** sur sa ligne ouvre M-Passage via le
/// contrat socle `OuvrirPassage` (`multisite → passage`).
@ExtendWith(ApplicationExtension.class)
class ParcoursMultisiteVersPassageE2ETest {

    private static final String ID_USER = "u-e2e-ms";
    private static final String SERIE = "1925492";
    private static final int FREQUENCE_WAV = 384_000;
    private static final int TRAMES = 576_000;
    private static final String DATE_NUIT = "2026-04-22"; // date du journal → cellule unique du tableau
    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-ms");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur E2E"));
        ServiceSites sites = injector.getInstance(ServiceSites.class);
        Site site = sites.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        PointDEcoute point = sites.ajouterPoint(site.id(), "A1", 43.4010, -1.5740, null);
        Path sd = creerNuitSynthetique(workspace.resolve("sd"));
        injector.getInstance(ServiceImport.class).importer(sd, point.id(), new Prefixe("640380", 2026, 1, "A1"));

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1280, 860));
        stage.show();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("M-Multisite : le passage figure dans la vue agrégée, le double-clic ouvre M-Passage")
    void multisite_drill_vers_passage(FxRobot robot) throws TimeoutException {
        NavigationViewModel navigation = injector.getInstance(NavigationViewModel.class);

        // 1) Ouvrir la vue agrégée multi-sites (navigation socle réelle). Le chargement de l'agrégat
        // tourne hors du fil JavaFX (occupation.occuper, MultisiteController) : sans cette attente, la
        // table est encore vide quand l'assertion tombe, un échec qui ne se produit que sur une machine
        // lente, donc en CI.
        robot.interact(() -> injector.getInstance(NavigationMultisite.class).ouvrirAccueil());
        TableView<?> table = robot.lookup("#tableLignes").queryAs(TableView.class);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !table.getItems().isEmpty());
        assertThat(table.getItems()).hasSize(1);
        assertThat(navigation.getVueCourante()).isEqualTo("multisite");

        // 2) Double-clic sur la ligne (cellule date, unique) → drill-down vers M-Passage.
        doubleClicVersPassage(robot, navigation);

        assertThat(navigation.getVueCourante()).isEqualTo("passage");
        Button verifier = robot.lookup("#boutonVerifier").queryAs(Button.class);
        assertThat(verifier).isNotNull();
        // navigation.getVueCourante() bascule dès le changement d'écran, avant que le chargement du
        // passage (lui aussi asynchrone) n'ait appliqué ses données ; le fil d'Ariane ci-dessous n'est
        // renseigné qu'à la fin de ce chargement (navigation.actualiserFil, même callback que
        // verificationDisponibleProperty). Attendre l'un garantit l'autre.
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !verifier.isDisabled());

        // 3) Le fil d'Ariane GLOBAL situe le passage sous son site, même atteint via multisite (#140) :
        // Accueil › Mes sites › Carré 640380 › Détails du passage N° 1 (emplacement, pas l'historique).
        HBox fil = robot.lookup("#filAriane").queryAs(HBox.class);
        var libelles = fil.getChildren().stream()
                .filter(n -> n.getStyleClass().contains("fil-ariane-segment")
                        || n.getStyleClass().contains("fil-ariane-courant"))
                .map(n -> ((Labeled) n).getText())
                .toList();
        assertThat(libelles)
                .contains("Carré 640380")
                .anySatisfy(t -> assertThat(t).startsWith("Détails du passage"));
    }

    @Test
    @DisplayName("Multisite → Passage → enfant : cliquer « Détails du passage » dans le fil rouvre M-Passage")
    void multisite_enfant_clic_fil_rouvre_le_passage(FxRobot robot) throws TimeoutException {
        NavigationViewModel navigation = injector.getInstance(NavigationViewModel.class);

        // 1) Multisite → double-clic → M-Passage (atteint sans passer par le site). Mêmes attentes que
        // multisite_drill_vers_passage : chargement de l'agrégat puis chargement du passage, tous deux
        // asynchrones.
        robot.interact(() -> injector.getInstance(NavigationMultisite.class).ouvrirAccueil());
        WaitForAsyncUtils.waitFor(
                5,
                TimeUnit.SECONDS,
                () -> !robot.lookup("#tableLignes")
                        .queryAs(TableView.class)
                        .getItems()
                        .isEmpty());
        doubleClicVersPassage(robot, navigation);
        assertThat(navigation.getVueCourante()).isEqualTo("passage");
        Button verifier = robot.lookup("#boutonVerifier").queryAs(Button.class);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !verifier.isDisabled());

        // 2) M-Passage → écran enfant (carte « Diagnostic matériel »).
        robot.interact(robot.lookup("#boutonDiagnostic").queryButton()::fire);
        assertThat(navigation.getVueCourante()).isEqualTo("diagnostic");

        // 3) Sur l'enfant, le fil situe le passage sous son site (#140) ; « Détails du passage N° 1 »
        // est un segment ANCÊTRE cliquable (Hyperlink), pas le segment courant.
        Hyperlink segmentPassage = filSegment(robot, "Détails du passage N° 1");

        // 4) Cliquer ce segment exerce le Runnable du Lieu (OuvrirPassage) et rouvre réellement
        // M-Passage : y compris depuis un enfant atteint via la vue multi-sites.
        robot.interact(segmentPassage::fire);
        assertThat(navigation.getVueCourante()).isEqualTo("passage");
        assertThat(robot.lookup("#boutonVerifier").tryQuery()).isPresent();
    }

    /// Segment ancêtre (cliquable) du fil d'Ariane du chrome portant le libellé donné.
    private static Hyperlink filSegment(FxRobot robot, String libelle) {
        HBox fil = robot.lookup("#filAriane").queryAs(HBox.class);
        return fil.getChildren().stream()
                .filter(Hyperlink.class::isInstance)
                .map(Hyperlink.class::cast)
                .filter(segment -> libelle.equals(segment.getText()))
                .findFirst()
                .orElseThrow();
    }

    /// Double-clic « robuste » de drill-down vers M-Passage. Sous charge (suite complète, forks
    /// parallèles), TestFX peut ne pas enregistrer le double-clic ou naviguer avec un léger différé,
    /// laissant l'écran sur la vue intermédiaire quand l'assertion tombe. On attend donc que la navigation
    /// aboutisse réellement, avec quelques réessais ; l'assertion de l'appelant tranche clairement sinon.
    ///
    /// Entre le moment où `table.getItems()` n'est plus vide et celui où TestFX peut localiser la cellule
    /// correspondante, il reste une passe de layout JavaFX à consommer : sans l'attendre, `doubleClickOn`
    /// peut lever `FxRobotException` (« returned no nodes ») avant même le premier essai. On attend donc
    /// que la cellule soit effectivement interrogeable avant de cliquer, à chaque essai.
    ///
    /// ⚠️ **Épuiser les essais lève, au lieu de rendre la main.** Le helper abandonnait en **silence**
    /// après trois tentatives : l'appelant asserta alors `vueCourante`, et l'échec se présentait comme
    /// « attendu passage, obtenu multisite » - un bug de navigation apparent, là où le robot n'avait
    /// simplement pas abouti. Deux échecs de cette classe ont été lus ainsi (#3823).
    ///
    /// C'est le motif de l'ADR 2213 : un dispositif qui ne peut pas conclure **rapporte ce qu'il a
    /// vu**, il ne rend pas un verdict qui ressemble à autre chose.
    private static void doubleClicVersPassage(FxRobot robot, NavigationViewModel navigation) {
        for (int essai = 1; essai <= 3; essai++) {
            try {
                WaitForAsyncUtils.waitFor(
                        3,
                        TimeUnit.SECONDS,
                        () -> robot.lookup(DATE_NUIT).tryQuery().isPresent());
                robot.doubleClickOn(DATE_NUIT);
                WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> "passage".equals(navigation.getVueCourante()));
                return;
            } catch (TimeoutException reessai) {
                // Cellule pas encore rendue ou navigation pas encore aboutie : on retente.
            }
        }
        throw new AssertionError("Le double-clic vers le passage n'a pas abouti après 3 essais de 3 s : "
                + "la cellule « " + DATE_NUIT + " » n'a jamais été interrogeable, ou la navigation n'a "
                + "jamais atteint « passage » (vue courante : " + navigation.getVueCourante() + "). "
                + "Ce n'est pas un défaut de navigation - c'est le robot qui n'a pas abouti sous charge.");
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
