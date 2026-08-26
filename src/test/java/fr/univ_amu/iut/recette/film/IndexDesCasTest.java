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
/// Ce défaut ne pouvait rougir nulle part. L'index gardait toutes ses colonnes, aucune de ses
/// lignes n'était fausse, et rien n'annonçait qu'il en manquait quatre. Un index amputé se lit
/// exactement comme un index complet.
///
/// Et il a fallu s'y reprendre à deux fois. Le premier remède nommait le fragment d'après le
/// `pid`, ce qui suffit tant que les forks vivent ENSEMBLE. Sur un runner macOS ils se sont
/// enchaînés, le système a recyclé un numéro, et un fragment en a effacé un autre : neuf clips,
/// huit lignes, `S1-37` disparu. La collision avait changé de place, pas disparu. C'est pourquoi
/// deux cas d'ici prennent l'identité par DÉFAUT plutôt qu'une identité dictée.
class IndexDesCasTest {

    private static IndexDesCas.Ligne ligne(String cas, String test) {
        return new IndexDesCas.Ligne(cas, test, test + ".mp4", true);
    }

    /// LE cas de non-régression. Deux index, deux identités, le même dossier : c'est la situation
    /// de deux forks, et l'index final doit porter les deux moitiés.
    @Test
    @DisplayName("deux JVM écrivent le même index, et aucune n'efface l'autre")
    void deux_jvm_ne_s_effacent_pas(@TempDir Path bac) throws IOException {
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

    /// L'ordre inverse compte autant. Si la reconstruction ne relisait que les fragments écrits
    /// AVANT le sien, celle-ci passerait et l'autre pas, ou l'inverse selon l'implémentation.
    @Test
    @DisplayName("l'ordre d'arrivée des forks ne change pas l'index")
    void l_ordre_des_forks_ne_change_rien(@TempDir Path bac) throws IOException {
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
    void les_lignes_sont_rangees_par_cas(@TempDir Path bac) throws IOException {
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

    /// LE second cas de non-régression, et c'est macOS qui l'a dicté.
    ///
    /// Les deux index d'ici prennent leur identité PAR DÉFAUT, dans une seule JVM : ils
    /// partagent donc le même numéro de processus. C'est exactement la situation de deux forks
    /// enchaînés dont le système a recyclé le numéro, et c'est ce qui a fait disparaître `S1-37`
    /// d'un tournage dont les neuf clips existaient pourtant.
    ///
    /// Ce cas rougissait sur la première version du remède, celle qui nommait le fragment d'après
    /// le seul `pid` : la collision avait changé de place, pas disparu.
    @Test
    @DisplayName("deux JVM de même numéro de processus ne s'effacent pas non plus")
    void deux_jvm_de_meme_pid_ne_s_effacent_pas(@TempDir Path bac) throws IOException {
        Path index = bac.resolve("index.md");

        IndexDesCas premiere = new IndexDesCas(index);
        premiere.ajouter(ligne("S1-37", "ScenarioPerceptifRecuperationCarreTest.la_recuperation_ramene"));
        premiere.close();

        IndexDesCas suivante = new IndexDesCas(index);
        suivante.ajouter(ligne("S4-33", "ScenarioPerceptifRefusDepotTest.le_compte_rendu"));
        suivante.close();

        assertThat(Files.readString(index))
                .as("le cas de la première JVM ne doit pas disparaître")
                .contains("| S1-37 |")
                .contains("| S4-33 |");
    }

    /// Le garde de la cause, à côté de celui du symptôme. Un identifiant qui se réemploie
    /// n'identifie pas.
    @Test
    @DisplayName("l'identité par défaut ne se réemploie jamais")
    void l_identite_par_defaut_ne_se_reemploie_jamais() {
        assertThat(IndexDesCas.identiteParDefaut())
                .as("deux séances de la même JVM doivent porter deux noms de fragment")
                .isNotEqualTo(IndexDesCas.identiteParDefaut());
    }

    /// La déduplication se fait sur la paire cas + test, et non sur le fichier de fragment : deux
    /// séances qui rejouent le même cas rendent une seule ligne, tout en gardant deux fragments.
    @Test
    @DisplayName("un même cas joué par un même test ne paraît qu'une fois")
    void un_meme_cas_ne_parait_qu_une_fois(@TempDir Path bac) throws IOException {
        Path index = bac.resolve("index.md");

        IndexDesCas premiere = new IndexDesCas(index);
        premiere.ajouter(ligne("S1-26", "le_meme_test"));
        premiere.close();

        IndexDesCas rejouee = new IndexDesCas(index);
        rejouee.ajouter(ligne("S1-26", "le_meme_test"));
        rejouee.close();

        assertThat(Files.readString(index).split("\\| S1-26 \\|", -1))
                .as("une seule ligne pour ce cas, malgré deux fragments")
                .hasSize(2);
    }

    /// Un cas couvert par DEUX tests garde ses deux lignes : c'est la promesse de l'en-tête, et
    /// dédupliquer sur le seul cas la trahirait.
    @Test
    @DisplayName("un cas couvert par deux tests garde ses deux lignes")
    void un_cas_couvert_par_deux_tests_garde_ses_deux_lignes(@TempDir Path bac) throws IOException {
        Path index = bac.resolve("index.md");

        IndexDesCas seul = new IndexDesCas(index, "fork-1");
        seul.ajouter(ligne("S1-26", "premier_test"));
        seul.ajouter(ligne("S1-26", "second_test"));
        seul.close();

        assertThat(Files.readString(index).split("\\| S1-26 \\|", -1)).hasSize(3);
    }

    @Test
    @DisplayName("sans aucune ligne, aucun index n'est écrit")
    void sans_ligne_aucun_index(@TempDir Path bac) throws IOException {
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
    void un_fragment_etranger_se_compte(@TempDir Path bac) throws IOException {
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
