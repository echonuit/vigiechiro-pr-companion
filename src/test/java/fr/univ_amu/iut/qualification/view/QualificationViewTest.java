package fr.univ_amu.iut.qualification.view;

import static org.assertj.core.api.Assertions.assertThat;
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
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX de l'écran **M-Qualification** : chargement du FXML par Guice avec un
/// [ServiceQualification] mocké, ouverture sur un passage, et vérification du **câblage de la vue**
/// (bandeau de contexte, liste de la sélection, activation différée du bouton « Enregistrer »).
///
/// Le câblage repose sur les deux ViewModel injectés ; un module local ne fournit qu'eux (sur le
/// service mocké), sans base de données. La logique de bout en bout est couverte ailleurs
/// (`ServiceQualificationTest` + tests Mockito des deux VM) et le câblage Guice réel par
/// `QualificationModuleTest`.
@ExtendWith(ApplicationExtension.class)
class QualificationViewTest {

    private static final long ID_PASSAGE = 42L;
    private static final long ID_SELECTION = 7L;

    private QualificationController controleur;
    private SelectionEcouteViewModel selectionVm;

    @Start
    void start(Stage stage) throws Exception {
        ServiceQualification service = mock(ServiceQualification.class);
        when(service.precheck(anyLong())).thenReturn(new PreCheckNuit.Diagnostic(Feu.VERT, Feu.ORANGE, Feu.VERT));
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

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            QualificationViewModel verdict() {
                return new QualificationViewModel(service);
            }

            @Provides
            SelectionEcouteViewModel selection() {
                selectionVm = new SelectionEcouteViewModel(service);
                return selectionVm;
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
        controleur.ouvrirSur(
                new ContextePassage(ID_PASSAGE, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière")));
        stage.setScene(new Scene(vue, 1100, 760));
        stage.show();
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
    @DisplayName("#798 : « Régénérer » demande confirmation quand une progression d'écoute serait perdue")
    void regenerer_confirme_avant_de_perdre_la_progression(FxRobot robot) {
        // Progression d'écoute > 0 : une séquence marquée écoutée (sans quoi il n'y a rien à perdre).
        robot.interact(() -> {
            selectionVm.selectionner(selectionVm.lignes().get(0));
            selectionVm.marquerCouranteEcoutee();
        });
        double progressionAvant = selectionVm.progressionProperty().get();
        assertThat(progressionAvant).isGreaterThan(0);

        List<String> demandes = new ArrayList<>();
        controleur.setConfirmateur(message -> {
            demandes.add(message);
            return false; // l'utilisateur refuse
        });

        robot.clickOn("#boutonRegenerer");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(demandes).as("la régénération directe demande confirmation").hasSize(1);
        assertThat(demandes.get(0)).contains("progression");
        assertThat(selectionVm.progressionProperty().get())
                .as("refus → la sélection n'est pas régénérée (progression conservée)")
                .isEqualTo(progressionAvant);
    }

    @Test
    @DisplayName("L'écran affiche le bandeau de contexte et la liste de la sélection")
    void affiche_bandeau_et_liste(FxRobot robot) {
        Label titre = robot.lookup("#lblTitreContexte").queryAs(Label.class);
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);

        assertThat(titre.getText()).contains("640380").contains("A1");
        assertThat(table.getItems()).hasSize(3);
    }

    @Test
    @DisplayName("Le bouton « Enregistrer » est désactivé tant qu'aucun verdict décisif n'est choisi")
    void enregistrer_desactive_sans_verdict(FxRobot robot) {
        Button enregistrer = robot.lookup("#boutonEnregistrer").queryAs(Button.class);

        assertThat(enregistrer.isDisabled()).isTrue();
    }

    @Test
    @DisplayName("Choisir un verdict OK active le bouton « Enregistrer »")
    void choisir_ok_active_enregistrer(FxRobot robot) {
        Button ok = robot.lookup("#boutonOk").queryAs(Button.class);
        robot.interact(ok::fire);

        Button enregistrer = robot.lookup("#boutonEnregistrer").queryAs(Button.class);
        assertThat(enregistrer.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Les boutons de verdict reçoivent bien leurs classes CSS (liste FXML à virgules)")
    void boutons_verdict_recoivent_leurs_classes_css(FxRobot robot) {
        Button ok = robot.lookup("#boutonOk").queryAs(Button.class);

        assertThat(ok.getStyleClass()).contains("verdict", "verdict-ok");
    }

    @Test
    @DisplayName("La vue audio suit le fichier de la séquence sélectionnée")
    void audio_suit_la_sequence_selectionnee(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);
        AudioView audio = robot.lookup("#audioView").queryAs(AudioView.class);

        // La normalisation est activée par la vue pour égaliser le niveau d'écoute (#109), et l'expansion
        // temporelle ×10 est posée pour que les axes affichent les grandeurs réelles.
        assertThat(audio.isNormalisation()).isTrue();
        assertThat(audio.getTimeExpansionFactor()).isEqualTo(10.0);

        robot.interact(() -> table.getSelectionModel().select(0));

        assertThat(audio.getAudioFile()).isNotNull();
        assertThat(audio.getAudioFile().toString()).endsWith("seq0.wav");
    }

    @Test
    @DisplayName("Non-régression : la vue audio applique l'expansion temporelle ×10 (axe des fréquences réel)")
    void vue_audio_applique_l_expansion_temporelle_x10(FxRobot robot) {
        AudioView audio = robot.lookup("#audioView").queryAs(AudioView.class);

        // Les séquences transformées sont les originaux ralentis ×10 : l'AudioView doit ré-étirer ses axes
        // pour afficher les fréquences RÉELLES (× 10). Sans ce réglage, l'axe plafonnait à ~19 kHz au lieu
        // des ~192 kHz réels (fréquences ÷10 sur l'écran « Vérifier l'enregistrement »).
        assertThat(audio.getTimeExpansionFactor()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("Le début de lecture marque la séquence courante comme écoutée (R10)")
    void debut_de_lecture_marque_ecoutee(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);
        AudioView audio = robot.lookup("#audioView").queryAs(AudioView.class);
        robot.interact(() -> table.getSelectionModel().select(0));

        robot.interact(() -> audio.setPlaying(true));

        SequenceEnSelection premiere = (SequenceEnSelection) table.getItems().get(0);
        assertThat(premiere.ecoutee()).isTrue();
    }

    @Test
    @DisplayName("Le bouton « Personnaliser » est présent et actif dans l'en-tête de la liste")
    void bouton_personnaliser_present(FxRobot robot) {
        Button personnaliser = robot.lookup("#boutonPersonnaliser").queryAs(Button.class);

        assertThat(personnaliser.getText()).contains("Personnaliser");
        assertThat(personnaliser.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Le raccourci clavier O choisit le verdict OK (active « Enregistrer »)")
    void raccourci_o_choisit_verdict_ok(FxRobot robot) {
        Button enregistrer = robot.lookup("#boutonEnregistrer").queryAs(Button.class);
        assertThat(enregistrer.isDisabled()).isTrue();

        robot.push(KeyCode.O);

        assertThat(enregistrer.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Choisir « À jeter » affiche l'aperçu R14 avant l'enregistrement")
    void apercu_r14_visible_quand_a_jeter_choisi(FxRobot robot) {
        Label apercu = robot.lookup("#lblApercuR14").queryAs(Label.class);
        assertThat(apercu.isVisible()).isFalse();

        robot.push(KeyCode.J);

        assertThat(apercu.isVisible()).isTrue();
    }
}
