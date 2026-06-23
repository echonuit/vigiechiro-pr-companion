package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.importation.model.Renommeur;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests du renommeur (R6/R7) : préfixe appliqué aux originaux, tirets U+002D, idempotence.
class RenommeurTest {

    @TempDir
    Path racine;

    private final Renommeur renommeur = new Renommeur();
    private final Prefixe prefixe = new Prefixe("640380", 2026, 2, "Z1");
    private Path bruts;

    @BeforeEach
    void preparer() throws IOException {
        bruts = Files.createDirectories(racine.resolve("bruts"));
        creerFichier("PaRecPR1925492_20260422_203922.wav");
        creerFichier("PaRecPR1925492_20260422_204326.wav");
        creerFichier("notice.txt"); // non-WAV : doit être ignoré
    }

    @Test
    @DisplayName("R6/R7 : chaque original reçoit le préfixe et conserve son suffixe d'origine")
    void prefixe_applique() {
        List<Path> resultats = renommeur.renommer(bruts, prefixe);

        assertThat(resultats)
                .extracting(p -> p.getFileName().toString())
                .containsExactly(
                        "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_203922.wav",
                        "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_204326.wav");
    }

    @Test
    @DisplayName("R6 : les tirets du préfixe sont des tirets du 6 (U+002D), pas des cadratins")
    void tirets_u002d() {
        renommeur.renommer(bruts, prefixe);

        String nom = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_203922.wav";
        assertThat(Files.exists(bruts.resolve(nom))).isTrue();
        assertThat(nom).doesNotContain("—").doesNotContain("–"); // ni cadratin ni demi-cadratin
        assertThat(nom.chars().filter(c -> c == '-').count()).isEqualTo(4L);
    }

    @Test
    @DisplayName("Le fichier non-WAV n'est pas renommé")
    void non_wav_ignore() {
        renommeur.renommer(bruts, prefixe);

        assertThat(Files.exists(bruts.resolve("notice.txt"))).isTrue();
    }

    @Test
    @DisplayName("#111 : un fichier déjà préfixé (autre rattachement) est conservé, jamais re-préfixé")
    void deja_prefixe_discordant_non_re_prefixe() throws IOException {
        // Un WAV portant un préfixe R6 d'un AUTRE rattachement (carré/année/passage/point différents).
        String dejaPrefixe = "Car111111-2025-Pass1-A1-PaRecPR1925492_20260422_203922.wav";
        creerFichier(dejaPrefixe);

        List<Path> resultats = renommeur.renommer(bruts, prefixe);

        // Le nom discordant est conservé tel quel (pas de double préfixe Car640380-...-Car111111-...).
        assertThat(Files.exists(bruts.resolve(dejaPrefixe)))
                .as("le fichier déjà préfixé reste inchangé")
                .isTrue();
        assertThat(resultats)
                .extracting(p -> p.getFileName().toString())
                .contains(dejaPrefixe)
                .allSatisfy(nom -> assertThat(nom).doesNotContain("Car640380-2026-Pass2-Z1-Car"));
    }

    @Test
    @DisplayName("Idempotent : relancer le renommage ne re-préfixe pas les fichiers déjà nommés")
    void idempotent() throws IOException {
        renommeur.renommer(bruts, prefixe);
        List<Path> secondPassage = renommeur.renommer(bruts, prefixe);

        assertThat(secondPassage)
                .extracting(p -> p.getFileName().toString())
                .allSatisfy(nom -> assertThat(nom).doesNotContain("Car640380-2026-Pass2-Z1-Car"));
        try (Stream<Path> flux = Files.list(bruts)) {
            assertThat(flux.filter(p -> p.getFileName().toString().endsWith(".wav")))
                    .hasSize(2);
        }
    }

    private void creerFichier(String nom) throws IOException {
        Files.writeString(bruts.resolve(nom), "contenu " + nom, StandardCharsets.UTF_8);
    }
}
