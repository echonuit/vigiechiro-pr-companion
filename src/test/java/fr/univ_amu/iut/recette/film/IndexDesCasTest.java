package fr.univ_amu.iut.recette.film;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le garde de l'index, et d'abord celui du défaut qui l'a rendu nécessaire.
///
/// Surefire tourne à `forkCount=1C` : une JVM par cœur, chacune avec son propre index. La première
/// version écrivait `index.md` directement, si bien que la dernière JVM à finir effaçait le travail
/// des autres. Mesuré sur quatre forks et neuf cas : **cinq lignes sur neuf**.
///
/// ⚠️ Ce défaut ne pouvait rougir nulle part. L'index gardait toutes ses colonnes, aucune de ses
/// lignes n'était fausse, et rien n'annonçait qu'il en manquait quatre. Un index amputé se lit
/// exactement comme un index complet.
class IndexDesCasTest {

    private static IndexDesCas.Ligne ligne(String cas, String test) {
        return new IndexDesCas.Ligne(cas, test, test + ".mp4", true);
    }

    /// LE cas de non-régression. Deux index, deux identités, le même dossier : c'est la situation
    /// de deux forks, et l'index final doit porter les deux moitiés.
    @Test
    @DisplayName("deux JVM écrivent le même index, et aucune n'efface l'autre")
    void deuxJvmNeSEffacentPas(@TempDir Path bac) throws IOException {
        Path index = bac.resolve("index.md");

        IndexDesCas premiere = new IndexDesCas(index, "fork-1");
        premiere.ajouter(ligne("S1-26", "ScenarioPerceptifConnexionTest.la_modale_s_ouvre"));
        premiere.ajouter(ligne("S1-27", "ScenarioPerceptifConnexionTest.la_recuperation"));
        premiere.close();

        IndexDesCas seconde = new IndexDesCas(index, "fork-2");
        seconde.ajouter(ligne("S6-25", "ScenarioPerceptifFiltresTest.une_puce"));
        seconde.close();

        assertThat(Files.readString(index))
                .as("l'index final porte les lignes des DEUX forks")
                .contains("| S1-26 |")
                .contains("| S1-27 |")
                .contains("| S6-25 |");
    }

    /// ⚠️ L'ordre inverse compte autant. Si la reconstruction ne relisait que les fragments écrits
    /// AVANT le sien, celle-ci passerait et l'autre pas, ou l'inverse selon l'implémentation.
    @Test
    @DisplayName("l'ordre d'arrivée des forks ne change pas l'index")
    void lOrdreDesForksNeChangeRien(@TempDir Path bac) throws IOException {
        Path index = bac.resolve("index.md");

        IndexDesCas tardive = new IndexDesCas(index, "fork-z");
        tardive.ajouter(ligne("S6-29", "ScenarioPerceptifFiltresTest.tout_effacer"));
        tardive.close();

        IndexDesCas precoce = new IndexDesCas(index, "fork-a");
        precoce.ajouter(ligne("S1-26", "ScenarioPerceptifConnexionTest.la_modale_s_ouvre"));
        precoce.close();

        assertThat(Files.readString(index)).contains("| S1-26 |").contains("| S6-29 |");
    }

    @Test
    @DisplayName("les lignes sont rangées par cas, quel que soit le fork qui les a produites")
    void lesLignesSontRangeesParCas(@TempDir Path bac) throws IOException {
        Path index = bac.resolve("index.md");

        IndexDesCas seconde = new IndexDesCas(index, "fork-2");
        seconde.ajouter(ligne("S6-25", "b"));
        seconde.close();

        IndexDesCas premiere = new IndexDesCas(index, "fork-1");
        premiere.ajouter(ligne("S1-26", "a"));
        premiere.close();

        String page = Files.readString(index);
        assertThat(page.indexOf("| S1-26 |")).as("S1-26 doit précéder S6-25").isLessThan(page.indexOf("| S6-25 |"));
    }

    /// Un fork rejoué dépose un fragment au même nom : ses lignes remplacent les siennes, elles ne
    /// s'y ajoutent pas.
    @Test
    @DisplayName("un même cas joué par un même test ne paraît qu'une fois")
    void unMemeCasNeParaitQuUneFois(@TempDir Path bac) throws IOException {
        Path index = bac.resolve("index.md");

        IndexDesCas premiere = new IndexDesCas(index, "fork-1");
        premiere.ajouter(ligne("S1-26", "le_meme_test"));
        premiere.close();

        IndexDesCas rejouee = new IndexDesCas(index, "fork-1");
        rejouee.ajouter(ligne("S1-26", "le_meme_test"));
        rejouee.close();

        assertThat(Files.readString(index).split("\\| S1-26 \\|", -1))
                .as("une seule ligne pour ce cas")
                .hasSize(2);
    }

    /// Un cas couvert par DEUX tests garde ses deux lignes : c'est la promesse de l'en-tête, et
    /// dédupliquer sur le seul cas la trahirait.
    @Test
    @DisplayName("un cas couvert par deux tests garde ses deux lignes")
    void unCasCouvertParDeuxTestsGardeSesDeuxLignes(@TempDir Path bac) throws IOException {
        Path index = bac.resolve("index.md");

        IndexDesCas seul = new IndexDesCas(index, "fork-1");
        seul.ajouter(ligne("S1-26", "premier_test"));
        seul.ajouter(ligne("S1-26", "second_test"));
        seul.close();

        assertThat(Files.readString(index).split("\\| S1-26 \\|", -1)).hasSize(3);
    }

    @Test
    @DisplayName("sans aucune ligne, aucun index n'est écrit")
    void sansLigneAucunIndex(@TempDir Path bac) throws IOException {
        Path index = bac.resolve("index.md");

        new IndexDesCas(index, "fork-vide").close();

        assertThat(Files.exists(index))
                .as("un index vide vaut moins que pas d'index : il se lirait comme un tournage sans cas")
                .isFalse();
    }

    /// Le nombre de fragments fusionnés est ANNONCÉ, parce que c'est le seul moyen de voir qu'un
    /// dossier de tournage réutilisé en porte de trop.
    @Test
    @DisplayName("un fragment étranger au tournage se compte, il ne se devine pas")
    void unFragmentEtrangerSeCompte(@TempDir Path bac) throws IOException {
        Path index = bac.resolve("index.md");
        Files.createDirectories(bac.resolve("index.d"));
        Files.writeString(bac.resolve("index.d/index-tournage-precedent.tsv"), "S9-99\tvieux_test\tvieux.mp4\ttrue\n");

        IndexDesCas aujourd = new IndexDesCas(index, "fork-1");
        aujourd.ajouter(ligne("S1-26", "test_du_jour"));
        aujourd.close();

        assertThat(Files.readString(index))
                .as("la limite est assumée : le vieux fragment EST repris, et le compte le dit")
                .contains("| S9-99 |")
                .contains("| S1-26 |");
    }
}
