package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.viewmodel.EtatImport;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX qui **clique réellement** sur « Importer cette nuit »
/// et vérifie que l'import aboutit, puis affiche son retour **en place** (récap de
/// succès, ou message d'erreur en cas de refus).
///
/// Complète `ImportationViewTest` (qui ne pilote pas le bouton) sur le point le
/// plus sensible : le handler `#importer` doit lancer le travail lourd **hors du
/// fil JavaFX**, mais effectuer le marquage d'état (`marquerEnCours` /
/// `marquerTermine` / `marquerEchec`) **sur le fil JavaFX** (`Platform.runLater`),
/// car ces méthodes mutent des propriétés liées au graphe de scène (barre/zone de
/// progression, libellés). Lancer l'`ImportationViewModel#importer()` synchrone
/// directement sur un fil d'arrière-plan lèverait « Not on FX application thread »
/// (avalée par le thread daemon), et l'écran ne bougerait plus.
@ExtendWith(ApplicationExtension.class)
class ImportationClicImporterTest {

    private static final String ID_USER = "u-clic";
    private static final int FREQUENCE_WAV = 2000; // Hz, multiple de 10
    private static final int TRAMES = 3000;

    private static final String LOG =
            "22/04/26 - 16:02:20 PR1925492 Démarrage Passive Recorder numéro de série 1925492, V1.01,"
                    + " CPU 600000000, T4.1\n"
                    + "22/04/26 - 16:02:21 PR1925492 Sonde température/hygrométrie présente, lecture toutes"
                    + " les 600s\n"
                    + "22/04/26 - 16:02:21 PR1925492 Paramètres : Acquisi. 20:25-07:47, Fe384kHz FL N FPH"
                    + " 00, S. R. 16dB 1dt. GN0, Bd. Freq. 8-120kHz, Wav 2-30s SD 99%\n";

    private Injector injector;
    private ImportationViewModel viewModel;
    private ImportationController controleur;
    private Path sd;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-import-clic");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        ServiceSites service = injector.getInstance(ServiceSites.class);
        Site etang = service.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(etang.id(), "A1", 43.5, 5.4, "Chêne");

