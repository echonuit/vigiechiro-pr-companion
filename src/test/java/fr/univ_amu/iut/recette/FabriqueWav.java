package fr.univ_amu.iut.recette;

import fr.univ_amu.iut.commun.model.FichierWav;
import java.io.IOException;
import java.nio.file.Path;

/// Écrit des WAV **minimaux mais valides** (mono, PCM 16 bits) au signal **déterministe** (dent de
/// scie `(i * 41) % 1000 - 500`, aucun aléatoire) : deux générations produisent des octets identiques,
/// condition d'une recette rejouable.
///
/// L'**en-tête** RIFF/WAVE est délégué au writer de production [FichierWav#ecrire] (source unique de
/// l'en-tête WAV du dépôt), plutôt que réécrit à la main : cette fabrique n'apporte que le **PCM
/// déterministe** propre à la recette.
final class FabriqueWav {

    private static final int PCM_16_BITS = 16;
    private static final int MONO = 1;
    private static final int OCTETS_PAR_TRAME = MONO * PCM_16_BITS / 8;

    private FabriqueWav() {}

    /// Nombre de trames couvrant `dureeSecondes` à `frequenceHz`.
    static int tramesPour(int frequenceHz, double dureeSecondes) {
        return (int) Math.round(frequenceHz * dureeSecondes);
    }

    /// Écrit dans `fichier` un WAV mono 16 bits de `nombreTrames` trames échantillonné à `frequenceHz`
    /// (valeur inscrite telle quelle dans l'en-tête).
    static void ecrireWav(Path fichier, int frequenceHz, int nombreTrames) throws IOException {
        byte[] pcm = pcmDeterministe(nombreTrames);
        FichierWav.ecrire(fichier, MONO, frequenceHz, PCM_16_BITS, pcm, 0, pcm.length);
    }

    private static byte[] pcmDeterministe(int nombreTrames) {
        byte[] pcm = new byte[nombreTrames * OCTETS_PAR_TRAME];
        for (int i = 0; i < nombreTrames; i++) {
            short echantillon = (short) (((i * 41) % 1000) - 500);
            pcm[2 * i] = (byte) (echantillon & 0xFF);
            pcm[2 * i + 1] = (byte) ((echantillon >> 8) & 0xFF);
        }
        return pcm;
    }
}
