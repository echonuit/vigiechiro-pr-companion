package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.commun.viewmodel.ResteDeRestauration;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.SortType;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// La **mémoire de session** partagée (#3098) : retrouver ses filtres et son tri en revenant sur un
/// écran, sans tout re-régler.
///
/// Née dans Sons & validation (#484), elle y était restée : les trois autres écrans à barre de filtres
/// repartaient de zéro alors qu'ils partagent le même socle.
///
/// **Filtres et tri sont mémorisés séparément**, et ce n'est pas une commodité. Les quatre écrans ont
/// tous des filtres ; ils ont zéro, une ou trois tables. Une mémoire qui exigerait « la » table de
/// l'écran ne conviendrait ni à Activité (un graphe) ni à Espèces & observations (trois tables).
@ExtendWith(ApplicationExtension.class)
class MemoireFiltresTest {

    private static final String ECRAN = "analyse";
    private static final String AUTRE_ECRAN = "multisite";

    private final MemoireFiltres memoire = new MemoireFiltres();
    private VBox racine;

    @Start
    void start(Stage stage) {
        racine = new VBox();
        FenetreAjustable.poser(stage, racine, 300, 200);
        FenetreAjustable.afficher(stage);
    }

    private static TableView<String> tableAvecColonnes(String id) {
        TableView<String> table = new TableView<>();
        table.setId(id);
        table.getColumns().add(new TableColumn<>("Date"));
        table.getColumns().add(new TableColumn<>("Heure"));
        return table;
    }

    /// Une barre de filtres portant une puce à cocher sur les valeurs de `source`.
    private static GestionnaireFiltres<String> gestionnaireSur(List<String> source) {
        ObservableList<String> lignes = FXCollections.observableArrayList(source);
        return new GestionnaireFiltres<>(
                new TextField(),
                new MenuButton(),
                new FlowPane(),
                new Filtres<>(new FilteredList<>(lignes), () -> {}),
                List.of(CritereListe.multiple(
                        ClesCriteres.LIEU, "Lieu", "Choisir un lieu", () -> source, ligne -> ligne)),
                (ligne, aiguille) -> ligne.contains(aiguille));
    }

    @Test
    @DisplayName("#3098 : le tri d'une ouverture est restitué à la suivante")
    void le_tri_est_restitue(FxRobot robot) {
        TableView<String> premiere = tableAvecColonnes("tableLignes");
        robot.interact(() -> {
            racine.getChildren().add(premiere);
            memoire.memoriserTri(ECRAN, premiere);
            TableColumn<String, ?> heure = premiere.getColumns().get(1);
            heure.setSortType(SortType.DESCENDING);
            premiere.getSortOrder().add(heure);
        });
        robot.interact(() -> racine.getChildren().remove(premiere));

        TableView<String> seconde = tableAvecColonnes("tableLignes");
        robot.interact(() -> {
            racine.getChildren().add(seconde);
            memoire.memoriserTri(ECRAN, seconde);
        });

        assertThat(seconde.getSortOrder()).extracting(TableColumn::getText).containsExactly("Heure");
        assertThat(seconde.getColumns().get(1).getSortType()).isEqualTo(SortType.DESCENDING);
    }

    @Test
    @DisplayName("#3098 : trois tables d'un MÊME écran ne confondent pas leurs tris")
    void trois_tables_du_meme_ecran_ne_se_confondent_pas(FxRobot robot) {
        // Le cas qui a fait refaire ce socle. Espèces & observations a trois tables ; mémoriser « la »
        // table de l'écran aurait obligé à en choisir une, arbitrairement et sans l'écrire.
        TableView<String> especes = tableAvecColonnes("tableEspeces");
        TableView<String> carres = tableAvecColonnes("tableCarres");
        robot.interact(() -> {
            racine.getChildren().addAll(especes, carres);
            memoire.memoriserTri(ECRAN, especes);
            memoire.memoriserTri(ECRAN, carres);
            especes.getSortOrder().add(especes.getColumns().get(0));
        });
        robot.interact(() -> racine.getChildren().removeAll(especes, carres));

        TableView<String> especesRouvert = tableAvecColonnes("tableEspeces");
        TableView<String> carresRouvert = tableAvecColonnes("tableCarres");
        robot.interact(() -> {
            racine.getChildren().addAll(especesRouvert, carresRouvert);
            memoire.memoriserTri(ECRAN, especesRouvert);
            memoire.memoriserTri(ECRAN, carresRouvert);
        });

        assertThat(especesRouvert.getSortOrder())
                .extracting(TableColumn::getText)
                .containsExactly("Date");
        assertThat(carresRouvert.getSortOrder())
                .as("le tri de la table des espèces ne doit pas déborder sur celle des carrés")
                .isEmpty();
    }

