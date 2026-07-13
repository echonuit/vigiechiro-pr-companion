package fr.univ_amu.iut.commun.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
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
/// Capture l'ecran d'accueil (chrome principal `MainView.fxml` + cartes d'activites) en PNG, pour
/// le comparer a la maquette du brief. Contrairement aux `CaptureEcrans` / `CaptureImport` des
/// features, l'accueil appartient au socle `commun` : il agrege les
// [fr.univ_amu.iut.commun.view.ActiviteAccueil]
/// publiees par toutes les features. On utilise donc l'injecteur applicatif complet
/// ([RacineInjecteur#creer()]) afin que **toutes** les cartes soient presentes.
///
/// Demarche : workspace SQLite jetable (le rendu de l'accueil ne touche pas la base, mais on evite
/// d'ecrire dans le workspace reel), chargement du chrome via la `controllerFactory` Guice du
/// `FXMLLoader` (le `MainController` peuple les cartes a l'initialisation), puis rendu hors-ecran
/// par [ApercuFx] dans `.github/assets/`.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureAccueil {

    private static final String CHROME = "/fr/univ_amu/iut/commun/view/MainView.fxml";

    private CaptureAccueil() {}

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
        Path workspace = Files.createTempDirectory("vc-capture-accueil");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = creerInjecteur();

        // Le rendu de l'accueil interroge les compteurs du tableau de bord (#141) : on migre donc le
        // schema pour que les tables existent (compteurs a 0 sur une base neuve, bandeau masque).
        // On ne seme PAS de donnees ici : ce serait coupler le socle a une feature (sites), ce que
        // l'ArchitectureTest interdit (slices acycliques). L'apercu illustre donc l'accueil « vierge ».
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        Parent chrome = chargerFxml(injecteur, CHROME);
        // Largeur suffisante pour poser les **deux sections de prismes** côte à côte (Collecte & passages /
        // Espèces & biodiversité) ; hauteur ajustée pour ne pas rogner les cartes (l'app réelle défile).
        ApercuFx.enregistrerPng(new Scene(chrome, 1180, 760), sortie.resolve("apercu-accueil.png"));

        System.out.println("Apercu d'accueil ecrit dans " + sortie.toAbsolutePath());
    }

    private static Parent chargerFxml(Injector injecteur, String chemin) throws IOException {
        FXMLLoader loader = new FXMLLoader(CaptureAccueil.class.getResource(chemin));
        loader.setControllerFactory(injecteur::getInstance);
        return loader.load();
    }

    /// Injecteur applicatif complet dont les exécuteurs hors fil sont surchargés en synchrone (le
    /// snapshot doit voir le contenu chargé, cf. [ModuleCaptureCommun]). Exposé pour le garde-fou de
    /// câblage (test).
    public static Injector creerInjecteur() {
        return Guice.createInjector(
                Modules.override(RacineInjecteur.modules()).with(ModuleCaptureCommun.executeursSynchrones()));
    }
}
