package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.fixture.JournalDeCapteur;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
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

/// **Test E2E de parcours (fil rouge P1 → P4)** : sur le **vrai chrome** de l'application (injecteur
/// applicatif `RacineInjecteur`), on enchaîne plusieurs écrans via la **navigation réelle** et on
/// vérifie les **transitions de workflow** de bout en bout, jusqu'au dépôt :
///
/// `M-Passage (Transformé)` → **Vérifier** → `M-Qualification` (verdict OK) → `Transformé→Vérifié`
/// → **Préparer le dépôt** → `M-Lot` (préparer puis déposer) → `Vérifié → Prêt → Déposé`.
///
/// La nuit est **importée via le vrai [ServiceImport]** en préparation (l'écran M-Import passe par un
/// `DirectoryChooser` natif que TestFX ne peut pas piloter ; son IHM est couverte par
/// `ImportationViewTest`, sa mécanique par `ServiceImportTest`). Tout le reste est piloté **à l'écran**
/// (boutons réels) et asserté **en base** (le statut du passage).
@ExtendWith(ApplicationExtension.class)
class ParcoursDepotE2ETest {

    private static final String ID_USER = "u-e2e";
    private static final String SERIE = "1925492";
    private static final int FREQUENCE_WAV = 384_000; // Hz, multiple de 10 (R10)
    private static final int TRAMES = 576_000;
    private Injector injector;
    private SourceDeDonnees source;
    private long idPassage;
    private ContexteSite contexte;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        // Préconditions (P1 + P2) : utilisateur, site + point, nuit importée → passage Transformé.
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur E2E"));
        ServiceSites sites = injector.getInstance(ServiceSites.class);
        Site site = sites.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        PointDEcoute point = sites.ajouterPoint(site.id(), "A1", 43.4010, -1.5740, "Près du chêne");
        Path sd = creerNuitSynthetique(workspace.resolve("sd"));
        idPassage = injector.getInstance(ServiceImport.class)
                .importer(sd, point.id(), new Prefixe("640380", 2026, 1, "A1"))
                .passage()
                .id();
        contexte = new ContexteSite("640380", "A1", null);

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
    @DisplayName("Fil rouge : un passage importé est vérifié puis déposé via les écrans (Transformé → Déposé)")
    void parcours_verifier_puis_deposer(FxRobot robot) throws TimeoutException {
        PassageDao passages = new PassageDao(source);
        assertThat(statut(passages)).isEqualTo(StatutWorkflow.TRANSFORME);

        // 1) Entrer sur M-Passage (navigation socle, comme un double-clic depuis M-Sites). Le chargement
        // du passage tourne hors du fil JavaFX (occupation.occuper) : sans cette attente, l'assertion
        // ci-dessous tombe pendant que verificationDisponibleProperty est encore à sa valeur par défaut
        // (désactivé), un échec qui ne se produit que sur une machine lente, donc en CI.
        robot.interact(() -> injector.getInstance(OuvrirPassage.class).ouvrir(idPassage, contexte));
        Button verifier = robot.lookup("#boutonVerifier").queryAs(Button.class);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !verifier.isDisabled());
        assertThat(verifier.isDisabled()).isFalse();

        // 2) Vérifier → M-Qualification, poser le verdict OK puis enregistrer. M-Qualification se charge
        // elle aussi hors du fil JavaFX (#1210) : sans cette attente, le clic sur « Enregistrer » part
        // pendant que le chargement est encore en vol, qui atterrit ensuite et écrase le verdict qu'on
        // venait de choisir (verdictVm.appliquer(...) réapplique l'état lu en base) - le passage reste
        // alors « Transformé ».
        robot.interact(verifier::fire);
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> !robot.lookup("#tableSequences")
                        .queryAs(TableView.class)
                        .getItems()
                        .isEmpty());
        robot.interact(robot.lookup("#boutonOk").queryAs(Button.class)::fire);
        robot.interact(robot.lookup("#boutonEnregistrer").queryAs(Button.class)::fire);
        assertThat(statut(passages)).isEqualTo(StatutWorkflow.VERIFIE);

        // 3) Revenir sur M-Passage : le dépôt est désormais disponible. Même attente qu'à l'étape 1 : le
        // rechargement du passage est asynchrone.
        robot.interact(() -> injector.getInstance(OuvrirPassage.class).ouvrir(idPassage, contexte));
        Button depot = robot.lookup("#boutonDepot").queryAs(Button.class);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !depot.isDisabled());
        assertThat(depot.isDisabled()).isFalse();

        // 4) Préparer le dépôt → M-Lot : préparer puis déposer.
        robot.interact(depot::fire);
        robot.interact(robot.lookup("#btnPreparer").queryAs(Button.class)::fire);
        robot.interact(robot.lookup("#btnDeposer").queryAs(Button.class)::fire);

        // 5) Bout-en-bout : le passage est déposé en base.
        assertThat(statut(passages)).isEqualTo(StatutWorkflow.DEPOSE);
    }

    private StatutWorkflow statut(PassageDao passages) {
        return passages.findById(idPassage).orElseThrow().statutWorkflow();
    }

    /// Crée un dossier SD minimal (journal LogPR + un WAV PCM valide à 2 kHz) que l'import peut traiter.
    private static Path creerNuitSynthetique(Path sd) throws Exception {
        Files.createDirectories(sd);
        JournalDeCapteur.ecrire(sd, SERIE, LocalDate.of(2026, 4, 22));
        JournalDeCapteur.ecrireReleve(sd, SERIE);
        ecrireWav(sd.resolve("PaRecPR" + SERIE + "_20260422_203922.wav"));
        return sd;
    }

    private static void ecrireWav(Path fichier) throws Exception {
        byte[] pcm = new byte[TRAMES * 2];
        for (int i = 0; i < TRAMES; i++) {
            short e = (short) (((i * 41) % 1000) - 500);
            pcm[2 * i] = (byte) (e & 0xFF);
            pcm[2 * i + 1] = (byte) ((e >> 8) & 0xFF);
        }
        // Writer de production (#2864) : mêmes octets que l'en-tête écrit ici à la main, et
        // c'est le format que l'application saura relire - un test qui compose le sien teste un
        // format que le produit n'utilise pas.
        FichierWav.ecrire(fichier, 1, FREQUENCE_WAV, 16, pcm, 0, pcm.length);
    }
}
