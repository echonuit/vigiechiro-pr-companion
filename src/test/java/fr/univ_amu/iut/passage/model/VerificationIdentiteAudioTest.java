package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.passage.model.VerdictIdentite.Acceptee;
import fr.univ_amu.iut.passage.model.VerdictIdentite.NiveauConfiance;
import fr.univ_amu.iut.passage.model.VerdictIdentite.Refusee;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Cascade de vérification d'identité (#1309) sur des WAV **synthétiques** : un « bon » fichier
/// porte des salves sinusoïdales exactement aux instants et fréquences des cris attendus, un
/// fichier « étranger » porte le même nom et la même durée mais pas les cris. Les fichiers sont
/// écrits comme le pipeline le fait (en-tête à Fe/10, instants et fréquences réels).
class VerificationIdentiteAudioTest {

    private static final double FREQUENCE_REELLE_HZ = 384_000;
    private static final double DUREE_REELLE_S = 1.0;
    private static final String NOM = "Car040962-2026-Pass1-A1-PaRecPR1925492_20260620_223000_000.wav";
    private static final List<CriAttendu> CRIS = List.of(
            new CriAttendu(0.20, 0.22, 45_000), new CriAttendu(0.50, 0.52, 30_000), new CriAttendu(0.80, 0.82, 50_000));

    @TempDir
    Path dossier;

    private final VerificationIdentiteAudio verification = new VerificationIdentiteAudio();

