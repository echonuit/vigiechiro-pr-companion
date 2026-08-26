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
/// diverger : un encodage oublié, un flux non vidé, un nettoyage manquant.
///
/// ## Périmètre
///
/// Ce cliquet ne compte **que** les tests Java. Les fixtures partagées côté `bats` relèvent de #1592 :
/// deux couches distinctes, découpe écrite des deux côtés.
class CliquetCaptureStdoutTest {

    /// La dette épinglée : **elle est vide**, et le cliquet reste.
    ///
    /// Son rôle a changé le jour où le dernier fichier a été migré : il ne compte plus une dette, il
    /// empêche qu'elle renaisse. C'est le second axe du chantier #1771 à atteindre zéro.
    private static final List<String> MONTENT_LEUR_PROPRE_CAPTURE = List.of();

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

    /// Un tampon **branché sur un flux d'impression** : c'est cela, capturer une sortie.
    ///
    /// **Correction de la mesure, pas migration** (ADR 2867, la confusion usage / mention). La version
    /// précédente se contentait d'un tampon et d'un `@BeforeEach` quelque part dans le fichier, et
    /// comptait donc `TransformationAudioTest` - qui empile du **PCM audio** dans un
    /// `ByteArrayOutputStream` pour vérifier que la concaténation des séquences reconstitue la source. Ce
    /// fichier n'a jamais capturé la moindre sortie : il n'écrit pas une seule ligne de texte, et ne
    /// construit aucun `PrintStream`.
    ///
    /// Il sort donc de la liste **sans avoir été touché**. Un cliquet qui surcompte se décrédibilise
    /// aussi sûrement qu'un qui sous-compte, et le distinguer d'une migration réussie est exactement ce
    /// que le message du patron réclame.
    private static boolean monteSaPropreCapture(Cliquet.Fichier fichier) {
        // Le paquet `fixture` est la DESTINATION de la migration : `SortieCapturee` monte forcément le
        // tampon que tous les autres cessent de monter.
        if (fichier.dansLePaquet("cliquet") || fichier.dansLePaquet("fixture")) {
            return false;
        }
        return TAMPON.matcher(fichier.source()).find()
                && FLUX_D_IMPRESSION.matcher(fichier.source()).find();
    }

    /// Le tampon est-il **branché sur un flux** ? Sans cela, ce n'est pas une capture de sortie.
    private static final Pattern FLUX_D_IMPRESSION = Pattern.compile("new\\s+(?:[\\w.]+\\.)?PrintStream\\s*\\(");
}
