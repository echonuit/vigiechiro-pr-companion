package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.fixture.JournalDeCapteur;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// **Test E2E de parcours (P7)** : depuis **M-Passage**, l'ouverture de **M-Vision-Tadarida**
/// (validation des observations) via le contrat socle `OuvrirValidation`. La validation n'est
/// **déverrouillée que sur un passage déposé** (`validationVerrouillee`) : on prépare donc un passage
/// importé puis **déposé**, et on vérifie que le bouton « Sons & validation » est actif puis qu'il
/// ouvre l'écran de validation (`passage → validation`, sans dépendance directe).
@ExtendWith(ApplicationExtension.class)
class ParcoursPassageVersValidationE2ETest {

    private static final String ID_USER = "u-e2e-val";
    private static final String SERIE = "1925492";
    private static final int FREQUENCE_WAV = 384_000;
    private static final int TRAMES = 576_000;
    private Injector injector;
    private long idPassage;
    private ContexteSite contexte;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-val");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur E2E"));
        ServiceSites sites = injector.getInstance(ServiceSites.class);
        Site site = sites.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        PointDEcoute point = sites.ajouterPoint(site.id(), "A1", 43.4010, -1.5740, null);
        Path sd = creerNuitSynthetique(workspace.resolve("sd"));
        Passage importe = injector.getInstance(ServiceImport.class)
                .importer(sd, point.id(), new Prefixe("640380", 2026, 1, "A1"))
                .passage();
        idPassage = importe.id();
        contexte = new ContexteSite("640380", "A1", null);

        // La validation Tadarida n'est ouverte que sur un passage déposé : on dépose le passage
        // importé (statut DEPOSE + verdict OK) directement en base, l'étape verif/dépôt étant couverte
        // par le parcours fil rouge E2E.
        new PassageDao(source).update(deposer(importe));

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1280, 860));
        stage.show();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("M-Passage déposé : « Sons & validation » est actif et ouvre l'écran audio du passage")
    void passage_depose_ouvre_validation(FxRobot robot) {
        // 1) Entrer sur M-Passage (déposé) : le bouton validation est déverrouillé.
        robot.interact(() -> injector.getInstance(OuvrirPassage.class).ouvrir(idPassage, contexte));
        Button validation = robot.lookup("#boutonValidation").queryAs(Button.class);
        assertThat(validation.isDisabled()).isFalse();

        // 2) Ouvrir la validation Tadarida → l'écran M-Vision-Tadarida s'affiche.
        robot.interact(validation::fire);

        assertThat(robot.lookup("#tableObservations").queryAs(TableView.class)).isNotNull();
    }

    private static Passage deposer(Passage p) {
        return new Passage(
                p.id(),
                p.numeroPassage(),
                p.annee(),
                p.dateEnregistrement(),
                p.heureDebut(),
                p.heureFin(),
                p.parametresAcquisition(),
                StatutWorkflow.DEPOSE,
                Verdict.OK,
                p.commentaire(),
                p.donneesMeteo(),
                "2026-06-22",
                p.idPoint(),
                p.idEnregistreur(),
                null);
    }

    private static Path creerNuitSynthetique(Path sd) throws Exception {
        Files.createDirectories(sd);
        // Journal et releve par la fixture (#2868) : le trace complet d'une nuit, la ou ce test
        // n'avait besoin que d'un journal valide. Il n'affirme rien sur son contenu.
        JournalDeCapteur.ecrire(sd, SERIE, LocalDate.of(2026, 4, 22));
        JournalDeCapteur.ecrireReleve(sd, SERIE);
        byte[] pcm = new byte[TRAMES * 2];
        for (int i = 0; i < TRAMES; i++) {
            short e = (short) (((i * 41) % 1000) - 500);
            pcm[2 * i] = (byte) (e & 0xFF);
            pcm[2 * i + 1] = (byte) ((e >> 8) & 0xFF);
        }
        // Writer de production (#2864) : memes octets, et c'est le format que l'application
        // saura relire.
        FichierWav.ecrire(
                sd.resolve("PaRecPR" + SERIE + "_20260422_203922.wav"), 1, FREQUENCE_WAV, 16, pcm, 0, pcm.length);
        return sd;
    }
}
