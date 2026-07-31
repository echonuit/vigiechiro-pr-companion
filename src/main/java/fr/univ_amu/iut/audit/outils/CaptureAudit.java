package fr.univ_amu.iut.audit.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.audit.view.AuditController;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'écran **« Audit de cohérence »** (feature `audit`) en PNG, sur une base vide : l'écran montre
/// alors l'état sain (« aucun écart détecté ») et sa mise en page (résumé + table des constats). Injecteur
/// **partiel** : socle + fourniture directe de [ServiceAuditCoherence] / [AuditViewModel] (sans
/// `AuditModule`, pour ne pas tirer la carte d'accueil et le `Navigateur` du chrome).
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureAudit {

    private static final String ID_UTILISATEUR = "u-demo";
    private static final String SERIE = "1925492";

    private static final String FXML_AUDIT = "Audit.fxml";

    private CaptureAudit() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException | IOException probleme) {
                erreur.set(probleme);
            } finally {
                fini.countDown();
            }
        });
        fini.await();
        Platform.exit();
        if (erreur.get() != null) {
            erreur.get().printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void capturer() throws IOException {
        Path workspace = Files.createTempDirectory("vc-capture-audit");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = creerInjecteur();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        // Un workspace ABIME : sans lui, la capture montrerait un écran vide, qui ne documente rien. Un
        // audit ne se comprend qu'en voyant un constat - sa gravité, sa catégorie, et le passage qu'il
        // accuse (celui qu'on ouvre d'un double-clic depuis #1347).
        semerDesEcarts(source);

        FXMLLoader loader = new FXMLLoader(AuditController.class.getResource(FXML_AUDIT));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        ApercuFx.enregistrerPng(new Scene(vue, 1080, 640), sortie.resolve("apercu-audit.png"));

        System.out.println("Apercu ecrit dans " + sortie.toAbsolutePath());
    }

    /// Une nuit dont les fichiers ont disparu du disque : la base les déclare, le disque ne les a plus.
    /// C'est l'écart le plus courant, et celui que l'audit existe pour dire.
    private static void semerDesEcarts(SourceDeDonnees source) {
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        Site site = new SiteDao(source)
                .insert(new Site(
                        null, "640380", "Étang de Berre", Protocole.STANDARD, null, "2026-04-01", ID_UTILISATEUR));
        Long idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));
        Passage passage = new PassageDao(source)
                .insert(new Passage(
                        null,
                        2,
                        2026,
                        "2026-06-22",
                        "21:30",
                        "06:10",
                        null,
                        StatutWorkflow.DEPOSE,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE,
                        null));
        Long idSession = new SessionDao(source)
                .insert(new SessionDEnregistrement(
                        null, "/media/carte-sd-absente/Car640380-2026-Pass2-A1", 0L, 0L, passage.id()))
                .id();
        Long idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(
                        null,
                        "PaRec_20260622_213000.wav",
                        "/media/carte-sd-absente/PaRec_20260622_213000.wav",
                        5.0,
                        384_000,
                        null,
                        idSession))
                .id();
        SequenceDao sequences = new SequenceDao(source);
        for (int index = 0; index < 3; index++) {
            String nom = "Car640380-2026-Pass2-A1-seq" + index + ".wav";
            sequences.insert(new SequenceDEcoute(
                    null,
                    nom,
                    idOriginal,
                    index,
                    0.0,
                    5.0,
                    "/media/carte-sd-absente/transformes/" + nom,
                    false,
                    idSession));
        }
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage
    /// ([fr.univ_amu.iut.commun.outils.CablageInjecteursCaptureTest]).
    public static Injector creerInjecteur() {
        // Le service et son ViewModel viennent de `AuditModule`, avec leurs collaborateurs optionnels
        // réels (`VerificationDepot`, `AuditPointsServeur`) que la substitution locale forçait à
        // `Optional.empty()`.
        //
        // ⚠️ L'aperçu ne change pas pour autant : vérifié image contre image, les mêmes deux écarts
        // s'affichent. La nuit semée ici n'exerce simplement aucune des deux sources. La substitution
        // n'amputait donc rien de VISIBLE - elle amputait la possibilité de le voir un jour.
        return Guice.createInjector(
                Modules.override(RacineInjecteur.modules()).with(ModuleCaptureCommun.executeursSynchrones()));
    }
}
