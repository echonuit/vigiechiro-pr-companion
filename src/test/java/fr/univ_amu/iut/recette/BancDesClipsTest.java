package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Comment une classe **filmée** monte son banc (#4133).
///
/// ## Pourquoi ce garde existe
///
/// Le préambule d'un scénario filmé - fenêtre, injecteur, chrome, exécuteur - pèse aujourd'hui **494
/// lignes pour cinquante cas**, et les trois plus lourds coûtent de quarante-sept à soixante-neuf
/// lignes **pour un seul cas**. Écrire un cas neuf, c'est donc recopier le préambule du voisin, et
/// [une copie hérite de la dette de son modèle]. Mesuré à la clôture de #4133 : trois classes sur onze
/// posaient encore leur fenêtre à la main, avec un idiome antérieur à [FenetreDuBanc], et deux tailles
/// d'écran différentes circulaient.
///
/// Ce n'est pas un détail de style. Le `Stage` du harnais TestFX est **partagé** par toutes les classes
/// d'un même fork : `setScene` seul laisse la fenêtre à la taille que la classe précédente lui a
/// donnée, et le clip est alors cadré par le hasard de l'ordre d'exécution. [FenetreDuBanc#afficher]
/// fait le `sizeToScene()` qui rend ce cadrage indépendant du voisin (ADR 4134).
class BancDesClipsTest {

    private static final Path SOURCES = Path.of("src", "test", "java");

    /// ⚠️ Ce fichier-ci cite les motifs qu'il traque, et se signalerait lui-même (ADR 3645).
    private static final String MOI = "BancDesClipsTest.java";

    /// Les classes qui montent une vue SEULE, en attendant d'être portées sur le chrome.
    ///
    /// ⚠️ Une liste, pas un interrupteur : elle est **nommée et finie**, le garde rougit dès qu'elle
    /// s'allonge, et chaque entrée dit son issue. Un cas de recette doit montrer la fonctionnalité
    /// **et le chemin pour y accéder** ; une vue montée seule ne montre pas comment on y arrive.
    private static final Set<String> SANS_CHROME_EN_ATTENTE = Set.of("ScenarioPerceptifFiltresTest.java");

    @Test
    @DisplayName("#4133 : une classe filmée pose sa fenêtre par FenetreDuBanc, jamais à la main")
    void une_classe_filmee_pose_sa_fenetre_par_le_banc() {
        List<String> fautives = new ArrayList<>();
        int inspectees = 0;
        for (Path source : classesFilmees()) {
            inspectees++;
            String code = Files.exists(source) ? lire(source) : "";
            if (code.contains("stage.setScene(") || code.contains("fenetre.setScene(")) {
                fautives.add(source.getFileName().toString());
            }
        }

        assertThat(inspectees)
                .as("aucune classe filmée inspectée : le garde ne garde plus rien")
                .isPositive();
        System.out.printf("%nBanc des clips : %d classes filmées inspectées.%n", inspectees);

        assertThat(fautives).as("""
                        Ces classes filmées posent leur scène à la main sur le Stage du harnais.

                        Ce Stage est PARTAGÉ par toutes les classes d'un même fork : `setScene` seul \
                        laisse la fenêtre à la taille que la classe précédente lui a donnée, et le clip \
                        est cadré par le hasard de l'ordre d'exécution.

                        Remède : `FenetreDuBanc.poser(stage, racine, largeur, hauteur)` puis \
                        `FenetreDuBanc.afficher(stage)`, qui fait le `sizeToScene()` (ADR 4134).""").isEmpty();
    }

    @Test
    @DisplayName("#4133 : un cas filmé monte le chrome, et la liste d'attente ne s'allonge pas")
    void un_cas_filme_monte_le_chrome() {
        List<String> sansChrome = new ArrayList<>();
        for (Path source : classesFilmees()) {
            String nom = source.getFileName().toString();
            if (!lire(source).contains("MainView.fxml") && !SANS_CHROME_EN_ATTENTE.contains(nom)) {
                sansChrome.add(nom);
            }
        }

        assertThat(sansChrome).as("""
                        Ces classes filment une vue montée SEULE. Un cas de recette montre la \
                        fonctionnalité ET le chemin pour y accéder : sans le chrome, le clip commence \
                        au milieu, et personne ne comprend comment on est arrivé là.

                        Remède : monter `commun/view/MainView.fxml` et atteindre l'écran par les GESTES \
                        (clic de menu, clic de carte), en ne remplaçant que la frontière réseau.

                        Si la conversion est un chantier à elle seule, l'ajouter à \
                        SANS_CHROME_EN_ATTENTE avec son numéro d'issue - la dette reste alors visible \
                        et comptée, au lieu de se fondre dans le vert.""").isEmpty();
    }

    /// Les classes de test qui **citent un cas** et **montent une scène** : celles dont le banc tourne
    /// un clip.
    private static List<Path> classesFilmees() {
        try (Stream<Path> fichiers = Files.walk(SOURCES)) {
            return fichiers.filter(chemin -> chemin.toString().endsWith(".java"))
                    .filter(chemin -> !chemin.getFileName().toString().equals(MOI))
                    .filter(chemin -> {
                        String code = lire(chemin);
                        return code.contains("@CasDeRecette") && code.contains("ApplicationExtension");
                    })
                    .sorted()
                    .toList();
        } catch (IOException echec) {
            throw new UncheckedIOException("Lecture des sources de test impossible", echec);
        }
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier, StandardCharsets.UTF_8);
        } catch (IOException echec) {
            throw new UncheckedIOException("Lecture impossible : " + fichier, echec);
        }
    }
}
