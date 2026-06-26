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
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
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
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test E2E de **synchronisation au retour de navigation** : M-Passage doit refléter la vérification
/// dès qu'on y revient depuis M-Qualification, sans qu'il faille ressortir jusqu'au site et rouvrir
/// le passage.
///
/// Régression visée : le [fr.univ_amu.iut.commun.view.Navigateur] garde les écrans vivants dans sa
/// pile et les ré-affiche tels quels au retour ; sans le contrat
/// [fr.univ_amu.iut.commun.view.RafraichirAuRetour], le passage ré-affiché par ← Retour montrait son
/// état **d'avant** le verdict (statut « Transformé », verdict « non saisi »).
///
/// Parcours réel sur de vrais services + base SQLite : ouverture M-Passage → ouverture
/// M-Qualification → choix du verdict OK + enregistrement → ← Retour. On vérifie qu'au retour le
/// statut est passé à « Vérifié » et le verdict à « OK ».
@ExtendWith(ApplicationExtension.class)
class RetourPassageApresVerificationE2ETest {

    private static final String ID_USER = "u-sync";
    private static final String CARRE = "640380";
    private static final String POINT = "A1";
    private static final String ENREGISTREUR = "1925492";

    private Injector injector;
    private long idPassage;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-sync-e2e");
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
    @DisplayName("Revenir sur M-Passage après vérification montre le verdict, sans ressortir jusqu'au site")
    void retour_sur_passage_reflete_la_verification(FxRobot robot) {
        ContexteSite contexteSite = new ContexteSite(CARRE, POINT, "Étang de la Tuilière");

        // 1) Ouvrir M-Passage : statut « Transformé », verdict non saisi.
        robot.interact(() -> injector.getInstance(OuvrirPassage.class).ouvrir(idPassage, contexteSite));
        Label lblStatut = robot.lookup("#lblStatut").queryAs(Label.class);
        Label lblVerdict = robot.lookup("#lblVerdict").queryAs(Label.class);
        assertThat(lblStatut.getText()).isEqualTo(StatutWorkflow.TRANSFORME.libelle());
        assertThat(lblVerdict.getText()).isEqualTo("non saisi");

        // 2) Ouvrir M-Qualification sur ce passage (drill-down empilé sur M-Passage).
        robot.interact(() ->
                injector.getInstance(OuvrirVerification.class).ouvrir(new ContextePassage(idPassage, 2, contexteSite)));

        // 3) Choisir le verdict OK puis enregistrer (vrai service → base mise à jour).
        robot.interact(robot.lookup("#boutonOk").queryAs(Button.class)::fire);
        robot.interact(robot.lookup("#boutonEnregistrer").queryAs(Button.class)::fire);

        // 4) ← Retour vers M-Passage : il doit se rafraîchir et montrer la vérification.
        robot.interact(robot.lookup("#boutonRetour").queryAs(Button.class)::fire);

        // Mêmes instances de labels (l'écran est ré-affiché, pas reconstruit) : leur texte doit
        // pourtant refléter le nouvel état — c'est tout l'enjeu du rafraîchissement au retour.
        assertThat(lblStatut.getText()).isEqualTo(StatutWorkflow.VERIFIE.libelle());
        assertThat(lblVerdict.getText()).isEqualTo(Verdict.OK.libelle());
    }

    /// Seede un passage **transformé** complet (site, point, enregistreur, session, trois séquences)
    /// prêt à être vérifié, et renvoie son identifiant.
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
