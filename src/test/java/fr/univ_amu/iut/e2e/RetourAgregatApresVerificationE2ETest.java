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
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
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
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Tests E2E de **synchronisation au retour** pour les écrans **agrégateurs** qui listent des passages
/// avec leur statut/verdict (M-Multisite, M-Site-detail). Même classe de régression que pour
/// M-Passage : le [Navigateur] garde l'écran vivant et le ré-affiche tel quel au retour ; sans le
/// contrat [fr.univ_amu.iut.commun.view.RafraichirAuRetour], le tableau resterait sur l'état d'avant
/// la vérification du passage ouvert depuis cet écran.
///
/// Scénario commun : ouvrir l'agrégat (le passage seedé y apparaît « Transformé »), ouvrir le passage
/// (drill-down empilé), enregistrer un verdict via le **vrai** [ServiceQualification] (ce que fait
/// M-Qualification), puis revenir sur l'agrégat. On vérifie que la ligne reflète alors « Vérifié » +
/// verdict « OK ».
@ExtendWith(ApplicationExtension.class)
class RetourAgregatApresVerificationE2ETest {

    private static final String ID_USER = "u-agg";
    private static final String CARRE = "640380";
    private static final String POINT = "A1";
    private static final String ENREGISTREUR = "1925492";

    private Injector injector;
    private long idPassage;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-agg-e2e");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        idPassage = seederPassageTransforme(source, workspace);

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1100, 760));
        stage.show();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("M-Multisite : revenir après vérification d'un passage reflète le nouveau statut/verdict")
    void multisite_se_rafraichit_au_retour(FxRobot robot) {
        robot.interact(() -> injector.getInstance(NavigationMultisite.class).ouvrirAccueil());
        TableView<?> table = robot.lookup("#tableLignes").queryAs(TableView.class);
        assertThat(table.getItems()).hasSize(1);
        assertThat(ligneMultisite(table).statut()).isEqualTo(StatutWorkflow.TRANSFORME);

        verifierPassageDepuis(robot);

        // Mêmes instances de tableau ré-affichées : le contenu doit pourtant refléter la vérification.
        assertThat(ligneMultisite(table).statut()).isEqualTo(StatutWorkflow.VERIFIE);
        assertThat(ligneMultisite(table).verdict()).isEqualTo(Verdict.OK);
    }

    @Test
    @DisplayName("M-Site-detail : revenir après vérification d'un passage reflète le nouveau statut/verdict")
    void site_detail_se_rafraichit_au_retour(FxRobot robot) {
        robot.interact(() -> injector.getInstance(OuvrirSite.class).ouvrirDetail(CARRE));
        TableView<?> table = robot.lookup("#tablePassages").queryAs(TableView.class);
        assertThat(table.getItems()).hasSize(1);
        assertThat(ligneSite(table).statut()).isEqualTo(StatutWorkflow.TRANSFORME);

        verifierPassageDepuis(robot);

        assertThat(ligneSite(table).statut()).isEqualTo(StatutWorkflow.VERIFIE);
        assertThat(ligneSite(table).verdict()).isEqualTo(Verdict.OK);
    }

    /// Ouvre le passage depuis l'agrégat (drill-down), enregistre le verdict OK via le vrai service
    /// (comme le ferait M-Qualification), puis revient sur l'agrégat (← Retour).
    private void verifierPassageDepuis(FxRobot robot) {
        ContexteSite contexte = new ContexteSite(CARRE, POINT, "Étang de la Tuilière");
        robot.interact(() -> injector.getInstance(OuvrirPassage.class).ouvrir(idPassage, contexte));
        robot.interact(
                () -> injector.getInstance(ServiceQualification.class).enregistrerVerdict(idPassage, Verdict.OK, null));
        robot.interact(() -> injector.getInstance(Navigateur.class).revenir());
    }

    private static fr.univ_amu.iut.multisite.model.LignePassage ligneMultisite(TableView<?> table) {
        return (fr.univ_amu.iut.multisite.model.LignePassage) table.getItems().get(0);
    }

    private static fr.univ_amu.iut.sites.viewmodel.LignePassage ligneSite(TableView<?> table) {
        return (fr.univ_amu.iut.sites.viewmodel.LignePassage) table.getItems().get(0);
    }

    /// Seede un unique utilisateur (→ utilisateur courant de l'app), son site, un point et un passage
    /// **transformé** prêt à être vérifié. Renvoie l'identifiant du passage.
    private long seederPassageTransforme(SourceDeDonnees source, Path workspace) {
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        new EnregistreurDao(source).insert(new Enregistreur(ENREGISTREUR, "V1.01", null));
        Site site = new SiteDao(source)
                .insert(new Site(
                        null, CARRE, "Étang de la Tuilière", Protocole.STANDARD, "Aix", "2026-01-01", ID_USER));
        PointDEcoute point =
                new PointDao(source).insert(new PointDEcoute(null, POINT, 43.5298, 5.4474, "Chêne", site.id()));

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
