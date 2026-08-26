package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.sites.model.RapatriementCarre;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// La **couture** entre la modale de déclaration et l'écran qui l'a ouverte, quand un carré vient
/// d'être récupéré (#3806), et l'écran sur lequel ce geste **se conclut** (#4099).
///
/// ## Ce que ce test existe pour empêcher
///
/// [ModaleSiteVerifierCarreViewTest] s'arrête une étape plus tôt : il vérifie que l'appelant **reçoit**
/// le carré rapatrié. Ce que l'appelant en fait n'était exercé nulle part. Mesuré à la clôture des
/// suites de #3458 : le compte rendu retiré, **286 tests restaient verts**. On pouvait donc supprimer
/// la seule phrase qui explique à l'utilisateur d'où sortent les points apparus sur son écran.
///
/// ## Trois brins, et pourquoi il en faut trois
///
/// La modale s'ouvre depuis « Mes sites ». Le geste s'y conclut donc, liste rafraîchie et compte rendu
/// au bandeau de cet écran (ADR 0023) - alors qu'il ouvrait la fiche du carré jusqu'à #4099.
///
/// Chacun des trois brins doit pouvoir rougir **seul**, parce que chacun garde une moitié différente
/// du besoin :
///
/// 1. rester sur l'écran. Sans lui, on retomberait dans la navigation d'avant ;
/// 2. voir le carré dans la liste. Ce brin ne garde **pas** un appel à `rafraichir()` : l'écran
///    déclare [fr.univ_amu.iut.commun.view.SuitLaRevision], donc le socle le recharge de lui-même sur
///    l'`insert`. Il garde que cet écran suit bien la révision - retirer le contrat le ferait rougir.
///    C'est une mesure qui a corrigé une intention : la première version appelait `rafraichir()`, et le
///    test est resté vert quand on l'a retiré ;
/// 3. lire le compte rendu. Une liste rafraîchie **en silence** ferait paraître des points sans dire
///    d'où ils viennent : c'est exactement le défaut que #3806 avait corrigé, et qu'un test des deux
///    seuls premiers brins laisserait revenir au vert.
///
/// ## Le nombre de points est compté, pas posé
///
/// Le carré reçoit de vrais points, et le résultat annonce **ce que la base contient**. Un scénario qui
/// promet quarante et un points sur un site qui n'en a aucun rend un écran impossible - le clip de
/// recette S1-37 le montrait, bandeau et liste se contredisant à l'image.
///
/// ## Pourquoi la composition complète
///
/// L'écran est monté pour de vrai, et le test appelle la **méthode que la modale reçoit**, prise sur le
/// contrôleur en place. Rejouer ses gestes à côté ferait dériver le test de la production sans que rien
/// ne rougisse.
@ExtendWith(ApplicationExtension.class)
class NavigationSitesRapatriementTest {

    // Cette classe ne cite plus `S1-34`, et ce n'est pas un oubli. Elle APPELLE `rapatrierLeCarre()`
    // puis annonce le résultat : aucun bouton n'était cliqué à l'image, et la revue l'a vu - « pas clair
    // que l'on a cliqué sur le bouton de récupération » (#4181).
    //
    // Le cas est joué par `ScenarioModaleCarreTest`, qui part de « Mes sites », ouvre la déclaration par
    // son bouton, vérifie le carré, clique « Récupérer ce carré », et montre l'écran d'arrivée
    // ([ADR 4188]).
    //
    // Ses assertions restent : elles gardent les trois brins du rafraîchissement - on ne quitte pas
    // l'écran, la liste suit, le bandeau explique - ce qui est un autre travail que de le montrer.

