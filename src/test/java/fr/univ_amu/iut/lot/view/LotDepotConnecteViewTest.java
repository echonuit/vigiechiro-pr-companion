package fr.univ_amu.iut.lot.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.api.ResultatLancement;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.view.NavigationDeTestModule;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.StatutDepotUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import java.util.List;
import java.util.Optional;
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

/// Écran **M-Lot en mode connecté** (#984) : les tests d'intégration existants
/// ([LotVueIntegrationTest]) montent l'écran **sans** dépôt VigieChiro (`Optional.empty()`), donc sans
/// participation liée — ils ne peuvent pas exercer l'étape ④ dans son mode « Lancer la participation »
/// ni le bouton « Réinitialiser le dépôt ». Ce fichier monte l'écran **avec** un [DepotVigieChiro]
/// mocké et couvre les liaisons que le chantier #984 a ajoutées.
///
/// Le passage de départ est en « **Dépôt en cours** » avec un plan de dépôt déjà rempli : c'est l'état
/// réel **après** un téléversement par l'API, et celui qui a fait apparaître la régression corrigée
/// (bouton ④ désactivé dès la fin de l'upload, alors que c'est précisément le moment de lancer le
/// traitement serveur).
@ExtendWith(ApplicationExtension.class)
class LotDepotConnecteViewTest {

    private static final long ID_PASSAGE = 42L;
    private static final ContextePassage CONTEXTE =
            new ContextePassage(ID_PASSAGE, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière"));

    private ServiceLot service;
    private DepotVigieChiro depot;
    private LotController controleur;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceLot.class);
        depot = mock(DepotVigieChiro.class);
        // État réel après un dépôt par l'API : nuit téléversée (plan rempli), participation liée.
        when(service.consulterLot(anyLong()))
                .thenReturn(new EtatLot(StatutWorkflow.DEPOT_EN_COURS, "/ws/session-42", 2, 8192L, List.of(), null));
        when(service.unitesDepot(ID_PASSAGE)).thenReturn(List.of(unite("Car-1.zip", StatutDepotUnite.DEPOSE)));
        when(depot.participationLiee(ID_PASSAGE)).thenReturn(true);

        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Provides
                    LotViewModel viewModel() {
                        return new LotViewModel(service);
                    }

                    @Provides
                    DepotViewModel depotViewModel() {
                        return new DepotViewModel(service, Optional.of(depot));
                    }

                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        return lien -> {};
                    }
                },
                new NavigationDeTestModule());
        FXMLLoader loader = new FXMLLoader(LotController.class.getResource("Lot.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        controleur.definirConfirmateur(message -> true); // pas de dialogue natif bloquant sous TestFX
        controleur.ouvrirSur(CONTEXTE);
        stage.setScene(new Scene(vue, 980, 980));
        stage.show();
    }

    @Test
    @DisplayName("#984 : participation liée → l'étape ④ devient « Lancer la participation », cliquable même"
            + " en « Dépôt en cours »")
    void participation_liee_bascule_le_bouton_et_le_garde_actif(FxRobot robot) {
        Button deposer = robot.lookup("#btnDeposer").queryAs(Button.class);

        assertThat(deposer.getText()).isEqualTo("🚀 Lancer la participation");
        // Régression : la garde « Marquer déposé » (statut « Prêt à déposer ») désactivait le bouton dès la
        // fin de l'upload — or c'est justement là qu'il faut pouvoir lancer le traitement.
        assertThat(deposer.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("#984 : clic sur « Lancer la participation » → le compte rendu du compute est demandé au moteur")
    void clic_lance_le_traitement_serveur(FxRobot robot) {
        when(depot.lancerTraitement(ID_PASSAGE)).thenReturn(ResultatLancement.accepte());

        robot.clickOn("#btnDeposer");

        // Le compute part sur un fil de fond (executerEnFond) : on laisse le temps à l'appel d'arriver.
        verify(depot, timeout(5_000)).lancerTraitement(ID_PASSAGE);
    }

    @Test
    @DisplayName("#984 : « Réinitialiser le dépôt » visible dès qu'un plan existe et efface le suivi local")
    void reinitialiser_efface_le_suivi_local(FxRobot robot) {
        Button reinitialiser = robot.lookup("#btnReinitialiserDepot").queryAs(Button.class);
        assertThat(reinitialiser.isVisible()).isTrue();
        assertThat(reinitialiser.isDisabled()).isFalse();

        robot.clickOn("#btnReinitialiserDepot");

        verify(service).reinitialiserDepot(ID_PASSAGE);
    }

    @Test
    @DisplayName("#984 : sans participation liée, l'étape ④ reste « Marquer déposé » (dépôt manuel)")
    void sans_participation_le_bouton_reste_marquer_depose(FxRobot robot) {
        when(depot.participationLiee(ID_PASSAGE)).thenReturn(false);
        robot.interact(() -> controleur.ouvrirSur(CONTEXTE)); // réhydrate depuis le lien local

        assertThat(robot.lookup("#btnDeposer").queryAs(Button.class).getText()).isEqualTo("✅ Marquer déposé");
    }

    private static DepotUnite unite(String identifiant, StatutDepotUnite statut) {
        return new DepotUnite(
                1L, ID_PASSAGE, identifiant, TypeDepotUnite.ZIP, statut, null, null, "2026-07-11T15:00:00");
    }
}
