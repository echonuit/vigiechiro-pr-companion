package fr.univ_amu.iut.maj.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.VersionApplication;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.maj.model.DerniereVersionPubliee;
import fr.univ_amu.iut.maj.model.NumeroDeVersion;
import fr.univ_amu.iut.maj.model.VersionDisponible;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/// Aperçu de l'annonce de mise à jour **dans sa variante Windows** (#3876), la plus longue.
///
/// ## Pourquoi cette capture existe, à côté de celle du socle
///
/// [fr.univ_amu.iut.commun.outils.CaptureBandeauAnnonce] montre déjà le bandeau, mais avec un message
/// **figé écrit à la main**, et sans conseil. Depuis #3457, ce n'est plus le seul message possible :
/// sous Windows, l'annonce porte en plus « fermez l'application avant d'installer » et le geste
/// winget, ce qui la fait environ **tripler** de longueur.
///
/// C'est cette variante-là qu'il faut regarder, parce que c'est la seule où le libellé peut enrouler
/// sur deux lignes et pousser la hauteur du bandeau.
///
/// ⚠️ **Elle ne pouvait pas vivre dans le socle**, et la tentative a été faite : `CaptureBandeauAnnonce`
/// est dans `commun`, et lui faire composer son message par `ConseilDeMiseAJour` crée le cycle
/// `commun -> maj -> commun` qu'ArchUnit interdit. Le socle a raison de rester ignorant : son aperçu
/// doit valoir même quand la feature `maj` est désactivée. L'aperçu d'un message appartient donc à la
/// feature qui le **produit**.
///
/// ## Ce qui est bouchonné, et ce qui ne l'est pas
///
/// Le message est rendu par le **vrai** [fr.univ_amu.iut.maj.view.AnnonceMiseAJour], à travers le vrai
/// `BandeauAnnonce` du chrome : on ne substitue que ses **entrées**, jamais son rendu (ADR 0025).
///
/// | Entrée | Pourquoi elle est bouchonnée |
/// | --- | --- |
/// | [DerniereVersionPubliee] | sinon l'aperçu dépendrait de ce que GitHub publie le jour où il tourne. C'est le port
/// prévu pour ça, son doc-comment le dit |
/// | `os.name` | le conseil n'existe que sous Windows, et la CI rend sous Linux |
///
/// ⚠️ **Le système est nommé en dur plutôt que lu**, comme le fuseau et la locale que le script de
/// capture impose déjà : un aperçu dont le contenu dépend du poste qui le rend ne se compare plus d'une
/// campagne à l'autre. La propriété est restaurée après le rendu, pour ne rien laisser derrière.
public final class CaptureAnnonceMiseAJour {

    private static final String CHROME = "/fr/univ_amu/iut/commun/view/MainView.fxml";

    private static final String PROPRIETE_SYSTEME = "os.name";

    /// Le système sous lequel l'aperçu se rend, quel que soit celui qui l'exécute.
    private static final String SYSTEME_FIGE = "Windows 11";

    /// Numéros fictifs et figés, pour que l'aperçu ne change pas à chaque publication.
    private static final String VERSION_DISPONIBLE = "2.23.0";

    /// La version « installée » que l'aperçu affiche.
    ///
    /// ⚠️ **Elle doit être figée, et pas seulement pour le déterminisme.** Hors d'un jar - ce qui est
    /// le cas d'un outil de capture - `VersionApplication.versionEmpaquetee()` rend un `Optional`
    /// vide, et `VerificateurMiseAJour` renonce alors sans rien proposer. Sans cette valeur, l'aperçu
    /// rendait le chrome **sans aucun bandeau** : mesuré en le regardant, pas déduit.
    private static final String VERSION_INSTALLEE = "2.21.3";

    private CaptureAnnonceMiseAJour() {}

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
        Path workspace = Files.createTempDirectory("vc-capture-annonce-maj");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        // ⚠️ La base est migrée AVANT de mentir sur le système, et l'ordre n'est pas cosmétique : le
        // pilote SQLite lit `os.name` pour choisir sa bibliothèque native, et se voir répondre
        // « Windows » sur une machine Linux le fait échouer sur
        // `NativeLibraryNotFoundException: No native library found for os.name=Windows`.
        // Mesuré en écrivant cette classe. La propriété n'est donc posée que le temps du RENDU, une
        // fois la bibliothèque native chargée.
        Injector injecteur = creerInjecteur();
        new MigrationSchema(injecteur.getInstance(SourceDeDonnees.class)).migrer();

        String systemeAvant = System.getProperty(PROPRIETE_SYSTEME);
        try {
            System.setProperty(PROPRIETE_SYSTEME, SYSTEME_FIGE);

            FXMLLoader loader = new FXMLLoader(CaptureAnnonceMiseAJour.class.getResource(CHROME));
            loader.setControllerFactory(injecteur::getInstance);
            Parent chrome = loader.load();

            ApercuFx.enregistrerPng(
                    Habillage.scene(chrome, 1180, 460), sortie.resolve("apercu-annonce-maj-windows.png"));
        } finally {
            // Restauré même si le rendu échoue : cette propriété est globale à la JVM, et le processus
            // de capture en rend parfois plusieurs.
            if (systemeAvant == null) {
                System.clearProperty(PROPRIETE_SYSTEME);
            } else {
                System.setProperty(PROPRIETE_SYSTEME, systemeAvant);
            }
        }
        System.out.println("Apercu de l'annonce de mise a jour ecrit dans " + sortie.toAbsolutePath());
    }

    /// L'injecteur applicatif, dont le seul port **réseau** de la feature `maj` est remplacé par une
    /// version figée. Tout le reste - le vérificateur, l'annonce, le bandeau - est celui de production.
    ///
    /// Exposé pour le garde-fou de câblage (`CablageInjecteursCaptureTest`) : un injecteur de capture
    /// auquel il manque une liaison ne casse ni la compilation ni aucun test, et ne se voit qu'au rendu.
    public static Injector creerInjecteur() {
        Module amontFige = new AbstractModule() {
            @Override
            protected void configure() {
                DerniereVersionPubliee figee = () -> NumeroDeVersion.lire(VERSION_DISPONIBLE)
                        .map(numero -> new VersionDisponible(
                                numero, "https://github.com/echonuit/vigiechiro-pr-companion/releases/latest"));
                bind(DerniereVersionPubliee.class).toInstance(figee);
                bind(VersionApplication.class).toInstance(VersionApplication.figeeA(VERSION_INSTALLEE));
            }
        };
        return Guice.createInjector(Modules.override(RacineInjecteur.modules())
                .with(ModuleCaptureCommun.executeursSynchrones(), amontFige));
    }
}
