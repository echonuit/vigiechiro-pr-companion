package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La question posée au volume, et **rien de plus** (#4991).
///
/// Le cas positif ne se fabrique pas depuis un banc : monter un volume en lecture seule demande les
/// droits d'administration, et une image de boucle est un dispositif que la CI n'a pas. Il est donc
/// cherché **parmi les montages réels** de la machine, et le test se déclare sans objet quand il n'y
/// en a aucun - ce que `assumeTrue` dit à voix haute, plutôt que de passer en silence sur rien.
///
/// La vérification qui manque ici est nommée dans #4991 : une carte réellement passée en lecture
/// seule, sous Windows, où le drapeau lu est `FILE_READ_ONLY_VOLUME`.
class VolumeEnLectureSeuleTest {

    @TempDir
    Path dossier;

    @Test
    @DisplayName("#4991 : un volume ordinaire n'est pas dit en lecture seule")
    void un_volume_ordinaire_se_tait() {
        assertThat(VolumeEnLectureSeule.vrai(dossier)).isFalse();
    }

    @Test
    @DisplayName("#4991 : un dossier dont les PERMISSIONS refusent l'écriture n'est pas le sujet")
    void les_permissions_ne_sont_pas_le_volume() throws IOException {
        // La distinction qui fait tout le lot. `Files.isWritable` répond aux permissions, et ment sur
        // les partages réseau comme sous Windows ; cette classe interroge le VOLUME. Un dossier
        // protégé sur un disque sain n'est pas une carte en fin de vie, et le dire serait une fausse
        // alerte - le genre qui apprend à ignorer le vrai message.
        Path protege = Files.createDirectory(dossier.resolve("protege"));
        Files.setPosixFilePermissions(protege, java.nio.file.attribute.PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assertThat(Files.isWritable(protege))
                    .as("les permissions refusent bien l'écriture, c'est la prémisse du cas")
                    .isFalse();
            assertThat(VolumeEnLectureSeule.vrai(protege))
                    .as("et pourtant le volume, lui, est inscriptible : on ne confond pas les deux")
                    .isFalse();
        } finally {
            Files.setPosixFilePermissions(
                    protege, java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    @DisplayName("#4991 : un chemin qui n'existe pas ne fait pas conclure")
    void un_chemin_absent_ne_conclut_pas() {
        // Le volume peut avoir été retiré entre l'inspection et le rafraîchissement. Une lecture
        // ratée doit rendre « je ne sais pas », c'est-à-dire le silence d'aujourd'hui, jamais une
        // alerte.
        assertThat(VolumeEnLectureSeule.vrai(dossier.resolve("carte-retiree"))).isFalse();
    }

    @Test
    @DisplayName("#4991 : sur un montage RÉELLEMENT en lecture seule, le drapeau remonte")
    void un_montage_en_lecture_seule_est_reconnu() {
        Path monte = unMontageEnLectureSeule();
        assumeTrue(monte != null, "aucun montage en lecture seule sur cette machine : cas sans objet");

        assertThat(VolumeEnLectureSeule.vrai(monte)).isTrue();
    }

    /// Un point de montage en lecture seule pris sur la machine, ou `null`.
    ///
    /// Les images `squashfs` de `snap` en sont, et les runners Ubuntu en portent. On lit `/proc/mounts`
    /// plutôt que d'en nommer un : un chemin écrit en dur disparaîtrait le jour où la machine change,
    /// et le test rougirait pour une raison qui n'a rien à voir avec ce qu'il éprouve.
    private static Path unMontageEnLectureSeule() {
        Path table = Path.of("/proc/mounts");
        if (!Files.isReadable(table)) {
            return null;
        }
        try (Stream<String> lignes = Files.lines(table)) {
            return lignes.map(l -> l.split(" "))
                    .filter(c -> c.length > 3 && c[3].startsWith("ro,"))
                    .map(c -> Path.of(c[1].replace("\\040", " ")))
                    .filter(Files::isDirectory)
                    .findFirst()
                    .orElse(null);
        } catch (IOException | RuntimeException illisible) {
            return null;
        }
    }
}
