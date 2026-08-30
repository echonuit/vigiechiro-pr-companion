package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.EtapeNavigation;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.recette.FenetreDuBanc;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// #3672 : après un **renommage de carré**, la fiche site continuait d'annoncer l'ancien numéro.
///
/// Le geste est joué par le **chemin réel** - le rappel de succès que `modifierSite()` passe à la
/// modale d'édition - au moyen d'un double de [NavigationSites] qui renomme puis exécute ce rappel,
/// sans ouvrir de fenêtre. Un appel direct au service ne prouverait que le service.
@ExtendWith(ApplicationExtension.class)
class SiteDetailRenommageViewTest {

    // Cette classe ne cite plus `S1-22`, et ce n'est pas un oubli.
    // Elle remplaçait la modale d'édition par un double qui écrivait le nouveau numéro : le clip
    // montrait un clic sur « Modifier » puis un numéro qui change, sans qu'aucune modale ne paraisse
    // (#4174). Le cas est joué par `ScenarioFicheSiteTest`, où la vraie modale s'ouvre.
    //
    // Ses assertions restent : elles gardent le câblage, ce qui est un autre travail que de le montrer.

    private static final String ID_USER = "u-1";
    private static final String CARRE_AVANT = "640380";
    private static final String CARRE_APRES = "640999";

    private Injector injecteur;
    private Site site;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-renommage-carre");
        System.setProperty("vigiechiro.workspace", workspace.toString());

        injecteur =
                Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(new AbstractModule() {
                    /// Double de navigation : il joue ce que fait la modale « Modifier » quand
                    /// l'utilisateur valide - écrire le nouveau numéro, puis prévenir l'appelant -
                    /// sans ouvrir de fenêtre, qui figerait TestFX en headless.
                    @Provides
                    @Singleton
                    NavigationSites navigation(Injector interne, Navigateur navigateur) {
                        return new NavigationSites(interne, navigateur) {
                            @Override
                            public void ouvrirModaleEditionSite(Window parent, Site aModifier, Runnable apresSucces) {
                                interne.getInstance(ServiceSites.class)
                                        .modifierSite(
                                                aModifier.id(),
                                                CARRE_APRES,
                                                aModifier.nomConvivial(),
                                                aModifier.protocole(),
                                                aModifier.commentaire());
                                apresSucces.run();
                            }
                        };
                    }
                }));

        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        site = new SiteDao(source)
                .insert(new Site(null, CARRE_AVANT, "Étang", Protocole.STANDARD, null, "2026-01-01", ID_USER));

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(DiagnosticGuice.pour(injecteur));
        Parent racine = loader.load();
        // `Habillage` via `FenetreDuBanc` : ce cas est FILMÉ (#3773, #4149).
        FenetreDuBanc.poser(stage, racine, 1180, 900);
        injecteur.getInstance(NavigationSites.class).ouvrirDetail(site);
        FenetreDuBanc.afficher(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#3672 : renommer le carré met à jour l'en-tête de la fiche, pas seulement la base")
    void renommer_met_a_jour_l_entete(FxRobot robot) {
        assertThat(numeroAffiche(robot)).isEqualTo(CARRE_AVANT);
        // L'en-tête AVANT : sans elle, on ne peut pas dire que le numéro a changé.
        Respiration.leTempsDeLire(robot);

        robot.clickOn("#boutonModifier");
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);

        // `rafraichir()` rechargeait points, passages et bandeau, mais ne relisait JAMAIS le `Site` :
        // le ViewModel gardait l'enregistrement d'avant, et avec lui l'ancien numéro.
        assertThat(numeroAffiche(robot))
                .as("la fiche affiche le numéro qu'elle vient d'écrire, pas celui d'avant")
                .isEqualTo(CARRE_APRES);
    }

    @Test
    @DisplayName("#3672 : renommer le carré relibelle aussi l'étape de navigation")
    void renommer_relibelle_l_etape(FxRobot robot) {
        assertThat(libelleDeLEtapeCourante()).isEqualTo("Carré " + CARRE_AVANT);

        robot.clickOn("#boutonModifier");

        // Le libellé est un `String` figé dans `EtapeNavigation` : c'est le symptôme que le balayage
        // #3545 avait repéré. Il ne se distingue pas à l'écran, la fiche n'ayant pas
        // d'`EmplacementNavigation` : le fil d'Ariane retombe sur l'historique et lit le MÊME libellé.
        assertThat(libelleDeLEtapeCourante())
                .as("le fil et le bouton Retour cessent d'annoncer un carré qui n'existe plus")
                .isEqualTo("Carré " + CARRE_APRES);
    }

    @Test
    @DisplayName("#3672 : contrôle du dispositif - écrire sans passer par l'écran ne met rien à jour")
    void controle_du_dispositif_ecrire_seul_ne_rafraichit_pas(FxRobot robot) {
        // L'ancien chemin, joué à la main : le service écrit, personne ne prévient l'écran. Sans ce
        // contrôle, les deux tests ci-dessus prouveraient seulement que le double de navigation marche.
        robot.interact(() -> injecteur
                .getInstance(ServiceSites.class)
                .modifierSite(site.id(), CARRE_APRES, site.nomConvivial(), site.protocole(), site.commentaire()));

        assertThat(numeroAffiche(robot))
                .as("rien n'a prévenu l'écran : il montre encore ce qu'il avait lu")
                .isEqualTo(CARRE_AVANT);
        assertThat(libelleDeLEtapeCourante()).isEqualTo("Carré " + CARRE_AVANT);
    }

    private String numeroAffiche(FxRobot robot) {
        return robot.lookup("#valNumeroCarre").queryAs(Label.class).getText();
    }

    private String libelleDeLEtapeCourante() {
        var historique = injecteur.getInstance(Navigateur.class).historique();
        EtapeNavigation sommet = historique.get(historique.size() - 1);
        return sommet.libelle();
    }
}
