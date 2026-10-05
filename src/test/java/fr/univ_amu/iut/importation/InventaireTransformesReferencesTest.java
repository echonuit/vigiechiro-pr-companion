package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.importation.model.InventaireTransformesReferences;
import fr.univ_amu.iut.importation.model.InventaireTransformesReferences.OriginalTransforme;
import fr.univ_amu.iut.importation.model.InventaireTransformesReferences.SequenceTransformee;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// **Un perdant de collision n'est pas une tranche** (#5719). Dans un nom horodaté, le suffixe ne
/// numérote plus rien : chaque tranche porte l'heure de son début et un `_000`, et un `_001` marque le
/// perdant d'une collision, venu d'un **autre** enregistrement que le `_000` du même nom
/// ([fr.univ_amu.iut.commun.model.NommageSequences]). L'inventaire le rangeait en seconde tranche du
/// `_000`, avec un décalage de 5 s qu'il n'a pas.
///
/// Un nom **sans** horodatage garde l'ancienne lecture : ses `_000`, `_001` y sont de vraies tranches.
class InventaireTransformesReferencesTest {

    private static final String HORODATE = "Car640380-2026-Pass1-Z1-PaRecPR1925492_20260422_203922";

    @TempDir
    Path dossier;

    @Test
    @DisplayName("#5719 : un perdant de collision horodaté est son propre original, d'index 0")
    void le_perdant_de_collision_est_son_propre_original() throws IOException {
        ecrire(HORODATE + "_000.wav", 1);
        ecrire(HORODATE + "_001.wav", 2);

        List<OriginalTransforme> originaux = InventaireTransformesReferences.inventorier(dossier);

        assertThat(originaux)
                .hasSize(2)
                .allSatisfy(original -> assertThat(original.sequences())
                        .singleElement()
                        .extracting(SequenceTransformee::index)
                        .isEqualTo(0));
        assertThat(originaux)
                .extracting(OriginalTransforme::nomOriginal)
                .containsExactly(HORODATE + ".wav", HORODATE + "_001.wav");
    }

    @Test
    @DisplayName("#5719 : le perdant garde l'heure de début que porte son nom")
    void le_perdant_garde_son_heure() throws IOException {
        ecrire(HORODATE + "_001.wav", 2);

        assertThat(InventaireTransformesReferences.inventorier(dossier))
                .singleElement()
                .extracting(original -> original.sequences().getFirst().horodatageCapture())
                .isEqualTo(LocalDateTime.of(2026, 4, 22, 20, 39, 22));
    }

    @Test
    @DisplayName("#5719 : sans horodatage, les suffixes restent des tranches du même original")
    void sans_horodatage_les_suffixes_sont_des_tranches() throws IOException {
        ecrire("nuit_000.wav", 1);
        ecrire("nuit_001.wav", 2);

        assertThat(InventaireTransformesReferences.inventorier(dossier))
                .singleElement()
                .satisfies(original -> {
                    assertThat(original.nomOriginal()).isEqualTo("nuit.wav");
                    assertThat(original.sequences())
                            .extracting(SequenceTransformee::index)
                            .containsExactly(0, 1);
                });
    }

    /// Trouvé par PIT à la clôture de #5596 : le repli sur un nom sans suffixe n'était joué par aucun cas.
    @Test
    @DisplayName("un nom sans suffixe de tranche est son propre original, d'index zéro")
    void un_nom_sans_suffixe_est_son_propre_original() throws IOException {
        ecrire("enregistrement.wav", 1);

        assertThat(InventaireTransformesReferences.inventorier(dossier))
                .singleElement()
                .satisfies(original -> {
                    assertThat(original.nomOriginal()).isEqualTo("enregistrement.wav");
                    assertThat(original.sequences())
                            .extracting(SequenceTransformee::index)
                            .containsExactly(0);
                });
    }

    /// Un petit WAV mono 16 bits ; le `germe` varie le contenu pour des empreintes distinctes.
    private void ecrire(String nom, int germe) throws IOException {
        byte[] pcm = new byte[800];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (byte) (i * 31 + germe);
        }
        FichierWav.ecrire(dossier.resolve(nom), 1, 38_400, 16, pcm, 0, pcm.length);
    }
}
