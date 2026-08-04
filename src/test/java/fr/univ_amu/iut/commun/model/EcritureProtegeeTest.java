package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Écriture d'un secret sur disque (#2735) : jamais dans un fichier plus permissif que lui, jamais à
/// moitié.
///
/// ⚠️ Ces tests éprouvent l'état **final**, et la fenêtre que l'issue corrige est un état
/// **intermédiaire** : après coup, écrire-puis-restreindre laisse exactement le même fichier. C'est
/// [fr.univ_amu.iut.architecture.SecretsEcritsProtegesTest] qui garde la forme d'écriture, faute de
/// pouvoir garder l'instant.
///
/// Survivant PIT **assumé** : l'inversion du test « le système de fichiers est-il POSIX ». Mesuré sous
/// Java 25 avec un umask à `0002`, `Files.createTempFile` sans attribut crée déjà `rw-------` (là où
/// `Files.createFile` donne `rw-rw-r--`) : les deux chemins donnent le même fichier, le mutant est
/// **équivalent par construction**. L'attribut explicite reste parce que le JDK qualifie ce défaut d'
/// « implementation specific ».
class EcritureProtegeeTest {

    private static final Set<PosixFilePermission> PROPRIETAIRE_SEUL =
            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    @TempDir
    Path dossier;

    @Test
    @DisplayName("Création : le contenu est là, et le fichier n'est lisible que par son propriétaire")
    void creation_restreinte() throws IOException {
        Path cible = dossier.resolve("connexion.json");

        EcritureProtegee.ecrire(cible, "{\"token\":\"secret\"}");

        assertThat(cible).hasContent("{\"token\":\"secret\"}");
        assertThat(permissions(cible)).containsExactlyInAnyOrderElementsOf(PROPRIETAIRE_SEUL);
        assertThat(temporairesResiduels()).isEmpty();
    }

    @Test
    @DisplayName("Réécriture par-dessus un fichier laissé permissif : le nouveau reste privé")
    void reecriture_ne_herite_pas_des_permissions_laxistes() throws IOException {
        assumeTrue(posixDisponible(), "système de fichiers non POSIX : permissions non applicables");
        Path cible = dossier.resolve("connexion.json");
        // Un fichier que quelqu'un aurait rendu lisible par tous : l'écriture ne doit pas s'y couler.
        Files.createFile(cible, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-rw-rw-")));
        Files.writeString(cible, "ancien");

        EcritureProtegee.ecrire(cible, "nouveau");

        assertThat(cible).hasContent("nouveau");
        assertThat(permissions(cible)).containsExactlyInAnyOrderElementsOf(PROPRIETAIRE_SEUL);
        assertThat(temporairesResiduels()).isEmpty();
    }

    @Test
    @DisplayName("Échec de l'écriture : le contenu précédent survit, et aucun temporaire ne traîne")
    void echec_laisse_l_etat_precedent_intact() throws IOException {
        Path cible = dossier.resolve("connexion.json");
        Files.writeString(cible, "ancien");
        // Un dossier occupe la place du remplacement : le déplacement final ne peut pas aboutir.
        Path impossible = dossier.resolve("occupe");
        Files.createDirectory(impossible);
        Files.writeString(impossible.resolve("dedans"), "x");

        assertThatThrownBy(() -> EcritureProtegee.ecrire(impossible, "nouveau")).isInstanceOf(IOException.class);

        assertThat(cible).hasContent("ancien");
        assertThat(temporairesResiduels())
                .as("un temporaire abandonné garderait le secret sur le disque")
                .isEmpty();
    }

    private static boolean posixDisponible() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    private static Set<PosixFilePermission> permissions(Path chemin) throws IOException {
        assumeTrue(posixDisponible(), "système de fichiers non POSIX : permissions non applicables");
        return Files.getPosixFilePermissions(chemin);
    }

    /// Tout ce qui traîne dans le dossier en dehors des fichiers que les tests y placent eux-mêmes.
    private List<Path> temporairesResiduels() throws IOException {
        try (Stream<Path> entrees = Files.list(dossier)) {
            return entrees.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equals("connexion.json"))
                    .toList();
        }
    }
}
