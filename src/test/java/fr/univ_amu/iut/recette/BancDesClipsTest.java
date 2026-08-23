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

    /// Les classes qui montent encore leur banc à la main, en attendant [BancDeRecette].
    ///
    /// ⚠️ Nommée et finie, comme [#SANS_CHROME_EN_ATTENTE] : le garde rougit dès qu'elle s'allonge, et
    /// la migration se compte au lieu de se promettre. Une classe NEUVE ne peut donc pas repartir d'un
    /// copier-coller de préambule - c'est tout l'objet de #4133.
    ///
    /// Elle comptait dix entrées à la clôture ; il en reste **une**, et c'est la même que celle de
    /// [#SANS_CHROME_EN_ATTENTE] : `ScenarioPerceptifFiltresTest` monte une vue seule, et la porter sur
    /// le chrome est un chantier de la session 6, pas de celle-ci.
    private static final Set<String> BANC_A_LA_MAIN_EN_ATTENTE = Set.of("ScenarioPerceptifFiltresTest.java");

    @Test
    @DisplayName("#4133 : une classe filmée neuve déclare son banc, elle ne le recopie pas")
    void une_classe_filmee_neuve_declare_son_banc() {
        List<String> aLaMain = new ArrayList<>();
        for (Path source : classesFilmees()) {
            String nom = source.getFileName().toString();
            if (!lire(source).contains("BancDeRecette") && !BANC_A_LA_MAIN_EN_ATTENTE.contains(nom)) {
                aLaMain.add(nom);
            }
        }

        assertThat(aLaMain).as("""
                        Ces classes filmées montent leur banc à la main : espace de travail, injecteur, \
                        migrations, semis, chrome, fenêtre, ouverture.

                        Ce préambule pesait 494 lignes pour cinquante cas à la clôture de #4133, et les \
                        trois plus lourds 47 à 69 lignes POUR UN SEUL CAS. Écrire un cas neuf revenait \
                        donc à recopier le préambule du voisin - et une copie hérite de la dette de son \
                        modèle : trois classes sur onze traînaient encore un idiome de fenêtre périmé.

                        Remède : `BancDeRecette.surLeChrome()`, qui porte les huit gestes toujours \
                        identiques et n'exige que les quatre décisions qui varient vraiment.""").isEmpty();
    }

    @Test
    @DisplayName("#4133 : un cas filmé monte le chrome, et la liste d'attente ne s'allonge pas")
    void un_cas_filme_monte_le_chrome() {
        List<String> sansChrome = new ArrayList<>();
        for (Path source : classesFilmees()) {
            String nom = source.getFileName().toString();
            // ⚠️ Deux façons de monter le chrome, et le garde doit connaître les deux : nommer le
            // FXML, ou passer par `BancDeRecette.surLeChrome()` qui le nomme à sa place. Le premier jet
            // ne cherchait que la chaîne « MainView.fxml » et a déclaré fautive la première classe
            // migrée sur le banc - un garde qui mesure un PROXY plutôt que la propriété se trompe dès
            // que le proxy change.
            String code = lire(source);
            boolean surLeChrome = code.contains("MainView.fxml") || code.contains("BancDeRecette.surLeChrome()");
            if (!surLeChrome && !SANS_CHROME_EN_ATTENTE.contains(nom)) {
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