    @Test
    @DisplayName("Bon fichier, cris retrouvés : CERTITUDE (structurelle + acoustique, sans empreinte)")
    void bon_fichier_certitude() throws IOException {
        Path candidat = ecrireWav(NOM, CRIS);

        VerdictIdentite verdict = verification.verifierSequence(sequenceSansEmpreinte(), candidat, CRIS);

        assertThat(verdict)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(Acceptee.class))
                .satisfies(acceptee -> {
                    assertThat(acceptee.niveau()).isEqualTo(NiveauConfiance.CERTITUDE);
                    assertThat(acceptee.preuves()).contains("acoustique");
                });
    }

    @Test
    @DisplayName("Homonyme de même durée mais sans les cris : refusé (acoustique discordante)")
    void homonyme_sans_les_cris_refuse() throws IOException {
        Path candidat = ecrireWav(NOM, List.of(new CriAttendu(0.35, 0.37, 80_000)));

        VerdictIdentite verdict = verification.verifierSequence(sequenceSansEmpreinte(), candidat, CRIS);

        assertThat(verdict)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(Refusee.class))
                .satisfies(refusee -> assertThat(refusee.motif()).contains("coustique"));
    }

    @Test
    @DisplayName("Durée réelle différente : refusé avant toute acoustique (redécoupe)")
    void duree_differente_refusee() throws IOException {
        Path candidat = ecrireWav(NOM, CRIS, 0.5);

        VerdictIdentite verdict = verification.verifierSequence(sequenceSansEmpreinte(), candidat, CRIS);

        assertThat(verdict)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(Refusee.class))
                .satisfies(refusee -> assertThat(refusee.motif()).contains("Durée"));
    }

    @Test
    @DisplayName("Nom différent : refusé d'emblée")
    void nom_different_refuse() throws IOException {
        Path candidat = ecrireWav("autre_20260620_223000_000.wav", CRIS);

        assertThat(verification.verifierSequence(sequenceSansEmpreinte(), candidat, CRIS))
                .isInstanceOf(Refusee.class);
    }

    @Test
    @DisplayName("Empreinte connue et concordante : CERTITUDE, sans analyse acoustique")
    void empreinte_concordante_certitude() throws IOException {
        Path candidat = ecrireWav(NOM, CRIS);
        SequenceDEcoute sequence = sequence(Files.size(candidat), Empreintes.empreinteCourte(candidat));

        VerdictIdentite verdict = verification.verifierSequence(sequence, candidat, List.of());

        assertThat(verdict)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(Acceptee.class))
                .satisfies(acceptee -> {
                    assertThat(acceptee.niveau()).isEqualTo(NiveauConfiance.CERTITUDE);
                    assertThat(acceptee.preuves()).contains("mpreinte");
                });
    }

    @Test
    @DisplayName("Empreinte connue discordante : refusé, même si nom et durée collent")
    void empreinte_discordante_refusee() throws IOException {
        Path candidat = ecrireWav(NOM, CRIS);
        SequenceDEcoute sequence = sequence(Files.size(candidat), "empreinte-d-un-autre-contenu");

        assertThat(verification.verifierSequence(sequence, candidat, CRIS)).isInstanceOf(Refusee.class);
    }

    @Test
    @DisplayName("Taille connue différente : refusé sans lire le contenu")
    void taille_differente_refusee() throws IOException {
        Path candidat = ecrireWav(NOM, CRIS);
        SequenceDEcoute sequence = sequence(Files.size(candidat) + 1, null);

        VerdictIdentite verdict = verification.verifierSequence(sequence, candidat, CRIS);

        assertThat(verdict)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(Refusee.class))
                .satisfies(refusee -> assertThat(refusee.motif()).contains("Taille"));
    }

    @Test
    @DisplayName("Séquence sans observation : structurelle seule, FORTE (rien à corrompre)")
    void sans_cris_forte() throws IOException {
        Path candidat = ecrireWav(NOM, CRIS);

        VerdictIdentite verdict = verification.verifierSequence(sequenceSansEmpreinte(), candidat, List.of());

        assertThat(verdict)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(Acceptee.class))
                .satisfies(acceptee -> assertThat(acceptee.niveau()).isEqualTo(NiveauConfiance.FORTE));
    }

    @Test
    @DisplayName("Fichier candidat introuvable : refusé avec motif clair")
    void fichier_introuvable_refuse() {
        assertThat(verification.verifierSequence(
                        sequenceSansEmpreinte(), dossier.resolve("absent").resolve(NOM), CRIS))
                .isInstanceOf(Refusee.class);
    }

    @Test
    @DisplayName("Original : SHA-256 intégral concordant → CERTITUDE ; discordant → refusé")
    void original_par_sha256() throws IOException {
        Path candidat = ecrireWav("PaRecPR1925492_20260620_223000.wav", CRIS);
        String sha = Empreintes.sha256Hex(candidat);
        EnregistrementOriginal bon = original("PaRecPR1925492_20260620_223000.wav", sha);
        EnregistrementOriginal autre = original("PaRecPR1925492_20260620_223000.wav", "sha-d-un-autre");

        assertThat(verification.verifierOriginal(bon, candidat))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(Acceptee.class))
                .satisfies(acceptee -> assertThat(acceptee.niveau()).isEqualTo(NiveauConfiance.CERTITUDE));
        assertThat(verification.verifierOriginal(autre, candidat)).isInstanceOf(Refusee.class);
    }

    // --- Fixture ---------------------------------------------------------------------------------

    private static SequenceDEcoute sequenceSansEmpreinte() {
        return sequence(null, null);
    }

    private static SequenceDEcoute sequence(Long taille, String empreinte) {
        return new SequenceDEcoute(
                1L,
                NOM,
                1L,
                0,
                0.0,
                DUREE_REELLE_S,
                "peu-importe/" + NOM,
                false,
                1L,
                null,
                new EmpreinteContenu(taille, empreinte));
    }

    private static EnregistrementOriginal original(String nom, String sha256) {
        return new EnregistrementOriginal(
                1L, nom, "peu-importe/" + nom, 1.0, 384_000, 1L, new EmpreinteContenu(null, sha256));
    }

    private Path ecrireWav(String nom, List<CriAttendu> cris) throws IOException {
        return ecrireWav(nom, cris, DUREE_REELLE_S);
    }

    /// Écrit un WAV comme le pipeline : en-tête à Fe/10, contenu = léger bruit déterministe + une
    /// salve sinusoïdale par cri, à sa fréquence médiane, entre ses instants réels.
    private Path ecrireWav(String nom, List<CriAttendu> cris, double dureeReelleSecondes) throws IOException {
        int nombreEchantillons = (int) Math.round(dureeReelleSecondes * FREQUENCE_REELLE_HZ);
        double[] signal = new double[nombreEchantillons];
        int graine = 7;
        for (int n = 0; n < nombreEchantillons; n++) {
            graine = graine * 31 + 17;
            signal[n] = ((graine % 1000) / 1000.0 - 0.5) * 0.01;
        }
        for (CriAttendu cri : cris) {
            int debut = (int) (cri.debutSecondes() * FREQUENCE_REELLE_HZ);
            int fin = Math.min((int) (cri.finSecondes() * FREQUENCE_REELLE_HZ), nombreEchantillons);
            for (int n = Math.max(0, debut); n < fin; n++) {
                signal[n] += 0.5 * Math.sin(2 * Math.PI * cri.frequenceMedianeHz() * n / FREQUENCE_REELLE_HZ);
            }
        }
        byte[] pcm = new byte[nombreEchantillons * 2];
        for (int n = 0; n < nombreEchantillons; n++) {
            short valeur = (short) (Math.max(-1.0, Math.min(1.0, signal[n])) * 32_767);
            pcm[2 * n] = (byte) (valeur & 0xFF);
            pcm[2 * n + 1] = (byte) ((valeur >> 8) & 0xFF);
        }
        Path fichier = dossier.resolve(nom);
        FichierWav.ecrire(fichier, 1, (int) (FREQUENCE_REELLE_HZ / 10), 16, pcm, 0, pcm.length);
        return fichier;
    }
}
