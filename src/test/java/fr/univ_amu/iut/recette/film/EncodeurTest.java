package fr.univ_amu.iut.recette.film;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le refus d'encoder quand l'encodeur n'est pas là.
///
/// Ce que ces cas gardent n'est pas la résolution en elle-même : c'est que le banc **dise** ce qui
/// lui manque. Lancé par son nom nu, un `ffmpeg` absent produisait un échec du système au moment
/// d'écrire la première image, loin de la cause et sans nommer le remède.
class EncodeurTest {

    private static final String PROPRIETE = "recette.faux-encodeur";

    @Test
    @DisplayName("un programme introuvable est refusé, et le refus le NOMME")
    void unProgrammeIntrouvableEstRefuse() {
        IOException refus = assertThrows(
                IOException.class, () -> Encodeur.VersFfmpeg.resoudre("programme-qui-n-existe-nulle-part-42"));
        assertTrue(
                refus.getMessage().contains("programme-qui-n-existe-nulle-part-42"),
                "le refus devrait nommer le programme : " + refus.getMessage());
        assertTrue(
                refus.getMessage().contains("-Drecette."),
                "le refus devrait dire comment le désigner : " + refus.getMessage());
    }

    @Test
    @DisplayName("un chemin imposé par propriété est honoré")
    void unCheminImposeEstHonore(@TempDir Path dossier) throws IOException {
        Path faux = executable(dossier.resolve("faux-encodeur"));
        System.setProperty(PROPRIETE, faux.toString());
        try {
            assertEquals(faux, Encodeur.VersFfmpeg.resoudre("faux-encodeur"));
        } finally {
            System.clearProperty(PROPRIETE);
        }
    }

    @Test
    @DisplayName("un chemin imposé qui n'est pas exécutable est refusé, et non ignoré")
    void unCheminImposeNonExecutableEstRefuse(@TempDir Path dossier) throws IOException {
        // Ignorer silencieusement une propriété fausse ferait retomber sur le PATH, donc lancer
        // AUTRE CHOSE que ce qui a été demandé. Le refus vaut mieux que la substitution.
        Path inerte = Files.writeString(dossier.resolve("inerte"), "pas un programme");
        System.setProperty(PROPRIETE, inerte.toString());
        try {
            IOException refus = assertThrows(IOException.class, () -> Encodeur.VersFfmpeg.resoudre("faux-encodeur"));
            assertTrue(
                    refus.getMessage().contains(inerte.toString()),
                    "le refus devrait nommer le chemin fautif : " + refus.getMessage());
        } finally {
            System.clearProperty(PROPRIETE);
        }
    }

    /// Un fichier réellement exécutable, ou le test n'éprouverait que `Files.exists`.
    private static Path executable(Path chemin) throws IOException {
        Files.writeString(chemin, "#!/bin/sh\n");
        try {
            Files.setPosixFilePermissions(
                    chemin, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException horsPosix) {
            chemin.toFile().setExecutable(true);
        }
        return chemin;
    }
}
