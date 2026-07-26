package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.analyse.model.PointActivite;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/// Vérifie le **texte de survol** d'un point de la courbe d'activité
/// ([ActiviteController#texteInfobulle]) : espèce, heure exacte de la tranche, nombre de contacts avec
/// l'accord singulier/pluriel.
class ActiviteControllerTest {

    @Test
    void l_infobulle_donne_espece_heure_et_nombre_pluriel() {
        String texte = ActiviteController.texteInfobulle(
                "Pipistrelle de Kuhl", new PointActivite(LocalDateTime.of(2026, 6, 21, 22, 30), 14));

        assertThat(texte).isEqualTo("Pipistrelle de Kuhl · 22:30 · 14 contacts");
    }

    @Test
    void l_infobulle_accorde_le_singulier() {
        String texte = ActiviteController.texteInfobulle(
                "Barbastelle d'Europe", new PointActivite(LocalDateTime.of(2026, 6, 21, 23, 0), 1));

        assertThat(texte).isEqualTo("Barbastelle d'Europe · 23:00 · 1 contact");
    }
}
