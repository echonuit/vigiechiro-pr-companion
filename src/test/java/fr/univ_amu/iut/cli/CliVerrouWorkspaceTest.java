package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.persistence.VerrouWorkspace;
import fr.univ_amu.iut.fixture.SortieCapturee;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La CLI et le **verrou du dossier de travail** (#3498).
///
/// `VerrouWorkspace` (#2731) nomme lui-même le cas qui l'a fait naître : « deux instances graphiques,
/// **une IHM et une CLI**, ou une restauration pendant un import ». L'application graphique réserve le
/// dossier pour toute sa durée ; la CLI ne le demandait jamais, et écrivait donc par-dessus.
///
/// ⚠️ Le verrou est **réentrant dans un processus** (`DETENUS`) : le prendre ici par
/// `VerrouWorkspace.prendre` rendrait celui de la CLI factice, et le test ne prouverait rien. On pose
/// donc le verrou de fichier **brut**, ce que la CLI verra comme un chevauchement - exactement ce
/// qu'elle voit quand une autre instance le détient.
class CliVerrouWorkspaceTest {

    @TempDir
    Path workspace;

    @TempDir
    Path config;

    private Cli cli;
    private final SortieCapturee capture = new SortieCapturee();
    private final PrintStream sortie = capture.sortie();
    private final PrintStream erreur = capture.erreur();

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        System.setProperty("vigiechiro.config", config.toString());
        Injector injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
        System.clearProperty("vigiechiro.config");
    }

    @Test
    @DisplayName("une commande qui écrit est refusée quand le dossier est déjà occupé")
    void une_commande_d_ecriture_est_refusee_sur_un_dossier_occupe() throws IOException {
        // ⚠️ La base doit exister ET être à jour AVANT de verrouiller. Sur un workspace neuf, la
        // migration est en attente et prend le verrou d'elle-même : le refus viendrait d'elle, pas de
        // la commande, et le test passerait au vert sans rien prouver du défaut.
        cli.executer(new String[] {"lister-sites"}, sortie, erreur);
        capture.vider();

        try (Occupation ignore = new Occupation(workspace)) {
            int code = cli.executer(
                    new String[] {"creer-site", "--carre", "640380", "--nom", "Aix centre"}, sortie, erreur);

            assertThat(code)
                    .as("un refus, pas une panne : l'état local est intact, et un script doit pouvoir"
                            + " distinguer les deux (code 2, convention #2294)")
                    .isEqualTo(Cli.CODE_REFUS);
            assertThat(capture.texteErreur())
                    .as("le message doit nommer ce qui occupe la place et quoi faire")
                    .contains("déjà utilisé");
        }
    }

    @Test
    @DisplayName("une commande qui ne fait que lire passe, même sur un dossier occupé")
    void une_commande_de_lecture_passe_sur_un_dossier_occupe() throws IOException {
        // Refuser une lecture coûterait plus que la protection ne rapporte : c'est déjà écrit dans
        // `MigrationSchema`, et c'est la moitié du contrat qu'on casserait en verrouillant tout.
        cli.executer(new String[] {"creer-site", "--carre", "640380", "--nom", "Aix centre"}, sortie, erreur);
        capture.vider();

        try (Occupation ignore = new Occupation(workspace)) {
            int code = cli.executer(new String[] {"lister-sites"}, sortie, erreur);

            assertThat(code).isEqualTo(Cli.CODE_SUCCES);
            assertThat(capture.texte()).contains("640380");
        }
    }

    /// Le dossier de travail, tenu par « quelqu'un d'autre ».
    ///
    /// Le verrou de fichier brut, et non `VerrouWorkspace.prendre` : celui-ci inscrirait le dossier
    /// dans `DETENUS`, et la CLI - même JVM - obtiendrait un verrou factice sans jamais rien refuser.
    private static final class Occupation implements AutoCloseable {

        private final FileChannel canal;
        private final FileLock verrou;

        Occupation(Path workspace) throws IOException {
            Files.createDirectories(workspace);
            canal = FileChannel.open(
                    workspace.resolve(VerrouWorkspace.NOM_FICHIER),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            verrou = canal.lock();
        }

        @Override
        public void close() throws IOException {
            try (FileChannel aFermer = canal) {
                verrou.release();
            }
        }
    }
}
