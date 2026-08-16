package fr.univ_amu.iut.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Les deux dispositifs de l'invariant « tu écris, tu signales » (#3645) : une **invariante** sur les
/// annonces, un **cliquet** sur les sites d'écriture.
///
/// L'[ADR 3537](../../../../../../../dev-docs/decisions/3537-un-signal-se-pose-a-l-ecriture.md) veut que
/// toute écriture **structurelle** validée - celle qui change l'un des quatre comptes de l'accueil :
/// sites, points, passages, observations - appelle `JournalMutations.mutationStructurelleValidee()`.
///
/// ## Ce qu'aucun dispositif de ce fichier ne prouve
///
/// **Que les bonnes opérations annoncent.** L'invariant lie deux populations qui ne se correspondent pas
/// une pour une : l'ADR 3537 autorise explicitement qu'une **seule** annonce couvre une rafale, et
/// `RapprochementSites` crée deux cent cinquante sites en appelant le même service qu'un ajout manuel.
/// Comparer les deux comptes rougirait donc en permanence sur un dépôt correct.
///
/// **Et une règle « qui appelle `insert` sur un DAO compté annonce aussi » serait aveugle à un tiers de
/// la population** : vingt et une écritures passent par du **SQL brut**, hors DAO. Un vert dessus
/// affirmerait sans preuve, ce que l'ADR 2213 interdit.
///
/// Ces deux dispositifs tiennent donc une promesse **plus faible et vérifiable** : rien de nouveau
/// n'entre sans être vu, ni du côté des annonces, ni du côté des écritures.
///
/// ## Le premier a commencé sa vie vacant
///
/// Livré le 15/08/2026 comme cliquet de dette à cinq, il parcourait tous les fichiers de test **y
/// compris le sien**, dont la documentation nommait précisément les cinq débiteurs. Il les certifiait
/// donc gardés : mesuré, il n'en nommait aucun, et rien n'aurait pu le faire rougir. Il s'exclut
/// désormais de son corpus (ADR 3645), les cinq gardes ont été écrites, et le plafond de dette a laissé
/// place à une invariante : la liste doit être **vide**.
class AnnonceDesMutationsTest {

    private static final Path SOURCES = Path.of("src", "main", "java");
    private static final Path TESTS = Path.of("src", "test", "java");

    private static final String ANNONCE = "mutationStructurelleValidee";

    /// Le port et son implémentation : ils **portent** l'annonce, ils ne l'émettent pas.
    private static final List<String> HORS_POPULATION = List.of("JournalMutations", "RevisionDonnees");

    /// Écritures par DAO sur les quatre entités comptées.
    private static final Pattern ECRITURE_DAO = Pattern.compile(
            "(siteDao|pointDao|passageDao|observationDao|observationsDao)\\s*\\.\\s*(insert|delete|supprimer)");

    /// Les mêmes écritures en **SQL brut**, que nulle règle sur les DAO ne verra.
    private static final Pattern ECRITURE_SQL = Pattern.compile(
            "(?i)(INSERT INTO|DELETE FROM)\\s+(monitoring_site|listening_point|survey_visit|observation)");

    /// Le détecteur lit du **texte**, et ce fichier-ci en est. Sans cette exclusion, nommer une classe
    /// dans la documentation ci-dessus suffisait à la déclarer gardée, puisque le fichier contient par
    /// construction le nom du port. Le cliquet de dette livré le 15/08/2026 était vacant pour cette
    /// seule raison : mesuré, il ne nommait **aucun** débiteur sur cinq. Cf. ADR 3645.
    private static final String SOI_MEME = "AnnonceDesMutationsTest.java";

    /// Relevé du 15/08/2026 : 38 écritures par DAO et 21 en SQL brut.
    ///
    /// ⚠️ Mon premier relevé, fait au shell, en annonçait **36** : `grep -c` compte des **lignes**, pas
    /// des occurrences, et deux lignes en portaient deux. C'est ce test-ci qui l'a corrigé - le
    /// quatrième inventaire de cet invariant, et le troisième à démentir le précédent.
    private static final int SITES_D_ECRITURE = 59;

