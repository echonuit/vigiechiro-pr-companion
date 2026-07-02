package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.importation.model.Empreintes;
import fr.univ_amu.iut.importation.model.OriginalDejaRalentiException;
import fr.univ_amu.iut.importation.model.OriginalIllisibleException;
import fr.univ_amu.iut.importation.model.SequenceProduite;
import fr.univ_amu.iut.importation.model.TransformationAudio;
import fr.univ_amu.iut.importation.model.TransformationOriginal;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests du moteur de transformation audio (R10/R11), le **point dur** de l'import.
///
/// Les fixtures WAV sont **synthétiques** et générées en `@TempDir` (et non les WAV du brief, qui
/// sont déjà transformés) : on contrôle ainsi exactement les octets PCM source, condition du test
/// de déterminisme et de continuité. Fréquence source {@value #FREQUENCE_SOURCE} Hz (multiple de
/// 10), durée volontairement non entière pour exercer la dernière séquence plus courte.
class TransformationAudioTest {

    private static final int FREQUENCE_SOURCE = 2000; // Hz, divisible par 10 (R10)
    private static final int FREQUENCE_SORTIE = FREQUENCE_SOURCE / 10; // 200 Hz
    private static final int CANAUX = 1; // mono
    private static final int BITS = 16;
    private static final int TRAMES_SOURCE = 5200; // 2,6 s à 2000 Hz
    private static final int ENTETE_WAV = 44;

    @TempDir
    Path dossier;

    private final TransformationAudio transformation = new TransformationAudio();
    private final Prefixe prefixe = new Prefixe("640380", 2026, 2, "Z1");

    private Path originalWav;
    private byte[] pcmSource;

    @BeforeEach
    void preparer() throws IOException {
        pcmSource = pcmDeterministe(TRAMES_SOURCE);
        originalWav = dossier.resolve("PaRecPR1925492_20260422_203922.wav");
        ecrireWav(originalWav, CANAUX, FREQUENCE_SOURCE, BITS, pcmSource);
    }

    @Test
    @DisplayName("#231 : reprise — une séquence corrompue (même taille) est régénérée à l'identique")
    void reprise_regenere_les_sequences_corrompues() throws IOException {
        Path transformes = dossier.resolve("transformes");
        Path sequence = transformation
                .transformer(originalWav, transformes, prefixe, FREQUENCE_SOURCE)
                .sequences()
                .get(0)
                .chemin();
        byte[] correct = Files.readAllBytes(sequence);

        // Corruption silencieuse : on remplace le contenu par des octets bidons DE MÊME TAILLE — un saut
        // fondé sur la seule taille ne la détecterait pas, mais la régénération systématique (R11) si.
        Files.write(sequence, new byte[correct.length]);
        assertThat(Files.readAllBytes(sequence)).isNotEqualTo(correct);

        transformation.transformer(originalWav, transformes, prefixe, FREQUENCE_SOURCE);

        // La séquence a été régénérée au bit près, jamais laissée corrompue.
        assertThat(Files.readAllBytes(sequence)).isEqualTo(correct);
    }

    @Test
    @DisplayName("R10 : le nombre de séquences vaut ceil(2 × durée source)")
    void nombre_de_sequences() {
        TransformationOriginal resultat =
                transformation.transformer(originalWav, dossier.resolve("transformes"), prefixe, FREQUENCE_SOURCE);

        // durée source = 5200 / 2000 = 2,6 s -> ceil(2 × 2,6) = ceil(5,2) = 6
        assertThat(resultat.sequences()).hasSize(6);
        assertThat(TransformationAudio.nombreSequencesAttendu(2.6)).isEqualTo(6L);
        assertThat(resultat.frequenceSourceHz()).isEqualTo(FREQUENCE_SOURCE);
        assertThat(resultat.frequenceSortieHz()).isEqualTo(FREQUENCE_SORTIE);
    }

