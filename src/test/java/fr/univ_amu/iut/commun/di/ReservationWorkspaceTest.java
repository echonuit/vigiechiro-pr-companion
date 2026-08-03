package fr.univ_amu.iut.commun.di;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.persistence.DataAccessException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La **réservation du dossier de travail** par l'application graphique (#2731, lot 1 du chantier de
/// dette #2720).
///
/// Deux instances sur le même dossier écrivaient chacune de leur côté, et la seconde découvrait le
/// problème au mieux par un échec SQLite tardif. L'application le **refuse** désormais au démarrage,
/// plutôt que de basculer en lecture seule : ce mode n'existe nulle part dans le produit, il faudrait
/// gater chaque écriture de chaque fonctionnalité (ADR 2731).
class ReservationWorkspaceTest {

    private static final String PROPRIETE = "vigiechiro.workspace";

    @TempDir
    Path racine;

    @AfterEach
    void rendreLeWorkspace() {
        Amorcage.libererLeWorkspace();
        System.clearProperty(PROPRIETE);
    }

    @Test
    @DisplayName("la première instance réserve le dossier de travail")
    void premiere_instance_reserve() {
        System.setProperty(PROPRIETE, racine.resolve("ws").toString());

        assertThatCode(Amorcage::reserverLeWorkspace).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la seconde instance est refusée, et le message dit qui occupe et quoi faire")
    void seconde_instance_refusee() {
        System.setProperty(PROPRIETE, racine.resolve("ws").toString());
        Amorcage.reserverLeWorkspace();

        assertThatThrownBy(Amorcage::reserverLeWorkspace)
                .isInstanceOf(DataAccessException.class)
                .as("nommer l'occupant, sinon « déjà utilisé » n'aide personne à s'en sortir")
                .hasMessageContaining("processus")
                .as("et dire quoi faire : une application qui refuse sans proposer de suite est un mur")
                .hasMessageContaining("fenêtre");
    }

    @Test
    @DisplayName("le dossier rendu est de nouveau réservable : fermer l'application libère la place")
    void dossier_rendu_est_reservable() {
        System.setProperty(PROPRIETE, racine.resolve("ws").toString());
        Amorcage.reserverLeWorkspace();

        Amorcage.libererLeWorkspace();

        assertThatCode(Amorcage::reserverLeWorkspace)
                .as("sinon un verrou mal rendu transforme un incident en blocage définitif")
                .doesNotThrowAnyException();
        assertThat(racine.resolve("ws").resolve(".verrou")).exists();
    }
}
