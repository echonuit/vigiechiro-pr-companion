package fr.univ_amu.iut.connexion.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.film.EnregistreurDeFilm;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// La connexion à la **vraie** plateforme, filmée (#4307, lot 5 du chantier #4291).
///
/// ## Ce que ce scénario apporte, et qu'un bouchon ne peut pas donner
///
/// `ScenarioPerceptifConnexionTest` filme la même modale contre un `ClientVigieChiro` bouchonné, et
/// c'est juste : il montre que le geste a lieu. Mais l'objet de `S8` est **ce que la plateforme
/// répond**, et un bouchon répond ce que nous croyons qu'elle répond. Le clip y serait convaincant et
/// **creux** - muet sur son propre objet (ADR 4142).
///
/// Ici la latence est réelle, la progression suit un vrai balayage, et l'identité affichée à la fin est
/// celle que `GET /moi` a rendue.
///
/// ## Pourquoi ces deux cas-là, et pas les six de l'étape 1
///
/// `S8-02` demande que le libellé nomme la nuit en cours (« Nuits k / N »), `S8-03` qu'une estimation
/// paraisse. Les deux **supposent que le compte porte des nuits** : sur un compte qui n'en a pas, il n'y
/// a rien à voir, et le cas rougirait pour une raison qui n'est pas le produit.
///
/// `S8-05` et `S8-06` tiennent quel que soit le contenu du compte, puisque `GET /moi` répond toujours.
/// Ce sont donc les deux par lesquels commencer. Les autres viendront quand on saura, d'un premier tir,
/// ce que le compte de tournage contient.
///
/// ## ⚠️ Le jeton ne passe jamais par l'écran
///
/// Le champ du jeton est un `TextField`, pas un `PasswordField` : ce qu'on y colle **se lit**, et le
/// banc photographie le graphe de scène. Ce scénario ne colle donc rien. `connecteALaPlateforme()`
/// dépose le jeton dans `StockageConnexion` **sans profil**, et la modale le revérifie d'elle-même à son
/// ouverture (#1369). C'est le seul chemin qui filme la connexion réelle sans la graver dans un clip
/// destiné à être publié.
///
/// Corollaire assumé : `S8-01`, « coller le jeton », restera hors de portée de ce banc.
///
/// ## ⚠️ Exclu du build par défaut
///
/// `@Tag("recette-connectee")`, exclu par `surefire.excludedGroups`. Sans jeton le banc **refuse** de
/// monter, comme il doit : laisser ces scénarios dans la suite ordinaire la ferait rougir sur chaque PR.
/// Le tournage connecté les rappelle par `-Dsurefire.groups=recette-connectee`.
@Tag("recette-connectee")
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class})
class ScenarioConnecteConnexionTest {

    /// Même cadrage que les scénarios de connexion bouchonnés : la fenêtre reste plus petite que
    /// l'écran du banc, sans quoi la modale atterrit en (0,0) et sa barre de titre sort du champ.
    private static final int LARGEUR = 1100;

    private static final int HAUTEUR = 720;

    private static final String LIBELLE_ENTREE_MENU = "Se connecter à Vigie-Chiro…";

    /// La vérification traverse le réseau puis rejoue les rapprocheurs : plus long qu'un bouchon, et
    /// c'est le sujet. Large, parce qu'un compte fourni prend du temps et qu'un butoir trop court
    /// rougirait sur la taille du compte plutôt que sur le produit.
    private static final int ATTENTE_SECONDES = 120;

