package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import fr.univ_amu.iut.commun.view.Prisme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La carte d'accueil « Espèces & observations » ([ActiviteAnalyse]).
///
/// Elle n'était citée dans **aucun** fichier de test (#3521), et la mesure de mutation le disait :
/// huit mutants sans couverture. Le seul qui porte une conséquence est celui qui retire l'appel à la
/// navigation - la carte reste alors à l'écran, jolie et **inerte**. Rien ne rougissait.
class ActiviteAnalyseTest {

    @Test
    @DisplayName("#3521 : ouvrir la carte ouvre l'écran de la feature")
    void ouvrir_delegue_a_la_navigation() {
        NavigationAnalyse navigation = mock(NavigationAnalyse.class);

        new ActiviteAnalyse(navigation).ouvrir();

        verify(navigation).ouvrir();
    }

    @Test
    @DisplayName("#3521 : la carte annonce le prisme et le rang qui la placent sur l'accueil")
    void annonce_sa_place_sur_l_accueil() {
        // Rang 10 : carte de tête de son prisme. Le contrat collectif (rangs distincts dans un prisme)
        // est tenu par `ContratCartesAccueilTest` ; ici on fige la place de celle-ci.
        ActiviteAnalyse carte = new ActiviteAnalyse(mock(NavigationAnalyse.class));

        assertThat(carte.prisme()).isEqualTo(Prisme.ESPECES_BIODIVERSITE);
        assertThat(carte.ordre()).isEqualTo(10);
        assertThat(carte.titre()).isEqualTo("Espèces & observations");
        assertThat(carte.pageDoc()).isEqualTo("analyse");
    }
}
