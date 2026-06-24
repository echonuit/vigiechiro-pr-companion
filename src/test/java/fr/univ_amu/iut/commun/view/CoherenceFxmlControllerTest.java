package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javafx.fxml.FXML;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/// Garde-fou « le FXML et son controller se correspondent », par **analyse statique** (sans démarrer
/// JavaFX, donc rapide). Pour chaque `.fxml`, on confronte la déclaration au controller pointé par
/// `fx:controller` :
///
///  - chaque champ `@FXML` du controller doit avoir un `fx:id` du même nom dans le FXML : sinon le
///    champ reste `null` et l'écran plante par `NullPointerException` au chargement (typiquement
///    quand l'`fx:id` est mal orthographié) ;
///  - chaque `onAction="#methode"` du FXML doit viser une méthode existante du controller.
///
/// Ce test complète [ChargementFxmlTest] : il ne charge pas le FXML mais donne un message **plus
/// précis** (le nom exact du champ/de la méthode fautif). Si une vue n'a aucun champ `@FXML` ni
/// `onAction`, les ensembles sont vides et le test passe ; il se renforce à mesure que la vue se
/// complète.
///
/// Les commentaires XML sont retirés avant analyse : une déclaration mise en commentaire ne doit
/// pas être confondue avec la vraie.
class CoherenceFxmlControllerTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    private static final Pattern COMMENTAIRE = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern CONTROLLER = Pattern.compile("fx:controller\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern FX_ID = Pattern.compile("fx:id\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern HANDLER = Pattern.compile("\\bon[A-Z][A-Za-z]*\\s*=\\s*\"#([A-Za-z_][A-Za-z0-9_]*)\"");

    static Stream<Path> fichiersFxml() throws Exception {
        try (Stream<Path> chemins = Files.walk(SOURCE)) {
            return chemins.filter(p -> p.toString().endsWith(".fxml")).sorted().toList().stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fichiersFxml")
    @DisplayName("Chaque @FXML a son fx:id et chaque onAction sa méthode")
    void fxml_et_controller_sont_coherents(Path fxml) throws Exception {
        String contenu = COMMENTAIRE.matcher(Files.readString(fxml)).replaceAll("");
        String nom = relatif(fxml);

        Matcher correspondanceControleur = CONTROLLER.matcher(contenu);
        Assumptions.assumeTrue(
                correspondanceControleur.find(),
                "FXML sans fx:controller (chargé via setController) : cohérence non vérifiable ici : " + nom);
        String fqcn = correspondanceControleur.group(1);

        Class<?> controleur;
        try {
            controleur = Class.forName(fqcn);
        } catch (ClassNotFoundException introuvable) {
            throw new AssertionError(
                    "❌ " + nom + " : fx:controller=\"" + fqcn + "\" pointe vers une classe introuvable. "
                            + "Vérifie le nom complet du package et l'orthographe de la classe.",
                    introuvable);
        }

        Set<String> fxIds = extraire(FX_ID, contenu);
        Set<String> handlers = extraire(HANDLER, contenu);
        Set<String> champsFxml = champsAnnotesFxml(controleur);
        Set<String> methodes = nomsDeMethodes(controleur);

        SoftAssertions verifs = new SoftAssertions();
        for (String champ : champsFxml) {
            verifs.assertThat(fxIds)
                    .as(
                            "Le champ @FXML « %s » de %s n'a pas de fx:id=\"%s\" dans %s : le champ restera null "
                                    + "(NullPointerException au chargement). Vérifie l'orthographe de l'fx:id (cf. l'issue de la feature).",
                            champ, controleur.getSimpleName(), champ, nom)
                    .contains(champ);
        }
        for (String handler : handlers) {
            verifs.assertThat(methodes)
                    .as(
                            "Le FXML %s référence onAction=\"#%s\" mais %s n'a pas de méthode « %s » "
                                    + "(le chargement échouera). Vérifie l'orthographe ou ajoute la méthode @FXML.",
                            nom, handler, controleur.getSimpleName(), handler)
                    .contains(handler);
        }
        verifs.assertAll();
    }

    private static Set<String> extraire(Pattern motif, String contenu) {
        Set<String> trouve = new LinkedHashSet<>();
        Matcher m = motif.matcher(contenu);
        while (m.find()) {
            trouve.add(m.group(1));
        }
        return trouve;
    }

    private static Set<String> champsAnnotesFxml(Class<?> type) {
        Set<String> noms = new LinkedHashSet<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (var champ : c.getDeclaredFields()) {
                if (champ.isAnnotationPresent(FXML.class)) {
                    noms.add(champ.getName());
                }
            }
        }
        return noms;
    }

    private static Set<String> nomsDeMethodes(Class<?> type) {
        Set<String> noms = new LinkedHashSet<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (var m : c.getDeclaredMethods()) {
                noms.add(m.getName());
            }
        }
        return noms;
    }

    private static String relatif(Path fxml) {
        return SOURCE.relativize(fxml).toString().replace(File.separatorChar, '/');
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Le balayage trouve bien les FXML (garde-fou anti-faux-vert)")
    void le_balayage_trouve_des_fxml() throws Exception {
        assertThat(fichiersFxml().count())
                .as(
                        "Aucun (ou trop peu de) FXML trouvé sous %s : le test tourne-t-il depuis la racine du module ?",
                        SOURCE)
                .isGreaterThanOrEqualTo(10);
    }
}
