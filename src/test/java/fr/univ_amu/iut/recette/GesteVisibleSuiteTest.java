package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/// [GesteVisible#ecrireALaSuite] écrit-il À LA FIN, ou là où le clic est tombé (#5869) ?
///
/// **Le texte de ce banc DÉBORDE son champ, et c'est la condition de sa valeur.** Sur un texte court,
/// le centre du champ - où `pointSurLeFil` fait cliquer - tombe APRÈS le dernier caractère, si bien
/// que le caret arrive à la fin tout seul. Un banc monté comme celui de [GesteVisibleRemplacementTest],
/// avec un chemin de vingt caractères dans un champ de six cents pixels, passerait donc même si l'aide
/// omettait son `end()` : il ne distinguerait rien.
///
/// Le champ fait ici deux cents pixels pour un texte de quatre-vingts caractères. Le clic tombe vers
/// le quatorzième, et sans le `end()` la saisie s'insérerait au milieu.
///
/// **Et le troisième cas éprouve le REFUS**, qui est la moitié qu'on oublie : un champ désactivé ne
/// prend pas le clavier, donc la saisie n'atterrit pas, donc la relecture doit refuser. Sans ce cas,
/// retirer la relecture ne ferait rougir personne et elle serait décorative.
@ExtendWith(ApplicationExtension.class)
class GesteVisibleSuiteTest {

    /// Quatre-vingts caractères pour deux cents pixels : le texte déborde, donc le clic tombe DEDANS.
    private static final String DEBORDE =
            "/un/chemin/deja/saisi/qui/deborde/largement/le/champ/pour/que/le/clic/tombe/dedans";

    private static final String AJOUT = "/ajoute";

    private TextField champ;

    @Start
    void start(Stage stage) {
        champ = new TextField(DEBORDE);
        champ.setId("champ");
        champ.setPrefWidth(200);
        champ.setMaxWidth(200);
        FenetreAjustable.poser(stage, new VBox(champ), 600, 200);
        stage.show();
    }

    @Test
    @DisplayName("#5869 : la saisie s'ajoute À LA FIN, pas là où le clic est tombé")
    void ecrire_a_la_suite_ajoute_a_la_fin(FxRobot robot) {
        assertThat(champ.getText())
                .as("la prémisse du cas : le champ part rempli d'un texte qui déborde")
                .isEqualTo(DEBORDE);

        GesteVisible.ecrireALaSuite(robot, "#champ", AJOUT);

        // `isEqualTo(DEBORDE + AJOUT)` et non `contains(AJOUT)` : c'est toute la différence entre
        // « à la fin » et « au milieu », et `contains` aurait laissé passer les deux.
        assertThat(luDansLeChamp()).isEqualTo(DEBORDE + AJOUT);
    }

    @Test
    @DisplayName("#5869 : écrire à la suite d'un champ VIDE écrit simplement le texte")
    void ecrire_a_la_suite_d_un_champ_vide(FxRobot robot) {
        Attente.surLeFil(() -> champ.setText(""), "vider le champ", 5_000);

        GesteVisible.ecrireALaSuite(robot, "#champ", "/depuis/rien");

        assertThat(luDansLeChamp()).isEqualTo("/depuis/rien");
    }

    @Test
    @DisplayName("#5869 : si la saisie n'atterrit pas, l'aide REFUSE au lieu de rendre la main")
    void une_saisie_qui_n_atterrit_pas_fait_refuser(FxRobot robot) {
        // Un champ désactivé ne prend pas le clavier : le clic tombe, le caret se pose, et la saisie
        // n'arrive nulle part. C'est le seul moyen honnête de forcer l'échec que la relecture garde.
        Attente.surLeFil(() -> champ.setDisable(true), "désactiver le champ", 5_000);

        assertThatThrownBy(() -> GesteVisible.ecrireALaSuite(robot, "#champ", AJOUT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("#champ")
                .hasMessageContaining(DEBORDE + AJOUT);

        assertThat(luDansLeChamp())
                .as("et le champ est resté tel quel, ce qui est bien la raison du refus")
                .isEqualTo(DEBORDE);
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
