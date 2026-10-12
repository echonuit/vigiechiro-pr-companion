package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.recette.SansEcritureHorsDuFil;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Tests du câblage de la [BarreStatut] (#495) : les trois libellés suivent les zones du
/// [NavigationViewModel] et le conteneur se masque tant qu'aucune zone n'a de contenu.
/// [ApplicationExtension] initialise le toolkit JavaFX (construction de nœuds) ; aucune scène affichée.
@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})
class BarreStatutTest {

    @Test
    @DisplayName("Les libellés suivent les zones ; la barre se masque quand tout est vide")
    void libelles_suivent_les_zones_et_barre_masquee_si_vide() {
        NavigationViewModel navigation = new NavigationViewModel();
        BorderPane conteneur = new BorderPane();
        Label gauche = new Label();
        Label centre = new Label();
        Label droite = new Label();
        BarreStatut.lier(conteneur, gauche, centre, droite, navigation);

        // Défaut (toutes zones vides) → barre masquée et retirée du layout.
        assertThat(conteneur.isVisible()).isFalse();
        assertThat(conteneur.isManaged()).isFalse();
        assertThat(centre.getText()).isEmpty();

        // Un résumé zoné (centre + droite) → libellés alimentés, barre affichée.
        navigation.setZonesStatut(ZonesStatut.centreEtDroite("60 observation(s)", "12 / 60 revues"));
        assertThat(centre.getText()).isEqualTo("60 observation(s)");
        assertThat(droite.getText()).isEqualTo("12 / 60 revues");
        assertThat(gauche.getText()).isEmpty();
        assertThat(conteneur.isVisible()).isTrue();
        assertThat(conteneur.isManaged()).isTrue();

        // Retour à des zones vides → la barre se masque de nouveau.
        navigation.setZonesStatut(ZonesStatut.VIDE);
        assertThat(conteneur.isVisible()).isFalse();
        assertThat(conteneur.isManaged()).isFalse();
    }
}
