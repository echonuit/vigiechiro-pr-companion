package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.VueSauvegardee;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.VueSauvegardeeDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.Filtres;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
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

/// Onglets de vues mémorisées « à la Notion » (#623) : un onglet par vue de la feature, « + Vue »
/// enregistre les filtres courants, cliquer un onglet rejoue ses filtres, ✕ supprime, ✎ renomme. Test en
/// isolation : DAO réel sur une base SQLite jetable + barre de filtres pilotant une [FilteredList].
@ExtendWith(ApplicationExtension.class)
class GestionnaireVuesTest {

    private static final String FEATURE = "test";

    private VueSauvegardeeDao dao;
    private FilteredList<String> affichees;
    private TextField recherche;
    private FlowPane onglets;
    private GestionnaireFiltres<String> filtres;
    private GestionnaireVues<String> gestion;

    /// Réponse de la « saisie de nom » injectée (nouvelle vue / renommage), contrôlée par test.
    private Function<String, Optional<String>> saisieNom = defaut -> Optional.of("Vue par bouton");

    @Start
    void start(Stage stage) throws Exception {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(Files.createTempDirectory("vues-test")));
        new MigrationSchema(source).migrer();
        dao = new VueSauvegardeeDao(source);

        ObservableList<String> lignes = FXCollections.observableArrayList("apple", "banana", "cherry");
        affichees = new FilteredList<>(lignes);
        recherche = new TextField();
        filtres = new GestionnaireFiltres<>(
                recherche,
                new MenuButton(),
                new FlowPane(),
                new Filtres<>(affichees, () -> {}),
                List.of(),
                (ligne, texte) -> ligne.contains(texte));
        onglets = new FlowPane();
        stage.setScene(new Scene(new VBox(recherche, onglets), 400, 200));
        stage.show();
    }

    /// Construit le gestionnaire d'onglets (sur le fil FX) et le mémorise dans [#gestion].
    private void construire() {
        gestion = new GestionnaireVues<>(onglets, filtres, dao, FEATURE, defaut -> saisieNom.apply(defaut));
    }

    /// Construit le gestionnaire avec des **vues par défaut** (lecture seule) fournies par l'écran.
    private void construireAvecDefauts(VueSauvegardee... defauts) {
        gestion = new GestionnaireVues<>(
                onglets, filtres, dao, FEATURE, List.of(defauts), defaut -> saisieNom.apply(defaut));
    }

    private VueSauvegardee inserer(String nom, String descripteurJson) {
        return dao.insert(new VueSauvegardee(null, FEATURE, nom, descripteurJson));
    }

    /// Les onglets de vues (HBox) présents dans la barre, hors bouton « + Vue ».
    private List<HBox> ongletsVues() {
        return onglets.getChildren().stream()
                .filter(HBox.class::isInstance)
                .map(HBox.class::cast)
                .toList();
    }

    @Test
    @DisplayName("À l'ouverture : un onglet par vue de la feature, plus le bouton « + Vue »")
    void rafraichir_rend_un_onglet_par_vue_plus_le_bouton(FxRobot robot) {
        inserer("Vue A", "{\"texte\":\"app\",\"criteres\":[]}");
        inserer("Vue B", "{\"texte\":\"ban\",\"criteres\":[]}");

        robot.interact(this::construire);

        assertThat(ongletsVues()).hasSize(2);
        assertThat(onglets.getChildren()).last().isInstanceOf(Button.class); // « + Vue »
    }

    @Test
    @DisplayName("« + Vue » enregistre les FILTRES COURANTS sous le nom saisi et ajoute un onglet")
    void bouton_nouvelle_vue_enregistre_les_filtres_courants(FxRobot robot) {
        robot.interact(() -> recherche.setText("app")); // filtre courant à mémoriser
        robot.interact(this::construire);
        saisieNom = defaut -> Optional.of("Pommes");

        Button nouvelle = (Button) onglets.getChildren().getLast();
        robot.interact(nouvelle::fire);

        assertThat(dao.findByFeature(FEATURE)).singleElement().satisfies(vue -> {
            assertThat(vue.nom()).isEqualTo("Pommes");
            assertThat(vue.descripteurJson()).contains("\"texte\":\"app\"");
        });
        assertThat(ongletsVues()).hasSize(1);
    }

    @Test
    @DisplayName("Cliquer un onglet REJOUE les filtres de la vue (restaure la recherche et re-filtre)")
    void cliquer_un_onglet_rejoue_les_filtres(FxRobot robot) {
        inserer("Bananes", "{\"texte\":\"ban\",\"criteres\":[]}");
        robot.interact(this::construire);
        assertThat(affichees).hasSize(3); // aucun filtre au départ

        robot.interact(() -> ongletsVues()
                .getFirst()
                .getChildren()
                .get(0)
                .getOnMouseClicked()
                .handle(null));

        assertThat(recherche.getText()).isEqualTo("ban");
        assertThat(affichees).containsExactly("banana");
    }

    @Test
    @DisplayName("✕ supprime la vue et retire son onglet")
    void supprimer_retire_l_onglet(FxRobot robot) {
        inserer("À supprimer", "{\"texte\":\"\",\"criteres\":[]}");
        robot.interact(this::construire);
        assertThat(ongletsVues()).hasSize(1);

        // La croix ✕ est le dernier bouton de l'onglet.
        Button croix = (Button) ongletsVues().getFirst().getChildren().getLast();
        robot.interact(croix::fire);

        assertThat(ongletsVues()).isEmpty();
        assertThat(dao.findByFeature(FEATURE)).isEmpty();
    }

    @Test
    @DisplayName("✎ renomme la vue (le descripteur est conservé)")
    void renommer_change_le_nom(FxRobot robot) {
        VueSauvegardee vue = inserer("Ancien nom", "{\"texte\":\"ban\",\"criteres\":[]}");
        robot.interact(this::construire);

        robot.interact(() -> gestion.renommer(vue, "Nouveau nom"));

        assertThat(dao.findById(vue.id()).orElseThrow()).satisfies(v -> {
            assertThat(v.nom()).isEqualTo("Nouveau nom");
            assertThat(v.descripteurJson()).isEqualTo("{\"texte\":\"ban\",\"criteres\":[]}");
        });
    }

    /// Active la première vue (clic sur son libellé) : rejoue ses filtres et la marque active.
    private void activerPremiereVue(FxRobot robot) {
        robot.interact(() -> ongletsVues()
                .getFirst()
                .getChildren()
                .get(0)
                .getOnMouseClicked()
                .handle(null));
    }

    /// Le bouton « enregistrer » (icône disquette) de l'onglet, présent seulement quand la vue active a
    /// divergé. Repéré par son **libellé accessible** (« Enregistrer… ») et non par un glyphe : l'icône est
    /// une FontIcon Ikonli, sans texte.
    private static Optional<Button> boutonEnregistrer(HBox onglet) {
        return onglet.getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(bouton -> bouton.getAccessibleText() != null
                        && bouton.getAccessibleText().startsWith("Enregistrer"))
                .findFirst();
    }

    @Test
    @DisplayName("Modifier les filtres d'une vue active la marque « modifiée » (bouton 💾 sur l'onglet)")
    void modifier_les_filtres_marque_la_vue_active_modifiee(FxRobot robot) {
        inserer("Bananes", "{\"texte\":\"ban\",\"criteres\":[]}");
        robot.interact(this::construire);
        activerPremiereVue(robot); // rejoue « ban » : aucune divergence

        assertThat(boutonEnregistrer(ongletsVues().getFirst()))
                .as("pas de divergence juste après activation")
                .isEmpty();

        robot.interact(() -> recherche.setText("app")); // les filtres divergent de la vue

        assertThat(boutonEnregistrer(ongletsVues().getFirst()))
                .as("filtres divergents → la vue active est modifiée")
                .isPresent();
    }

    @Test
    @DisplayName("« 💾 Enregistrer dans la vue » réécrit l'instantané avec les filtres courants")
    void enregistrer_dans_la_vue_ecrase_le_descripteur(FxRobot robot) {
        VueSauvegardee vue = inserer("Bananes", "{\"texte\":\"ban\",\"criteres\":[]}");
        robot.interact(this::construire);
        activerPremiereVue(robot);
        robot.interact(() -> recherche.setText("app"));

        Button enregistrer = boutonEnregistrer(ongletsVues().getFirst()).orElseThrow();
        robot.interact(enregistrer::fire);

        assertThat(dao.findById(vue.id()).orElseThrow().descripteurJson()).contains("\"texte\":\"app\"");
        assertThat(boutonEnregistrer(ongletsVues().getFirst()))
                .as("plus de divergence après enregistrement")
                .isEmpty();
    }

    @Test
    @DisplayName("Revenir aux filtres de la vue efface l'indicateur « modifié »")
    void revenir_aux_filtres_de_la_vue_efface_l_indicateur(FxRobot robot) {
        inserer("Bananes", "{\"texte\":\"ban\",\"criteres\":[]}");
        robot.interact(this::construire);
        activerPremiereVue(robot);
        robot.interact(() -> recherche.setText("app"));
        assertThat(boutonEnregistrer(ongletsVues().getFirst())).isPresent();

        robot.interact(() -> recherche.setText("ban")); // retour exact à l'état de la vue

        assertThat(boutonEnregistrer(ongletsVues().getFirst()))
                .as("filtres redevenus identiques → vue non modifiée")
                .isEmpty();
    }

    @Test
    @DisplayName("Vue par défaut : active au chargement (filtres appliqués) et en lecture seule (sans ✎/✕)")
    void vue_par_defaut_active_au_chargement_et_lecture_seule(FxRobot robot) {
        VueSauvegardee defaut = new VueSauvegardee(null, FEATURE, "Avec a", "{\"texte\":\"a\",\"criteres\":[]}");
        robot.interact(() -> construireAvecDefauts(defaut));

        // Aucun filtre courant ne correspond au chargement → la 1re vue par défaut est appliquée (recherche « a »,
        // qui ne laisse que les lignes contenant « a »).
        assertThat(recherche.getText()).isEqualTo("a");
        assertThat(affichees).containsExactly("apple", "banana");
        // L'onglet par défaut, actif et non modifié, n'a QUE son libellé (pas de ✎/✕/💾 : lecture seule).
        assertThat(ongletsVues().getFirst().getChildren()).hasSize(1);
    }

    @Test
    @DisplayName("💾 sur une vue par défaut enregistre une NOUVELLE vue (ne l'écrase pas)")
    void enregistrer_depuis_une_vue_par_defaut_cree_une_nouvelle_vue(FxRobot robot) {
        VueSauvegardee defaut = new VueSauvegardee(null, FEATURE, "Avec a", "{\"texte\":\"a\",\"criteres\":[]}");
        saisieNom = defautNom -> Optional.of("Ma vue");
        robot.interact(() -> construireAvecDefauts(defaut));

        robot.interact(() -> recherche.setText("ab")); // diverge de la vue par défaut

        Button enregistrer = boutonEnregistrer(ongletsVues().getFirst()).orElseThrow();
        robot.interact(enregistrer::fire);

        // Une nouvelle vue utilisateur est créée ; la vue par défaut n'est jamais persistée.
        assertThat(dao.findByFeature(FEATURE)).singleElement().satisfies(vue -> {
            assertThat(vue.nom()).isEqualTo("Ma vue");
            assertThat(vue.descripteurJson()).contains("\"texte\":\"ab\"");
        });
    }
}
