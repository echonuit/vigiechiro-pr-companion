package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.OuvrirDiagnostic;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.OuvrirValidation;
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Tests d'intégration TestFX **complémentaires** de l'écran pivot **M-Passage** (`Passage.fxml`),
/// centrés sur le **vrai lookup des `fx:id`** et sur des **interactions** (sélection d'onglet, clic
/// sur les boutons dédiés) — là où un écran réduit à un placeholder échouerait alors qu'il
/// passerait des tests qui ne liraient que les propriétés du ViewModel.
///
/// Couvre des câblages non vérifiés par [PassageViewTest] : fil d'Ariane, bandeau (enregistreur,
/// plage horaire, verdict), statistiques (volume transformé, durée audible), états précis du
/// **stepper** (étape courante / franchies) et navigation entre les onglets (« Diagnostic
/// matériel », « Validation Tadarida ») avec leurs boutons propres. Même harnais que
/// [PassageViewTest] (chargement du FXML via Guice avec un [ServicePassage] mocké, ouverture sur un
/// passage + contexte site). Pas de base de données.
@ExtendWith(ApplicationExtension.class)
@Tag("conformite")
class PassageVueIntegrationTest {

    private static final long ID_PASSAGE = 42L;

    private final AtomicReference<Long> verificationOuverte = new AtomicReference<>();
    private final AtomicReference<Long> diagnosticOuvert = new AtomicReference<>();
    private final AtomicReference<Long> validationOuverte = new AtomicReference<>();
    private final AtomicReference<Long> depotOuvert = new AtomicReference<>();

    @Start
    void start(Stage stage) throws Exception {
        ServicePassage service = mock(ServicePassage.class);
        when(service.detailPassage(anyLong()))
                .thenReturn(new DetailPassage(
                        2,
                        2026,
                        "2026-06-22",
                        "20:25:00",
                        "07:47:00",
                        "1925492",
                        StatutWorkflow.VERIFIE,
                        Verdict.OK,
                        null,
                        4096L,
                        1024L,
                        30,
                        150.0));
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            PassageViewModel viewModel() {
                return new PassageViewModel(service);
            }

            @Provides
            OuvrirVerification ouvrirVerification() {
                return verificationOuverte::set;
            }

            @Provides
            OuvrirDiagnostic ouvrirDiagnostic() {
                return diagnosticOuvert::set;
            }

            @Provides
            OuvrirValidation ouvrirValidation() {
                return validationOuverte::set;
            }

            @Provides
            OuvrirLot ouvrirLot() {
                return depotOuvert::set;
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
        FXMLLoader loader = new FXMLLoader(PassageController.class.getResource("Passage.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        PassageController controleur = loader.getController();
        controleur.ouvrirSur(ID_PASSAGE, new ContexteSite("640380", "A1", "Étang de la Tuilière"));
        stage.setScene(new Scene(vue, 1100, 700));
        stage.show();
    }

    @Test
    @DisplayName("Le fil d'Ariane reflète le contexte du passage (préfixe « Mes sites » + identité)")
    void fil_d_ariane_reflete_le_contexte(FxRobot robot) {
        Label filAriane = robot.lookup("#lblFilAriane").queryAs(Label.class);

        // Câblage par StringBinding sur titreContexte : un écran stub n'afficherait pas ce préfixe.
        assertThat(filAriane.getText())
                .contains("Mes sites")
                .contains("640380")
                .contains("A1")
                .contains("N° 2");
    }

    @Test
    @DisplayName("Le bandeau d'identité affiche l'enregistreur, la plage horaire et le verdict")
    void bandeau_affiche_enregistreur_plage_verdict(FxRobot robot) {
        Label enregistreur = robot.lookup("#lblEnregistreur").queryAs(Label.class);
        Label plage = robot.lookup("#lblPlageHoraire").queryAs(Label.class);
        Label verdict = robot.lookup("#lblVerdict").queryAs(Label.class);

        assertThat(enregistreur.getText()).isEqualTo("PR 1925492");
        assertThat(plage.getText()).contains("2026-06-22").contains("20:25:00").contains("07:47:00");
        assertThat(verdict.getText()).isEqualTo("OK"); // Verdict.OK → libellé « OK »
    }

    @Test
    @DisplayName("L'onglet « Vue d'ensemble » affiche le volume transformé et la durée audible")
    void statistiques_volume_transforme_et_duree_audible(FxRobot robot) {
        Label volTransformes = robot.lookup("#lblVolTransformes").queryAs(Label.class);
        Label dureeAudible = robot.lookup("#lblDureeAudible").queryAs(Label.class);

        assertThat(volTransformes.getText()).isEqualTo("1 Ko"); // octetsLisibles(1024)
        assertThat(dureeAudible.getText()).isEqualTo("2 min 30 s"); // dureeLisible(150.0)
    }

    @Test
    @DisplayName("Le stepper marque l'étape courante (Vérifié) et les deux étapes franchies")
    void stepper_marque_l_etape_courante_et_les_franchies(FxRobot robot) {
        HBox stepper = robot.lookup("#stepper").queryAs(HBox.class);
        List<Label> puces =
                stepper.getChildren().stream().map(Label.class::cast).toList();

        // L'étape « courante » porte la classe CSS dérivée de l'EtatEtape et le libellé du statut VM.
        Label courante = puces.stream()
                .filter(puce -> puce.getStyleClass().contains("etape-courante"))
                .findFirst()
                .orElseThrow();
        assertThat(courante.getText()).isEqualTo("Vérifié");

        // Importé + Transformé précèdent Vérifié → deux étapes franchies.
        long franchies = puces.stream()
                .filter(puce -> puce.getStyleClass().contains("etape-franchie"))
                .count();
        assertThat(franchies).isEqualTo(2);
    }

    @Test
    @DisplayName("Onglet « Diagnostic matériel » : son bouton dédié ouvre M-Diagnostic du passage")
    void onglet_diagnostic_le_bouton_dedie_ouvre_le_diagnostic(FxRobot robot) {
        TabPane onglets = robot.lookup(".onglets").queryAs(TabPane.class);

        // Sélectionner l'onglet attache son contenu au graphe de scène (rendu paresseux), ce qui rend
        // le bouton dédié interrogeable par son fx:id.
        robot.interact(() -> onglets.getSelectionModel().select(1));

        Button ouvrirDiagnostic = robot.lookup("#boutonOuvrirDiagnostic").queryAs(Button.class);
        assertThat(ouvrirDiagnostic.isDisabled()).isFalse();

        robot.interact(ouvrirDiagnostic::fire);

        assertThat(diagnosticOuvert.get()).isEqualTo(ID_PASSAGE);
    }

    @Test
    @DisplayName("Onglet « Validation Tadarida » : bouton dédié verrouillé tant que le passage n'est pas déposé")
    void onglet_validation_le_bouton_dedie_est_verrouille(FxRobot robot) {
        TabPane onglets = robot.lookup(".onglets").queryAs(TabPane.class);

        robot.interact(() -> onglets.getSelectionModel().select(2));

        Button ouvrirValidation = robot.lookup("#boutonOuvrirValidation").queryAs(Button.class);
        Label message = robot.lookup("#lblValidation").queryAs(Label.class);

        // Passage VERIFIE (≠ DEPOSE) → validationVerrouillee true : le bouton reste désactivé.
        assertThat(ouvrirValidation.isDisabled()).isTrue();
        assertThat(message.getText()).contains("🔒").contains("déposé");
        assertThat(validationOuverte.get()).isNull(); // aucune ouverture déclenchée
    }
}