    private static final String ID_USER = "u-test";
    private static final String CARRE = "640380";

    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-nav-rapatriement");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector =
                Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        // Exécuteur synchrone (#1212) : même raison que les autres tests de vue de la feature.
                        bind(ExecuteurTache.class)
                                .to(ExecuteurTacheSynchrone.class)
                                .in(Singleton.class);
                    }
                }));
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        stage.setScene(Habillage.scene(loader.load(), 1100, 720));
        injector.getInstance(NavigationSites.class).ouvrirAccueil();
        stage.show();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#4099 : récupérer un carré rafraîchit « Mes sites » et y rend compte, sans quitter l'écran")
    void le_rapatriement_rafraichit_mes_sites_et_y_rend_compte(FxRobot robot) {
        assertThat(titresDesCartes(robot))
                .as("aucun carré déclaré au départ : c'est ce qui rend le rafraîchissement observable")
                .isEmpty();

        // La liste VIDE, avant le rapatriement : c'est la référence de qui compare. Sans cet arrêt, le
        // clip montre une liste peuplée et rien ne dit qu'elle ne l'était pas (#4149).
        Respiration.avantLeGeste(robot);

        AtomicReference<RapatriementCarre.Resultat.Rapatrie> resultat = new AtomicReference<>();
        robot.interact(() -> {
            resultat.set(rapatrierLeCarre());
            ecranCourant().annoncerRapatriement(resultat.get());
        });
        WaitForAsyncUtils.waitForFxEvents();
        // Les trois brins du cas sont à l'image en même temps : la liste qui s'est peuplée, le fil resté
        // sur « Mes sites », et le bandeau qui explique d'où vient le carré.
        Respiration.surLeMomentCle(robot);
        RapatriementCarre.Resultat.Rapatrie rapatrie = resultat.get();

        // Premier brin : on ne quitte pas l'écran d'où la modale a été ouverte.
        assertThat(robot.lookup("#boutonImporterNuit").tryQuery())
                .as("la fiche du carré ne s'ouvre pas")
                .isEmpty();
        assertThat(segmentsDuFil(robot))
                .as("le fil d'Ariane reste sur « Mes sites »")
                .endsWith("Mes sites");

        // Deuxième brin : la liste montre ce qui vient d'être récupéré.
        assertThat(titresDesCartes(robot))
                .as("l'écran suit la révision : le carré récupéré paraît dans la liste")
                .anyMatch(titre -> titre.contains(CARRE));

        // Troisième brin : sans compte rendu, ces points paraîtraient sans explication.
        HBox bandeau = robot.lookup("#bandeauRetour").queryAs(HBox.class);
        assertThat(bandeau.isVisible())
                .as("l'écran montre son bandeau de retour")
                .isTrue();
        assertThat(robot.lookup("#lblRetour").queryAs(Label.class).getText())
                .as("le compte rendu nomme le carré et compte ses points")
                .contains(CARRE)
                .contains(String.valueOf(rapatrie.points()));
        assertThat(bandeau.getStyleClass())
                .as("un carré récupéré est une bonne nouvelle, pas un avertissement")
                .contains("retour-succes");
    }

    /// Crée le carré et ses points, puis rend le résultat que la modale aurait produit - avec le nombre
    /// de points **compté sur la base**, et non posé en dur.
    private RapatriementCarre.Resultat.Rapatrie rapatrierLeCarre() {
        ServiceSites service = injector.getInstance(ServiceSites.class);
        Site carre = service.creerSite(CARRE, "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(carre.id(), "A1", 43.42, 5.11, "Près du grand chêne");
        service.ajouterPoint(carre.id(), "B2", 43.43, 5.12, "Lisière de roselière");
        return new RapatriementCarre.Resultat.Rapatrie(
                carre, service.listerPoints(carre.id()).size());
    }

    /// Le contrôleur de l'écran **en place**, celui-là même dont la modale reçoit la méthode.
    private MesSitesController ecranCourant() {
        Object controleur =
                injector.getInstance(Navigateur.class).historique().getLast().controleur();
        return (MesSitesController) controleur;
    }

    private static List<String> titresDesCartes(FxRobot robot) {
        return robot.lookup(".carte-titre").queryAllAs(Label.class).stream()
                .map(Label::getText)
                .toList();
    }

    /// Les libellés du fil, dans l'ordre, sur le modèle de `MainViewTest#fil_ariane_reflete_le_parcours`.
    private static List<String> segmentsDuFil(FxRobot robot) {
        HBox fil = robot.lookup("#filAriane").queryAs(HBox.class);
        return fil.getChildren().stream()
                .filter(noeud -> noeud.getStyleClass().contains("fil-ariane-segment")
                        || noeud.getStyleClass().contains("fil-ariane-courant"))
                .map(noeud -> ((Labeled) noeud).getText())
                .toList();
    }
}
