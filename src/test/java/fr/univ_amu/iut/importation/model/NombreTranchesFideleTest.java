package fr.univ_amu.iut.importation.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.NommageSequences;
import fr.univ_amu.iut.commun.model.Prefixe;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// **L'hypothèse sur laquelle repose l'arbitrage rejoué** (#1932) : le nombre de tranches déduit de la
/// durée persistée doit être **exactement** celui que la transformation produit.
///
/// L'arbitrage de la réactivation se calcule sur les originaux connus en base, dont on ne garde que le nom
/// et la durée. S'il compte une tranche de trop pour un enregistrement, elle réserve un nom ; le nom
/// suivant se décale ; et la réactivation renomme des tranches sous des noms que l'import n'a jamais
/// écrits. Une erreur d'une unité ici se propage à toute la suite de la nuit.
///
/// Les durées réelles ne sont pas des multiples de 5 s : une nuit observée porte des `5,013`, `5,099`,
/// `20,288`, `30,016`. C'est là que `ceil` se joue, et c'est donc là qu'il faut le mesurer.
class NombreTranchesFideleTest {

    private static final int FREQUENCE_ACQUISITION = 2000;
    private static final int CANAUX = 1;
    private static final int BITS = 16;
    private static final int OCTETS_PAR_TRAME = 2;
    private static final int ENTETE_WAV = 44;

    private final Prefixe prefixe = new Prefixe("640380", 2026, 2, "Z1");

    @ParameterizedTest(name = "durée {0} s")
    @ValueSource(doubles = {5.0, 5.013, 5.099, 10.0, 15.723, 20.288, 30.0, 30.016})
    @DisplayName("Le nombre de tranches déduit de la durée est celui que la transformation produit")
    void nombre_de_tranches_fidele(double duree, @TempDir Path tmp) throws IOException {
        Path brut = ecrireWav(tmp.resolve("Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205332.wav"), (int)
                Math.round(duree * FREQUENCE_ACQUISITION));

        TransformationOriginal produit = new TransformationAudio()
                .transformer(
                        brut,
                        brut.getFileName().toString(),
                        tmp.resolve("transformes"),
                        prefixe,
                        FREQUENCE_ACQUISITION);

        assertThat(NommageSequences.nombreTranches(produit.dureeSourceSecondes()))
                .as("tranches déduites de la durée, contre tranches réellement écrites")
                .isEqualTo(produit.sequences().size());
    }

    // --- Helpers (autonomes, pas de helper partagé entre fichiers de test) --------------------

    private static Path ecrireWav(Path fichier, int trames) throws IOException {
        byte[] pcm = new byte[trames * OCTETS_PAR_TRAME];
        for (int i = 0; i < trames; i++) {
            short echantillon = (short) ((7919 + i) % Short.MAX_VALUE);
            pcm[2 * i] = (byte) (echantillon & 0xFF);
            pcm[2 * i + 1] = (byte) ((echantillon >> 8) & 0xFF);
        }
        int blocAlign = CANAUX * (BITS / 8);
        ByteBuffer buf = ByteBuffer.allocate(ENTETE_WAV + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + pcm.length);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) CANAUX);
        buf.putInt(FREQUENCE_ACQUISITION);
        buf.putInt(FREQUENCE_ACQUISITION * blocAlign);
        buf.putShort((short) blocAlign);
        buf.putShort((short) BITS);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(pcm.length);
        buf.put(pcm);
        Files.write(fichier, buf.array());
        return fichier;
    }
}
