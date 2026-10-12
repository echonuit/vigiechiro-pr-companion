package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
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
    @DisplayName("#1934 : l'en-tête seul dit la même chose que la lecture complète")
    void entete_seul_concorde_avec_la_lecture_complete() throws IOException {
        // Deux tailles de PCM, dont une qui ne tombe pas juste : c'est là qu'un décompte de trames dérape.
        for (int octets : new int[] {8, 4002}) {
            Path fichier = ecrire("concordance" + octets + ".wav", construire(octets, new byte[octets]));

            FichierWav complet = FichierWav.lire(fichier);
            FichierWav.EnteteWav entete = FichierWav.lireEntete(fichier);

            assertThat(entete.nombreCanaux()).isEqualTo(complet.nombreCanaux());
            assertThat(entete.frequenceEchantillonnageHz()).isEqualTo(complet.frequenceEchantillonnageHz());
            assertThat(entete.bitsParEchantillon()).isEqualTo(complet.bitsParEchantillon());
            assertThat(entete.nombreTrames())
                    .as("le nombre de trames décide du nombre de tranches, donc des noms arbitrés")
                    .isEqualTo(complet.nombreTrames());
        }
    }

    @Test
    @DisplayName("#1934 : l'en-tête seul ne charge pas le signal (fichier plus grand que la fenêtre lue)")
    void entete_seul_ne_charge_pas_le_signal() throws IOException {
        // 64 Kio de PCM, très au-delà de la fenêtre d'en-tête : la lecture doit aboutir quand même, et
        // compter toutes les trames, pas seulement celles qu'elle a sous les yeux.
        int octets = 65_536;
        Path fichier = ecrire("gros.wav", construire(octets, new byte[octets]));

        assertThat(FichierWav.lireEntete(fichier).nombreTrames()).isEqualTo(octets / 2);
    }

    @Test
    @DisplayName("#104 : la lecture par morceaux rend le même signal que la lecture complète, chunk annexe compris")
    void lecture_par_morceaux_concorde_avec_la_lecture_complete() throws IOException {
        // Un chunk annexe de taille impaire (padding d'un octet) et plus grand que la fenêtre d'en-tête, puis
        // un PCM de longueur impaire : les trois endroits où une position se décale d'un octet.
        byte[] pcm = new byte[4001];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (byte) (i * 31 + 7);
        }
        Path fichier = ecrire("annexe.wav", avecChunkAnnexe(10_001, pcm));
        FichierWav complet = FichierWav.lire(fichier);

        ByteArrayOutputStream parMorceaux = new ByteArrayOutputStream();
        try (FichierWav.LecturePcm lecture = FichierWav.ouvrirPcm(fichier)) {
            assertThat(lecture.entete().octetsDonnees()).isEqualTo(pcm.length);
            byte[] tampon = new byte[7];
            int lus;
            while ((lus = lecture.lire(tampon)) > 0) {
                parMorceaux.write(tampon, 0, lus);
            }
        }

        assertThat(complet.donneesPcm()).isEqualTo(pcm);
        assertThat(parMorceaux.toByteArray()).isEqualTo(pcm);
    }

    @Test
    @DisplayName("#104 : un WAV de plus de 2 Gio s'ouvre, et sa longueur ne déborde pas")
    void plus_de_deux_gio_ne_deborde_pas() throws IOException {
        // Fichiers creux : la taille est posée sans écrire les octets, aucun disque n'est consommé. Deux
        // formes : une taille annoncée de 2,5 Gio, et la taille inconnue d'un enregistreur en flux.
        long annoncee = 2_500_000_000L;
        Path declare = creux("declare.wav", construire(annoncee, new byte[0]), 44 + annoncee);
        long disponible = 3L * 1024 * 1024 * 1024;
        Path inconnue = creux("inconnue.wav", construire(0xFFFFFFFFL, new byte[0]), 44 + disponible);

        assertThat(FichierWav.lireEntete(declare).octetsDonnees()).isEqualTo(annoncee);
        assertThat(FichierWav.lireEntete(inconnue).octetsDonnees()).isEqualTo(disponible);
        try (FichierWav.LecturePcm lecture = FichierWav.ouvrirPcm(inconnue)) {
            assertThat(lecture.entete().octetsDonnees()).isEqualTo(disponible);
            byte[] tampon = new byte[16];
            assertThat(lecture.lire(tampon)).isEqualTo(tampon.length);
        }
    }

    @Test
    @DisplayName("#104 : la lecture par morceaux refuse ce que la lecture complète refuse")
    void lecture_par_morceaux_refuse_un_wav_tronque() throws IOException {
        Path fichier = ecrire("tronque-flux.wav", construire(4096, new byte[] {1, 2, 3, 4}));

        assertThatThrownBy(() -> FichierWav.ouvrirPcm(fichier).close())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("tronqué");
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

    /// Un WAV dont un chunk annexe `LIST` de `tailleAnnexe` octets précède `data`.
    private static byte[] avecChunkAnnexe(int tailleAnnexe, byte[] pcm) {
        int padding = tailleAnnexe % 2;
        ByteBuffer buf = ByteBuffer.allocate(12 + 24 + 8 + tailleAnnexe + padding + 8 + pcm.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(buf.capacity() - 8);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        ecrireFmt(buf, FORMAT_PCM_TEST);
        buf.put("LIST".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(tailleAnnexe);
        buf.put(new byte[tailleAnnexe + padding]);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(pcm.length);
        buf.put(pcm);
        return buf.array();
    }

    /// Écrit `debut` puis étend le fichier à `taille` octets sans les écrire (fichier creux).
    private Path creux(String nom, byte[] debut, long taille) throws IOException {
        Path fichier = ecrire(nom, debut);
        try (RandomAccessFile acces = new RandomAccessFile(fichier.toFile(), "rw")) {
            acces.setLength(taille);
        }
        return fichier;
    }

    private Path ecrire(String nom, byte[] octets) throws IOException {
        Path fichier = dossier.resolve(nom);
        Files.write(fichier, octets);
        return fichier;
    }
}
