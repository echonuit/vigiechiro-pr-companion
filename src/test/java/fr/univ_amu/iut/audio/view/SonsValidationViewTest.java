package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.util.Modules;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.DiscussionValidateur;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.VueSauvegardee;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.DescripteurFiltre;
import fr.univ_amu.iut.commun.view.NavigationDeTestModule;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MarquageDouteux;
import fr.univ_amu.iut.validation.model.PlageNuitPassage;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.SaisieCertitude;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.kordamp.ikonli.javafx.FontIcon;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX de la **vue audio unifiée** (`SonsValidation.fxml`) ouverte sur la source
/// `References` : chargement du FXML via Guice (services mockés), câblage table / sélection / détail /
/// écoute, bascule de référence, et adaptation à la source (menu « Exporter la bibliothèque » visible,
/// actions de passage masquées ; colonnes de contexte visibles car la source n'est pas un seul passage).
/// Services métier mockés ; seule une base `app_setting` jetable sert aux préférences de lecture (#1006).
@ExtendWith(ApplicationExtension.class)
class SonsValidationViewTest {

    private ServiceValidation service;
    private ProjectionsAudioDao projections;
    private RevueEnLot revueEnLot;
    private SaisieCertitude saisieCertitude;
    private DepotVues depotVues;
    private OuvrirAnalyse ouvrirAnalyse;
    private SonsValidationController controleur;
    private final List<String> urlsFiche = new ArrayList<>();

    /// Base jetable pour les seules **préférences** (`app_setting`) : les options de lecture du menu ☰
    /// (#1006) passent par `ReglagesReactifs`, qui lit/écrit ce réglage. Le reste de la vue reste mocké.
    @TempDir
    Path dossierReglages;

