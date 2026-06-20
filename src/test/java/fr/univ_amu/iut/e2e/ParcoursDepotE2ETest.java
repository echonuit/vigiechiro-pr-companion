package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

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
@Tag("conformite")
@ExtendWith(ApplicationExtension.class)
class ParcoursDepotE2ETest {

    private static final String ID_USER = "u-e2e";
    private static final String SERIE = "1925492";
    private static final int FREQUENCE_WAV = 2000; // Hz, multiple de 10 (R10)
    private static final int TRAMES = 3000;
    private static final String LOG =
            "22/04/26 - 16:02:20 PR1925492 Demarrage Passive Recorder numero de serie 1925492, V1.01,"
                    + " CPU 600000000, T4.1\n"
                    + "22/04/26 - 16:02:21 PR1925492 Sonde temperature/hygrometrie presente, lecture toutes"
                    + " les 600s\n";

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
        PointDEcoute point = sites.ajouterPoint(site.id(), "A1", 43.5298, 5.4474, "Près du chêne");
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
    void parcours_verifier_puis_deposer(FxRobot robot) {
        PassageDao passages = new PassageDao(source);
        assertThat(statut(passages)).isEqualTo(StatutWorkflow.TRANSFORME);

        // 1) Entrer sur M-Passage (navigation socle, comme un double-clic depuis M-Sites).
        robot.interact(() -> injector.getInstance(OuvrirPassage.class).ouvrir(idPassage, contexte));
        Button verifier = robot.lookup("#boutonVerifier").queryAs(Button.class);
        assertThat(verifier.isDisabled()).isFalse();

        // 2) Vérifier → M-Qualification, poser le verdict OK puis enregistrer.
        robot.interact(verifier::fire);
        robot.interact(robot.lookup("#boutonOk").queryAs(Button.class)::fire);
        robot.interact(robot.lookup("#boutonEnregistrer").queryAs(Button.class)::fire);
        assertThat(statut(passages)).isEqualTo(StatutWorkflow.VERIFIE);

        // 3) Revenir sur M-Passage : le dépôt est désormais disponible.
        robot.interact(() -> injector.getInstance(OuvrirPassage.class).ouvrir(idPassage, contexte));
        Button depot = robot.lookup("#boutonDepot").queryAs(Button.class);
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
        Files.writeString(sd.resolve("LogPR" + SERIE + ".txt"), LOG, StandardCharsets.UTF_8);
        Files.writeString(sd.resolve("PaRecPR" + SERIE + "_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
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
        ByteBuffer buf = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + pcm.length);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putInt(FREQUENCE_WAV);
        buf.putInt(FREQUENCE_WAV * 2);
        buf.putShort((short) 2);
        buf.putShort((short) 16);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(pcm.length);
        buf.put(pcm);
        Files.write(fichier, buf.array());
    }
}
