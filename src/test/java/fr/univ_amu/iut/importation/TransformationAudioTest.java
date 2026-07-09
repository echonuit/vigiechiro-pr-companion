package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.importation.model.Empreintes;
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
    // 12 s à 2000 Hz : > 5 s pour exercer plusieurs tranches (découpage à 5 s RÉELLES) + une dernière plus
    // courte. Une tranche = 5 s source = 5 × 2000 = 10 000 trames ; 24 000 = 2 tranches pleines + 4 000.
    private static final int TRAMES_SOURCE = 24_000;
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
    @DisplayName("R10 : le nombre de séquences vaut ceil(durée source / 5)")
    void nombre_de_sequences() {
        TransformationOriginal resultat =
                transformation.transformer(originalWav, dossier.resolve("transformes"), prefixe, FREQUENCE_SOURCE);

        // durée source = 24000 / 2000 = 12 s -> ceil(12 / 5) = ceil(2,4) = 3
        assertThat(resultat.sequences()).hasSize(3);
        assertThat(TransformationAudio.nombreSequencesAttendu(12.0)).isEqualTo(3L);
        assertThat(resultat.frequenceSourceHz()).isEqualTo(FREQUENCE_SOURCE);
        assertThat(resultat.frequenceSortieHz()).isEqualTo(FREQUENCE_SORTIE);
    }

    @Test
    @DisplayName("R10 : 2 séquences pleines (5 s réelles = 50 s d'écoute) + une dernière plus courte (20 s)")
    void duree_de_chaque_sequence() {
        List<SequenceProduite> sequences = transformation
                .transformer(originalWav, dossier.resolve("transformes"), prefixe, FREQUENCE_SOURCE)
                .sequences();

        // Séquence pleine = 5 s source = 10 000 trames ; à l'écoute (÷10) 10 000 / 200 Hz = 50 s.
        for (int i = 0; i < 2; i++) {
            assertThat(sequences.get(i).dureeSecondes())
                    .as("séquence pleine n°%d", i)
                    .isEqualTo(50.0, org.assertj.core.api.Assertions.within(1e-9));
        }
        // 24000 trames - 2 × 10000 = 4000 trames -> à l'écoute 4000 / 200 Hz = 20 s.
        assertThat(sequences.get(2).dureeSecondes())
                .as("dernière séquence plus courte")
                .isEqualTo(20.0, org.assertj.core.api.Assertions.within(1e-9));
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
    @DisplayName("R8 : les séquences portent l'heure réelle de leur début (horodatage + 5k s) + suffixe _000")
    void nommage_des_sequences() {
        List<SequenceProduite> sequences = transformation
                .transformer(originalWav, dossier.resolve("transformes"), prefixe, FREQUENCE_SOURCE)
                .sequences();

        // Original à 20:39:22 : tranche 0 à 20:39:22, tranche 1 à +5 s = 20:39:27, tranche 2 à +10 s = 20:39:32.
        assertThat(sequences.get(0).nomFichier()).isEqualTo("PaRecPR1925492_20260422_203922_000.wav");
        assertThat(sequences.get(1).nomFichier()).isEqualTo("PaRecPR1925492_20260422_203927_000.wav");
        assertThat(sequences.get(2).nomFichier()).isEqualTo("PaRecPR1925492_20260422_203932_000.wav");
    }

    @Test
    @DisplayName("R10 : une fréquence d'acquisition non multiple de 10 est un défaut récupérable")
    void frequence_non_multiple_de_dix_refusee() {
        // Le log annonce une acquisition non divisible par 10 : défaut récupérable (#155) → l'original est
        // rejeté (et consigné au rapport par l'appelant), pas une erreur fatale d'écriture du workspace.
        assertThatThrownBy(() -> transformation.transformer(originalWav, dossier.resolve("ko"), prefixe, 384003))
                .isInstanceOf(OriginalIllisibleException.class)
                .hasMessageContaining("384003");
    }

    @Test
    @DisplayName("Réalité PR : un brut déjà expansé ×10 (en-tête = Fe/10 du log) s'importe sans double expansion")
    void brut_pr_deja_expanse_importe_sans_double_expansion() {
        // En-tête 2000 Hz mais le log déclare une acquisition à 20000 Hz : l'enregistreur a DÉJÀ appliqué
        // l'expansion ×10 (en-tête = Fe/10). On ne ré-expanse donc pas : la sortie reste à 2000 Hz (= l'en-tête,
        // sans double expansion), et le découpage suit 5 s RÉELLES au rythme d'acquisition (20000 Hz).
        TransformationOriginal resultat =
                transformation.transformer(originalWav, dossier.resolve("transformes"), prefixe, 20000);

        assertThat(resultat.frequenceSourceHz())
                .as("fréquence d'acquisition = celle du log")
                .isEqualTo(20000);
        assertThat(resultat.frequenceSortieHz())
                .as("sortie = Fe/10 = en-tête : aucune double expansion")
                .isEqualTo(2000);
        // 24000 trames à 20000 Hz réels = 1,2 s réelle → ceil(1,2 / 5) = 1 séquence, écrite à 2000 Hz.
        assertThat(resultat.sequences()).hasSize(1);
        assertThat(resultat.sequences().get(0).frequenceSortieHz()).isEqualTo(2000);
    }

    @Test
    @DisplayName("Sans log : un en-tête bas est traité comme déjà expansé ×10 (repli PR), sans double expansion")
    void sans_log_entete_bas_traite_comme_deja_expanse() {
        // Pas de journal (mode dégradé) : un en-tête (2000 Hz) sous le seuil d'un ultrason brut est
        // interprété comme déjà expansé (Fe/10). On ne ré-expanse pas : sortie = en-tête (2000 Hz),
        // acquisition déduite = en-tête × 10 = 20000 Hz.
        TransformationOriginal resultat =
                transformation.transformer(originalWav, dossier.resolve("transformes"), prefixe, null);

        assertThat(resultat.frequenceSourceHz())
                .as("acquisition déduite = en-tête × 10")
                .isEqualTo(20000);
        assertThat(resultat.frequenceSortieHz())
                .as("sortie = en-tête : pas de double expansion")
                .isEqualTo(2000);
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
