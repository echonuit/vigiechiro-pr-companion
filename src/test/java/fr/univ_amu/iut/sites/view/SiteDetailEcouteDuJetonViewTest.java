package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.connexion.viewmodel.RefletDuJeton;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.recette.SansEcritureHorsDuFil;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Hyperlink;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// La fiche d'un carré écoute le jeton sans que le jeton la retienne (#6170).
///
/// La fiche suit la connexion (#4219) par un écouteur posé sur `EtatConnexion`, un singleton qui vit
/// autant que l'application. Inscrit tel quel, cet écouteur tenait la fiche : chaque ouverture laissait
/// derrière elle son contrôleur, son modèle et son graphe de scène, environ 2 Mo, jusqu'à la fermeture.
/// Le banc de l'IHM l'a vu en relevant quatre fiches vivantes après quatre nuits.
///
/// Les deux tests tiennent les deux moitiés du remède : la fiche quittée est rendue au ramasse-miettes,
/// et la fiche ouverte suit encore la connexion après un passage du ramasse-miettes. Le second rougirait
/// si l'écouteur faible n'était tenu par rien : il serait ramassé, et la fiche cesserait d'écouter.
///
/// Aucun geste du robot : TestFX garde dans `FiredEvents` tout événement tiré sur la fenêtre, et un clic
/// retiendrait la fiche à lui seul. L'écran s'ouvre et se quitte par la navigation, comme l'application.
@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})
class SiteDetailEcouteDuJetonViewTest {

    private static final String ID_USER = "u-1";
    private static final String LIBELLE_ACTION = "Publier sur Vigie-Chiro";

    /// JUnit crée ce répertoire et le **supprime** en fin de test (#4888).
    @TempDir
    private Path dossierTemporaire;

    private Injector injecteur;
    private Site site;

    @Start
    void start(Stage stage) throws Exception {
        System.setProperty("vigiechiro.workspace", dossierTemporaire.toString());
        injecteur = Guice.createInjector(RacineInjecteur.modules());
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        long idSite = JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre("640380")
                .nomSite("Étang")
                .point("A1")
                .position(43.52, 5.46)
                .semerSiteEtPoint()
                .idSite();
        // Le carré est relié à la plateforme : sa fiche offre alors « Publier » sur ses points.
        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(
                        LienVigieChiro.ENTITE_SITE, String.valueOf(idSite), "6a4961f587bc8dba39481180", false));
        site = new SiteDao(source).findById(idSite).orElseThrow();

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(DiagnosticGuice.pour(injecteur));
        Parent racine = loader.load();
        FenetreAjustable.poser(stage, racine, 1100, 760);
        FenetreAjustable.afficher(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#6170 : une fiche de carré quittée est rendue au ramasse-miettes")
    void une_fiche_quittee_est_rendue() throws Exception {
        WeakReference<Object> fiche = ouvrirLaFiche();

        // Témoin : tant qu'elle est affichée, le navigateur la tient. Sans cela, une référence faible
        // vide à la fin ne dirait rien de la fiche, seulement que le test ne l'a jamais tenue.
        assertThat(ramasser(fiche))
                .as("la fiche affichée doit rester vivante : sinon le test ne tient rien")
                .isNotNull();

        surLeFil(() -> injecteur.getInstance(NavigationSites.class).ouvrirAccueil());

        assertThat(ramasser(fiche))
                .as("la fiche quittée doit être rendue : un singleton qui la tiendrait la garderait"
                        + " jusqu'à la fermeture de l'application")
                .isNull();
    }

    @Test
    @DisplayName("#6170 : la fiche ouverte suit encore la connexion après un passage du ramasse-miettes")
    void la_fiche_ouverte_suit_la_connexion_apres_un_ramassage(FxRobot robot) throws Exception {
        WeakReference<Object> fiche = ouvrirLaFiche();
        assertThat(lienPublier(robot).isDisable())
                .as("écran de départ : pas de jeton, geste fermé")
                .isTrue();

        // Le ramasse-miettes passe pendant que la fiche est ouverte. Un écouteur faible que rien ne
        // tiendrait tomberait ici, et la fiche cesserait d'écouter sans que rien ne le dise.
        assertThat(ramasser(fiche)).as("la fiche affichée reste vivante").isNotNull();

        injecteur
                .getInstance(StockageConnexion.class)
                .enregistrer("jeton-de-test", new ProfilVigieChiro(ID_USER, "chiro", "observateur"));
        surLeFil(() -> injecteur.getInstance(RefletDuJeton.class).relire());

        assertThat(lienPublier(robot).isDisable())
                .as("le jeton posé, le geste se rouvre : la fiche écoute toujours la connexion")
                .isFalse();
    }

    /// Ouvre la fiche et rend une référence faible sur son contrôleur, pris chez le navigateur. Dans sa
    /// propre méthode, pour qu'aucune variable locale du test ne la tienne.
    private WeakReference<Object> ouvrirLaFiche() throws Exception {
        surLeFil(() -> injecteur.getInstance(NavigationSites.class).ouvrirDetail(site));
        Object controleur = WaitForAsyncUtils.asyncFx(() -> injecteur
                        .getInstance(Navigateur.class)
                        .historique()
                        .getLast()
                        .controleur())
                .get(10, TimeUnit.SECONDS);
        assertThat(controleur).isInstanceOf(SiteDetailController.class);
        return new WeakReference<>(controleur);
    }

    /// Insiste auprès du ramasse-miettes, puis rend ce que la référence tient encore. Un seul
    /// `System.gc()` n'est qu'une demande : un témoin faible sans autre lien doit d'abord tomber, sans
    /// quoi le ramasse-miettes n'a pas tourné et le test ne prouverait rien.
    private static Object ramasser(WeakReference<Object> reference) {
        WeakReference<Object> temoin = new WeakReference<>(new Object());
        for (int essai = 0; essai < 50 && (temoin.get() != null || reference.get() != null); essai++) {
            System.gc();
            WaitForAsyncUtils.waitForFxEvents();
        }
        assertThat(temoin.get())
                .as("le ramasse-miettes n'a pas tourné : ce test ne prouverait rien")
                .isNull();
        return reference.get();
    }

    /// Le lien « Publier » de la carte du point `A1`. Échoue si la carte ne le porte pas : une recherche
    /// qui ne trouve rien ne doit pas passer pour un vert.
    private static Hyperlink lienPublier(FxRobot robot) {
        return robot.lookup(".carte-point").queryAll().stream()
                .map(Node.class::cast)
                .flatMap(carte -> carte.lookupAll(".hyperlink").stream())
                .map(Hyperlink.class::cast)
                .filter(lien -> LIBELLE_ACTION.equals(lien.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune action « " + LIBELLE_ACTION + " » sur la fiche"));
    }

    private static void surLeFil(Runnable geste) throws Exception {
        WaitForAsyncUtils.asyncFx(geste).get(10, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
