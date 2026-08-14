package fr.univ_amu.iut.qualification.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.qualification.model.ContexteVerification;
import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import fr.univ_amu.iut.qualification.model.PreCheckNuit.Feu;
import fr.univ_amu.iut.qualification.model.SelectionDEcoute;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import fr.univ_amu.iut.qualification.viewmodel.QualificationViewModel;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import java.util.List;
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

/// L'identité du passage reste dans la barre de statut de **M-Qualification**, y compris quand
/// l'ouverture échoue (#3548).
///
/// Cet écran-ci était déjà juste avant la correction, mais **par accident** :
/// `SelectionEcouteViewModel#reinitialiser` termine par `recalculerProgression()`, qui pose
/// `progressionTexte` de `""` à `"Aucune séquence"`. Cette propriété est déclarée, donc le binding se
/// recalculait, et la zone gauche redevenait juste. Retirer ce libellé d'attente aurait cassé la barre
/// de statut sans qu'aucun test ne le voie. Ce test transforme l'accident en garde.
@ExtendWith(ApplicationExtension.class)
class QualificationIdentiteStatutViewTest {

    private static final long ID_PASSAGE = 42L;

    private static final ContextePassage CONTEXTE =
            new ContextePassage(ID_PASSAGE, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière"));

    private ServiceQualification service;
    private QualificationController controleur;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceQualification.class);
        doThrow(new IllegalStateException("Passage introuvable : 42"))
                .when(service)
                .precheck(anyLong());
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            QualificationViewModel verdict() {
                return new QualificationViewModel(service);
            }

            @Provides
            SelectionEcouteViewModel selection() {
                return new SelectionEcouteViewModel(service);
            }

            @Provides
            OuvrirPassage ouvrirPassage() {
                return (id, contexte) -> {};
            }

            // Façade de navigation (#1431) : inerte ici, ce test n'ouvre aucune modale.
            @Provides
            NavigationQualification navigation() {
                return mock(NavigationQualification.class);
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
        // Compte rendu neutralisé (#1509) : le vrai dialogue figerait le headless.
        controleur.notificateur().definir((niveau, entete, message) -> {});
        stage.setScene(new Scene(vue, 1100, 760));
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
        doReturn(new PreCheckNuit.Diagnostic(Feu.VERT, Feu.ORANGE, Feu.VERT))
                .when(service)
                .precheck(anyLong());
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
                .thenReturn(new SelectionDEcoute(7L, MethodeSelection.REPARTITION_TEMPORELLE, 0, ID_PASSAGE));
        when(service.detaillerSelection(anyLong())).thenReturn(List.of());

        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));

        assertThat(controleur.zonesStatutProperty().get().gauche()).isEqualTo(CONTEXTE.identiteStatut());
    }
}
