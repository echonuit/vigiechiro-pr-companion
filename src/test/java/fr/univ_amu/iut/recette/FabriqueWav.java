package fr.univ_amu.iut.recette;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/// Fabrique d'octets WAV **minimaux mais valides** (RIFF/WAVE, mono, PCM 16 bits, petit-boutiste),
/// factorisée depuis les parcours E2E d'import qui la dupliquaient (`ecrireWav`). Le signal PCM est
/// **déterministe** (dent de scie `(i * 41) % 1000 - 500`, aucun aléatoire) : deux générations
/// produisent des octets identiques, condition d'une recette rejouable.
final class FabriqueWav {

    /// En-tête RIFF/WAVE canonique : 44 octets avant les données PCM.
    private static final int TAILLE_ENTETE = 44;

    private static final int PCM_16_BITS = 16;
    private static final int MONO = 1;
    private static final int FORMAT_PCM = 1;
    private static final int OCTETS_PAR_TRAME = MONO * PCM_16_BITS / 8;

    private FabriqueWav() {}

    /// Nombre de trames couvrant `dureeSecondes` à `frequenceHz`.
    static int tramesPour(int frequenceHz, double dureeSecondes) {
        return (int) Math.round(frequenceHz * dureeSecondes);
    }

    /// Octets d'un WAV mono 16 bits de `nombreTrames` trames, échantillonné à `frequenceHz` (valeur
    /// inscrite telle quelle dans l'en-tête).
    static byte[] octetsWav(int frequenceHz, int nombreTrames) {
        byte[] pcm = new byte[nombreTrames * OCTETS_PAR_TRAME];
        for (int i = 0; i < nombreTrames; i++) {
            short echantillon = (short) (((i * 41) % 1000) - 500);
            pcm[2 * i] = (byte) (echantillon & 0xFF);
            pcm[2 * i + 1] = (byte) ((echantillon >> 8) & 0xFF);
        }

        ByteBuffer tampon = ByteBuffer.allocate(TAILLE_ENTETE + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        tampon.put(ascii("RIFF"));
        tampon.putInt(36 + pcm.length); // ChunkSize = 36 + taille des données
        tampon.put(ascii("WAVE"));
        tampon.put(ascii("fmt "));
        tampon.putInt(16); // Subchunk1Size (PCM)
        tampon.putShort((short) FORMAT_PCM);
        tampon.putShort((short) MONO);
        tampon.putInt(frequenceHz); // SampleRate
        tampon.putInt(frequenceHz * OCTETS_PAR_TRAME); // ByteRate = SampleRate * BlockAlign
        tampon.putShort((short) OCTETS_PAR_TRAME); // BlockAlign
        tampon.putShort((short) PCM_16_BITS); // BitsPerSample
        tampon.put(ascii("data"));
        tampon.putInt(pcm.length); // Subchunk2Size
        tampon.put(pcm);
        return tampon.array();
    }

    private static byte[] ascii(String texte) {
        return texte.getBytes(StandardCharsets.US_ASCII);
    }
}
