package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.commun.viewmodel.ResteDeRestauration;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Scene;
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

/// Mémoire de session du **tri** de la table audio (#484) : le tri d'une ouverture est restitué à la
/// suivante. On simule une fermeture (retrait de la table de la scène) puis une réouverture (nouvelle table).
@ExtendWith(ApplicationExtension.class)
class MemoireRevueAudioTest {

    private final MemoireRevueAudio memoire = new MemoireRevueAudio();
    private VBox racine;

    @Start
    void start(Stage stage) {
        racine = new VBox();
        stage.setScene(new Scene(racine, 300, 200));
        stage.show();
    }

    private static TableView<LigneObservationAudio> tableAvecColonnes() {
        TableView<LigneObservationAudio> table = new TableView<>();
        table.getColumns().add(new TableColumn<>("Date"));
        table.getColumns().add(new TableColumn<>("Heure"));
        return table;
    }

    @Test
    @DisplayName("Le tri d'une ouverture (colonne + sens) est restitué à la réouverture")
    void memorise_et_restaure_le_tri(FxRobot robot) {
        // Ouverture 1 : trier par « Heure » décroissant, puis fermer (retrait de la scène → mémorisation).
        TableView<LigneObservationAudio> premiere = tableAvecColonnes();
        robot.interact(() -> {
            racine.getChildren().add(premiere);
            memoire.installer(premiere, null, reste -> {});
            TableColumn<LigneObservationAudio, ?> heure = premiere.getColumns().get(1);
            heure.setSortType(SortType.DESCENDING);
            premiere.getSortOrder().add(heure);
        });
        robot.interact(() -> racine.getChildren().remove(premiere));

        // Ouverture 2 : nouvelle table (comme un rechargement de la vue) → le tri mémorisé est réappliqué.
        TableView<LigneObservationAudio> seconde = tableAvecColonnes();
        robot.interact(() -> {
            racine.getChildren().add(seconde);
            memoire.installer(seconde, null, reste -> {});
        });

        assertThat(seconde.getSortOrder()).extracting(TableColumn::getText).containsExactly("Heure");
        assertThat(seconde.getColumns().get(1).getSortType()).isEqualTo(SortType.DESCENDING);
    }

    @Test
    @DisplayName("#3093 : un filtre que la réouverture ne sait plus replacer est signalé, pas perdu")
    void filtre_non_replace_est_signale(FxRobot robot) {
        // Le geste banal : poser un filtre de lieu, aller écouter un son, revenir. Entre-temps le jeu de
        // lignes a changé et la valeur mémorisée n'est plus offerte. Sans signalement, l'écran se rouvre
        // avec la puce visible et ne filtre plus : il montre plus que ce qu'il annonce.
        List<ResteDeRestauration> signalements = new ArrayList<>();

        // Ouverture 1 : le carré « 640380 » est présent, on filtre dessus, puis on ferme (le retrait de
        // la scène déclenche la mémorisation).
        TableView<LigneObservationAudio> premiere = tableAvecColonnes();
        robot.interact(() -> {
            racine.getChildren().add(premiere);
            GestionnaireFiltres<LigneObservationAudio> filtres =
                    gestionnaireSur(ligneAuCarre("640380"), ligneAuCarre("640381"));
            memoire.installer(premiere, filtres, reste -> {});
            filtres.poser("lieu", List.of("640380"));
        });
        robot.interact(() -> racine.getChildren().remove(premiere));

        // Ouverture 2 : plus aucune ligne du carré « 640380 », la valeur mémorisée n'est plus offerte.
        TableView<LigneObservationAudio> seconde = tableAvecColonnes();
        robot.interact(() -> {
            racine.getChildren().add(seconde);
            memoire.installer(seconde, gestionnaireSur(ligneAuCarre("640381")), signalements::add);
        });

        assertThat(signalements)
                .as("la mémoire de session doit dire ce qu'elle n'a pas su remettre en place")
                .hasSize(1);
        // Ce qui se mémorise est l'étiquette affichée, et le carré porte son nom convivial depuis #3157.
        // Le rattrapage (#3158) ne peut rien ici : ce n'est pas un renommage, c'est un lieu qui a
        // réellement disparu du jeu de lignes.
        //
        // Les carrés de cette fixture sont des NUMÉROS, et non « Z1 » / « Z2 » comme avant #3157 : un
        // carré nommé comme un code de point produisait deux entrées partageant le segment « Z1 » (le
        // carré et le point « Z1 · A1 »), que le rattrapage refuse d'arbitrer. La collision était un
        // artefact de fixture - un numéro de carré a six chiffres.
        assertThat(signalements.get(0).valeursPerdues()).containsExactly("640380 · Site");
    }

    /// Une barre de filtres portant la puce « Lieu » de l'écran audio, alimentée par `lignes`.
    private static GestionnaireFiltres<LigneObservationAudio> gestionnaireSur(LigneObservationAudio... lignes) {
        ObservableList<LigneObservationAudio> source = FXCollections.observableArrayList(lignes);
        return new GestionnaireFiltres<>(
                new TextField(),
                new MenuButton(),
                new FlowPane(),
                new Filtres<>(new FilteredList<>(source), () -> {}),
                List.of(CriteresAudio.lieu(() -> source)),
                CriteresAudio.rechercheTexte());
    }

    /// Une ligne d'observation rattachée au carré `carre` : seule dimension qui varie, les autres
    /// (point, site) restant constantes pour que le carré soit ce qui distingue les jeux.
    private static LigneObservationAudio ligneAuCarre(String carre) {
        return new LigneObservationAudio(
                1L,
                11L,
                7L,
                1,
                "2026-06-20",
                carre,
                "A1",
                "Site",
                "Pippip",
                0.9,
                null,
                null,
                StatutObservation.NON_TOUCHEE,
                false,
                null,
                45,
                null,
                "Pippip",
                null,
                "Chiroptères",
                "PaRec_1.wav",
                0.2,
                0.4,
                null,
                false,
                null,
                null,
                null,
                null,
                0,
                null);
    }
}
