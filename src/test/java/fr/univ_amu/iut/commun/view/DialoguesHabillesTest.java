package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Un `Alert` construit **est habillé** (#1499).
///
/// ## Le défaut
///
/// Un `Alert` vit dans sa **propre scène** : il n'hérite pas des feuilles de la fenêtre qui l'ouvre.
/// La confirmation d'écrasement d'un import s'affichait donc avec le rendu **par défaut** de JavaFX -
/// titre doublé dans la barre de fenêtre, icône « ? » système, boutons gris - au milieu d'une
/// application entièrement habillée. Relevé en session S2 de la recette.
///
/// Deux classes posaient déjà les feuilles à la main, **chacune sa copie de la même boucle** ; les
/// autres ne les posaient pas du tout. C'est la forme du défaut, pas son symptôme : une consigne
/// recopiée se défait au fichier suivant.
///
/// ## Pourquoi un garde de source
///
/// Décalqué de [ScenesHabilleesTest] (#3374), pour la même raison : `new Alert(...)` reste la façon
/// évidente d'ouvrir une confirmation, et **rien ne signale l'oubli** - le dialogue s'ouvre, il est
/// juste laid. Aucun test d'intégration ne rougit pour un habillage manquant.
class DialoguesHabillesTest {

    /// La fabrique légitime : elle **est** le point de passage, donc elle s'exempte elle-même.
    private static final String FABRIQUE = "commun/view/Habillage.java";

    /// Ce qu'on cherche : une construction de dialogue, et le geste qui doit la suivre.
    private static final String CONSTRUCTION = "new Alert(";

    /// Compter `Habillage.poser(` tout court **ne suffit pas** : il existe une surcharge qui prend
    /// une `Scene`, et habiller la scene hote d un dialogue ne pose RIEN sur son panneau. Une premiere
    /// version de ce garde s y est laissee prendre - `ApercuFx` n appelait que la variante `Scene`, le
    /// garde etait vert, et la capture montrait encore l icone systeme.
    ///
    /// On exige donc un appel dont l argument **est** un panneau de dialogue. `ApercuFx`
    /// `enregistrerDialogPane` compte aussi : c est un point de passage, qui habille le panneau qu on
    /// lui confie - un outil de capture qui le lui remet a donc fait le necessaire.
    private static final Pattern HABILLAGE =
            Pattern.compile("Habillage\\.poser\\(\\s*(?:[\\w.]*getDialogPane\\(\\)|\\w*[Pp]ane\\w*)\\s*\\)"
                    + "|enregistrerDialogPane\\(");

    @Test
    @DisplayName("#1499 : chaque Alert construit est habillé, pas seulement le premier du fichier")
    void chaqueAlerteEstHabillee() {
        // Compter, et non chercher une présence : une première version de ce garde se contentait de
        // « le fichier contient au moins un Habillage.poser ». `NotificationDialogue` construisait DEUX
        // dialogues et n'en habillait qu'un : le garde aurait été vert sur un défaut réel.
        List<String> coupables = sources()
                .filter(source -> occurrences(lire(source), CONSTRUCTION) > occurrences(lire(source), HABILLAGE))
                .map(Path::toString)
                .sorted()
                .toList();

        assertThat(coupables)
                .as("« new Alert(...) » sans son « Habillage.poser(...) » : ce dialogue s'ouvrira avec "
                        + "le rendu par défaut de JavaFX, au milieu d'une application habillée")
                .isEmpty();
    }

    @Test
    @DisplayName("#1499 : le garde saurait voir un Alert nu, y compris le second d'un fichier")
    void leGardeSaitEncoreVoir() {
        // Sans ce cas, le test ci-dessus resterait vert le jour où sa détection cesse de fonctionner :
        // un garde qui ne détecte plus est vert, et c'est le seul défaut qui se présente en succès.
        String nu = "Alert a = new Alert(AlertType.CONFIRMATION);";
        String habille = nu + "\nHabillage.poser(a.getDialogPane());";
        String sceneSeule = nu + "\nHabillage.poser(scene);";
        String deuxDontUnNu = habille + "\n" + nu;

        assertThat(occurrences(nu, CONSTRUCTION) > occurrences(nu, HABILLAGE)).isTrue();
        assertThat(occurrences(habille, CONSTRUCTION) > occurrences(habille, HABILLAGE))
                .isFalse();
        assertThat(occurrences(deuxDontUnNu, CONSTRUCTION) > occurrences(deuxDontUnNu, HABILLAGE))
                .as("le second dialogue d'un fichier compte autant que le premier")
                .isTrue();
        assertThat(occurrences(sceneSeule, CONSTRUCTION) > occurrences(sceneSeule, HABILLAGE))
                .as("habiller la scène hôte ne pose rien sur le panneau du dialogue")
                .isTrue();
    }

    private static int occurrences(String texte, String motif) {
        int compte = 0;
        for (int i = texte.indexOf(motif); i >= 0; i = texte.indexOf(motif, i + motif.length())) {
            compte++;
        }
        return compte;
    }

    private static int occurrences(String texte, Pattern motif) {
        return (int) motif.matcher(texte).results().count();
    }

    private static Stream<Path> sources() {
        try {
            return Files.walk(Path.of("src/main/java"))
                    .filter(chemin -> chemin.toString().endsWith(".java"))
                    .filter(chemin -> !chemin.toString().replace('\\', '/').endsWith(FABRIQUE));
        } catch (IOException probleme) {
            throw new UncheckedIOException(probleme);
        }
    }

    private static String lire(Path source) {
        try {
            return Files.readString(source);
        } catch (IOException probleme) {
            throw new UncheckedIOException(probleme);
        }
    }
}
