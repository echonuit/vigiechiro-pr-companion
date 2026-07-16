package fr.univ_amu.iut.diagnostic.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.view.Lieu;
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
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kordamp.ikonli.javafx.FontIcon;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Tests d'**intégration TestFX** de l'écran **M-Diagnostic** centrés sur le câblage réel
/// Vue ↔ ViewModel : chaque test fait un **vrai lookup des `fx:id`** (`robot.lookup("#…")`) puis
/// vérifie que l'état affiché reflète le [DiagnosticViewModel] et qu'un **changement de passage**
/// (réinvocation de `ouvrirSur(...)` sur le fil JavaFX) propage le bon effet à l'écran. Un écran
/// resté à l'état de placeholder (sans `fx:id` ni bindings) échouerait donc ici, là où une lecture
/// directe des propriétés du ViewModel l'aurait laissé passer.
///
/// Cet écran est en **lecture seule** (patron « pur câblage » du CM4) : il ne comporte ni bouton,
/// ni `ComboBox`, ni `TableView`, donc aucun `onAction`. Les « interactions » testées sont les
/// transitions d'état déclenchées par `ouvrirSur(...)` : reconstruction du graphe climatique
/// (`ListChangeListener` → `majGraphe()`), signalement R20 (relevé absent), ligne d'état GPS et message
/// d'erreur (visibilités liées). Le [ServiceDiagnostic] est mocké : aucune base de données.
@ExtendWith(ApplicationExtension.class)
class DiagnosticVueIntegrationTest {

    private DiagnosticController controleur;

    @Start
    void start(Stage stage) throws Exception {
        ServiceDiagnostic service = mock(ServiceDiagnostic.class);
        // Passage nominal : relevé climatique présent (2 mesures), GPS renseigné, 1 anomalie R19.
        when(service.diagnostiquer(42L)).thenReturn(diagnosticAvecReleve());
        // Passage R20 : aucun relevé climatique, GPS non renseigné, journal sans anomalie.
        when(service.diagnostiquer(8L)).thenReturn(diagnosticSansReleve());
        // Passage introuvable : le ViewModel neutralise l'erreur dans son message sans lever.
        when(service.diagnostiquer(999L)).thenThrow(new RuntimeException("Passage 999 introuvable"));
        // Passage nuit complète (#1497) : GPS renseigné ET cohérence horaires calculée. Le repère GPS
        // doit rester affiché (l'ancien câblage le masquait dès que la fenêtre nocturne était calculable).
        when(service.diagnostiquer(77L)).thenReturn(diagnosticNuitComplete());

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
        controleur.ouvrirSur(ctx(42L));
        stage.setScene(new Scene(vue, 1000, 760));
        stage.show();
    }

