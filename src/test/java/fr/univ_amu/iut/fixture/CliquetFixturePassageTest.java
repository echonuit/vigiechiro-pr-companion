package fr.univ_amu.iut.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Le cliquet de la dette de fixtures** (#1258) : la liste des tests qui sèment encore la topologie d'une
/// nuit **à la main** est **épinglée ici**, et elle ne peut que **rétrécir**.
///
/// ## Pourquoi un cliquet
///
/// La migration vers [JeuDeDonneesPassage] est **opportuniste** : on bascule un fichier quand on le
/// retouche, parce qu'une conversion mécanique en masse est risquée (trois styles SQL, jeux de colonnes
/// variables) et qu'un test converti trop vite est un test qu'on ne relit plus.
///
/// Mais **une migration opportuniste sans garde-fou est une migration qu'on oublie.** C'est exactement le
/// défaut que `DocumentationAJourTest` a corrigé ailleurs (#1458) : la doc dérivait parce que **rien ne
/// rougissait**. Une dette qu'aucun test ne compte n'est pas une dette, c'est un vœu.
///
/// ## Ce qu'il fait rougir
///
/// - **Un nouveau semeur à la main** : la liste s'allonge → **CI rouge**. Le message renvoie vers la
///   fixture. C'est le cas qui compte le plus : sans lui, la dette **repousserait** aussi vite qu'on la
///   coupe.
/// - **Une migration réussie** : la liste raccourcit → **CI rouge** aussi, jusqu'à ce qu'on **retire le
///   nom**. Le geste est trivial, et il rend le progrès **visible** : le compteur qui descend est la seule
///   preuve que le chantier avance.
///
/// Le nombre restant est donc **toujours exact**, et personne n'a besoin de s'en souvenir.
class CliquetFixturePassageTest {

    /// Surefire s'exécute depuis la racine du projet.
    private static final Path TESTS = Path.of("src", "test", "java");

    /// Les 64 tests qui sèment encore un passage à la main, au 2026-07-15.
    ///
    /// **Cette liste ne doit que rétrécir.** Pour en retirer un : basculer son semis sur
    /// [JeuDeDonneesPassage], puis supprimer sa ligne ici.
    private static final List<String> SEMENT_ENCORE_A_LA_MAIN = List.of(
            "fr/univ_amu/iut/audit/model/ServiceAuditCoherenceTest.java",
            "fr/univ_amu/iut/audit/model/ServiceRecuperabiliteTest.java",
            "fr/univ_amu/iut/audit/view/AuditNavigationViewTest.java",
            "fr/univ_amu/iut/bibliotheque/ServiceBibliothequeApprovalTest.java",
            "fr/univ_amu/iut/bibliotheque/ServiceBibliothequeTest.java",
            "fr/univ_amu/iut/cli/CliResetGuideTest.java",
            "fr/univ_amu/iut/cli/CliRetroEmpreintesTest.java",
            "fr/univ_amu/iut/commun/model/CycleTraitementIntegrationTest.java",
            "fr/univ_amu/iut/commun/model/dao/ReleveTraitementDaoTest.java",
            "fr/univ_amu/iut/commun/persistence/BackfillVerdictMigrationTest.java",
            "fr/univ_amu/iut/diagnostic/DiagnosticModuleTest.java",
            "fr/univ_amu/iut/diagnostic/ServiceDiagnosticTest.java",
            "fr/univ_amu/iut/e2e/ParcoursAnalyseVersValidationE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursDepotE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursFicheEspeceE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursImporterNuitE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursPassageVersValidationE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursPremiereNuitE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursRegrouperNuitsParPointE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursResetE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursRestaurationDepuisVigieChiroE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursValidationExpertE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursVerifierEchantillonnageE2ETest.java",
            "fr/univ_amu/iut/e2e/RetourApresVerificationE2ETest.java",
            "fr/univ_amu/iut/importation/ServiceImportTest.java",
            "fr/univ_amu/iut/importation/view/ImportationVueIntegrationTest.java",
            "fr/univ_amu/iut/lot/DepotUniteDaoTest.java",
            "fr/univ_amu/iut/lot/DepotVigieChiroTest.java",
            "fr/univ_amu/iut/lot/LotModuleTest.java",
            "fr/univ_amu/iut/lot/ServiceLotTest.java",
            "fr/univ_amu/iut/lot/VerificationCoherenceTest.java",
            "fr/univ_amu/iut/passage/BackfillEmpreintesTest.java",
            "fr/univ_amu/iut/passage/BackfillHorodatageCaptureTest.java",
            "fr/univ_amu/iut/passage/EnregistrementOriginalDaoTest.java",
            "fr/univ_amu/iut/passage/JournalDuCapteurDaoTest.java",
            "fr/univ_amu/iut/passage/MaterielMicroDaoTest.java",
            "fr/univ_amu/iut/passage/PassageDaoTest.java",
            "fr/univ_amu/iut/passage/ReleveClimatiqueDaoTest.java",
            "fr/univ_amu/iut/passage/SequenceDaoTest.java",
            "fr/univ_amu/iut/passage/ServicePassageDetailTest.java",
            "fr/univ_amu/iut/passage/ServicePassageTest.java",
            "fr/univ_amu/iut/passage/SessionDaoTest.java",
            "fr/univ_amu/iut/passage/dao/RattachementDaoTest.java",
            "fr/univ_amu/iut/passage/model/ServiceDisponibiliteAudioTest.java",
            "fr/univ_amu/iut/passage/model/ServiceReactivationPassageTest.java",
            "fr/univ_amu/iut/passage/model/ServiceReconstructionPassagesTest.java",
            "fr/univ_amu/iut/qualification/SelectionDaoTest.java",
            "fr/univ_amu/iut/qualification/ServiceQualificationTest.java",
            "fr/univ_amu/iut/sites/ServiceSitesTest.java",
            "fr/univ_amu/iut/sites/model/RapprochementSitesTest.java",
            "fr/univ_amu/iut/sites/view/SiteDetailSuppressionsViewTest.java",
            "fr/univ_amu/iut/sites/view/SiteDetailVersPassageViewTest.java",
            "fr/univ_amu/iut/sites/viewmodel/PointEditViewModelTest.java",
            "fr/univ_amu/iut/sites/viewmodel/SiteDetailViewModelTest.java",
            "fr/univ_amu/iut/sites/viewmodel/SitesViewModelTest.java",
            "fr/univ_amu/iut/validation/ObservationDaoTest.java",
            "fr/univ_amu/iut/validation/PublicationMessageTest.java",
            "fr/univ_amu/iut/validation/ResultatsIdentificationDaoTest.java",
            "fr/univ_amu/iut/validation/SaisieCertitudeTest.java",
            "fr/univ_amu/iut/validation/ServiceValidationTest.java",
            "fr/univ_amu/iut/validation/ValidationExpertTest.java",
            "fr/univ_amu/iut/validation/ValidationManuelleTest.java");

    @Test
    @DisplayName("La dette de fixtures ne peut que rétrécir : aucun nouveau semeur à la main, et toute"
            + " migration se solde en retirant son nom de la liste")
    void la_dette_ne_peut_que_retrecir() {
        List<String> reels = semeursALaMain();

        assertThat(reels).as("""
                        La liste des tests qui sèment un passage À LA MAIN a changé.

                        • Elle s'ALLONGE ? Vous venez d'ajouter un semeur de plus (il y en a déjà assez).
                          Utilisez fr.univ_amu.iut.fixture.JeuDeDonneesPassage :

                              JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source).semer();
                              long idObservation = jeu.ajouterObservation("Pipkuh");

                        • Elle RACCOURCIT ? Bravo, vous venez d'en migrer un : retirez son nom de
                          SEMENT_ENCORE_A_LA_MAIN, dans ce fichier. C'est le seul geste qui rend le
                          progrès visible.

                        Pourquoi ce cliquet : la migration est opportuniste (on bascule un fichier quand
                        on le retouche), et une migration opportuniste sans garde-fou est une migration
                        qu'on oublie. Une dette qu'aucun test ne compte n'est pas une dette, c'est un vœu.
                        """).containsExactlyInAnyOrderElementsOf(SEMENT_ENCORE_A_LA_MAIN);
    }

    /// Un test **sème un passage à la main** dès qu'il en crée un lui-même : par `INSERT INTO passage`
    /// (les trois styles SQL de l'audit) ou par le DAO typé. C'est le **passage** qu'on guette, parce que
    /// c'est lui qui entraîne toute la chaîne derrière (session, séquence, observation…).
    private static List<String> semeursALaMain() {
        try (Stream<Path> fichiers = Files.walk(TESTS)) {
            return fichiers.filter(fichier -> fichier.toString().endsWith(".java"))
                    .filter(CliquetFixturePassageTest::semeUnPassage)
                    .map(fichier -> TESTS.relativize(fichier).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException echec) {
            throw new UncheckedIOException("parcours de " + TESTS, echec);
        }
    }

    /// Nommer [JeuDeDonneesPassage] vaut **migration** : c'est ce qui écarte du compte les fichiers
    /// basculés - et, au passage, la fixture elle-même et ce cliquet, qui la nomment tous deux.
    ///
    /// **La limite est assumée** : un fichier qui utiliserait la fixture **et** sèmerait en plus un second
    /// passage à la main passerait à travers. On l'accepte - le détecteur doit rester lisible, et ce cas
    /// n'existe pas aujourd'hui.
    private static boolean semeUnPassage(Path fichier) {
        String source = lire(fichier);
        if (source.contains("JeuDeDonneesPassage")) {
            return false;
        }
        String minuscules = source.toLowerCase(Locale.ROOT);
        return minuscules.contains("insert into passage") || source.contains("new PassageDao(");
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier);
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + fichier, echec);
        }
    }
}