        // Le contrôleur est créé par Guice (robuste quelle que soit la signature de
        // son constructeur), puis on récupère SON ViewModel par réflexion (par type,
        // robuste au nom du champ) pour observer l'état de l'import.
        FXMLLoader loader = new FXMLLoader(ImportationController.class.getResource("Importation.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = (ImportationController) loader.getController();
        viewModel = extraireViewModel(controleur);
        // Confirmateur par défaut NON bloquant : depuis #214/#147, importer une nuit déjà importée ouvre
        // une confirmation. En headless, la boîte de dialogue native (Alert.showAndWait) bloquerait le fil
        // JavaFX indéfiniment. On injecte donc un confirmateur qui accepte ; les tests qui vérifient le
        // dialogue le surchargent (setConfirmateur) pour capturer/refuser.
        controleur.setConfirmateur(message -> true);
        stage.setScene(new Scene(vue, 1100, 760));
        stage.show();

        sd = preparerCarteSD(workspace.resolve("sd"));
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    /// Récupère le ViewModel détenu par le contrôleur par réflexion **sur le type**
    /// (et non le nom du champ) : indépendant de la signature du constructeur et du
    /// nommage interne du contrôleur.
    private static ImportationViewModel extraireViewModel(Object controleur) throws IllegalAccessException {
        for (java.lang.reflect.Field champ : controleur.getClass().getDeclaredFields()) {
            if (ImportationViewModel.class.isAssignableFrom(champ.getType())) {
                champ.setAccessible(true);
                return (ImportationViewModel) champ.get(controleur);
            }
        }
        throw new IllegalStateException("Aucun champ ImportationViewModel dans " + controleur.getClass());
    }

    @Test
    @DisplayName("Un clic sur « Importer cette nuit » lance et termine réellement l'import")
    void clic_importer_termine_l_import(FxRobot robot) {
        // Place le ViewModel dans un état où l'import est possible (dossier inspecté
        // + site + point rattachés), depuis le fil JavaFX.
        robot.interact(() -> {
            viewModel.inspection().dossierSourceProperty().set(sd);
            viewModel.inspecter();
            Site site = viewModel.rattachement().sites().get(0);
            viewModel.rattachement().siteSelectionneProperty().set(site);
            viewModel
                    .rattachement()
                    .pointSelectionneProperty()
                    .set(viewModel.rattachement().points().get(0));
        });
        WaitForAsyncUtils.waitForFxEvents();

        Button importer = robot.lookup("#boutonImporter").queryButton();
        assertThat(importer.isDisabled())
                .as("le bouton doit être actif une fois le rattachement complet")
                .isFalse();

        // Le clic réel sur le bouton (déclenche #importer).
        robot.interact(importer::fire);

        // L'import doit aboutir : sinon le clic « ne fait rien ».
        WaitForAsyncUtils.waitForFxEvents();
        boolean termine = attendreEtat(EtatImport.TERMINE);

        assertThat(termine)
                .as("après le clic, l'import doit atteindre l'état TERMINE (sinon « rien ne se passe »)")
                .isTrue();
        assertThat(viewModel.resultatProperty().get())
                .as("un import abouti expose son résultat")
                .isNotNull();

        // Le récap de succès s'affiche EN PLACE (l'import n'ouvre pas de nouvelle fenêtre).
        WaitForAsyncUtils.waitForFxEvents();
        Label statut = robot.lookup("#labelStatut").queryAs(Label.class);
        assertThat(statut.isVisible())
                .as("le récap de succès doit être affiché après l'import")
                .isTrue();
        assertThat(statut.getText()).contains("Import terminé");
    }

    @Test
    @DisplayName("Un import refusé (doublon R5) affiche le message d'erreur au lieu de disparaître")
    void import_refuse_affiche_l_erreur(FxRobot robot) {
        robot.interact(() -> {
            viewModel.inspection().dossierSourceProperty().set(sd);
            viewModel.inspecter();
            viewModel
                    .rattachement()
                    .siteSelectionneProperty()
                    .set(viewModel.rattachement().sites().get(0));
            viewModel
                    .rattachement()
                    .pointSelectionneProperty()
                    .set(viewModel.rattachement().points().get(0));
        });
        WaitForAsyncUtils.waitForFxEvents();
        Button importer = robot.lookup("#boutonImporter").queryButton();

        // 1er import : réussit.
        robot.interact(importer::fire);
        assertThat(attendreEtat(EtatImport.TERMINE)).isTrue();

        // 2e import du même quadruplet : refusé (R5). L'erreur doit rester visible.
        robot.interact(importer::fire);
        assertThat(attendreEtat(EtatImport.ECHEC))
                .as("ré-importer la même nuit doit échouer (unicité R5)")
                .isTrue();

        WaitForAsyncUtils.waitForFxEvents();
        Label message = robot.lookup("#labelMessage").queryAs(Label.class);
        assertThat(message.isVisible())
                .as("le message d'erreur doit être visible (hors zone de progression)")
                .isTrue();
        assertThat(message.getText()).isNotEmpty();
    }

    @Test
    @DisplayName("#214 : « Écraser et réimporter » remplace le passage existant après double confirmation")
    void ecraser_remplace_apres_double_confirmation(FxRobot robot) {
        importerUneFois(robot);
        ServiceImport service = injector.getInstance(ServiceImport.class);
        assertThat(service.nuitDejaImportee("1925492", "2026-04-22")).hasSize(1);

        rendreNumeroDejaPris(robot); // re-vérifie le n° 1, désormais pris → zone « Écraser » visible
        Button ecraser = robot.lookup("#boutonEcraser").queryButton();
        assertThat(ecraser.isDisabled())
                .as("Écraser actif quand le n° est pris et une nuit inspectée")
                .isFalse();

        // Double confirmation acceptée.
        List<String> confirmations = new ArrayList<>();
        controleur.setConfirmateur(message -> {
            confirmations.add(message);
            return true;
        });
        robot.interact(ecraser::fire);
        assertThat(attendreEtat(EtatImport.TERMINE)).isTrue();

        assertThat(confirmations)
                .as("double confirmation avant l'écrasement destructif")
                .hasSize(2);
        assertThat(service.nuitDejaImportee("1925492", "2026-04-22"))
                .as("la nuit est remplacée, pas dupliquée")
                .hasSize(1);
    }

    @Test
    @DisplayName("#214 : refuser la confirmation n'écrase rien (aucun import lancé)")
    void refuser_confirmation_n_ecrase_rien(FxRobot robot) {
        importerUneFois(robot);
        rendreNumeroDejaPris(robot);

        controleur.setConfirmateur(message -> false); // l'utilisateur annule dès le 1er message
        robot.interact(robot.lookup("#boutonEcraser").queryButton()::fire);
        WaitForAsyncUtils.waitForFxEvents();

        // Aucun import (re)lancé : l'état n'est pas EN_COURS, et la nuit reste unique en base.
        assertThat(viewModel.etatProperty().get()).isNotEqualTo(EtatImport.EN_COURS);
        assertThat(injector.getInstance(ServiceImport.class).nuitDejaImportee("1925492", "2026-04-22"))
                .hasSize(1);
    }

    @Test
    @DisplayName("#214/#147 : importer une nuit déjà importée demande confirmation avant un nouveau passage")
    void nuit_deja_importee_demande_confirmation(FxRobot robot) {
        importerUneFois(robot); // la nuit est importée une 1re fois (n° 1)
        ServiceImport service = injector.getInstance(ServiceImport.class);

        // Scénario réaliste : SANS réinspecter, on vise un n° LIBRE (n° 2). L'avertissement figé à la 1re
        // inspection est encore vide ; c'est le clic « Importer » qui doit rafraîchir la détection #147.
        viserNumeroPassageLibre(robot);

        // L'utilisateur refuse « importer quand même » : aucun nouveau passage n'est créé.
        List<String> confirmations = new ArrayList<>();
        controleur.setConfirmateur(message -> {
            confirmations.add(message);
            return false;
        });
        robot.interact(robot.lookup("#boutonImporter").queryButton()::fire);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(confirmations)
                .as("la détection est rafraîchie au clic : la nuit déjà importée déclenche la confirmation")
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("déjà été importée");
        assertThat(service.nuitDejaImportee("1925492", "2026-04-22"))
                .as("refus → aucun nouveau passage créé")
                .hasSize(1);
    }

    @Test
    @DisplayName("#214/#147 : confirmer « importer quand même » crée un nouveau passage pour la nuit")
    void nuit_deja_importee_importer_quand_meme(FxRobot robot) {
        importerUneFois(robot);
        viserNumeroPassageLibre(robot); // sans réinspection : la garde se rafraîchit au clic

        controleur.setConfirmateur(message -> true); // l'utilisateur assume le doublon
        robot.interact(robot.lookup("#boutonImporter").queryButton()::fire);
        assertThat(attendreEtat(EtatImport.TERMINE)).isTrue();

        assertThat(injector.getInstance(ServiceImport.class).nuitDejaImportee("1925492", "2026-04-22"))
                .as("importer quand même → un second passage pour la même nuit")
                .hasSize(2);
    }

    /// Vise un n° de passage **libre** (n° 2) **sans réinspecter** : l'import redevient possible (R5 ok) et
    /// la détection « nuit déjà importée » doit être rafraîchie par le clic « Importer », pas par une
    /// réinspection artificielle (sinon le test masquerait le bug de l'avertissement figé).
    private void viserNumeroPassageLibre(FxRobot robot) {
        robot.interact(() -> viewModel.rattachement().numeroPassageProperty().set(2));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// Inspecte la carte SD, rattache au seul site/point, fixe le n° 1, puis importe jusqu'à TERMINE.
    private void importerUneFois(FxRobot robot) {
        robot.interact(() -> {
            viewModel.inspection().dossierSourceProperty().set(sd);
            viewModel.inspecter();
            viewModel
                    .rattachement()
                    .siteSelectionneProperty()
                    .set(viewModel.rattachement().sites().get(0));
            viewModel
                    .rattachement()
                    .pointSelectionneProperty()
                    .set(viewModel.rattachement().points().get(0));
            viewModel.rattachement().numeroPassageProperty().set(1);
        });
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(robot.lookup("#boutonImporter").queryButton()::fire);
        assertThat(attendreEtat(EtatImport.TERMINE)).isTrue();
    }

    /// Force la re-vérification du pré-contrôle R5 sur le n° 1 (toggle), désormais pris après l'import :
    /// la zone « passage déjà existant » (et son bouton « Écraser ») devient visible.
    private void rendreNumeroDejaPris(FxRobot robot) {
        robot.interact(() -> {
            viewModel.rattachement().numeroPassageProperty().set(2); // libre
            viewModel.rattachement().numeroPassageProperty().set(1); // repris
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private boolean attendreEtat(EtatImport attendu) {
        try {
            WaitForAsyncUtils.waitFor(
                    10, TimeUnit.SECONDS, () -> viewModel.etatProperty().get() == attendu);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Path preparerCarteSD(Path dossier) throws IOException {
        Files.createDirectories(dossier);
        Files.writeString(dossier.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        Files.writeString(dossier.resolve("PaRecPR1925492_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
        ecrireWav(dossier.resolve("PaRecPR1925492_20260422_203922.wav"));
        ecrireWav(dossier.resolve("PaRecPR1925492_20260422_204326.wav"));
        return dossier;
    }

    private static void ecrireWav(Path fichier) throws IOException {
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
