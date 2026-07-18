package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX **ciblé câblage Vue ↔ ViewModel** de l'assistant **M-Import**.
///
/// Il complète [ImportationViewTest] (états initiaux) et [ImportationClicImporterTest] (clic
/// « Importer » de bout en bout) en comblant le trou pointé par l'audit du 2026-06-18 : les tests
/// de vue de base lisent les propriétés du ViewModel **sans jamais retrouver les contrôles par leur
/// `fx:id`**, si bien qu'un écran resté à l'état d'ébauche (FXML placeholder, `ComboBox` sans
/// `StringConverter`, `comboPoints` jamais peuplé, `boutonImporter` figé) passerait quand même.
///
/// Ici chaque test fait un **vrai lookup** (`robot.lookup("#fx:id")`) puis :
///  - vérifie la présence du contrôle (un FXML placeholder échouerait au lookup) ;
///  - vérifie que l'état du contrôle **reflète** le ViewModel (lecture seule, visibilité) ;
///  - **interagit** réellement (sélection de site/point, saisie d'année) et vérifie l'effet propagé
///    sur un autre contrôle (peuplement de `comboPoints`, aperçu du préfixe R6).
///
/// On reprend **exactement** le harnais de [ImportationViewTest] : chrome `MainView.fxml` chargé via
/// Guice sur une base SQLite jetable, écran ouvert par [NavigationImportation#ouvrir()], même seeder
/// (un site « Carré 640380 » avec un point « A1 »). On ne pilote pas le `DirectoryChooser` natif (non
/// testable headless) : les interactions passent par les contrôles de rattachement, qui suffisent à
/// exercer les liaisons bidirectionnelles et les écouteurs du ViewModel.
@ExtendWith(ApplicationExtension.class)
class ImportationVueIntegrationTest {

