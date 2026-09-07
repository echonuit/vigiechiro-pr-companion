package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import java.util.concurrent.Callable;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// [GesteVisible#remplacerLeTexte] remplace-t-il, ou ajoute-t-il (#5436) ?
///
/// La question n'est pas rhétorique. Deux bancs ont composé eux-mêmes la séquence
/// `push(CONTROL, A)` puis `write`, en croyant remplacer. Sur macOS, `^A` déplace le curseur en début
/// de ligne au lieu de tout sélectionner, et le texte s'est **ajouté**. Aucun des deux ne l'a vu,
/// parce qu'aucun ne regardait le champ : l'un constatait un chemin de sortie faux, l'autre attendait
/// en vain qu'une liste navigue.
///
/// Ce banc regarde le champ, et il est le seul à le faire. Il éprouve donc la couture, pas le
/// dialogue qui l'emploie.
@ExtendWith(ApplicationExtension.class)
class GesteVisibleRemplacementTest {

    private static final String DEJA_LA = "/un/chemin/deja/saisi";

    private static final String NOUVEAU = "/le/nouveau/chemin";

    private TextField champ;

    @Start
    void start(Stage stage) {
        champ = new TextField(DEJA_LA);
        champ.setId("champ");
        FenetreAjustable.poser(stage, new VBox(champ), 600, 200);
        stage.show();
    }

    @Test
    @DisplayName("#5436 : le champ contient le nouveau texte, et RIEN de l'ancien")
    void remplacer_ne_laisse_rien_de_l_ancien(FxRobot robot) {
        assertThat(champ.getText())
                .as("la prémisse du cas : le champ part rempli")
                .isEqualTo(DEJA_LA);

        GesteVisible.remplacerLeTexte(robot, "#champ", NOUVEAU);

        // `isEqualTo` et non `contains` : c'est toute la différence entre remplacer et ajouter, et
        // `contains` aurait laissé passer les deux.
        assertThat(luDansLeChamp()).isEqualTo(NOUVEAU);
    }

    @Test
    @DisplayName("#5436 : remplacer un champ VIDE écrit simplement le texte")
    void remplacer_un_champ_vide(FxRobot robot) {
        Attente.surLeFil(() -> champ.setText(""), "vider le champ", 5_000);

        GesteVisible.remplacerLeTexte(robot, "#champ", "/depuis/rien");

        assertThat(luDansLeChamp()).isEqualTo("/depuis/rien");
    }

    /// Le contenu du champ, lu sur le fil JavaFX (#5269).
    ///
    /// La `Callable` est nommée plutôt que passée en référence : `champ::getText` satisfait à la fois
    /// la surcharge `Runnable` et la surcharge `Callable` d'[Attente#surLeFil], et le compilateur
    /// refuse alors de choisir.
    private String luDansLeChamp() {
        Callable<String> lecture = champ::getText;
        return Attente.surLeFil(lecture, "relire ce que le champ contient", 5_000);
    }
}
