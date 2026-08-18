package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.Notificateur;
import fr.univ_amu.iut.commun.view.NotificateurModifiable;
import fr.univ_amu.iut.commun.view.NotificationDialogue;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.Jugement;
import fr.univ_amu.iut.recette.Seance;
import fr.univ_amu.iut.sites.model.ImportSiteDistant;
import fr.univ_amu.iut.sites.model.RapatriementCarre;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.SouhaitDeclaration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le scénario qui **joue** `S1-37`, pour qu'un humain le tranche en regardant (#3914, EPIC #3667).
///
/// ## Ce que le cas demande, et pourquoi aucune assertion ne peut le rendre
///
/// > L'enchaînement « je récupère → la fenêtre se ferme → la fiche s'ouvre » paraît **naturel** : on
/// > comprend où l'on a atterri et pourquoi, sans relire le dialogue deux fois.
///
/// « Paraît naturel » et « on comprend » ne se mesurent pas. Ce que ce test prouve, c'est que
/// l'enchaînement **a eu lieu de bout en bout** ; ce qu'il vaut à l'oeil revient à qui regarde le
/// clip - d'où `jugement = HUMAIN`.
///
/// ## ⚠️ La jonction n'était jouée nulle part
///
/// Les deux moitiés étaient couvertes, jamais leur couture :
///
/// | Test (`S1-34`) | Ce qu'il prouve | Ce qu'il ne voit pas |
/// |---|---|---|
/// | [ModaleSiteVerifierCarreViewTest] | le clic ferme la modale | ce que l'appelant fait du carré |
/// | [NavigationSitesRapatriementTest] | la fiche s'ouvre, le compte rendu suit | la modale : il appelle
/// `ouvrirDetailRapatrie` **directement** |
///
/// Or `S1-37` ne porte ni sur l'une ni sur l'autre : il porte sur le **passage** de la première à la
/// seconde, qui est exactement ce qu'aucun des deux ne traverse.
///
/// ## ⚠️ Ce que le clip montre autrement que la production
///
/// Le compte rendu réel appelle `showAndWait`, qui **fige** TestFX headless : le film s'arrêterait
/// là. Ce scénario construit donc le dialogue **de la production** - même type, même habillage, même
/// texte, par [NotificationDialogue#dialogue] - et l'ouvre en `show()`, non bloquant.
///
/// Ce qui se voit sur le clip est donc juste, à une chose près qui ne se voit pas : la fenêtre ne
/// **bloque** pas. Le dire ici plutôt que de laisser croire que tout est reproduit.
///
/// ## L'exécuteur asynchrone, celui de la production
///
/// Même raison qu'en [fr.univ_amu.iut.connexion.view.ScenarioPerceptifConnexionTest] : en synchrone,
/// la récupération se ferait sur le fil JavaFX, aucune image ne serait rendue pendant ce temps, et le
/// passage à juger n'existerait sur aucune trame.
@ExtendWith(ApplicationExtension.class)
class ScenarioPerceptifRecuperationCarreTest {

    private static final String ID_USER = "u-scenario";
    private static final String CARRE = "640380";
    private static final int POINTS_POSES = 41;

    /// L'écran au repos avant le geste : la référence de qui compare.
    private static final long AVANT_MS = 700;

    /// Le temps de lire l'écran d'arrivée, une fois tout posé.
    private static final long APRES_MS = 1_500;

    /// Entre deux gestes : sans quoi le clic suivant tombe avant que l'oeil ait suivi le précédent.
    private static final long ENTRE_MS = 500;

