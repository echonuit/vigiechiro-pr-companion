package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Garde-fou « le FXML se charge ». Pour CHAQUE fichier `.fxml` du projet, on rejoue exactement le
/// chargement effectué par la CI **« Aperçus des vues »** (`.github/workflows/capture-vues.yml` →
/// `capture-screenshots.sh` → les `outils.CaptureXxx`) : `FXMLLoader.load()` avec la
/// `controllerFactory` Guice, sur une base SQLite jetable migrée. Si le chargement échoue, le test
/// nomme le fichier et la cause racine, et propose une checklist d'erreurs FXML courantes.
///
/// Intérêt : la capture des aperçus tourne sur `main`, APRÈS le merge. Ce test, lui, tourne dans la
/// CI Maven sur CHAQUE pull request : un FXML cassé est signalé tôt, avec un message clair, AVANT
/// que la galerie d'aperçus ne casse.
///
/// On ne seede aucune donnée d'écran : comme dans les `CaptureXxx`, le `load()` précède toute
/// alimentation (la navigation / les `demarrerXxx` poussent les données ensuite). On insère
/// seulement un utilisateur courant, car certains ViewModels le résolvent à la construction.
@ExtendWith(ApplicationExtension.class)
class ChargementFxmlTest {

    /// Racine des sources : les `.fxml` vivent à côté de leur controller dans `…/view/`
    /// (convention TP3, cf. `<resources>` du `pom.xml`).
    private static final Path SOURCE = Path.of("src", "main", "java");

    private Injector injecteur;

    @Start
    void demarrer(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-fxml-smoke");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = RacineInjecteur.creer();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        // Utilisateur courant minimal (cf. l'état « vide » de CaptureEcrans) : aucune autre donnée
        // d'écran, on veut juste charger les FXML.
        new UtilisateurDao(source).insert(new Utilisateur("u-smoke", "Smoke test"));
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    /// Tous les `.fxml` du projet, en chemins de ressource classpath
    /// (`/fr/univ_amu/iut/…/view/Xxx.fxml`). Statique : exigé par `@MethodSource` en cycle de vie
    /// par défaut (PER_METHOD).
    static Stream<String> fichiersFxml() throws Exception {
        try (Stream<Path> chemins = Files.walk(SOURCE)) {
            return chemins
                    .filter(p -> p.toString().endsWith(".fxml"))
                    .map(p -> "/" + SOURCE.relativize(p).toString().replace(File.separatorChar, '/'))
                    .sorted()
                    .toList()
                    .stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fichiersFxml")
    @DisplayName("Chaque FXML se charge sans erreur (comme la CI de capture)")
    void chaque_fxml_se_charge(String ressource) {
        URL url = getClass().getResource(ressource);
        assertThat(url)
                .as(
                        "FXML introuvable sur le classpath : %s "
                                + "(les .fxml de src/main/java sont-ils bien copiés dans target/classes ? cf. <resources> du pom)",
                        ressource)
                .isNotNull();

        try {
            WaitForAsyncUtils.waitForAsyncFx(15_000, () -> {
                FXMLLoader loader = new FXMLLoader(url);
                loader.setControllerFactory(injecteur::getInstance);
                return loader.load();
            });
        } catch (Throwable echec) {
            signalerEchec(ressource, echec);
        }
    }

    @Test
    @DisplayName("Le balayage trouve bien les FXML (garde-fou anti-faux-vert)")
    void le_balayage_trouve_des_fxml() throws Exception {
        assertThat(fichiersFxml().count())
                .as(
                        "Aucun (ou trop peu de) FXML trouvé sous %s : le test tourne-t-il depuis la racine du module ?",
                        SOURCE)
                .isGreaterThanOrEqualTo(10);
    }

    /// Transforme l'exception de chargement en message exploitable par un BUT1 : un échec qui
    /// provient seulement d'un ViewModel pas encore implémenté (`UnsupportedOperationException`
    /// appelé depuis `initialize()`) n'est PAS une erreur FXML → le test est neutralisé (skip).
    /// Tout le reste est une vraie erreur de structure FXML et fait échouer le test.
    private static void signalerEchec(String ressource, Throwable echec) {
        List<Throwable> chaine = new ArrayList<>();
        for (Throwable t = echec; t != null && !chaine.contains(t); t = t.getCause()) {
            chaine.add(t);
        }
        Throwable racine = chaine.get(chaine.size() - 1);

        boolean viewModelNonImplemente = chaine.stream().anyMatch(t -> t instanceof UnsupportedOperationException);
        if (viewModelNonImplemente) {
            Assumptions.abort("FXML « " + ressource + " » structurellement chargeable, mais une méthode du ViewModel "
                    + "n'est pas encore implémentée (UnsupportedOperationException appelée depuis initialize()). "
                    + "Rien à corriger côté FXML : ce test repassera au vert une fois le ViewModel codé.");
        }

        String message = "❌ Le fichier FXML « " + ressource + " » n'a pas pu être chargé.\n\n"
                + "   Cause racine : " + racine.getClass().getSimpleName()
                + (racine.getMessage() != null ? " : " + racine.getMessage() : "") + "\n\n"
                + "   Pistes selon le message ci-dessus :\n"
                + "   • ClassNotFoundException / « cannot be resolved » → un <?import ...?> manque ou est mal orthographié,\n"
                + "     ou fx:controller=\"...\" pointe vers une classe inexistante.\n"
                + "   • SAXParseException / « content is not allowed » → XML mal formé (balise non fermée, guillemet manquant).\n"
                + "   • « Error resolving onXxx='#methode' » → un attribut onAction/on... vise une méthode absente du controller.\n"
                + "   • NullPointerException dans initialize() → un fx:id ne correspond à aucun champ @FXML (le champ est resté null).\n\n"
                + "   Ce test rejoue le chargement de la CI « Aperçus des vues » (capture) mais sur ta PR :\n"
                + "   corrige-le ici et la capture passera.";
        fail(message, echec);
    }
}