    @Test
    @DisplayName("#3645 : toute classe qui annonce a un test qui compte son annonce")
    void toute_annonce_est_tenue_par_un_test() throws IOException {
        List<String> annoncent = classesQuiAnnoncent();

        // Non-vacuité : un détecteur qui ne voit plus personne passerait au vert en silence.
        assertThat(annoncent)
                .as("le détecteur ne voit plus AUCUNE classe annoncer : c'est lui qui est cassé")
                .isNotEmpty();

        List<String> sansGarde = annoncent.stream()
                .filter(classe -> !unTestCompteLAnnonce(classe))
                .toList();

        assertThat(sansGarde)
                .as(
                        "une classe qui annonce doit avoir un test qui **nomme** `JournalMutations`, lui en"
                                + " injecte un qui compte, et affirme l'appel. Un lambda anonyme `() -> {}` avale"
                                + " l'annonce ; un `() -> compteur[0]++` non typé la compte sans que ce détecteur"
                                + " puisse le voir. Sans garde : %s",
                        sansGarde)
                .isEmpty();
    }

    @Test
    @DisplayName("#3645 : tout nouveau site d'écriture structurelle force une relecture")
    void tout_site_d_ecriture_est_vu() throws IOException {
        int parDao = compter(ECRITURE_DAO);
        int parSql = compter(ECRITURE_SQL);

        assertThat(parDao + parSql)
                .as(
                        "une écriture structurelle a été ajoutée ou retirée (%d par DAO, %d en SQL brut)."
                                + " Ouvrez-la : annonce-t-elle ? Puis ajustez SITES_D_ECRITURE. Cf. ADR 3537.",
                        parDao, parSql)
                .isEqualTo(SITES_D_ECRITURE);

        // Non-vacuité, sur chaque moitié : le SQL brut est précisément ce qu'un garde naïf perd de vue.
        assertThat(parSql)
                .as("le détecteur ne voit plus AUCUNE écriture en SQL brut : c'est lui qui est cassé")
                .isPositive();
    }

    private static List<String> classesQuiAnnoncent() throws IOException {
        return fichiers(SOURCES).stream()
                .filter(chemin -> !chemin.toString().contains("/outils/"))
                .filter(chemin -> lire(chemin).contains(ANNONCE))
                .map(AnnonceDesMutationsTest::nomDeClasse)
                .filter(classe -> !HORS_POPULATION.contains(classe))
                .sorted()
                .toList();
    }

    /// Vrai si un test cite la classe **et** nomme le journal. Approximation assumée, et elle se trompe
    /// **dans les deux sens** :
    ///
    /// - elle **surestime** la couverture quand un test cite la classe pour une autre raison ;
    /// - elle la **sous-estime** quand la garde existe mais passe un lambda anonyme. `BaseNeuveTest`
    ///   comptait l'annonce depuis le lot 1 sans jamais prononcer le nom du port : le détecteur
    ///   l'accusait d'une dette qu'elle n'avait pas.
    ///
    /// La première version de ce doc-comment affirmait que seule la surestimation était possible. C'était
    /// faux, et de la moitié dangereuse : une dette surévaluée donne du mou à un cliquet.
    ///
    /// D'où la convention, écrite dans le message d'échec : **un garde nomme le port**.
    private static boolean unTestCompteLAnnonce(String classe) {
        try {
            return fichiers(TESTS).stream()
                    .filter(chemin -> !chemin.getFileName().toString().equals(SOI_MEME))
                    .anyMatch(chemin -> {
                        String texte = lire(chemin);
                        return texte.contains(classe)
                                && (texte.contains(ANNONCE) || texte.contains("JournalMutations"));
                    });
        } catch (IOException echec) {
            throw new UncheckedIOException(echec);
        }
    }

    private static int compter(Pattern motif) throws IOException {
        int total = 0;
        for (Path chemin : fichiers(SOURCES)) {
            Matcher trouve = motif.matcher(lire(chemin));
            while (trouve.find()) {
                total++;
            }
        }
        return total;
    }

    private static List<Path> fichiers(Path racine) throws IOException {
        try (Stream<Path> arbre = Files.walk(racine)) {
            return arbre.filter(chemin -> chemin.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (UncheckedIOException echec) {
            throw echec.getCause();
        }
    }

    private static String nomDeClasse(Path chemin) {
        String nom = chemin.getFileName().toString();
        return nom.substring(0, nom.length() - ".java".length());
    }

    private static String lire(Path chemin) {
        try {
            return Files.readString(chemin, StandardCharsets.UTF_8);
        } catch (IOException echec) {
            throw new UncheckedIOException(echec);
        }
    }
}
