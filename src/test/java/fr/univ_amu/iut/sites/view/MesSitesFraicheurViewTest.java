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
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// **Mes sites suit la donnée, pas la navigation** (#3644).
///
/// ## Ce que ce test tient
///
/// `Navigateur.revenirAIndex` restaure le **nœud mémorisé** : il ne recharge pas le FXML. Un écran
/// empilé qui ne charge sa donnée qu'au montage montre donc, au retour, l'état d'avant. `CarteSite`
/// porte trois compteurs et le site lui-même : tout y est périssable.
///
/// ## Pourquoi renommer, et pas ajouter un point
///
/// Ajouter un point **annonce** une mutation structurelle, donc `SuitLaRevision` suffirait à
/// rafraîchir l'écran et ce test passerait **sans rien dire du retour**. C'est le piège que
/// l'[ADR 3840](../../../../../../../dev-docs/decisions/3840-le-signal-et-le-retour-se-partagent-la-fraicheur.md)
/// a nommé la veille : un fait tenu par un autre dispositif que celui qu'on croit.
///
/// `ServiceSites.modifierSite` fait un `update` et **n'annonce rien** - aucun des quatre comptes de
/// l'accueil ne bouge. Seule la relecture **au retour** peut donc rattraper le nouveau nom, et c'est
/// exactement ce que ce test vérifie.
@ExtendWith(ApplicationExtension.class)
class MesSitesFraicheurViewTest {

    private static final String ID_USER = "u-fraicheur";
    private static final String CARRE = "640380";
    private static final String NOM_AVANT = "Étang de la Tuilière";
    private static final String NOM_APRES = "Étang renommé pendant l'absence";

    private Injector injector;
    private Site site;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-sites-fraicheur");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        // Exécuteur synchrone : le chargement des cartes passe par l'occupation, et une assertion posée
        // contre un chargement de fond ne prouverait rien.
        injector =
                Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ExecuteurTache.class)
                                .to(ExecuteurTacheSynchrone.class)
                                .in(Singleton.class);
                    }
                }));
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        site = injector.getInstance(ServiceSites.class).creerSite(CARRE, NOM_AVANT, Protocole.STANDARD, null, ID_USER);

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        FenetreAjustable.poser(stage, racine, 1280, 860);
        FenetreAjustable.afficher(stage);
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#3644 : un site renommé pendant l'absence est relu au retour sur Mes sites")
    void un_site_renomme_est_relu_au_retour(FxRobot robot) {
        NavigationSites navigation = injector.getInstance(NavigationSites.class);
        Navigateur navigateur = injector.getInstance(Navigateur.class);

        robot.interact(navigation::ouvrirAccueil);
        assertThat(nomsAffiches(robot)).as("l'inventaire de départ").contains(NOM_AVANT);

        // Ouvrir la fiche EMPILE : c'est ce qui rend un retour possible, donc ce qui rend l'affirmation
        // de ce test vérifiable.
        robot.interact(() -> navigation.ouvrirDetail(site));

        // Le geste qui n'annonce rien : un `update` sur le site, aucun compte de l'accueil ne bouge.
        robot.interact(() -> injector.getInstance(ServiceSites.class)
                .modifierSite(site.id(), CARRE, NOM_APRES, Protocole.STANDARD, null));

        robot.interact(navigateur::revenir);

        assertThat(nomsAffiches(robot))
                .as("l'écran quitté doit avoir relu sa donnée au retour ; sinon il affiche le nom d'avant")
                .contains(NOM_APRES)
                .doesNotContain(NOM_AVANT);
    }

    /// Les libellés rendus par l'écran, tels qu'ils s'affichent : on lit la vue, pas le ViewModel.
    private static java.util.List<String> nomsAffiches(FxRobot robot) {
        return robot.lookup(".carte-site").queryAll().stream()
                .flatMap(carte -> carte.lookupAll(".label").stream())
                .filter(Labeled.class::isInstance)
                .map(noeud -> ((Labeled) noeud).getText())
                .filter(texte -> texte != null && !texte.isBlank())
                .toList();
    }
}
