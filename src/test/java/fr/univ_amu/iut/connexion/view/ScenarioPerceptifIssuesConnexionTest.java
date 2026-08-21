package fr.univ_amu.iut.connexion.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.connexion.viewmodel.ConnexionViewModel;
import fr.univ_amu.iut.recette.CadreVisible;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.FenetreDuBanc;
import fr.univ_amu.iut.recette.Respiration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Les **quatre issues** de la connexion, jouées sur le vrai écran (`S1-05` à `S1-08`, #4130).
///
/// ## Ce que ces cas demandent, et ce qu'ils n'avaient pas
///
/// Les quatre étaient portés par `ConnexionViewModelTest`, qui n'ouvre aucune fenêtre : leur clip
/// était un rectangle noir, et la page de recette l'annonçait pourtant avec un lecteur. Or ces quatre
/// cas sont **des messages qu'on lit sur un écran** - c'est leur formulation qu'on juge, pas la valeur
/// que rend une méthode.
///
/// > Les cas de recette doivent être des E2E qui montrent les fonctionnalités du produit et comment on
/// > y accède.
///
/// ## Le chemin d'accès fait partie du cas
///
/// La modale s'ouvre par l'**entrée du menu ☰**, comme un observateur le ferait, et non par un appel
/// de navigation. C'est le retour de `S1-26` : « on ne comprend pas comment on arrive sur la modale ».
///
/// ## Une seule frontière truquée
///
/// [ClientVigieChiro] est remplacé, et lui seul : le reste du câblage est celui du produit. Chaque cas
/// pose sa réponse avant le geste, si bien que ce qui paraît à l'écran est ce que l'application fait
/// d'une réponse réelle.
///
/// ## Pourquoi ces cas restent ASSERTÉS
///
/// Le script de session ne les dit pas perceptifs, et il a raison : leur texte s'assertit mot pour
/// mot. Ce qui se regarde ici, c'est la **lisibilité** - qu'on comprenne le message et le geste qu'il
/// nomme - et c'est le clip qui la porte. Les deux ne s'excluent pas : l'assertion garde le texte, le
/// clip montre ce qu'on en fait.
///
/// ⚠️ L'exécuteur est **asynchrone**, celui de la production : en synchrone la vérification bloque le
/// fil JavaFX, aucune image n'est rendue pendant ce temps, et le passage à juger n'existerait sur
/// aucune trame.
///
/// ## Ce que chaque cas vérifie en plus de son texte
///
/// Que le message est **dans le cadre** ([CadreVisible]). Un clip qui ne montre pas son objet ne fait
/// rien juger, et `lookup` ne le dit pas : c'est le défaut qui a échappé deux fois à `S4-33`.
@ExtendWith(ApplicationExtension.class)
class ScenarioPerceptifIssuesConnexionTest {

    private static final int LARGEUR = 1100;
    private static final int HAUTEUR = 720;

    /// L'entrée du menu ☰ qui ouvre la modale, telle que le produit la nomme quand aucun profil n'est
    /// enregistré.
    private static final String LIBELLE_ENTREE_MENU = "Se connecter à Vigie-Chiro…";

    private static final String JETON = "TOK-DE-RECETTE";

    private static final ProfilVigieChiro PROFIL = new ProfilVigieChiro("u-scenario", "chiro", "observateur");

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);
    private final RapprochementVigieChiro rapprocheur = mock(RapprochementVigieChiro.class);

    private Injector injector;

    @Start
    void start(Stage stage) throws IOException {
        Path workspace = Files.createTempDirectory("vc-issues-connexion");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        StockageConnexion stockage = new StockageConnexion(new Workspace(workspace), Horloge.systeme());

        injector =
                Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ExecuteurTache.class)
                                .to(ExecuteurTacheAsynchrone.class)
                                .in(Singleton.class);
                    }

                    @Provides
                    ConnexionViewModel viewModel() {
                        return new ConnexionViewModel(stockage, client, Set.of(rapprocheur), Optional.empty());
                    }

                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        return lien -> {
                            // Aucun navigateur ne s'ouvre : rien à voir sur le film, rien à lancer sur la
                            // machine qui filme.
                        };
                    }
                }));
        new MigrationSchema(injector.getInstance(SourceDeDonnees.class)).migrer();

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        FenetreDuBanc.poser(stage, racine, LARGEUR, HAUTEUR);
        FenetreDuBanc.afficher(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @CasDeRecette("S1-05")
    @DisplayName("S1-05 · jeton vide : le message dit quoi faire, sans accuser le jeton")
    void jeton_vide_dit_quoi_faire(FxRobot robot) throws TimeoutException {
        // Aucun appel réseau n'est attendu : le produit refuse avant de partir.
        jouerLaConnexion(robot, "");

        assertThat(statut(robot).getText())
                .as("le message demande le geste manquant, il ne parle pas d'un jeton refusé")
                .contains("Collez d'abord votre token Vigie-Chiro.");
        montreLeMessage(robot);
    }

    @Test
    @CasDeRecette("S1-06")
    @DisplayName("S1-06 · jeton refusé : le message nomme la cause et le geste qui répare")
    void jeton_refuse_nomme_le_geste(FxRobot robot) throws TimeoutException {
        when(client.moi()).thenReturn(ReponseApi.refuse(401, "token invalide"));

        jouerLaConnexion(robot, JETON);

        assertThat(statut(robot).getText())
                .as("un 401 se dit en clair, et le geste qui répare est nommé")
                .contains("Token invalide ou expiré")
                .contains("recollez-en un depuis le site Vigie-Chiro");
        montreLeMessage(robot);
    }

    @Test
    @CasDeRecette("S1-07")
    @DisplayName("S1-07 · plateforme injoignable : le message ne fait pas accuser le jeton")
    void injoignable_ne_fait_pas_accuser_le_jeton(FxRobot robot) throws TimeoutException {
        when(client.moi()).thenReturn(ReponseApi.injoignable("délai d'attente dépassé"));

        jouerLaConnexion(robot, JETON);

        // ⚠️ La moitié du cas est là : avant #1284, une panne réseau s'affichait « Token invalide ou
        // expiré », ce qui poussait l'observateur à jeter un jeton parfaitement valide.
        assertThat(statut(robot).getText())
                .as("la cause est la plateforme, et le jeton est explicitement mis hors de cause")
                .contains("Vigie-Chiro est injoignable")
                .contains("le jeton n'est peut-être pas en cause");
        assertThat(statut(robot).getText())
                .as("et surtout, il ne dit pas que le jeton est invalide")
                .doesNotContain("Token invalide");
        montreLeMessage(robot);
    }

    @Test
    @CasDeRecette("S1-08")
    @DisplayName("S1-08 · succès : le bandeau dit ce qui a été rapatrié, et l'identité paraît")
    void succes_dit_ce_qui_a_ete_rapatrie(FxRobot robot) throws TimeoutException {
        when(client.moi()).thenReturn(ReponseApi.succes(PROFIL));
        // ⚠️ La surcharge à TROIS arguments : c'est celle que le ViewModel appelle. Bouchonner
        // `synchroniser(client)` laissait le résumé vide, et le bandeau disait « Connexion réussie. »
        // sans dire ce qui avait été rapatrié - la moitié du cas.
        when(rapprocheur.synchroniser(eq(client), any(), any()))
                .thenReturn(Optional.of(new RapportSynchro("taxons", 385)));

        jouerLaConnexion(robot, JETON);

        assertThat(statut(robot).getText())
                .as("le succès dit ce qu'il a rapatrié : sans ce compte, on ignore si le référentiel a suivi")
                .contains("Connexion réussie")
                .contains("385 taxons");
        assertThat(robot.lookup("#labelIdentite").queryAs(Label.class).getText())
                .as("l'identité et le rôle paraissent, pour qu'on sache SOUS QUEL COMPTE on déposera")
                .contains("chiro");
        montreLeMessage(robot);
    }

    // --------------------------------------------------------------------------------------------

    /// Ouvre la modale **par le menu**, tape `jeton`, et attend que le bandeau ait quelque chose à dire.
    private void jouerLaConnexion(FxRobot robot, String jeton) throws TimeoutException {
        Respiration.avantLeGeste(robot);
        ouvrirLaModaleParLeMenu(robot);
        Respiration.entreDeuxGestes(robot);

        if (!jeton.isEmpty()) {
            // ⚠️ Le jeton se TAPE. Un champ qui se remplit d'un coup par `setText` ne montre pas le
            // geste, et c'est le geste que ce cas fait juger autant que le message.
            robot.clickOn("#champToken").write(jeton);
            Respiration.entreDeuxGestes(robot);
        }
        robot.clickOn("#boutonConnecter");
        // L'exécuteur est asynchrone : le message n'est PAS là au retour du clic.
        WaitForAsyncUtils.waitFor(
                20, TimeUnit.SECONDS, () -> !statut(robot).getText().isBlank());
    }

    /// Amène le message dans le cadre, s'y arrête le temps qu'on le lise, et **vérifie** qu'il y est.
    private void montreLeMessage(FxRobot robot) {
        CadreVisible.amener(statut(robot), robot);
        Respiration.surLeMomentCle(robot);
        assertThat(CadreVisible.contient(statut(robot)))
                .as("le message que ce cas fait juger est visible à l'image, et non sous le pli")
                .isTrue();
    }

    private static Label statut(FxRobot robot) {
        return robot.lookup("#bandeauStatut").queryAs(Label.class);
    }

    private void ouvrirLaModaleParLeMenu(FxRobot robot) {
        robot.clickOn("#menuOutils");
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.entreDeuxGestes(robot);
        robot.clickOn(LIBELLE_ENTREE_MENU);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
