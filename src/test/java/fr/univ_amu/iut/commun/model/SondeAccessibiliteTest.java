package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import fr.univ_amu.iut.commun.model.SondeAccessibilite.Verdict;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La sonde partagée qui éprouve un dossier de destination (#2258, harmonisation ; née de #1038).
class SondeAccessibiliteTest {

    @Test
    @DisplayName("Un dossier inscriptible est ACCESSIBLE, et un dossier manquant est créé")
    void dossier_inscriptible(@TempDir Path racine) {
        assertThat(SondeAccessibilite.sonder(racine)).isEqualTo(Verdict.ACCESSIBLE);

        Path aCreer = racine.resolve("sous").resolve("dossier");
        assertThat(SondeAccessibilite.sonder(aCreer))
                .as("désigner un dossier encore inexistant est un usage normal")
                .isEqualTo(Verdict.ACCESSIBLE);
        assertThat(aCreer).isDirectory();
    }

    @Test
    @DisplayName("Le fichier témoin est effacé : la sonde ne jonche pas le dossier de l'utilisateur")
    void ne_laisse_pas_de_temoin(@TempDir Path racine) throws IOException {
        assertThat(SondeAccessibilite.sonder(racine)).isEqualTo(Verdict.ACCESSIBLE);

        try (var contenu = Files.list(racine)) {
            assertThat(contenu)
                    .as("la sonde écrit un témoin pour éprouver l'écriture : elle doit l'effacer ensuite")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("Un fichier n'est PAS un dossier")
    void fichier(@TempDir Path racine) throws IOException {
        Path fichier = Files.createFile(racine.resolve("un-fichier"));
        assertThat(SondeAccessibilite.sonder(fichier)).isEqualTo(Verdict.PAS_UN_DOSSIER);
    }

    @Test
    @DisplayName("Un dossier impossible à créer (sous un fichier) est INEXISTANT_NON_CREABLE")
    void non_creable(@TempDir Path racine) throws IOException {
        Path fichier = Files.createFile(racine.resolve("obstacle"));
        assertThat(SondeAccessibilite.sonder(fichier.resolve("dessous")))
                .as("créer un dossier SOUS un fichier régulier échoue")
                .isEqualTo(Verdict.INEXISTANT_NON_CREABLE);
    }

    @Test
    @DisplayName("Un dossier en lecture seule est NON_INSCRIPTIBLE")
    void lecture_seule(@TempDir Path racine) throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"), "POSIX requis");
        Path lecture = Files.createDirectory(racine.resolve("lecture-seule"));
        Files.setPosixFilePermissions(lecture, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            // Auto-garde : si l'on peut écrire malgré r-x (exécution en root), le test ne prouve rien.
            boolean ecritureRefusee;
            try {
                Files.delete(Files.createTempFile(lecture, "root", ".tmp"));
                ecritureRefusee = false;
            } catch (IOException attendu) {
                // C'est le comportement voulu : on ne peut pas écrire.
                ecritureRefusee = true;
            }
            assumeTrue(ecritureRefusee, "écriture possible malgré r-x (probablement root) : test sans objet");
            assertThat(SondeAccessibilite.sonder(lecture)).isEqualTo(Verdict.NON_INSCRIPTIBLE);
        } finally {
            Files.setPosixFilePermissions(lecture, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    @DisplayName("Chaque verdict d'échec porte un motif lisible ; ACCESSIBLE est le seul utilisable")
    void motifs_et_accessibilite() {
        assertThat(Verdict.ACCESSIBLE.accessible()).isTrue();
        for (Verdict v : Verdict.values()) {
            if (v != Verdict.ACCESSIBLE) {
                assertThat(v.accessible()).isFalse();
                assertThat(v.motif()).isNotBlank();
            }
        }
    }
}
