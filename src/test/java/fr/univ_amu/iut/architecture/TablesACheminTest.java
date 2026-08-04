package fr.univ_amu.iut.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.persistence.TablesAChemin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le garde qui empêche une **septième table à chemin** de passer inaperçue (#3133).
///
/// Quand une nuit change de dossier, tout ce que la base retient d'elle doit suivre : sa racine, mais
/// aussi chaque original, chaque séquence, le journal, le relevé et le CSV Tadarida. Six tables. En
/// oublier une donne une base qui **paraît** corrigée et une application qui ne retrouve plus un
/// fichier : c'est l'état dans lequel #2727 a d'abord été livrée.
///
/// La liste vit désormais à un seul endroit ([TablesAChemin]), ce qui protège de l'oubli d'un côté.
/// Ce test protège d'autre chose, que la déduplication ne couvre pas : une **migration** qui ajoute
/// une colonne de chemin à une table de plus, et que personne ne pense à inscrire.
///
/// Il lit le schéma, pas le code : c'est le schéma qui fait foi.
class TablesACheminTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    /// La seule table que la liste canonique n'a pas à porter, et **pourquoi** : sa colonne s'appelle
    /// `root_path`, c'est la **racine** que tous les autres chemins suivent, et elle se réécrit avant
    /// eux. L'exception est nommée ici plutôt que filtrée en silence : une seconde devra s'écrire, et
    /// se justifier.
    private static final String RACINE_TRAITEE_A_PART = "recording_session";

    /// Une colonne de chemin dans une déclaration SQL : `file_path TEXT NOT NULL`, `root_path TEXT`.
    private static final Pattern COLONNE_CHEMIN =
            Pattern.compile("^\\s*(\\w*_?path)\\s+TEXT", Pattern.CASE_INSENSITIVE);

    private static final Pattern DEBUT_TABLE = Pattern.compile("^\\s*CREATE TABLE\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern COLONNE_AJOUTEE =
            Pattern.compile("^\\s*ALTER TABLE\\s+(\\w+)\\s+ADD COLUMN\\s+(\\w*_?path)\\s", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("toute table du schéma portant un chemin est inscrite dans la liste canonique")
    void toute_table_a_chemin_est_inscrite() throws IOException {
        List<String> duSchema = tablesACheminDuSchema();
        List<String> duCode = TablesAChemin.toutes().stream()
                .map(TablesAChemin.TableAChemin::table)
                .map(nom -> nom.toLowerCase(Locale.ROOT))
                .toList();

        assertThat(duSchema)
                .as("le schéma fait foi : une table qu'il fait porter un chemin et que la liste ignore"
                        + " est une table dont les fichiers ne suivront pas leur dossier")
                .isNotEmpty();
        assertThat(duCode)
                .as("une migration a ajouté une colonne de chemin : inscrivez sa table dans"
                        + " TablesAChemin, faute de quoi une restauration laissera ses fichiers"
                        + " introuvables sans que rien ne rougisse")
                .containsAll(duSchema);
    }

    /// Les tables que le schéma fait porter un chemin, `recording_session` comprise : sa racine est
    /// le chemin dont tous les autres dépendent.
    private static List<String> tablesACheminDuSchema() throws IOException {
        List<String> tables = new ArrayList<>();
        try (Stream<Path> scripts = Files.list(MIGRATIONS)) {
            for (Path script : scripts.sorted().toList()) {
                lireScript(script, tables);
            }
        }
        return tables.stream()
                .distinct()
                .filter(table -> !RACINE_TRAITEE_A_PART.equals(table))
                .toList();
    }

    private static void lireScript(Path script, List<String> tables) throws IOException {
        String tableCourante = "";
        for (String ligne :
                Files.readString(script, StandardCharsets.UTF_8).lines().toList()) {
            if (ligne.strip().startsWith("--")) {
                continue;
            }
            Matcher debut = DEBUT_TABLE.matcher(ligne);
            if (debut.find()) {
                tableCourante = debut.group(1).toLowerCase(Locale.ROOT);
            }
            Matcher ajout = COLONNE_AJOUTEE.matcher(ligne);
            if (ajout.find()) {
                tables.add(ajout.group(1).toLowerCase(Locale.ROOT));
            } else if (!tableCourante.isEmpty() && COLONNE_CHEMIN.matcher(ligne).find()) {
                tables.add(tableCourante);
            }
        }
    }
}
