package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.animation.AnimationTimer;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// **S1-17**, le voile d'occupation, filmé sur une base qu'un coordinateur peut avoir (#4172).
///
/// ## Pourquoi cette classe existe
///
/// La revue disait « je ne comprends pas ce que je dois voir », et j'ai répondu que le voile ne pouvait
/// **pas** se filmer : il n'enveloppe qu'un `viewModel::charger`, une lecture en base, et la seule
/// opération longue de l'écran a quitté le voile pour un dialogue de progression (#2558).
///
/// ⚠️ **C'était mesuré sur une fixture de deux sites, et faux du produit.** `charger()` recompose une
/// carte par site, chacune avec ses lectures : sur **cent cinquante carrés**, il prend **un demi-seconde**
/// - de quoi rendre cinq images à la cadence du banc. Le voile n'est pas invisible ; il est invisible
/// sur une base de démonstration.
///
/// Cent cinquante carrés n'est pas un chiffre inventé pour faire durer : c'est ce que suit un
/// coordinateur départemental. Le clip montre donc le produit d'un utilisateur chargé, pas une lenteur
/// fabriquée pour la caméra.
@ExtendWith(ApplicationExtension.class)
class ScenarioVoileChargementTest {

    private static final String ID_USER = "u-coordinateur";

    /// Ce que suit un coordinateur départemental. Mesuré : `charger()` y prend ~500 ms.
    private static final int CARRES = 150;

    private Injector injector;

    /// Le voile a-t-il été VU, pendant que l'écran chargeait ? Posé depuis le fil JavaFX.
    private final AtomicBoolean voileVu = new AtomicBoolean();

    @Start
    void start(Stage stage) throws IOException {
        injector = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                // ⚠️ ASYNCHRONE, et c'est tout l'objet : en synchrone le fil JavaFX porte le chargement,
                // aucune image n'est rendue pendant ce temps, et le voile n'existerait sur aucune trame.
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .semer(this::semerUneSaisonEntiere)
                // ⚠️ On reste sur l'ACCUEIL. Ouvrir « Mes sites » ici ferait tomber le chargement
                // pendant `@Start`, c'est-à-dire AVANT que la caméra ne tourne : le clip publié montrait
                // un écran déjà chargé, et l'assertion restait verte parce que le voile avait bel et
                // bien paru - hors champ. C'est le vert qui ne prouve rien, vu en ouvrant l'image.
                .montrer(stage);

        // ⚠️ Le guet se pose ICI, et pas dans le corps du cas. Le chargement démarre pendant
        // l'`initialize()` du contrôleur, c'est-à-dire pendant que le banc monte la fenêtre : quand le
        // premier `robot.interact` s'exécute, le voile a souvent déjà cédé. Guetter après coup mesurait
        // l'inverse de ce qu'on croyait mesurer.
        surveillerLeVoile(stage);
    }

    /// Guette le voile **à chaque battement de JavaFX**, comme la caméra échantillonne ses images.
    ///
    /// ⚠️ Trois formes ont échoué avant celle-ci, et chacune pour la même raison : elles regardaient au
    /// mauvais moment. Un écouteur posé sur les voiles existants ne voit pas celui qui NAÎT avec l'écran
    /// « Mes sites » ; un guet posé dans le corps du cas arrive après que le voile a cédé ; un filtre
    /// d'événements ne se déclenche pas pendant qu'un fil d'arrière-plan travaille.
    ///
    /// Un `AnimationTimer` bat sur le fil JavaFX à la cadence du rendu : s'il ne voit pas le voile,
    /// c'est qu'aucune image rendue ne le portait - donc que le clip ne le montre pas non plus. C'est
    /// exactement la question que ce cas doit trancher.
    private void surveillerLeVoile(Stage fenetre) {
        Scene scene = fenetre.getScene();
        new AnimationTimer() {
            @Override
            public void handle(long maintenant) {
                scene.getRoot().lookupAll(".occupation-voile").stream()
                        .filter(Node::isVisible)
                        .findAny()
                        .ifPresent(voile -> voileVu.set(true));
            }
        }.start();
    }

    private void semerUneSaisonEntiere(Injector inj) {
        new UtilisateurDao(inj.getInstance(SourceDeDonnees.class)).insert(new Utilisateur(ID_USER, "Coordinateur"));
        ServiceSites service = inj.getInstance(ServiceSites.class);
        for (int i = 0; i < CARRES; i++) {
            Site site = service.creerSite(
                    String.format("%06d", 600000 + i), "Carré " + i, Protocole.STANDARD, null, ID_USER);
            service.ajouterPoint(site.id(), "A1", 43.5 + i * 0.001, 5.4, "Lisière");
            service.ajouterPoint(site.id(), "B2", 43.6 + i * 0.001, 5.5, "Roselière");
        }
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @CasDeRecette(value = "S1-17", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-17 · le voile paraît pendant le chargement, et cède la place aux cartes")
    void le_voile_parait_puis_cede_la_place(FxRobot robot) throws TimeoutException {
        Respiration.avantLeGeste(robot);

        // ⚠️ Le guet ne compte QUE ce qui se passe pendant le film : la fenêtre a pu montrer un voile
        // en se montant, hors champ, et ce voile-là ne prouve rien de ce que le clip donne à voir.
        voileVu.set(false);

        // C'est ce clic qui déclenche le chargement, et le clip le montre : sans lui, l'écran
        // paraîtrait chargé sans qu'on sache d'où il vient.
        GesteVisible.cliquer(robot, "Mes sites");

        WaitForAsyncUtils.waitFor(
                20,
                TimeUnit.SECONDS,
                () -> !robot.lookup(".carte-site").queryAll().isEmpty());
        Respiration.surLeMomentCle(robot);

        assertThat(voileVu.get())
                .as("le voile a-t-il paru PENDANT le film ? Sans cette question, un clip qui montrerait"
                        + " un écran déjà chargé passerait pour la preuve que le voile fonctionne")
                .isTrue();

        Node voile = robot.lookup(".occupation-voile").query();
        assertThat(voile.isVisible())
                .as("et il cède la place : un voile resté en place bloquerait tout l'écran")
                .isFalse();
        assertThat(robot.lookup(".carte-site").queryAll())
                .as("les cartes sont là, lisibles : c'est ce que le voile attendait")
                .isNotEmpty();
        Respiration.leTempsDeLire(robot);
    }
}
