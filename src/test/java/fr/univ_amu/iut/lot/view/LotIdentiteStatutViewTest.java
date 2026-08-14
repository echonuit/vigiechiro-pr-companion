package fr.univ_amu.iut.lot.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.view.NavigationDeTestModule;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import fr.univ_amu.iut.lot.viewmodel.TraitementViewModel;
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

/// L'identité du passage reste dans la barre de statut de **M-Lot**, y compris quand l'ouverture échoue
/// (#3548). Même mécanique que sur M-Diagnostic : le binding se calcule au `bind()`, avec un contexte
/// encore nul, et le chemin d'erreur de `LotViewModel#ouvrirSur` ne change aucune dépendance déclarée
/// (`reinitialiser()` réécrit des valeurs déjà en place, l'erreur part dans `messages`).
///
/// Le déclencheur y est plus rare que sur M-Diagnostic : `consulterLot` tolère une session absente et ne
/// lève que sur un **passage** introuvable, ce qui demande un état périmé ou concurrent.
@ExtendWith(ApplicationExtension.class)
class LotIdentiteStatutViewTest {

    private static final ContextePassage CONTEXTE =
            new ContextePassage(42L, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière"));

    private ServiceLot service;
    private LotController controleur;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceLot.class);
        doThrow(new IllegalStateException("Passage introuvable : 42"))
                .when(service)
                .consulterLot(anyLong());
        DepotVigieChiro depot = mock(DepotVigieChiro.class);
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
                    TraitementViewModel traitementViewModel() {
                        return new TraitementViewModel(Optional.empty(), Horloge.systeme());
                    }

                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        return url -> {};
                    }
                },
                new NavigationDeTestModule());
        FXMLLoader loader = new FXMLLoader(LotController.class.getResource("Lot.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        stage.setScene(new Scene(vue, 900, 640));
        stage.show();
    }

    @Test
    @DisplayName("#3548 : l'ouverture échoue, la barre de statut dit quand même de quel passage il s'agit")
    void identite_presente_apres_une_erreur_d_ouverture(FxRobot robot) {
        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));

        assertThat(controleur.zonesStatutProperty().get().gauche())
                .as("le contexte doit être une dépendance DÉCLARÉE du binding")
                .isEqualTo(CONTEXTE.identiteStatut());
    }

    @Test
    @DisplayName("Témoin : sur le chemin de succès, le dispositif voit bien l'identité")
    void temoin_le_dispositif_voit_l_identite(FxRobot robot) {
        doReturn(new EtatLot(StatutWorkflow.VERIFIE, "/ws/session-42", 2, 8192L, List.of(), null))
                .when(service)
                .consulterLot(anyLong());

        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));

        assertThat(controleur.zonesStatutProperty().get().gauche()).isEqualTo(CONTEXTE.identiteStatut());
    }
}