    @Test
    @DisplayName("R10 : 5 séquences pleines de 5 s + une dernière plus courte (1 s)")
    void duree_de_chaque_sequence() {
        List<SequenceProduite> sequences = transformation
                .transformer(originalWav, dossier.resolve("transformes"), prefixe, FREQUENCE_SOURCE)
                .sequences();

        for (int i = 0; i < 5; i++) {
            assertThat(sequences.get(i).dureeSecondes())
                    .as("séquence pleine n°%d", i)
                    .isEqualTo(5.0, org.assertj.core.api.Assertions.within(1e-9));
        }
        // 5200 trames - 5 × 1000 = 200 trames -> 200 / 200 Hz = 1 s
        assertThat(sequences.get(5).dureeSecondes())
                .as("dernière séquence plus courte")
                .isEqualTo(1.0, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    @DisplayName("R10 : la fréquence de sortie des fichiers écrits vaut source / 10")
    void frequence_de_sortie_dans_le_fichier() throws IOException {
        List<SequenceProduite> sequences = transformation
                .transformer(originalWav, dossier.resolve("transformes"), prefixe, FREQUENCE_SOURCE)
                .sequences();

        byte[] entete = Files.readAllBytes(sequences.get(0).chemin());
        int frequenceEcrite = lireIntLE(entete, 24); // offset du sample rate dans l'en-tête WAV
        assertThat(frequenceEcrite).isEqualTo(FREQUENCE_SORTIE);
    }

    @Test
    @DisplayName("R10/R11 : la concaténation des séquences reconstitue exactement le PCM source")
    void continuite_et_aucun_sample_modifie() throws IOException {
        List<SequenceProduite> sequences = transformation
                .transformer(originalWav, dossier.resolve("transformes"), prefixe, FREQUENCE_SOURCE)
                .sequences();

        ByteArrayOutputStream concatenation = new ByteArrayOutputStream();
        for (SequenceProduite sp : sequences) {
            byte[] octets = Files.readAllBytes(sp.chemin());
            // En-tête canonique de 44 octets : le reste est le PCM brut, copié sans altération.
            concatenation.write(octets, ENTETE_WAV, octets.length - ENTETE_WAV);
        }
        assertThat(concatenation.toByteArray())
                .as("aucun octet PCM modifié (pas de rééchantillonnage, pas de clipping)")
                .isEqualTo(pcmSource);
    }

    @Test
    @DisplayName("R11 : deux exécutions produisent des fichiers identiques au bit près (déterminisme)")
    void determinisme_hash_stable() {
        List<SequenceProduite> premier = transformation
                .transformer(originalWav, dossier.resolve("run1"), prefixe, FREQUENCE_SOURCE)
                .sequences();
        List<SequenceProduite> second = transformation
                .transformer(originalWav, dossier.resolve("run2"), prefixe, FREQUENCE_SOURCE)
                .sequences();

        assertThat(premier).hasSameSizeAs(second);
        for (int i = 0; i < premier.size(); i++) {
            assertThat(Empreintes.sha256Hex(second.get(i).chemin()))
                    .as("empreinte stable de la séquence n°%d", i)
                    .isEqualTo(Empreintes.sha256Hex(premier.get(i).chemin()));
        }
    }

    @Test
    @DisplayName("R8 : les séquences reprennent le nom de l'original + suffixe _000, _001…")
    void nommage_des_sequences() {
        List<SequenceProduite> sequences = transformation
                .transformer(originalWav, dossier.resolve("transformes"), prefixe, FREQUENCE_SOURCE)
                .sequences();

        assertThat(sequences.get(0).nomFichier()).isEqualTo("PaRecPR1925492_20260422_203922_000.wav");
        assertThat(sequences.get(1).nomFichier()).isEqualTo("PaRecPR1925492_20260422_203922_001.wav");
    }

    @Test
    @DisplayName("R10 : une fréquence source non multiple de 10 est un défaut de format SOURCE récupérable")
    void frequence_non_multiple_de_dix_refusee() throws IOException {
        Path mauvais = dossier.resolve("mauvais.wav");
        ecrireWav(mauvais, CANAUX, 384003, BITS, pcmDeterministe(100));

        // Défaut de format de la SOURCE (#155) : récupérable → l'original est rejeté (et consigné au
        // rapport par l'appelant), pas une erreur fatale d'écriture du workspace.
        assertThatThrownBy(() -> transformation.transformer(mauvais, dossier.resolve("ko"), prefixe, FREQUENCE_SOURCE))
                .isInstanceOf(OriginalIllisibleException.class)
                .hasMessageContaining("384003");
    }

    @Test
    @DisplayName("Garde-fou : un original déjà ralenti (en-tête < Fe du log) est rejeté (récupérable)")
    void original_deja_ralenti_avec_log_est_rejete() {
        // En-tête 2000 Hz alors que le log annonce une acquisition à 20000 Hz → source déjà ralentie
        // (elle serait ré-expansée ×10). Rejet récupérable, pas une transformation.
        assertThatThrownBy(() -> transformation.transformer(originalWav, dossier.resolve("ko"), prefixe, 20000))
                .isInstanceOf(OriginalDejaRalentiException.class)
                .hasMessageContaining("2000")
                .hasMessageContaining("20000");
    }

    @Test
    @DisplayName("Garde-fou sans log : un en-tête sous le seuil d'un ultrason brut est rejeté")
    void original_deja_ralenti_sans_log_est_rejete() {
        // Pas de journal (mode dégradé) : 2000 Hz est bien en dessous du seuil d'un ultrason brut → rejet.
        assertThatThrownBy(() -> transformation.transformer(originalWav, dossier.resolve("ko"), prefixe, null))
                .isInstanceOf(OriginalDejaRalentiException.class)
                .hasMessageContaining("2000");
    }

    // --- Helpers (autonomes, pas de helper partagé entre fichiers de test) --------------------

    /// PCM mono 16 bits déterministe : motif reproductible, sans dépendre du hasard.
    private static byte[] pcmDeterministe(int trames) {
        byte[] pcm = new byte[trames * 2];
        for (int i = 0; i < trames; i++) {
            short echantillon = (short) (((i * 37) % 2000) - 1000);
            pcm[2 * i] = (byte) (echantillon & 0xFF);
            pcm[2 * i + 1] = (byte) ((echantillon >> 8) & 0xFF);
        }
        return pcm;
    }

    /// Écrit un WAV PCM canonique (en-tête 44 octets, little-endian).
    private static void ecrireWav(Path fichier, int canaux, int frequence, int bits, byte[] pcm) throws IOException {
        int blocAlign = canaux * (bits / 8);
        ByteBuffer buf = ByteBuffer.allocate(ENTETE_WAV + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + pcm.length);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 1); // PCM
        buf.putShort((short) canaux);
        buf.putInt(frequence);
        buf.putInt(frequence * blocAlign);
        buf.putShort((short) blocAlign);
        buf.putShort((short) bits);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(pcm.length);
        buf.put(pcm);
        Files.write(fichier, buf.array());
    }

    private static int lireIntLE(byte[] o, int pos) {
        return (o[pos] & 0xFF) | ((o[pos + 1] & 0xFF) << 8) | ((o[pos + 2] & 0xFF) << 16) | ((o[pos + 3] & 0xFF) << 24);
    }
}
