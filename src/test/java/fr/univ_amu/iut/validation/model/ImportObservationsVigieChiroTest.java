package fr.univ_amu.iut.validation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le port [fr.univ_amu.iut.commun.model.ImportObservations] sait dire si une nuit a déjà ses
/// observations (#5784) : c'est ce qui retient l'écran de lot et la ligne de commande de réimporter.
class ImportObservationsVigieChiroTest {

    private final ImportVigieChiro importateur = mock(ImportVigieChiro.class);
    private final ResultatsIdentificationDao resultats = mock(ResultatsIdentificationDao.class);
    private final ImportObservationsVigieChiro port = new ImportObservationsVigieChiro(importateur, resultats);

    @Test
    @DisplayName("#5784 : une nuit qui a un jeu de résultats a déjà ses observations, lu sans réseau")
    void une_nuit_avec_resultats_a_ses_observations() {
        when(resultats.findByPassage(42L)).thenReturn(Optional.of(mock(ResultatsIdentification.class)));

        assertThat(port.aDejaSesObservations(42L)).isTrue();
        verifyNoInteractions(importateur);
    }

    @Test
    @DisplayName("#5784 : une nuit sans jeu de résultats n'a pas encore ses observations")
    void une_nuit_sans_resultats_n_a_pas_ses_observations() {
        when(resultats.findByPassage(42L)).thenReturn(Optional.empty());

        assertThat(port.aDejaSesObservations(42L)).isFalse();
    }
}
