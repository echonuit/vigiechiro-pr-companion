package fr.univ_amu.iut.connexion.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.EchelleProgression;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.view.SuiviProgression;
import fr.univ_amu.iut.connexion.view.NavigationConnexion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture la **modale « Connexion VigieChiro »** (#727) en **trois états** : `apercu-connexion.png`
/// (« non connecté », bandeau masqué), `apercu-connexion-bandeau.png` (bandeau de statut affiché) et
/// `apercu-connexion-progression.png` (la modale de progression que la connexion ouvre depuis #2558).
/// Le workspace pointe sur un dossier temporaire vierge, donc aucun token n'est stocké et aucun appel
/// réseau n'a lieu. Le second état documente le cas où les boutons du bas débordaient (#1534) : sans
/// capture, la revue visuelle ne pouvait pas l'attraper.
///
/// Charge le vrai `ConnexionModale.fxml` avec la `controllerFactory` Guice (socle + persistence +
/// connexion), puis rend la scène hors-écran via [ApercuFx]. Lancement headless :
/// `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureConnexion {

    private static final String APERCU_ECRIT = "Apercu ecrit dans ";
    private static final String FXML_MODALE = "ConnexionModale.fxml";

    private CaptureConnexion() {}

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
        Path workspace = Files.createTempDirectory("vc-capture-connexion");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        FXMLLoader loader = new FXMLLoader(NavigationConnexion.class.getResource(FXML_MODALE));
        loader.setControllerFactory(creerInjecteur()::getInstance);
        Parent vue = loader.load();
        Path fichier = sortie.resolve("apercu-connexion.png");
        ApercuFx.enregistrerPng(new Scene(vue), fichier);
        System.out.println(APERCU_ECRIT + fichier.toAbsolutePath());
        capturerAvecBandeau(sortie);
        capturerProgression(sortie);
    }

    /// Troisième état : la connexion **en cours de récupération** (#2558, #2642).
    ///
    /// Se connecter n'est plus instantané : depuis #2554, la connexion rejoue les rapprocheurs, et l'un
    /// d'eux rapatrie les nuits du compte avec leur contenu. L'opération dure, elle s'annonce donc, et elle
    /// se laisse **interrompre** - ce que le seul bandeau « Vérification en cours… » ne faisait pas.
    ///
    /// La barre est capturée **dans la modale**, à sa place réelle. Elle s'y greffe depuis #2642 : ouvrir
    /// une seconde fenêtre par-dessus celle-ci montrait deux fenêtres pour un seul geste, et aucune image
    /// ne pouvait en rendre compte - un aperçu rend une scène, pas une pile de fenêtres. Maintenant qu'il
    /// n'y a plus qu'une scène, la capture dit tout.
    ///
    /// L'étape montrée est celle qu'émet réellement le balayage (`ExecutionParallele`, libellé « Nuits
    /// k/N ») et sa fraction vient de la vraie échelle, pas d'un nombre choisi pour l'image.
    private static void capturerProgression(Path sortie) throws IOException {
        FXMLLoader loader = new FXMLLoader(NavigationConnexion.class.getResource(FXML_MODALE));
        loader.setControllerFactory(creerInjecteur()::getInstance);
        Parent vue = loader.load();
        VBox contenu = SuiviProgression.apercu(
                "Connexion à Vigie-Chiro",
                new Progression("Nuits 7/12", EchelleProgression.autonome(12).fraction(7)));
        // On greffe le VRAI contenu du socle dans la VRAIE zone d'accueil, exactement comme le fait
        // PanneauProgression en production. Le skin du conteneur n'existe qu'après une passe de layout.
        vue.applyCss();
        vue.layout();
        StackPane zone = (StackPane) vue.lookup("#zoneProgression");
        zone.getChildren().setAll(contenu);
        zone.setVisible(true);
        zone.setManaged(true);
        Path fichier = sortie.resolve("apercu-connexion-progression.png");
        ApercuFx.enregistrerPng(new Scene(vue), fichier);
        System.out.println(APERCU_ECRIT + fichier);
    }

    /// Second état : le bandeau de statut affiché. C'est là que le bug #1534 se voyait - les boutons du
    /// bas poussés hors du cadre - et qu'AUCUNE capture ne le montrait, donc que la revue visuelle ne
    /// pouvait pas l'attraper.
    ///
    /// On recharge une vue **indépendante** (un noeud ne vit que dans une scène : réutiliser la première
    /// lèverait « already set as root of another scene »), on déclenche la VRAIE action « Se connecter »
    /// sans token - le code de production, pas un fac-similé (ADR 0025) - qui pose un bandeau d'invite
    /// **sans toucher au presse-papier**, puis on crée la scène **après** : elle se dimensionne alors sur
    /// une préférence qui inclut déjà le bandeau, et le PNG loge le bandeau ET les boutons.
    private static void capturerAvecBandeau(Path sortie) throws IOException {
        FXMLLoader loader = new FXMLLoader(NavigationConnexion.class.getResource(FXML_MODALE));
        loader.setControllerFactory(creerInjecteur()::getInstance);
        Parent vue = loader.load();
        ((Button) vue.lookup("#boutonConnecter")).fire();
        Path avecBandeau = sortie.resolve("apercu-connexion-bandeau.png");
        ApercuFx.enregistrerPng(new Scene(vue), avecBandeau);
        System.out.println(APERCU_ECRIT + avecBandeau.toAbsolutePath());
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage (test).
    public static Injector creerInjecteur() {
        return Guice.createInjector(
                Modules.override(RacineInjecteur.modules()).with(ModuleCaptureCommun.executeursSynchrones()));
    }
}
