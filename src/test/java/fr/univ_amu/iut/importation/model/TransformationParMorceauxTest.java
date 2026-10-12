package fr.univ_amu.iut.importation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.Prefixe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La transformation lit l'original par morceaux (#104) et produit **au bit près** ce que produisait la
/// lecture complète.
///
/// Deux attendus. Le signal de chaque séquence est comparé à la tranche que le test découpe lui-même dans
/// le PCM qu'il a écrit, sans passer par aucun code de production. Le fichier entier, en-tête compris, est
/// comparé à l'ancienne voie : [FichierWav#lire] charge le PCM entier, on le tranche tous les `5 × Fe`
/// trames et on écrit chaque tranche par [FichierWav#ecrire]. Un morceau perdu, décalé ou complété par les
/// octets de la tranche précédente change au moins un signal.
class TransformationParMorceauxTest {

    private static final int FREQUENCE = 2000; // Hz, divisible par 10 (R10)
    private static final int ENTETE_CANONIQUE = 44;
    private static final int OCTETS_PAR_TRANCHE = TransformationAudio.DUREE_SEQUENCE_SECONDES * FREQUENCE * 2;

    @TempDir
    Path dossier;

    private final Prefixe prefixe = new Prefixe("640380", 2026, 2, "Z1");

    @Test
    @DisplayName("#104 : chaque séquence est identique, octet par octet, à celle de la lecture complète")
    void sequences_identiques_a_la_lecture_complete() throws IOException {
        // Deux tranches pleines, une dernière plus courte et de longueur impaire : les frontières où un flux
        // perd ou décale un octet. Le chunk annexe avant `data` est éprouvé par `FichierWavTest`.
        byte[] pcm = pcmDeterministe(2 * OCTETS_PAR_TRANCHE + 12_345);
        Path original = dossier.resolve("PaRecPR1925492_20260422_203922.wav");
        ecrireWav(original, pcm);

        TransformationOriginal produit =
                new TransformationAudio().transformer(original, dossier.resolve("transformes"), prefixe, FREQUENCE);

        List<byte[]> attendues = oracle(original);
        List<SequenceProduite> sequences = produit.sequences();
        int tranchesAttendues = (pcm.length + OCTETS_PAR_TRANCHE - 1) / OCTETS_PAR_TRANCHE;
        assertThat(sequences)
                .as("ceil(D/5) tranches pour D = %s s", produit.dureeSourceSecondes())
                .hasSize(tranchesAttendues)
                .hasSize((int) TransformationAudio.nombreSequencesAttendu(produit.dureeSourceSecondes()))
                .hasSameSizeAs(attendues);
        for (int i = 0; i < sequences.size(); i++) {
            byte[] octets = Files.readAllBytes(sequences.get(i).chemin());
            int debut = i * OCTETS_PAR_TRANCHE;
            byte[] signalAttendu = Arrays.copyOfRange(pcm, debut, Math.min(pcm.length, debut + OCTETS_PAR_TRANCHE));
            assertThat(Arrays.copyOfRange(octets, ENTETE_CANONIQUE, octets.length))
                    .as("signal de la séquence n°%d, découpé par le test", i)
                    .isEqualTo(signalAttendu);
            assertThat(octets).as("séquence n°%d identique au bit près", i).isEqualTo(attendues.get(i));
        }
    }

    @Test
    @DisplayName("#104 : un original qui cesse de se lire au milieu est rejeté (#155), pas fatal")
    void lecture_interrompue_est_un_rejet() throws IOException {
        Path original = dossier.resolve("PaRecPR1925492_20260422_203922.wav");
        ecrireWav(original, pcmDeterministe(3 * OCTETS_PAR_TRANCHE));
        TransformationAudio transformation =
                new TransformationAudio(fichier -> interrompueALaDeuxiemeTranche(FichierWav.ouvrirPcm(fichier)));

        assertThatThrownBy(
                        () -> transformation.transformer(original, dossier.resolve("transformes"), prefixe, FREQUENCE))
                .isInstanceOf(OriginalIllisibleException.class)
                .hasMessageContaining("support retiré");
    }

    /// L'ancienne voie : PCM entier, tranché puis écrit, relu en octets.
    private List<byte[]> oracle(Path original) throws IOException {
        FichierWav source = FichierWav.lire(original);
        byte[] pcm = source.donneesPcm();
        Path sortie = Files.createDirectories(dossier.resolve("oracle"));
        List<byte[]> tranches = new ArrayList<>();
        for (int offset = 0, k = 0; offset < pcm.length; offset += OCTETS_PAR_TRANCHE, k++) {
            Path tranche = sortie.resolve(k + ".wav");
            FichierWav.ecrire(
                    tranche,
                    source.nombreCanaux(),
                    FREQUENCE / TransformationAudio.FACTEUR_EXPANSION,
                    source.bitsParEchantillon(),
                    pcm,
                    offset,
                    Math.min(OCTETS_PAR_TRANCHE, pcm.length - offset));
            tranches.add(Files.readAllBytes(tranche));
        }
        return tranches;
    }

    /// Une lecture qui rend la première tranche puis échoue, comme une carte SD retirée en cours d'import.
    private static FichierWav.LecturePcm interrompueALaDeuxiemeTranche(FichierWav.LecturePcm vraie) {
        return new FichierWav.LecturePcm() {
            private int appels;

            @Override
            public FichierWav.EnteteWav entete() {
                return vraie.entete();
            }

            @Override
            public int lire(byte[] tampon) throws IOException {
                if (++appels == 2) {
                    throw new IOException("support retiré");
                }
                return vraie.lire(tampon);
            }

            @Override
            public void close() throws IOException {
                vraie.close();
            }
        };
    }

    private static byte[] pcmDeterministe(int octets) {
        byte[] pcm = new byte[octets];
        for (int i = 0; i < octets; i++) {
            pcm[i] = (byte) ((i * 37) ^ (i >>> 8));
        }
        return pcm;
    }

    /// WAV mono 16 bits à [#FREQUENCE] Hz, par le writer de production (#2864).
    private static void ecrireWav(Path fichier, byte[] pcm) throws IOException {
        FichierWav.ecrire(fichier, 1, FREQUENCE, 16, pcm, 0, pcm.length);
    }
}
