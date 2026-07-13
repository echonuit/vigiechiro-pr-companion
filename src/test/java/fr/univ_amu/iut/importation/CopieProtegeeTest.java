package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.importation.model.CopieProtegee;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests de la copie protégée (R9) : la SD est copiée vers le workspace, **sans jamais** être
/// modifiée. Le cœur du test est le SHA-256 des fichiers source, identique avant et après copie.
class CopieProtegeeTest {

    @TempDir
    Path racine;

    private final CopieProtegee copie = new CopieProtegee();
    private Path source;
    private Path destination;

    @BeforeEach
    void preparer() throws IOException {
        source = Files.createDirectories(racine.resolve("sd"));
        destination = racine.resolve("workspace/bruts");
        Files.writeString(source.resolve("a.wav"), "octets PCM factices A", StandardCharsets.UTF_8);
        Files.writeString(source.resolve("b.wav"), "octets PCM factices B", StandardCharsets.UTF_8);
        Files.writeString(source.resolve("LogPR1925492.txt"), "journal", StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("R9 : la copie est fidèle (empreinte destination = empreinte source)")
    void copie_fidele() {
        Path copieA = copie.copierVers(source.resolve("a.wav"), destination);

        assertThat(Files.exists(copieA)).isTrue();
        assertThat(Empreintes.sha256Hex(copieA)).isEqualTo(Empreintes.sha256Hex(source.resolve("a.wav")));
    }

    @Test
    @DisplayName("R9 : aucune écriture sur la source (empreintes SD identiques avant et après)")
    void source_jamais_modifiee() throws IOException {
        Map<Path, String> empreintesAvant = empreintesDuDossier(source);

        copie.copierVers(source.resolve("a.wav"), destination);
        copie.copierVers(source.resolve("b.wav"), destination);
        copie.copierVers(source.resolve("LogPR1925492.txt"), racine.resolve("workspace"));

        Map<Path, String> empreintesApres = empreintesDuDossier(source);
        assertThat(empreintesApres)
                .as("la carte SD doit être strictement inchangée après la copie (R9)")
                .isEqualTo(empreintesAvant);
    }

    @Test
    @DisplayName("La copie crée les dossiers parents manquants du workspace")
    void cree_les_dossiers_parents() {
        Path cible = racine.resolve("workspace/Car640380-2026-Pass2-Z1/bruts/a.wav");

        Path ecrit = copie.copier(source.resolve("a.wav"), cible);

        assertThat(Files.exists(ecrit)).isTrue();
        assertThat(Files.isDirectory(cible.getParent())).isTrue();
    }

    @Test
    @DisplayName("Le message d'échec « disque plein » (ENOSPC) est explicite et actionnable")
    void message_echec_disque_plein() {
        String message = CopieProtegee.messageEchec(source.resolve("a.wav"), "No space left on device");

        assertThat(message)
                .contains("Espace disque insuffisant")
                .contains("a.wav")
                .doesNotContain("copie protégée");
    }

    @Test
    @DisplayName("Pour toute autre cause d'IOException, la cause technique est jointe au message")
    void message_echec_joint_la_cause() {
        String message = CopieProtegee.messageEchec(source.resolve("a.wav"), "Permission denied");

        assertThat(message).contains("Échec de la copie protégée").contains("Permission denied");
    }

    /// Empreinte SHA-256 de chaque fichier du dossier (clé = nom de fichier).
    private static Map<Path, String> empreintesDuDossier(Path dossier) throws IOException {
        Map<Path, String> empreintes = new LinkedHashMap<>();
        try (var flux = Files.list(dossier)) {
            flux.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(p -> empreintes.put(p.getFileName(), Empreintes.sha256Hex(p)));
        }
        return empreintes;
    }
}
