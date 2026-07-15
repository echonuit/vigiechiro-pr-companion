package fr.univ_amu.iut.qualification.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.qualification.model.ContexteVerification;
import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import fr.univ_amu.iut.qualification.model.PreCheckNuit.Feu;
import fr.univ_amu.iut.qualification.model.SelectionDEcoute;
import fr.univ_amu.iut.qualification.model.SequenceEnSelection;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import fr.univ_amu.iut.qualification.viewmodel.QualificationViewModel;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX **complémentaire** de l'écran M-Qualification, ciblant les câblages
/// Vue↔ViewModel que les tests de vue « lecture de propriétés » laissent passer : un écran non câblé
/// (feux non liés, colonnes sans `cellValueFactory`, bouton « Régénérer » mort) passerait pourtant
/// les tests qui se contentent de lire le ViewModel. Ici, chaque vérification fait un **vrai lookup
/// de contrôle par `fx:id`** (`robot.lookup`) ou inspecte un contrôle réellement rendu, puis vérifie
/// soit que son état reflète le ViewModel, soit qu'une interaction (`fire`, sélection de ligne,
/// lecture audio) produit bien l'effet attendu.
///
/// Couvre en priorité les manques récurrents de la feature relevés par l'audit (2026-06-18) :
/// pré-check à 3 feux R13 (couleur + anomalie), colonnes de la liste de sélection liées aux données,
/// et personnalisation/régénération R12 (recharge de la liste, remise à zéro de la progression,
/// verdict conservé).
///
/// Même harnais que `QualificationViewTest` : chargement du FXML par Guice avec un
/// [ServiceQualification] mocké, deux ViewModel sur le service mocké, ouverture sur un passage.
/// Le diagnostic de pré-check seedé porte ici un feu **rouge** (couverture) pour exercer le chemin
/// « anomalie signalée », et `creerSelection` est stubbé pour que la régénération R12 aboutisse.
@ExtendWith(ApplicationExtension.class)
class QualificationVueIntegrationTest {

    private static final long ID_PASSAGE = 42L;
    private static final long ID_SELECTION = 7L;

    private QualificationController controleur;

