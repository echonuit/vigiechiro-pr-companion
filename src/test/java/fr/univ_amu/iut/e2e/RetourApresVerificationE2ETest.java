package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.multisite.view.NavigationMultisite;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test E2E de **propagation d'une vérification** : **une seule** vérification, faite par l'interface,
/// doit se refléter sur **toutes** les surfaces qui l'affichent, au fur et à mesure qu'on dépile la
/// navigation (#1822).
///
/// Régression visée : le [fr.univ_amu.iut.commun.view.Navigateur] garde les écrans **vivants** dans sa
/// pile et les ré-affiche tels quels au retour ; sans le contrat
/// [fr.univ_amu.iut.commun.view.RafraichirAuRetour], l'écran ré-affiché montre son état **d'avant** le
/// verdict (statut « Transformé », verdict « non saisi », ligne d'agrégat périmée).
///
/// **Pourquoi un seul parcours plutôt que deux.** Ce test remplace `RetourPassageApresVerification` et
/// `RetourAgregatApresVerification`, qui prouvaient chacun une moitié : le premier faisait la vraie
/// vérification par l'IHM mais ne contrôlait que M-Passage ; les seconds contrôlaient les agrégats mais
/// court-circuitaient l'interface en appelant `ServiceQualification` directement. Ni l'un ni l'autre ne
/// prouvait qu'**une** vérification réelle se propage **partout** - or c'est exactement là que le défaut
/// se loge (cache non invalidé, vue agrégée non rafraîchie), et c'est ce que fait l'utilisateur : il
/// vérifie **une fois**.
///
/// Parcours réel sur de vrais services + base SQLite : écran d'origine (agrégat) → M-Passage →
/// M-Qualification (verdict OK **par les boutons**) → ← Retour (M-Passage à jour) → ← Retour (l'écran
/// d'origine à jour).
@ExtendWith(ApplicationExtension.class)
class RetourApresVerificationE2ETest {

    private static final String ID_USER = "u-retour";
    private static final String CARRE = "640380";
    private static final String POINT = "A1";
    private static final String SITE = "Étang de la Tuilière";
    private static final String ENREGISTREUR = "1925492";

