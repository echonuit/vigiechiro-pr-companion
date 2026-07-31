package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La carte des lectures ([CatalogueApi]) **confrontée au source de l'API**.
///
/// Une carte écrite à la main dérive : le serveur ajoute une route, en retire une autre, et la carte
/// continue d'affirmer. Ce test la compare au code qui fait autorité - le dépôt de l'API, dont un
/// miroir vit à côté du nôtre. **Il se saute proprement si le miroir est absent** (même politique que
/// les tests `api-live` sans jeton) : un poste qui ne l'a pas cloné ne doit pas rougir, mais le poste
/// qui l'a doit voir la dérive.
class CatalogueApiTest {

    /// Le miroir du source de l'API, à côté du dépôt (`SAE201/vigiechiro-api`).
    private static final Path SOURCE_API =
            Path.of("..", "vigiechiro-api", "vigiechiro", "resources").normalize();

    /// Une route Flask : `@ressource.route('/chemin', methods=['GET', …])`.
    private static final Pattern ROUTE = Pattern.compile("@\\w+\\.route\\('([^']+)'\\s*,\\s*methods=\\[([^\\]]+)\\]");

    /// Un identifiant dans un chemin Flask (`<objectid:site_id>`, `<int:observation_id>`), que la
    /// carte note `{id}` : deux écritures du même trou.
    private static final Pattern PARAMETRE = Pattern.compile("<[^>]+>");

    @Test
    @DisplayName("Chaque chemin de lecture annoncé par la carte existe dans le source de l'API")
    void aucun_chemin_invente() throws IOException {
        Set<String> reelles = cheminsDeLectureDuSource();
        assumeTrue(!reelles.isEmpty(), "miroir du source de l'API absent : garde sautée");

        List<String> inventes = CatalogueApi.ressources().stream()
                .flatMap(ressource -> ressource.lectures().stream())
                .map(CatalogueApi.RouteApi::chemin)
                .filter(chemin -> !reelles.contains(chemin))
                .sorted()
                .toList();

        assertThat(inventes)
                .as(
                        "Ces chemins sont annoncés par CatalogueApi mais n'existent dans aucune route GET du "
                                + "source (%s). Une carte qui invente est pire qu'une carte absente : elle fait "
                                + "chercher là où il n'y a rien.",
                        SOURCE_API)
                .isEmpty();
    }

    @Test
    @DisplayName("La carte couvre les ressources du source, aucune n'est oubliée")
    void aucune_ressource_oubliee() throws IOException {
        assumeTrue(Files.isDirectory(SOURCE_API), "miroir du source de l'API absent : garde sautée");
        Set<String> duSource;
        try (Stream<Path> fichiers = Files.list(SOURCE_API)) {
            duSource = fichiers.map(fichier -> fichier.getFileName().toString())
                    .filter(nom -> nom.endsWith(".py") && !nom.equals("__init__.py"))
                    .map(nom -> nom.substring(0, nom.length() - 3))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }

        List<String> cartographiees = CatalogueApi.ressources().stream()
                .map(CatalogueApi.RessourceApi::nom)
                .toList();

        assertThat(cartographiees)
                .as(
                        "le source déclare %s ressources : la carte doit toutes les nommer, y compris celles "
                                + "qui n'ont aucune route de collection (c'est précisément ce qu'il faut savoir)",
                        duSource.size())
                .containsExactlyInAnyOrderElementsOf(duSource);
    }

    @Test
    @DisplayName("Les pièges communs sont énoncés : plafond, filtre ignoré, tout-ou-rien")
    void les_pieges_sont_dits() {
        // Ces trois-là ont chacun coûté un incident ; la carte les rappelle là où on la lit, faute de
        // quoi ils restent dans la doc que personne n'ouvre au moment de taper la commande.
        assertThat(CatalogueApi.pieges())
                .anySatisfy(piege -> assertThat(piege).contains("max_results"))
                .anySatisfy(piege -> assertThat(piege).contains("where="))
                .anySatisfy(piege -> assertThat(piege).contains("tout-ou-rien"));
    }

    /// Les chemins **de lecture** déclarés par le source, normalisés comme la carte les écrit
    /// (paramètres Flask réduits à `{id}`).
    private static Set<String> cheminsDeLectureDuSource() throws IOException {
        if (!Files.isDirectory(SOURCE_API)) {
            return Set.of();
        }
        Set<String> chemins = new LinkedHashSet<>();
        try (Stream<Path> fichiers = Files.list(SOURCE_API)) {
            for (Path fichier :
                    fichiers.filter(f -> f.toString().endsWith(".py")).toList()) {
                Matcher route = ROUTE.matcher(Files.readString(fichier, StandardCharsets.UTF_8));
                while (route.find()) {
                    if (route.group(2).contains("GET")) {
                        chemins.add(PARAMETRE.matcher(route.group(1)).replaceAll("{id}"));
                    }
                }
            }
        }
        return chemins;
    }
}