    @Start
    void start(Stage stage) throws IOException {
        BancDeRecette.surLeChrome()
                .taille(LARGEUR, HAUTEUR)
                // ASYNCHRONE : la progression est le sujet, et en synchrone le fil JavaFX est bloqué,
                // donc aucune image n'est rendue pendant l'opération.
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                // ⚠️ Aucun `ClientVigieChiro` remplacé, et c'est tout l'intérêt : la frontière réseau
                // reste celle de la production.
                .connecteALaPlateforme()
                .remplacer(new AbstractModule() {
                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        // Rien à ouvrir sur la machine qui filme, et rien à voir sur le clip.
                        return lien -> {};
                    }
                })
                .montrer(stage);
    }

    @Test
    @CasDeRecette(value = "S8-05", portee = Portee.A_L_ECRAN)
    @DisplayName("S8-05 · l'avancement paraît dans la modale, sans seconde fenêtre, et « Fermer » y est grisé")
    void l_avancement_parait_dans_la_modale(FxRobot robot) throws TimeoutException {
        Respiration.avantLeGeste(robot);
        ouvrirLaModaleParLeMenu(robot);

        // La zone de progression est celle de la modale elle-même (#2642) : elle s'y greffe, plutôt
        // que d'ouvrir une seconde fenêtre par-dessus. Un aperçu rend une scène, pas une pile de
        // fenêtres, et deux fenêtres pour un seul geste ne se jugeraient sur aucune image.
        WaitForAsyncUtils.waitFor(ATTENTE_SECONDES, TimeUnit.SECONDS, () -> visible(robot, "#zoneProgression"));

        assertThat(fenetresOuvertes())
                .as("l'avancement doit paraître DANS la modale : une seconde fenêtre par-dessus"
                        + " montrerait deux fenêtres pour un seul geste, et aucun clip n'en rendrait compte")
                .isLessThanOrEqualTo(2);
        assertThat(grise(robot, "#boutonFermer"))
                .as("« Fermer » reste grisé tant que l'opération tourne : la fermer en cours laisserait"
                        + " un jeton à moitié vérifié et une modale qu'on croit close")
                .isTrue();
    }

    @Test
    @CasDeRecette(value = "S8-06", portee = Portee.A_L_ECRAN)
    @DisplayName("S8-06 · à la fin, la modale annonce l'identité rendue par la plateforme")
    void la_modale_annonce_l_identite(FxRobot robot) throws TimeoutException {
        Respiration.avantLeGeste(robot);
        ouvrirLaModaleParLeMenu(robot);

        // ⚠️ On attend que la progression RETOMBE, et non qu'un libellé paraisse : un texte cherché
        // trop tôt se trouve parfois avant que l'opération soit finie, et le cas serait vert sur un
        // écran à moitié construit.
        WaitForAsyncUtils.waitFor(ATTENTE_SECONDES, TimeUnit.SECONDS, () -> !visible(robot, "#zoneProgression"));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(texte(robot, "#labelIdentite"))
                .as("la plateforme a répondu : la modale doit nommer QUI est connecté. Un libellé vide"
                        + " après une vérification réussie voudrait dire que l'identité n'est pas"
                        + " redescendue jusqu'à l'écran")
                .isNotBlank();
    }

    // --------------------------------------------------------------------------------------------

    /// Ouvre la modale **par le menu**, et non dans le `@Start` : les repères de la séance filmée sont
    /// posés autour du test, si bien qu'une ouverture antérieure n'apparaîtrait sur aucune image.
    private void ouvrirLaModaleParLeMenu(FxRobot robot) throws TimeoutException {
        // `GesteVisible.choisir` plutôt qu'un `clickOn` nu : le banc rend le graphe de scène, où le
        // pointeur n'existe pas. C'est lui qui dessine le halo de l'appui, sans quoi la modale
        // s'ouvrirait sans qu'on voie ce qui l'a ouverte (ADR 4248).
        GesteVisible.choisir(robot, "#menuOutils", LIBELLE_ENTREE_MENU);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static boolean visible(FxRobot robot, String selecteur) {
        Node noeud = robot.lookup(selecteur).tryQuery().orElse(null);
        return noeud != null && noeud.isVisible();
    }

    private static boolean grise(FxRobot robot, String selecteur) {
        Node noeud = robot.lookup(selecteur).tryQuery().orElse(null);
        return noeud instanceof Button bouton && bouton.isDisabled();
    }

    private static String texte(FxRobot robot, String selecteur) {
        Node noeud = robot.lookup(selecteur).tryQuery().orElse(null);
        return noeud instanceof Labeled libelle && libelle.getText() != null ? libelle.getText() : "";
    }

    private static long fenetresOuvertes() {
        return javafx.stage.Window.getWindows().stream()
                .filter(javafx.stage.Window::isShowing)
                .count();
    }
}
