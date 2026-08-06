package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Toute fenêtre de l'application passe-t-elle par [Habillage] ? (#3374)
///
/// ## Le défaut que ce garde empêche de revenir
///
/// `base.css` était déclarée à la main dans deux FXML sur des dizaines. La fenêtre principale la
/// portait ; les **dix autres** - modales de point, de site, de rattachement, de connexion, de
/// qualification, dialogues de progression - naissaient d'un `new Scene(vue)` et rendaient avec la
/// police par défaut de JavaFX, différente de celle de la fenêtre qui les portait et différente d'une
/// machine à l'autre.
///
/// Le correctif est un point de passage unique. Sans garde, il tient jusqu'à la onzième fenêtre :
/// `new Scene(...)` reste la façon évidente d'en écrire une, et rien ne signale l'oubli - la fenêtre
/// s'ouvre, simplement pas habillée.
///
/// ## Ce que ce garde vérifie vraiment
///
/// Il lit les **sources**, pas le comportement : un test d'intégration devrait ouvrir chacune des
/// fenêtres, ce que la moitié refuse en headless. La contrepartie est assumée, et c'est le même choix
/// que les inventaires de `cli-surface.bats` : mieux vaut un inventaire vérifiable qu'une couverture
/// qu'on n'écrira pas.
class ScenesHabilleesTest {

    /// Le seul endroit qui fabrique une scène de fenêtre.
    private static final String FABRIQUE = "commun/view/Habillage.java";

    @Test
    @DisplayName("#3374 : aucune fenêtre de l'application ne se construit hors de Habillage")
    void toutes_les_scenes_passent_par_habillage() throws IOException {
        List<String> coupables;
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            coupables = sources.filter(chemin -> chemin.toString().endsWith(".java"))
                    .filter(chemin -> !normaliser(chemin).endsWith(FABRIQUE))
                    .filter(ScenesHabilleesTest::construitUneScene)
                    // Les 35 outils de capture construisent légitimement leur scène : ils l'habillent
                    // ensuite, directement ou en la confiant à `ApercuFx`. La dispense est donc
                    // CONDITIONNELLE - un outil qui garderait sa scène pour lui retombe dans la liste,
                    // et c'est précisément le cas qui produirait un aperçu ne montrant pas le produit.
                    // Trois l'ont été à l'écriture de ce garde : ils posaient palette+base à la main,
                    // sans jamais installer la police, donc `base.css` y demandait une famille non
                    // enregistrée.
                    .filter(chemin -> !(normaliser(chemin).contains("/outils/") && habille(chemin)))
                    .map(ScenesHabilleesTest::normaliser)
                    .sorted()
                    .toList();
        }

        assertThat(coupables)
                .as("« new Scene(...) » hors de Habillage : cette fenêtre s'ouvrira sans la police du "
                        + "produit ni ses feuilles de socle, donc avec un rendu dépendant de la machine, "
                        + "et rien ne le signalera. Utiliser Habillage.scene(racine) (#3374)")
                .isEmpty();
    }

    @Test
    @DisplayName("#3374 : le garde saurait voir un « new Scene » - il en trouve un dans Habillage lui-même")
    void le_garde_detecte_bien_ce_qu_il_cherche() {
        // Sans cette vérification, le test ci-dessus resterait vert si le motif cherché ne correspondait
        // plus à rien (une reformulation, un chemin de sources déplacé) : il certifierait une absence
        // qu'il ne sait plus constater.
        assertThat(construitUneScene(Path.of("src/main/java/fr/univ_amu/iut/" + FABRIQUE)))
                .as("Habillage construit bien des scènes : si le garde ne l'y voit pas, il ne voit rien")
                .isTrue();
    }

    private static boolean construitUneScene(Path source) {
        return lire(source).contains("new Scene(");
    }

    /// L'outil habille-t-il sa scène - lui-même, ou en la confiant à `ApercuFx` qui le fait ?
    private static boolean habille(Path source) {
        String code = lire(source);
        return code.contains("Habillage.") || code.contains("ApercuFx.");
    }

    private static String normaliser(Path source) {
        return source.toString().replace('\\', '/').replace("src/main/java/fr/univ_amu/iut/", "");
    }

    private static String lire(Path source) {
        try {
            return Files.readString(source);
        } catch (IOException echec) {
            throw new java.io.UncheckedIOException(echec);
        }
    }
}
