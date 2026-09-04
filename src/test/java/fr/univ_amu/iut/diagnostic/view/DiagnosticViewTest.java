package fr.univ_amu.iut.diagnostic.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.model.Completude;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.view.InfobulleDeBlocage;
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
import fr.univ_amu.iut.recette.Attente;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de l'écran **M-Diagnostic** : chargement du FXML via Guice (avec un
/// [ServiceDiagnostic] mocké), ouverture sur un passage, vérification du câblage (graphe à deux
/// séries T°/hygrométrie, listes d'anomalies/évènements, enregistreur). Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class DiagnosticViewTest {

    private DiagnosticController controleur;

    @Start
    void start(Stage stage) throws Exception {
        ServiceDiagnostic service = mock(ServiceDiagnostic.class);
        when(service.diagnostiquer(anyLong()))
                .thenReturn(new Diagnostic(
                        42L,
                        7L,
                        "1925492",
                        new AnalyseAnomalies(List.of("Réveil non programmé à 03:12"), List.of("Démarrage")),
                        SerieClimatique.presente(List.of(
                                new MesureClimatique(LocalDate.of(2026, 6, 22), LocalTime.of(22, 0), 18.5, 72),
                                new MesureClimatique(LocalDate.of(2026, 6, 23), LocalTime.of(2, 0), 14.0, 88))),
                        43.5,
                        5.4,
                        LocalDateTime.of(2026, 6, 23, 8, 0),
                        8.5,
                        CoherenceHoraire.indisponible(),
                        Completude.INCONNUE));
        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Provides
                    DiagnosticViewModel viewModel() {
                        return new DiagnosticViewModel(service);
                    }
                },
                new NavigationDeTestModule());
        FXMLLoader loader = new FXMLLoader(DiagnosticController.class.getResource("Diagnostic.fxml"));
        loader.setControllerFactory(DiagnosticGuice.pour(injector));
        Parent vue = loader.load();
        controleur = loader.getController();
        controleur.ouvrirSur(new ContextePassage(42L, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière")));
        FenetreAjustable.poser(stage, vue, 1000, 760);
        FenetreAjustable.afficher(stage);
    }

    @Test
    @DisplayName("Le graphe affiche deux séries (température + humidité) et les listes sont peuplées")
    void affiche_graphe_et_listes(FxRobot robot) {
        LineChart<?, ?> graphe = robot.lookup("#grapheClimat").queryAs(LineChart.class);
        ListView<?> anomalies = robot.lookup("#listeAnomalies").queryAs(ListView.class);
        ListView<?> evenements = robot.lookup("#listeEvenements").queryAs(ListView.class);

        assertThat(graphe.getData()).hasSize(2);
        assertThat(graphe.getData().get(0).getData()).hasSize(2); // température
        assertThat(graphe.getData().get(1).getData()).hasSize(2); // humidité
        assertThat(anomalies.getItems()).hasSize(1);
        assertThat(evenements.getItems()).hasSize(1);
        // Barre de statut 3 zones (#1022) : contexte à gauche, matériel + nombre de mesures au centre (#1498).
        var zones = controleur.zonesStatutProperty().get();
        assertThat(zones.gauche()).isEqualTo("Carré 640380 · A1 · N° 2");
        assertThat(zones.centre()).isEqualTo("PR 1925492 · 2 mesures");
    }

    @Test
    @DisplayName("#5205 : chaque point du graphe dit son heure, sa série et sa valeur au survol")
    void les_points_du_graphe_se_disent(FxRobot robot) throws TimeoutException {
        // Le nœud d'un point naît à la mise en page : sans cette attente, la liste est vide et le cas
        // passerait au vert en n'ayant rien regardé.
        Attente.que(
                () -> !grapheClimat(robot).getData().isEmpty()
                        && grapheClimat(robot).getData().get(0).getData().stream()
                                .anyMatch(d -> d.getNode() != null),
                "les points du graphe ont pris un nœud",
                5000L);

        javafx.scene.chart.XYChart.Data<Number, Number> premier =
                grapheClimat(robot).getData().get(0).getData().get(0);

        // #5205 : la légende est ÉTEINTE, et ce cas n'a de valeur que parce qu'il vérifie l'autre
        // moitié. Un test qui se contenterait de constater son absence laisserait passer un écran
        // devenu muet : rien n'y dirait plus quelle courbe est laquelle.
        assertThat(grapheClimat(robot).isLegendVisible())
                .as("la légende a été retirée : c'est le survol qui nomme les séries désormais")
                .isFalse();

        // Le pointeur n'atteint pas un symbole de dix pixels : `moveTo` laisse `isHover()` faux, et
        // l'infobulle ne paraît jamais. Le harnais poste donc l'entrée de souris, et dit pourquoi.
        assertThat(InfobulleDeBlocage.montrerParEntreeDeSouris(premier.getNode(), robot))
                .as("un point qui ne dit ni son heure ni sa valeur laisse lire une courbe sans pouvoir"
                        + " la chiffrer : c'est ce que la légende ne remplace pas")
                .containsPattern("\\d{2}:\\d{2}")
                .as("et il NOMME sa série : c'est ce que la légende disait, et le seul moyen restant"
                        + " de distinguer les deux courbes")
                .contains("T°")
                .contains("°C");

        // Et elle est PHOTOGRAPHIABLE : une infobulle vit dans sa propre fenêtre, que `Window.getWindows`
        // rend et que `CameraDeScene` compose. Sans ce contrôle, « le survol se filme » resterait une
        // supposition, et c'est de cette supposition que dépend le retrait de la légende.
        assertThat(javafx.stage.Window.getWindows().stream()
                        .filter(fenetre -> fenetre instanceof javafx.stage.PopupWindow)
                        .count())
                .as("l'infobulle doit paraître comme une fenêtre à part, sinon aucune capture ne la" + " montrera")
                .isPositive();
    }

    @SuppressWarnings("unchecked")
    private static javafx.scene.chart.XYChart<Number, Number> grapheClimat(FxRobot robot) {
        return (javafx.scene.chart.XYChart<Number, Number>)
                robot.lookup("#grapheClimat").query();
    }
}
