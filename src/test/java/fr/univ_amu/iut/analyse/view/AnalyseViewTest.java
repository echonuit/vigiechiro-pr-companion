package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.analyse.model.ServiceAnalyse;
import fr.univ_amu.iut.analyse.viewmodel.AnalyseViewModel;
import fr.univ_amu.iut.analyse.viewmodel.Regroupement;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.VueSauvegardee;
import fr.univ_amu.iut.commun.view.DescripteurCritere;
import fr.univ_amu.iut.commun.view.DescripteurFiltre;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX de l'écran **« Espèces & observations »** : chargement du FXML via Guice
/// (avec un [ServiceAnalyse] mocké), table par espèce affichée par défaut, et bascule Par carré qui
/// montre la table des carrés. Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class AnalyseViewTest {

    private ServiceAnalyse service;
    private OuvrirPassage ouvrirPassage;
    private OuvrirAudio ouvrirAudio;
    private DepotVues depotVues;
    private AnalyseController controleur;
    private final List<String> urlsFiche = new ArrayList<>();

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceAnalyse.class);
        ouvrirPassage = mock(OuvrirPassage.class);
        ouvrirAudio = mock(OuvrirAudio.class);
        when(service.observationsAnalyse(anyString())).thenReturn(List.of(obsAnalyse("Pippip", "Pipistrelle commune")));
        when(service.observationsDeLEspece(anyString(), anyString(), any()))
                .thenReturn(List.of(new ObservationEspece(
                        7L,
                        70L,
                        42L,
                        2,
                        2026,
                        "2026-06-22",
                        "640380",
                        "A1",
                        "Étang",
                        "Pippip",
                        0.9,
                        "Pippip",
                        0.95,
                        StatutObservation.VALIDEE)));
        OuvrirPassage navigationPassage = ouvrirPassage;
        OuvrirAudio navigationAudio = ouvrirAudio;
        depotVues = mock(DepotVues.class);
        // Une vue déjà enregistrée pour cet écran : elle doit apparaître comme onglet (nom seul lu à l'init).
        when(depotVues.findByFeature("analyse"))
                .thenReturn(List.of(new VueSauvegardee(1L, "analyse", "Validées 2026", "{\"criteres\":[]}")));
        DepotVues depot = depotVues;
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            AnalyseViewModel viewModel() {
                return new AnalyseViewModel(service, "u-1");
            }

            @Provides
            OuvrirPassage ouvrirPassage() {
                return navigationPassage;
            }

            @Provides
            OuvrirAudio ouvrirAudio() {
                return navigationAudio;
            }

            @Provides
            DepotVues depotVues() {
                return depot;
            }

            // « Fiche de l'espèce » (#848) : navigateur factice qui enregistre l'URL ouverte.
            @Provides
            OuvreurDeLien ouvreurDeLien() {
                return urlsFiche::add;
            }
        });
        FXMLLoader loader = new FXMLLoader(AnalyseController.class.getResource("Analyse.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        // Désignation du fichier d'export (#1431) : sans ce double, « Exporter… » ouvrirait un FileChooser
        // natif, qui fige le test. C'est pourquoi ce geste n'était couvert nulle part - la Javadoc de
        // `exporter()` le disait franchement : « le dialog vit dans la vue (non testé en TestFX) ».
        controleur.selecteur().definir(new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                throw new AssertionError("l'export désigne un fichier à écrire, pas un dossier");
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
                throw new AssertionError("l'export écrit un fichier, il n'en ouvre pas un existant");
            }

            @Override
            public Optional<Path> enregistrerFichier(String titre, String nomPropose, FiltreFichier filtre) {
                nomsProposes.add(nomPropose);
                filtres.add(filtre);
                return choixExport;
            }
        });
        stage.setScene(new Scene(vue, 1000, 640));
        stage.show();
    }

    /// Ce que le double de sélection répondra à l'export : vide = l'utilisateur a **annulé**.
    private Optional<Path> choixExport = Optional.empty();

    /// Noms de fichier **proposés** par l'export (l'utilisateur reste libre de les changer).
    private final List<String> nomsProposes = new ArrayList<>();

    /// Types de fichiers proposés par l'export.
    private final List<FiltreFichier> filtres = new ArrayList<>();

    /// Une observation enrichie de l'espèce `taxon` sur le carré 640380 (statut validé) : matière brute que
    /// le ViewModel filtre puis agrège (#537).
    private static ObservationAnalyse obsAnalyse(String taxon, String vern) {
        return new ObservationAnalyse(
                taxon,
                taxon + " (latin)",
                vern,
                "Chiroptères",
                StatutObservation.VALIDEE,
                42L,
                2026,
                "640380",
                "Étang",
                1L);
    }

    /// Variante **sans nom latin** : un pseudo-taxon (« Bruit ») n'a ni code PNA ni binôme sur lequel se
    /// rabattre, donc aucune fiche n'est constructible, quelle que soit la source universelle préférée.
    private static ObservationAnalyse obsSansFiche(String taxon, String vern) {
        return new ObservationAnalyse(
                taxon, null, vern, "Chiroptères", StatutObservation.VALIDEE, 42L, 2026, "640380", "Étang", 1L);
    }

    @Test
    @DisplayName("La vue par défaut « À valider » est un onglet qui filtre : l'unique observation Validée disparaît")
    void vue_par_defaut_a_valider_filtre_le_tableau(FxRobot robot) {
        FlowPane onglets = robot.lookup("#barreOnglets").queryAs(FlowPane.class);
        // Les 4 onglets par défaut sont rendus, dans l'ordre, avant la vue utilisateur « Validées 2026 ».
        assertThat(robot.from(onglets).lookup(".onglet-vue-nom").queryAllAs(Label.class))
                .extracting(Label::getText)
                .containsSequence("Tout", "À valider", "Validées", "Chiroptères");

        Label aValider = robot.from(onglets).lookup(".onglet-vue-nom").queryAllAs(Label.class).stream()
                .filter(label -> "À valider".equals(label.getText()))
                .findFirst()
                .orElseThrow();
        robot.interact(() -> aValider.getOnMouseClicked().handle(null));
        WaitForAsyncUtils.waitForFxEvents();

        // La seule observation seedée est Validée : la vue « À valider » (statut À revoir) l'écarte → 0 espèce.
        assertThat(robot.lookup("#tableEspeces").queryAs(TableView.class).getItems())
                .as("« À valider » n'affiche aucune observation Validée")
                .isEmpty();
    }

    @Test
    @DisplayName("Par défaut : la table par espèce est affichée et peuplée ; le résumé compte les espèces")
    void affiche_inventaire_par_espece_par_defaut(FxRobot robot) {
        TableView<?> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        TableView<?> carres = robot.lookup("#tableCarres").queryAs(TableView.class);

        assertThat(especes.isVisible()).isTrue();
        assertThat(carres.isVisible()).isFalse();
        assertThat(especes.getItems()).hasSize(1);
        assertThat(robot.lookup("#lblResume").queryAs(Label.class).getText()).contains("espèce");
        // Barre de statut (#1023) : le résumé occupe la zone centre (agrégat top-level → gauche au défaut).
        assertThat(controleur.zonesStatutProperty().get().centre()).contains("espèce");
        assertThat(controleur.zonesStatutProperty().get().gauche()).isEmpty();
    }

    @Test
    @DisplayName("Basculer Par carré affiche la table des carrés (richesse spécifique)")
    void basculer_par_carre_affiche_les_carres(FxRobot robot) {
        @SuppressWarnings("unchecked")
        ComboBox<Regroupement> choixRegroupement =
                robot.lookup("#choixRegroupement").queryAs(ComboBox.class);
        robot.interact(() -> choixRegroupement.getSelectionModel().select(Regroupement.PAR_CARRE));

        TableView<?> carres = robot.lookup("#tableCarres").queryAs(TableView.class);
        assertThat(carres.isVisible()).isTrue();
        assertThat(carres.getItems()).hasSize(1);
        assertThat(robot.lookup("#tableEspeces").queryAs(TableView.class).isVisible())
                .isFalse();
    }

    @Test
    @DisplayName("Au retour sur l'écran, l'inventaire est rechargé (RafraichirAuRetour)")
    void retour_recharge_l_inventaire(FxRobot robot) {
        TableView<?> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        assertThat(especes.getItems()).as("état initial").hasSize(1);

        // Des observations ont été validées ailleurs : le service renvoie désormais deux espèces.
        when(service.observationsAnalyse(anyString()))
                .thenReturn(List.of(
                        obsAnalyse("Pippip", "Pipistrelle commune"), obsAnalyse("Nyclei", "Noctule de Leisler")));
        robot.interact(controleur::rafraichirAuRetour);

        assertThat(especes.getItems())
                .as("le retour recharge l'inventaire (plus de compteurs périmés)")
                .hasSize(2);
    }

    @Test
    @DisplayName("Sélectionner une espèce remplit le détail ; « Ouvrir le passage » navigue vers M-Passage")
    void selectionner_espece_affiche_le_detail_et_ouvre_le_passage(FxRobot robot) {
        @SuppressWarnings("unchecked")
        TableView<Object> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        TableView<?> observations = robot.lookup("#tableObservations").queryAs(TableView.class);
        Button ouvrir = robot.lookup("#boutonOuvrirPassage").queryAs(Button.class);
        assertThat(observations.getItems()).as("détail vide au départ").isEmpty();
        assertThat(ouvrir.isDisabled()).as("rien à ouvrir sans sélection").isTrue();

        // Sélectionner l'espèce charge ses observations (à travers les passages).
        robot.interact(() -> especes.getSelectionModel().select(0));
        assertThat(observations.getItems()).hasSize(1);

        // Sélectionner une observation active le bouton, qui ouvre le bon passage avec son contexte.
        robot.interact(() -> observations.getSelectionModel().select(0));
        assertThat(ouvrir.isDisabled()).isFalse();
        robot.interact(ouvrir::fire);

        ArgumentCaptor<ContexteSite> contexte = ArgumentCaptor.forClass(ContexteSite.class);
        verify(ouvrirPassage).ouvrir(eq(42L), contexte.capture());
        assertThat(contexte.getValue().numeroCarre()).isEqualTo("640380");
        assertThat(contexte.getValue().codePoint()).isEqualTo("A1");
        assertThat(contexte.getValue().nomSite()).isEqualTo("Étang");
    }

    @Test
    @DisplayName("#848 : le menu contextuel « Fiche de l'espèce » ouvre la fiche PNA de l'espèce sélectionnée")
    void fiche_espece_ouvre_la_fiche_de_l_espece_selectionnee(FxRobot robot) {
        TableView<?> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        // Première espèce = Pipistrelle commune (code « Pippip »), un chiroptère à fiche PNA.
        robot.interact(() -> especes.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        MenuItem fiche = especes.getContextMenu().getItems().get(0);
        assertThat(fiche.isDisable()).isFalse();
        assertThat(fiche.getText()).isEqualTo("Fiche de l'espèce (Pipistrelle commune)");

        robot.interact(fiche::fire);
        assertThat(urlsFiche)
                .containsExactly(
                        "https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/pipistrelle-commune/");
    }

    @Test
    @DisplayName("#916 : le clic droit compose « Fiche de l'espèce » + « Colonnes… » ; le ☰ porte « Colonnes… »")
    void colonnes_coexistent_avec_la_fiche(FxRobot robot) {
        TableView<?> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        robot.interact(() -> especes.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        var itemsClicDroit = especes.getContextMenu().getItems();
        // [Fiche de l'espèce, Copier ▸, séparateur, Colonnes…].
        assertThat(itemsClicDroit).hasSize(4);
        assertThat(itemsClicDroit.get(0).getText()).as("la fiche vient en tête").startsWith("Fiche de l'espèce");
        assertThat(itemsClicDroit.get(1).getText()).isEqualTo("Copier");
        assertThat(itemsClicDroit.get(itemsClicDroit.size() - 1).getText()).isEqualTo("Colonnes…");

        MenuButton outils = robot.lookup("#menuOutils").queryAs(MenuButton.class);
        assertThat(outils.getItems())
                .anySatisfy(item -> assertThat(item.getText()).isEqualTo("Colonnes…"));
    }

    @Test
    @DisplayName("#1798 : « Copier ▸ » sur les espèces propose « Nom latin » et « Nom vernaculaire »")
    void copier_sur_les_especes(FxRobot robot) {
        TableView<?> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        robot.interact(() -> especes.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        Menu copier = (Menu) especes.getContextMenu().getItems().stream()
                .filter(i -> "Copier".equals(i.getText()))
                .findFirst()
                .orElseThrow();
        assertThat(copier.getItems()).extracting(MenuItem::getText).containsExactly("Nom latin", "Nom vernaculaire");
    }

    @Test
    @DisplayName("EPIC #914 : les tables carrés et observations offrent aussi « Colonnes… » au clic droit")
    void colonnes_sur_les_tables_carres_et_observations(FxRobot robot) {
        TableView<?> carres = robot.lookup("#tableCarres").queryAs(TableView.class);
        TableView<?> observations = robot.lookup("#tableObservations").queryAs(TableView.class);

        assertThat(carres.getContextMenu())
                .as("clic droit câblé sur l'inventaire par carré")
                .isNotNull();
        assertThat(dernierTexte(carres.getContextMenu())).isEqualTo("Colonnes…");
        assertThat(observations.getContextMenu())
                .as("clic droit câblé sur les observations")
                .isNotNull();
        assertThat(dernierTexte(observations.getContextMenu())).isEqualTo("Colonnes…");

        // Un seul ☰ pour la vue : il ne porte qu'**une** entrée « Colonnes… » (celle de la table maître
        // visible), pas une par table.
        MenuButton outils = robot.lookup("#menuOutils").queryAs(MenuButton.class);
        assertThat(outils.getItems())
                .filteredOn(item -> "Colonnes…".equals(item.getText()))
                .hasSize(1);
    }

    private static String dernierTexte(ContextMenu menu) {
        var items = menu.getItems();
        return items.get(items.size() - 1).getText();
    }

    @Test
    @DisplayName("« Écouter / valider » ouvre la vue audio sur l'espèce, ciblée sur l'observation")
    void ecouter_valider_ouvre_la_vue_audio_sur_l_espece(FxRobot robot) {
        @SuppressWarnings("unchecked")
        TableView<Object> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        TableView<?> observations = robot.lookup("#tableObservations").queryAs(TableView.class);
        Button ecouter = robot.lookup("#boutonEcouter").queryAs(Button.class);
        assertThat(ecouter.isDisabled()).as("rien à écouter sans sélection").isTrue();

        robot.interact(() -> especes.getSelectionModel().select(0));
        robot.interact(() -> observations.getSelectionModel().select(0));
        assertThat(ecouter.isDisabled()).isFalse();
        robot.interact(ecouter::fire);

        // Ouvre la vue audio sur TOUTE l'espèce (source ParEspece), focalisée sur la détection cliquée.
        ArgumentCaptor<SourceObservations> source = ArgumentCaptor.forClass(SourceObservations.class);
        verify(ouvrirAudio).ouvrir(source.capture(), eq(7L));
        assertThat(source.getValue()).isInstanceOfSatisfying(SourceObservations.ParEspece.class, espece -> {
            assertThat(espece.idUtilisateur()).isEqualTo("u-1");
            assertThat(espece.codeEspece()).isEqualTo("Pippip");
            assertThat(espece.statut()).as("aucun filtre de statut actif").isNull();
        });
    }

    @Test
    @DisplayName("#1794 : double-clic sur une observation ouvre la fiche de l'espèce (ni écoute ni passage)")
    void double_clic_observation_ouvre_la_fiche(FxRobot robot) {
        TableView<?> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        robot.interact(() -> especes.getSelectionModel().select(0)); // charge une observation (Pipistrelle commune)

        // Double-clic sur la ligne d'observation. On type le nœud en Node pour lever l'ambiguïté entre les
        // surcharges doubleClickOn(Matcher) et doubleClickOn(Predicate).
        doubleCliquerLigne(robot, "#tableObservations", 0);

        // Le double-clic ouvre la fiche de l'espèce sélectionnée (#1794). L'écoute reste sur le bouton
        // « Écouter » et l'ouverture du passage sur son action : aucun des deux n'est déclenché ici.
        assertThat(urlsFiche)
                .containsExactly(
                        "https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/pipistrelle-commune/");
        verify(ouvrirAudio, never()).ouvrir(any(), any());
        verify(ouvrirPassage, never()).ouvrir(any(), any());
    }

    /// Double-clic **déterministe** sur la ligne d'index `index` de `idTable`.
    ///
    /// Deux fragilités sont évitées ici. Les `TableRow` sont **recyclés** : leur ordre dans le graphe de
    /// scène ne suit pas celui des lignes affichées, donc la première `.table-row-cell` venue est souvent
    /// une ligne **vide**. Et en headless, `doubleClickOn(Node)` vise le **centre du nœud en coordonnées
    /// écran** : il rate sa cible quand la mise en page n'est pas encore stabilisée. On cible donc la ligne
    /// par son **index réel** et on lui envoie l'événement directement, ce qui exerce le même gestionnaire
    /// de production (`DoubleClicLigne`) sans dépendre du placement.
    private static void doubleCliquerLigne(FxRobot robot, String idTable, int index) {
        Node ligne = robot.lookup(idTable).lookup(".table-row-cell").queryAll().stream()
                .map(noeud -> (TableRow<?>) noeud)
                .filter(rangee -> !rangee.isEmpty() && rangee.getIndex() == index)
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune ligne d'index " + index + " dans " + idTable));
        robot.interact(() -> ligne.fireEvent(new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                MouseButton.PRIMARY,
                2,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                null)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("#1837 : double-clic sur une espèce sans fiche : rien ne s'ouvre, et le bandeau dit pourquoi")
    void double_clic_espece_sans_fiche_affiche_le_motif(FxRobot robot) {
        Node bandeau = robot.lookup("#bandeauRetour").query();
        assertThat(bandeau.isVisible()).as("aucun retour au départ").isFalse();

        // « Bruit » : pseudo-taxon, ni code PNA ni nom latin, donc aucune fiche possible nulle part.
        when(service.observationsAnalyse(anyString())).thenReturn(List.of(obsSansFiche("noise", "Bruit")));
        robot.interact(controleur::rafraichirAuRetour);
        WaitForAsyncUtils.waitForFxEvents(); // la ligne doit être remise en page avant d'être cliquée

        doubleCliquerLigne(robot, "#tableEspeces", 0);

        assertThat(urlsFiche)
                .as("un pseudo-taxon n'a pas de fiche : rien ne doit s'ouvrir")
                .isEmpty();
        assertThat(bandeau.isVisible())
                .as("le geste ne doit plus rester muet : sans retour, il passe pour cassé")
                .isTrue();
        assertThat(robot.lookup("#lblRetour").queryAs(Label.class).getText())
                .as("le motif nomme le taxon tel que l'utilisateur le lit dans la table")
                .contains("Aucune fiche disponible pour « Bruit »");
        assertThat(bandeau.getStyleClass())
                .as("action refusée faute de cible : guidage, pas échec technique")
                .contains("retour-info");
    }

    @Test
    @DisplayName("#1794 : double-clic sur une espèce ouvre sa fiche (même cible que le menu contextuel)")
    void double_clic_espece_ouvre_la_fiche(FxRobot robot) {
        // Première espèce = Pipistrelle commune, chiroptère à fiche PNA.
        doubleCliquerLigne(robot, "#tableEspeces", 0);

        assertThat(urlsFiche)
                .containsExactly(
                        "https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/pipistrelle-commune/");
    }

    @Test
    @DisplayName("#1795 : la table des observations propose « Fiche de l'espèce » au clic droit")
    void fiche_au_clic_droit_des_observations(FxRobot robot) {
        TableView<?> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        robot.interact(() -> especes.getSelectionModel().select(0)); // charge les observations (Pipistrelle commune)
        WaitForAsyncUtils.waitForFxEvents();

        TableView<?> observations = robot.lookup("#tableObservations").queryAs(TableView.class);
        MenuItem fiche = observations.getContextMenu().getItems().stream()
                .filter(i -> i.getText() != null && i.getText().startsWith("Fiche de l'espèce"))
                .findFirst()
                .orElseThrow();
        assertThat(fiche.getText()).isEqualTo("Fiche de l'espèce (Pipistrelle commune)");
        assertThat(fiche.isDisable()).isFalse();

        robot.interact(fiche::fire);
        assertThat(urlsFiche)
                .containsExactly(
                        "https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/pipistrelle-commune/");
    }

    @Test
    @DisplayName("#1796 : le détail des observations propose « Écouter » et « Ouvrir le passage » au clic droit")
    void menu_de_ligne_des_observations(FxRobot robot) {
        TableView<?> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        robot.interact(() -> especes.getSelectionModel().select(0)); // charge les observations
        WaitForAsyncUtils.waitForFxEvents();

        TableView<?> observations = robot.lookup("#tableObservations").queryAs(TableView.class);
        robot.interact(() -> observations.getSelectionModel().select(0));
        var items = observations.getContextMenu().getItems();
        assertThat(items.get(0).getText()).isEqualTo("Écouter");
        assertThat(items.get(1).getText()).isEqualTo("Ouvrir le passage");

        robot.interact(() -> items.get(1).fire()); // « Ouvrir le passage »
        verify(ouvrirPassage).ouvrir(eq(42L), any(ContexteSite.class));
    }

    @Test
    @DisplayName("La bascule « 🗺️ Carte » affiche la carte de répartition à la place des tables")
    void bascule_carte_affiche_la_carte_de_repartition(FxRobot robot) {
        TableView<?> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        StackPane zoneCarte = robot.lookup("#zoneCarte").queryAs(StackPane.class);
        Button boutonCarte = robot.lookup("#boutonCarte").queryAs(Button.class);
        assertThat(zoneCarte.isVisible()).as("carte masquée au départ").isFalse();
        assertThat(especes.isVisible()).isTrue();

        robot.interact(boutonCarte::fire);

        assertThat(zoneCarte.isVisible()).as("la carte remplace les tables").isTrue();
        assertThat(especes.isVisible()).isFalse();
        assertThat(boutonCarte.getText()).contains("Tableau");
        assertThat(robot.lookup(".carte-legende").tryQuery())
                .as("la légende de richesse est superposée")
                .isPresent();

        robot.interact(boutonCarte::fire);

        assertThat(zoneCarte.isVisible()).isFalse();
        assertThat(especes.isVisible()).isTrue();
        assertThat(boutonCarte.getText()).contains("Carte");
    }

    @Test
    @DisplayName("#537 : ajouter un filtre « Statut » via la barre à puces filtre l'inventaire côté client")
    void filtre_statut_via_la_barre_a_puces(FxRobot robot) {
        TableView<?> especes = robot.lookup("#tableEspeces").queryAs(TableView.class);
        assertThat(especes.getItems())
                .as("l'espèce validée est affichée au départ")
                .hasSize(1);

        // Ouvrir le menu « + Filtre » et ajouter la puce Statut (l'item porte le libellé du critère).
        MenuButton menuAjout = robot.lookup("#menuAjoutFiltre").queryAs(MenuButton.class);
        MenuItem itemStatut = menuAjout.getItems().stream()
                .filter(item -> "Statut".equals(item.getText()))
                .findFirst()
                .orElseThrow();
        robot.interact(itemStatut::fire);
        WaitForAsyncUtils.waitForFxEvents();

        // Choisir « Non touchée » dans la liste de la puce : la seule observation (validée) est écartée →
        // l'inventaire se vide, ce qui prouve que la puce filtre bien la table côté client.
        FlowPane puces = robot.lookup("#pucesFiltres").queryAs(FlowPane.class);
        @SuppressWarnings("unchecked")
        ComboBox<StatutObservation> choixStatut = (ComboBox<StatutObservation>)
                robot.from(puces).lookup(".combo-box").queryAs(ComboBox.class);
        robot.interact(() -> choixStatut.setValue(StatutObservation.NON_TOUCHEE));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(especes.getItems())
                .as("le filtre statut « Non touchée » écarte l'observation validée")
                .isEmpty();
    }

    @Test
    @DisplayName("#623 : les vues enregistrées s'affichent en onglets, avec le bouton « + Vue »")
    void onglets_de_vues_affiche_les_vues_et_le_bouton_nouvelle(FxRobot robot) {
        FlowPane onglets = robot.lookup("#barreOnglets").queryAs(FlowPane.class);

        // La vue persistée « Validées 2026 » (fournie par le dépôt) apparaît comme onglet.
        assertThat(robot.from(onglets).lookup(".onglet-vue-nom").queryAllAs(Label.class))
                .extracting(Label::getText)
                .contains("Validées 2026");
        // Le bouton « + Vue » clôt la barre : il enregistre les filtres courants comme une nouvelle vue.
        assertThat(robot.from(onglets)
                        .lookup(".onglet-vue-nouvelle")
                        .queryAs(Button.class)
                        .getText())
                .isEqualTo("+ Vue");
    }

    @Test
    @DisplayName("#476 : appliquer(descripteur, carte) rejoue les filtres partagés et ouvre la carte")
    void appliquer_transporte_les_filtres_partages_et_ouvre_la_carte(FxRobot robot) {
        StackPane zoneCarte = robot.lookup("#zoneCarte").queryAs(StackPane.class);
        Button boutonCarte = robot.lookup("#boutonCarte").queryAs(Button.class);
        TextField recherche = robot.lookup("#champRecherche").queryAs(TextField.class);
        FlowPane puces = robot.lookup("#pucesFiltres").queryAs(FlowPane.class);
        assertThat(zoneCarte.isVisible()).as("carte masquée au départ").isFalse();

        // Descripteur tel que transporté depuis l'audio : recherche texte + critère partagé « statut » et un
        // critère « proba » propre à l'audio, que l'analyse doit ignorer (catalogue différent).
        DescripteurFiltre descripteur = new DescripteurFiltre(
                "Pippip",
                List.of(
                        new DescripteurCritere("statut", List.of("VALIDEE")),
                        new DescripteurCritere("proba", List.of("0.5"))));

        robot.interact(() -> controleur.appliquer(descripteur, true));
        WaitForAsyncUtils.waitForFxEvents();

        // La carte s'ouvre et le libellé du bouton suit la bascule **programmatique** (#476).
        assertThat(zoneCarte.isVisible()).as("la carte s'ouvre").isTrue();
        assertThat(boutonCarte.getText()).contains("Tableau");
        // La recherche texte est transportée telle quelle.
        assertThat(recherche.getText()).isEqualTo("Pippip");
        // Seul le critère partagé « statut » est rejoué (une puce à combo) ; « proba » (audio) est ignoré.
        assertThat(robot.from(puces).lookup(".combo-box").queryAllAs(ComboBox.class))
                .as("seul le critère partagé est rejoué, le critère propre à l'audio est ignoré")
                .hasSize(1);
    }

    @Test
    @DisplayName("#800 : le combo de regroupement (sans étiquette visible) expose un libellé accessible")
    void combo_regroupement_a_un_libelle_accessible(FxRobot robot) {
        assertThat(robot.lookup("#choixRegroupement").queryAs(ComboBox.class).getAccessibleText())
                .isEqualTo("Regrouper par espèce ou par carré");
    }

    @Test
    @DisplayName("#1208 : l'overlay d'occupation est en place, masqué une fois le chargement terminé")
    void overlay_occupation_masque_apres_chargement(FxRobot robot) {
        Node voile = robot.lookup(".occupation-voile").query();

        assertThat(voile).as("overlay d'occupation superposé à l'écran").isNotNull();
        assertThat(voile.isVisible())
                .as("chargement terminé (exécuteur synchrone) : overlay masqué")
                .isFalse();
    }

    @Test
    @DisplayName("#1431 : « Exporter… » écrit l'inventaire dans le fichier désigné, et propose un nom")
    void export_ecrit_dans_le_fichier_designe(FxRobot robot) {
        Path destination = Path.of("/tmp/mon-inventaire.csv");
        choixExport = Optional.of(destination);

        robot.interact(() -> robot.lookup("#boutonExporter").queryButton().fire());

        // Le nom proposé n'est pas un détail : c'est ce que l'utilisateur va accepter neuf fois sur dix.
        assertThat(nomsProposes).containsExactly("inventaire-especes.csv");
        assertThat(filtres)
                .singleElement()
                .satisfies(filtre -> assertThat(filtre.motif()).isEqualTo("*.csv"));
        // Ce qu'on exporte, c'est l'inventaire AFFICHÉ (la liste filtrée), pas la base entière.
        verify(service).exporterEspeces(eq(destination), any());
    }

    @Test
    @DisplayName("#1431 : « Exporter… » annulé : aucun fichier n'est écrit")
    void export_annule_n_ecrit_rien(FxRobot robot) {
        choixExport = Optional.empty();

        robot.interact(() -> robot.lookup("#boutonExporter").queryButton().fire());

        verify(service, never()).exporterEspeces(any(), any());
        verify(service, never()).exporterCarres(any(), any());
    }
}
