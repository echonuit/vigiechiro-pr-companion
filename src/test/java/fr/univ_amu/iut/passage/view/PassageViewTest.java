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
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.view.OuvrirDiagnostic;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.OuvrirValidation;
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.util.concurrent.atomic.AtomicReference;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de l'écran **M-Passage** : chargement du FXML via Guice
/// (avec un [ServicePassage] mocké), ouverture sur un passage + contexte site, et vérification du
/// câblage (titre/bandeau d'identité, stepper de statut). Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class PassageViewTest {

    private static final long ID_PASSAGE = 42L;

    private final AtomicReference<Long> verificationOuverte = new AtomicReference<>();
    private final AtomicReference<Long> diagnosticOuvert = new AtomicReference<>();
    private final AtomicReference<Long> validationOuverte = new AtomicReference<>();
    private final AtomicReference<Long> depotOuvert = new AtomicReference<>();
    private final AtomicReference<String> carteFocalisee = new AtomicReference<>();
    private PassageController controleur;

    @Start
    void start(Stage stage) throws Exception {
        ServicePassage service = mock(ServicePassage.class);
        ServicePurgeOriginaux purge = mock(ServicePurgeOriginaux.class);
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
                        150.0,
                        null));
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                OptionalBinder.newOptionalBinder(binder(), OuvrirDiagnostic.class)
                        .setBinding()
                        .toInstance(passage -> diagnosticOuvert.set(passage.idPassage()));
            }

            @Provides
            PassageViewModel viewModel() {
                return new PassageViewModel(service, purge);
            }

            @Provides
            OuvrirVerification ouvrirVerification() {
                return passage -> verificationOuverte.set(passage.idPassage());
            }

            @Provides
            OuvrirValidation ouvrirValidation() {
                return passage -> validationOuverte.set(passage.idPassage());
            }

            @Provides
            OuvrirLot ouvrirLot() {
                return passage -> depotOuvert.set(passage.idPassage());
            }

            @Provides
            OuvrirMultisite ouvrirMultisite() {
                return carteFocalisee::set;
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
        });
        FXMLLoader loader = new FXMLLoader(PassageController.class.getResource("Passage.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        controleur.ouvrirSur(ID_PASSAGE, new ContexteSite("640380", "A1", "Étang de la Tuilière"));
        stage.setScene(new Scene(vue, 1100, 700));
        stage.show();
    }

    @Test
    @DisplayName("Le bandeau affiche l'identité du passage (carré, point, statut)")
    void affiche_l_identite(FxRobot robot) {
        Label statut = robot.lookup("#lblStatut").queryAs(Label.class);

        // Barre de statut 3 zones (#1022) : contexte à gauche, statut (+ verdict) au centre, volumétrie à droite.
        var zones = controleur.zonesStatutProperty().get();
        assertThat(zones.gauche()).contains("640380").contains("A1").contains("N° 2");
        assertThat(zones.centre()).as("statut + verdict saisi").contains("Vérifié");
        assertThat(zones.droite()).as("nombre de séquences").isEqualTo("30 séquence(s)");
        assertThat(statut.getText()).isEqualTo("Vérifié");
    }

    @Test
    @DisplayName("Le stepper affiche les 5 étapes du workflow")
    void stepper_affiche_les_etapes(FxRobot robot) {
        HBox stepper = robot.lookup("#stepper").queryAs(HBox.class);

        assertThat(stepper.getChildren()).hasSize(5);
    }

    @Test
    @DisplayName("Le résumé de la nuit affiche les statistiques")
    void affiche_les_statistiques(FxRobot robot) {
        assertThat(robot.lookup("#lblVolBruts").queryAs(Label.class).getText()).isEqualTo("4 Ko");
        assertThat(robot.lookup("#lblNbSequences").queryAs(Label.class).getText())
                .isEqualTo("30");
    }

    @Test
    @DisplayName("« Vérifier » ouvre la qualification du passage courant (contrat socle)")
    void verifier_ouvre_la_qualification(FxRobot robot) {
        Button verifier = robot.lookup("#boutonVerifier").queryAs(Button.class);
        assertThat(verifier.isDisabled()).isFalse(); // statut ≥ transformé → vérification disponible

        robot.interact(verifier::fire);

        assertThat(verificationOuverte.get()).isEqualTo(ID_PASSAGE);
    }

    @Test
    @DisplayName("« Diagnostic matériel » ouvre M-Diagnostic du passage courant (contrat socle)")
    void diagnostic_ouvre_le_diagnostic(FxRobot robot) {
        Button diagnostic = robot.lookup("#boutonDiagnostic").queryAs(Button.class);
        assertThat(diagnostic.isDisabled()).isFalse(); // toujours disponible (relevé climatique + journal)

        robot.interact(diagnostic::fire);

        assertThat(diagnosticOuvert.get()).isEqualTo(ID_PASSAGE);
    }

    @Test
    @DisplayName("« Préparer le dépôt » ouvre M-Lot du passage courant (Vérifié → actif)")
    void depot_ouvre_le_lot(FxRobot robot) {
        Button depot = robot.lookup("#boutonDepot").queryAs(Button.class);
        assertThat(depot.isDisabled()).isFalse(); // passage Vérifié → phase de dépôt

        robot.interact(depot::fire);

        assertThat(depotOuvert.get()).isEqualTo(ID_PASSAGE);
    }

    @Test
    @DisplayName("La carte « Sons & validation » est désactivée tant que le passage n'est pas déposé")
    void validation_verrouillee_tant_que_non_depose(FxRobot robot) {
        // Le passage de la fixture est « Vérifié » (≠ Déposé) → carte de validation verrouillée.
        Button validation = robot.lookup("#boutonValidation").queryAs(Button.class);

        assertThat(validation.isDisabled()).isTrue();
        assertThat(validationOuverte.get()).isNull(); // rien n'est ouvert
    }

    @Test
    @DisplayName("Le bouton « Supprimer » est présent et actif dans l'en-tête")
    void bouton_supprimer_present(FxRobot robot) {
        Button supprimer = robot.lookup("#boutonSupprimer").queryAs(Button.class);

        assertThat(supprimer.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Le bouton « Purger les originaux » est présent et visible quand la nuit conserve des originaux")
    void bouton_purger_present(FxRobot robot) {
        Button purger = robot.lookup("#boutonPurger").queryAs(Button.class);

        assertThat(purger.isVisible())
                .as("volume bruts > 0 dans la fixture → purge proposée")
                .isTrue();
    }

    @Test
    @DisplayName("Le bouton « Modifier le passage » est présent et actif dans l'en-tête")
    void bouton_rattachement_present(FxRobot robot) {
        Button rattachement = robot.lookup("#boutonRattachement").queryAs(Button.class);

        assertThat(rattachement.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("#163 : les cartes d'action (icône seule) portent un nom accessible")
    void cartes_action_ont_un_nom_accessible(FxRobot robot) {
        // contentDisplay=GRAPHIC_ONLY : le libellé visible est dans le graphique, pas dans la propriété
        // text du bouton. Sans accessibleText, un lecteur d'écran n'annoncerait rien.
        for (String id : new String[] {"#boutonVerifier", "#boutonDiagnostic", "#boutonDepot", "#boutonValidation"}) {
            Button bouton = robot.lookup(id).queryAs(Button.class);
            assertThat(bouton.getAccessibleText()).as("nom accessible de " + id).isNotBlank();
        }
    }

    @Test
    @DisplayName("#152 : « Voir sur la carte » focalise la vue « Carte & passages » sur le carré du passage")
    void voir_sur_la_carte_focalise_le_carre(FxRobot robot) {
        Button voir = robot.lookup("#boutonVoirCarte").queryAs(Button.class);

        robot.interact(voir::fire);

        assertThat(carteFocalisee.get())
                .as("ouvre le multi-sites centré sur le carré du contexte (640380)")
                .isEqualTo("640380");
    }
}
