package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.CouvertureNuageuse;
import fr.univ_amu.iut.passage.model.DecompteAudio;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.MaterielMicro;
import fr.univ_amu.iut.passage.model.PositionMicro;
import fr.univ_amu.iut.passage.model.ServiceConditionsPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.ServiceRattachement;
import fr.univ_amu.iut.passage.model.Vent;
import fr.univ_amu.iut.passage.viewmodel.RattachementViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX de la modale **« Modifier le passage »** : chargement du FXML via
/// Guice (avec un [ServicePassage] mocké), `demarrer` sur un passage, vérification du câblage
/// (Spinners pré-remplis en bidirectionnel + récapitulatif réactif, et champs des conditions de dépôt
/// météo/micro dont le type de micro en liste fermée). Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class RattachementModaleViewTest {

    private RattachementModaleController controleur;
    private RattachementViewModel viewModel;
    private ServiceConditionsPassage conditionsService;
    private final AtomicBoolean succesAppele = new AtomicBoolean(false);

    @Start
    void start(Stage stage) throws Exception {
        ServicePassage service = mock(ServicePassage.class);
        when(service.detailPassage(anyLong()))
                .thenReturn(new DetailPassage(
                        1,
                        2026,
                        "2026-06-20",
                        "21:00:00",
                        "05:00:00",
                        "1925492",
                        StatutWorkflow.TRANSFORME,
                        Verdict.OK,
                        null,
                        0L,
                        0L,
                        30,
                        0.0,
                        null,
                        new DecompteAudio(0, 0)));
        conditionsService = mock(ServiceConditionsPassage.class);
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            RattachementViewModel viewModel() {
                viewModel = new RattachementViewModel(
                        service,
                        mock(ServiceRattachement.class),
                        conditionsService,
                        Optional.empty(),
                        // Import indisponible ici (hors connexion) : le bouton « Importer les observations »
                        // n'apparaît pas — la modale est celle d'avant.
                        Optional.empty());
                return viewModel;
            }
        });
        FXMLLoader loader = new FXMLLoader(RattachementModaleController.class.getResource("RattachementModale.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        controleur.demarrer(7L, "040962", "A1", () -> succesAppele.set(true));
        stage.setScene(new Scene(vue));
        stage.show();
    }

    @Test
    @DisplayName("#1216 : le tir VigieChiro passe par le socle, le bouton est relâché et le message routé")
    void tir_relache_le_bouton_et_route_le_message(FxRobot robot) {
        Button tirer = robot.lookup("#boutonTirerVigieChiro").queryAs(Button.class);

        robot.interact(tirer::fire);

        // Passerelle absente dans cette fixture : le tir répond « rien récupéré », jamais un silence.
        Label message = robot.lookup("#messageErreur").queryAs(Label.class);
        assertThat(message.getText()).contains("Aucune participation VigieChiro");
        assertThat(tirer.isDisabled())
                .as("bouton relâché par binding une fois l'opération finie (exécuteur synchrone)")
                .isFalse();
    }

    @Test
    @DisplayName("#1216 : un échec de « Récupérer la météo » est routé vers le message, le bouton relâché")
    void echec_meteo_route_et_relache(FxRobot robot) {
        when(conditionsService.recupererMeteo(7L)).thenThrow(new RuntimeException("Open-Meteo injoignable"));
        Button meteo = robot.lookup("#boutonRecupererMeteo").queryAs(Button.class);

        robot.interact(meteo::fire);

        // L'échec inattendu rejoint la ligne de message (#795) au lieu de mourir dans le fil de fond
        // en laissant le bouton grisé pour toujours.
        Label message = robot.lookup("#messageErreur").queryAs(Label.class);
        assertThat(message.getText()).contains("a échoué").contains("Open-Meteo injoignable");
        assertThat(meteo.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("#798 : « Appliquer » confirme avant un renommage irréversible des séquences")
    void appliquer_confirme_avant_renommage(FxRobot robot) {
        // Changer le n° de passage → le rattachement change → les séquences seraient renommées sur le disque.
        robot.interact(() -> viewModel.numeroPassageProperty().set(9));
        assertThat(viewModel.entraineRenommage()).isTrue();

        List<String> demandes = new ArrayList<>();
        controleur.confirmateur().definir(message -> {
            demandes.add(message);
            return false; // l'utilisateur refuse
        });

        robot.clickOn("#boutonAppliquer");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(demandes).as("un renommage effectif demande confirmation").hasSize(1);
        assertThat(demandes.get(0)).contains("renommé");
        assertThat(succesAppele)
                .as("refus → rien n'est appliqué (l'action de succès n'est pas déclenchée)")
                .isFalse();
    }

    @Test
    @DisplayName("Les spinners sont pré-remplis et le récap est neutre tant que rien ne change")
    void prerempli_et_recap_neutre(FxRobot robot) {
        Spinner<?> annee = robot.lookup("#spinnerAnnee").queryAs(Spinner.class);
        Spinner<?> numero = robot.lookup("#spinnerNumero").queryAs(Spinner.class);
        Label recap = robot.lookup("#labelRecap").queryAs(Label.class);

        assertThat(annee.getValue()).isEqualTo(2026);
        assertThat(numero.getValue()).isEqualTo(1);
        assertThat(recap.getText()).contains("Aucun changement");
    }

    @Test
    @DisplayName("Changer le n° dans le spinner met à jour le récap (quadruplet X → Y)")
    void changer_numero_met_a_jour_le_recap(FxRobot robot) {
        @SuppressWarnings("unchecked")
        Spinner<Integer> numero = robot.lookup("#spinnerNumero").queryAs(Spinner.class);
        Label recap = robot.lookup("#labelRecap").queryAs(Label.class);

        robot.interact(() -> numero.getValueFactory().setValue(2));

        assertThat(recap.getText()).contains("Car040962-2026-Pass1-A1").contains("Car040962-2026-Pass2-A1");
    }

    @Test
    @DisplayName("Le spinner n'écrête pas une valeur hors domaine : le ViewModel reste l'autorité")
    void spinner_ne_preclampe_pas_la_saisie(FxRobot robot) {
        @SuppressWarnings("unchecked")
        Spinner<Integer> numero = robot.lookup("#spinnerNumero").queryAs(Spinner.class);

        // 0 (hors domaine) et 100000 (au-delà d'une borne arbitraire) sont conservés tels quels : c'est
        // valider() qui rejettera 0 — le spinner ne le normalise pas silencieusement.
        robot.interact(() -> numero.getValueFactory().setValue(0));
        assertThat(numero.getValue()).isZero();

        robot.interact(() -> numero.getValueFactory().setValue(100000));
        assertThat(numero.getValue()).isEqualTo(100000);
    }

    @Test
    @DisplayName("Les conditions de dépôt sont câblées : température libre, vent/couverture/type en listes fermées")
    void champs_conditions_cables(FxRobot robot) {
        // Température : saisie libre (champ texte).
        assertThat(robot.lookup("#champTemperature").queryAs(TextField.class)).isNotNull();

        // Vent : catégories nul/faible/moyen/fort + entrée « non renseigné » (null) en tête.
        @SuppressWarnings("unchecked")
        ComboBox<Vent> vent = robot.lookup("#champVent").queryAs(ComboBox.class);
        assertThat(vent.getItems()).containsExactly(null, Vent.NUL, Vent.FAIBLE, Vent.MOYEN, Vent.FORT);

        // Couverture nuageuse : tranches 0-25 … 75-100 % + entrée « non renseigné » en tête.
        @SuppressWarnings("unchecked")
        ComboBox<CouvertureNuageuse> couverture =
                robot.lookup("#champCouverture").queryAs(ComboBox.class);
        assertThat(couverture.getItems()).hasSize(CouvertureNuageuse.values().length + 1);
        assertThat(couverture.getItems()).contains(CouvertureNuageuse.DE_25_A_50, CouvertureNuageuse.DE_75_A_100);

        // Position : liste sol/canopée + entrée « non renseigné » (null) en tête.
        @SuppressWarnings("unchecked")
        ComboBox<PositionMicro> position = robot.lookup("#champPosition").queryAs(ComboBox.class);
        assertThat(position.getItems()).containsExactly(null, PositionMicro.SOL, PositionMicro.CANOPEE);

        // Type de micro : liste fermée VigieChiro + entrée vide « (non renseigné) » en tête.
        @SuppressWarnings("unchecked")
        ComboBox<String> typeMicro = robot.lookup("#champTypeMicro").queryAs(ComboBox.class);
        assertThat(typeMicro.getItems()).hasSize(MaterielMicro.TYPES_VIGIECHIRO.size() + 1);
        assertThat(typeMicro.getItems()).contains("SMX-U1", "SPU avec coque de protection");
    }
}
