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

/// Les deux cliquets de l'invariant « tu écris, tu signales » (#3645).
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
/// Ces deux cliquets tiennent donc une promesse **plus faible et vérifiable** : rien de nouveau n'entre
/// sans être vu, ni du côté des annonces, ni du côté des écritures.
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

    /// Relevé du 15/08/2026. **Cinq** classes annoncent sans qu'un test compte leur annonce :
    /// `BaseNeuve`, `CreationPassageArchive`, `NoyauImportObservations`, `ServiceImportReference`,
    /// `ValidationManuelle`. Les nommer ici est une trace de départ ; ce que le cliquet tient est le
    /// **nombre**, et c'est le message d'échec qui redonne les noms du jour.
    ///
    /// C'est un **cliquet de dette** (ADR 2843) : le nombre doit descendre, jamais monter. Un compteur
    /// et non une liste, l'ADR 3575 ayant montré qu'une liste de noms ne protège qu'elle-même.
    private static final int ANNONCES_SANS_GARDE = 5;

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
                        "le cliquet de #3645 descend, il ne monte pas. Une classe qui annonce doit avoir un"
                                + " test injectant un `JournalMutations` compteur et vérifiant l'appel. En dette : %s",
                        sansGarde)
                .hasSizeLessThanOrEqualTo(ANNONCES_SANS_GARDE);
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

    /// Vrai si un test cite la classe **et** manipule le journal. Approximation assumée : elle peut
    /// surestimer la couverture (un test de vue qui cite la classe pour une autre raison). Elle ne peut
    /// pas la sous-estimer, ce qui est le bon sens pour un cliquet qui doit descendre.
    private static boolean unTestCompteLAnnonce(String classe) {
        try {
            return fichiers(TESTS).stream().anyMatch(chemin -> {
                String texte = lire(chemin);
                return texte.contains(classe) && (texte.contains(ANNONCE) || texte.contains("JournalMutations"));
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