    @Start
    void start(Stage stage) throws Exception {
        ServiceQualification service = mock(ServiceQualification.class);
        // Feu rouge sur la couverture → presenteUneAnomalie() vrai (exerce l'anomalie R13 visible).
        when(service.precheck(anyLong())).thenReturn(new PreCheckNuit.Diagnostic(Feu.ROUGE, Feu.ORANGE, Feu.VERT));
        when(service.chargerContexte(anyLong()))
                .thenReturn(new ContexteVerification(
                        "640380",
                        "A1",
                        "Étang de la Tuilière",
                        2,
                        2026,
                        "2026-06-22",
                        "20:25:00",
                        "07:47:00",
                        30,
                        18000.0,
                        StatutWorkflow.TRANSFORME,
                        null));
        when(service.ouvrirVerification(anyLong()))
                .thenReturn(new SelectionDEcoute(ID_SELECTION, MethodeSelection.REPARTITION_TEMPORELLE, 3, ID_PASSAGE));
        when(service.detaillerSelection(anyLong())).thenReturn(lignes(3));
        // R12 : la régénération doit aboutir (sinon NPE captée → liste inchangée).
        when(service.creerSelection(anyLong(), any(MethodeSelection.class), anyInt()))
                .thenReturn(new SelectionDEcoute(99L, MethodeSelection.ALEATOIRE, 3, ID_PASSAGE));

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            QualificationViewModel verdict() {
                return new QualificationViewModel(service);
            }

            @Provides
            SelectionEcouteViewModel selection() {
                return new SelectionEcouteViewModel(service);
            }

            @Provides
            OuvrirPassage ouvrirPassage() {
                return (id, contexte) -> {};
            }

            @Provides
            OuvrirSite ouvrirSite() {
                return new OuvrirSite() {
                    @Override
                    public void ouvrirListe() {}

                    @Override
                    public void ouvrirDetail(String numeroCarre) {}
                };
            }
        });
        FXMLLoader loader = new FXMLLoader(QualificationController.class.getResource("Qualification.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        // Ces tests vérifient le comportement R12 de « Régénérer », pas la confirmation (#798) : on accepte
        // d'office pour ne pas bloquer sur un Alert natif quand une progression d'écoute est en cours.
        controleur.confirmateur().definir(message -> true);
        controleur.ouvrirSur(
                new ContextePassage(ID_PASSAGE, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière")));
        stage.setScene(new Scene(vue, 1100, 760));
        stage.show();
    }

    @Test
    @DisplayName(
            "Emplacement (fil d'Ariane) : Mes sites › Carré N › Détails du passage N° X › Vérifier l'enregistrement")
    void emplacement_reflete_le_passage() {
        assertThat(controleur.emplacement())
                .extracting(Lieu::libelle)
                .containsExactly("Mes sites", "Carré 640380", "Détails du passage N° 2", "Vérifier l'enregistrement");
        assertThat(controleur.emplacement().get(0).estCliquable()).isTrue();
        assertThat(controleur.emplacement().get(3).estCliquable()).isFalse();
    }

    private static List<SequenceEnSelection> lignes(int n) {
        List<SequenceEnSelection> lignes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            SequenceDEcoute sequence = new SequenceDEcoute(
                    (long) i, "PaRec_" + i + ".wav", null, i, 0.0, 5.0, "/ws/seq" + i + ".wav", true, 1L);
            lignes.add(new SequenceEnSelection(sequence, i, false));
        }
        return lignes;
    }

    @Test
    @DisplayName("R13 : les 3 feux du pré-check reflètent le diagnostic du ViewModel (lookup fx:id)")
    void les_trois_feux_refletent_le_precheck(FxRobot robot) {
        Label feuCouverture = robot.lookup("#feuCouverture").queryAs(Label.class);
        Label feuNombre = robot.lookup("#feuNombre").queryAs(Label.class);
        Label feuRenommage = robot.lookup("#feuRenommage").queryAs(Label.class);

        // La couleur du feu est portée par une classe CSS feu-vert / feu-orange / feu-rouge.
        assertThat(feuCouverture.getStyleClass()).contains("feu-rouge");
        assertThat(feuNombre.getStyleClass()).contains("feu-orange");
        assertThat(feuRenommage.getStyleClass()).contains("feu-vert");
        // Le libellé reste lisible (le câblage ne doit pas écraser le texte de l'indicateur).
        assertThat(feuCouverture.getText()).contains("Couverture");
        assertThat(feuNombre.getText()).contains("Nombre");
        assertThat(feuRenommage.getText()).contains("renommage");
        // #801 : différenciateur NON chromatique (pictogramme distinct par état) + infobulle explicative,
        // pour ne pas encoder l'état par la seule couleur.
        assertThat(feuCouverture.getText()).startsWith("✖");
        assertThat(feuNombre.getText()).startsWith("⚠");
        assertThat(feuRenommage.getText()).startsWith("✓");
        assertThat(feuCouverture.getTooltip()).isNotNull();
        assertThat(feuCouverture.getTooltip().getText()).contains("anomalie");
    }

    @Test
    @DisplayName("R13 : l'avertissement d'anomalie est visible quand un feu est rouge")
    void anomalie_visible_quand_feu_rouge(FxRobot robot) {
        Label anomalie = robot.lookup("#lblAnomalie").queryAs(Label.class);

        // Le diagnostic seedé porte un feu rouge (couverture) → preCheckAnomalieProperty vrai → affiché.
        assertThat(anomalie.isVisible()).isTrue();
        assertThat(anomalie.isManaged()).isTrue();
        // #1506 : la barre nomme le feu en cause plutôt qu'un « anomalie » anonyme.
        assertThat(anomalie.getText()).contains("couverture horaire");
    }

    @Test
    @DisplayName("Les colonnes de la liste de sélection sont liées aux données (cellValueFactory)")
    void colonnes_table_liees_aux_donnees(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);

        assertThat(table.getColumns()).hasSize(4);
        TableColumn<?, ?> colPosition = table.getColumns().get(0);
        TableColumn<?, ?> colFichier = table.getColumns().get(1);
        TableColumn<?, ?> colDuree = table.getColumns().get(2);
        TableColumn<?, ?> colEcoute = table.getColumns().get(3);

        // Une colonne non liée renverrait null : ces valeurs prouvent les cellValueFactory câblées.
        assertThat((String) colPosition.getCellData(0)).isEqualTo("1");
        assertThat((String) colFichier.getCellData(0)).isEqualTo("PaRec_0.wav");
        assertThat((String) colDuree.getCellData(0)).contains("5").endsWith("s");
        assertThat((String) colEcoute.getCellData(0)).isEqualTo("○"); // séquence non encore écoutée
    }

    @Test
    @DisplayName("Sélectionner une ligne met à jour le détail de la séquence courante (lookup fx:id)")
    void selection_ligne_met_a_jour_le_detail(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);
        Label seqNumero = robot.lookup("#lblSeqNumero").queryAs(Label.class);
        Label seqMeta = robot.lookup("#lblSeqMeta").queryAs(Label.class);

        robot.interact(() -> table.getSelectionModel().select(1));

        assertThat(seqNumero.getText()).contains("N° 2"); // position 1 → numéro 2
        assertThat(seqMeta.getText()).contains("PaRec_1.wav");
    }

    @Test
    @DisplayName("R10 : marquer une séquence écoutée met à jour la barre et le texte de progression")
    void progression_reflete_l_ecoute(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);
        AudioView audio = robot.lookup("#audioView").queryAs(AudioView.class);
        ProgressBar barre = robot.lookup("#barreProgression").queryAs(ProgressBar.class);
        Label progression = robot.lookup("#lblProgression").queryAs(Label.class);
        assertThat(barre.getProgress()).isEqualTo(0.0);

        robot.interact(() -> table.getSelectionModel().select(0));
        robot.interact(() -> audio.setPlaying(true)); // début de lecture → marquage écouté (R10)

        assertThat(barre.getProgress()).isGreaterThan(0.0);
        assertThat(progression.getText()).contains("1 / 3");
    }

    @Test
    @DisplayName("R12 : « Régénérer » recharge la liste et remet la progression à zéro (lookup fx:id)")
    void regenerer_remet_la_progression_a_zero(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);
        AudioView audio = robot.lookup("#audioView").queryAs(AudioView.class);
        ProgressBar barre = robot.lookup("#barreProgression").queryAs(ProgressBar.class);
        Button regenerer = robot.lookup("#boutonRegenerer").queryAs(Button.class);

        // On écoute une séquence pour faire progresser la barre...
        robot.interact(() -> table.getSelectionModel().select(0));
        robot.interact(() -> audio.setPlaying(true));
        assertThat(barre.getProgress()).isGreaterThan(0.0);

        // ... puis on régénère : la sélection est rechargée, la progression repart de zéro (R12).
        robot.interact(regenerer::fire);

        assertThat(barre.getProgress()).isEqualTo(0.0);
        TableColumn<?, ?> colEcoute = table.getColumns().get(3);
        assertThat((String) colEcoute.getCellData(0)).isEqualTo("○"); // liste rechargée, rien d'écouté
    }

    @Test
    @DisplayName("R12 : régénérer conserve le verdict déjà choisi (« Enregistrer » reste actif)")
    void regenerer_conserve_le_verdict_choisi(FxRobot robot) {
        Button ok = robot.lookup("#boutonOk").queryAs(Button.class);
        Button regenerer = robot.lookup("#boutonRegenerer").queryAs(Button.class);
        Button enregistrer = robot.lookup("#boutonEnregistrer").queryAs(Button.class);

        robot.interact(ok::fire); // verdict OK choisi → enregistrement possible
        assertThat(enregistrer.isDisabled()).isFalse();

        robot.interact(regenerer::fire); // régénérer la sélection ne doit pas effacer le verdict

        assertThat(enregistrer.isDisabled()).isFalse();
    }
}
