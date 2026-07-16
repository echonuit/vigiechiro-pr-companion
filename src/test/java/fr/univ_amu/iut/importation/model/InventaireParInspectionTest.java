package fr.univ_amu.iut.importation.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.passage.model.BrutInventorie;
import fr.univ_amu.iut.passage.model.InventaireBruts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Inventaire des bruts d'un passage reconstruit (#1649) : à partir d'un dossier, la fréquence est lue du
/// **log** enregistreur et chaque brut reçoit son **nom R6**. Sans log exploitable, l'inventaire renonce
/// (repli sur le compte rendu honnête #1648), plutôt que d'inventer une fréquence.
class InventaireParInspectionTest {

    private static final Prefixe PREFIXE = new Prefixe("130711", 2026, 1, "Z41");

    private final InventaireParInspection inventaire =
            new InventaireParInspection(new InspecteurDossier(new AnalyseurLogPR()));

    @TempDir
    Path dossier;

    @Test
    @DisplayName("Log présent : la Fe est lue du log et chaque brut de carte SD reçoit son nom R6")
    void log_present_bruts_nommes_r6() throws IOException {
        ecrireLog(dossier.resolve("LogPR1997632.txt"), 384);
        ecrireBrut(dossier.resolve("PaRecPR1997632_20260703_210004.wav"));
        ecrireBrut(dossier.resolve("PaRecPR1997632_20260703_210254.wav"));

        Optional<InventaireBruts> resultat = inventaire.inventorier(dossier, PREFIXE);

        assertThat(resultat).isPresent();
        InventaireBruts inventorie = resultat.orElseThrow();
        assertThat(inventorie.frequenceAcquisitionHz())
                .as("la Fe est celle du log (384 kHz), pas celle de l'en-tête (Fe/10)")
                .isEqualTo(384_000);
        assertThat(inventorie.bruts())
                .extracting(BrutInventorie::nomOriginal)
                .containsExactlyInAnyOrder(
                        "Car130711-2026-Pass1-Z41-PaRecPR1997632_20260703_210004.wav",
                        "Car130711-2026-Pass1-Z41-PaRecPR1997632_20260703_210254.wav");
    }

    @Test
    @DisplayName("Sans log exploitable : l'inventaire renonce (pas de fréquence inventée)")
    void sans_log_renonce() throws IOException {
        ecrireBrut(dossier.resolve("PaRecPR1997632_20260703_210004.wav"));

        assertThat(inventaire.inventorier(dossier, PREFIXE)).isEmpty();
    }

    @Test
    @DisplayName("Un brut déjà préfixé R6 (copie du dossier bruts/) n'est jamais re-préfixé")
    void brut_deja_prefixe_conserve() throws IOException {
        ecrireLog(dossier.resolve("LogPR1997632.txt"), 384);
        String nomR6 = "Car130711-2026-Pass1-Z41-PaRecPR1997632_20260703_210004.wav";
        ecrireBrut(dossier.resolve(nomR6));

        Optional<InventaireBruts> resultat = inventaire.inventorier(dossier, PREFIXE);

        assertThat(resultat).isPresent();
        assertThat(resultat.orElseThrow().bruts())
                .singleElement()
                .extracting(BrutInventorie::nomOriginal)
                .isEqualTo(nomR6);
    }

    /// Journal minimal au format du firmware Teensy : une ligne « Paramètres » porte la fréquence `Fe…kHz`.
    private void ecrireLog(Path fichier, int frequenceKhz) throws IOException {
        Files.write(
                fichier,
                List.of(
                        "03/07/26 - 19:00:00 PR1997632 Démarrage v1.0",
                        "03/07/26 - 19:00:01 PR1997632 Paramètres : Acquisi. 19:00-04:00, Fe" + frequenceKhz
                                + "kHz, S. R. Med, Bd. Freq. 8-120kHz"),
                StandardCharsets.UTF_8);
    }

    /// Brut synthétique minimal : l'inventaire ne lit que le nom (pas le contenu), un WAV valide suffit.
    private void ecrireBrut(Path fichier) throws IOException {
        FichierWav.ecrire(fichier, 1, 38_400, 16, new byte[2_000], 0, 2_000);
    }
}