    private static final String ID_USER = "u-integ";
    private Injector injector;
    private Long idPoint;
    private int anneeCourante;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-import-integ");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        seeder(source);
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1100, 760));
        injector.getInstance(NavigationImportation.class).ouvrir();
        stage.show();
    }

    private void seeder(SourceDeDonnees source) {
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        ServiceSites service = injector.getInstance(ServiceSites.class);
        Site etang = service.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        PointDEcoute pointA1 = service.ajouterPoint(etang.id(), "A1", 43.5, 5.4, "Chêne");
        idPoint = pointA1.id();
        anneeCourante = injector.getInstance(Horloge.class).aujourdhui().getYear();
    }

    /// Insère un passage existant `(point, année, n°)` (avec son enregistreur, FK obligatoire) pour
    /// exercer le pré-contrôle R5 (#108) côté vue. Appelé depuis un test, après l'ouverture de l'écran.
    private void semerPassageExistant(int numero) {
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new EnregistreurDao(source).insert(new Enregistreur("9999999", "V1.01", null));
        new PassageDao(source)
                .insert(new Passage(
                        null,
                        numero,
                        anneeCourante,
                        "2026-04-22",
                        "20:25:00",
                        "07:47:00",
                        null,
                        StatutWorkflow.TRANSFORME,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        "9999999"));
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Tous les contrôles fx:id du formulaire d'import sont présents (pas un FXML placeholder)")
    void tous_les_controles_du_formulaire_sont_presents(FxRobot robot) {
        // Un écran resté à l'état d'ébauche (Importation.fxml placeholder) n'aurait aucun de ces fx:id :
        // ces lookups échoueraient et le test virerait au rouge.
        assertThat(robot.lookup("#champDossier").queryAs(TextField.class)).isNotNull();
        assertThat(robot.lookup("#boutonParcourir").queryAs(Button.class)).isNotNull();
        assertThat(robot.lookup("#boutonZip").queryAs(Button.class)).isNotNull(); // #139
        assertThat(robot.lookup("#racineImport").queryAs(VBox.class)).isNotNull(); // #139 (zone de dépôt)
        assertThat(robot.lookup("#sectionInspection").queryAs(VBox.class)).isNotNull();
        assertThat(robot.lookup("#comboSites").queryAs(ComboBox.class)).isNotNull();
        assertThat(robot.lookup("#comboPoints").queryAs(ComboBox.class)).isNotNull();
        assertThat(robot.lookup("#champAnnee").queryAs(TextField.class)).isNotNull();
        assertThat(robot.lookup("#champPassage").queryAs(TextField.class)).isNotNull();
        assertThat(robot.lookup("#labelApercu").queryAs(Label.class)).isNotNull();
        assertThat(robot.lookup("#labelPrefixeDiscordant").queryAs(Label.class)).isNotNull(); // #111
        assertThat(robot.lookup("#labelNuitExistante").queryAs(Label.class)).isNotNull(); // #147
        assertThat(robot.lookup("#boutonImporter").queryAs(Button.class)).isNotNull();
        assertThat(robot.lookup("#caseConserverOriginaux").queryAs(CheckBox.class))
                .isNotNull(); // conservation
        assertThat(robot.lookup("#zoneProgression").queryAs(VBox.class)).isNotNull();
        TableView<?> tableFichiers = robot.lookup("#tableFichiers").queryAs(TableView.class);
        assertThat(tableFichiers).isNotNull(); // #947 (suivi fichiers)
        // #1800 : cette table n'avait aucun menu contextuel ; elle offre désormais « Colonnes… ».
        assertThat(tableFichiers.getContextMenu()).isNotNull();
        assertThat(tableFichiers.getContextMenu().getItems().stream()
                        .map(i -> i.getText())
                        .toList())
                .as("#1798 : le nom de fichier est la valeur qu'on recopie depuis cet écran")
                .contains("Colonnes…", "Copier");
        assertThat(robot.lookup("#boutonAnnuler").queryAs(Button.class)).isNotNull(); // #146 (annulation)
        assertThat(robot.lookup("#zoneRejets").queryAs(VBox.class)).isNotNull(); // #155 (rapport d'import)
        assertThat(robot.lookup("#listeRejets").queryAs(ListView.class)).isNotNull(); // #155
        // Pré-contrôle R5 (#108) : la zone d'avertissement de doublon et son bouton « n° libre » existent.
        assertThat(robot.lookup("#zonePassageExistant").queryAs(HBox.class)).isNotNull();
        assertThat(robot.lookup("#labelPassageExistant").queryAs(Label.class)).isNotNull();
        assertThat(robot.lookup("#boutonNumeroLibre").queryAs(Button.class)).isNotNull();
    }

    @Test
    @DisplayName("#108 : sans passage existant, la zone d'avertissement de doublon reste masquée")
    void zone_passage_existant_masquee_sans_doublon(FxRobot robot) {
        @SuppressWarnings("unchecked")
        ComboBox<Site> comboSites = robot.lookup("#comboSites").queryAs(ComboBox.class);
        @SuppressWarnings("unchecked")
        ComboBox<PointDEcoute> comboPoints = robot.lookup("#comboPoints").queryAs(ComboBox.class);
        HBox zone = robot.lookup("#zonePassageExistant").queryAs(HBox.class);

        assertThat(zone.isVisible())
                .as("aucun rattachement : pas d'avertissement")
                .isFalse();

        // Rattacher à un site/point réels : le pré-contrôle R5 interroge la base (vide) et ne trouve
        // aucun doublon → la zone reste masquée (et non gérée, pour ne pas occuper d'espace).
        robot.interact(() -> {
            comboSites.setValue(comboSites.getItems().get(0));
            comboPoints.setValue(comboPoints.getItems().get(0));
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(zone.isVisible())
                .as("n° de passage libre pour ce point : aucun avertissement")
                .isFalse();
        assertThat(zone.isManaged()).isFalse();
    }

    @Test
    @DisplayName("#108 : saisir un n° déjà pris affiche l'avertissement ; « Utiliser ce n° » corrige et le masque")
    void doublon_affiche_avertissement_puis_bouton_corrige(FxRobot robot) {
        // Un passage n° 1 existe déjà pour le point A1 (année courante). À la sélection du point, le n° est
        // pré-rempli au prochain libre (2, source unique #…) : PAS d'avertissement au départ. C'est en
        // SAISISSANT le n° 1 (déjà pris) que le pré-contrôle R5 (#108) doit se déclencher.
        semerPassageExistant(1);

        @SuppressWarnings("unchecked")
        ComboBox<Site> comboSites = robot.lookup("#comboSites").queryAs(ComboBox.class);
        @SuppressWarnings("unchecked")
        ComboBox<PointDEcoute> comboPoints = robot.lookup("#comboPoints").queryAs(ComboBox.class);
        HBox zone = robot.lookup("#zonePassageExistant").queryAs(HBox.class);
        Label labelPassageExistant = robot.lookup("#labelPassageExistant").queryAs(Label.class);
        TextField champPassage = robot.lookup("#champPassage").queryAs(TextField.class);

        robot.interact(() -> {
            comboSites.setValue(comboSites.getItems().get(0));
            comboPoints.setValue(comboPoints.getItems().get(0));
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Pré-remplissage au prochain libre (2) → n° libre, aucun avertissement.
        assertThat(champPassage.getText()).as("n° pré-rempli au prochain libre").isEqualTo("2");
        assertThat(zone.isVisible())
                .as("n° pré-rempli libre : aucun avertissement")
                .isFalse();

        // L'utilisateur saisit le n° 1, déjà pris → la zone d'avertissement s'affiche.
        robot.interact(() -> champPassage.setText("1"));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(zone.isVisible())
                .as("n° 1 déjà pris : la zone d'avertissement s'affiche")
                .isTrue();
        assertThat(zone.isManaged()).isTrue();
        assertThat(labelPassageExistant.getText()).containsIgnoringCase("existe déjà");

        // « Utiliser ce n° » adopte le prochain libre (2, le 1 étant pris) et masque l'avertissement.
        robot.clickOn("#boutonNumeroLibre");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(champPassage.getText())
                .as("le n° de passage est corrigé vers le prochain libre")
                .isEqualTo("2");
        assertThat(zone.isVisible())
                .as("n° libre adopté : l'avertissement disparaît")
                .isFalse();
    }

    @Test
    @DisplayName("Le champ dossier est en lecture seule et vide tant qu'aucun dossier n'est choisi")
    void champ_dossier_lecture_seule_et_vide_au_depart(FxRobot robot) {
        TextField champDossier = robot.lookup("#champDossier").queryAs(TextField.class);

        // editable=false dans le FXML : l'utilisateur passe par « Parcourir », pas par la saisie.
        assertThat(champDossier.isEditable())
                .as("le champ dossier ne doit pas être éditable à la main")
                .isFalse();
        // Texte lié à dossierSourceProperty (null au départ -> chaîne vide).
        assertThat(champDossier.getText())
                .as("aucun dossier choisi : le champ reflète un dossierSource vide")
                .isEmpty();
    }

    @Test
    @DisplayName("La section inspection est masquée et non gérée avant toute inspection")
    void section_inspection_masquee_au_depart(FxRobot robot) {
        VBox sectionInspection = robot.lookup("#sectionInspection").queryAs(VBox.class);

        // visibleProperty/managedProperty liées à inspecteProperty (false au départ).
        assertThat(sectionInspection.isVisible())
                .as("rien n'est inspecté : la section inspection reste cachée")
                .isFalse();
        assertThat(sectionInspection.isManaged())
                .as("la section cachée ne doit pas occuper d'espace dans la mise en page")
                .isFalse();
    }

    @Test
    @DisplayName("Sélectionner un site peuple la combo des points d'écoute (rattachement R6/R7)")
    void selectionner_un_site_peuple_la_combo_des_points(FxRobot robot) {
        @SuppressWarnings("unchecked")
        ComboBox<Site> comboSites = robot.lookup("#comboSites").queryAs(ComboBox.class);
        @SuppressWarnings("unchecked")
        ComboBox<PointDEcoute> comboPoints = robot.lookup("#comboPoints").queryAs(ComboBox.class);

        assertThat(comboPoints.getItems())
                .as("aucun site sélectionné : la combo des points est vide")
                .isEmpty();

        // Interaction : choisir le premier site (propagé au ViewModel par bindBidirectional, ce qui
        // déclenche le rechargement de ses points).
        robot.interact(() -> comboSites.setValue(comboSites.getItems().get(0)));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(comboPoints.getItems())
                .as("après sélection du site, la combo des points doit se peupler (point « A1 »)")
                .hasSize(1);
    }

    @Test
    @DisplayName("Les combos site et point affichent un libellé lisible (StringConverter câblé)")
    void les_combos_affichent_un_libelle_lisible(FxRobot robot) {
        @SuppressWarnings("unchecked")
        ComboBox<Site> comboSites = robot.lookup("#comboSites").queryAs(ComboBox.class);
        @SuppressWarnings("unchecked")
        ComboBox<PointDEcoute> comboPoints = robot.lookup("#comboPoints").queryAs(ComboBox.class);

        // Sélectionner le site peuple la combo des points (sinon aucun point à convertir).
        robot.interact(() -> comboSites.setValue(comboSites.getItems().get(0)));
        WaitForAsyncUtils.waitForFxEvents();

        Site site = comboSites.getItems().get(0);
        PointDEcoute point = comboPoints.getItems().get(0);

        // Sans StringConverter, les combos afficheraient le toString brut du record.
        assertThat(comboSites.getConverter())
                .as("la combo des sites doit avoir un StringConverter")
                .isNotNull();
        assertThat(comboSites.getConverter().toString(site)).isEqualTo("Carré 640380 — Étang de la Tuilière");
        assertThat(comboPoints.getConverter())
                .as("la combo des points doit avoir un StringConverter")
                .isNotNull();
        assertThat(comboPoints.getConverter().toString(point)).isEqualTo("A1");
    }

    @Test
    @DisplayName("Sélectionner un site puis un point alimente l'aperçu du préfixe (R6)")
    void selectionner_site_et_point_alimente_l_apercu(FxRobot robot) {
        @SuppressWarnings("unchecked")
        ComboBox<Site> comboSites = robot.lookup("#comboSites").queryAs(ComboBox.class);
        @SuppressWarnings("unchecked")
        ComboBox<PointDEcoute> comboPoints = robot.lookup("#comboPoints").queryAs(ComboBox.class);
        Label labelApercu = robot.lookup("#labelApercu").queryAs(Label.class);

        assertThat(labelApercu.getText())
                .as("tant que site/point ne sont pas choisis, l'aperçu du préfixe est vide")
                .isEmpty();

        // Interaction : rattacher la nuit à un site puis à un point d'écoute.
        robot.interact(() -> {
            comboSites.setValue(comboSites.getItems().get(0));
            comboPoints.setValue(comboPoints.getItems().get(0));
        });
        WaitForAsyncUtils.waitForFxEvents();

        // L'aperçu non vide prouve que pointSelectionne est bien câblé (bindBidirectional + majApercu).
        // Forme R6 : Car<carré>-<année>-Pass<n>-<point>-<suffixe>.
        assertThat(labelApercu.getText())
                .as("l'aperçu du préfixe doit refléter le rattachement (carré 640380, passage 1, point A1)")
                .startsWith("Car640380-")
                .contains("-Pass1-A1-");
    }

    @Test
    @DisplayName("Saisir une année met à jour l'aperçu du préfixe (champ ↔ ViewModel bidirectionnel)")
    void saisir_une_annee_met_a_jour_l_apercu(FxRobot robot) {
        @SuppressWarnings("unchecked")
        ComboBox<Site> comboSites = robot.lookup("#comboSites").queryAs(ComboBox.class);
        @SuppressWarnings("unchecked")
        ComboBox<PointDEcoute> comboPoints = robot.lookup("#comboPoints").queryAs(ComboBox.class);
        TextField champAnnee = robot.lookup("#champAnnee").queryAs(TextField.class);
        Label labelApercu = robot.lookup("#labelApercu").queryAs(Label.class);

        // Pré-requis : un site et un point sélectionnés pour que l'aperçu soit calculé.
        robot.interact(() -> {
            comboSites.setValue(comboSites.getItems().get(0));
            comboPoints.setValue(comboPoints.getItems().get(0));
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Interaction : changer l'année dans le champ (lié bidirectionnellement à anneeProperty).
        robot.interact(() -> champAnnee.setText("2099"));
        WaitForAsyncUtils.waitForFxEvents();

        // La nouvelle année doit se répercuter dans l'aperçu : preuve du binding champ -> VM -> aperçu.
        assertThat(labelApercu.getText())
                .as("modifier l'année doit recalculer l'aperçu du préfixe (R6)")
                .contains("Car640380-2099-Pass1-A1-");
    }
}
