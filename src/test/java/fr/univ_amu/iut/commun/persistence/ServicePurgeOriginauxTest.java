package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux.ResultatPurge;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Purge des originaux ([ServicePurgeOriginaux]) sur un workspace jetable (@TempDir). On vérifie que
/// seuls les `bruts/` sont supprimés (les `transformes/` et la base survivent), le volume libéré, et le
/// caractère idempotent.
class ServicePurgeOriginauxTest {

    @TempDir
    Path racine;

    private Workspace workspace;
    private ServicePurgeOriginaux purge;

    @BeforeEach
    void preparer() {
        workspace = new Workspace(racine);
        purge = new ServicePurgeOriginaux(workspace);
    }

    /// Sème une session `<prefixe>/` avec un `bruts/` (un original de `octetsBruts`) et un `transformes/`
    /// (une séquence). Renvoie le dossier de session.
    private Path semerSession(String prefixe, int octetsBruts) throws IOException {
        Path bruts = Files.createDirectories(workspace.dossierBruts(prefixe));
        Files.write(bruts.resolve("original.wav"), new byte[octetsBruts]);
        Path transformes = Files.createDirectories(workspace.dossierTransformes(prefixe));
        Files.writeString(transformes.resolve("seq_000.wav"), "sequence", StandardCharsets.UTF_8);
        return workspace.dossierSession(prefixe);
    }

    @Test
    @DisplayName("volumeRecuperable somme la taille de tous les bruts/ du workspace")
    void volume_recuperable_somme_les_bruts() throws IOException {
        semerSession("Car640380-2026-Pass1-A1", 1000);
        semerSession("Car640380-2026-Pass2-A1", 500);

        assertThat(purge.volumeRecuperable()).isEqualTo(1500L);
    }

    @Test
    @DisplayName("purgerTout supprime tous les bruts/ mais conserve transformes/ et la base")
    void purger_tout_supprime_les_bruts_seulement() throws IOException {
        Path session1 = semerSession("Car640380-2026-Pass1-A1", 1000);
        semerSession("Car640380-2026-Pass2-A1", 500);
        Files.writeString(workspace.cheminBaseDeDonnees(), "db", StandardCharsets.UTF_8);

        ResultatPurge resultat = purge.purgerTout();

        assertThat(resultat.nombreSessions()).isEqualTo(2);
        assertThat(resultat.octetsLiberes()).isEqualTo(1500L);
        // Les bruts/ ont disparu…
        assertThat(workspace.dossierBruts("Car640380-2026-Pass1-A1")).doesNotExist();
        assertThat(workspace.dossierBruts("Car640380-2026-Pass2-A1")).doesNotExist();
        // …mais les séquences transformées et la base sont intactes.
        assertThat(workspace.dossierTransformes("Car640380-2026-Pass1-A1").resolve("seq_000.wav"))
                .exists();
        assertThat(workspace.cheminBaseDeDonnees()).exists();
        assertThat(session1).exists(); // le dossier de session demeure (il porte transformes/)
    }

    @Test
    @DisplayName("purgerSession ne supprime que le bruts/ de la session visée")
    void purger_session_cible_une_seule_nuit() throws IOException {
        Path session1 = semerSession("Car640380-2026-Pass1-A1", 1000);
        semerSession("Car640380-2026-Pass2-A1", 500);

        long liberes = purge.purgerSession(session1);

        assertThat(liberes).isEqualTo(1000L);
        assertThat(workspace.dossierBruts("Car640380-2026-Pass1-A1")).doesNotExist();
        // L'autre session est intacte.
        assertThat(workspace.dossierBruts("Car640380-2026-Pass2-A1")).isDirectory();
    }

    @Test
    @DisplayName("purge idempotente : re-purger un bruts/ absent libère 0 sans échec")
    void purge_idempotente() throws IOException {
        Path session = semerSession("Car640380-2026-Pass1-A1", 1000);
        purge.purgerSession(session);

        assertThat(purge.purgerSession(session)).isZero();
        assertThat(purge.purgerTout()).isEqualTo(new ResultatPurge(0, 0L));
    }

    @Test
    @DisplayName("workspace sans aucune session : volume 0 et purge sans effet")
    void workspace_vide() {
        assertThat(purge.volumeRecuperable()).isZero();
        assertThat(purge.purgerTout()).isEqualTo(new ResultatPurge(0, 0L));
    }
}
