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
import javafx.stage.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// La connexion à la **vraie** plateforme, filmée (#4307, corrigé par #4324).
///
/// ## Ce que ce scénario apporte, et qu'un bouchon ne peut pas donner
///
/// `ScenarioPerceptifConnexionTest` filme la même modale contre un `ClientVigieChiro` bouchonné, et
/// c'est juste : il montre que le geste a lieu. Mais l'objet de `S8` est **ce que la plateforme
/// répond**, et un bouchon répond ce que nous croyons qu'elle répond. Ici la latence est réelle, la
/// progression suit un vrai balayage, et l'identité affichée à la fin est celle que `GET /moi` a rendue.
///
/// ## ⚠️ Ce que le premier tir a appris, et qui a réécrit ces deux cas
///
/// Le tournage du run 32692906378 a rendu **deux tests verts et deux clips qui ne montraient pas leur
/// cas**. Trois défauts, tous corrigés ici, et aucun n'était visible autrement qu'en ouvrant les images.
///
/// **Une attente satisfaite avant que l'opération commence.** `S8-06` attendait que la progression
/// **disparaisse** : c'est vrai à `t=0`, avant qu'elle ne paraisse. Le test n'attendait rien. On attend
/// donc son **apparition puis** sa disparition, dans cet ordre.
///
/// **Une assertion que rien ne peut faire rougir.** `S8-06` demandait un libellé d'identité « non
/// vide ». Or `ConnexionViewModel` y pose `« Jeton enregistré, non vérifié »` **dès qu'un jeton est
/// enregistré sans profil**, c'est-à-dire l'état exact que [BancDeRecette#connecteALaPlateforme] crée.
/// L'assertion passait réseau débranché. On asserte donc le **succès**, que le produit distingue par la
/// classe `badge-succes` et par le bandeau « Connexion réussie ».
///
/// **Une caméra qui s'arrête sur le geste.** `S8-05` assertait juste - la progression **paraît** - et
/// le clip s'arrêtait là : sur 35 images, **34 sans modale, la 35e avec**. L'assertion disait « ça a
/// existé », le cas promet « ça se voit ». Chaque cas **tient donc l'écran** après son assertion, par
/// [Respiration], qui ne coûte rien hors séance filmée.
///
/// ## ⚠️ Le jeton ne passe jamais par l'écran
///
/// Le champ du jeton est un `TextField`, pas un `PasswordField` : ce qu'on y colle **se lit**. Ce
/// scénario ne colle rien ; le jeton est déposé sans profil et la modale le revérifie d'elle-même à son
/// ouverture (#1369). Vérifié sur les images du premier tir : le champ est **vide** partout.
///
/// Corollaire assumé : `S8-01`, « coller le jeton », restera hors de portée de ce banc.
///
/// ## ⚠️ Exclu du build par défaut
///
/// `@Tag("recette-connectee")`, exclu par `surefire.excludedGroups`. Sans jeton le banc **refuse** de
/// monter, comme il doit. Le tournage connecté les rappelle en inversant les **deux** propriétés -
/// poser `groups` seul ne lève pas l'exclusion, et le premier tir l'a appris en rendant
/// « Tests run: 0 » sur un build vert.
@Tag("recette-connectee")
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class})
class ScenarioConnecteConnexionTest {

    /// Même cadrage que les scénarios de connexion bouchonnés : la fenêtre reste plus petite que
    /// l'écran du banc, sans quoi la modale atterrit en (0,0) et sa barre de titre sort du champ.
    private static final int LARGEUR = 1100;

    private static final int HAUTEUR = 720;

    private static final String LIBELLE_ENTREE_MENU = "Se connecter à Vigie-Chiro…";

    /// Ce que le produit met sur le badge d'identité **quand la plateforme a répondu**, et lui seul.
    /// L'état initial porte `badge-neutre` : c'est ce qui distingue un succès d'un jeton simplement
    /// enregistré.
    private static final String BADGE_CONNECTE = "badge-succes";

    /// La progression **paraît** vite : c'est le premier aller-retour réseau. Une attente courte suffit,
    /// et une attente longue masquerait un écran qui ne s'ouvre pas.
    private static final int APPARITION_SECONDES = 60;

    /// ⚠️ La **fin**, elle, est bien plus lente que ce qu'un bouchon laissait croire. Mesuré au premier
    /// tir : à **23 secondes**, les rapprocheurs tournaient encore - se connecter rejoue le
    /// rapatriement des nuits du compte (#2557), donc la durée suit la taille du compte.
    ///
    /// Si ce butoir est atteint, la conclusion n'est **pas** « le produit est cassé » mais « le compte
    /// de tournage est plus gros que ce banc ne le prévoit ». Le message le dit.
    private static final int FIN_SECONDES = 240;

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

        // La zone de progression est celle de la modale elle-même (#2642) : elle s'y greffe, plutôt que
        // d'ouvrir une seconde fenêtre par-dessus. Un aperçu rend une scène, pas une pile de fenêtres.
        attendre(
                APPARITION_SECONDES,
                () -> visible(robot, "#zoneProgression"),
                "la progression n'a jamais paru dans la modale");

