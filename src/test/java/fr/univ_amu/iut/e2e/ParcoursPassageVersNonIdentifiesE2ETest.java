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
import fr.univ_amu.iut.commun.view.OuvrirValidation;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// **Test E2E de parcours** : l'écran **« Sons & validation »** d'un passage (source `ParPassage`) réunit
/// désormais les observations Tadarida **et** les **séquences non identifiées** (sons présents sur disque
/// mais **sans observation Tadarida**), qui y forment la vue « Sons non identifiés ».
///
/// On importe une nuit synthétique **sans importer de CSV Tadarida** : le passage a donc des séquences
/// d'écoute (`listening_sequence`) mais **aucune observation**. L'écran audio du passage (ouvert via le
/// contrat socle `OuvrirValidation`, comme la carte « Sons & validation » de M-Passage) ne liste donc que
/// ces séquences non identifiées. On vérifie qu'elles **apparaissent** (sans taxon), qu'elles ne sont **pas
/// validables en l'état** (« Valider » reste désactivé : pas de proposition Tadarida à retenir), puis qu'on
/// peut les **valider à la main** (« Corriger ») — ce qui crée une observation corrigée qui **persiste** et
/// laisse la séquence dans la liste.
@ExtendWith(ApplicationExtension.class)
class ParcoursPassageVersNonIdentifiesE2ETest {

    private static final String ID_USER = "u-e2e-ni";
    private static final String SERIE = "1925492";
    private static final int FREQUENCE_WAV = 384_000;
    private static final int TRAMES = 576_000;
    private static final String LOG = "22/04/26 - 16:02:20 PR1925492 Demarrage Passive Recorder numero de serie"
            + " 1925492, V1.01, CPU 600000000, T4.1\n";

    private Injector injector;
    private ContextePassage contextePassage;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-ni");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur E2E"));
        ServiceSites sites = injector.getInstance(ServiceSites.class);
        Site site = sites.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        PointDEcoute point = sites.ajouterPoint(site.id(), "A1", 43.4010, -1.5740, null);
        Path sd = creerNuitSynthetique(workspace.resolve("sd"));
        // Import de la nuit uniquement : des séquences sont créées, mais AUCUNE observation (pas de CSV).
        Passage importe = injector.getInstance(ServiceImport.class)
                .importer(sd, point.id(), new Prefixe("640380", 2026, 1, "A1"))
                .passage();
        contextePassage = new ContextePassage(importe.id(), 1, new ContexteSite("640380", "A1", null));

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

    /// Ouvre l'écran « Sons & validation » du passage (source `ParPassage`) via le contrat socle, comme la
    /// carte « Sons & validation » de M-Passage, et renvoie sa table d'observations.
    @SuppressWarnings("unchecked")
    private TableView<LigneObservationAudio> ouvrirSonsEtValidation(FxRobot robot) {
        robot.interact(() -> injector.getInstance(OuvrirValidation.class).ouvrir(contextePassage));
        return (TableView<LigneObservationAudio>)
                (TableView<?>) robot.lookup("#tableObservations").queryAs(TableView.class);
    }

    @Test
    @DisplayName("« Sons & validation » d'un passage sans CSV liste ses séquences non identifiées (sans observation)")
    void sons_et_validation_liste_les_non_identifies(FxRobot robot) {
        // Sans CSV importé, la liste fusionnée du passage ne contient que les séquences non identifiées.
        TableView<LigneObservationAudio> table = ouvrirSonsEtValidation(robot);

        assertThat(table.getItems())
                .as("des séquences non identifiées à écouter")
                .isNotEmpty();
        assertThat(table.getItems()).allSatisfy(ligne -> {
            assertThat(ligne.idObservation()).as("pas d'observation").isNull();
            assertThat(ligne.taxonTadarida())
                    .as("aucune identification Tadarida")
                    .isNull();
        });

        // « Valider » (retenir la proposition Tadarida) reste désactivé : pas de proposition sur une séquence
        // non identifiée. La validation se fait par « Corriger » (cf. test suivant).
        robot.interact(() -> table.getSelectionModel().select(0));
        assertThat(robot.lookup("#btnValider").queryAs(Button.class).isDisabled())
                .as("rien à « retenir » : pas de proposition Tadarida")
                .isTrue();
    }

    @Test
    @DisplayName("Valider une séquence non identifiée à la main crée une observation (corrigée) qui persiste")
    void valider_manuellement_une_sequence(FxRobot robot) {
        TableView<LigneObservationAudio> table = ouvrirSonsEtValidation(robot);
        long idSequence = table.getItems().get(0).idSequence();

        // Sélectionner la 1re séquence, choisir un taxon, puis « Corriger » = la valider à la main.
        robot.interact(() -> table.getSelectionModel().select(0));
        @SuppressWarnings("unchecked")
        ComboBox<Taxon> choixTaxon =
                (ComboBox<Taxon>) (ComboBox<?>) robot.lookup("#choixTaxon").queryAs(ComboBox.class);
        Taxon pippip = choixTaxon.getItems().stream()
                .filter(t -> "Pippip".equals(t.code()))
                .findFirst()
                .orElseThrow();
        robot.interact(() -> choixTaxon.setValue(pippip));

        Button corriger = robot.lookup("#btnCorriger").queryAs(Button.class);
        assertThat(corriger.isDisabled())
                .as("Corriger actif : une ligne est sélectionnée et un taxon choisi")
                .isFalse();
        robot.interact(corriger::fire);

        // La séquence RESTE dans la liste, désormais validée à la main (observation, corrigée, taxon retenu).
        LigneObservationAudio ligne = table.getItems().stream()
                .filter(l -> l.idSequence() == idSequence)
                .findFirst()
                .orElseThrow();
        assertThat(ligne.idObservation())
                .as("une observation manuelle a été créée")
                .isNotNull();
        assertThat(ligne.taxonObservateur()).isEqualTo("Pippip");
        assertThat(ligne.statut()).isEqualTo(StatutObservation.CORRIGEE);

        // Persistance : l'observation manuelle est bien en base.
        assertThat(injector.getInstance(ObservationDao.class).observationManuelleDeLaSequence(idSequence))
                .as("observation manuelle persistée pour la séquence")
                .isPresent();
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
