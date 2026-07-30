package fr.univ_amu.iut.cliquet;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Cliquet du journal en dur** (#2868) : les fichiers qui écrivent un journal `LogPR` littéral.
///
/// ## Pourquoi ça compte
///
/// Le format du journal (`JJ/MM/AA - HH:MM:SS PR<série> …`) est celui que produit un vrai enregistreur.
/// Le recopier, c'est autant d'endroits à retoucher le jour où un champ bouge, et autant d'occasions
/// d'en oublier un, sans que rien ne le dise.
///
/// La destination est `fr.univ_amu.iut.fixture.JournalDeCapteur`, extrait de `GenerateurCartesSD` par
/// #2904 - le générateur de recette s'en sert désormais lui aussi, une seule source pour deux usages.
///
/// ## Ce qui est hors mesure, et pourquoi c'est écrit
///
/// `AnalyseurLogPRTest` et `InspecteurDossierTest` testent les **analyseurs** de ce format : leur donner
/// un générateur reviendrait à tester le générateur contre lui-même. Le paquet `recette` est la
/// **source** de la brique, pas sa dette.
///
/// Le paquet `fixture` non plus : `JournalDeCapteur` compose évidemment un nom de journal, c'est son
/// métier et c'est la **destination** de la migration. L'exclusion manquait à la pose de ce cliquet, où
/// le paquet n'écrivait encore aucun journal - la destination n'existait pas. Elle est écrite maintenant,
/// plutôt que laissée à la chance.
class CliquetJournalEnDurTest {

    /// Une **ligne de journal écrite à la main** : l'horodatage que produit un vrai enregistreur, suivi de
    /// `PR` et du numéro de série - `22/04/26 - 16:02:20 PR1925492 …`.
    ///
    /// ⚠️ **Correction de la mesure** (ADR 2867, la confusion usage / mention). Ce motif cherchait
    /// autrefois le **nom** du fichier (`"LogPR…"`), et comptait donc neuf fichiers qui n'écrivent aucun
    /// journal :
    ///
    ///  - `ServiceLotTest`, `VerificationCoherenceTest`, `PassageDaoTest`, `JournalDuCapteurDaoTest`
    ///    insèrent une **ligne en base** portant un nom de fichier : rien n'est écrit sur le disque ;
    ///  - `CliAuditTest` et `PropositionsEnregistreurTest` **composent un chemin**, l'un pour dire qu'il
    ///    manque, l'autre dans un bouchon ;
    ///  - `ServiceAuditCoherenceTest`, `CopieProtegeeTest` et `ExtracteurZipTest` écrivent bien un fichier,
    ///    mais **volontairement pas un journal** : seize octets nuls, ou le mot « journal ». Ils éprouvent
    ///    la corruption, la copie et l'extraction - leur donner un vrai journal détruirait leur sujet.
    ///
    /// Aucun de ces neuf n'avait quoi que ce soit à migrer. Un cliquet qui surcompte se décrédibilise
    /// aussi sûrement qu'un qui sous-compte, et il fait pire : il donne à croire qu'un chantier est plus
    /// gros qu'il n'est, ce qui décourage de le finir.
    private static final Pattern JOURNAL_COMPOSE = Pattern.compile("\\d\\d/\\d\\d/\\d\\d - \\d\\d:\\d\\d:\\d\\d PR");

    /// La dette épinglée. **Ne peut que rétrécir** : cf. [Cliquet] pour les deux sens de variation.
    private static final List<String> ECRIVENT_UN_JOURNAL_EN_DUR = List.of(
            "fr/univ_amu/iut/cli/CliImportTest.java",
            "fr/univ_amu/iut/diagnostic/AnalyseAnomaliesTest.java",
            "fr/univ_amu/iut/diagnostic/ServiceDiagnosticTest.java",
            "fr/univ_amu/iut/e2e/ParcoursImporterNuitE2ETest.java",
            "fr/univ_amu/iut/importation/ServiceImportTest.java",
            "fr/univ_amu/iut/importation/viewmodel/ImportationViewModelTest.java",
            "fr/univ_amu/iut/importation/viewmodel/InspectionImportViewModelTest.java");

    @Test
    @DisplayName("La dette du journal en dur ne peut que rétrécir : aucun nouveau littéral")
    void la_dette_ne_peut_que_retrecir() {
        Cliquet.verifier(
                Cliquet.fichiersOu(CliquetJournalEnDurTest::ecritUnJournalEnDur),
                ECRIVENT_UN_JOURNAL_EN_DUR,
                "les tests qui écrivent un journal LogPR en dur",
                "fr.univ_amu.iut.fixture.JournalDeCapteur",
                "ECRIVENT_UN_JOURNAL_EN_DUR, dans ce fichier");
    }

    private static boolean ecritUnJournalEnDur(Cliquet.Fichier fichier) {
        if (fichier.dansLePaquet("cliquet") || fichier.dansLePaquet("recette") || fichier.dansLePaquet("fixture")) {
            return false;
        }
        String nom = fichier.chemin().getFileName().toString();
        if (nom.equals("AnalyseurLogPRTest.java") || nom.equals("InspecteurDossierTest.java")) {
            return false;
        }
        return JOURNAL_COMPOSE.matcher(fichier.source()).find();
    }
}
