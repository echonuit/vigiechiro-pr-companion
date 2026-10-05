package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import java.util.concurrent.Callable;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// [GesteVisible#doubleCliquer] double-clique-t-il, et [GesteVisible#cliquerDroit] clique-t-il DROIT (#5908) ?
///
/// Chaque cas éprouve la seule chose qu'un banc puisse voir : la **conséquence** du bon bouton et du
/// bon nombre d'appuis. Un clic simple ne sélectionne aucun mot, un clic gauche n'ouvre aucun menu.
///
/// **Ce banc ne voit PAS sur quel fil la cible est située**, et aucun banc Java ne le peut : le fil
/// JavaFX rend à chaque trame, donc il recalcule la liaison le premier et sert son cache. C'est le
/// garde de l'ADR 5707 qui tient cette propriété, en comptant les sites.
@ExtendWith(ApplicationExtension.class)
class GesteVisibleDoubleEtDroitTest {

    /// Assez long pour DÉBORDER deux cents pixels : sur un texte court, le centre du champ tombe
    /// après le dernier caractère, et le double-clic ne sélectionnerait rien.
    private static final String DEBORDE = "640002 Le pre un nom de lieu assez long pour deborder ce champ";

    private TextField champ;
    private Button bouton;

    @Start
    void start(Stage stage) {
        champ = new TextField(DEBORDE);
        champ.setId("champ");
        champ.setPrefWidth(200);
        champ.setMaxWidth(200);

        bouton = new Button("ici");
        bouton.setId("bouton");
        bouton.setContextMenu(new ContextMenu(new MenuItem("Copier")));

        FenetreAjustable.poser(stage, new VBox(champ, bouton), 600, 240);
        stage.show();
    }

    @Test
    @DisplayName("#5908 : le double-clic SÉLECTIONNE un mot, là où un clic simple poserait un caret")
    void le_double_clic_selectionne_un_mot(FxRobot robot) {
        // Un `TextField` qui prend le focus sélectionne TOUT son texte, et ce banc l'a appris en
        // rougissant sur sa propre prémisse. On POSE donc l'état de départ au lieu de le supposer.
        Attente.surLeFil(champ::deselect, "retirer la sélection avant le geste", 5_000);
        assertThat(selection())
                .as("la prémisse, POSÉE et non supposée : rien n'est sélectionné avant le geste")
                .isEmpty();

        GesteVisible.doubleCliquer(robot, "#champ");

        // DEUX bornes, parce qu'une seule laisserait passer une dégradation dans un sens : un clic
        // simple rendrait une sélection VIDE, et un select-all accidentel rendrait TOUT le texte.
        // Pas d'égalité à un mot précis : le centre du champ dépend de la police du poste.
        assertThat(selection())
                .as("un double-clic dans un texte sélectionne LE MOT sous le pointeur, ni rien ni tout")
                .isNotEmpty()
                .isNotEqualTo(DEBORDE);
    }

    @Test
    @DisplayName("#5908 : le clic droit OUVRE le menu contextuel, là où un clic gauche ne l'ouvrirait pas")
    void le_clic_droit_ouvre_le_menu(FxRobot robot) {
        assertThat(menuOuvert())
                .as("la prémisse du cas : le menu est fermé avant le geste")
                .isFalse();

        GesteVisible.cliquerDroit(robot, bouton);

        assertThat(menuOuvert())
                .as("un clic droit sur un contrôle qui porte un menu contextuel l'ouvre, dans sa" + " propre fenêtre")
                .isTrue();
    }

    /// Le texte sélectionné, lu sur le fil JavaFX.
    ///
    /// La `Callable` est nommée plutôt que passée en référence : elle satisferait sinon les deux
    /// surcharges d'[Attente#surLeFil], et le compilateur refuse de choisir.
    private String selection() {
        Callable<String> lecture = champ::getSelectedText;
        return Attente.surLeFil(lecture, "relire la sélection du champ", 5_000);
    }

    /// Si le menu contextuel du bouton est montré, lu sur le fil JavaFX.
    private boolean menuOuvert() {
        Callable<Boolean> lecture = () -> bouton.getContextMenu().isShowing();
        return Attente.surLeFil(lecture, "relire l'état du menu contextuel", 5_000);
    }
}
