package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.PanneauCompteRendu;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.commun.viewmodel.EtatUnite;
import fr.univ_amu.iut.fixture.JournalDeCapteur;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.viewmodel.EtatImport;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel;
import fr.univ_amu.iut.importation.viewmodel.LigneFichierImport;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
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
    private static final int FREQUENCE_WAV = 384_000; // Hz, multiple de 10
    private static final int TRAMES = 576_000;

    private Injector injector;
    private ImportationViewModel viewModel;
    private ImportationController controleur;
    private Path sd;

    /// JUnit crée ce répertoire et le **supprime** en fin de test. `createTempDirectory`
    /// n'enlevait rien, et cette classe était l'une des dix qui laissaient la moitié des
    /// répertoires attribuables (#4868).
    @TempDir
    private Path dossierTemporaire;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = dossierTemporaire;
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
        loader.setControllerFactory(DiagnosticGuice.pour(injector));
        Parent vue = loader.load();
        controleur = (ImportationController) loader.getController();
        viewModel = extraireViewModel(controleur);
        // Confirmateur par défaut NON bloquant : depuis #214/#147, importer une nuit déjà importée ouvre
        // une confirmation. En headless, la boîte de dialogue native (Alert.showAndWait) bloquerait le fil
        // JavaFX indéfiniment. On injecte donc un confirmateur qui accepte ; les tests qui vérifient le
        // dialogue le surchargent (confirmateur().definir(...)) pour capturer/refuser.
        controleur.confirmateur().definir(message -> true);
        // Désignation de la source (#1431) : le double répond `choix`. Sans lui, « Parcourir » ouvrirait
        // un DirectoryChooser natif, qui fige le test aussi sûrement qu'un Alert - c'est pourquoi ce
        // bouton n'était jamais cliqué, et pourquoi les tests posaient le dossier directement sur le
        // ViewModel, en contournant l'écran.
        controleur.selecteur().definir(new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                return choix;
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
                filtres.add(filtre);
                return choix;
            }

            @Override
            public Optional<Path> enregistrerFichier(String titre, String nomPropose, FiltreFichier filtre) {
                throw new AssertionError("l'import lit une source, il n'écrit aucun fichier");
            }
        });
        FenetreAjustable.poser(stage, vue, 1100, 760);
        FenetreAjustable.afficher(stage);

        sd = preparerCarteSD(workspace.resolve("sd"));
    }

    /// Ce que le double de sélection répondra : `Optional.empty()` = l'utilisateur a **annulé**.
    private Optional<Path> choix = Optional.empty();

    /// Filtres réellement proposés par le sélecteur de fichier (« Choisir un .zip »).
    private final List<FiltreFichier> filtres = new ArrayList<>();

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
    @DisplayName("#1431 : un clic sur « Parcourir » désigne la nuit, et l'écran l'inspecte pour de bon")
    void clic_parcourir_charge_et_inspecte_la_source(FxRobot robot) {
        choix = Optional.of(sd);

        robot.interact(() -> robot.lookup("#boutonParcourir").queryButton().fire());
        WaitForAsyncUtils.waitForFxEvents();
        // `waitForFxEvents` vide la file du fil FX ; il n'attend PAS le thread d'arriere-plan qui la
        // remplira, et l'inspection tourne precisement hors de ce fil. Le banc affirmait donc sur un
        // travail qui n'avait pas forcement fini, et il est tombe 3 fois sur 1 150 (#4815, mesure par
        // #4811). Meme mecanisme que #4408 et #4814, sur un autre ecran.
        //
        // Les deux premieres assertions lisent des proprietes posees AU CLIC : elles tiennent. C'est la
        // troisieme, qui depend de la fin de l'inspection, qui doit etre attendue.
        Attente.queSurLeFil(() -> !viewModel.rattachement().sites().isEmpty(), "que l'inspection ait rendu ses sites");

        // Le dossier désigné devient la source de l'écran, et l'inspection tourne : c'est tout le geste,
        // et il n'avait jamais été joué - les tests posaient le dossier directement sur le ViewModel.
        assertThat(viewModel.inspection().dossierSourceProperty().get()).isEqualTo(sd);
        assertThat(robot.lookup("#champDossier").queryAs(TextField.class).getText())
                .contains(sd.getFileName().toString());
        assertThat(viewModel.rattachement().sites())
                .as("l'inspection a bien tourné : les sites rattachables sont proposés")
                .isNotEmpty();
    }

    @Test
    @DisplayName("#1431 : « Parcourir » annulé : aucune source n'est chargée")
    void clic_parcourir_annule_ne_charge_rien(FxRobot robot) {
        choix = Optional.empty();

        robot.interact(() -> robot.lookup("#boutonParcourir").queryButton().fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(viewModel.inspection().dossierSourceProperty().get())
                .as("renoncer au sélecteur ne doit rien changer à l'écran")
                .isNull();
    }

    @Test
    @DisplayName("#1431 : « Choisir un .zip » ne propose que des archives (#139)")
    void clic_zip_propose_le_filtre_archive(FxRobot robot) {
        choix = Optional.empty(); // on vérifie ce qui est PROPOSÉ, pas ce qui est choisi

        robot.interact(() -> robot.lookup("#boutonZip").queryButton().fire());

        assertThat(filtres)
                .singleElement()
                .satisfies(filtre -> assertThat(filtre.motif()).isEqualTo("*.zip"));
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
        Attente.que(
                () -> viewModel.etatProperty().get() == EtatImport.TERMINE,
                "après le clic, l'import atteint TERMINE (sinon « rien ne se passe »)",
                10_000L);
        assertThat(viewModel.resultatProperty().get())
                .as("un import abouti expose son résultat")
                .isNotNull();

        // Le récap de succès s'affiche EN PLACE (l'import n'ouvre pas de nouvelle fenêtre). Depuis #2358
        // c'est la bande de compte rendu chiffré qui le porte, et non plus la phrase de statut.
        WaitForAsyncUtils.waitForFxEvents();
        PanneauCompteRendu compteRendu = robot.lookup("#compteRenduChiffre").queryAs(PanneauCompteRendu.class);
        assertThat(compteRendu.isVisible())
                .as("le compte rendu de l'import doit être affiché après le clic")
                .isTrue();
        assertThat(((Label) compteRendu.lookup(".cr-titre")).getText())
                .as("le titre du compte rendu nomme l'import abouti et sa nuit")
                .startsWith("Import terminé");
        assertThat(((Label) compteRendu.lookup(".cr-badge")).getText())
                .as("la pastille chiffre le résultat : le récap n'est plus une phrase")
                .contains("importés");

        // La phrase de statut s'efface sur un import abouti : deux formulations du même fait côte à côte
        // se liraient comme deux faits. La barre de statut du chrome, elle, garde sa version courte.
        Label statut = robot.lookup("#labelStatut").queryAs(Label.class);
        assertThat(statut.isVisible())
                .as("le titre de la bande dit déjà ce que cette phrase disait")
                .isFalse();
        // Barre de statut (#1024) : le statut du wizard reste porté par la zone centre ; l'agrégat racine
        // (sans contexte passage) laisse la gauche au défaut du chrome.
        var zones = controleur.zonesStatutProperty().get();
        assertThat(zones.centre()).contains("Import terminé");
        assertThat(zones.gauche()).isEmpty();
    }

    @Test
    @DisplayName("#947 : la table de suivi par fichier se remplit pendant l'import et finit toute « terminée »")
    void table_de_suivi_par_fichier_se_remplit(FxRobot robot) {
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

        robot.interact(() -> robot.lookup("#boutonImporter").queryButton().fire());
        Attente.que(() -> viewModel.etatProperty().get() == EtatImport.TERMINE, "l'import atteint TERMINE", 10_000L);
        WaitForAsyncUtils.waitForFxEvents();

        // Les événements par fichier (relayés au fil JavaFX pendant l'import) ont rempli la table : une
        // ligne par original de la carte, toutes terminées (barre au bout), sans étape résiduelle.
        TableView<?> table = robot.lookup("#tableFichiers").queryAs(TableView.class);
        assertThat(table.getItems()).hasSize(2);
        assertThat(viewModel.suiviFichiers().lignes())
                .extracting(LigneFichierImport::nomFichier)
                .allSatisfy(nom -> assertThat(nom).endsWith(".wav"));
        assertThat(viewModel.suiviFichiers().lignes()).allSatisfy(ligne -> {
            assertThat(ligne.etatProperty().get()).isEqualTo(EtatUnite.TERMINEE);
            assertThat(ligne.fractionProperty().get()).isEqualTo(1.0);
            assertThat(ligne.etapeProperty().get()).isEmpty();
        });
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
        Attente.que(() -> viewModel.etatProperty().get() == EtatImport.TERMINE, "l'import atteint TERMINE", 10_000L);

        // 2e import du même quadruplet : refusé (R5). L'erreur doit rester visible.
        robot.interact(importer::fire);
        Attente.que(
                () -> viewModel.etatProperty().get() == EtatImport.ECHEC,
                "ré-importer la même nuit échoue (unicité R5)",
                10_000L);

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
        controleur.confirmateur().definir(message -> {
            confirmations.add(message);
            return true;
        });
        robot.interact(ecraser::fire);
        Attente.que(() -> viewModel.etatProperty().get() == EtatImport.TERMINE, "l'import atteint TERMINE", 10_000L);

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

        controleur.confirmateur().definir(message -> false); // l'utilisateur annule dès le 1er message
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
        controleur.confirmateur().definir(message -> {
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

        controleur.confirmateur().definir(message -> true); // l'utilisateur assume le doublon
        robot.interact(robot.lookup("#boutonImporter").queryButton()::fire);
        Attente.que(() -> viewModel.etatProperty().get() == EtatImport.TERMINE, "l'import atteint TERMINE", 10_000L);

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
        Attente.que(() -> viewModel.etatProperty().get() == EtatImport.TERMINE, "l'import atteint TERMINE", 10_000L);
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

    private Path preparerCarteSD(Path dossier) throws IOException {
        Files.createDirectories(dossier);
        JournalDeCapteur.ecrire(dossier, "1925492", LocalDate.of(2026, 4, 22));
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
        // Writer de production (#2864) : memes octets, et c'est le format que l'application
        // saura relire.
        FichierWav.ecrire(fichier, 1, FREQUENCE_WAV, 16, pcm, 0, pcm.length);
    }
}
