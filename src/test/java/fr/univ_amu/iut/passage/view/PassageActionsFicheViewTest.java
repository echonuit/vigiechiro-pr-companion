package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
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
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.ServiceReactivationPassage;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Les trois gestes **destructifs ou correctifs** de la fiche d'un passage, cliqués pour de vrai
/// (#1405) : supprimer, annuler le dépôt, purger les originaux de la nuit.
///
/// Ils n'étaient couverts par **rien**. Le seul test qui les nommait disait « le bouton Supprimer est
/// présent et actif » - c'est-à-dire tout sauf ce qui compte. La cause était mécanique, et la même
/// partout : le refus se terminait par un `Alert.showAndWait()` en dur, qui **fige** un test headless.
/// On ne pouvait donc pas cliquer, seulement regarder le bouton.
///
/// Le refus passe maintenant par le port [Notificateur], le oui/non passait déjà par `Confirmateur` :
/// les deux dialogues deviennent des doubles, et l'on peut enfin vérifier ce qui compte vraiment sur
/// une **suppression en cascade** - qu'elle prévient de ce qu'elle emporte, et qu'un « Annuler »
/// annule.
///
/// La fixture est un passage **vérifié** (la suppression est fermée sur un passage déposé) ; les tests
/// qui ont besoin d'un autre statut rouvrent l'écran dessus.
@ExtendWith(ApplicationExtension.class)
class PassageActionsFicheViewTest {

    private static final long ID_PASSAGE = 42L;
    private static final ContexteSite CONTEXTE = new ContexteSite("640380", "A1", "Étang de la Tuilière");
    private static final Path SESSION = Path.of("/data/Car640380-2026-Pass2-A1");

    /// Ce que le confirmateur a **demandé** : sur une cascade, le message est le seul avertissement.
    private final List<String> confirmations = new ArrayList<>();

    /// Ce que le notificateur a **dit**, au lieu de l'afficher.
    private final List<String> annonces = new ArrayList<>();

    private final List<NiveauNotification> niveaux = new ArrayList<>();

    /// Ce que le double de confirmation répondra : chaque test le pose avant de cliquer.
    private boolean confirme = true;

    /// Validations de l'observateur menacées par la cascade (0 = la nuit n'en porte aucune).
    private int validationsMenacees;

