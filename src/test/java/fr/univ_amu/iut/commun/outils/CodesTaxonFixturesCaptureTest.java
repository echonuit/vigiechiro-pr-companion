package fr.univ_amu.iut.commun.outils;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Garde-fou des **fixtures de capture** : un code de taxon inventé, ou écrit dans une autre casse que
/// celle du référentiel, doit faire rougir un contrôle (#2715).
///
/// ## Le défaut qu'on empêche de revenir
///
/// `CaptureActivite` semait ses contacts de démonstration en majuscules (`PIPPIP`), alors que le
/// référentiel Tadarida porte `Pippip`. Le repère « espèce à enjeu » compare des codes : la comparaison
/// échouait, et l'aperçu publié dans la documentation montrait l'écran **sans aucun bouclier**, alors
/// que trois des espèces affichées sont prioritaires au plan national.
///
/// Deux conséquences, la seconde plus grave : la documentation illustrait une fonctionnalité par une
/// image où elle est absente ; et en lisant cette image, on pouvait conclure que le repère ne
/// fonctionne pas : alors que le produit était juste et la **fixture** fausse. Une capture ne casse pas
/// quand elle ne montre rien : c'est pour cela qu'il faut un test.
///
/// ## Comment le contrôle est mené
///
/// Tous les littéraux de **six lettres** des `Capture*.java` sont relevés : c'est la forme d'un code
/// Tadarida (`Pipkuh`, `Nycnoc`). Chacun doit alors être :
///
/// - un code du référentiel réel, **écrit comme le référentiel l'écrit** ; ou
/// - un mot ordinaire déclaré dans [#MOTS_ORDINAIRES].
///
/// L'exemption est une liste, et c'est assumé : l'oublier fait **rougir**, jamais passer en silence.
/// C'est l'inverse d'une liste d'inclusion, où l'oubli se traduit par une vérification qui ne s'exécute
/// pas : le défaut relevé en #2813.
class CodesTaxonFixturesCaptureTest {

    /// Un code Tadarida fait six lettres : trois du genre, trois de l'espèce.
    private static final Pattern LITTERAL_SIX_LETTRES = Pattern.compile("\"([A-Za-z]{6})\"");

    private static final Path SOURCES = Path.of("src/main/java");

    /// Mots de six lettres employés par les fixtures et qui **ne sont pas** des codes de taxon. Chacun
    /// est là pour une raison lisible ; un mot qui manquerait ferait échouer le test, pas passer.
    private static final Set<String> MOTS_ORDINAIRES = Set.of(
            "Ahetze", // commune, dans un libellé de site de démonstration
            "Bidart", // commune voisine d'Ahetze, semée pour que l'aperçu du critère « Lieu » ait deux communes
            "Groupe", // en-tête de colonne
            "Import", // libellé d'action
            "Testes", // fragment de nom de fichier de démonstration
            "Urbain" // milieu du référentiel d'activité, pas un taxon
            );

    @TempDir
    Path dossier;

    private Set<String> codesReels;

    @BeforeEach
    void migrer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        codesReels = lireCodes(source);
    }

    private static Set<String> lireCodes(SourceDeDonnees source) {
        Set<String> codes = new HashSet<>();
        try (Connection connexion = source.getConnection();
                Statement requete = connexion.createStatement();
                ResultSet lignes = requete.executeQuery("SELECT code FROM taxon")) {
            while (lignes.next()) {
                codes.add(lignes.getString(1));
            }
        } catch (SQLException echec) {
            throw new IllegalStateException("Lecture du referentiel des taxons impossible", echec);
        }
        return codes;
    }

    /// La casse que le référentiel emploie : première lettre en capitale, le reste en minuscules.
    private static String casseDuReferentiel(String mot) {
        return mot.substring(0, 1).toUpperCase(Locale.ROOT) + mot.substring(1).toLowerCase(Locale.ROOT);
    }

    private static List<Path> fichiersDeCapture() throws IOException {
        try (Stream<Path> arbre = Files.walk(SOURCES)) {
            return arbre.filter(Files::isRegularFile)
                    .filter(chemin -> chemin.getFileName().toString().startsWith("Capture"))
                    .filter(chemin -> chemin.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    /// Un littéral relevé, avec le fichier d'où il vient : pour que le message nomme le coupable.
    private record Trouvaille(String fichier, String litteral) {
        @Override
        public String toString() {
            return litteral + " (" + fichier + ")";
        }
    }

    private List<Trouvaille> litterauxDeSixLettres() throws IOException {
        List<Trouvaille> trouvailles = new ArrayList<>();
        for (Path fichier : fichiersDeCapture()) {
            String source = Files.readString(fichier, StandardCharsets.UTF_8);
            Matcher trouve = LITTERAL_SIX_LETTRES.matcher(source);
            while (trouve.find()) {
                trouvailles.add(new Trouvaille(fichier.getFileName().toString(), trouve.group(1)));
            }
        }
        return trouvailles;
    }

    @Test
    @DisplayName("Le contrôle porte sur de vrais fichiers : sans cela, il serait vert pour rien")
    void le_controle_a_de_la_matiere() throws IOException {
        // Un test qui ne balaye rien passe toujours. On exige donc de la matière avant de conclure quoi
        // que ce soit de son verdict.
        assertThat(fichiersDeCapture())
                .as("les outils de capture doivent être trouvés là où on les cherche")
                .hasSizeGreaterThan(20);
        assertThat(codesReels)
                .as("le référentiel migré doit porter des taxons, sinon toute comparaison est vide")
                .hasSizeGreaterThan(100);
        assertThat(litterauxDeSixLettres())
                .as("des codes de taxon sont bien employés par les fixtures")
                .isNotEmpty();
    }

    @Test
    @DisplayName("Un code de taxon est écrit dans la casse du référentiel, jamais en majuscules")
    void casse_du_referentiel_respectee() throws IOException {
        List<String> fautifs = litterauxDeSixLettres().stream()
                .filter(trouvaille -> !codesReels.contains(trouvaille.litteral()))
                .filter(trouvaille -> codesReels.contains(casseDuReferentiel(trouvaille.litteral())))
                .map(trouvaille ->
                        trouvaille + " → le référentiel porte « " + casseDuReferentiel(trouvaille.litteral()) + " »")
                .toList();

        assertThat(fautifs)
                .as("un code dans la mauvaise casse ne joint rien : la capture montrera un écran sans "
                        + "son repère, et l'image fera croire que la fonctionnalité ne marche pas")
                .isEmpty();
    }

    @Test
    @DisplayName("Un littéral en forme de code de taxon existe au référentiel, ou est déclaré ordinaire")
    void aucun_code_invente() throws IOException {
        List<String> inconnus = litterauxDeSixLettres().stream()
                .filter(trouvaille -> !codesReels.contains(trouvaille.litteral()))
                .filter(trouvaille -> !codesReels.contains(casseDuReferentiel(trouvaille.litteral())))
                .filter(trouvaille -> !MOTS_ORDINAIRES.contains(trouvaille.litteral()))
                .map(Trouvaille::toString)
                .distinct()
                .toList();

        assertThat(inconnus)
                .as("ce littéral a la forme d'un code de taxon mais n'existe pas au référentiel. Si "
                        + "c'en est un, corrigez-le ; si c'est un mot ordinaire, ajoutez-le à "
                        + "MOTS_ORDINAIRES avec la raison")
                .isEmpty();
    }
}
