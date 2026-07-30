package fr.univ_amu.iut.cliquet;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Cliquet de la capture stdout** (#2866) : les fichiers qui montent leur propre `ByteArrayOutputStream`
/// pour lire ce qu'une commande a écrit.
///
/// ## Pourquoi ça compte
///
/// C'est le même échafaudage recopié : un tampon, un `PrintStream` en UTF-8, un workspace jetable, une
/// base migrée. Rien de tout cela n'est propre au test qui le monte, et chaque copie est une occasion de
/// diverger — un encodage oublié, un flux non vidé, un nettoyage manquant.
///
/// ## Périmètre
///
/// Ce cliquet ne compte **que** les tests Java. Les fixtures partagées côté `bats` relèvent de #1592 :
/// deux couches distinctes, découpe écrite des deux côtés.
class CliquetCaptureStdoutTest {

    /// La dette épinglée. **Ne peut que rétrécir** : cf. [Cliquet] pour les deux sens de variation.
    private static final List<String> MONTENT_LEUR_PROPRE_CAPTURE = List.of(
            "fr/univ_amu/iut/cli/CliAuditTest.java",
            "fr/univ_amu/iut/cli/CliCampagneTest.java",
            "fr/univ_amu/iut/cli/CliDiscussionTest.java",
            "fr/univ_amu/iut/cli/CliEmplacementsTest.java",
            "fr/univ_amu/iut/cli/CliExportVuTest.java",
            "fr/univ_amu/iut/cli/CliExporterActiviteTest.java",
            "fr/univ_amu/iut/cli/CliGestesRevueTest.java",
            "fr/univ_amu/iut/cli/CliImportTest.java",
            "fr/univ_amu/iut/cli/CliImportTransformesTest.java",
            "fr/univ_amu/iut/cli/CliListerObservationsTest.java",
            "fr/univ_amu/iut/cli/CliMarquageRevueTest.java",
            "fr/univ_amu/iut/cli/CliRattacherCampagneTest.java",
            "fr/univ_amu/iut/cli/CliRattraperCommunesTest.java",
            "fr/univ_amu/iut/cli/CliReactiverTest.java",
            "fr/univ_amu/iut/cli/CliResetGuideTest.java",
            "fr/univ_amu/iut/cli/CliRetroEmpreintesTest.java",
            "fr/univ_amu/iut/cli/CliSauvegardeTest.java",
            "fr/univ_amu/iut/cli/CliSoldeSaisonTest.java",
            "fr/univ_amu/iut/cli/CliStatutRecupereTest.java",
            "fr/univ_amu/iut/cli/CliSupprimerPassageTest.java",
            "fr/univ_amu/iut/cli/CliSynthetiserPassageTest.java",
            "fr/univ_amu/iut/cli/CliTest.java",
            "fr/univ_amu/iut/importation/TransformationAudioTest.java",
            "fr/univ_amu/iut/recette/RecetteImportCliGoldenTest.java");

    @Test
    @DisplayName("La dette des captures stdout ne peut que rétrécir : aucun nouvel échafaudage")
    void la_dette_ne_peut_que_retrecir() {
        Cliquet.verifier(
                Cliquet.fichiersOu(CliquetCaptureStdoutTest::monteSaPropreCapture),
                MONTENT_LEUR_PROPRE_CAPTURE,
                "les tests qui montent leur propre capture de sortie",
                "le harnais de capture partagé",
                "MONTENT_LEUR_PROPRE_CAPTURE, dans ce fichier");
    }

    /// Construction d'un tampon, **qualifiée ou non**.
    ///
    /// La première version cherchait la chaîne littérale `new ByteArrayOutputStream` et ratait
    /// `new java.io.ByteArrayOutputStream(…)`. Trouvé par la sonde qui vérifiait que ce cliquet voit une
    /// copie apparaître : il ne la voyait pas. Un détecteur trop littéral est un détecteur qui se tait.
    private static final Pattern TAMPON = Pattern.compile("new\\s+(?:[\\w.]+\\.)?ByteArrayOutputStream\\s*\\(");

    /// Un tampon **monté dans un `@BeforeEach`** : c'est la forme de l'échafaudage recopié. Un
    /// `ByteArrayOutputStream` construit au fil d'un test isolé sert souvent à autre chose.
    private static boolean monteSaPropreCapture(Cliquet.Fichier fichier) {
        if (fichier.dansLePaquet("cliquet")) {
            return false;
        }
        return TAMPON.matcher(fichier.source()).find() && fichier.source().contains("@BeforeEach");
    }
}
