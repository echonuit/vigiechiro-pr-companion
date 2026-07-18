package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.passage.model.DecompteAudio;
import fr.univ_amu.iut.passage.model.RapportReactivation;
import fr.univ_amu.iut.passage.model.VerdictIdentite.NiveauConfiance;
import fr.univ_amu.iut.passage.model.VoieReactivation;
import fr.univ_amu.iut.passage.viewmodel.ReactivationModaleViewModel;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de la modale **« Réactiver ce passage »** (#1780) : exécuteur **synchrone**,
/// travail bouchonné (aucun service). On vérifie que les **deux phases** vont sur leurs barres respectives
/// (la barre d'ancrage n'apparaît que si la phase se déclenche), que le **compte rendu** honnête s'affiche
/// dans la modale, qu'un **échec** devient un message (pas une exception muette) et qu'une **annulation**
/// est un état neutre. La fermeture rafraîchit l'écran appelant.
@ExtendWith(ApplicationExtension.class)
class ReactivationModaleViewTest {

    private ReactivationModaleController controleur;
    private Stage stage;
    private final AtomicBoolean appelantRafraichi = new AtomicBoolean(false);

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            ReactivationModaleViewModel viewModel() {
                return new ReactivationModaleViewModel();
            }

            @Provides
            ExecuteurTache executeur() {
                return new ExecuteurTacheSynchrone();
            }
        });
        FXMLLoader loader = new FXMLLoader(ReactivationModaleController.class.getResource("ReactivationModale.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        stage.setScene(new Scene(vue));
        // Comme NavigationPassage : le rafraîchissement de l'appelant joue à TOUTE fermeture.
        stage.setOnHidden(evenement -> controleur.rafraichirSiReactive());
        stage.show();
    }

    /// Lance la modale avec un travail donné. Exécuteur synchrone : à la fin de l'interaction, le travail
    /// est terminé et la modale affiche son état final.
    private void lancer(ReactivationModaleController.Travail travail, FxRobot robot) {
        robot.interact(() -> controleur.demarrer(travail, () -> appelantRafraichi.set(true)));
    }

    @Test
    @DisplayName("Réactivation réussie : le compte rendu s'affiche dans la modale, sur quelle preuve")
    void reactivation_reussie_affiche_le_compte_rendu(FxRobot robot) {
        lancer(
                (progresRegeneration, progresAncrage, jeton) -> new RapportReactivation(
                        30,
                        0,
                        0,
                        0,
                        NiveauConfiance.CERTITUDE,
                        List.of(),
                        new DecompteAudio(30, 30),
                        VoieReactivation.TRANSFORMES),
                robot);

        assertThat(robot.lookup("#lblCompteRendu").queryAs(Label.class).getText())
                .contains("réactivée(s)")
                .contains("30")
                .contains("écoutable");
        assertThat(robot.lookup("#boutonFermer").queryButton().isDisabled())
                .as("l'opération est terminée : « Fermer » redevient actif")
                .isFalse();
    }

    @Test
    @DisplayName("Sans phase d'ancrage, la seconde barre reste masquée")
    void sans_ancrage_la_barre_d_ancrage_reste_masquee(FxRobot robot) {
        lancer(
                (progresRegeneration, progresAncrage, jeton) -> {
                    progresRegeneration.accept(new Progression("Vérification 3/3", 1.0));
                    return new RapportReactivation(
                            3,
                            0,
                            0,
                            0,
                            NiveauConfiance.FORTE,
                            List.of(),
                            new DecompteAudio(3, 3),
                            VoieReactivation.TRANSFORMES);
                },
                robot);

        assertThat(robot.lookup("#zoneAncrage").queryAs(HBox.class).isVisible())
                .as("aucun ancrage n'a été émis : la barre d'ancrage n'apparaît pas")
                .isFalse();
    }

    @Test
    @DisplayName("#1780 : quand la phase d'ancrage démarre, sa barre apparaît (plus de barre figée à 100 %)")
    void la_phase_d_ancrage_revele_sa_barre(FxRobot robot) {
        lancer(
                (progresRegeneration, progresAncrage, jeton) -> {
                    progresRegeneration.accept(new Progression("Vérification 30/30", 1.0));
                    progresAncrage.accept(
                            new Progression("Récupération des identifiants depuis VigieChiro… (page 1/2)", 0.5));
                    return new RapportReactivation(
                            30,
                            0,
                            0,
                            0,
                            NiveauConfiance.CERTITUDE,
                            List.of(),
                            new DecompteAudio(30, 30),
                            VoieReactivation.TRANSFORMES);
                },
                robot);

        assertThat(robot.lookup("#zoneAncrage").queryAs(HBox.class).isVisible())
                .as("la phase réseau a émis : sa barre est révélée")
                .isTrue();
        // Le libellé dit le sens réel de l'échange (#1853) : cette phase *récupère* les identifiants, elle
        // n'écrit rien sur la plateforme — « Ancrage … sur VigieChiro » laissait croire l'inverse.
        assertThat(robot.lookup("#lblAncrage").queryAs(Label.class).getText())
                .contains("Récupération des identifiants depuis VigieChiro");
    }

    @Test
    @DisplayName("#1648 : passage reconstruit : compte rendu honnête, jamais « introuvables »")
    void passage_reconstruit_compte_rendu_honnete(FxRobot robot) {
        lancer(
                (progresRegeneration, progresAncrage, jeton) -> new RapportReactivation(
                        0, 0, 30, 0, null, List.of(), new DecompteAudio(0, 30), VoieReactivation.RECONSTRUIT),
                robot);

        String compteRendu =
                robot.lookup("#lblCompteRendu").queryAs(Label.class).getText();
        assertThat(compteRendu).contains("reconstruit depuis VigieChiro");
        assertThat(compteRendu)
                .as("les fichiers peuvent être présents : ne pas prétendre le contraire")
                .doesNotContain("introuvables");
    }

    @Test
    @DisplayName("Un échec devient un message dans la modale, pas une exception muette")
    void echec_devient_un_message(FxRobot robot) {
        lancer(
                (progresRegeneration, progresAncrage, jeton) -> {
                    throw new RegleMetierException("Dossier introuvable : /media/absent.");
                },
                robot);

        assertThat(robot.lookup("#lblErreur").queryAs(Label.class).getText()).contains("Dossier introuvable");
    }

    @Test
    @DisplayName("Annulation : état neutre (rien n'est défait), pas d'erreur rouge")
    void annulation_est_un_etat_neutre(FxRobot robot) {
        lancer(
                (progresRegeneration, progresAncrage, jeton) -> {
                    throw new OperationAnnuleeException();
                },
                robot);

        assertThat(robot.lookup("#lblCompteRendu").queryAs(Label.class).getText())
                .contains("annulée");
        assertThat(robot.lookup("#lblErreur").queryAs(Label.class).getText())
                .as("une annulation n'est pas une erreur : rien en rouge")
                .isEmpty();
    }

    @Test
    @DisplayName("Fermer après une réactivation conclue rafraîchit l'écran appelant (audio revenu)")
    void fermer_rafraichit_l_appelant(FxRobot robot) {
        lancer(
                (progresRegeneration, progresAncrage, jeton) -> new RapportReactivation(
                        30,
                        0,
                        0,
                        0,
                        NiveauConfiance.CERTITUDE,
                        List.of(),
                        new DecompteAudio(30, 30),
                        VoieReactivation.TRANSFORMES),
                robot);

        robot.interact(() -> robot.lookup("#boutonFermer").queryButton().fire());

        assertThat(appelantRafraichi)
                .as("M-Passage se recharge : le passage redevenu écoutable apparaît")
                .isTrue();
    }
}