    private Injector injector;
    private long idPassage;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-retour-e2e");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        idPassage = seederPassageTransforme(source, workspace);

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1100, 760));
        stage.show(); // démarre sur l'accueil du chrome
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Depuis M-Multisite : une vérification par l'IHM se reflète sur M-Passage PUIS sur l'agrégat")
    void depuis_multisite_la_verification_se_propage(FxRobot robot) throws TimeoutException {
        robot.interact(() -> injector.getInstance(NavigationMultisite.class).ouvrirAccueil());
        TableView<?> agregat = robot.lookup("#tableLignes").queryAs(TableView.class);
        assertThat(agregat.getItems()).hasSize(1);
        assertThat(ligneMultisite(agregat).statut()).isEqualTo(StatutWorkflow.TRANSFORME);

        verifierParLInterfaceEtRevenirSurLePassage(robot);

        // Second retour : l'agrégat d'où l'on vient doit refléter la MÊME vérification.
        robot.interact(robot.lookup("#boutonRetour").queryAs(Button.class)::fire);
        assertThat(ligneMultisite(agregat).statut())
                .as("une vérification faite dans M-Qualification doit remonter jusqu'à la vue agrégée")
                .isEqualTo(StatutWorkflow.VERIFIE);
        assertThat(ligneMultisite(agregat).verdict()).isEqualTo(Verdict.OK);
    }

    @Test
    @DisplayName("Depuis M-Site-detail : une vérification par l'IHM se reflète sur M-Passage PUIS sur la fiche site")
    void depuis_site_detail_la_verification_se_propage(FxRobot robot) throws TimeoutException {
        robot.interact(() -> injector.getInstance(OuvrirSite.class).ouvrirDetail(CARRE));
        TableView<?> passages = robot.lookup("#tablePassages").queryAs(TableView.class);
        assertThat(passages.getItems()).hasSize(1);
        assertThat(ligneSite(passages).statut()).isEqualTo(StatutWorkflow.TRANSFORME);

        verifierParLInterfaceEtRevenirSurLePassage(robot);

        robot.interact(robot.lookup("#boutonRetour").queryAs(Button.class)::fire);
        assertThat(ligneSite(passages).statut())
                .as("une vérification faite dans M-Qualification doit remonter jusqu'à la fiche du site")
                .isEqualTo(StatutWorkflow.VERIFIE);
        assertThat(ligneSite(passages).verdict()).isEqualTo(Verdict.OK);
    }

    /// Le cœur du parcours, commun aux deux points d'entrée : ouvrir le passage (il est « Transformé »),
    /// ouvrir M-Qualification, poser le verdict **par les boutons de l'écran** (pas par un appel de
    /// service), revenir, et vérifier que M-Passage s'est rafraîchi. À la sortie, on est sur M-Passage :
    /// à l'appelant de dépiler une fois de plus pour contrôler son écran d'origine.
    private void verifierParLInterfaceEtRevenirSurLePassage(FxRobot robot) throws TimeoutException {
        ContexteSite contexte = new ContexteSite(CARRE, POINT, SITE);
        robot.interact(() -> injector.getInstance(OuvrirPassage.class).ouvrir(idPassage, contexte));
        Label lblStatut = robot.lookup("#lblStatut").queryAs(Label.class);
        Label lblVerdict = robot.lookup("#lblVerdict").queryAs(Label.class);
        assertThat(lblStatut.getText()).isEqualTo(StatutWorkflow.TRANSFORME.libelle());
        assertThat(lblVerdict.getText()).isEqualTo("non saisi");

        robot.interact(() ->
                injector.getInstance(OuvrirVerification.class).ouvrir(new ContextePassage(idPassage, 2, contexte)));

        // M-Qualification se charge **hors du fil JavaFX** (#1210) et, sur le vrai injecteur, l'exécuteur
        // est **asynchrone**. Sans cette attente, le clic ci-dessous part pendant que le chargement est
        // encore en vol : celui-ci atterrit ensuite et **écrase** le verdict qu'on venait de choisir
        // (`verdictVm.appliquer(...)` réapplique l'état lu en base). Rien n'est alors enregistré, et le
        // passage reste « Transformé » - un échec qui ne se produit que sur une machine lente, donc en CI.
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> !robot.lookup("#tableSequences")
                        .queryAs(TableView.class)
                        .getItems()
                        .isEmpty());

        robot.interact(robot.lookup("#boutonOk").queryAs(Button.class)::fire);
        robot.interact(robot.lookup("#boutonEnregistrer").queryAs(Button.class)::fire);

        // Premier retour : M-Passage. Mêmes instances de labels (l'écran est ré-affiché, pas reconstruit)
        // : leur texte doit pourtant refléter le nouvel état.
        robot.interact(robot.lookup("#boutonRetour").queryAs(Button.class)::fire);
        assertThat(lblStatut.getText()).isEqualTo(StatutWorkflow.VERIFIE.libelle());
        assertThat(lblVerdict.getText()).isEqualTo(Verdict.OK.libelle());
    }

    private static fr.univ_amu.iut.multisite.model.LignePassage ligneMultisite(TableView<?> table) {
        return (fr.univ_amu.iut.multisite.model.LignePassage) table.getItems().get(0);
    }

    private static fr.univ_amu.iut.sites.viewmodel.LignePassage ligneSite(TableView<?> table) {
        return (fr.univ_amu.iut.sites.viewmodel.LignePassage) table.getItems().get(0);
    }

    /// Seede un passage **transformé** complet (utilisateur courant, site, point, enregistreur, session,
    /// trois séquences) prêt à être vérifié, et renvoie son identifiant.
    private long seederPassageTransforme(SourceDeDonnees source, Path workspace) {
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        new EnregistreurDao(source).insert(new Enregistreur(ENREGISTREUR, "V1.01", null));
        Site site = new SiteDao(source)
                .insert(new Site(null, CARRE, SITE, Protocole.STANDARD, "Aix", "2026-01-01", ID_USER));
        PointDEcoute point =
                new PointDao(source).insert(new PointDEcoute(null, POINT, 43.4010, -1.5740, "Chêne", site.id()));

        Passage passage = new PassageDao(source)
                .insert(new Passage(
                        null,
                        2,
                        2026,
                        "2026-06-22",
                        "20:25:00",
                        "07:47:00",
                        null,
                        StatutWorkflow.TRANSFORME,
                        null,
                        null,
                        null,
                        null,
                        point.id(),
                        ENREGISTREUR));
        SessionDEnregistrement session = new SessionDao(source)
                .insert(new SessionDEnregistrement(
                        null, workspace.resolve("session").toString(), null, null, passage.id()));

        EnregistrementOriginalDao originalDao = new EnregistrementOriginalDao(source);
        SequenceDao sequenceDao = new SequenceDao(source);
        for (String base : List.of("seqA", "seqB", "seqC")) {
            EnregistrementOriginal original = originalDao.insert(new EnregistrementOriginal(
                    null, base + ".wav", workspace.resolve(base + ".wav").toString(), 5.0, 384000, null, session.id()));
            sequenceDao.insert(new SequenceDEcoute(
                    null,
                    base + "_000.wav",
                    original.id(),
                    0,
                    0.0,
                    5.0,
                    workspace.resolve(base + "_000.wav").toString(),
                    false,
                    session.id()));
        }
        return passage.id();
    }
}