        // ⚠️ On asserte AVANT de tenir l'écran. L'état est celui de l'instant où la progression paraît,
        // donc déterministe ; le maintien qui suit sert la caméra, pas l'assertion. L'inverse rendrait
        // le cas dépendant de la vitesse du compte.
        assertThat(fenetresOuvertes())
                .as("l'avancement doit paraître DANS la modale : une seconde fenêtre par-dessus"
                        + " montrerait deux fenêtres pour un seul geste, et aucun clip n'en rendrait compte")
                .isLessThanOrEqualTo(2);
        assertThat(grise(robot, "#boutonFermer"))
                .as("« Fermer » reste grisé tant que l'opération tourne : la fermer en cours laisserait"
                        + " un jeton à moitié vérifié et une modale qu'on croit close")
                .isTrue();

        // ⚠️ Et on TIENT L'ÉCRAN. Sans cela le clip s'arrête sur le geste : au premier tir, la modale
        // n'apparaissait que sur la dernière des 35 images. Un cas qui promet « ça se voit » doit
        // laisser la caméra l'enregistrer.
        Respiration.surLeMomentCle(robot);
        Respiration.leTempsDeLire(robot);

        assertThat(visible(robot, "#zoneProgression"))
                .as("la progression doit avoir été à l'écran ASSEZ LONGTEMPS pour être filmée. Si elle a"
                        + " disparu pendant ce maintien, c'est que l'opération est plus rapide que le"
                        + " temps de lecture, et ce cas n'a rien montré")
                .isTrue();
    }

    @Test
    @CasDeRecette(value = "S8-06", portee = Portee.A_L_ECRAN)
    @DisplayName("S8-06 · à la fin, la modale annonce l'identité rendue par la plateforme")
    void la_modale_annonce_l_identite(FxRobot robot) throws TimeoutException {
        Respiration.avantLeGeste(robot);
        ouvrirLaModaleParLeMenu(robot);

        // ⚠️ L'apparition D'ABORD. Attendre la seule disparition rendrait ce cas vert à l'instant zéro,
        // avant même que l'opération ne commence : c'est le défaut du premier tir.
        attendre(
                APPARITION_SECONDES,
                () -> visible(robot, "#zoneProgression"),
                "la progression n'a jamais paru : l'opération n'a pas démarré");
        attendre(
                FIN_SECONDES,
                () -> !visible(robot, "#zoneProgression"),
                "l'opération n'a pas fini dans le temps imparti. À lire comme « le compte de tournage est"
                        + " plus gros que ce banc ne le prévoit », pas comme un défaut du produit :"
                        + " se connecter rejoue le rapatriement des nuits du compte");
        WaitForAsyncUtils.waitForFxEvents();

        // ⚠️ On asserte le SUCCÈS, pas la non-vacuité. `identiteProperty` porte
        // « Jeton enregistré, non vérifié » dès qu'un jeton est déposé sans profil, donc dès le premier
        // instant de ce scénario : un `isNotBlank()` passerait réseau débranché. Ce que le produit
        // réserve au succès, c'est la classe du badge et le texte du bandeau.
        assertThat(classes(robot, "#labelIdentite"))
                .as(
                        "le badge d'identité passe à « %s » quand la plateforme a répondu, et reste"
                                + " « badge-neutre » sinon. C'est le seul signal que l'état initial ne porte pas",
                        BADGE_CONNECTE)
                .contains(BADGE_CONNECTE);
        assertThat(texte(robot, "#labelIdentite"))
                .as("le badge doit nommer QUI est connecté, pas rappeler qu'un jeton attend d'être vérifié")
                .isNotBlank()
                .doesNotContain("non vérifié");
        assertThat(texte(robot, "#bandeauStatut"))
                .as("la case demande l'identité ET le résumé. Le bandeau les annonce ensemble :"
                        + " « Connexion réussie · référentiel à jour : … ». Le CONTENU du résumé dépend du"
                        + " compte, donc seule son annonce est assertée ici")
                .startsWith("Connexion réussie");

        // Le clip doit montrer l'état d'arrivée, pas seulement l'atteindre.
        Respiration.leTempsDeLire(robot);
        Respiration.apresLeGeste(robot);
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

    /// Une attente qui **dit ce qu'elle attendait** quand elle échoue. `WaitForAsyncUtils` rend sinon un
    /// `TimeoutException` nu, et le lecteur d'un tournage raté n'a que la ligne pour comprendre.
    private static void attendre(int secondes, java.util.concurrent.Callable<Boolean> condition, String quoi)
            throws TimeoutException {
        try {
            WaitForAsyncUtils.waitFor(secondes, TimeUnit.SECONDS, condition);
        } catch (TimeoutException expiration) {
            throw new TimeoutException(quoi + " (au bout de " + secondes + " s)");
        }
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

    private static java.util.List<String> classes(FxRobot robot, String selecteur) {
        Node noeud = robot.lookup(selecteur).tryQuery().orElse(null);
        return noeud == null ? java.util.List.of() : java.util.List.copyOf(noeud.getStyleClass());
    }

    private static long fenetresOuvertes() {
        return Window.getWindows().stream().filter(Window::isShowing).count();
    }
}