    @Test
    @DisplayName("#3098 : chaque écran a SA mémoire, elles ne se mélangent pas")
    void chaque_ecran_a_sa_memoire(FxRobot robot) {
        TableView<String> ecranA = tableAvecColonnes("tableLignes");
        robot.interact(() -> {
            racine.getChildren().add(ecranA);
            memoire.memoriserTri(ECRAN, ecranA);
            ecranA.getSortOrder().add(ecranA.getColumns().get(0));
        });
        robot.interact(() -> racine.getChildren().remove(ecranA));

        TableView<String> ecranB = tableAvecColonnes("tableLignes");
        robot.interact(() -> {
            racine.getChildren().add(ecranB);
            memoire.memoriserTri(AUTRE_ECRAN, ecranB);
        });

        assertThat(ecranB.getSortOrder())
                .as("le tri mémorisé pour « %s » ne doit pas s'appliquer à « %s »", ECRAN, AUTRE_ECRAN)
                .isEmpty();
    }

    @Test
    @DisplayName("#3098 : un filtre que la réouverture ne sait plus replacer est signalé, pas perdu")
    void un_filtre_non_replace_est_signale(FxRobot robot) {
        // Porté depuis `MemoireRevueAudioTest` (#3093), et c'est ce qui rend l'extension à trois écrans
        // de plus **sûre** : sans ce compte rendu, la mémoire perdrait un filtre en silence sur chacun
        // d'eux. Le geste est banal : poser un filtre, sortir, revenir - et le jeu a changé entre-temps.
        List<ResteDeRestauration> signalements = new ArrayList<>();
        VBox ancrage = new VBox();

        robot.interact(() -> {
            racine.getChildren().add(ancrage);
            GestionnaireFiltres<String> filtres = gestionnaireSur(List.of("Aix", "Venelles"));
            memoire.installer(ECRAN, ancrage, filtres, reste -> {});
            filtres.poser(ClesCriteres.LIEU, List.of("Aix"));
        });
        robot.interact(() -> racine.getChildren().remove(ancrage));

        // « Aix » n'est plus dans le jeu courant : la valeur mémorisée ne peut plus être cochée.
        VBox ancrageRouvert = new VBox();
        robot.interact(() -> {
            racine.getChildren().add(ancrageRouvert);
            memoire.installer(ECRAN, ancrageRouvert, gestionnaireSur(List.of("Venelles")), signalements::add);
        });

        assertThat(signalements)
                .as("la mémoire doit dire ce qu'elle n'a pas su remettre en place")
                .hasSize(1);
        assertThat(signalements.get(0).valeursPerdues()).containsExactly("Aix");
    }

    @Test
    @DisplayName("#3098 : « Tout effacer » oublie l'état, le retour repart à neuf")
    void oublier_repart_a_neuf(FxRobot robot) {
        // Sans cela, le bouton viderait les filtres à l'écran et la mémoire les remettrait à la visite
        // suivante : le geste paraîtrait ne pas avoir pris.
        TableView<String> premiere = tableAvecColonnes("tableLignes");
        robot.interact(() -> {
            racine.getChildren().add(premiere);
            memoire.memoriserTri(ECRAN, premiere);
            premiere.getSortOrder().add(premiere.getColumns().get(1));
        });
        robot.interact(() -> racine.getChildren().remove(premiere));

        memoire.oublier(ECRAN);

        TableView<String> seconde = tableAvecColonnes("tableLignes");
        robot.interact(() -> {
            racine.getChildren().add(seconde);
            memoire.memoriserTri(ECRAN, seconde);
        });

        assertThat(seconde.getSortOrder()).isEmpty();
    }

    @Test
    @DisplayName("#3098 : une colonne disparue est ignorée, pas une erreur")
    void une_colonne_disparue_est_ignoree(FxRobot robot) {
        TableView<String> premiere = tableAvecColonnes("tableLignes");
        robot.interact(() -> {
            racine.getChildren().add(premiere);
            memoire.memoriserTri(ECRAN, premiere);
            premiere.getSortOrder().add(premiere.getColumns().get(1));
        });
        robot.interact(() -> racine.getChildren().remove(premiere));

        TableView<String> sansHeure = new TableView<>();
        sansHeure.setId("tableLignes");
        sansHeure.getColumns().add(new TableColumn<>("Date"));
        robot.interact(() -> {
            racine.getChildren().add(sansHeure);
            memoire.memoriserTri(ECRAN, sansHeure);
        });

        assertThat(sansHeure.getSortOrder()).isEmpty();
    }
}