    private ServicePassage service;
    private ServicePurgeOriginaux purge;
    private PassageController controleur;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServicePassage.class);
        purge = mock(ServicePurgeOriginaux.class);
        when(service.detailPassage(anyLong())).thenReturn(detail(StatutWorkflow.VERIFIE));
        ServiceArchivagePassage archivage = mock(ServiceArchivagePassage.class);
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
                return idPassage -> validationsMenacees;
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
        controleur.confirmateur().definir(message -> {
            confirmations.add(message);
            return confirme;
        });
        controleur.notificateur().definir((niveau, entete, message) -> {
            niveaux.add(niveau);
            annonces.add(entete + " | " + message);
        });
        controleur.ouvrirSur(ID_PASSAGE, CONTEXTE);
        stage.setScene(new Scene(vue, 1100, 700));
        stage.show();
    }

    /// Passage de la fixture, dans le statut voulu : une nuit qui porte encore ses bruts (1 Go).
    private static DetailPassage detail(StatutWorkflow statut) {
        return new DetailPassage(
                2,
                2026,
                "2026-06-22",
                "20:25:00",
                "07:47:00",
                "1925492",
                statut,
                Verdict.OK,
                null,
                4_294_967_296L,
                1_073_741_824L,
                30,
                150.0,
                null,
                new DecompteAudio(30, 30));
    }

    /// Rouvre l'écran sur un passage dans un autre statut (les boutons s'ouvrent ou se ferment avec lui).
    private void rouvrirEn(FxRobot robot, StatutWorkflow statut) {
        when(service.detailPassage(anyLong())).thenReturn(detail(statut));
        robot.interact(() -> controleur.ouvrirSur(ID_PASSAGE, CONTEXTE));
    }

    private void cliquer(FxRobot robot, String bouton) {
        robot.interact(() -> robot.lookup(bouton).queryButton().fire());
    }

    @Test
    @DisplayName(
            "#1405 : « Supprimer » confirmé : la nuit est supprimée et l'écran est quitté (le passage n'existe plus)")
    void suppression_confirmee_supprime_et_quitte_l_ecran(FxRobot robot) {
        cliquer(robot, "#boutonSupprimer");

        assertThat(confirmations)
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .contains("Supprimer définitivement")
                        .as("la cascade emporte toute la nuit, pas seulement la fiche")
                        .contains("toute sa nuit"));
        verify(service).supprimer(ID_PASSAGE);
    }

    @Test
    @DisplayName("#786 : la confirmation de suppression annonce les validations de l'observateur qu'elle va perdre")
    void suppression_annonce_les_validations_menacees(FxRobot robot) {
        validationsMenacees = 7;

        cliquer(robot, "#boutonSupprimer");

        // Ces validations sont un travail humain : contrairement à un CSV, rien ne les régénère.
        assertThat(confirmations)
                .singleElement()
                .satisfies(message ->
                        assertThat(message).contains("7 validation(s)").contains("définitivement perdues"));
        verify(service).supprimer(ID_PASSAGE);
    }

    @Test
    @DisplayName("#1405 : « Supprimer » refusé : la nuit est intacte")
    void suppression_refusee_ne_supprime_rien(FxRobot robot) {
        confirme = false;

        cliquer(robot, "#boutonSupprimer");

        verify(service, never()).supprimer(anyLong());
        assertThat(annonces).isEmpty();
    }

    @Test
    @DisplayName("#1405 : refus métier de la suppression : l'utilisateur est averti, l'écran ne bouge pas")
    void suppression_refusee_par_le_service_est_annoncee(FxRobot robot) {
        doThrow(new RegleMetierException("Ce passage est déposé : annulez d'abord le dépôt."))
                .when(service)
                .supprimer(ID_PASSAGE);

        cliquer(robot, "#boutonSupprimer");

        assertThat(niveaux).containsExactly(NiveauNotification.AVERTISSEMENT);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce ->
                        assertThat(annonce).contains("Suppression impossible").contains("annulez d'abord le dépôt"));
    }

    @Test
    @DisplayName("#1405 : « Annuler le dépôt » confirmé : le passage revient à « Prêt à déposer »")
    void annulation_du_depot_confirmee(FxRobot robot) {
        rouvrirEn(robot, StatutWorkflow.DEPOSE);

        cliquer(robot, "#boutonAnnulerDepot");

        // Ce qui rassure ici, c'est ce qu'on NE perd PAS : le travail de validation est conservé.
        assertThat(confirmations)
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .contains("Prêt à déposer")
                        .contains("validations Tadarida déjà saisies sont conservées"));
        verify(service).annulerDepot(ID_PASSAGE);
    }

    @Test
    @DisplayName("#1405 : refus métier de l'annulation de dépôt : l'utilisateur est averti")
    void annulation_du_depot_refusee_par_le_service(FxRobot robot) {
        rouvrirEn(robot, StatutWorkflow.DEPOSE);
        doThrow(new RegleMetierException("Ce passage n'est pas déposé."))
                .when(service)
                .annulerDepot(ID_PASSAGE);

        cliquer(robot, "#boutonAnnulerDepot");

        assertThat(niveaux).containsExactly(NiveauNotification.AVERTISSEMENT);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce -> assertThat(annonce).contains("Annulation impossible"));
    }

    @Test
    @DisplayName("#1405 : « Purger les originaux » confirmé : les bruts de la nuit partent, et le geste est marqué")
    void purge_de_la_nuit_confirmee(FxRobot robot) {
        when(service.cheminSession(ID_PASSAGE)).thenReturn(Optional.of(SESSION));

        cliquer(robot, "#boutonPurger");

        assertThat(confirmations)
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .contains("cette suppression est définitive")
                        .as("ce qui reste après la purge est ce qui décide l'utilisateur")
                        .contains("Les séquences d'écoute, la validation et le dépôt sont conservés"));
        verify(purge).purgerSession(SESSION);
        // Sans ce marquage, l'audit prendrait les bruts purgés pour une corruption.
        verify(service).marquerOriginauxPurges(ID_PASSAGE);
    }

    @Test
    @DisplayName("#1405 : « Purger les originaux » refusé : les bruts de la nuit sont intacts")
    void purge_de_la_nuit_refusee(FxRobot robot) {
        confirme = false;

        cliquer(robot, "#boutonPurger");

        verify(purge, never()).purgerSession(SESSION);
        verify(service, never()).marquerOriginauxPurges(anyLong());
        assertThat(annonces).isEmpty();
    }

    @Test
    @DisplayName("#1405 : purge interrompue (disque) : l'utilisateur est averti")
    void purge_de_la_nuit_interrompue_avertit(FxRobot robot) {
        when(service.cheminSession(ID_PASSAGE)).thenReturn(Optional.of(SESSION));
        doThrow(new IllegalStateException("fichier verrouillé")).when(purge).purgerSession(SESSION);

        cliquer(robot, "#boutonPurger");

        assertThat(niveaux).containsExactly(NiveauNotification.AVERTISSEMENT);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce ->
                        assertThat(annonce).contains("Purge impossible").contains("fichier verrouillé"));
    }
}
