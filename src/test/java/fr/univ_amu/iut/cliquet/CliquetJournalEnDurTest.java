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
/// La destination est la brique paramétrée que porte déjà `GenerateurCartesSD`, dans le paquet `recette`.
///
/// ## Ce qui est hors mesure, et pourquoi c'est écrit
///
/// `AnalyseurLogPRTest` et `InspecteurDossierTest` testent les **analyseurs** de ce format : leur donner
/// un générateur reviendrait à tester le générateur contre lui-même. Le paquet `recette` est la
/// **source** de la brique, pas sa dette.
class CliquetJournalEnDurTest {

    /// Nom de fichier de journal composé dans le code : `"LogPR…"`, `"LogPR" + serie`, `LogPR1925492`.
    /// Une simple occurrence du mot dans un commentaire ne compte pas - c'est l'usage qui compte, pas la
    /// mention (cf. [Cliquet], les deux pièges du patron).
    private static final Pattern JOURNAL_COMPOSE = Pattern.compile("\"LogPR|LogPR\"\\s*\\+|LogPR\\d");

    /// La dette épinglée. **Ne peut que rétrécir** : cf. [Cliquet] pour les deux sens de variation.
    private static final List<String> ECRIVENT_UN_JOURNAL_EN_DUR = List.of(
            "fr/univ_amu/iut/audit/model/ServiceAuditCoherenceTest.java",
            "fr/univ_amu/iut/cli/CliAuditTest.java",
            "fr/univ_amu/iut/cli/CliImportTest.java",
            "fr/univ_amu/iut/diagnostic/AnalyseAnomaliesTest.java",
            "fr/univ_amu/iut/diagnostic/ServiceDiagnosticTest.java",
            "fr/univ_amu/iut/e2e/ParcoursDepotE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursImporterNuitE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursMultisiteVersPassageE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursPassageVersDiagnosticE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursPassageVersNonIdentifiesE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursPassageVersValidationE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursPremiereNuitE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursRestaurationDepuisVigieChiroE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursSitesVersPassageE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursVerifierEchantillonnageE2ETest.java",
            "fr/univ_amu/iut/importation/CopieProtegeeTest.java",
            "fr/univ_amu/iut/importation/ExtracteurZipTest.java",
            "fr/univ_amu/iut/importation/ServiceImportTest.java",
            "fr/univ_amu/iut/importation/SuiviFichiersImportTest.java",
            "fr/univ_amu/iut/importation/model/InventaireParInspectionTest.java",
            "fr/univ_amu/iut/importation/view/ImportationClicImporterTest.java",
            "fr/univ_amu/iut/importation/viewmodel/ImportationViewModelTest.java",
            "fr/univ_amu/iut/importation/viewmodel/InspectionImportViewModelTest.java",
            "fr/univ_amu/iut/lot/ServiceLotTest.java",
            "fr/univ_amu/iut/lot/VerificationCoherenceTest.java",
            "fr/univ_amu/iut/passage/JournalDuCapteurDaoTest.java",
            "fr/univ_amu/iut/passage/PassageDaoTest.java",
            "fr/univ_amu/iut/passage/model/PropositionsEnregistreurTest.java",
            "fr/univ_amu/iut/passage/model/ServiceReactivationPassageTest.java");

    @Test
    @DisplayName("La dette du journal en dur ne peut que rétrécir : aucun nouveau littéral")
    void la_dette_ne_peut_que_retrecir() {
        Cliquet.verifier(
                Cliquet.fichiersOu(CliquetJournalEnDurTest::ecritUnJournalEnDur),
                ECRIVENT_UN_JOURNAL_EN_DUR,
                "les tests qui écrivent un journal LogPR en dur",
                "la brique paramétrée de GenerateurCartesSD",
                "ECRIVENT_UN_JOURNAL_EN_DUR, dans ce fichier");
    }

    private static boolean ecritUnJournalEnDur(Cliquet.Fichier fichier) {
        if (fichier.dansLePaquet("cliquet") || fichier.dansLePaquet("recette")) {
            return false;
        }
        String nom = fichier.chemin().getFileName().toString();
        if (nom.equals("AnalyseurLogPRTest.java") || nom.equals("InspecteurDossierTest.java")) {
            return false;
        }
        return JOURNAL_COMPOSE.matcher(fichier.source()).find();
    }
}
