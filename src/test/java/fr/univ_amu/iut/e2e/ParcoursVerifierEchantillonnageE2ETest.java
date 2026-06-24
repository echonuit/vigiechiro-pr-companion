package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import fr.univ_amu.iut.qualification.model.SelectionDEcoute;
import fr.univ_amu.iut.qualification.model.SequenceSelectionnee;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// **Test E2E de parcours (P3 — Vérifier l'enregistrement par échantillonnage)** : sur l'injecteur
/// applicatif réel ([RacineInjecteur]) et une base SQLite jetable (workspace temporaire +
/// [MigrationSchema]), on enchaîne les **trois étapes du parcours P3** depuis une nuit fraîchement
/// importée (précondition P2, statut `Transformé`) jusqu'au verdict global (`Vérifié`) :
///
/// 1. **Pré-check synthétique** ([ServiceQualification#precheck]) : les trois feux de l'état de la
///    nuit (couverture horaire, nombre de fichiers, cohérence du renommage R6) sont calculés sans
///    écoute. Sur la nuit de test (une seule série copiée/renommée par l'import), le feu « nombre de
///    fichiers » est **orange** (nuit creuse, < 50) et le feu « cohérence du renommage » est **vert**
///    (l'import a appliqué le préfixe R6 attendu).
/// 2. **Sound check par échantillonnage** ([ServiceQualification#ouvrirVerification]) : l'ouverture
///    constitue automatiquement une **sélection d'écoute** `RéparTemporel` (R12) ; on écoute une
///    séquence (flag « écoutée » de la jonction).
/// 3. **Verdict global** ([ServiceQualification#enregistrerVerdict]) : le verdict `OK` est mémorisé
///    et le passage **transite vers `Vérifié`** (R13, aucun seuil d'écoute imposé).
///
/// Conformément au harnais E2E « sans IHM », tout est piloté **par les services réels** (assemblés
/// par leurs modules Guice via les mêmes constructeurs que l'application), et asserté **en base** ou
/// sur les objets métier renvoyés. La nuit est importée par le vrai [ServiceImport] (l'écran M-Import
/// passe par un `DirectoryChooser` natif non pilotable par TestFX, comme dans `ParcoursDepotE2ETest`).
@ExtendWith(ApplicationExtension.class)
class ParcoursVerifierEchantillonnageE2ETest {

    private static final String ID_USER = "u-e2e-p3";
    private static final String SERIE = "1925492";
    private static final int FREQUENCE_WAV = 2000; // Hz, multiple de 10 (R10)
    private static final int TRAMES = 3000;
    private static final String LOG =
            "22/04/26 - 16:02:20 PR1925492 Demarrage Passive Recorder numero de serie 1925492, V1.01,"
                    + " CPU 600000000, T4.1\n"
                    + "22/04/26 - 16:02:21 PR1925492 Sonde temperature/hygrometrie presente, lecture toutes"
                    + " les 600s\n";

    private Injector injector;
    private SourceDeDonnees source;
    private ServiceQualification qualification;
    private long idPassage;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-p3");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        // Précondition P2 : utilisateur + site + point, puis import d'une nuit → passage Transformé.
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur E2E"));
        ServiceSites sites = injector.getInstance(ServiceSites.class);
        Site site = sites.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        PointDEcoute point = sites.ajouterPoint(site.id(), "A1", 43.5298, 5.4474, "Près du chêne");
        Path sd = creerNuitSynthetique(workspace.resolve("sd"));
        idPassage = injector.getInstance(ServiceImport.class)
                .importer(sd, point.id(), new Prefixe("640380", 2026, 1, "A1"))
                .passage()
                .id();

        // Service de la feature qualification, assemblé par QualificationModule (mêmes constructeurs).
        qualification = injector.getInstance(ServiceQualification.class);
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("P3 : pré-check 3 feux → sélection d'écoute RéparTemporel → verdict OK (Transformé → Vérifié)")
    void parcours_verifier_par_echantillonnage() {
        PassageDao passages = new PassageDao(source);
        // Précondition : la nuit importée est au statut Transformé (sortie de l'import P2).
        assertThat(passages.findById(idPassage).orElseThrow().statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);

        // --- Étape 1 : pré-check synthétique (3 feux, sans écoute) -------------------------------
        PreCheckNuit.Diagnostic diagnostic = qualification.precheck(idPassage);
        assertThat(diagnostic).isNotNull();
        assertThat(diagnostic.nombreFichiers())
                .as("une seule série importée → nuit creuse (< 50)")
                .isEqualTo(PreCheckNuit.Feu.ORANGE);
        assertThat(diagnostic.coherenceRenommage())
                .as("l'import a renommé les fichiers selon le préfixe R6 attendu")
                .isEqualTo(PreCheckNuit.Feu.VERT);

        // --- Étape 2 : sound check par échantillonnage (sélection RéparTemporel R12) -------------
        SelectionDEcoute selection = qualification.ouvrirVerification(idPassage);
        assertThat(selection.id()).isNotNull();
        assertThat(selection.methode())
                .as("méthode par défaut à l'ouverture (R12)")
                .isEqualTo(MethodeSelection.REPARTITION_TEMPORELLE);
        List<SequenceSelectionnee> sequences = qualification.sequencesDeLaSelection(selection.id());
        assertThat(sequences)
                .as("la sélection d'écoute est constituée automatiquement")
                .isNotEmpty();
        assertThat(sequences).hasSize(selection.taille());
        assertThat(sequences).extracting(SequenceSelectionnee::position).startsWith(0);

        // L'utilisateur écoute une séquence : son flag « écoutée » bascule dans la jonction.
        Long premiereSequence = sequences.get(0).idSequence();
        qualification.marquerSequenceEcoutee(selection.id(), premiereSequence);
        SequenceSelectionnee relue = qualification.sequencesDeLaSelection(selection.id()).stream()
                .filter(s -> s.idSequence().equals(premiereSequence))
                .findFirst()
                .orElseThrow();
        assertThat(relue.ecoutee()).isTrue();

        // --- Étape 3 : verdict global (R13 : aucun seuil d'écoute imposé) ------------------------
        Passage verifie = qualification.enregistrerVerdict(idPassage, Verdict.OK, "Échantillon sans saturation, RAS.");
        assertThat(verifie.verdictVerification()).isEqualTo(Verdict.OK);
        assertThat(verifie.statutWorkflow()).isEqualTo(StatutWorkflow.VERIFIE);

        // Bout-en-bout : le passage est bien Vérifié + verdict OK en base, et n'est pas « à jeter » (R14).
        Passage relu = passages.findById(idPassage).orElseThrow();
        assertThat(relu.statutWorkflow()).isEqualTo(StatutWorkflow.VERIFIE);
        assertThat(relu.verdictVerification()).isEqualTo(Verdict.OK);
        assertThat(relu.commentaire()).isEqualTo("Échantillon sans saturation, RAS.");
        assertThat(qualification.estAJeter(idPassage)).isFalse();
    }

    /// Crée un dossier SD minimal (journal LogPR + un WAV PCM valide à 2 kHz) que l'import peut traiter.
    private static Path creerNuitSynthetique(Path sd) throws Exception {
        Files.createDirectories(sd);
        Files.writeString(sd.resolve("LogPR" + SERIE + ".txt"), LOG, StandardCharsets.UTF_8);
        Files.writeString(sd.resolve("PaRecPR" + SERIE + "_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
        ecrireWav(sd.resolve("PaRecPR" + SERIE + "_20260422_203922.wav"));
        return sd;
    }

    private static void ecrireWav(Path fichier) throws Exception {
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
        Files.write(fichier, buf.array());
    }
}
