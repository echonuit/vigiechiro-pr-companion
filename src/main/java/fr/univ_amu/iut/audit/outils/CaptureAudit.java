package fr.univ_amu.iut.audit.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.audit.model.ServiceAuditCoherence;
import fr.univ_amu.iut.audit.view.AuditController;
import fr.univ_amu.iut.audit.viewmodel.AuditViewModel;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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
        new MigrationSchema(injecteur.getInstance(SourceDeDonnees.class)).migrer();

        FXMLLoader loader = new FXMLLoader(AuditController.class.getResource(FXML_AUDIT));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        ApercuFx.enregistrerPng(new Scene(vue, 1080, 640), sortie.resolve("apercu-audit.png"));

        System.out.println("Apercu ecrit dans " + sortie.toAbsolutePath());
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage
    /// ([fr.univ_amu.iut.commun.outils.CablageInjecteursCaptureTest]).
    public static Injector creerInjecteur() {
        return Guice.createInjector(
                ModuleCaptureCommun.communSynchrone(), new PersistenceModule(), new AbstractModule() {
                    @Provides
                    ServiceAuditCoherence fournirService(SourceDeDonnees source, Workspace workspace) {
                        return new ServiceAuditCoherence(source, workspace, Optional.empty(), Optional.empty());
                    }

                    @Provides
                    AuditViewModel fournirViewModel(ServiceAuditCoherence service) {
                        return new AuditViewModel(service);
                    }

                    // Navigation « du constat au passage qu'il accuse » (#1338) : inerte ici, la capture
                    // ne navigue pas. Mais sans cette liaison, l'injecteur partiel ne sait plus fournir
                    // AuditController, et la capture échoue au chargement du FXML.
                    @Provides
                    OuvrirPassage fournirOuvrirPassage() {
                        return (idPassage, contexte) -> {};
                    }
                });
    }
}
