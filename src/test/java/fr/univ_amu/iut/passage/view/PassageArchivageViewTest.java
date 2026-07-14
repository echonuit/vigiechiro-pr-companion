package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.commun.model.CompteurValidations;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.OuvrirDiagnostic;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.OuvrirValidation;
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.model.DecompteAudio;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.ServiceArchivagePassage;
import fr.univ_amu.iut.passage.model.ServiceArchivagePassage.BilanArchivage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.ServiceReactivationPassage;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Le **geste** d'archivage, cliqué pour de vrai (#1300, EPIC #1297).
///
/// Jusqu'ici, aucun test ne cliquait sur « Archiver ce passage » : on ne vérifiait que le **grisage**
/// de son bouton. La cause était mécanique - l'action se terminait par un `Alert.showAndWait()`, qui
/// **fige** TestFX headless. Le compte rendu passe désormais par le port [Notificateur], comme le
/// oui/non passait déjà par `Confirmateur` : les deux dialogues sont remplacés par des doubles, et le
/// clic devient testable **jusqu'à son effet**.
///
/// La fixture est un passage **déposé** (l'archivage n'est ouvert qu'à partir de là), avec un service
/// d'archivage bouchonné : ce qu'on vérifie ici, c'est le **câblage** du geste (le service, lui, est
/// couvert sur disque réel par `ServiceArchivagePassageTest`, et le cycle complet
/// archiver → réactiver par `CliArchivageTest`).
@ExtendWith(ApplicationExtension.class)
class PassageArchivageViewTest {

    private static final long ID_PASSAGE = 42L;

    /// Ce que le notificateur a **dit** : le double capture au lieu d'ouvrir un dialogue.
    private final List<String> annonces = new ArrayList<>();

    private final List<NiveauNotification> niveaux = new ArrayList<>();

    /// Ce que le confirmateur a **demandé** (le message de confirmation est un contenu à part entière :
    /// il annonce le gain, ce qu'on garde, ce qu'on perd, et que la perte peut être définitive).
    private final List<String> confirmations = new ArrayList<>();

    /// Ce que le double de confirmation répondra : chaque test le pose avant de cliquer.
    private boolean confirme = true;

    private ServiceArchivagePassage archivage;
    private PassageController controleur;

    @Start
    void start(Stage stage) throws Exception {
        ServicePassage service = mock(ServicePassage.class);
        archivage = mock(ServiceArchivagePassage.class);
        when(service.detailPassage(anyLong()))
                .thenReturn(new DetailPassage(
                        2,
                        2026,
                        "2026-06-22",
                        "20:25:00",
                        "07:47:00",
                        "1925492",
                        StatutWorkflow.DEPOSE,
                        Verdict.OK,
                        null,
                        4_294_967_296L,
                        1_073_741_824L,
                        30,
                        150.0,
                        null,
                        new DecompteAudio(30, 30)));
        ServicePurgeOriginaux purge = mock(ServicePurgeOriginaux.class);
        ServiceReactivationPassage reactivation = mock(ServiceReactivationPassage.class);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                // Écrans voisins : présents mais inertes (ce test ne navigue pas).
                OptionalBinder.newOptionalBinder(binder(), OuvrirDiagnostic.class)
                        .setBinding()
                        .toInstance(passage -> {});
                OptionalBinder.newOptionalBinder(binder(), OuvrirVerification.class)
                        .setBinding()
                        .toInstance(passage -> {});
                OptionalBinder.newOptionalBinder(binder(), OuvrirLot.class)
                        .setBinding()
                        .toInstance(passage -> {});
            }

            @Provides
            PassageViewModel viewModel() {
                return new PassageViewModel(service, purge, archivage, reactivation);
            }

            @Provides
            OuvrirValidation ouvrirValidation() {
                return passage -> {};
            }

            @Provides
            OuvrirMultisite ouvrirMultisite() {
                return carre -> {};
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
        Parent vue = loader.load();
        controleur = loader.getController();
        // Les deux dialogues du geste, remplacés par des doubles : le oui/non répond ce qu'on lui dit,
        // le compte rendu est capturé au lieu d'être affiché (un showAndWait figerait le test).
        controleur.confirmateur().definir(message -> {
            confirmations.add(message);
            return confirme;
        });
        controleur.notificateur().definir((niveau, entete, message) -> {
            niveaux.add(niveau);
            annonces.add(entete + " | " + message);
        });
        controleur.ouvrirSur(ID_PASSAGE, new ContexteSite("640380", "A1", "Étang de la Tuilière"));
        stage.setScene(new Scene(vue, 1100, 700));
        stage.show();
    }

    @Test
    @DisplayName("Passage déposé : « Archiver » confirme, archive, et annonce ce qui a été libéré")
    void clic_archive_et_rend_compte(FxRobot robot) {
        when(archivage.archiver(ID_PASSAGE)).thenReturn(new BilanArchivage(4_294_967_296L, 30));

        Button archiver = robot.lookup("#boutonArchiver").queryAs(Button.class);
        assertThat(archiver.isDisabled())
                .as("le passage est déposé et porte encore son audio : le geste est ouvert")
                .isFalse();
        robot.interact(archiver::fire);

        // La confirmation dit ce qu'on perd, et que la perte peut être DÉFINITIVE : c'est le coeur du
        // consentement, pas une formalité.
        assertThat(confirmations)
                .singleElement()
                .satisfies(message ->
                        assertThat(message).contains("libérer environ").contains("définitive"));
        verify(archivage).archiver(ID_PASSAGE);
        assertThat(niveaux).containsExactly(NiveauNotification.INFORMATION);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce -> assertThat(annonce)
                        .contains("Passage archivé")
                        .contains("libéré")
                        .as("le compte rendu redit ce qui reste consultable et ce qu'il faudra pour réécouter")
                        .contains("réimportez"));
    }

    @Test
    @DisplayName("Confirmation refusée : rien n'est archivé, rien n'est annoncé")
    void refus_de_confirmation_n_archive_rien(FxRobot robot) {
        confirme = false;

        robot.interact(() -> robot.lookup("#boutonArchiver").queryButton().fire());

        verify(archivage, never()).archiver(anyLong());
        assertThat(annonces).isEmpty();
    }

    @Test
    @DisplayName("Refus métier du service : l'utilisateur est averti, l'écran ne bouge pas")
    void refus_metier_est_annonce(FxRobot robot) {
        when(archivage.archiver(ID_PASSAGE))
                .thenThrow(new RegleMetierException("Ce passage n'est pas déposé : son audio est encore nécessaire."));

        robot.interact(() -> robot.lookup("#boutonArchiver").queryButton().fire());

        assertThat(niveaux).containsExactly(NiveauNotification.AVERTISSEMENT);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce ->
                        assertThat(annonce).contains("Archivage impossible").contains("encore nécessaire"));
    }
}
