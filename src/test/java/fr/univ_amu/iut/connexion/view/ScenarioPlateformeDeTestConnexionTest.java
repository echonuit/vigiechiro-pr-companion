package fr.univ_amu.iut.connexion.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le premier scénario d'écran joué contre la **plateforme de test** (#5665, ADR 5641).
///
/// Le banc part avec le jeton que la plateforme a frappé pour l'observatrice déclarée, sans profil.
/// Ouvrir la modale de connexion la fait revérifier ce jeton contre le **vrai code serveur**, sans
/// geste : c'est le même chemin que `ScenarioConnecteConnexionTest` emprunte face à la plateforme
/// réelle, sans jeton de production ni secret.
///
/// L'état de départ étant déclaré, l'identité attendue est connue : « Observatrice ». Le scénario de la
/// plateforme réelle ne peut asserter que la non-vacuité, le compte de tournage changeant.
@Tag("plateforme-de-test")
@ExtendWith({ApplicationExtension.class, SansExceptionAvalee.class})
class ScenarioPlateformeDeTestConnexionTest {

    private static final String LIBELLE_ENTREE_MENU = "Se connecter à Vigie-Chiro…";

    /// Ce que le produit met sur le badge d'identité **quand la plateforme a répondu**, et lui seul.
    private static final String BADGE_CONNECTE = "badge-succes";

    /// La revérification puis la synchro d'un compte qui porte deux participations : quelques
    /// secondes sur la plateforme de test. Le butoir est large pour un runner chargé, pas pour masquer
    /// une modale qui ne répondrait pas.
    private static final long BUTOIR_MS = 60_000;

    @Start
    void start(Stage stage) throws IOException {
        BancDeRecette.surLeChrome()
                .taille(1100, 720)
                // ASYNCHRONE : la revérification part sur le réseau, et en synchrone le fil JavaFX serait
                // bloqué pendant l'appel.
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .surLaPlateformeDeTest("observatrice")
                .remplacer(new AbstractModule() {
                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        return lien -> {};
                    }
                })
                .montrer(stage);
    }

    @Test
    void la_modale_reverifie_le_jeton_de_la_plateforme_de_test_et_nomme_l_observatrice(FxRobot robot)
            throws TimeoutException {
        GesteVisible.choisir(robot, "#menuOutils", LIBELLE_ENTREE_MENU);

        Attente.queSurLeFil(
                () -> classes(robot, "#labelIdentite").contains(BADGE_CONNECTE),
                "le badge d'identité n'est pas passé à « " + BADGE_CONNECTE + " » : la plateforme de test"
                        + " n'a pas validé le jeton qu'elle avait frappé, ou le banc ne l'a pas visée",
                BUTOIR_MS);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(texte(robot, "#labelIdentite"))
                .as("le badge nomme l'utilisateur que l'état de départ déclare, et que le serveur a reconnu")
                .contains("Observatrice")
                .doesNotContain("non vérifié");
    }

    private static String texte(FxRobot robot, String selecteur) {
        Node noeud = robot.lookup(selecteur).tryQuery().orElse(null);
        return noeud instanceof Labeled libelle && libelle.getText() != null ? libelle.getText() : "";
    }

    private static List<String> classes(FxRobot robot, String selecteur) {
        Node noeud = robot.lookup(selecteur).tryQuery().orElse(null);
        return noeud == null ? List.of() : List.copyOf(noeud.getStyleClass());
    }
}
