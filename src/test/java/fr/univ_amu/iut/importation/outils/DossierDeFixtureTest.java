package fr.univ_amu.iut.importation.outils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Un dossier de fixture doit montrer **un** état, jamais la somme de ceux qu'il a hérités (#4044).
class DossierDeFixtureTest {

    private static final String LOG = "recorder.serial_number: 1925492\n";

    @Test
    @DisplayName("#4044 : une seconde préparation ne laisse RIEN de la première")
    void une_seconde_preparation_ne_laisse_rien_de_la_premiere(@TempDir Path temporaire) throws IOException {
        String ancienTmp = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", temporaire.toString());
        try {
            DossierDeFixture.preparer("sd-essai", LOG, List.of("PaRecPR1925492_20260422_203922.wav"));
            Path sd = DossierDeFixture.preparer("sd-essai", LOG, List.of("PaRecPR1648011_20260422_204010.wav"));

            assertThat(wavs(sd))
                    .as("le chemin d'une fixture est déterministe : sans vidage, les fichiers de la"
                            + " préparation précédente restent, et la capture montre leur SOMME - vécu"
                            + " en ajustant une fixture, « 4 enregistrement(s) WAV détecté(s) » pour"
                            + " une fixture qui en déclare deux")
                    .containsExactly("PaRecPR1648011_20260422_204010.wav");
        } finally {
            System.setProperty("java.io.tmpdir", ancienTmp);
        }
    }

    @Test
    @DisplayName("#4044 : le journal et le relevé sont écrits à côté des WAV")
    void le_journal_et_le_releve_accompagnent_les_wav(@TempDir Path temporaire) throws IOException {
        String ancienTmp = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", temporaire.toString());
        try {
            Path sd = DossierDeFixture.preparer("sd-complet", LOG, List.of("a.wav", "b.wav"));

            assertThat(sd.resolve("LogPR1925492.txt")).exists();
            assertThat(sd.resolve("PaRecPR1925492_THLog.csv")).exists();
            assertThat(Files.readString(sd.resolve("LogPR1925492.txt")))
                    .as("le journal porte ce qu'on lui a donné : c'est lui qui fixe la série et la nuit"
                            + " que l'inspection lira")
                    .isEqualTo(LOG);
        } finally {
            System.setProperty("java.io.tmpdir", ancienTmp);
        }
    }

    private static List<String> wavs(Path dossier) throws IOException {
        try (Stream<Path> contenu = Files.list(dossier)) {
            return contenu.map(chemin -> chemin.getFileName().toString())
                    .filter(nom -> nom.endsWith(".wav"))
                    .sorted()
                    .toList();
        }
    }
}
