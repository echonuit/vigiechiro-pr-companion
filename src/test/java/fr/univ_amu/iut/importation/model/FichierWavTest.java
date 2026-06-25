package fr.univ_amu.iut.importation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests de lecture/validation de l'en-tête WAV ([FichierWav#lire]) : le **garde-fou d'intégrité** de
/// l'import (#156). On fabrique des en-têtes RIFF/WAVE à la main pour contrôler exactement la taille
/// **annoncée** du chunk `data` indépendamment des octets réellement présents, et vérifier qu'un
/// fichier tronqué/corrompu est **refusé** au lieu d'être lu silencieusement plus court (ce qui
/// fausserait l'analyse).
class FichierWavTest {

    private static final int CANAUX = 1;
    private static final int FREQUENCE = 2000;
    private static final int BITS = 16;

    @TempDir
    Path dossier;

    @Test
    @DisplayName("Un WAV PCM canonique est lu (canaux, fréquence, bits, PCM exacts)")
    void lire_wav_valide() throws IOException {
        byte[] pcm = {1, 2, 3, 4, 5, 6, 7, 8};
        Path fichier = ecrire("ok.wav", construire(pcm.length, pcm));

        FichierWav lu = FichierWav.lire(fichier);

        assertThat(lu.nombreCanaux()).isEqualTo(CANAUX);
        assertThat(lu.frequenceEchantillonnageHz()).isEqualTo(FREQUENCE);
        assertThat(lu.bitsParEchantillon()).isEqualTo(BITS);
        assertThat(lu.donneesPcm()).isEqualTo(pcm);
    }

    @Test
    @DisplayName("#156 : un WAV tronqué (data annoncé > octets présents) est refusé")
    void data_tronque_est_refuse() throws IOException {
        byte[] pcmPartiel = {1, 2, 3, 4}; // 4 octets présents…
        Path fichier = ecrire("tronque.wav", construire(4096, pcmPartiel)); // …mais 4096 annoncés

        assertThatThrownBy(() -> FichierWav.lire(fichier))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("tronqué");
    }

    @Test
    @DisplayName("#156 : la taille de données inconnue (0xFFFFFFFF) est lue jusqu'à la fin, pas une troncature")
    void taille_inconnue_lue_jusqua_la_fin() throws IOException {
        // PCM piégé : >= 8 octets commençant par un faux en-tête « data » + une taille plausible. Sans
        // l'arrêt du scan sur taille inconnue, (int) 0xFFFFFFFF = -1 ferait reboucler la lecture sur ce
        // PCM, relire ce faux « data » comme un vrai chunk et corrompre dataDebut/dataLongueur.
        byte[] pcm = ByteBuffer.allocate(12)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put("data".getBytes(StandardCharsets.US_ASCII))
                .putInt(4)
                .put(new byte[] {9, 8, 7, 6})
                .array();
        Path fichier = ecrire("flux.wav", construire(0xFFFFFFFFL, pcm));

        FichierWav lu = FichierWav.lire(fichier);

        // Les 12 octets sont rendus tels quels (aucune relecture du PCM comme de faux chunks RIFF).
        assertThat(lu.donneesPcm()).isEqualTo(pcm);
    }

    @Test
    @DisplayName("Un fichier sans en-tête RIFF/WAVE est refusé")
    void riff_absent_est_refuse() throws IOException {
        Path fichier = ecrire("pasunwav.wav", "ceci n'est pas un WAV".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> FichierWav.lire(fichier))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("RIFF/WAVE");
    }

    @Test
    @DisplayName("Un WAV sans chunk data est refusé (en-tête incomplet)")
    void chunk_data_manquant_est_refuse() throws IOException {
        // En-tête RIFF/WAVE + fmt seul, sans chunk data.
        ByteBuffer buf = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(28);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        ecrireFmt(buf, FORMAT_PCM_TEST);
        Path fichier = ecrire("sansdata.wav", buf.array());

        assertThatThrownBy(() -> FichierWav.lire(fichier))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("incomplet");
    }

    @Test
    @DisplayName("Un WAV non-PCM (compressé) est refusé")
    void format_non_pcm_est_refuse() throws IOException {
        byte[] pcm = {1, 2, 3, 4};
        Path fichier = ecrire("compresse.wav", construire(pcm.length, pcm, 3)); // 3 = IEEE float

        assertThatThrownBy(() -> FichierWav.lire(fichier))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("PCM");
    }

    // --- Helpers (autonomes, pas de helper partagé entre fichiers de test) ------------------------

    private static final short FORMAT_PCM_TEST = 1;

    /// Construit les octets d'un WAV canonique, avec une taille `data` **annoncée** arbitraire
    /// (`tailleDataAnnoncee`) indépendante des octets réellement écrits (`pcm`), pour simuler une
    /// troncature ou un placeholder.
    private static byte[] construire(long tailleDataAnnoncee, byte[] pcm) {
        return construire(tailleDataAnnoncee, pcm, FORMAT_PCM_TEST);
    }

    private static byte[] construire(long tailleDataAnnoncee, byte[] pcm, int formatAudio) {
        ByteBuffer buf = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + pcm.length);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        ecrireFmt(buf, (short) formatAudio);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt((int) tailleDataAnnoncee);
        buf.put(pcm);
        return buf.array();
    }

    private static void ecrireFmt(ByteBuffer buf, short formatAudio) {
        int octetsParTrame = CANAUX * (BITS / 8);
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort(formatAudio);
        buf.putShort((short) CANAUX);
        buf.putInt(FREQUENCE);
        buf.putInt(FREQUENCE * octetsParTrame); // byte rate
        buf.putShort((short) octetsParTrame); // block align
        buf.putShort((short) BITS);
    }

    private Path ecrire(String nom, byte[] octets) throws IOException {
        Path fichier = dossier.resolve(nom);
        Files.write(fichier, octets);
        return fichier;
    }
}
