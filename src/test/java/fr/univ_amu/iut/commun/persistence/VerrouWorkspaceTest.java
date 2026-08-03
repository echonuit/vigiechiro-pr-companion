package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import fr.univ_amu.iut.commun.model.Workspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le **verrou de workspace** (#2731, lot 1 du chantier de dette #2720).
///
/// Rien n'empêchait deux processus de travailler sur le même workspace : deux instances graphiques,
/// une IHM et une CLI, ou une restauration pendant un import. Toutes les garanties que ce lot vient
/// de poser (migration atomique, filet, restauration vérifiée) tombent si un second processus écrit
/// pendant l'opération.
///
/// Le verrou est un **verrou de fichier système** et non un fichier de PID : le système le relâche
/// quand le processus meurt, donc un plantage ne condamne pas le workspace. Le PID écrit dans le
/// fichier sert au **message**, jamais à la décision.
class VerrouWorkspaceTest {

    @TempDir
    Path racine;

    @Test
    @DisplayName("le premier preneur obtient le verrou")
    void premier_preneur_obtient_le_verrou() {
        try (VerrouWorkspace verrou = VerrouWorkspace.prendre(workspace()).orElseThrow()) {
            assertThat(verrou.detenu()).isTrue();
        }
    }

    @Test
    @DisplayName("un second preneur ne l'obtient pas tant que le premier le tient")
    void second_preneur_refuse() {
        try (VerrouWorkspace premier = VerrouWorkspace.prendre(workspace()).orElseThrow()) {
            assertThat(premier.detenu()).isTrue();

            Optional<VerrouWorkspace> second = VerrouWorkspace.prendre(workspace());

            assertThat(second)
                    .as("deux processus sur le même workspace : le second doit repartir, pas écrire")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("le verrou relâché est de nouveau prenable, et ne se dit plus détenu")
    void verrou_relache_est_reprenable() {
        VerrouWorkspace rendu = VerrouWorkspace.prendre(workspace()).orElseThrow();
        rendu.close();

        assertThat(rendu.detenu())
                .as("un verrou qui se dit encore détenu après avoir été rendu ferait croire une"
                        + " opération exclusive protégée alors qu'elle ne l'est plus")
                .isFalse();
        assertThat(VerrouWorkspace.prendre(workspace()))
                .as("fermer l'application doit rendre le workspace, sinon le verrou devient une prison")
                .isPresent();
    }

    @Test
    @DisplayName("le fichier de verrou dit qui l'occupe, pour que le refus soit lisible")
    void le_fichier_dit_qui_occupe() throws Exception {
        try (VerrouWorkspace verrou = VerrouWorkspace.prendre(workspace()).orElseThrow()) {
            assertThat(verrou.detenu()).isTrue();
            String contenu = Files.readString(racine.resolve("ws").resolve(".verrou"));

            assertThat(contenu)
                    .as("« le workspace est utilisé » sans dire par qui n'aide personne à s'en sortir")
                    .contains(String.valueOf(ProcessHandle.current().pid()));
        }
    }

    @Test
    @DisplayName("l'occupant est nommé au second preneur, qui n'a que le fichier pour le savoir")
    void occupant_lisible_par_le_second() {
        try (VerrouWorkspace premier = VerrouWorkspace.prendre(workspace()).orElseThrow()) {
            assertThat(premier.detenu()).isTrue();

            assertThat(VerrouWorkspace.occupant(workspace()))
                    .as("c'est ce que l'IHM et la CLI afficheront à la place d'un échec SQLite tardif")
                    .contains(String.valueOf(ProcessHandle.current().pid()));
        }
    }

    @Test
    @DisplayName("le processus qui détient déjà le verrou ne se bloque pas lui-même")
    void detenteur_ne_se_bloque_pas() {
        try (VerrouWorkspace verrou = VerrouWorkspace.prendre(workspace()).orElseThrow()) {
            assertThat(verrou.detenu()).isTrue();

            // L'IHM tient le verrou pour toute sa durée. Si une restauration lancée depuis cette même
            // IHM se heurtait au verrou de l'IHM, plus aucune opération exclusive ne serait possible.
            assertThatCode(() -> VerrouWorkspace.pourOperationExclusive(workspace(), "la restauration")
                            .close())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("une opération exclusive sur un workspace libre le verrouille, puis le rend")
    void operation_exclusive_prend_et_rend() {
        try (VerrouWorkspace pendant = VerrouWorkspace.pourOperationExclusive(workspace(), "la migration")) {
            assertThat(VerrouWorkspace.prendre(workspace()))
                    .as("pendant l'opération, personne d'autre n'entre")
                    .isEmpty();
        }

        assertThat(VerrouWorkspace.prendre(workspace()))
                .as("et l'opération finie, le workspace est rendu : un verrou d'opération ne survit pas"
                        + " à son opération")
                .isPresent();
    }

    private Workspace workspace() {
        return new Workspace(racine.resolve("ws"));
    }
}