    private static ContextePassage ctx(long idPassage) {
        return new ContextePassage(idPassage, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière"));
    }

    @Test
    @DisplayName("Emplacement (fil d'Ariane) : Mes sites › Carré N › Détails du passage N° X › Diagnostic matériel")
    void emplacement_reflete_le_passage() {
        assertThat(controleur.emplacement())
                .extracting(Lieu::libelle)
                .containsExactly("Mes sites", "Carré 640380", "Détails du passage N° 2", "Diagnostic matériel");
        // Ancêtres cliquables ; écran courant non cliquable.
        assertThat(controleur.emplacement().get(0).estCliquable()).isTrue();
        assertThat(controleur.emplacement().get(3).estCliquable()).isFalse();
    }

    @Test
    @DisplayName("Tous les contrôles attendus de Diagnostic.fxml sont présents (lookup des fx:id)")
    void tous_les_controles_attendus_sont_presents(FxRobot robot) {
        // Un écran placeholder (sans fx:id) ferait échouer ces lookups : c'est la garde structurelle.
        // L'enregistreur est déporté en barre de statut (#693) : vérifié via les zones, plus de label.
        assertThat(controleur.zonesStatutProperty().get().centre()).isNotEmpty();
        assertThat(robot.lookup("#lblReleveAbsent").tryQuery()).isPresent();
        assertThat(robot.lookup("#grapheClimat").tryQuery()).isPresent();
        assertThat(robot.lookup("#listeAnomalies").tryQuery()).isPresent();
        assertThat(robot.lookup("#listeEvenements").tryQuery()).isPresent();
        assertThat(robot.lookup("#ligneGps").tryQuery()).isPresent();
        assertThat(robot.lookup("#iconeGps").tryQuery()).isPresent();
        assertThat(robot.lookup("#lblGps").tryQuery()).isPresent();
        assertThat(robot.lookup("#lblMessage").tryQuery()).isPresent();
        assertThat(robot.lookup("#lblTemperature").tryQuery()).isPresent(); // #106
    }

    @Test
    @DisplayName("À l'ouverture, labels, graphe et listes reflètent le ViewModel")
    void etat_initial_reflete_le_viewmodel(FxRobot robot) {
        Label temperature = robot.lookup("#lblTemperature").queryAs(Label.class);
        LineChart<?, ?> graphe = robot.lookup("#grapheClimat").queryAs(LineChart.class);
        ListView<?> anomalies = robot.lookup("#listeAnomalies").queryAs(ListView.class);
        ListView<?> evenements = robot.lookup("#listeEvenements").queryAs(ListView.class);

        // Enregistreur + nombre de mesures déportés en barre de statut (#693, #1498).
        assertThat(controleur.zonesStatutProperty().get().centre()).isEqualTo("PR 1925492 · 2 mesures");
        assertThat(temperature.getText())
                .as("#106 : température affichée en M-Diagnostic")
                .contains("8,5 °C");
        // Le graphe se reconstruit depuis viewModel.mesures() : deux séries (T° + humidité), 2 points.
        assertThat(graphe.getData()).hasSize(2);
        assertThat(graphe.getData().get(0).getData()).hasSize(2);
        assertThat(graphe.getData().get(1).getData()).hasSize(2);
        assertThat(anomalies.getItems()).hasSize(1);
        assertThat(anomalies.getItems().get(0)).isEqualTo("Réveil non programmé à 03:12");
        assertThat(evenements.getItems()).hasSize(1);
        assertThat(evenements.getItems().get(0)).isEqualTo("Démarrage");
    }

    @Test
    @DisplayName("Le graphe utilise un axe temporel (NumberAxis) étiqueté en HH:mm, aux minutes réelles")
    void axe_du_graphe_est_temporel_en_heures(FxRobot robot) {
        LineChart<?, ?> graphe = robot.lookup("#grapheClimat").queryAs(LineChart.class);

        assertThat(graphe.getXAxis()).isInstanceOf(NumberAxis.class);
        NumberAxis axe = (NumberAxis) graphe.getXAxis();
        // Chaque point est placé à sa minute réelle depuis la première mesure : 22:00 -> 0, 02:00 (J+1) -> 240.
        assertThat(graphe.getData().get(0).getData().get(0).getXValue()).isEqualTo(0L);
        assertThat(graphe.getData().get(0).getData().get(1).getXValue()).isEqualTo(240L);
        // Les étiquettes sont reconstruites en HH:mm, et non un libellé brut par mesure.
        assertThat(axe.getTickLabelFormatter().toString(0L)).isEqualTo("22:00");
        assertThat(axe.getTickLabelFormatter().toString(240L)).isEqualTo("02:00");
        assertThat(axe.isAutoRanging()).isFalse();
    }

    @Test
    @DisplayName("Relevé présent : l'alerte R20 est masquée et aucun message d'erreur n'est affiché")
    void releve_present_masque_l_alerte_et_le_message(FxRobot robot) {
        Label alerteR20 = robot.lookup("#lblReleveAbsent").queryAs(Label.class);
        Label message = robot.lookup("#lblMessage").queryAs(Label.class);

        // visible ET managed sont liés à releveClimatiqueAbsentProperty (false ici).
        assertThat(alerteR20.isVisible()).isFalse();
        assertThat(alerteR20.isManaged()).isFalse();
        assertThat(message.isVisible()).isFalse();
    }

    @Test
    @DisplayName("Diagnostic avec GPS renseigné : ligne d'état verte « GPS du point : disponible » (marqueur)")
    void ligne_gps_disponible_quand_gps_renseigne(FxRobot robot) {
        HBox ligne = robot.lookup("#ligneGps").queryAs(HBox.class);
        FontIcon icone = robot.lookup("#iconeGps").queryAs(FontIcon.class);
        Label gps = robot.lookup("#lblGps").queryAs(Label.class);

        assertThat(ligne.isVisible()).isTrue();
        assertThat(ligne.isManaged()).isTrue();
        assertThat(ligne.getStyleClass()).contains("gps-disponible");
        assertThat(icone.getIconLiteral()).isEqualTo("fas-map-marker-alt");
        assertThat(gps.getText()).isEqualTo("GPS du point : disponible");
    }

    @Test
    @DisplayName("#1497 : le repère GPS reste affiché même quand la cohérence horaires est calculée")
    void gps_reste_visible_quand_coherence_disponible(FxRobot robot) {
        // Nuit complète : GPS renseigné + fenêtre nocturne calculable. L'ancien câblage masquait le GPS
        // dès que la cohérence était disponible ; il doit désormais rester visible et « disponible ».
        robot.interact(() -> controleur.ouvrirSur(ctx(77L)));

        HBox ligne = robot.lookup("#ligneGps").queryAs(HBox.class);
        Label gps = robot.lookup("#lblGps").queryAs(Label.class);

        assertThat(ligne.isVisible()).isTrue();
        assertThat(ligne.isManaged()).isTrue();
        assertThat(ligne.getStyleClass()).contains("gps-disponible");
        assertThat(gps.getText()).isEqualTo("GPS du point : disponible");
    }

    @Test
    @DisplayName(
            "Changer pour un passage sans relevé signale R20, vide le graphe et bascule le GPS en « non renseigné »")
    void releve_absent_signale_r20_et_vide_le_graphe(FxRobot robot) {
        // Interaction : on rouvre l'écran sur un passage dont le relevé climatique est absent (R20).
        robot.interact(() -> controleur.ouvrirSur(ctx(8L)));

        Label alerteR20 = robot.lookup("#lblReleveAbsent").queryAs(Label.class);
        Label gps = robot.lookup("#lblGps").queryAs(Label.class);
        HBox ligneGps = robot.lookup("#ligneGps").queryAs(HBox.class);
        FontIcon iconeGps = robot.lookup("#iconeGps").queryAs(FontIcon.class);
        LineChart<?, ?> graphe = robot.lookup("#grapheClimat").queryAs(LineChart.class);

        assertThat(alerteR20.isVisible()).isTrue();
        assertThat(alerteR20.isManaged()).isTrue();
        // Relevé absent : pas de compteur au centre (enregistreur seul), l'absence est portée par la
        // zone droite de la barre de statut (#1498, piste B) et le bandeau R20.
        var zones = controleur.zonesStatutProperty().get();
        assertThat(zones.centre()).isEqualTo("PR 1925492");
        assertThat(zones.droite()).contains("Relevé climatique absent");
        // Le ListChangeListener a reconstruit le graphe : deux séries, mais sans aucun point.
        assertThat(graphe.getData()).hasSize(2);
        assertThat(graphe.getData().get(0).getData()).isEmpty();
        assertThat(graphe.getData().get(1).getData()).isEmpty();
        // GPS absent : ligne d'état toujours affichée (jamais muette), en état d'avertissement ambre.
        assertThat(ligneGps.isVisible()).isTrue();
        assertThat(ligneGps.getStyleClass()).contains("gps-absent");
        assertThat(iconeGps.getIconLiteral()).isEqualTo("fas-exclamation-triangle");
        assertThat(gps.getText()).isEqualTo("GPS du point : non renseigné (compléter la fiche site)");
    }

    @Test
    @DisplayName("Un passage introuvable réinitialise l'écran et affiche le message d'erreur")
    void passage_introuvable_reinitialise_l_ecran_et_affiche_le_message(FxRobot robot) {
        // Interaction : le service lève, le ViewModel doit neutraliser l'erreur dans son message.
        robot.interact(() -> controleur.ouvrirSur(ctx(999L)));

        Label message = robot.lookup("#lblMessage").queryAs(Label.class);
        HBox ligneGps = robot.lookup("#ligneGps").queryAs(HBox.class);
        ListView<?> anomalies = robot.lookup("#listeAnomalies").queryAs(ListView.class);
        ListView<?> evenements = robot.lookup("#listeEvenements").queryAs(ListView.class);

        assertThat(message.isVisible()).isTrue();
        assertThat(message.getText()).contains("introuvable");
        // Réinitialisation : enregistreur (déporté en barre de statut, #693) vidé, listes vides, GPS masqué.
        assertThat(controleur.zonesStatutProperty().get().centre()).isEmpty();
        assertThat(anomalies.getItems()).isEmpty();
        assertThat(evenements.getItems()).isEmpty();
        // La ligne GPS n'a de sens qu'avec un diagnostic chargé : masquée après réinitialisation.
        assertThat(ligneGps.isVisible()).isFalse();
        assertThat(ligneGps.isManaged()).isFalse();
    }

    @Test
    @DisplayName("Les listes d'anomalies et d'évènements sont des contrôles ListView peuplés")
    void les_listes_sont_des_listview_peuplees(FxRobot robot) {
        Node anomalies = robot.lookup("#listeAnomalies").query();
        Node evenements = robot.lookup("#listeEvenements").query();

        // Garde de type : les fx:id doivent bien désigner des ListView (pas de simples Label).
        assertThat(anomalies).isInstanceOf(ListView.class);
        assertThat(evenements).isInstanceOf(ListView.class);
        assertThat(((ListView<?>) anomalies).getItems()).isNotEmpty();
        assertThat(((ListView<?>) evenements).getItems()).isNotEmpty();
    }

    /// Diagnostic nominal : relevé climatique présent (2 mesures), GPS renseigné, 1 anomalie R19.
    private static Diagnostic diagnosticAvecReleve() {
        return new Diagnostic(
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
                CoherenceHoraire.indisponible());
    }

    /// Diagnostic R20 : aucun relevé climatique rattaché, GPS non renseigné, journal sans anomalie.
    private static Diagnostic diagnosticSansReleve() {
        return new Diagnostic(
                8L,
                9L,
                "1925492",
                new AnalyseAnomalies(List.of(), List.of("Arrêt manuel")),
                SerieClimatique.absente(),
                null,
                null,
                LocalDateTime.of(2026, 6, 24, 8, 0),
                null,
                CoherenceHoraire.indisponible());
    }

    /// Diagnostic « nuit complète » (#1497) : GPS renseigné ET cohérence horaires calculée (fenêtre
    /// nocturne connue, sans écart). Sert à prouver que le repère GPS reste affiché dans ce cas.
    private static Diagnostic diagnosticNuitComplete() {
        return new Diagnostic(
                77L,
                11L,
                "1925492",
                new AnalyseAnomalies(List.of(), List.of("Démarrage")),
                SerieClimatique.presente(List.of(
                        new MesureClimatique(LocalDate.of(2026, 6, 22), LocalTime.of(22, 0), 18.5, 72),
                        new MesureClimatique(LocalDate.of(2026, 6, 23), LocalTime.of(2, 0), 14.0, 88))),
                43.5,
                5.4,
                LocalDateTime.of(2026, 6, 23, 8, 0),
                8.5,
                new CoherenceHoraire(true, LocalTime.of(21, 58), LocalTime.of(5, 48), false, false));
    }
}
