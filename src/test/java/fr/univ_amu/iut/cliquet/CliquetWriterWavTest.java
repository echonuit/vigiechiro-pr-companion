package fr.univ_amu.iut.cliquet;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Cliquet des writers WAV de test** (#2864) : les fichiers qui écrivent un en-tête RIFF **à la main**,
/// au `ByteBuffer`, au lieu d'appeler le writer de production.
///
/// ## Pourquoi ça compte
///
/// Un test qui écrit son propre en-tête teste un format que l'application n'utilise pas. S'ils
/// divergent, c'est le **test** qui a raison et personne ne le sait : il continue de passer sur un
/// fichier que le produit ne saurait pas relire.
///
/// La destination est `fr.univ_amu.iut.commun.model.FichierWav#ecrire` - le writer **de production**, pas
/// une fabrique de test. Sept fichiers l'utilisent déjà.
///
/// ## Ce qui est hors mesure, et pourquoi c'est écrit
///
/// `FichierWavTest` teste le **parseur**. Il a besoin d'écrire des en-têtes à la main, y compris
/// malformés : c'est son objet même. L'exclure par une règle vaut mieux que de le laisser exclu par
/// accident (cf. [Cliquet], les deux pièges du patron).
class CliquetWriterWavTest {

    /// La dette épinglée. **Ne peut que rétrécir** : cf. [Cliquet] pour les deux sens de variation.
    private static final List<String> ECRIVENT_UN_EN_TETE_A_LA_MAIN = List.of(
            "fr/univ_amu/iut/cli/CliImportTest.java",
            "fr/univ_amu/iut/cli/CliImportTransformesTest.java",
            "fr/univ_amu/iut/e2e/ParcoursDepotE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursImporterNuitE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursMultisiteVersPassageE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursPassageVersDiagnosticE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursPassageVersNonIdentifiesE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursPassageVersValidationE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursPremiereNuitE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursSitesVersPassageE2ETest.java",
            "fr/univ_amu/iut/e2e/ParcoursVerifierEchantillonnageE2ETest.java",
            "fr/univ_amu/iut/importation/ServiceImportReferenceTest.java",
            "fr/univ_amu/iut/importation/ServiceImportTest.java",
            "fr/univ_amu/iut/importation/SuiviFichiersImportTest.java",
            "fr/univ_amu/iut/importation/TransformationAudioTest.java",
            "fr/univ_amu/iut/importation/model/EquivalenceImportReactivationTest.java",
            "fr/univ_amu/iut/importation/model/NombreTranchesFideleTest.java",
            "fr/univ_amu/iut/importation/view/ActionImportTransformesTest.java",
            "fr/univ_amu/iut/importation/view/ImportationClicImporterTest.java",
            "fr/univ_amu/iut/passage/model/HydratationCollisionTest.java",
            "fr/univ_amu/iut/passage/model/ReliquatPlaceholderTest.java");

    @Test
    @DisplayName("La dette des writers WAV ne peut que rétrécir : aucun nouvel en-tête écrit à la main")
    void la_dette_ne_peut_que_retrecir() {
        Cliquet.verifier(
                Cliquet.fichiersOu(CliquetWriterWavTest::ecritUnEnTeteALaMain),
                ECRIVENT_UN_EN_TETE_A_LA_MAIN,
                "les tests qui écrivent un en-tête WAV à la main",
                "fr.univ_amu.iut.commun.model.FichierWav#ecrire",
                "ECRIVENT_UN_EN_TETE_A_LA_MAIN, dans ce fichier");
    }

    /// Écrire un en-tête RIFF demande un `ByteBuffer` : la conjonction des deux distingue l'**écriture**
    /// d'une simple mention du format dans un commentaire ou une assertion.
    private static boolean ecritUnEnTeteALaMain(Cliquet.Fichier fichier) {
        if (fichier.dansLePaquet("cliquet") || fichier.chemin().endsWith("FichierWavTest.java")) {
            return false;
        }
        return fichier.source().contains("RIFF") && fichier.source().contains("ByteBuffer");
    }
}
