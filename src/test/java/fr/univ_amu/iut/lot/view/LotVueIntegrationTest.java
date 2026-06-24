package fr.univ_amu.iut.lot.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.Alerte;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.NavigationDeTestModule;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Tests d'intégration TestFX **complémentaires** de l'écran **M-Lot** (`Lot.fxml` +
/// [LotController]).
///
/// Là où [LotViewTest] couvre l'affichage de base (statut/récap/dossier sur un passage `Vérifié` et
/// délégation du clic « Préparer »), ce fichier cible les comportements que l'audit 2026-06-18
/// signale comme souvent absents derrière un ViewModel pourtant complet : la **zone d'alertes** (R14)
/// qui n'apparaît qu'en présence d'alertes bloquantes, le **récapitulatif dérivé** de l'[EtatLot]
/// (volume lisible avec bascule Mo, garde `null`), le parcours **Marquer déposé** (état `Prêt à
/// déposer`) et le message de l'état terminal `Déposé`.
///
/// Chaque test fait un **vrai lookup** des contrôles par `fx:id` (`robot.lookup("#…")`) puis exerce
/// soit un toggle de visibilité, soit une **interaction** (clic). Un écran resté à l'état placeholder
/// (sans `@FXML` ni `onAction`) échoue donc, contrairement aux tests qui ne liraient que les
/// propriétés du ViewModel. L'état affiché provient du [ServiceLot] mocké : pour exercer un autre
/// état que la fixture de départ, on **re-stube** `consulterLot` puis on rouvre l'écran sur le fil
/// JavaFX via [#reouvrirAvec]. Aucune base de données.
@ExtendWith(ApplicationExtension.class)
class LotVueIntegrationTest {

    private static final long ID_PASSAGE = 42L;
    private static final ContextePassage CONTEXTE =
            new ContextePassage(ID_PASSAGE, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière"));

    private ServiceLot service;
    private LotController controleur;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceLot.class);
        // État de départ : passage Vérifié, aucune alerte → préparer actif, déposer inactif.
        when(service.consulterLot(anyLong()))
                .thenReturn(new EtatLot(StatutWorkflow.VERIFIE, "/ws/session-42", 2, 8192L, List.of(), null));
        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Provides
                    LotViewModel viewModel() {
                        return new LotViewModel(service);
                    }
                },
                new NavigationDeTestModule());
        FXMLLoader loader = new FXMLLoader(LotController.class.getResource("Lot.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        controleur.ouvrirSur(CONTEXTE);
        stage.setScene(new Scene(vue, 900, 640));
        stage.show();
    }

    /// Re-stube `consulterLot` pour renvoyer `etat`, puis rouvre l'écran sur le même passage depuis
    /// le fil JavaFX. Les propriétés du ViewModel étant liées aux contrôles, la vue se met à jour.
    private void reouvrirAvec(FxRobot robot, EtatLot etat) {
        when(service.consulterLot(anyLong())).thenReturn(etat);
        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));
    }

    @Test
    @DisplayName("Emplacement (fil d'Ariane) : Mes sites › Carré N › Détails du passage N° X › Préparer le dépôt")
    void emplacement_reflete_le_passage() {
        assertThat(controleur.emplacement())
                .extracting(Lieu::libelle)
                .containsExactly("Mes sites", "Carré 640380", "Détails du passage N° 2", "Préparer le dépôt");
        assertThat(controleur.emplacement().get(0).estCliquable()).isTrue();
        assertThat(controleur.emplacement().get(3).estCliquable()).isFalse();
    }

    @Test
    @DisplayName("Sans alerte : zone d'alertes et message masqués, préparer actif / déposer inactif")
    void sans_alerte_zone_et_message_masques(FxRobot robot) {
        Label statut = robot.lookup("#lblStatut").queryAs(Label.class);
        Label recap = robot.lookup("#lblRecap").queryAs(Label.class);
        Label chemin = robot.lookup("#lblCheminDossier").queryAs(Label.class);
        VBox zoneAlertes = robot.lookup("#zoneAlertes").queryAs(VBox.class);
        ListView<?> listeAlertes = robot.lookup("#listeAlertes").queryAs(ListView.class);
        Label message = robot.lookup("#lblMessage").queryAs(Label.class);
        Button preparer = robot.lookup("#btnPreparer").queryAs(Button.class);
        Button deposer = robot.lookup("#btnDeposer").queryAs(Button.class);

        assertThat(statut.getText()).isEqualTo("Vérifié");
        assertThat(recap.getText()).isEqualTo("2 séquences · 8 Ko");
        assertThat(chemin.getText()).isEqualTo("/ws/session-42");
        // R14 : pas d'alerte bloquante → la zone est repliée (ni visible, ni gérée par le layout).
        assertThat(listeAlertes.getItems()).isEmpty();
        assertThat(zoneAlertes.isVisible()).isFalse();
        assertThat(zoneAlertes.isManaged()).isFalse();
        // Pas de message d'état en fonctionnement nominal.
        assertThat(message.isVisible()).isFalse();
        assertThat(preparer.isDisabled()).isFalse();
        assertThat(deposer.isDisabled()).isTrue();
    }

    @Test
    @DisplayName("Alertes bloquantes (R14) : zone visible, alertes listées, préparer désactivé")
    void alertes_bloquantes_listees_et_preparer_desactive(FxRobot robot) {
        reouvrirAvec(
                robot,
                new EtatLot(
                        StatutWorkflow.VERIFIE,
                        "/ws/session-42",
                        2,
                        8192L,
                        List.of(
                                Alerte.bloquante("Transformation incomplète"),
                                Alerte.bloquante("Préfixe de fichier non conforme")),
                        null));

        VBox zoneAlertes = robot.lookup("#zoneAlertes").queryAs(VBox.class);
        @SuppressWarnings("unchecked")
        ListView<String> listeAlertes = robot.lookup("#listeAlertes").queryAs(ListView.class);
        Label message = robot.lookup("#lblMessage").queryAs(Label.class);
        Button preparer = robot.lookup("#btnPreparer").queryAs(Button.class);

        assertThat(zoneAlertes.isVisible()).isTrue();
        assertThat(zoneAlertes.isManaged()).isTrue();
        // La liste expose les messages des alertes bloquantes, pas un libellé codé en dur.
        assertThat(listeAlertes.getItems())
                .containsExactly("Transformation incomplète", "Préfixe de fichier non conforme");
        // Tant que la cohérence n'est pas corrigée, la préparation est interdite (R14).
        assertThat(preparer.isDisabled()).isTrue();
        assertThat(message.isVisible()).isTrue();
        assertThat(message.getText()).contains("Cohérence");
    }

    @Test
    @DisplayName("Le récapitulatif est dérivé de l'EtatLot (volume avec bascule Mo)")
    void recap_derive_de_l_etat_lot_avec_bascule_mo(FxRobot robot) {
        // 5 séquences, ~3 Mo (3 × 1 048 576 octets) : le récap doit refléter ces valeurs, pas la
        // fixture de départ « 2 séquences · 8 Ko ».
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.VERIFIE, "/ws/session-42", 5, 3_145_728L, List.of(), null));

        Label recap = robot.lookup("#lblRecap").queryAs(Label.class);

        assertThat(recap.getText()).isEqualTo("5 séquences · 3 Mo");
    }

    @Test
    @DisplayName("Récapitulatif sans volume calculé : « volume inconnu », sans erreur")
    void recap_volume_inconnu_sans_erreur(FxRobot robot) {
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.VERIFIE, "/ws/session-42", 5, null, List.of(), null));

        Label recap = robot.lookup("#lblRecap").queryAs(Label.class);

        assertThat(recap.getText()).isEqualTo("5 séquences · volume inconnu");
    }

    @Test
    @DisplayName("« Prêt à déposer » : déposer actif, préparer inactif ; le clic délègue au service")
    void pret_a_deposer_active_deposer_et_clic_delegue(FxRobot robot) {
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 2, 8192L, List.of(), null));

        Button preparer = robot.lookup("#btnPreparer").queryAs(Button.class);
        Button deposer = robot.lookup("#btnDeposer").queryAs(Button.class);

        assertThat(preparer.isDisabled()).isTrue();
        assertThat(deposer.isDisabled()).isFalse();

        robot.clickOn("#btnDeposer");
        verify(service).marquerDepose(ID_PASSAGE);
    }

    @Test
    @DisplayName("État « Déposé » : statut affiché, message de dépôt visible, actions désactivées")
    void statut_depose_affiche_message_et_desactive_actions(FxRobot robot) {
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.DEPOSE, "/ws/session-42", 2, 8192L, List.of(), "2026-06-18"));

        Label statut = robot.lookup("#lblStatut").queryAs(Label.class);
        Label message = robot.lookup("#lblMessage").queryAs(Label.class);
        Button preparer = robot.lookup("#btnPreparer").queryAs(Button.class);
        Button deposer = robot.lookup("#btnDeposer").queryAs(Button.class);

        assertThat(statut.getText()).isEqualTo("Déposé");
        assertThat(message.isVisible()).isTrue();
        assertThat(message.getText()).contains("déposé");
        assertThat(preparer.isDisabled()).isTrue();
        assertThat(deposer.isDisabled()).isTrue();
    }
}
