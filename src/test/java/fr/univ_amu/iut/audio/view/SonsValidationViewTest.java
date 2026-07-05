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
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.view.NavigationDeTestModule;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.Taxon;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kordamp.ikonli.javafx.FontIcon;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de la **vue audio unifiée** (`SonsValidation.fxml`) ouverte sur la source
/// `References` : chargement du FXML via Guice (services mockés), câblage table / sélection / détail /
/// écoute, bascule de référence, et adaptation à la source (menu « Exporter la bibliothèque » visible,
/// actions de passage masquées ; colonnes de contexte visibles car la source n'est pas un seul passage).
/// Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class SonsValidationViewTest {

    private ServiceValidation service;
    private RevueEnLot revueEnLot;
    private SonsValidationController controleur;

    private static LigneObservationAudio ligne(
            long id, long seq, String tadarida, String observateur, String nomEspece, String nomTadarida) {
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
                "Chiroptères",
                "PaRec_" + seq + "_000.wav",
                0.20,
                0.32,
                // Heure de capture : 22:00 + n° de séquence (seq 10 → 22:10, seq 11 → 22:11), pour vérifier
                // l'affichage de la colonne « Heure ».
                LocalDateTime.of(2026, 4, 22, 22, 0).plusMinutes(seq));
    }

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceValidation.class);
        revueEnLot = mock(RevueEnLot.class);
        ServiceBibliotheque bibliotheque = mock(ServiceBibliotheque.class);
        when(service.taxonsDisponibles())
                .thenReturn(List.of(new Taxon("Nyclei", "Nyctalus leisleri", "Noctule de Leisler", 1L)));
        when(service.lignesAudioReferences("u-1"))
                .thenReturn(List.of(
                        ligne(1, 10, "Pippip", "Pippip", "Pipistrelle commune", "Pipistrelle commune"),
                        ligne(2, 11, "Nyclei", "Nyclei", "Noctule de Leisler", "Noctule de Leisler")));
        when(service.cheminAudio(anyLong())).thenReturn(Optional.empty());
        when(service.cheminAudio(10L)).thenReturn(Optional.of(Path.of("/ws/transformes/p.wav")));

        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Provides
                    AudioViewModel viewModel() {
                        return new AudioViewModel(service, revueEnLot, bibliotheque);
                    }
                },
                new NavigationDeTestModule());
        FXMLLoader loader = new FXMLLoader(SonsValidationController.class.getResource("SonsValidation.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        controleur.ouvrirSur(new SourceObservations.References("u-1"));
        stage.setScene(new Scene(vue, 1000, 700));
        stage.show();
    }

    @Test
    @DisplayName("La table liste les références ; le résumé de statut compte + l'avancement (sans bandeau de titre)")
    void affiche_table_et_resume(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        assertThat(table.getItems()).hasSize(2);

        // Plus de bandeau de titre en tête de vue (déporté : nom d'écran dans le fil d'Ariane).
        assertThat(robot.lookup("#lblResume").tryQuery()).isEmpty();

        // Le résumé destiné à la barre de statut porte le total + l'avancement (les 2 lignes sont VALIDEE),
        // sans répéter le nom d'écran.
        assertThat(controleur.resumeStatutProperty().get()).isEqualTo("2 observation(s) · 2 / 2 revues");
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
        // Les deux lignes de la fixture sont en référence et commentées → icônes aussi en cellule
        // (donc plus d'occurrences que le seul en-tête).
        assertThat(robot.lookup(".icone-reference").queryAll()).hasSizeGreaterThan(1);
        assertThat(robot.lookup(".icone-commentaire").queryAll()).hasSizeGreaterThan(1);
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
        when(service.lignesAudioReferences("u-1"))
                .thenReturn(List.of(
                        ligne(1, 10, "Pippip", "Nyclei", "Noctule de Leisler", "Pipistrelle commune"),
                        ligne(2, 11, "Nyclei", "Nyclei", "Noctule de Leisler", "Noctule de Leisler")));

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
        Label message = robot.lookup("#lblMessage").queryAs(Label.class);

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
        // Les 15 premières colonnes par en-tête ; les 2 indicateurs (icônes, sans texte) par leur id.
        assertThat(table.getColumns().stream()
                        .limit(15)
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
                        "Fréquence",
                        "FME",
                        "Fréq. term.",
                        "Début",
                        "Durée",
                        "Statut");
        assertThat(table.getColumns().get(15).getId()).isEqualTo("colReference");
        assertThat(table.getColumns().get(16).getId()).isEqualTo("colCommentaire");
        // Colonnes-indicateurs : non triables (trier une icône est déroutant, cf. « colonne vide triable »).
        assertThat(colonneParId(robot, "colReference").isSortable()).isFalse();
        assertThat(colonneParId(robot, "colCommentaire").isSortable()).isFalse();
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
        // Les MenuItem ne sont pas des Node : on passe par le MenuButton et ses items (ordre du FXML :
        // importer, inclure le mode, exporter _Vu, exporter bibliothèque).
        MenuButton menu = robot.lookup("#menuActions").queryAs(MenuButton.class);
        MenuItem importer = menu.getItems().get(0);
        MenuItem inclureMode = menu.getItems().get(1);
        MenuItem exporterVu = menu.getItems().get(2);
        MenuItem exporterBiblio = menu.getItems().get(3);

        assertThat(menu.isVisible()).isTrue();
        assertThat(exporterBiblio.isVisible()).isTrue();
        assertThat(importer.isVisible()).isFalse();
        assertThat(inclureMode.isVisible()).isFalse();
        assertThat(exporterVu.isVisible()).isFalse();
        // Source multi-passages : les colonnes de contexte restent visibles.
        assertThat(colonne(robot, "Passage").isVisible()).isTrue();
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
}
