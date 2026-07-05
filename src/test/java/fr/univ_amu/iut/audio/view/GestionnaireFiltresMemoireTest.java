package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.audio.view.GestionnaireFiltres.EtatCritere;
import fr.univ_amu.iut.audio.view.GestionnaireFiltres.EtatFiltres;
import fr.univ_amu.iut.audio.viewmodel.FiltresAudio;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Mémoire de session des **filtres** (#484) : l'état capturé (recherche texte + puces actives avec leurs
/// valeurs) doit être restitué fidèlement. On vérifie le tour complet **restaurer → capturer** : capturer
/// après restauration doit rendre exactement l'état cible (curseur de proba, plage horaire, puce booléenne).
@ExtendWith(ApplicationExtension.class)
class GestionnaireFiltresMemoireTest {

    private GestionnaireFiltres gestion;

    @Start
    void start(javafx.stage.Stage stage) {
        FilteredList<LigneObservationAudio> affichees = new FilteredList<>(FXCollections.observableArrayList());
        FiltresAudio filtres = new FiltresAudio(affichees, () -> {});
        gestion = new GestionnaireFiltres(
                new TextField(),
                new MenuButton(),
                new HBox(),
                filtres,
                List.of(
                        CriteresAudio.statut(),
                        CriteresAudio.probabilite(),
                        CriteresAudio.heure(),
                        CriteresAudio.references()));
    }

    @Test
    @DisplayName("Restaurer un état de filtres puis le capturer redonne exactement le même état")
    void restaurer_puis_capturer_est_fidele(FxRobot robot) {
        // Curseur proba à 0,8 ; plage horaire de 3 h (index 3) à 9 h (index 9) ; puce Références (booléenne).
        EtatFiltres cible = new EtatFiltres(
                "bruant",
                List.of(
                        new EtatCritere("proba", List.of(0.8)),
                        new EtatCritere("heure", List.of(3.0, 9.0)),
                        new EtatCritere("references", List.of())));

        EtatFiltres[] capture = new EtatFiltres[1];
        robot.interact(() -> {
            gestion.restaurer(cible);
            capture[0] = gestion.capturer();
        });

        assertThat(capture[0]).isEqualTo(cible);
    }
}
