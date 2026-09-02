package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.fixture.SortieCapturee;
import java.io.PrintStream;
import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.NodeQueryUtils;

/// [GesteVisible#amenerDansLeCadre] survit-il à une course INTERNE À JAVAFX pendant ses bornes ?
///
/// Sous contention **disque**, `PathUtils.configShape` itère les éléments d'un `Path` pendant qu'ils
/// changent, et la `ConcurrentModificationException` remonte jusqu'au geste, qui abandonne. Aucun code
/// du dépôt ne manipule de `Path` : celui-là appartient à JavaFX (#4823).
///
/// Le geste est **déjà** une boucle de réessai, pour des bornes qui « peuvent ne pas encore être
/// établies ». Des bornes illisibles à cet instant sont ce cas même, sous forme d'exception.
///
/// ## Pourquoi un saboteur
///
/// La vraie course tombe 1 fois sur 45, et seulement sous contention disque. Un garde qui en
/// dépendrait ne garderait rien - c'est déjà la doctrine de [GesteVisibleCadreTest]. Le saboteur lève
/// l'exception telle que JavaFX la lève, pile comprise, puisque c'est elle que le remède lit.
@ExtendWith(ApplicationExtension.class)
class GesteVisibleCourseDeBornesTest {

    private static final double REMPLISSAGE = 1500;

    private static final double LARGEUR = 600;

    private static final double HAUTEUR = 400;

    private ScrollPane pane;

    @Start
    void start(Stage stage) {
        Button cible = new Button("La cible");
        cible.setId("cible");
        pane = new ScrollPane(new VBox(remplissage(), cible, remplissage()));
        pane.setPrefViewportHeight(HAUTEUR);
        FenetreAjustable.poser(stage, pane, LARGEUR, HAUTEUR);
        FenetreAjustable.afficher(stage);
    }

    private static Region remplissage() {
        Region region = new Region();
        region.setMinHeight(REMPLISSAGE);
        region.setPrefHeight(REMPLISSAGE);
        return region;
    }

    @Test
    @DisplayName("#4823 : une passe qu'une course de bornes fait avorter est REJOUÉE")
    void une_course_de_bornes_ne_fait_pas_echouer_le_geste(FxRobot robot) {
        AtomicInteger courses = poserUneCourseDeBornes(robot, 1, GesteVisibleCourseDeBornesTest::commeJavafx);

        GesteVisible.amenerDansLeCadre(robot, "#cible");

        assertThat(courses.get())
                .as("la course a bien eu lieu : sans elle ce cas ne prouverait rien")
                .isEqualTo(1);
        assertThat(estDansLeCadre(robot))
                .as("et la cible est dans le cadre malgré elle. C'est la promesse du geste, et la"
                        + " course est un accident de calcul, pas un refus du panneau")
                .isTrue();
    }

    @Test
    @DisplayName("#4823 : une exception qui n'est PAS une course de bornes n'est pas avalée")
    void une_vraie_panne_reste_une_panne(FxRobot robot) {
        poserUneCourseDeBornes(robot, 1, () -> new IllegalStateException("le banc est mal monté"));

        assertThatThrownBy(() -> GesteVisible.amenerDansLeCadre(robot, "#cible"))
                .as("rattraper large éteindrait les vraies pannes. Le remède ne reconnaît QUE la course"
                        + " de bornes, par son type ET sa pile")
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("#4823 : une course qui ne cesse jamais est DÉNONCÉE, et le geste dit combien")
    void une_course_qui_ne_cesse_jamais_est_comptee(FxRobot robot) {
        poserUneCourseDeBornes(robot, Integer.MAX_VALUE, GesteVisibleCourseDeBornesTest::commeJavafx);

        assertThatThrownBy(() -> GesteVisible.amenerDansLeCadre(robot, "#cible"))
                .as("l'ADR 0008 interdit l'échec silencieux : ce qui a été absorbé doit paraître dans le"
                        + " message, sinon un banc qui rame se lit comme un banc qui refuse")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("#cible")
                .hasMessageContaining("course");
    }

    /// L'exception TELLE QUE JavaFX la lève, pile comprise : c'est cette pile que le remède lit.
    private static RuntimeException commeJavafx() {
        ConcurrentModificationException course = new ConcurrentModificationException();
        course.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("java.util.AbstractList$Itr", "checkForComodification", "AbstractList.java", 401),
            new StackTraceElement("com.sun.javafx.scene.shape.PathUtils", "configShape", "PathUtils.java", 45),
            new StackTraceElement("javafx.scene.shape.Shape", "doComputeGeomBounds", "Shape.java", 912),
            new StackTraceElement("javafx.scene.Parent", "updateCachedBounds", "Parent.java", 1785),
        });
        return course;
    }

    /// Un saboteur qui lève `quoi` les `coups` premières fois que le panneau bouge.
    private AtomicInteger poserUneCourseDeBornes(
            FxRobot robot, int coups, java.util.function.Supplier<RuntimeException> quoi) {
        AtomicInteger faits = new AtomicInteger();
        ChangeListener<Number> saboteur = (observable, avant, apres) -> {
            // `apres == 0` est la remise à zéro faite juste en dessous : elle rentre ici et doit
            // repartir sans rien compter.
            if (faits.get() >= coups || apres.doubleValue() == 0) {
                return;
            }
            faits.incrementAndGet();
            // SANS cette remise à zéro, le saboteur ne sabote qu'une fois : un `ChangeListener` ne
            // se déclenche que si la valeur CHANGE, et la passe suivante recalcule la même. Le cas
            // « qui ne cesse jamais » passait alors au vert du second coup.
            pane.setVvalue(0);
            throw quoi.get();
        };
        robot.interact(() -> {
            pane.setVvalue(0);
            pane.vvalueProperty().addListener(saboteur);
        });
        return faits;
    }

    private static boolean estDansLeCadre(FxRobot robot) {
        Node cible = robot.lookup("#cible").query();
        return NodeQueryUtils.isVisible().test(cible);
    }

    @Test
    @DisplayName("#4823 : une course ABSORBÉE se dit, sinon le remède est invisible")
    void une_course_absorbee_est_annoncee(FxRobot robot) {
        // Le harnais PARTAGÉ, et non un tampon monté ici : son UTF-8 est fixé à un seul endroit, et
        // le cliquet #2866 refuse toute nouvelle copie de cet échafaudage.
        SortieCapturee capture = new SortieCapturee();
        PrintStream avant = System.err;
        System.setErr(capture.erreur());
        try {
            poserUneCourseDeBornes(robot, 1, GesteVisibleCourseDeBornesTest::commeJavafx);
            GesteVisible.amenerDansLeCadre(robot, "#cible");
        } finally {
            System.setErr(avant);
        }

        assertThat(capture.texteErreur())
                .as("un vert obtenu APRÈS une course n'est pas le même vert qu'un vert du premier coup :"
                        + " sans cette ligne, on ne saurait plus si le banc est sain ou rattrapé")
                .contains("#cible")
                .contains("course interne à JavaFX");
    }
}