    private static LigneObservationAudio ligne(
            long id,
            long seq,
            String tadarida,
            String observateur,
            String nomEspece,
            String nomTadarida,
            Certitude certitude) {
        return new LigneObservationAudio(
                id,
                seq,
                7L,
                1,
                "2026-06-20",
                "640380",
                "A1",
                "Mon site",
                tadarida,
                0.9,
                observateur,
                0.95,
                StatutObservation.VALIDEE,
                true,
                "beau cri",
                45,
                nomEspece,
                nomTadarida,
                null,
                "Chiroptères",
                "PaRec_" + seq + "_000.wav",
                0.20,
                0.32,
                // Heure de capture : 22:00 + n° de séquence (seq 10 → 22:10, seq 11 → 22:11), pour vérifier
                // l'affichage de la colonne « Heure ».
                LocalDateTime.of(2026, 4, 22, 22, 0).plusMinutes(seq),
                false,
                certitude,
                null,
                null,
                null,
                0);
    }

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceValidation.class);
        projections = mock(ProjectionsAudioDao.class);
        revueEnLot = mock(RevueEnLot.class);
        saisieCertitude = mock(SaisieCertitude.class);
        ServiceBibliotheque bibliotheque = mock(ServiceBibliotheque.class);
        when(service.taxonsDisponibles())
                .thenReturn(List.of(new Taxon("Nyclei", "Nyctalus leisleri", "Noctule de Leisler", 1L)));
        when(projections.lignesAudioReferences("u-1"))
                .thenReturn(List.of(
                        ligne(1, 10, "Pippip", "Pippip", "Pipistrelle commune", "Pipistrelle commune", null),
                        ligne(
                                2,
                                11,
                                "Nyclei",
                                "Nyclei",
                                "Noctule de Leisler",
                                "Noctule de Leisler",
                                Certitude.PROBABLE)));
        when(service.cheminAudio(anyLong())).thenReturn(Optional.empty());
        when(service.cheminAudio(10L)).thenReturn(Optional.of(Path.of("/ws/transformes/p.wav")));
        depotVues = mock(DepotVues.class);
        ouvrirAnalyse = mock(OuvrirAnalyse.class);
        // Une vue déjà enregistrée pour cet écran : elle doit apparaître comme onglet (nom seul lu à l'init).
        when(depotVues.findByFeature("audio"))
                .thenReturn(List.of(new VueSauvegardee(1L, "audio", "À revoir", "{\"texte\":\"\",\"criteres\":[]}")));

        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Provides
                    AudioViewModel viewModel() {
                        return new AudioViewModel(
                                service,
                                projections,
                                mock(PlageNuitPassage.class),
                                mock(ValidationManuelle.class),
                                mock(MarquageDouteux.class),
                                saisieCertitude,
                                revueEnLot,
                                bibliotheque,
                                mock(ServiceDisponibiliteAudio.class),
                                p -> true,
                                mock(DiscussionValidateur.class));
                    }

                    @Provides
                    DepotVues depotVues() {
                        return depotVues;
                    }

                    @Provides
                    ImportVigieChiroViewModel importVigieChiro() {
                        return new ImportVigieChiroViewModel(Optional.empty());
                    }

                    @Provides
                    fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel publicationCorrections() {
                        return new fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel(Optional.empty());
                    }

                    // « Fiche de l'espèce » (#847) : navigateur factice qui enregistre l'URL ouverte.
                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        return urlsFiche::add;
                    }

                    // « Ouvrir les données sur Vigie-Chiro » (#1124) : portail factice (aucun lien posé).
                    @Provides
                    PortailVigieChiro portail() {
                        return mock(PortailVigieChiro.class);
                    }

                    // Réglages réactifs (#1006) : les options de lecture (auto-lecture / boucle) du menu ☰ y
                    // sont liées. Base app_setting jetable et migrée, sinon la lecture du réglage échoue.
                    @Provides
                    ReglagesReactifs reglagesReactifs() {
                        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossierReglages));
                        new MigrationSchema(source).migrer();
                        return new ReglagesReactifs(new Reglages(new ReglagesDao(source)));
                    }
                },
                // Le socle de navigation est neutre par défaut (OptionalBinder VIDE simulé par un no-op côté
                // NavigationDeTestModule, #1087) ; on remplace ce setBinding par un mock pour vérifier l'appel
                // « Voir sur la carte » (#476). Même slot OptionalBinder que la base : Modules.override le
                // remplace proprement, sans binding OuvrirAnalyse en double.
                Modules.override(new NavigationDeTestModule()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        OptionalBinder.newOptionalBinder(binder(), OuvrirAnalyse.class)
                                .setBinding()
                                .toInstance(ouvrirAnalyse);
                    }
                }));
        FXMLLoader loader = new FXMLLoader(SonsValidationController.class.getResource("SonsValidation.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        controleur.ouvrirSur(new SourceObservations.References("u-1"));
        stage.setScene(new Scene(vue, 1000, 700));
        stage.show();
    }

    @Test
    @DisplayName("#160 : le bouton « douteux » est actif sur une observation, libellé « Marquer douteux »")
    void bouton_douteux_actif_sur_observation(FxRobot robot) {
        Button btnDouteux = robot.lookup("#btnDouteux").queryAs(Button.class);
        robot.interact(() -> robot.lookup("#tableObservations")
                .queryAs(TableView.class)
                .getSelectionModel()
                .select(0));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(btnDouteux.isDisabled())
                .as("actif dès qu'une observation (idObservation non nul) est sélectionnée")
                .isFalse();
        assertThat(btnDouteux.getText()).isEqualTo("Marquer douteux");
    }

    @Test
    @DisplayName("#160 : le filtre « Douteux » (puce) écarte les observations non douteuses")
    void filtre_douteux_ecarte_les_non_douteux(FxRobot robot) {
        MenuButton menuAjout = robot.lookup("#menuAjoutFiltre").queryAs(MenuButton.class);
        robot.interact(() -> itemParLibelle(menuAjout, "Douteux").fire());
        WaitForAsyncUtils.waitForFxEvents();

        // Les références de test ne sont pas douteuses → la puce « Douteux » vide la table.
        assertThat(robot.lookup("#tableObservations").queryAs(TableView.class).getItems())
                .as("aucune observation douteuse dans les données de test")
                .isEmpty();
    }

    @Test
    @DisplayName("La table liste les références ; le résumé de statut compte + l'avancement (sans bandeau de titre)")
    void affiche_table_et_resume(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        assertThat(table.getItems()).hasSize(2);

        // Plus de bandeau de titre en tête de vue (déporté : nom d'écran dans le fil d'Ariane).
        assertThat(robot.lookup("#lblResume").tryQuery()).isEmpty();

        // Barre de statut 3 zones : gauche = identité de la source (#1025), centre = total, droite =
        // avancement (les 2 lignes sont VALIDEE). Source « Sons de référence » → pas de passage ciblé, donc
        // la gauche reprend l'intitulé de la source.
        ZonesStatut zones = controleur.zonesStatutProperty().get();
        assertThat(zones.gauche()).isEqualTo("Sons de référence");
        assertThat(zones.centre()).isEqualTo("2 observation(s)");
        assertThat(zones.droite()).isEqualTo("2 / 2 revues");
    }

    @Test
    @DisplayName("#476 : « Voir sur la carte » rouvre l'analyse sur la carte avec les filtres courants")
    void voir_sur_la_carte_ouvre_l_analyse_avec_carte(FxRobot robot) {
        MenuButton menuActions = robot.lookup("#menuActions").queryAs(MenuButton.class);
        MenuItem voirCarte = itemParLibelle(menuActions, "Voir sur la carte");

        robot.interact(voirCarte::fire);

        // Le clic rouvre l'analyse en demandant la carte (afficherCarte=true), avec un descripteur des
        // filtres courants (jamais null : la barre décrit au moins une recherche vide).
        verify(ouvrirAnalyse).ouvrir(any(DescripteurFiltre.class), eq(true));
    }

    @Test
    @DisplayName("#847 : « Fiche de l'espèce » ouvre la fiche PNA de la proposition Tadarida sélectionnée")
    void fiche_espece_ouvre_la_fiche_de_la_proposition_tadarida(FxRobot robot) {
        // Première ligne = proposition Tadarida « Pippip » (Pipistrelle commune), un chiroptère à fiche PNA.
        robot.interact(() -> robot.lookup("#tableObservations")
                .queryAs(TableView.class)
                .getSelectionModel()
                .select(0));
        WaitForAsyncUtils.waitForFxEvents();

        MenuButton menuActions = robot.lookup("#menuActions").queryAs(MenuButton.class);
        MenuItem fiche = itemParLibelle(menuActions, "Fiche de l'espèce (Pipistrelle commune)");
        assertThat(fiche.isDisable()).isFalse();

        robot.interact(fiche::fire);

        assertThat(urlsFiche)
                .containsExactly(
                        "https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/pipistrelle-commune/");
    }

    @Test
    @DisplayName("#1794 : double-clic sur une observation ouvre la fiche de la proposition Tadarida")
    void double_clic_observation_ouvre_la_fiche(FxRobot robot) {
        // Première ligne = « Pippip » (Pipistrelle commune), chiroptère à fiche PNA.
        Node ligne =
                robot.lookup("#tableObservations").lookup(".table-row-cell").query();
        robot.doubleClickOn(ligne);

        assertThat(urlsFiche)
                .containsExactly(
                        "https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/pipistrelle-commune/");
    }

    @Test
    @DisplayName("#1795 : « Fiche de l'espèce » est aussi au clic droit de la table (en plus du ☰)")
    void fiche_au_clic_droit_des_observations(FxRobot robot) {
        // Première ligne = « Pippip » (Pipistrelle commune), chiroptère à fiche PNA.
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        robot.interact(() -> table.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        MenuItem fiche = table.getContextMenu().getItems().stream()
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
    @DisplayName("#1796 : « Ouvrir le passage » figure au clic droit de la table, actif sur une sélection")
    void menu_de_ligne_a_ouvrir_le_passage(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        robot.interact(() -> table.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        MenuItem ouvrir = table.getContextMenu().getItems().get(0);
        assertThat(ouvrir.getText()).isEqualTo("Ouvrir le passage");
        assertThat(ouvrir.isDisable())
                .as("actif quand une ligne est sélectionnée")
                .isFalse();
    }

    @Test
    @DisplayName("#1797 : le sous-menu « Validation » au clic droit valide la sélection")
    void menu_validation_valide_la_selection(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        robot.interact(() -> table.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        Menu validation = (Menu) table.getContextMenu().getItems().stream()
                .filter(i -> "Validation".equals(i.getText()))
                .findFirst()
                .orElseThrow();
        assertThat(validation.getItems())
                .extracting(MenuItem::getText)
                .contains("Valider", "Corriger", "Certitude", "Marquer référence", "Marquer douteux");

        MenuItem valider = validation.getItems().stream()
                .filter(i -> "Valider".equals(i.getText()))
                .findFirst()
                .orElseThrow();
        robot.interact(valider::fire);
        verify(service).validerSelonMode(eq(1L), any());
    }

    @Test
    @DisplayName("Les puces de filtres s'alignent horizontalement (une seule rangée, pas empilées)")
    void puces_filtres_alignees_horizontalement(FxRobot robot) {
        MenuButton menuAjout = robot.lookup("#menuAjoutFiltre").queryAs(MenuButton.class);
        robot.interact(() -> itemParLibelle(menuAjout, "Statut").fire());
        robot.interact(() -> itemParLibelle(menuAjout, "Groupe").fire());
        WaitForAsyncUtils.waitForFxEvents();

        FlowPane puces = robot.lookup("#pucesFiltres").queryAs(FlowPane.class);
        assertThat(puces.getChildren()).hasSize(2);
        // Les deux puces partagent la même ordonnée : côte à côte, et non empilées verticalement (le
        // FlowPane occupe toute la largeur de la rangée grâce à hgrow, donc n'enveloppe pas dès 400px).
        double y0 = puces.getChildren().get(0).getBoundsInParent().getMinY();
        double y1 = puces.getChildren().get(1).getBoundsInParent().getMinY();
        assertThat(y1).isCloseTo(y0, within(1.0));
    }

    @Test
    @DisplayName("Le filtre « Heure » centre verticalement son éditeur (« de »/« à » alignés avec les listes)")
    void filtre_heure_editeur_centre_verticalement(FxRobot robot) {
        MenuButton menuAjout = robot.lookup("#menuAjoutFiltre").queryAs(MenuButton.class);
        robot.interact(() -> itemParLibelle(menuAjout, "Heure").fire());
        WaitForAsyncUtils.waitForFxEvents();

        FlowPane puces = robot.lookup("#pucesFiltres").queryAs(FlowPane.class);
        HBox puce = (HBox) puces.getChildren().get(0);
        // Structure de la puce : [Label « Heure », éditeur (« de » / liste / « à » / liste), bouton ✕].
        HBox editeur = (HBox) puce.getChildren().get(1);
        assertThat(editeur.getAlignment())
                .as("les libellés « de »/« à » doivent être centrés avec les listes, pas collés en haut")
                .isEqualTo(Pos.CENTER_LEFT);
    }

    @Test
    @DisplayName("Proposition Tadarida et Votre taxon affichent le vernaculaire ; Proba la probabilité")
    void affiche_nom_vernaculaire_et_proba(FxRobot robot) {
        // Les deux lignes sont revues (taxon observateur renseigné) : « Votre taxon » montre le
        // vernaculaire retenu, comme « Proposition Tadarida ». Plus de colonne « Espèce » redondante.
        assertThat(colonne(robot, "Proposition Tadarida").getCellData(0)).isEqualTo("Pipistrelle commune");
        assertThat(colonne(robot, "Votre taxon").getCellData(0)).isEqualTo("Pipistrelle commune");
        assertThat(colonne(robot, "Votre taxon").getCellData(1)).isEqualTo("Noctule de Leisler");
        // Proba = probabilité Tadarida formatée (0.9 → « 90 % »).
        assertThat(colonne(robot, "Proba.").getCellData(0)).isEqualTo("90 %");
    }

    @Test
    @DisplayName("#1139 : la colonne « Certitude » est vide par défaut (tiret) et affiche le libellé saisi")
    void colonne_certitude_vide_par_defaut(FxRobot robot) {
        assertThat(colonne(robot, "Certitude").getCellData(0))
                .as("jamais préremplie : vide tant que l'observateur n'a pas déclaré")
                .isEqualTo("—");
        assertThat(colonne(robot, "Certitude").getCellData(1)).isEqualTo("Probable");
    }

    @Test
    @DisplayName("#1139 : le menu Certitude est bloqué sans sélection, puis pose la certitude choisie"
            + " (unitaire et lot)")
    void menu_certitude_pose_sur_la_selection(FxRobot robot) {
        MenuButton menu = robot.lookup("#menuCertitude").queryAs(MenuButton.class);
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        assertThat(menu.isDisabled())
                .as("sans sélection : bloqué (comme Référence/Douteux)")
                .isTrue();

        robot.interact(() -> table.getSelectionModel().select(0));
        assertThat(menu.isDisabled()).isFalse();
        robot.interact(() -> itemParLibelle(menu, "Sûr").fire());
        WaitForAsyncUtils.waitForFxEvents();
        verify(saisieCertitude).poser(1L, Certitude.SUR);

        robot.interact(() -> table.getSelectionModel().selectIndices(0, 1));
        robot.interact(() -> itemParLibelle(menu, "Possible").fire());
        WaitForAsyncUtils.waitForFxEvents();
        verify(saisieCertitude).poser(List.of(1L, 2L), Certitude.POSSIBLE);
    }

    @Test
    @DisplayName("#1139 : les touches 1/2/3 déclarent la certitude de la sélection (revue au clavier)")
    void raccourci_clavier_certitude(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        robot.interact(() -> {
            table.getSelectionModel().select(0);
            table.requestFocus();
        });

        robot.push(KeyCode.DIGIT2);
        WaitForAsyncUtils.waitForFxEvents();

        verify(saisieCertitude).poser(1L, Certitude.PROBABLE);
    }

    @Test
    @DisplayName("La colonne « Fichier » affiche le nom de fichier de la séquence")
    void affiche_le_nom_de_fichier(FxRobot robot) {
        assertThat(colonne(robot, "Fichier").getCellData(0)).isEqualTo("PaRec_10_000.wav");
        assertThat(colonne(robot, "Fichier").getCellData(1)).isEqualTo("PaRec_11_000.wav");
    }

    @Test
    @DisplayName("#530 : la colonne Heure porte l'instant de capture et est triable (tri chronologique)")
    void colonne_heure_porte_l_instant(FxRobot robot) {
        // Valeur = l'instant complet (LocalDateTime), pour un tri chronologique correct à cheval sur minuit.
        assertThat(colonne(robot, "Heure").getCellData(0)).isEqualTo(LocalDateTime.of(2026, 4, 22, 22, 10));
        assertThat(colonne(robot, "Heure").getCellData(1)).isEqualTo(LocalDateTime.of(2026, 4, 22, 22, 11));
        assertThat(colonne(robot, "Heure").isSortable()).isTrue();
    }

    @Test
    @DisplayName("Colonnes Date, Fréquence, Début et Durée (position et durée réelles du cri)")
    void affiche_date_frequence_debut_duree(FxRobot robot) {
        assertThat(colonne(robot, "Date").getCellData(0)).isEqualTo("2026-06-20");
        assertThat(colonne(robot, "Fréquence").getCellData(0)).isEqualTo("45 kHz");
        // FME / fréquence terminale (#500) : « — » tant que le cri n'a pas été sélectionné (calcul
        // paresseux par l'audio-view ; aucun audio ne se charge en headless).
        assertThat(colonne(robot, "FME").getCellData(0)).isEqualTo("—");
        assertThat(colonne(robot, "Fréq. term.").getCellData(0)).isEqualTo("—");
        // debutS 0.20 s est déjà en secondes réelles → position affichée telle quelle.
        assertThat(colonne(robot, "Début").getCellData(0)).isEqualTo("0,20 s");
        // Bornes réelles 0.20→0.32 s → durée 0,12 s = 120 ms (< 1 s, unité adaptative).
        assertThat(colonne(robot, "Durée").getCellData(0)).isEqualTo("120 ms");
    }

    @Test
    @DisplayName("Indicateurs référence / commentaire : en-tête et cellules en icônes Ikonli colorées")
    void indicateurs_en_icones(FxRobot robot) {
        TableColumn<?, ?> ref = colonneParId(robot, "colReference");
        TableColumn<?, ?> commentaire = colonneParId(robot, "colCommentaire");
        // En-tête : pas de texte (emoji retiré), une icône colorée à la place.
        assertThat(ref.getText()).isEmpty();
        assertThat(ref.getGraphic()).isInstanceOf(FontIcon.class);
        assertThat(ref.getGraphic().getStyleClass()).contains("icone-reference");
        assertThat(commentaire.getGraphic().getStyleClass()).contains("icone-commentaire");
        // #794 : l'en-tête icône seule expose un libellé accessible (sinon un lecteur d'écran n'annonce
        // que le glyphe).
        assertThat(ref.getGraphic().getAccessibleText()).isEqualTo("Référence");
        assertThat(commentaire.getGraphic().getAccessibleText()).isEqualTo("Commentaire");
        // Les deux lignes de la fixture sont en référence et commentées → icônes aussi en cellule
        // (donc plus d'occurrences que le seul en-tête).
        assertThat(robot.lookup(".icone-reference").queryAll()).hasSizeGreaterThan(1);
        assertThat(robot.lookup(".icone-commentaire").queryAll()).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("#800 : le combo « mode de revue » (sans étiquette visible) expose un libellé accessible")
    void combo_mode_a_un_libelle_accessible(FxRobot robot) {
        assertThat(robot.lookup("#choixMode").queryAs(ComboBox.class).getAccessibleText())
                .isEqualTo("Mode de revue");
    }

    @Test
    @DisplayName("Les colonnes sont triables : trier « Fichier » en décroissant réordonne les lignes")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void colonnes_triables(FxRobot robot) {
        TableView table = robot.lookup("#tableObservations").queryAs(TableView.class);
        TableColumn fichier = colonne(robot, "Fichier");

        // Sans l'enveloppe SortedList, trier une table alimentée par une FilteredList ne réordonne rien.
        robot.interact(() -> {
            table.getSortOrder().setAll(fichier);
            fichier.setSortType(TableColumn.SortType.DESCENDING);
            table.sort();
        });

        assertThat(colonne(robot, "Fichier").getCellData(0)).isEqualTo("PaRec_11_000.wav");
        assertThat(colonne(robot, "Fichier").getCellData(1)).isEqualTo("PaRec_10_000.wav");
    }

    @Test
    @DisplayName("Sélection d'un cri : la fenêtre [début, fin] réelle est surlignée sur l'AudioView (#482)")
    void selection_surligne_la_fenetre_du_cri(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        AudioView audio = robot.lookup("#audioView").queryAs(AudioView.class);

        // Bornes du cri déjà en secondes réelles (0.20–0.32 s) → surlignées telles quelles sur l'axe réel.
        robot.interact(() -> table.getSelectionModel().select(0));
        double[] fenetre = audio.getHighlightedWindow();
        assertThat(fenetre).hasSize(2);
        assertThat(fenetre[0]).isEqualTo(0.20, within(1e-9));
        assertThat(fenetre[1]).isEqualTo(0.32, within(1e-9));

        // Désélection → surlignage effacé.
        robot.interact(() -> table.getSelectionModel().clearSelection());
        assertThat(audio.getHighlightedWindow()).isNull();
    }

    @Test
    @DisplayName("Après un Valider, la ligne revue reste surlignée (sélection re-synchronisée VM → table)")
    void selection_reste_surlignee_apres_action(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        Button valider = robot.lookup("#btnValider").queryAs(Button.class);

        // Le rechargement post-action renvoie la même observation (id=1) mais **modifiée** (nouvelle
        // instance de record, non égale à l'ancienne) : sans resync, la table perdrait sa surbrillance.
        when(projections.lignesAudioReferences("u-1"))
                .thenReturn(List.of(
                        ligne(1, 10, "Pippip", "Nyclei", "Noctule de Leisler", "Pipistrelle commune", null),
                        ligne(2, 11, "Nyclei", "Nyclei", "Noctule de Leisler", "Noctule de Leisler", null)));

        robot.interact(() -> table.getSelectionModel().select(0)); // observation id=1
        robot.interact(valider::fire);

        Object surlignee = table.getSelectionModel().getSelectedItem();
        assertThat(surlignee).isNotNull();
        assertThat(((LigneObservationAudio) surlignee).idObservation()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Sélectionner une ligne charge l'écoute (AudioView) ; plus de ligne de détail dans le panneau")
    void selection_alimente_l_audio(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        AudioView audio = robot.lookup("#audioView").queryAs(AudioView.class);

        // La ligne de détail redondante avec la table a été retirée du panneau (déportée en barre de statut,
        // #495) : le détail reste calculé côté ViewModel (couvert par AudioViewModelTest).
        assertThat(robot.lookup("#lblDetail").tryQuery()).isEmpty();

        assertThat(audio.getAudioFile()).isNull();
        assertThat(audio.isNormalisation()).isTrue();
        robot.interact(() -> table.getSelectionModel().select(0));
        assertThat(audio.getAudioFile().toString()).endsWith("p.wav");
    }

    @Test
    @DisplayName("Le bouton de référence bascule l'archivage de l'observation sélectionnée")
    void basculer_reference(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        Button btnReference = robot.lookup("#btnReference").queryAs(Button.class);

        assertThat(btnReference.isDisabled()).isTrue(); // pas de sélection au départ
        robot.interact(() -> table.getSelectionModel().select(0));
        assertThat(btnReference.getText()).contains("Retirer la référence"); // la ligne est déjà référence

        robot.interact(btnReference::fire);
        verify(service).marquerReference(1L, false);
    }

    @Test
    @DisplayName("#479 : multi-sélection puis Valider délègue au LOT (RevueEnLot), pas à l'action unitaire")
    void valider_lot_sur_multi_selection(FxRobot robot) {
        when(revueEnLot.valider(List.of(1L, 2L))).thenReturn(2);
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        Button btnValider = robot.lookup("#btnValider").queryAs(Button.class);

        robot.interact(() -> table.getSelectionModel().selectIndices(0, 1));
        robot.interact(btnValider::fire);

        verify(revueEnLot).valider(List.of(1L, 2L));
        verify(service, never()).validerSelonMode(anyLong(), any());
    }

    @Test
    @DisplayName("#479 : multi-sélection puis Référence bascule tout le lot (retire si toutes déjà en référence)")
    void reference_lot_sur_multi_selection(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        Button btnReference = robot.lookup("#btnReference").queryAs(Button.class);

        robot.interact(() -> table.getSelectionModel().selectIndices(0, 1));
        robot.interact(btnReference::fire);

        // Les deux lignes sont déjà en référence → le lot les RETIRE toutes (marquer = false). Le passage par
        // RevueEnLot (et non l'action unitaire du service) prouve l'aiguillage vers le lot.
        verify(revueEnLot).marquerReference(List.of(1L, 2L), false);
    }

    @Test
    @DisplayName("Cliquer une case commentaire ouvre l'éditeur pré-rempli ; enregistrer appelle le service")
    void clic_commentaire_edite_via_service(FxRobot robot) {
        // La colonne commentaire est à droite : la faire défiler dans le viewport avant de cliquer.
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        robot.interact(() -> table.scrollToColumnIndex(15));

        // Clique l'icône commentaire d'une ligne de données (pas l'en-tête) → ouvre le popup d'édition.
        Node iconeCommentaire = robot.lookup(".table-cell .icone-commentaire").query();
        robot.clickOn(iconeCommentaire);

        TextArea zone = robot.lookup(".popup-commentaire .text-area").queryAs(TextArea.class);
        assertThat(zone.getText())
                .as("popup pré-rempli avec le commentaire de la ligne")
                .isEqualTo("beau cri");

        robot.interact(() -> zone.setText("commentaire modifié"));
        robot.clickOn(".bouton-enregistrer-commentaire");

        verify(service).commenter(anyLong(), eq("commentaire modifié"));
    }

    @Test
    @DisplayName("Un échec s'affiche dans le bandeau de retour (erreur) et la croix le ferme")
    void echec_affiche_puis_ferme_le_bandeau(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        Node bandeau = robot.lookup("#bandeauRetour").query();
        Label message = robot.lookup("#lblRetour").queryAs(Label.class);

        assertThat(bandeau.isVisible()).as("aucun retour au départ").isFalse();

        Button btnReference = robot.lookup("#btnReference").queryAs(Button.class);
        robot.interact(() -> table.getSelectionModel().select(0)); // ligne id=1, déjà référence
        doThrow(new RegleMetierException("Échec simulé")).when(service).marquerReference(1L, false);
        robot.interact(btnReference::fire);

        // Le retour d'erreur est visible et stylé erreur, indépendamment du placeholder d'état vide.
        assertThat(bandeau.isVisible()).isTrue();
        assertThat(message.getText()).contains("Échec simulé");
        assertThat(bandeau.getStyleClass()).contains("retour-erreur");

        // La croix ferme le bandeau (l'utilisateur l'a lu).
        Button btnFermer = robot.lookup("#btnFermerRetour").queryAs(Button.class);
        robot.interact(btnFermer::fire);
        assertThat(bandeau.isVisible())
                .as("le bandeau est masqué après fermeture")
                .isFalse();
    }

    @Test
    @DisplayName(
            "Ordre par défaut des colonnes (contexte, fichier, identification, indicateurs) et indicateurs non triables")
    void ordre_par_defaut_et_indicateurs_non_triables(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        // Les 17 premières colonnes par en-tête ; les 3 indicateurs (icônes, sans texte) par leur id.
        assertThat(table.getColumns().stream()
                        .limit(17)
                        .map(TableColumn::getText)
                        .toList())
                .containsExactly(
                        "Date",
                        "Heure",
                        "Passage",
                        "Carré",
                        "Point",
                        "Fichier",
                        "Proposition Tadarida",
                        "Proba.",
                        "Votre taxon",
                        "Certitude",
                        // Les TROIS avis se lisent de gauche à droite (#1417) : Tadarida propose, vous
                        // corrigez, l'expert du MNHN tranche. Le troisième arrivait déjà du serveur, et
                        // l'écran ne le montrait pas.
                        "Avis du validateur",
                        "Fréquence",
                        "FME",
                        "Fréq. term.",
                        "Début",
                        "Durée",
                        "Statut");
        assertThat(table.getColumns().get(17).getId()).isEqualTo("colReference");
        assertThat(table.getColumns().get(18).getId()).isEqualTo("colCommentaire");
        assertThat(table.getColumns().get(19).getId()).isEqualTo("colFil");
        // Colonnes-indicateurs : non triables (trier une icône est déroutant, cf. « colonne vide triable »).
        assertThat(colonneParId(robot, "colReference").isSortable()).isFalse();
        assertThat(colonneParId(robot, "colCommentaire").isSortable()).isFalse();
        // Le fil, lui, RESTE triable : c'est un compte, et remonter les détections qu'un validateur a
        // commentées est précisément ce qu'on veut pouvoir faire d'un clic.
        assertThat(colonneParId(robot, "colFil").isSortable()).isTrue();
    }

    @Test
    @DisplayName("Gestion des colonnes : item « Colonnes… » dans le menu contextuel (clic droit) et dans le ☰")
    void gestion_colonnes_accessible(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        assertThat(table.getContextMenu())
                .as("menu contextuel installé (clic droit)")
                .isNotNull();
        assertThat(table.getContextMenu().getItems())
                .anySatisfy(item -> assertThat(item.getText()).isEqualTo("Colonnes…"));

        MenuButton menu = robot.lookup("#menuActions").queryAs(MenuButton.class);
        assertThat(menu.getItems())
                .anySatisfy(item -> assertThat(item.getText()).isEqualTo("Colonnes…"));
    }

    @Test
    @DisplayName("Source References : menu « Exporter la bibliothèque » visible, actions passage masquées")
    void menu_adapte_a_la_source(FxRobot robot) {
        // Les MenuItem ne sont pas des Node : on passe par le MenuButton et ses items, repérés par leur
        // libellé (robuste à l'ordre / aux ajouts, comme « Voir sur la carte » en tête, #476).
        MenuButton menu = robot.lookup("#menuActions").queryAs(MenuButton.class);
        MenuItem importer = itemParLibelle(menu, "Importer un CSV Tadarida…");
        MenuItem inclureMode = itemParLibelle(menu, "Inclure le mode de validation à l'export _Vu");
        MenuItem exporterVu = itemParLibelle(menu, "Exporter _Vu…");
        MenuItem exporterBiblio = itemParLibelle(menu, "Exporter la bibliothèque…");

        assertThat(menu.isVisible()).isTrue();
        assertThat(exporterBiblio.isVisible()).isTrue();
        assertThat(importer.isVisible()).isFalse();
        assertThat(inclureMode.isVisible()).isFalse();
        assertThat(exporterVu.isVisible()).isFalse();
        // Source multi-passages : les colonnes de contexte restent visibles.
        assertThat(colonne(robot, "Passage").isVisible()).isTrue();
    }

    /// Retrouve un MenuItem par son libellé exact (les MenuItem ne sont pas des Node, donc pas de lookup CSS).
    private static MenuItem itemParLibelle(MenuButton menu, String libelle) {
        return menu.getItems().stream()
                .filter(item -> libelle.equals(item.getText()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("Source References : déposer un CSV est refusé (le dépôt n'est actif qu'en workflow passage)")
    void depot_refuse_hors_workflow(FxRobot robot) {
        // Câblage réel du controller : sur la source References (non workflow), le prédicat d'activation
        // du glisser-déposer est faux → un fichier déposé est refusé et n'enclenche aucun import.
        Region racine = robot.lookup("#racine").queryAs(Region.class);
        Dragboard presse = mock(Dragboard.class);
        when(presse.hasFiles()).thenReturn(true);
        when(presse.getFiles()).thenReturn(List.of(new File("obs.csv")));
        DragEvent drop = new DragEvent(
                null,
                racine,
                DragEvent.DRAG_DROPPED,
                presse,
                0,
                0,
                0,
                0,
                TransferMode.COPY,
                null,
                null,
                new PickResult(racine, 0, 0));

        robot.interact(() -> Event.fireEvent(racine, drop));

        assertThat(drop.isDropCompleted()).isFalse();
        verify(service, never()).importer(any(), any());
    }

    @Test
    @DisplayName("#483 : options de lecture — auto-lecture cochée par défaut, la boucle de l'AudioView suit sa case")
    void options_de_lecture(FxRobot robot) {
        MenuButton menu = robot.lookup("#menuActions").queryAs(MenuButton.class);
        AudioView audio = robot.lookup("#audioView").queryAs(AudioView.class);
        CheckMenuItem lectureAuto = optionLecture(menu, "automatique");
        CheckMenuItem boucle = optionLecture(menu, "boucle");

        assertThat(lectureAuto.isSelected())
                .as("auto-lecture activée par défaut")
                .isTrue();
        assertThat(audio.isLoop()).isFalse();

        robot.interact(() -> boucle.setSelected(true));
        assertThat(audio.isLoop()).as("la boucle de l'AudioView suit la case").isTrue();
    }

    @Test
    @DisplayName("#478 : raccourcis clavier sur la table — Entrée valide, R bascule la référence")
    void raccourcis_clavier_valider_et_reference(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        robot.interact(() -> table.getSelectionModel().select(0));

        robot.interact(() -> table.fireEvent(toucheAppuyee(KeyCode.ENTER)));
        verify(service).validerSelonMode(eq(1L), any());

        // Les deux lignes sont en référence : R la retire (marquerReference false).
        robot.interact(() -> table.fireEvent(toucheAppuyee(KeyCode.R)));
        verify(service).marquerReference(1L, false);
    }

    private static KeyEvent toucheAppuyee(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    /// Retrouve une option de lecture (CheckMenuItem) du menu ☰ par mot-clé de son libellé.
    private static CheckMenuItem optionLecture(MenuButton menu, String motCle) {
        return menu.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .filter(item ->
                        item.getText().toLowerCase(java.util.Locale.ROOT).contains(motCle))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("#623 : les vues enregistrées s'affichent en onglets, avec le bouton « + Vue »")
    void onglets_de_vues_affiche_les_vues_et_le_bouton_nouvelle(FxRobot robot) {
        FlowPane onglets = robot.lookup("#barreOnglets").queryAs(FlowPane.class);

        // La vue persistée « À revoir » (fournie par le dépôt) apparaît comme onglet.
        assertThat(robot.from(onglets).lookup(".onglet-vue-nom").queryAllAs(Label.class))
                .extracting(Label::getText)
                .contains("À revoir");
        // Le bouton « + Vue » clôt la barre : il enregistre les filtres courants comme une nouvelle vue.
        assertThat(robot.from(onglets)
                        .lookup(".onglet-vue-nouvelle")
                        .queryAs(Button.class)
                        .getText())
                .isEqualTo("+ Vue");
    }

    @Test
    @DisplayName("#681 : sur une vue active, modifier les filtres fait apparaître le bouton « enregistrer »")
    void modifier_les_filtres_d_une_vue_active_affiche_le_bouton_enregistrer(FxRobot robot) {
        FlowPane onglets = robot.lookup("#barreOnglets").queryAs(FlowPane.class);
        // Activer la vue « À revoir » (clic sur son libellé) : ses filtres sont rejoués, aucune divergence.
        Label nomVue = robot.from(onglets).lookup(".onglet-vue-nom").queryAllAs(Label.class).stream()
                .filter(label -> "À revoir".equals(label.getText()))
                .findFirst()
                .orElseThrow();
        robot.interact(() -> nomVue.getOnMouseClicked().handle(null));
        WaitForAsyncUtils.waitForFxEvents();
        // Le bouton « enregistrer » (icône disquette Ikonli, sans texte) est repéré par son libellé accessible.
        assertThat(robot.from(onglets).lookup(".onglet-vue-action").queryAllAs(Button.class))
                .as("pas de bouton « Enregistrer » juste après activation")
                .noneMatch(SonsValidationViewTest::estBoutonEnregistrer);

        // Ajouter un filtre « Statut » : les filtres divergent de la vue → le bouton « enregistrer » apparaît.
        MenuButton menuAjout = robot.lookup("#menuAjoutFiltre").queryAs(MenuButton.class);
        robot.interact(() -> itemParLibelle(menuAjout, "Statut").fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(robot.from(onglets).lookup(".onglet-vue-action").queryAllAs(Button.class))
                .as("filtres divergents → bouton « Enregistrer » présent")
                .anyMatch(SonsValidationViewTest::estBoutonEnregistrer);
    }

    /// Un bouton d'action d'onglet « enregistrer » : icône sans texte, identifié par son libellé accessible.
    private static boolean estBoutonEnregistrer(Button bouton) {
        return bouton.getAccessibleText() != null && bouton.getAccessibleText().startsWith("Enregistrer");
    }

    @Test
    @DisplayName("Vue par défaut « Sons non identifiés » : pose la puce et écarte les observations Tadarida")
    void vue_sons_non_identifies_filtre_sur_absence_de_tadarida(FxRobot robot) {
        FlowPane onglets = robot.lookup("#barreOnglets").queryAs(FlowPane.class);
        // L'onglet par défaut « Sons non identifiés » est présent parmi les vues.
        Label onglet = robot.from(onglets).lookup(".onglet-vue-nom").queryAllAs(Label.class).stream()
                .filter(label -> "Sons non identifiés".equals(label.getText()))
                .findFirst()
                .orElseThrow();

        // Les références de test ont toutes une proposition Tadarida : activer la vue (filtre taxonTadarida ==
        // null) doit vider la table et poser la puce « Non identifiés ».
        robot.interact(() -> onglet.getOnMouseClicked().handle(null));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(robot.lookup("#tableObservations").queryAs(TableView.class).getItems())
                .as("le filtre non identifiés écarte les observations Tadarida")
                .isEmpty();
        FlowPane puces = robot.lookup("#pucesFiltres").queryAs(FlowPane.class);
        assertThat(robot.from(puces).lookup(".puce-filtre .label").queryAllAs(Label.class))
                .extracting(Label::getText)
                .as("la puce du filtre « Non identifiés » est posée")
                .contains("Non identifiés");
    }

    private static TableColumn<?, ?> colonne(FxRobot robot, String entete) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        return table.getColumns().stream()
                .filter(c -> entete.equals(c.getText()))
                .findFirst()
                .orElseThrow();
    }

    private static TableColumn<?, ?> colonneParId(FxRobot robot, String id) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        return table.getColumns().stream()
                .filter(c -> id.equals(c.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("#1124 : source sans passage (références) → « Ouvrir les données sur Vigie-Chiro » masqué")
    void item_portail_masque_hors_passage(FxRobot robot) {
        MenuButton menu = robot.lookup("#menuActions").queryAs(MenuButton.class);
        MenuItem item = menu.getItems().stream()
                .filter(i -> "itemOuvrirVigieChiro".equals(i.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(item.isVisible()).isFalse();
    }

    @Test
    @DisplayName("#1214 : l'overlay d'occupation est en place, masqué une fois le chargement terminé")
    void overlay_occupation_masque_apres_chargement(FxRobot robot) {
        Node voile = robot.lookup(".occupation-voile").query();

        assertThat(voile).as("overlay d'occupation superposé à l'écran").isNotNull();
        assertThat(voile.isVisible())
                .as("chargement terminé (exécuteur synchrone) : overlay masqué")
                .isFalse();
    }

    @Test
    @DisplayName("#1792 : un clic droit dans une sélection multiple la préserve (la validation en lot en dépend)")
    void clic_droit_preserve_une_selection_multiple(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        robot.interact(() -> table.getSelectionModel().selectIndices(0, 1));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(table.getSelectionModel().getSelectedIndices()).containsExactly(0, 1);

        // Clic droit sur une ligne DÉJÀ sélectionnée : le menu doit s'ouvrir sur les deux lignes.
        // Si la sélection retombait à une seule ligne, « Validation ▸ » ne validerait qu'une observation
        // au lieu du lot que l'utilisateur croit avoir sous le curseur.
        clicDroitSurLigne(robot, 1);

        assertThat(table.getSelectionModel().getSelectedIndices())
                .as("le clic droit ne doit pas réduire une sélection multiple existante")
                .containsExactly(0, 1);
    }

    @Test
    @DisplayName("#1792 : un clic droit hors de la sélection cible la ligne survolée")
    void clic_droit_hors_selection_cible_la_ligne_survolee(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        robot.interact(() -> table.getSelectionModel().clearAndSelect(0));
        WaitForAsyncUtils.waitForFxEvents();

        clicDroitSurLigne(robot, 1);

        assertThat(table.getSelectionModel().getSelectedIndices())
                .as("le menu doit porter sur la ligne visée, pas sur l'ancienne sélection")
                .containsExactly(1);
    }

    /// Clic droit **déterministe** sur la ligne d'index `index` : l'événement part vers le nœud, sans
    /// passer par une position de souris (en headless, viser des coordonnées écran est fragile).
    private static void clicDroitSurLigne(FxRobot robot, int index) {
        Node ligne = robot.lookup("#tableObservations").lookup(".table-row-cell").queryAll().stream()
                .map(noeud -> (TableRow<?>) noeud)
                .filter(rangee -> !rangee.isEmpty() && rangee.getIndex() == index)
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune ligne d'index " + index));
        robot.interact(() -> ligne.fireEvent(new MouseEvent(
                MouseEvent.MOUSE_PRESSED,
                0,
                0,
                0,
                0,
                MouseButton.SECONDARY,
                1,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                false,
                null)));
        WaitForAsyncUtils.waitForFxEvents();
    }
}
