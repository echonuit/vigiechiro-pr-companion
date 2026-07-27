package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Cliquet de résolution des jetons de couleur** : tout `-couleur-…` cité dans une feuille de style
/// doit être défini dans `palette.css`.
///
/// ## Pourquoi ce test existe
///
/// Un jeton inexistant **ne casse rien de visible**. JavaFX ne trouve pas la couleur, ne lève rien,
/// n'affiche rien de particulier : il consigne une ligne
/// `WARNING: Caught 'java.lang.ClassCastException: class java.lang.String cannot be cast to class
/// javafx.scene.paint.Paint' while converting value for '-fx-border-color'` et laisse la propriété
/// **non appliquée**. À l'écran, il manque un filet ou une couleur de survol, ce que personne ne
/// cherche.
///
/// Le cas qui a motivé ce cliquet : `-couleur-bordure` et `-couleur-accent`, inventés de bonne foi en
/// écrivant la bande du compte rendu chiffré (#2358), alors que la palette nomme `#d0d7de` en littéral
/// pour les filets et `-couleur-primaire-sombre` pour l'accent. Les deux ont traversé la compilation,
/// les tests de composant et la revue ; seule la sortie console de la génération des captures les a
/// signalés, dans un flot de lignes qu'on ne lit pas.
///
/// ## Ce qu'il ne vérifie pas
///
/// Que la couleur soit la bonne : c'est l'affaire de [ContrasteAATest] pour la lisibilité, et de la
/// revue visuelle pour le reste. Ici on vérifie seulement qu'elle **existe**.
class JetonsDeCouleurResolusTest {

    private static final Path RACINE = Path.of("src/main/java/fr/univ_amu/iut");

    private static final Path PALETTE = RACINE.resolve("commun/view/palette.css");

    /// Définition d'un jeton : `-couleur-x: …;` en tête de règle dans `palette.css`.
    private static final Pattern DEFINITION = Pattern.compile("(?m)^\\s*(-couleur-[\\w-]+)\\s*:");

    /// Référence à un jeton, n'importe où dans une valeur. Le garde-fou `(?<![\w-])` évite de couper un
    /// nom plus long en son milieu.
    private static final Pattern REFERENCE = Pattern.compile("(?<![\\w-])(-couleur-[\\w-]+)");

    /// Commentaires CSS, retirés avant l'analyse : la palette **documente** les jetons qu'elle a retirés
    /// (`-couleur-neutre`, #322), et une note d'historique n'est pas une référence.
    private static final Pattern COMMENTAIRE = Pattern.compile("(?s)/\\*.*?\\*/");

    @Test
    @DisplayName("Tout jeton -couleur-… cité dans une feuille est défini dans palette.css")
    void chaque_jeton_cite_est_defini() {
        Set<String> definis = jetonsDefinis();
        assertThat(definis)
                .as("la palette est lue, pas devinée : sans définition trouvée, ce test ne prouverait rien")
                .isNotEmpty();

        List<String> inconnus = new ArrayList<>();
        for (Path feuille : feuillesDeStyle()) {
            Matcher reference = REFERENCE.matcher(sansCommentaires(lire(feuille)));
            while (reference.find()) {
                if (!definis.contains(reference.group(1))) {
                    inconnus.add(feuille.getFileName() + " : " + reference.group(1));
                }
            }
        }

        assertThat(new TreeSet<>(inconnus))
                .as("""
                        Une feuille de style cite un jeton de couleur que palette.css ne définit pas.

                        JavaFX ne lèvera rien : il consignera un ClassCastException en WARNING et
                        laissera la propriété NON APPLIQUÉE. À l'écran, un filet ou une couleur de
                        survol manquera, sans que rien ne le signale.

                        Soit le jeton existe sous un autre nom (les filets discrets sont en littéral
                        `#d0d7de`, l'accent est `-couleur-primaire-sombre`), soit il faut l'ajouter à
                        palette.css - et alors vérifier son contraste (ContrasteAATest).

                        Jetons non résolus :
                        %s
                        """.formatted(String.join("\n", new TreeSet<>(inconnus))))
                .isEmpty();
    }

    private static Set<String> jetonsDefinis() {
        Matcher definition = DEFINITION.matcher(sansCommentaires(lire(PALETTE)));
        Set<String> jetons = new TreeSet<>();
        while (definition.find()) {
            jetons.add(definition.group(1));
        }
        return jetons;
    }

    private static String sansCommentaires(String feuille) {
        return COMMENTAIRE.matcher(feuille).replaceAll(" ");
    }

    private static List<Path> feuillesDeStyle() {
        try (Stream<Path> chemins = Files.walk(RACINE)) {
            return chemins.filter(chemin -> chemin.toString().endsWith(".css"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException echec) {
            throw new UncheckedIOException("balayage des feuilles de style", echec);
        }
    }

    private static String lire(Path feuille) {
        try {
            return Files.readString(feuille);
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + feuille, echec);
        }
    }
}
