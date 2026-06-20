package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.multisite.view.NavigationMultisite;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// **Test E2E de parcours (P5)** : la **vue agrégée M-Multisite** et son **drill-down** vers
/// **M-Passage**, sur le vrai chrome. On ouvre l'écran multi-sites (navigation réelle), on vérifie
/// que le passage importé y figure, puis un **double-clic** sur sa ligne ouvre M-Passage via le
/// contrat socle `OuvrirPassage` (`multisite → passage`).
@Tag("conformite")
@ExtendWith(ApplicationExtension.class)
class ParcoursMultisiteVersPassageE2ETest {

    private static final String ID_USER = "u-e2e-ms";
    private static final String SERIE = "1925492";
    private static final int FREQUENCE_WAV = 2000;
    private static final int TRAMES = 3000;
    private static final String DATE_NUIT = "2026-04-22"; // date du journal → cellule unique du tableau
    private static final String LOG = "22/04/26 - 16:02:20 PR1925492 Demarrage Passive Recorder numero de serie"
            + " 1925492, V1.01, CPU 600000000, T4.1\n";

    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-ms");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur E2E"));
        ServiceSites sites = injector.getInstance(ServiceSites.class);
        Site site = sites.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        PointDEcoute point = sites.ajouterPoint(site.id(), "A1", 43.5298, 5.4474, null);
        Path sd = creerNuitSynthetique(workspace.resolve("sd"));
        injector.getInstance(ServiceImport.class).importer(sd, point.id(), new Prefixe("640380", 2026, 1, "A1"));

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
    @DisplayName("M-Multisite : le passage figure dans la vue agrégée, le double-clic ouvre M-Passage")
    void multisite_drill_vers_passage(FxRobot robot) {
        NavigationViewModel navigation = injector.getInstance(NavigationViewModel.class);

        // 1) Ouvrir la vue agrégée multi-sites (navigation socle réelle).
        robot.interact(() -> injector.getInstance(NavigationMultisite.class).ouvrirAccueil());
        TableView<?> table = robot.lookup("#tableLignes").queryAs(TableView.class);
        assertThat(table.getItems()).hasSize(1);
        assertThat(navigation.getVueCourante()).isEqualTo("multisite");

        // 2) Double-clic sur la ligne (cellule date, unique) → drill-down vers M-Passage.
        robot.doubleClickOn(DATE_NUIT);

        assertThat(navigation.getVueCourante()).isEqualTo("passage");
        assertThat(robot.lookup("#boutonVerifier").queryAs(Button.class)).isNotNull();
    }

    private static Path creerNuitSynthetique(Path sd) throws Exception {
        Files.createDirectories(sd);
        Files.writeString(sd.resolve("LogPR" + SERIE + ".txt"), LOG, StandardCharsets.UTF_8);
        Files.writeString(sd.resolve("PaRecPR" + SERIE + "_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
        byte[] pcm = new byte[TRAMES * 2];
        for (int i = 0; i < TRAMES; i++) {
            short e = (short) (((i * 41) % 1000) - 500);
            pcm[2 * i] = (byte) (e & 0xFF);
            pcm[2 * i + 1] = (byte) ((e >> 8) & 0xFF);
        }
        ByteBuffer buf = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + pcm.length);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putInt(FREQUENCE_WAV);
        buf.putInt(FREQUENCE_WAV * 2);
        buf.putShort((short) 2);
        buf.putShort((short) 16);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(pcm.length);
        buf.put(pcm);
        Files.write(sd.resolve("PaRecPR" + SERIE + "_20260422_203922.wav"), buf.array());
        return sd;
    }
}
