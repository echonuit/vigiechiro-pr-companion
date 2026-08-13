package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.InfobulleDeBlocage;
import fr.univ_amu.iut.commun.viewmodel.RevisionDonnees;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
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
    private ServicePassage service;
    private final AtomicBoolean succesAppele = new AtomicBoolean(false);

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServicePassage.class);
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

            /// Le `Navigateur` du socle abonne les écrans qui déclarent `SuitLaRevision` (ADR 3651) :
            /// il réclame donc la révision, que cet injecteur partiel doit lier comme les autres
            /// pièces du socle. Exécution en ligne : le test n'a pas de fil JavaFX à attendre.
            @Provides
            @Singleton
            RevisionDonnees revision() {
                return new RevisionDonnees(Runnable::run);
            }

            @Provides
            RattachementViewModel viewModel() {
                var propositions = mock(fr.univ_amu.iut.passage.model.PropositionsEnregistreur.class);
                when(propositions.pour(org.mockito.ArgumentMatchers.any()))
                        .thenReturn(java.util.List.of("1925492", "1997632"));
                viewModel = new RattachementViewModel(
                        service,
                        mock(ServiceRattachement.class),
                        conditionsService,
                        propositions,
                        Optional.empty(),
                        Optional.empty(),
                        // Sans `sites` dans cet injecteur : le port rend une liste vide, et la modale
                        // garde le point courant comme seul choix (#1495).
                        numeroCarre -> java.util.List.of());
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
    @DisplayName("#1216 : le tir Vigie-Chiro passe par le socle, le bouton est relâché et le message routé")
    void tir_relache_le_bouton_et_route_le_message(FxRobot robot) {
        Button tirer = robot.lookup("#boutonTirerVigieChiro").queryAs(Button.class);

        robot.interact(tirer::fire);

        // Passerelle absente dans cette fixture : le tir répond « rien récupéré », jamais un silence.
        Label message = robot.lookup("#lblRetour").queryAs(Label.class);
        assertThat(message.getText()).contains("Aucune participation Vigie-Chiro");
        assertThat(tirer.isDisabled())
                .as("bouton relâché par binding une fois l'opération finie (exécuteur synchrone)")
                .isFalse();
    }

    @Test
    @DisplayName("#1839 : « Envoyer vers Vigie-Chiro » rend compte de l'envoi et relâche le bouton")
    void envoi_rend_compte(FxRobot robot) {
        Button envoyer = robot.lookup("#boutonEnvoyerVigieChiro").queryAs(Button.class);

        robot.interact(envoyer::fire);

        // Passerelle absente dans cette fixture : l'envoi le DIT, au lieu de se taire comme avant #1839.
        Label message = robot.lookup("#lblRetour").queryAs(Label.class);
        assertThat(message.getText()).contains("Non connecté");
        assertThat(envoyer.isDisabled())
                .as("bouton relâché par binding une fois l'aller-retour terminé")
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
        Label message = robot.lookup("#lblRetour").queryAs(Label.class);
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
    @DisplayName("#1494 : l'avertissement irréversible est épinglé, il ne défile pas avec le formulaire")
    void recap_epingle_hors_du_corps_defilant(FxRobot robot) {
        Label recap = robot.lookup("#labelRecap").queryAs(Label.class);
        ScrollPane corps = robot.lookup(".corps-modale").queryAs(ScrollPane.class);

        // Le récapitulatif annonce « Action irréversible » ; le bandeau « Météo pré-remplie », lui, est
        // épinglé hors du corps défilant (#2496). Laisser le premier dans le corps inverse la
        // hiérarchie : le plus grave sort du champ d'un coup de molette, le moins grave reste.
        assertThat(ancetres(recap))
                .as("le récapitulatif ne doit pas vivre dans le corps défilant")
                .doesNotContain(corps);
        assertThat(recap.getScene()).as("mais il reste bien dans la modale").isNotNull();
    }

    /// Les ancêtres de `noeud`, jusqu'à la racine de la scène.
    private static java.util.List<javafx.scene.Node> ancetres(javafx.scene.Node noeud) {
        java.util.List<javafx.scene.Node> chemin = new java.util.ArrayList<>();
        for (javafx.scene.Node courant = noeud.getParent(); courant != null; courant = courant.getParent()) {
            chemin.add(courant);
        }
        return chemin;
    }

    @Test
    @DisplayName("Le repère « en cours » est masqué au repos (#1543)")
    void repere_en_cours_masque_au_repos(FxRobot robot) {
        javafx.scene.Node repere = robot.lookup("#ligneOccupation").query();

        assertThat(repere.isVisible())
                .as("le repère ne s'affiche que pendant un aller-retour Vigie-Chiro")
                .isFalse();
        assertThat(repere.isManaged()).isFalse();
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
        // valider() qui rejettera 0 : le spinner ne le normalise pas silencieusement.
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

    @Test
    @DisplayName("#1828 : l'enregistreur est une liste ÉDITABLE, garnie des numéros proposés")
    void champ_enregistreur_editable_et_propose(FxRobot robot) {
        @SuppressWarnings("unchecked")
        ComboBox<String> enregistreur = robot.lookup("#champEnregistreur").queryAs(ComboBox.class);

        assertThat(enregistreur.isEditable())
                .as("les propositions ne sont que des propositions : on doit pouvoir taper le n° lu sur l'appareil")
                .isTrue();
        assertThat(enregistreur.getItems())
                .as("les numéros proposés (noms de fichiers de la nuit, puis appareils connus du poste)")
                .containsExactly("1925492", "1997632");

        // Le TEXTE de l'éditeur fait foi : une saisie libre doit compter dès la frappe, sans validation.
        robot.interact(() -> enregistreur.getEditor().setText("1234567"));
        assertThat(viewModel.conditions().enregistreurSaisieProperty().get()).isEqualTo("1234567");
    }

    @Test
    @DisplayName("#1688 : passage déposé, année/n° verrouillés (spinners grisés + indice), météo éditable")
    void passage_depose_verrouille_le_renommage_pas_la_meteo(FxRobot robot) {
        // Rouvrir la modale sur un passage déposé : son nom est l'identité serveur (#1134). Les bindings du
        // verrou sont réactifs, l'IHM se met à jour au ré-ouvrir.
        when(service.detailPassage(anyLong())).thenReturn(detailDepose());
        robot.interact(() -> controleur.demarrer(7L, "040962", "A1", () -> {}));

        assertThat(robot.lookup("#spinnerAnnee").queryAs(Spinner.class).isDisabled())
                .as("année verrouillée sur un passage déposé")
                .isTrue();
        assertThat(robot.lookup("#spinnerNumero").queryAs(Spinner.class).isDisabled())
                .isTrue();
        assertThat(robot.lookup("#indiceRenommageVerrouille").query().isVisible())
                .as("un indice explique le verrou")
                .isTrue();
        // Le verrou ne porte que sur le nom : la météo reste éditable, c'est tout l'intérêt.
        assertThat(robot.lookup("#champTemperature").queryAs(TextField.class).isDisabled())
                .as("météo éditable même sur un passage déposé")
                .isFalse();
    }

    private static DetailPassage detailDepose() {
        return new DetailPassage(
                1,
                2026,
                "2026-06-20",
                "21:00:00",
                "05:00:00",
                "1925492",
                StatutWorkflow.DEPOSE,
                Verdict.OK,
                null,
                0L,
                0L,
                30,
                0.0,
                null,
                new DecompteAudio(0, 0));
    }

    @Test
    @DisplayName("#1970 : le grisage d'« Appliquer » dit LAQUELLE des deux conditions bloque")
    void le_grisage_dit_laquelle_des_deux_conditions_bloque(FxRobot robot) {
        StackPane enveloppe = robot.lookup("#enveloppeAppliquer").queryAs(StackPane.class);
        Button appliquer = robot.lookup("#boutonAppliquer").queryAs(Button.class);

        // Deux gardes du ViewModel disaient chacune leur motif, derrière un bouton grisé sur ces mêmes
        // prédicats : aucune des deux n'était lisible. Un motif générique obligerait de surcroît
        // l'utilisateur à chercher lequel des deux champs pèche.
        robot.interact(() -> viewModel.anneeProperty().set(99));
        assertThat(appliquer.isDisabled()).isTrue();
        assertThat(InfobulleDeBlocage.texteDe(enveloppe)).contains("année").contains("quatre chiffres");

        robot.interact(() -> {
            viewModel.anneeProperty().set(2026);
            viewModel.numeroPassageProperty().set(0);
        });
        assertThat(appliquer.isDisabled()).isTrue();
        assertThat(InfobulleDeBlocage.texteDe(enveloppe))
                .as("l'autre condition, l'autre motif")
                .contains("numéro de passage");

        robot.interact(() -> viewModel.numeroPassageProperty().set(2));
        assertThat(appliquer.isDisabled()).isFalse();
        assertThat(InfobulleDeBlocage.texteDe(enveloppe)).doesNotContain("doit");
    }
}
