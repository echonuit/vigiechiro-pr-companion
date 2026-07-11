package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Scene;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.PickResult;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Tests unitaires du helper [DepotFichier] sur le vrai chemin `DragEvent` / `Dragboard` : le dépôt
/// délègue les fichiers à `surDepot` et marque le dépôt complété quand l'activation est vraie ; il est
/// refusé hors activation (source non workflow) ou sans fichier. Presse-papiers mocké, pas de VM.
@ExtendWith(ApplicationExtension.class)
class DepotFichierTest {

    private Region cible;

    @Start
    void start(Stage stage) {
        cible = new Region();
        stage.setScene(new Scene(cible, 100, 100));
        stage.show();
    }

    private static Dragboard dragboardAvec(File... fichiers) {
        Dragboard presse = mock(Dragboard.class);
        when(presse.hasFiles()).thenReturn(fichiers.length > 0);
        when(presse.getFiles()).thenReturn(List.of(fichiers));
        return presse;
    }

    private DragEvent depot(Dragboard presse) {
        return evenement(DragEvent.DRAG_DROPPED, presse);
    }

    private DragEvent evenement(EventType<DragEvent> type, Dragboard presse) {
        return new DragEvent(
                null, cible, type, presse, 0, 0, 0, 0, TransferMode.COPY, null, null, new PickResult(cible, 0, 0));
    }

    @Test
    @DisplayName("Dépôt actif avec fichiers : surDepot reçoit les fichiers, le dépôt est marqué complété")
    void depot_actif_delegue(FxRobot robot) {
        List<List<File>> recus = new ArrayList<>();
        DepotFichier.installer(cible, () -> true, fichiers -> {
            recus.add(fichiers);
            return true;
        });
        DragEvent drop = depot(dragboardAvec(new File("a.csv")));

        robot.interact(() -> Event.fireEvent(cible, drop));

        assertThat(recus).containsExactly(List.of(new File("a.csv")));
        assertThat(drop.isDropCompleted()).isTrue();
    }

    @Test
    @DisplayName("Dépôt hors activation (source non workflow) : refusé, surDepot jamais appelé")
    void depot_inactif_refuse(FxRobot robot) {
        List<List<File>> recus = new ArrayList<>();
        DepotFichier.installer(cible, () -> false, fichiers -> {
            recus.add(fichiers);
            return true;
        });
        DragEvent drop = depot(dragboardAvec(new File("a.csv")));

        robot.interact(() -> Event.fireEvent(cible, drop));

        assertThat(recus)
                .as("surDepot ne doit pas être sollicité hors activation")
                .isEmpty();
        assertThat(drop.isDropCompleted()).isFalse();
    }

    @Test
    @DisplayName("Dépôt sans fichier (texte glissé) : refusé sans solliciter surDepot")
    void depot_sans_fichier_refuse(FxRobot robot) {
        List<List<File>> recus = new ArrayList<>();
        DepotFichier.installer(cible, () -> true, fichiers -> {
            recus.add(fichiers);
            return true;
        });
        DragEvent drop = depot(dragboardAvec());

        robot.interact(() -> Event.fireEvent(cible, drop));

        assertThat(recus).isEmpty();
        assertThat(drop.isDropCompleted()).isFalse();
    }

    @Test
    @DisplayName("#801 : le survol d'un fichier acceptable surligne la cible, la sortie retire la surbrillance")
    void survol_actif_surligne_la_cible(FxRobot robot) {
        DepotFichier.installer(cible, () -> true, fichiers -> true);

        robot.interact(
                () -> Event.fireEvent(cible, evenement(DragEvent.DRAG_ENTERED, dragboardAvec(new File("a.csv")))));
        assertThat(cible.getStyleClass()).as("retour visuel au survol").contains("depot-survol");

        robot.interact(
                () -> Event.fireEvent(cible, evenement(DragEvent.DRAG_EXITED, dragboardAvec(new File("a.csv")))));
        assertThat(cible.getStyleClass()).as("surbrillance retirée à la sortie").doesNotContain("depot-survol");
    }

    @Test
    @DisplayName("#801 : hors activation (source non workflow), le survol ne surligne pas")
    void survol_inactif_ne_surligne_pas(FxRobot robot) {
        DepotFichier.installer(cible, () -> false, fichiers -> true);

        robot.interact(
                () -> Event.fireEvent(cible, evenement(DragEvent.DRAG_ENTERED, dragboardAvec(new File("a.csv")))));

        assertThat(cible.getStyleClass()).doesNotContain("depot-survol");
    }
}
