package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Les temporaires de régénération naissent sous l'espace de travail (#6174), et un plantage les y
/// laisse : sur le disque, là où `/tmp` se vidait au redémarrage. Le filet du démarrage les retire.
class DossierTemporaireTest {

    @TempDir
    Path espaceDeTravail;

    @Test
    @DisplayName("#6174 : le filet du démarrage retire les vc-regen-* et vc-hydrate-* restés, et rien d'autre")
    void le_filet_retire_les_temporaires_restes() throws IOException {
        Path regeneration = Files.createDirectories(espaceDeTravail.resolve("vc-regen-12-4087"));
        Files.writeString(regeneration.resolve("Car640380-2026-Pass1-A1-PaRec_000.wav"), "tranche");
        Path hydratation = Files.createDirectories(espaceDeTravail.resolve("vc-hydrate-9931"));
        Files.writeString(hydratation.resolve("Car640380-2026-Pass1-A1-PaRec_001.wav"), "tranche");
        Path session = Files.createDirectories(espaceDeTravail.resolve("Car640380-2026-Pass1-A1"));
        Path homonymeFichier = Files.writeString(espaceDeTravail.resolve("vc-regen-note.txt"), "pas un dossier");

        DossierTemporaire.nettoyerResiduels(espaceDeTravail);

        assertThat(regeneration)
                .as("un temporaire de la voie bruts resté d'un plantage")
                .doesNotExist();
        assertThat(hydratation)
                .as("un temporaire de l'hydratation resté d'un plantage")
                .doesNotExist();
        assertThat(session).as("une session n'est pas un temporaire").exists();
        assertThat(homonymeFichier)
                .as("seuls des dossiers sont des temporaires")
                .exists();
    }

    @Test
    @DisplayName("#6174 : un espace de travail absent ne fait pas échouer le démarrage")
    void un_espace_de_travail_absent_ne_leve_pas() {
        assertThatCode(() -> DossierTemporaire.nettoyerResiduels(espaceDeTravail.resolve("absent")))
                .doesNotThrowAnyException();
    }
}
