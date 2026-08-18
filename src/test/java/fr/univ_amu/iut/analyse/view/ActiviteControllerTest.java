package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.analyse.model.PointActivite;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/// Vérifie les helpers purs du controller de la courbe d'activité : le **texte de survol** d'un point
/// ([CourbesActivite#texteInfobulle]) et la conversion d'une **heure de la fenêtre nocturne** en
/// position sur l'axe ([ActiviteController#minutesSurAxe], base de l'aplat coucher/lever).
class ActiviteControllerTest {

    @Test
    void l_infobulle_donne_espece_heure_et_nombre_pluriel() {
        String texte = CourbesActivite.texteInfobulle(
                "Pipistrelle de Kuhl", new PointActivite(LocalDateTime.of(2026, 6, 21, 22, 30), 14));

        assertThat(texte).isEqualTo("Pipistrelle de Kuhl · 22:30 · 14 contacts");
    }

    @Test
    void l_infobulle_accorde_le_singulier() {
        String texte = CourbesActivite.texteInfobulle(
                "Barbastelle d'Europe", new PointActivite(LocalDateTime.of(2026, 6, 21, 23, 0), 1));

        assertThat(texte).isEqualTo("Barbastelle d'Europe · 23:00 · 1 contact");
    }

    @Test
    void l_heure_du_soir_se_place_apres_l_origine_de_l_axe() {
        // 18 h est l'origine (0), 21 h est à 3 h de là.
        assertThat(ActiviteController.minutesSurAxe(18)).isEqualTo(0.0);
        assertThat(ActiviteController.minutesSurAxe(21)).isEqualTo(180.0);
    }

    @Test
    void l_heure_du_matin_se_place_apres_minuit_sur_l_axe() {
        // Minuit est à 6 h de 18 h (360), 6 h du matin à 12 h (720).
        assertThat(ActiviteController.minutesSurAxe(0)).isEqualTo(360.0);
        assertThat(ActiviteController.minutesSurAxe(6)).isEqualTo(720.0);
    }

    @Test
    void une_heure_hors_fenetre_est_bornee_a_l_axe() {
        // Lever tardif (9 h → 900) ramené au bord droit de la fenêtre (840 = 8 h).
        assertThat(ActiviteController.minutesSurAxe(9)).isEqualTo(840.0);
    }
}
