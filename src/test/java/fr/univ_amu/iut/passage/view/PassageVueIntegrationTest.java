package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.commun.model.CompteurValidations;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.OuvrirDiagnostic;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.OuvrirValidation;
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.ServiceArchivagePassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicReference;
import javafx.css.PseudoClass;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX **exhaustif** de l'écran pivot **M-Passage** refondu en « hub à plat »
/// (`Passage.fxml`), centré sur le **vrai lookup des `fx:id`** : un détecteur de trou par zone (en-tête,
/// bandeau, stepper, résumé/stats, cartes d'actions). Là où un écran réduit à un placeholder passerait
/// des tests ne lisant que le ViewModel, ce test échoue si le câblage Vue↔VM manque.
///
/// Couvre en plus :
/// - des **garde-fous structurels** de la refonte : plus aucun `TabPane`, ni fil d'Ariane interne
///   (`#lblFilAriane`), ni onglets-lanceurs (`#boutonOuvrirDiagnostic`/`#boutonOuvrirValidation`/
///   `#lblValidation`) — le retour et le fil sont désormais portés par le chrome (`commun`) ;
/// - les **états des cartes d'actions selon le statut** (Importé / Vérifié / Déposé) ;
/// - le **déclenchement des handlers** vers les contrats socle (idPassage attendu).
///
/// Chargement du FXML via Guice avec un [ServicePassage] mocké, ouverture sur un passage + contexte
/// site. Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class PassageVueIntegrationTest {

    private static final long ID_PASSAGE = 42L;

    private final AtomicReference<Long> verificationOuverte = new AtomicReference<>();
    private final AtomicReference<Long> diagnosticOuvert = new AtomicReference<>();
    private final AtomicReference<Long> validationOuverte = new AtomicReference<>();
    private final AtomicReference<Long> depotOuvert = new AtomicReference<>();
    private PassageController controleur;

    @Start
    void start(Stage stage) {
        // Fixture affichée : passage VÉRIFIÉ (dépôt possible, validation encore verrouillée).
        Parent vue = charger(StatutWorkflow.VERIFIE, 2);
        stage.setScene(new Scene(vue, 1100, 720));
        stage.show();
    }

    // ----- Balayage du câblage Vue <-> ViewModel par vrai lookup fx:id (fixture VÉRIFIÉ) -----

    @Test
    @DisplayName("En-tête : titre lié au VM + boutons rattachement/supprimer présents et actifs")
    void entete_reflete_le_vm(FxRobot robot) {
        // Le contexte (carré / point / N°) est déporté en zone gauche de la barre de statut (#693).
        assertThat(controleur.zonesStatutProperty().get().gauche())
                .contains("640380")
                .contains("A1")
                .contains("N° 2");
        assertThat(robot.lookup("#boutonRattachement").queryButton().isDisabled())
                .isFalse();
        // Passage vérifié (≠ déposé) : la suppression reste possible.
        assertThat(robot.lookup("#boutonSupprimer").queryButton().isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Bandeau d'identité : plage horaire, enregistreur, statut et verdict reflètent le VM")
    void bandeau_reflete_le_vm(FxRobot robot) {
        assertThat(robot.lookup("#lblPlageHoraire").queryAs(Label.class).getText())
                .contains("2026-06-22")
                .contains("20:25:00")
                .contains("07:47:00");
        assertThat(robot.lookup("#lblEnregistreur").queryAs(Label.class).getText())
                .isEqualTo("PR 1925492");
        assertThat(robot.lookup("#lblStatut").queryAs(Label.class).getText()).isEqualTo("Vérifié");
        assertThat(robot.lookup("#lblVerdict").queryAs(Label.class).getText()).isEqualTo("OK");
    }

    @Test
    @DisplayName("Stepper : une puce par étape, l'étape courante (Vérifié) et les deux franchies marquées")
    void stepper_reflete_le_workflow(FxRobot robot) {
        HBox stepper = robot.lookup("#stepper").queryAs(HBox.class);

        assertThat(stepper.getChildren()).hasSize(5);
        Label courante = stepper.getChildren().stream()
                .map(Label.class::cast)
                .filter(puce -> puce.getStyleClass().contains("etape-courante"))
                .findFirst()
                .orElseThrow();
        assertThat(courante.getText()).isEqualTo("Vérifié");
        long franchies = stepper.getChildren().stream()
                .map(Label.class::cast)
                .filter(puce -> puce.getStyleClass().contains("etape-franchie"))
                .count();
        assertThat(franchies).isEqualTo(2);
    }

    @Test
    @DisplayName("Résumé de la nuit : les 4 statistiques reflètent le VM")
    void stats_refletent_le_vm(FxRobot robot) {
        assertThat(robot.lookup("#lblVolBruts").queryAs(Label.class).getText()).isEqualTo("4 Ko");
        assertThat(robot.lookup("#lblVolTransformes").queryAs(Label.class).getText())
                .isEqualTo("1 Ko");
        assertThat(robot.lookup("#lblDureeEnregistree").queryAs(Label.class).getText())
                .isEqualTo("2 min 30 s");
        assertThat(robot.lookup("#lblNbSequences").queryAs(Label.class).getText())
                .isEqualTo("30");
    }

    @Test
    @DisplayName("Actions : les 4 cartes et l'indice contextuel sont présents")
    void cartes_actions_presentes(FxRobot robot) {
        assertThat(robot.lookup("#boutonVerifier").tryQuery()).isPresent();
        assertThat(robot.lookup("#boutonDiagnostic").tryQuery()).isPresent();
        assertThat(robot.lookup("#boutonDepot").tryQuery()).isPresent();
        assertThat(robot.lookup("#boutonValidation").tryQuery()).isPresent();
        assertThat(robot.lookup("#lblIndiceAction").tryQuery()).isPresent();
    }

    // ----- Garde-fous structurels de la refonte « hub à plat » -----

    @Test
    @DisplayName("Structure : plus de TabPane, ni fil interne, ni onglets-lanceurs (portés par le chrome)")
    void structure_hub_a_plat(FxRobot robot) {
        assertThat(robot.lookup((Node noeud) -> noeud instanceof TabPane).queryAll())
                .as("aucun TabPane : le hub est à plat")
                .isEmpty();
        assertThat(robot.lookup("#lblFilAriane").tryQuery())
                .as("le fil d'Ariane interne a été retiré (chrome)")
                .isEmpty();
        assertThat(robot.lookup("#boutonOuvrirDiagnostic").tryQuery()).isEmpty();
        assertThat(robot.lookup("#boutonOuvrirValidation").tryQuery()).isEmpty();
        assertThat(robot.lookup("#lblValidation").tryQuery()).isEmpty();
    }

    // ----- États des cartes d'actions selon le statut -----

    @Test
    @DisplayName("VÉRIFIÉ : Vérifier/Diagnostic/Dépôt actifs, Validation verrouillée")
    void etats_actions_verifie(FxRobot robot) {
        assertThat(robot.lookup("#boutonVerifier").queryButton().isDisabled()).isFalse();
        assertThat(robot.lookup("#boutonDiagnostic").queryButton().isDisabled()).isFalse();
        assertThat(robot.lookup("#boutonDepot").queryButton().isDisabled()).isFalse();
        assertThat(robot.lookup("#boutonValidation").queryButton().isDisabled()).isTrue();
    }

    @Test
    @DisplayName(
            "IMPORTÉ : Vérifier/Dépôt/Validation désactivés (nuit non transformée), Diagnostic actif, indice affiché")
    void etats_actions_importe(FxRobot robot) {
        Parent vue = chargerSurFx(robot, StatutWorkflow.IMPORTE, 2);

        assertThat(bouton(vue, "#boutonVerifier").isDisabled()).isTrue();
        assertThat(bouton(vue, "#boutonDepot").isDisabled()).isTrue();
        assertThat(bouton(vue, "#boutonValidation").isDisabled()).isTrue();
        assertThat(bouton(vue, "#boutonDiagnostic").isDisabled()).isFalse();
        assertThat(((Label) vue.lookup("#lblIndiceAction")).getText()).contains("transformée");
        // Nuit pas encore transformée → aucune carte mise en avant.
        assertThat(estRecommandee(bouton(vue, "#boutonVerifier"))).isFalse();
        assertThat(estRecommandee(bouton(vue, "#boutonDepot"))).isFalse();
    }

    @Test
    @DisplayName(
            "DÉPOSÉ : Dépôt encore accessible (retour possible), Validation déverrouillée et ouvrant M-Vision-Tadarida")
    void etats_actions_depose(FxRobot robot) {
        Parent vue = chargerSurFx(robot, StatutWorkflow.DEPOSE, 1);

        // #… : le dépôt reste accessible même une fois déposé, pour revenir consulter/supprimer les archives.
        assertThat(bouton(vue, "#boutonDepot").isDisabled()).isFalse();
        assertThat(bouton(vue, "#boutonValidation").isDisabled()).isFalse();
        assertThat(bouton(vue, "#boutonVerifier").isDisabled()).isFalse();
        // Déposé : le renommage (« Modifier le passage ») est bloqué, son nom étant l'identité serveur (#1134).
        assertThat(bouton(vue, "#boutonRattachement").isDisabled()).isTrue();
        // Déposé avec audio conservé (volumes > 0 dans la fixture) : l'archivage devient possible (#1300).
        assertThat(bouton(vue, "#boutonArchiver").isDisabled()).isFalse();
        // Déposé → la mise en avant est passée à la carte « Sons & validation » (le dépôt n'est plus recommandé).
        assertThat(estRecommandee(bouton(vue, "#boutonValidation"))).isTrue();
        assertThat(estRecommandee(bouton(vue, "#boutonDepot"))).isFalse();

        robot.interact(((Button) vue.lookup("#boutonValidation"))::fire);
        assertThat(validationOuverte.get()).isEqualTo(ID_PASSAGE);
    }

    @Test
    @DisplayName("Carte recommandée : la mise en avant suit le statut (Transformé→Vérifier, Vérifié→Dépôt)")
    void carte_recommandee_suit_le_statut(FxRobot robot) {
        // Fixture affichée = VÉRIFIÉ → la prochaine étape recommandée est « Préparer le dépôt ».
        assertThat(estRecommandee(robot.lookup("#boutonDepot").queryButton())).isTrue();
        assertThat(estRecommandee(robot.lookup("#boutonVerifier").queryButton()))
                .isFalse();

        // TRANSFORMÉ → la mise en avant est sur « Vérifier l'enregistrement ».
        Parent transforme = chargerSurFx(robot, StatutWorkflow.TRANSFORME, 2);
        assertThat(estRecommandee(bouton(transforme, "#boutonVerifier"))).isTrue();
        assertThat(estRecommandee(bouton(transforme, "#boutonDepot"))).isFalse();
    }

    // ----- Handlers : chaque carte active ouvre le bon écran via le contrat socle -----

    @Test
    @DisplayName("Handlers : Vérifier/Diagnostic/Dépôt ouvrent le bon écran avec l'idPassage courant")
    void handlers_ouvrent_les_bons_ecrans(FxRobot robot) {
        robot.interact(robot.lookup("#boutonVerifier").queryButton()::fire);
        assertThat(verificationOuverte.get()).isEqualTo(ID_PASSAGE);

        robot.interact(robot.lookup("#boutonDiagnostic").queryButton()::fire);
        assertThat(diagnosticOuvert.get()).isEqualTo(ID_PASSAGE);

        robot.interact(robot.lookup("#boutonDepot").queryButton()::fire);
        assertThat(depotOuvert.get()).isEqualTo(ID_PASSAGE);
    }

    // ----- Aides locales -----

    private Parent chargerSurFx(FxRobot robot, StatutWorkflow statut, int numero) {
        AtomicReference<Parent> ref = new AtomicReference<>();
        robot.interact(() -> ref.set(charger(statut, numero)));
        return ref.get();
    }

    private static Button bouton(Parent vue, String selecteur) {
        return (Button) vue.lookup(selecteur);
    }

    private static boolean estRecommandee(Button carte) {
        return carte.getPseudoClassStates().contains(PseudoClass.getPseudoClass("recommandee"));
    }

    /// Charge `Passage.fxml` via Guice sur un passage du `statut` donné et l'ouvre sur [#ID_PASSAGE].
    /// À appeler sur le fil JavaFX (chargement FXML).
    private Parent charger(StatutWorkflow statut, int numero) {
        ServicePassage service = mock(ServicePassage.class);
        ServicePurgeOriginaux purge = mock(ServicePurgeOriginaux.class);
        ServiceArchivagePassage archivage = mock(ServiceArchivagePassage.class);
        when(service.detailPassage(anyLong())).thenReturn(detail(statut, numero));
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                OptionalBinder.newOptionalBinder(binder(), OuvrirDiagnostic.class)
                        .setBinding()
                        .toInstance(passage -> diagnosticOuvert.set(passage.idPassage()));
                OptionalBinder.newOptionalBinder(binder(), OuvrirVerification.class)
                        .setBinding()
                        .toInstance(passage -> verificationOuverte.set(passage.idPassage()));
                OptionalBinder.newOptionalBinder(binder(), OuvrirLot.class)
                        .setBinding()
                        .toInstance(passage -> depotOuvert.set(passage.idPassage()));
            }

            @Provides
            PassageViewModel viewModel() {
                return new PassageViewModel(service, purge, archivage);
            }

            @Provides
            OuvrirValidation ouvrirValidation() {
                return passage -> validationOuverte.set(passage.idPassage());
            }

            @Provides
            OuvrirMultisite ouvrirMultisite() {
                return numeroCarre -> {};
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

            @Provides
            CompteurValidations compteurValidations() {
                return idPassage -> 0;
            }

            @Provides
            PortailVigieChiro portail() {
                return mock(PortailVigieChiro.class);
            }

            @Provides
            OuvreurDeLien ouvreurDeLien() {
                return url -> {};
            }
        });
        FXMLLoader loader = new FXMLLoader(PassageController.class.getResource("Passage.fxml"));
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            controleur = loader.getController();
            controleur.ouvrirSur(ID_PASSAGE, new ContexteSite("640380", "A1", "Étang de la Tuilière"));
            return vue;
        } catch (IOException echec) {
            throw new UncheckedIOException(echec);
        }
    }

    private static DetailPassage detail(StatutWorkflow statut, int numero) {
        return new DetailPassage(
                numero,
                2026,
                "2026-06-22",
                "20:25:00",
                "07:47:00",
                "1925492",
                statut,
                Verdict.OK,
                null,
                4096L,
                1024L,
                30,
                150.0,
                null);
    }
}
