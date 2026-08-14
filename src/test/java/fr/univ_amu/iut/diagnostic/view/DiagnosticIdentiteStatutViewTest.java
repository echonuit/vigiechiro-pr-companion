package fr.univ_amu.iut.diagnostic.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.view.NavigationDeTestModule;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.diagnostic.model.AnalyseAnomalies;
import fr.univ_amu.iut.diagnostic.model.CoherenceHoraire;
import fr.univ_amu.iut.diagnostic.model.Diagnostic;
import fr.univ_amu.iut.diagnostic.model.MesureClimatique;
import fr.univ_amu.iut.diagnostic.model.SerieClimatique;
import fr.univ_amu.iut.diagnostic.model.ServiceDiagnostic;
import fr.univ_amu.iut.diagnostic.viewmodel.DiagnosticViewModel;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

/// L'identité du passage reste dans la barre de statut de **M-Diagnostic**, y compris quand l'ouverture
/// échoue (#3548).
///
/// Le binding des trois zones est câblé dans `initialize()`, donc **avant** `ouvrirSur` : `bind()` le
/// calcule d'emblée (`ExpressionHelper#addListener` lit la valeur de référence), avec un contexte encore
/// nul. La zone gauche ne redevient juste que si une dépendance **déclarée** change ensuite. Sur le
/// chemin d'erreur, aucune ne change : `ouvrirSur` appelle `reinitialiser()`, qui réécrit chaque
/// dépendance à la valeur qu'elle a déjà, et route l'erreur dans `retour`, qui n'est pas déclaré.
///
/// Le défaut est atteignable : `ServiceDiagnostic#diagnostiquer` lève quand la session d'enregistrement
/// manque, et la carte « Diagnostic matériel » de M-Passage n'est gatée que par le drapeau de
/// fonctionnalité. Un passage dont la carte SD n'a pas été importée suffit.
///
/// Chaque mesure a son **témoin** dans le même dispositif : sans lui, une zone vide pourrait être une
/// cécité du test plutôt qu'un défaut de l'écran.
@ExtendWith(ApplicationExtension.class)
class DiagnosticIdentiteStatutViewTest {

    private static final ContextePassage CONTEXTE =
            new ContextePassage(42L, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière"));

    private ServiceDiagnostic service;
    private DiagnosticController controleur;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceDiagnostic.class);
        // Chemin d'erreur par défaut : « Session d'enregistrement introuvable pour le passage 42 ».
        doThrow(new IllegalStateException("Session d'enregistrement introuvable pour le passage 42."))
                .when(service)
                .diagnostiquer(anyLong());
        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Provides
                    DiagnosticViewModel viewModel() {
                        return new DiagnosticViewModel(service);
                    }
                },
                new NavigationDeTestModule());
        FXMLLoader loader = new FXMLLoader(DiagnosticController.class.getResource("Diagnostic.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        // On n'ouvre pas ici : chaque test choisit son chemin, comme la navigation le fait (contrôleur
        // neuf, puis `ouvrirSur`, puis lecture de la barre par le chrome).
        stage.setScene(new Scene(vue, 1000, 760));
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
        // `when(mock.methode(...))` appellerait la méthode, donc lèverait : il faut `doReturn`.
        doReturn(new Diagnostic(
                        42L,
                        7L,
                        "1925492",
                        new AnalyseAnomalies(List.of(), List.of()),
                        SerieClimatique.presente(List.of(
                                new MesureClimatique(LocalDate.of(2026, 6, 22), LocalTime.of(22, 0), 18.5, 72))),
                        43.5,
                        5.4,
                        LocalDateTime.of(2026, 6, 23, 8, 0),
                        8.5,
                        CoherenceHoraire.indisponible()))
                .when(service)
                .diagnostiquer(anyLong());

        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));

        assertThat(controleur.zonesStatutProperty().get().gauche()).isEqualTo(CONTEXTE.identiteStatut());
    }
}
