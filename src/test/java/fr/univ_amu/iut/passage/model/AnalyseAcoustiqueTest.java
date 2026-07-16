package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.FichierWav;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Vérification acoustique (#1309, #1687) : la présence d'un cri se mesure sur l'énergie **de pointe**
/// (fenêtre courte glissée dans la fenêtre de l'observation), pas sur la moyenne de toute la fenêtre. Un
/// cri bref (quelques ms) noyé dans une observation de plusieurs secondes doit être détecté - l'ancienne
/// moyenne le diluait jusqu'au plancher du bruit (faux négatifs constatés sur le cas réel).
class AnalyseAcoustiqueTest {

    private static final int FE_ENTETE_HZ = 38_400; // Fe/10 (l'audio d'écoute)
    private static final double FE_REELLE_HZ = 384_000; // Fe d'acquisition (échantillons)

    @TempDir
    Path dossier;

    @Test
    @DisplayName("#1687 : un cri bref au milieu d'une longue fenêtre d'observation est détecté (pas dilué)")
    void cri_bref_dans_longue_fenetre_detecte() throws IOException {
        FichierWav wav = tranche(2.0, 1.0, 50_000); // burst 50 kHz de 10 ms à 1,0 s réelle
        // Observation d'1,5 s (0,5 -> 2,0) : le cri de 10 ms y serait noyé pour une moyenne, pas pour une pointe.
        OptionalDouble fraction =
                AnalyseAcoustique.fractionCrisPresents(wav, List.of(new CriAttendu(0.5, 2.0, 50_000)));

        assertThat(fraction).hasValue(1.0);
    }

    @Test
    @DisplayName("Aucune énergie à cette fréquence : non détecté (pas de faux positif)")
    void frequence_absente_non_detectee() throws IOException {
        FichierWav wav = tranche(2.0, 1.0, 50_000);
        OptionalDouble fraction =
                AnalyseAcoustique.fractionCrisPresents(wav, List.of(new CriAttendu(0.5, 2.0, 150_000)));

        assertThat(fraction).hasValue(0.0);
    }

    @Test
    @DisplayName("Le cri hors de la fenêtre temporelle demandée n'est pas détecté (localisation préservée)")
    void cri_hors_fenetre_temporelle_non_detecte() throws IOException {
        FichierWav wav = tranche(2.0, 1.5, 50_000); // burst à 1,5 s
        OptionalDouble fraction =
                AnalyseAcoustique.fractionCrisPresents(wav, List.of(new CriAttendu(0.0, 1.0, 50_000)));

        assertThat(fraction).hasValue(0.0);
    }

    /// Tranche synthétique : un fond sinusoïdal **continu** (2 kHz, qui ne « ressort » jamais d'une fenêtre
    /// à l'autre) plus un **burst** bref (10 ms) à `instantBurstS`, à `frequenceBurstHz`. En-tête à Fe/10,
    /// échantillons à Fe réelle (comme une tranche d'écoute).
    private FichierWav tranche(double dureeReelleS, double instantBurstS, double frequenceBurstHz) throws IOException {
        int nombre = (int) Math.round(dureeReelleS * FE_REELLE_HZ);
        byte[] pcm = new byte[nombre * 2];
        int debutBurst = (int) Math.round(instantBurstS * FE_REELLE_HZ);
        int longueurBurst = (int) Math.round(0.010 * FE_REELLE_HZ);
        for (int i = 0; i < nombre; i++) {
            double fond = 0.2 * Math.sin(2 * Math.PI * 2_000 / FE_REELLE_HZ * i);
            double burst = (i >= debutBurst && i < debutBurst + longueurBurst)
                    ? 0.7 * Math.sin(2 * Math.PI * frequenceBurstHz / FE_REELLE_HZ * i)
                    : 0.0;
            short valeur = (short) Math.round((fond + burst) * 30_000);
            pcm[2 * i] = (byte) (valeur & 0xFF);
            pcm[2 * i + 1] = (byte) ((valeur >> 8) & 0xFF);
        }
        Path fichier = dossier.resolve("tranche.wav");
        FichierWav.ecrire(fichier, 1, FE_ENTETE_HZ, 16, pcm, 0, pcm.length);
        return FichierWav.lire(fichier);
    }
}
