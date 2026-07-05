package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.audio.viewmodel.FiltresAudio;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Barre de filtres « à la Notion » (#470/#471) : ajouter/retirer une puce via « + Filtre », recherche
/// texte permanente, réinitialisation. Vérifie le câblage sur la [FilteredList] via [FiltresAudio].
@ExtendWith(ApplicationExtension.class)
class GestionnaireFiltresTest {

    private TextField recherche;
    private MenuButton menu;
    private FlowPane puces;
    private FilteredList<LigneObservationAudio> affichees;
    private GestionnaireFiltres gestionnaire;

    @Start
    void start(Stage stage) {
        recherche = new TextField();
        menu = new MenuButton("+ Filtre");
        puces = new FlowPane();
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligne(1, "Pippip", "PaRec_1.wav", StatutObservation.NON_TOUCHEE),
                ligne(2, "Nyclei", "PaRec_2.wav", StatutObservation.VALIDEE),
                // Observation CORRIGÉE : Tadarida disait « Bruit », l'observateur a retenu « Grand Rhinolophe ».
                ligneCorrigee(3, "Bruit", "Rhifer", "Grand Rhinolophe", "PaRec_3.wav"));
        affichees = new FilteredList<>(source);
        FiltresAudio filtres = new FiltresAudio(affichees, () -> {});
        gestionnaire = new GestionnaireFiltres(recherche, menu, puces, filtres, List.of(CriteresAudio.statut()));
        stage.setScene(new Scene(new VBox(recherche, menu, puces), 400, 200));
        stage.show();
    }

    @Test
    @DisplayName("« + Filtre » ajoute la puce Statut (À revoir par défaut), filtre, et vide le menu ; ✕ restaure")
    void ajouter_puis_retirer_la_puce_statut(FxRobot robot) {
        assertThat(affichees).hasSize(3);
        assertThat(menu.getItems()).extracting(MenuItem::getText).containsExactly("Statut");
        assertThat(puces.getChildren()).isEmpty();

        // Ajouter le filtre Statut → défaut À revoir → seule la ligne 1 reste ; menu vidé ; une puce.
        robot.interact(() -> menu.getItems().get(0).fire());
        assertThat(affichees).extracting(LigneObservationAudio::idObservation).containsExactly(1L);
        assertThat(menu.getItems()).isEmpty();
        assertThat(puces.getChildren()).hasSize(1);

        // Retirer la puce (✕) → tout revisible ; le critère revient au menu.
        robot.interact(() -> boutonRetirer().fire());
        assertThat(affichees).hasSize(3);
        assertThat(menu.getItems()).hasSize(1);
        assertThat(puces.getChildren()).isEmpty();
    }

    @Test
    @DisplayName("Recherche texte : filtre sur fichier / taxon (insensible casse/accents)")
    void recherche_texte(FxRobot robot) {
        robot.interact(() -> recherche.setText("nyclei"));
        assertThat(affichees).extracting(LigneObservationAudio::idObservation).containsExactly(2L);
        robot.interact(() -> recherche.clear());
        assertThat(affichees).hasSize(3);
    }

    @Test
    @DisplayName("Recherche texte : trouve l'espèce RETENUE d'une observation corrigée (taxon + vernaculaire)")
    void recherche_texte_espece_corrigee(FxRobot robot) {
        // La ligne 3 est « Bruit » côté Tadarida mais corrigée en « Grand Rhinolophe » (code Rhifer) :
        // chercher l'espèce retenue doit la trouver, même si Tadarida ne l'a pas proposée.
        robot.interact(() -> recherche.setText("rhinolophe"));
        assertThat(affichees).extracting(LigneObservationAudio::idObservation).containsExactly(3L);
        robot.interact(() -> recherche.setText("rhifer")); // code taxon observateur
        assertThat(affichees).extracting(LigneObservationAudio::idObservation).containsExactly(3L);
    }

    @Test
    @DisplayName("reinitialiser efface la recherche et les puces (tout redevient visible)")
    void reinitialiser(FxRobot robot) {
        robot.interact(() -> {
            menu.getItems().get(0).fire(); // Statut = À revoir
            recherche.setText("zzz"); // ne matche rien
        });
        assertThat(affichees).isEmpty();

        robot.interact(() -> gestionnaire.reinitialiser());
        assertThat(affichees).hasSize(3);
        assertThat(puces.getChildren()).isEmpty();
        assertThat(recherche.getText()).isEmpty();
    }

    @Test
    @DisplayName("Critère Groupe : choix = groupes présents (distincts, triés) ; défaut « Chiroptères » ; re-filtre")
    void filtre_groupe(FxRobot robot) {
        // Jeu mixte chauves-souris / oiseaux (deux Chiroptères pour vérifier la déduplication).
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligne(1, "Pippip", "PaRec_1.wav", StatutObservation.VALIDEE), // Chiroptères
                ligneGroupe(2, "Turmer", "PaRec_2.wav", StatutObservation.VALIDEE, "Oiseaux"),
                ligne(3, "Nyclei", "PaRec_3.wav", StatutObservation.VALIDEE)); // Chiroptères
        FilteredList<LigneObservationAudio> vues = new FilteredList<>(source);
        FiltresAudio filtresLocaux = new FiltresAudio(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        GestionnaireFiltres ignore = new GestionnaireFiltres(
                new TextField(), menuLocal, pucesLocales, filtresLocaux, List.of(CriteresAudio.groupe(() -> source)));
        assertThat(ignore).isNotNull();

        // Ajouter la puce Groupe : le sélecteur liste les groupes présents (distincts, triés) et défaut
        // « Chiroptères » → seules les chauves-souris restent.
        robot.interact(() -> menuLocal.getItems().get(0).fire());
        ComboBox<Object> choixGroupe = comboDe(pucesLocales);
        assertThat(choixGroupe.getItems()).containsExactly("Chiroptères", "Oiseaux");
        assertThat(choixGroupe.getValue()).isEqualTo("Chiroptères");
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(1L, 3L);

        // Le même critère sert tout groupe : basculer sur « Oiseaux » → seule l'observation d'oiseau reste.
        robot.interact(() -> choixGroupe.setValue("Oiseaux"));
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(2L);
    }

    @Test
    @DisplayName("Critère Espèce : liste les espèces présentes (taxon retenu) ; sélectionner restreint à l'espèce")
    void filtre_taxon(FxRobot robot) {
        // Ligne 1 retenue Pippip (sans vernaculaire → libellé = code) ; ligne 2 corrigée : Tadarida dit
        // « Bruit » mais l'observateur a retenu « Rhifer » (Grand Rhinolophe) → le taxon retenu est Rhifer.
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(
                ligne(1, "Pippip", "PaRec_1.wav", StatutObservation.VALIDEE),
                ligneCorrigee(2, "Bruit", "Rhifer", "Grand Rhinolophe", "PaRec_2.wav"));
        FilteredList<LigneObservationAudio> vues = new FilteredList<>(source);
        FiltresAudio filtresLocaux = new FiltresAudio(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        GestionnaireFiltres ignore = new GestionnaireFiltres(
                new TextField(), menuLocal, pucesLocales, filtresLocaux, List.of(CriteresAudio.taxon(() -> source)));
        assertThat(ignore).isNotNull();

        // Ajouter la puce Espèce : espèces présentes triées par libellé, aucune présélection (rien masqué).
        robot.interact(() -> menuLocal.getItems().get(0).fire());
        ComboBox<Object> choix = comboDe(pucesLocales);
        List<String> libelles = choix.getItems().stream()
                .map(espece -> choix.getConverter().toString(espece))
                .toList();
        assertThat(libelles).containsExactly("Grand Rhinolophe", "Pippip");
        assertThat(vues).hasSize(2);

        // Sélectionner « Grand Rhinolophe » (taxon retenu Rhifer, corrigé depuis Bruit) → seule cette obs.
        robot.interact(() -> choix.setValue(choix.getItems().get(0)));
        assertThat(vues).extracting(LigneObservationAudio::idObservation).containsExactly(2L);
    }

    private Button boutonRetirer() {
        return (Button) puces.lookupAll(".puce-filtre-retirer").iterator().next();
    }

    /// L'éditeur d'un critère à liste déroulante est le 2e enfant de sa puce (Label, ComboBox, bouton ✕).
    @SuppressWarnings("unchecked")
    private static ComboBox<Object> comboDe(FlowPane puces) {
        HBox puce = (HBox) puces.getChildren().get(0);
        return (ComboBox<Object>) puce.getChildren().get(1);
    }

    private static LigneObservationAudio ligne(long id, String taxon, String fichier, StatutObservation statut) {
        return ligneGroupe(id, taxon, fichier, statut, "Chiroptères");
    }

    /// Variante fixant le **groupe taxon parent** (pour le critère Groupe) : Chiroptères, Oiseaux…
    private static LigneObservationAudio ligneGroupe(
            long id, String taxon, String fichier, StatutObservation statut, String groupe) {
        return new LigneObservationAudio(
                id,
                10 + id,
                7L,
                1,
                "2026-06-20",
                "640380",
                "A1",
                "Site",
                taxon,
                0.9,
                null,
                null,
                statut,
                false,
                null,
                45,
                null,
                taxon,
                groupe,
                fichier,
                0.2,
                0.4);
    }

    /// Observation **corrigée** : Tadarida a proposé `taxonTadarida`, l'observateur a retenu le taxon
    /// `codeObservateur` (vernaculaire `nomEspece`). Sert à vérifier que la recherche trouve l'espèce
    /// **retenue** (côté observateur), pas seulement la proposition Tadarida.
    private static LigneObservationAudio ligneCorrigee(
            long id, String taxonTadarida, String codeObservateur, String nomEspece, String fichier) {
        return new LigneObservationAudio(
                id,
                10 + id,
                7L,
                1,
                "2026-06-20",
                "640380",
                "A1",
                "Site",
                taxonTadarida,
                0.9,
                codeObservateur,
                0.95,
                StatutObservation.CORRIGEE,
                false,
                null,
                45,
                nomEspece,
                taxonTadarida,
                "Chiroptères",
                fichier,
                0.2,
                0.4);
    }
}
