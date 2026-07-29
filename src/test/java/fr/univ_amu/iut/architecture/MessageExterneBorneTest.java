package fr.univ_amu.iut.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Un retour d'opération ne renvoie pas un message d'exception nu** (#2076).
///
/// Le bandeau de retour n'a **pas de troncature** : son libellé porte `wrapText`, donc un long message
/// enroule et fait grandir le bandeau. Mesuré à la largeur d'un écran : un message de pilote SQLite
/// rappelant sa requête (379 caractères) le porte de 46 à 86 px, un collage de 625 caractères à 186 px.
///
/// `RetourOperation.erreur(Throwable)` **borne** ce qui vient d'ailleurs et y ajoute le **geste attendu**
/// (#2635). Passer `refus.getMessage()` contourne les deux : c'est ce que faisaient **dix-sept** appels,
/// dont quatorze perdaient au passage le « où le régler » que #2635 avait livré.
///
/// Ce test lit le **source** plutôt que le bytecode, pour la même raison qu'[IsolationFeatureSourcesTest]
/// : ce qu'on veut interdire est une **forme d'écriture**, pas une dépendance. Elle ne laisse aucune
/// trace distinctive dans le `.class` - un `getMessage()` y ressemble à n'importe quel appel.
///
/// Il ne prétend pas tout voir : il attrape la forme exacte qui a existé ici. C'est suffisant pour
/// empêcher la régression par recopie, qui est la façon dont ces dix-sept se sont accumulés.
class MessageExterneBorneTest {

    private static final Path RACINE = Path.of("src/main/java/fr/univ_amu/iut");

    /// `erreur(x.getMessage())`, `info(x.getMessage())` et leurs variantes concaténées.
    private static final Pattern MESSAGE_NU =
            Pattern.compile("\\b(erreur|info|avertissement|succes)\\s*\\([^;]*?\\.getMessage\\(\\)");

    @Test
    @DisplayName("#2076 : aucun retour d'opération ne renvoie un message d'exception nu")
    void aucun_retour_ne_renvoie_un_message_nu() throws IOException {
        List<String> fautifs = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(RACINE)) {
            for (Path source : sources.filter(chemin -> chemin.toString().endsWith(".java"))
                    .toList()) {
                releverDans(source, fautifs);
            }
        }

        assertThat(fautifs)
                .as("passez le Throwable, pas son message : `erreur(refus)` borne ce qui vient d'ailleurs"
                        + " et ajoute le geste attendu (#2635, #2076)")
                .isEmpty();
    }

    /// Une lambda qui **déballe son propre `Throwable`** : `erreur -> canal.echec(erreur.getMessage())`.
    ///
    /// C'est la porte contournée d'un saut : le canal reçoit une `String`, donc sa surcharge « texte que
    /// nous avons écrit », et l'exception perd son geste attendu comme sa borne. Le motif exige que le
    /// nom déballé soit **celui du paramètre de la lambda** : `() -> "…" + echec.getMessage()` dans un
    /// journal ne matche pas, et c'est voulu - un journal doit tout garder.
    private static final Pattern LAMBDA_QUI_DEBALLE =
            Pattern.compile("(\\w+)\\s*->\\s*[\\w.]+\\([^;]*\\b\\1\\.getMessage\\(\\)");

    @Test
    @DisplayName("#2841 : aucune lambda ne déballe son Throwable pour le passer en texte")
    void aucune_lambda_ne_deballe_son_throwable() throws IOException {
        List<String> fautifs = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(RACINE)) {
            for (Path source : sources.filter(chemin -> chemin.toString().endsWith(".java"))
                    .toList()) {
                List<String> lignes = Files.readAllLines(source, StandardCharsets.UTF_8);
                for (int rang = 0; rang < lignes.size(); rang++) {
                    if (LAMBDA_QUI_DEBALLE.matcher(lignes.get(rang)).find()) {
                        fautifs.add(RACINE.relativize(source) + ":" + (rang + 1) + " → "
                                + lignes.get(rang).strip());
                    }
                }
            }
        }

        assertThat(fautifs)
                .as("passez le Throwable au canal (`canal::echec`) : sa surcharge l'enrichit du geste"
                        + " attendu puis le borne, là où sa surcharge String suppose un texte de nous")
                .isEmpty();
    }

    private static void releverDans(Path source, List<String> fautifs) throws IOException {
        List<String> lignes = Files.readAllLines(source, StandardCharsets.UTF_8);
        for (int rang = 0; rang < lignes.size(); rang++) {
            String ligne = lignes.get(rang);
            // Les doc-comments citent la forme fautive pour l'expliquer : ils ne l'écrivent pas.
            if (ligne.stripLeading().startsWith("///") || ligne.stripLeading().startsWith("//")) {
                continue;
            }
            Matcher fautif = MESSAGE_NU.matcher(ligne);
            if (fautif.find()) {
                fautifs.add(RACINE.relativize(source) + ":" + (rang + 1) + " → " + ligne.strip());
            }
        }
    }
}
