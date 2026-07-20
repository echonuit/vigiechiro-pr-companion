package fr.univ_amu.iut.commun.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.AnnonceChrome;
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

/// Aperçu du **bandeau d'annonce** du chrome, dans son état visible (#2109).
///
/// Cet état n'apparaît que lorsqu'une version plus récente est publiée : sur une base neuve et sans
/// réseau, la galerie ne le montrerait donc **jamais**. Or c'est précisément l'état qu'il faut
/// regarder - un message tronqué ou un lien illisible ne se voit pas autrement.
///
/// L'annonce est **injectée** plutôt que rendue par un appel réseau : la capture doit être
/// déterministe et ne rien dépendre de ce que GitHub publie le jour où elle tourne. Le code
/// d'affichage exercé, lui, est bien celui de production ([BandeauAnnonce] via `MainView.fxml`) - on
/// substitue la **source** du message, jamais son rendu (ADR 0025).
public final class CaptureBandeauAnnonce {

    private static final String CHROME = "/fr/univ_amu/iut/commun/view/MainView.fxml";

    /// Une annonce représentative : deux versions nommées, et un lien. Les numéros sont fictifs et
    /// figés, pour que l'aperçu ne change pas à chaque release.
    private static final AnnonceChrome ANNONCE_FIGEE = () -> Optional.of(new AnnonceChrome.Annonce(
            "La version 2.23.0 est disponible (vous utilisez la 2.21.3).",
            "Voir cette version",
            "https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/releases/latest"));

    private CaptureBandeauAnnonce() {}

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
        Throwable probleme = erreur.get();
        if (probleme != null) {
            probleme.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void capturer() throws IOException {
        Path workspace = Files.createTempDirectory("vc-capture-annonce");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = creerInjecteur();
        new MigrationSchema(injecteur.getInstance(SourceDeDonnees.class)).migrer();

        FXMLLoader loader = new FXMLLoader(CaptureBandeauAnnonce.class.getResource(CHROME));
        loader.setControllerFactory(injecteur::getInstance);
        Parent chrome = loader.load();

        ApercuFx.enregistrerPng(new Scene(chrome, 1180, 420), sortie.resolve("apercu-annonce-maj.png"));
        System.out.println("Apercu du bandeau d'annonce ecrit dans " + sortie.toAbsolutePath());
    }

    /// L'injecteur applicatif, dont l'ensemble des [AnnonceChrome] est **remplacé** par une annonce
    /// figée : la feature `maj` peut être active ou non, l'aperçu ne dépend pas d'elle.
    ///
    /// Exposé pour le garde-fou de câblage (`CablageInjecteursCaptureTest`) : un injecteur de
    /// capture auquel il manque une liaison ne casse ni la compilation ni aucun test, et ne se
    /// voit qu'au rendu - donc en CI, sur le dos du push suivant.
    public static Injector creerInjecteur() {
        Module annonceFigee = new AbstractModule() {
            @Override
            protected void configure() {
                Multibinder.newSetBinder(binder(), AnnonceChrome.class)
                        .addBinding()
                        .toInstance(ANNONCE_FIGEE);
            }
        };
        return Guice.createInjector(Modules.override(RacineInjecteur.modules())
                .with(ModuleCaptureCommun.executeursSynchrones(), annonceFigee));
    }
}
