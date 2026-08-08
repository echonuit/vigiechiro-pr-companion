package fr.univ_amu.iut.saison.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import fr.univ_amu.iut.commun.view.Prisme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La carte d'accueil « Ma saison » ([ActiviteMaSaison]).
///
/// Elle n'était citée dans **aucun** fichier de test (#3521) : huit mutants sans couverture, dont
/// celui qui retire l'appel à la navigation - la carte reste alors à l'écran et ne fait rien.
class ActiviteMaSaisonTest {

    @Test
    @DisplayName("#3521 : ouvrir la carte ouvre l'écran de la feature")
    void ouvrir_delegue_a_la_navigation() {
        NavigationSaison navigation = mock(NavigationSaison.class);

        new ActiviteMaSaison(navigation).ouvrir();

        verify(navigation).ouvrir();
    }

    @Test
    @DisplayName("#3521 : la carte annonce le prisme et le rang qui la placent sur l'accueil")
    void annonce_sa_place_sur_l_accueil() {
        ActiviteMaSaison carte = new ActiviteMaSaison(mock(NavigationSaison.class));

        assertThat(carte.prisme()).isEqualTo(Prisme.COLLECTE_PASSAGES);
        assertThat(carte.ordre()).isEqualTo(30);
        assertThat(carte.titre()).isEqualTo("Ma saison");
        assertThat(carte.pageDoc()).isEqualTo("saison");
    }
}
