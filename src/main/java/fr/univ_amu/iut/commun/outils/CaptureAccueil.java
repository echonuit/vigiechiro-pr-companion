package fr.univ_amu.iut.commun.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.JournalMutations;
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

    /// En-tete partagee des trois points semes : PMD refuse le meme litteral trois fois.
    private static final String POINT = "INSERT INTO listening_point (code, gps_lat, gps_lon, site_id)";

    private static void capturer() throws IOException {
        Path workspace = Files.createTempDirectory("vc-capture-accueil");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = creerInjecteur();

        // Le rendu de l'accueil interroge les compteurs du tableau de bord (#141) : on migre donc le
        // schema pour que les tables existent (compteurs a 0 sur une base neuve, bandeau masque).
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        Parent chrome = chargerFxml(injecteur, CHROME);
        // Largeur suffisante pour poser les **deux sections de prismes** côte à côte (Collecte & passages /
        // Espèces & biodiversité) ; hauteur ajustée pour ne pas rogner les cartes (l'app réelle défile).
        ApercuFx.enregistrerPng(new Scene(chrome, 1180, 760), sortie.resolve("apercu-accueil.png"));

        // Second etat : le bandeau de compteurs RENSEIGNE (#3537, passe 8 de la cloture du lot 1).
        // Aucune capture ne le montrait, alors que c'est ce que le lot a rendu vivant.
        semerDeuxSitesEtLeursPoints(source);
        // L'outil annonce sa mutation comme n'importe quel ecrivain (#3541) : `AccueilViewModel` est un
        // SINGLETON qui a lu les compteurs sur base vide, et recharger le FXML lui rend le meme objet.
        // Sans cette ligne, les deux apercus sortent identiques - ce qu'un `md5sum` a montre.
        injecteur.getInstance(JournalMutations.class).mutationStructurelleValidee();
        Parent chromePeuple = chargerFxml(injecteur, CHROME);
        ApercuFx.enregistrerPng(new Scene(chromePeuple, 1180, 760), sortie.resolve("apercu-accueil-compteurs.png"));

        System.out.println("Apercu d'accueil ecrit dans " + sortie.toAbsolutePath());
    }

    /// Seme deux sites et leurs points, **en SQL**, pour que le bandeau de compteurs ait quelque chose
    /// a montrer.
    ///
    /// En SQL et non par les DAO : ceux-ci vivent dans la feature `sites`, et un outil de `commun` qui
    /// les importerait coupleraient le socle a une feature - ce que l'ArchitectureTest interdit (slices
    /// acycliques). Le SQL, lui, ne cite aucune classe de feature.
    ///
    /// Deux compteurs sur quatre restent a zero, et c'est **voulu** : l'apercu montre du meme coup
    /// l'etat attenue d'une pastille vide (classe `indicateur-vide`), que rien n'illustrait non plus.
    private static void semerDeuxSitesEtLeursPoints(SourceDeDonnees source) {
        executer(
                source,
                "INSERT INTO user (local_id, display_name) VALUES ('u-apercu', 'Observatrice')",
                "INSERT INTO monitoring_site (square_number, friendly_name, protocol, created_at, user_id)"
                        + " VALUES ('640380', 'Etang de la Tuiliere', 'STANDARD', '2026-05-31', 'u-apercu')",
                "INSERT INTO monitoring_site (square_number, friendly_name, protocol, created_at, user_id)"
                        + " VALUES ('840962', 'Plateau de Sault', 'STANDARD', '2026-05-31', 'u-apercu')",
                POINT + " VALUES ('A1', 43.4010, -1.5740, 1)",
                POINT + " VALUES ('B2', 43.4040, -1.5470, 1)",
                POINT + " VALUES ('C1', 44.0910, 5.4120, 2)");
    }

    private static void executer(SourceDeDonnees source, String... ordres) {
        try (java.sql.Connection cx = source.getConnection();
                java.sql.Statement st = cx.createStatement()) {
            for (String ordre : ordres) {
                st.executeUpdate(ordre);
            }
        } catch (java.sql.SQLException echec) {
            throw new IllegalStateException("Semis de l'apercu d'accueil impossible", echec);
        }
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
