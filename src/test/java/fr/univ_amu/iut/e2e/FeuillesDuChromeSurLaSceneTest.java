package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.audio.view.SonsValidationController;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.EcranReglagesController;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.NavigationReglages;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.importation.view.ImportationController;
import fr.univ_amu.iut.importation.view.NavigationImportation;
import fr.univ_amu.iut.lot.view.LotController;
import fr.univ_amu.iut.lot.view.NavigationLot;
import fr.univ_amu.iut.multisite.view.NavigationMultisite;
import fr.univ_amu.iut.qualification.view.NavigationQualification;
import fr.univ_amu.iut.qualification.view.QualificationController;
import fr.univ_amu.iut.recette.SansEcritureHorsDuFil;
import fr.univ_amu.iut.sites.view.MesSitesController;
import fr.univ_amu.iut.sites.view.NavigationSites;
import fr.univ_amu.iut.sites.view.SiteDetailController;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Les feuilles du chrome vivent sur la scène, et un écran accueilli ne les redéclare pas (#6207).
///
/// JavaFX enregistre, pour chaque feuille déclarée sur un parent, tous les parents qu'il contient, et
/// parcourt cette liste à chaque parent neuf qu'il style. Déclarées sur la racine du chrome et sur
/// celle de chaque écran, `palette.css`, `base.css` et `design.css` faisaient donc payer chaque
/// ouverture selon le carré du nombre de nœuds. Une feuille de scène ne passe pas par ce chemin.
///
/// Ce test ouvre par la navigation les écrans de [EcransSuivantUnSingleton#ECRANS] et la vue
/// multisite, et constate la forme : rien sur la racine, le trio sur la scène, et sur la vue sa seule
/// feuille de fonctionnalité.
@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})
class FeuillesDuChromeSurLaSceneTest {

    private static final String CARRE = "640380";
    private static final String POINT = "A1";

    /// JUnit crée ce répertoire et le **supprime** en fin de test (#4888).
    @TempDir
    private Path dossierTemporaire;

    private Injector injecteur;
    private JeuDeDonneesPassage jeu;

    @Start
    void start(Stage stage) throws Exception {
        System.setProperty("vigiechiro.workspace", dossierTemporaire.toString());
        injecteur = Guice.createInjector(RacineInjecteur.modules());
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        jeu = JeuDeDonneesPassage.dans(source)
                .utilisateur("u-1")
                .carre(CARRE)
                .nomSite("Étang")
                .point(POINT)
                .position(43.52, 5.46)
                .semer();
        jeu.ajouterObservation("Pippip");

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(DiagnosticGuice.pour(injecteur));
        Parent racine = loader.load();
        FenetreAjustable.poser(stage, racine, 1100, 760);
        FenetreAjustable.afficher(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    void le_chrome_porte_ses_feuilles_sur_la_scene_et_un_ecran_ne_les_redeclare_pas() throws Exception {
        Map<Class<?>, Consumer<Injector>> ecrans = new LinkedHashMap<>(ouvertures());
        ecrans.put(
                NavigationMultisite.class,
                inj -> inj.getInstance(NavigationMultisite.class).ouvrirAccueil());

        for (Map.Entry<Class<?>, Consumer<Injector>> ecran : ecrans.entrySet()) {
            String nom = ecran.getKey().getSimpleName();
            surLeFil(() -> ecran.getValue().accept(injecteur));
            List<List<String>> feuilles = WaitForAsyncUtils.asyncFx(() -> {
                        Parent vue = injecteur
                                .getInstance(Navigateur.class)
                                .historique()
                                .getLast()
                                .vue();
                        Scene scene = vue.getScene();
                        return List.of(
                                noms(scene.getStylesheets()),
                                noms(scene.getRoot().getStylesheets()),
                                noms(vue.getStylesheets()));
                    })
                    .get(10, TimeUnit.SECONDS);

            assertThat(feuilles.get(0))
                    .as("%s : la scène porte le trio du chrome, dans son ordre", nom)
                    .containsExactly("palette.css", "base.css", "design.css");
            assertThat(feuilles.get(1))
                    .as("%s : la racine du chrome ne déclare plus de feuille", nom)
                    .isEmpty();
            assertThat(feuilles.get(2))
                    .as("%s : la vue garde sa feuille de fonctionnalité, et elle seule", nom)
                    .hasSize(1)
                    .doesNotContain("palette.css", "base.css", "design.css");
        }
    }

    private static List<String> noms(List<String> urls) {
        return urls.stream().map(url -> url.substring(url.lastIndexOf('/') + 1)).collect(Collectors.toList());
    }

    /// Chaque écran de l'inventaire, avec le geste de navigation qui l'ouvre.
    private Map<Class<?>, Consumer<Injector>> ouvertures() {
        ContextePassage passage = new ContextePassage(
                jeu.idPassage(), jeu.lePassage().numeroPassage(), new ContexteSite(CARRE, POINT, null));
        Map<Class<?>, Consumer<Injector>> ouvertures = new LinkedHashMap<>();
        ouvertures.put(
                MesSitesController.class,
                inj -> inj.getInstance(NavigationSites.class).ouvrirAccueil());
        ouvertures.put(
                SiteDetailController.class,
                inj -> inj.getInstance(NavigationSites.class).ouvrirDetail(jeu.leSite()));
        ouvertures.put(
                EcranReglagesController.class,
                inj -> inj.getInstance(NavigationReglages.class).ouvrir());
        ouvertures.put(
                ImportationController.class,
                inj -> inj.getInstance(NavigationImportation.class).ouvrir());
        ouvertures.put(
                SonsValidationController.class,
                inj -> inj.getInstance(OuvrirAudio.class).ouvrir(new SourceObservations.ParPassage(passage)));
        ouvertures.put(
                QualificationController.class,
                inj -> inj.getInstance(NavigationQualification.class).ouvrir(passage));
        ouvertures.put(
                LotController.class, inj -> inj.getInstance(NavigationLot.class).ouvrir(passage));
        return ouvertures;
    }

    private static void surLeFil(Runnable geste) throws Exception {
        WaitForAsyncUtils.asyncFx(geste).get(10, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
