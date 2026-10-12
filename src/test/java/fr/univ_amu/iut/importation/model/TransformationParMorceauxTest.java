package fr.univ_amu.iut.importation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.Prefixe;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La transformation lit l'original par morceaux (#104) et produit **au bit près** ce que produisait la
/// lecture complète.
///
/// L'oracle est l'ancienne voie, rejouée dans le test : [FichierWav#lire] charge le PCM entier, on le
/// tranche tous les `5 × Fe` trames et on écrit chaque tranche par [FichierWav#ecrire]. Chaque fichier
/// produit est comparé octet par octet à son homologue, en-tête compris. Un morceau perdu, décalé ou
/// complété par les octets de la tranche précédente change au moins un fichier.
class TransformationParMorceauxTest {

    private static final int FREQUENCE = 2000; // Hz, divisible par 10 (R10)
    private static final int OCTETS_PAR_TRANCHE = TransformationAudio.DUREE_SEQUENCE_SECONDES * FREQUENCE * 2;

    @TempDir
    Path dossier;

    private final Prefixe prefixe = new Prefixe("640380", 2026, 2, "Z1");

    @Test
    @DisplayName("#104 : chaque séquence est identique, octet par octet, à celle de la lecture complète")
    void sequences_identiques_a_la_lecture_complete() throws IOException {
        // Deux tranches pleines, une dernière plus courte et de longueur impaire, un chunk annexe avant
        // `data` : les frontières où un flux perd ou décale un octet.
        byte[] pcm = pcmDeterministe(2 * OCTETS_PAR_TRANCHE + 12_345);
        Path original = dossier.resolve("PaRecPR1925492_20260422_203922.wav");
        Files.write(original, wavAvecChunkAnnexe(pcm));

        TransformationOriginal produit =
                new TransformationAudio().transformer(original, dossier.resolve("transformes"), prefixe, FREQUENCE);

        List<byte[]> attendues = oracle(original);
        List<SequenceProduite> sequences = produit.sequences();
        assertThat(sequences)
                .as("ceil(D/5) tranches pour D = %s s", produit.dureeSourceSecondes())
                .hasSize((int) TransformationAudio.nombreSequencesAttendu(produit.dureeSourceSecondes()))
                .hasSameSizeAs(attendues);
        for (int i = 0; i < sequences.size(); i++) {
            assertThat(Files.readAllBytes(sequences.get(i).chemin()))
                    .as("séquence n°%d identique au bit près", i)
                    .isEqualTo(attendues.get(i));
        }
    }

    @Test
    @DisplayName("#104 : un original qui cesse de se lire au milieu est rejeté (#155), pas fatal")
    void lecture_interrompue_est_un_rejet() throws IOException {
        Path original = dossier.resolve("PaRecPR1925492_20260422_203922.wav");
        Files.write(original, wavAvecChunkAnnexe(pcmDeterministe(3 * OCTETS_PAR_TRANCHE)));
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

    /// WAV mono 16 bits à [#FREQUENCE] Hz, avec un chunk `LIST` de taille impaire avant `data`.
    private static byte[] wavAvecChunkAnnexe(byte[] pcm) {
        int annexe = 33;
        ByteBuffer buf =
                ByteBuffer.allocate(12 + 24 + 8 + annexe + 1 + 8 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(buf.capacity() - 8);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putInt(FREQUENCE);
        buf.putInt(FREQUENCE * 2);
        buf.putShort((short) 2);
        buf.putShort((short) 16);
        buf.put("LIST".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(annexe);
        buf.put(new byte[annexe + 1]);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(pcm.length);
        buf.put(pcm);
        return buf.array();
    }
}
