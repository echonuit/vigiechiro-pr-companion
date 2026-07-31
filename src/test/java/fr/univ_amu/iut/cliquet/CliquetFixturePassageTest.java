package fr.univ_amu.iut.cliquet;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Le cliquet de la dette de fixtures** (#1258) : la liste des tests qui sèment encore la topologie d'une
/// nuit **à la main** est **épinglée ici**, et elle ne peut que **rétrécir**.
///
/// C'est le premier cliquet du dépôt, et celui sur lequel les deux pièges du patron ont été rencontrés
/// pour de vrai (#2714). Il passe désormais par [Cliquet], comme les autres, et vit dans le même paquet :
/// le patron et ses règles sont décrits par l'ADR 2867.
///
/// ## Pourquoi un cliquet
///
/// La migration vers `JeuDeDonneesPassage` est **opportuniste** : on bascule un fichier quand on le
/// retouche, parce qu'une conversion mécanique en masse est risquée (trois styles SQL, jeux de colonnes
/// variables) et qu'un test converti trop vite est un test qu'on ne relit plus.
///
/// Mais **une migration opportuniste sans garde-fou est une migration qu'on oublie.** C'est exactement le
/// défaut que `DocumentationAJourTest` a corrigé ailleurs (#1458) : la doc dérivait parce que **rien ne
/// rougissait**. Une dette qu'aucun test ne compte n'est pas une dette, c'est un vœu.
class CliquetFixturePassageTest {

    /// Les tests qui sèment encore un passage à la main.
    ///
    /// **Cette liste ne doit que rétrécir.** Pour en retirer un : basculer son semis sur
    /// `JeuDeDonneesPassage`, puis supprimer sa ligne ici. Aucun chiffre n'est écrit ici à dessein :
    /// l'annotation précédente en annonçait 64 pour 50 lignes, ce qui est le sort de tout nombre recopié.
    private static final List<String> SEMENT_ENCORE_A_LA_MAIN = List.of(
            "fr/univ_amu/iut/bibliotheque/ServiceBibliothequeApprovalTest.java",
            "fr/univ_amu/iut/bibliotheque/ServiceBibliothequeTest.java",
            "fr/univ_amu/iut/commun/model/dao/ReleveTraitementDaoTest.java",
            "fr/univ_amu/iut/commun/persistence/BackfillVerdictMigrationTest.java",
            "fr/univ_amu/iut/diagnostic/ServiceDiagnosticTest.java",
            "fr/univ_amu/iut/e2e/ParcoursAnalyseVersValidationE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursFicheEspeceE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursRegrouperNuitsParPointE2ETest.java",
            "fr/univ_amu/iut/e2e/RetourApresVerificationE2ETest.java",
            "fr/univ_amu/iut/importation/view/ImportationVueIntegrationTest.java",
            "fr/univ_amu/iut/lot/DepotUniteDaoTest.java",
            "fr/univ_amu/iut/passage/PassageDaoTest.java",
            "fr/univ_amu/iut/passage/ServicePassageTest.java",
            "fr/univ_amu/iut/passage/model/ServiceReactivationPassageTest.java",
            "fr/univ_amu/iut/qualification/SelectionDaoTest.java",
            "fr/univ_amu/iut/sites/ServiceSitesTest.java",
            "fr/univ_amu/iut/sites/view/SiteDetailSuppressionsViewTest.java",
            "fr/univ_amu/iut/sites/viewmodel/SiteDetailViewModelTest.java",
            "fr/univ_amu/iut/sites/viewmodel/SitesViewModelTest.java",
            "fr/univ_amu/iut/validation/ObservationDaoTest.java",
            "fr/univ_amu/iut/validation/ResultatsIdentificationDaoTest.java",
            "fr/univ_amu/iut/validation/SaisieCertitudeTest.java",
            "fr/univ_amu/iut/validation/ValidationManuelleTest.java");

    @Test
    @DisplayName("La dette de fixtures ne peut que rétrécir : aucun nouveau semeur à la main, et toute"
            + " migration se solde en retirant son nom de la liste")
    void la_dette_ne_peut_que_retrecir() {
        Cliquet.verifier(
                Cliquet.fichiersOu(CliquetFixturePassageTest::semeUnPassage),
                SEMENT_ENCORE_A_LA_MAIN,
                "les tests qui sèment un passage À LA MAIN",
                """
                fr.univ_amu.iut.fixture.JeuDeDonneesPassage :

                      JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source).semer();
                      long idObservation = jeu.ajouterObservation("Pipkuh")""",
                "SEMENT_ENCORE_A_LA_MAIN, dans ce fichier");
    }

    /// Ce fichier **sème-t-il encore un passage à la main** ?
    ///
    /// Un test sème un passage dès qu'il en crée un lui-même : par `INSERT INTO passage` (les trois styles
    /// SQL de l'audit) ou par le DAO typé. C'est le **passage** qu'on guette, parce que c'est lui qui
    /// entraîne toute la chaîne derrière (session, séquence, observation…).
    ///
    /// La question porte sur ce que le fichier **fait**, pas sur ce qu'il mentionne. Le détecteur
    /// s'arrêtait autrefois au premier `JeuDeDonneesPassage` rencontré et rendait `false` : un fichier
    /// **partiellement** migré sortait donc de la liste, avec le semis à la main qui y restait. Le
    /// cliquet devenait aveugle exactement sur les fichiers en cours de migration - c'est-à-dire là où
    /// il devait parler - et son silence se lisait comme « migré » (#2714).
    ///
    /// Il n'y a donc plus de court-circuit : un fichier compte tant qu'il lui reste **un** semis.
    ///
    /// **La limite est assumée** : un fichier qui utiliserait la fixture **et** sèmerait en plus un second
    /// passage à la main compterait, ce qui est juste ; l'inverse - une migration partielle qu'on croirait
    /// finie - est ce qu'on refuse.
    private static boolean semeUnPassage(Cliquet.Fichier fichier) {
        // `fixture` est la DESTINATION de la migration : y semer un passage est le métier de
        // `JeuDeDonneesPassage`. `cliquet` est la MESURE : ce fichier-ci cite « insert into passage » et
        // `new PassageDao(` dans ses propres motifs, et se compterait lui-même. Les deux exclusions
        // tenaient autrefois par accident (le court-circuit du détecteur les couvrait) ; elles sont
        // écrites depuis #2714, et ce changement de paquet montre pourquoi : un effet de bord fondé sur
        // l'emplacement d'un fichier ne survit pas à son déplacement.
        if (fichier.dansLePaquet("fixture") || fichier.dansLePaquet("cliquet")) {
            return false;
        }
        String source = fichier.source();
        return source.toLowerCase(Locale.ROOT).contains("insert into passage") || insereViaLeDao(source);
    }

    /// `new PassageDao(source)` **écrit-il** un passage, ou se contente-t-il de lire ?
    ///
    /// La présence du DAO ne prouve rien : `new PassageDao(source).findAll()` relit ce qu'un import a
    /// créé, il ne sème pas. Compter ces lectures maintenait dans la liste des fichiers qui n'y avaient
    /// plus leur place - un cliquet qui surcompte se décrédibilise aussi sûrement qu'un qui sous-compte.
    ///
    /// On cherche donc un `insert(` porté par le DAO, sous ses deux formes d'usage : appelé directement
    /// sur la construction, ou via la variable où elle a été rangée.
    private static boolean insereViaLeDao(String source) {
        if (DAO_INSERE_DIRECTEMENT.matcher(source).find()) {
            return true;
        }
        Matcher variable = DAO_RANGE_DANS_UNE_VARIABLE.matcher(source);
        while (variable.find()) {
            if (Pattern.compile("\\b" + Pattern.quote(variable.group(1)) + "\\s*\\.\\s*insert\\s*\\(")
                    .matcher(source)
                    .find()) {
                return true;
            }
        }
        return false;
    }

    /// `new PassageDao(source).insert(...)`, en une seule expression.
    private static final Pattern DAO_INSERE_DIRECTEMENT =
            Pattern.compile("new\\s+PassageDao\\s*\\([^)]*\\)\\s*\\.\\s*insert\\s*\\(");

    /// `PassageDao xxx = new PassageDao(source);` - le nom de la variable est capturé pour aller voir,
    /// ailleurs dans le fichier, si on lui demande un `insert`.
    private static final Pattern DAO_RANGE_DANS_UNE_VARIABLE =
            Pattern.compile("(\\w+)\\s*=\\s*new\\s+PassageDao\\s*\\(");
}
