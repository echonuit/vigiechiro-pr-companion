package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Une modale montée **seule** ne montre ni sa cause ni son effet ([ADR
/// 4188](../../../../../../../dev-docs/decisions/4188-une-modale-se-filme-avec-son-ecran.md)).
///
/// ## Ce que ce garde empêche de revenir
///
/// Cinq constats de la revue des clips disaient la même chose sous cinq formes. Le clip montrait une
/// modale flottant sur du **noir** : « aucun carré ajouté » n'avait aucun endroit où se voir, et « la
/// fenêtre se ferme, la fiche s'ouvre » aucun écran d'arrivée à montrer.
///
/// ⚠️ Et il n'y avait **aucun obstacle technique** : les modales du produit s'ouvrent en `show()`,
/// jamais en `showAndWait`. La raison qu'on se donnait - « un dialogue fige TestFX headless » - ne vaut
/// que pour les `Alert` du socle.
///
/// ## Ce que ce garde ne dit pas
///
/// Il refuse qu'une classe **charge une modale elle-même** tout en citant un cas. Il ne peut pas
/// vérifier qu'un scénario s'arrête assez longtemps sur l'écran d'arrivée, ni que le geste d'ouverture
/// est un clic : cela se juge en regardant, et c'est à cela que servent les clips.
class ClipDeModaleTest {

    private static final Path SOURCES = Path.of("src", "test", "java");

    /// Le chargement direct d'une modale, **où que le mot se trouve dans le nom** :
    /// `ModaleSite.fxml`, `ModalePoint.fxml`, `ConnexionModale.fxml`.
    ///
    /// ⚠️ La première version ancrait `Modale` au DÉBUT du nom. Elle ratait `ConnexionModale.fxml`, donc
    /// les deux cas de la modale de connexion - `S1-04` et `S1-11` - dont les clips montraient encore une
    /// modale sur fond noir. Le garde se déclarait vert en regardant deux fichiers sur quatre.
    private static final Pattern MODALE_CHARGEE = Pattern.compile("\"[A-Za-z]*Modale[A-Za-z]*\\.fxml\"");

    @Test
    @DisplayName("#4188 : aucun cas de recette n'est porté par une classe qui monte une modale seule")
    void aucun_cas_ne_monte_une_modale_seule() {
        List<String> fautifs = new ArrayList<>();
        for (Path source : sourcesDeTest()) {
            String code = lire(source);
            if (!code.contains("@CasDeRecette(") || code.contains("@FixtureDeRecette")) {
                continue;
            }
            // ⚠️ Un détecteur textuel s'exclut de son corpus ([ADR 3645]). Sans cela ce fichier-ci se
            // dénonce lui-même : son message d'échec cite les noms qu'il cherche, et son doc-comment
            // aussi. La première version se comptait donc parmi les fautifs.
            if (source.getFileName().toString().equals("ClipDeModaleTest.java")) {
                continue;
            }
            if (MODALE_CHARGEE.matcher(code).find()) {
                fautifs.add(source.getFileName().toString());
            }
        }

        assertThat(fautifs).as("""
                        Ces classes citent un cas de recette - donc le banc en tourne un clip - et \
                        montent elles-mêmes une modale. Le clip montre alors une modale flottant sur du \
                        NOIR : on ne voit ni le geste qui l'a ouverte, ni l'écran où l'on retombe, \
                        c'est-à-dire ni sa cause ni son effet.

                        Remède : le cas déménage vers un scénario qui monte le vrai chrome, atteint \
                        l'écran de départ par des gestes, ouvre la modale PAR SON BOUTON, et s'arrête \
                        sur l'écran d'arrivée. Les modales du produit s'ouvrent en `show()` : elles \
                        sont pilotables en headless, il n'y a pas d'obstacle.

                        La classe garde ses assertions - elles éprouvent le câblage de la modale, ce \
                        qui est un autre travail que de le montrer.""").isEmpty();
    }

    private static List<Path> sourcesDeTest() {
        try (Stream<Path> chemins = Files.walk(SOURCES)) {
            List<Path> trouvees =
                    chemins.filter(p -> p.toString().endsWith(".java")).toList();
            assertThat(trouvees)
                    .as("Le garde ne balaie aucun fichier : lancé hors de la racine du dépôt ?")
                    .isNotEmpty();
            return trouvees;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