    /// Ce que « la récupération prend du temps » veut dire : de quoi rendre une dizaine d'images à la
    /// cadence du banc, donc de quoi voir la modale céder la place à la fiche.
    private static final long RAPATRIEMENT_MS = 1_200;

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);

    private Injector injector;
    private Site siteExistant;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-scenario-recuperation");
        System.setProperty("vigiechiro.workspace", workspace.toString());

        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(
                        List.of(new SiteVigieChiro("6a49", "Vigiechiro - Point Fixe-" + CARRE, true))));

        injector =
                Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ExecuteurTache.class)
                                .to(ExecuteurTacheAsynchrone.class)
                                .in(Singleton.class);
                        bind(ClientVigieChiro.class).toInstance(client);
                        bind(NotificateurModifiable.class)
                                .toInstance(new NotificateurModifiable(new CompteRenduVisible()));
                    }

                    // ⚠️ QUALIFIÉ, et le qualifiant est recopié en toutes lettres.
                    // RechercheCarreExistantModule relie Optional<RapatriementCarre> à
                    // @Named("vigiechiro-carre-existant") : un @Provides RapatriementCarre NU est
                    // donc ignoré. Le bouton paraît quand même, le clic part, et c'est le
                    // rapatriement RÉEL qui répond - le premier jet de ce scénario expirait ainsi,
                    // sans rien dire de plus qu'un TimeoutException.
                    //
                    // La constante est private dans son module ; la recopier est le prix à payer, et
                    // le jour où elle changera ce scénario rougira, ce qui est le bon sens de l'échec.
                    @Provides
                    @Singleton
                    @Named("vigiechiro-carre-existant")
                    RapatriementCarre rapatriement(ImportSiteDistant importSiteDistant) {
                        return new RapatriementCarre(client, importSiteDistant) {
                            @Override
                            public Resultat rapatrier(SouhaitDeclaration souhait) {
                                // Hors séance filmée, aucune attente n'est payée : le test reste rapide.
                                if (Seance.filmee()) {
                                    dormir(RAPATRIEMENT_MS);
                                }
                                return new Resultat.Rapatrie(siteExistant, POINTS_POSES);
                            }
                        };
                    }
                }));

        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Observateur"));
        siteExistant = injector.getInstance(ServiceSites.class)
                .creerSite(CARRE, "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        stage.setScene(new Scene(loader.load(), 1100, 720));
        injector.getInstance(NavigationSites.class).ouvrirAccueil();
        stage.show();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @CasDeRecette(value = "S1-37", jugement = Jugement.HUMAIN)
    @DisplayName("S1-37 · récupérer un carré : à regarder, comprend-on où l'on vient d'atterrir ?")
    void la_recuperation_s_enchaine_jusqu_a_la_fiche(FxRobot robot) throws TimeoutException {
        respirer(robot, AVANT_MS);

        robot.interact(
                () -> injector.getInstance(NavigationSites.class).ouvrirModaleCreationSite(robot.window(0), () -> {}));
        WaitForAsyncUtils.waitForFxEvents();
        respirer(robot, ENTRE_MS);

        robot.interact(() -> robot.lookup("#champCarre")
                .queryAs(javafx.scene.control.TextField.class)
                .setText(CARRE));
        robot.clickOn("#btnVerifierCarre");
        // L'exécuteur est asynchrone : le verdict n'est PAS là au retour du clic (ADR 3668).
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> robot.lookup("#btnRecupererCarre").tryQuery().isPresent());
        respirer(robot, ENTRE_MS);

        robot.clickOn("#btnRecupererCarre");

        // C'est ici que se joue le cas : la modale s'efface et la fiche paraît. Attendre la FICHE, et
        // non la fermeture, garantit que le film porte les deux moments - donc leur passage.
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> robot.lookup("#boutonImporterNuit").tryQuery().isPresent());
        respirer(robot, APRES_MS);

        assertThat(robot.lookup("#boutonImporterNuit").tryQuery())
                .as("l'enchaînement est-il seulement allé jusqu'au bout ? Sans cette question, un"
                        + " scénario qui n'aurait rien déclenché rendrait un clip immobile que"
                        + " personne ne signalerait.")
                .isPresent();
    }

    // ----------------------------------------------------------------------------------------

    /// Le compte rendu **de la production**, ouvert sans bloquer.
    ///
    /// ⚠️ `NotificationDialogue` fait `showAndWait`, qui fige TestFX headless. Le dialogue est donc
    /// construit par sa propre fabrique - même type, même habillage, même texte - puis ouvert en
    /// `show()`. Ce qui se voit est juste ; ce qui ne se voit pas, c'est qu'il ne bloque pas.
    private static final class CompteRenduVisible implements Notificateur {

        private final NotificationDialogue reel = new NotificationDialogue();

        @Override
        public void notifier(NiveauNotification niveau, String entete, String message) {
            Alert alerte = reel.dialogue(niveau, entete, message);
            alerte.show();
        }

        @Override
        public void notifier(NiveauNotification niveau, String entete, CompteRenduChiffre compteRendu) {
            notifier(niveau, entete, compteRendu.toString());
        }
    }

    /// Ne s'arrête que si l'on filme : hors séance filmée, ces respirations n'allongeraient le build
    /// que pour personne.
    private static void respirer(FxRobot robot, long millis) {
        if (Seance.filmee()) {
            robot.sleep(millis);
        }
    }

    private static void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
        }
    }
}
