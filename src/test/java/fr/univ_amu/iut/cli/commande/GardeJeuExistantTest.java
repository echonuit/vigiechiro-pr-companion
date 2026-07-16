package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Garde « un seul jeu par passage » partagée par les deux commandes d'import de la CLI
/// (`importer-tadarida`, `importer-vigiechiro`) : le refus (code 2, « relancez avec --remplacer ») et son
/// message y sont décidés une seule fois.
class GardeJeuExistantTest {

    private final ResultatsIdentificationDao resultatsDao = mock(ResultatsIdentificationDao.class);

    private static ResultatsIdentification jeu() {
        return new ResultatsIdentification(9L, "observations.csv", "\"Brut\"", "2026-07-07T10:00:00", 42L);
    }

    @Test
    @DisplayName("Passage sans jeu : l'import est autorisé (aucune exception)")
    void sans_jeu_autorise() {
        when(resultatsDao.findByPassage(42L)).thenReturn(Optional.empty());

        assertThatCode(() -> GardeJeuExistant.refuserSiDejaImporte(resultatsDao, 42L, false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Jeu existant sans --remplacer : erreur d'usage qui invite à relancer avec --remplacer")
    void jeu_existant_sans_remplacer_refuse() {
        when(resultatsDao.findByPassage(42L)).thenReturn(Optional.of(jeu()));

        assertThatThrownBy(() -> GardeJeuExistant.refuserSiDejaImporte(resultatsDao, 42L, false))
                .isInstanceOf(ErreurUsage.class)
                .hasMessageContaining("déjà des résultats")
                .hasMessageContaining("--remplacer");
    }

    @Test
    @DisplayName("Jeu existant AVEC --remplacer : autorisé, le remplacement est explicite")
    void jeu_existant_avec_remplacer_autorise() {
        assertThatCode(() -> GardeJeuExistant.refuserSiDejaImporte(resultatsDao, 42L, true))
                .doesNotThrowAnyException();
    }
}
