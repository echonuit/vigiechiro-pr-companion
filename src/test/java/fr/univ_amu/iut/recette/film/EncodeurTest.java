package fr.univ_amu.iut.recette.film;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
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
    void un_programme_introuvable_est_refuse() {
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
    void un_chemin_impose_est_honore(@TempDir Path dossier) throws IOException {
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
    void un_chemin_impose_non_executable_est_refuse(@TempDir Path dossier) throws IOException {
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

    @Test
    @DisplayName("#4522 : hors POSIX, c'est le suffixe de PATHEXT qui dit si un fichier est un programme")
    void hors_posix_le_suffixe_decide(@TempDir Path dossier) throws IOException {
        // La branche Windows, jouée depuis un poste POSIX : sans cette couture elle ne serait
        // éprouvée qu'une fois par semaine, sur la machine où elle a rougi (ADR 3802).
        // `PATHEXT` est celui que la sonde a lu sous Windows Server 2025 (run 32942466901).
        String pathext = ".COM;.EXE;.BAT;.CMD;.VBS;.VBE;.JS;.JSE;.WSF;.WSH;.MSC";
        Path sansSuffixe = Files.writeString(dossier.resolve("inerte"), "pas un programme");
        Path texte = Files.writeString(dossier.resolve("inerte.txt"), "pas un programme");
        Path lot = Files.writeString(dossier.resolve("vrai.bat"), "@echo off\n");

        assertFalse(
                Encodeur.VersFfmpeg.estExecutable(sansSuffixe, false, pathext),
                "c'est ce fichier-là que Windows tenait pour exécutable, et le refus ne partait pas");
        assertFalse(Encodeur.VersFfmpeg.estExecutable(texte, false, pathext));
        assertTrue(Encodeur.VersFfmpeg.estExecutable(lot, false, pathext));
        assertFalse(
                Encodeur.VersFfmpeg.estExecutable(lot, false, null),
                "un PATHEXT absent ferme la question au lieu de l'ouvrir à tout");
        assertFalse(
                Encodeur.VersFfmpeg.estExecutable(dossier, false, pathext),
                "un dossier n'est pas un programme, et Windows le tient pourtant pour exécutable");
    }

    /// La couture joue la branche Windows depuis Linux ; elle ne joue pas l'inverse. Cette branche-ci
    /// délègue à `Files.isExecutable`, dont la réponse change de système : sous Windows elle rend
    /// `true` pour tout fichier existant (run 32945079829).
    @Test
    @DisplayName("#4522 : sous POSIX, c'est le bit d'exécution qui décide")
    @EnabledIf("fr.univ_amu.iut.recette.film.EncodeurTest#posixDisponible")
    void sous_posix_le_bit_decide(@TempDir Path dossier) throws IOException {
        Path inerte = Files.writeString(dossier.resolve("inerte.bat"), "pas un programme");

        assertFalse(
                Encodeur.VersFfmpeg.estExecutable(inerte, true, ".BAT"),
                "sous POSIX un suffixe ne rend rien exécutable");
        assertTrue(Encodeur.VersFfmpeg.estExecutable(executable(dossier.resolve("vrai")), true, ""));
    }

    /// La vue POSIX est-elle celle de cette machine ? Condition de [#sous_posix_le_bit_decide].
    static boolean posixDisponible() {
        return Encodeur.VersFfmpeg.vuePosixDisponible();
    }

    /// Un fichier réellement exécutable, ou le test n'éprouverait que `Files.exists`.
    ///
    /// Le nom prend un suffixe hors POSIX : c'est là ce qui fait qu'un fichier est un programme, et
    /// le rendu porte donc le chemin construit plutôt que celui qu'on a demandé.
    private static Path executable(Path chemin) throws IOException {
        Path cible = Encodeur.VersFfmpeg.vuePosixDisponible() ? chemin : Path.of(chemin + ".bat");
        Files.writeString(cible, "#!/bin/sh\n");
        try {
            Files.setPosixFilePermissions(
                    cible, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException horsPosix) {
            cible.toFile().setExecutable(true);
        }
        return cible;
    }
}
