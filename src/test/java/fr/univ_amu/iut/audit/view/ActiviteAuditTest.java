package fr.univ_amu.iut.audit.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import fr.univ_amu.iut.commun.view.Prisme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La carte d'accueil « Audit de cohérence » ([ActiviteAudit]), jumelle d'[ActiviteAnalyse] : PIT signalait
/// ses accesseurs (`description`, `titre`, `ordre`) sans aucune couverture.
class ActiviteAuditTest {

    @Test
    @DisplayName("ouvrir la carte ouvre l'écran de la feature")
    void ouvrir_delegue_a_la_navigation() {
        NavigationAudit navigation = mock(NavigationAudit.class);

        new ActiviteAudit(navigation).ouvrir();

        verify(navigation).ouvrir();
    }

    @Test
    @DisplayName("la carte annonce le prisme, le rang et le texte qui la placent sur l'accueil")
    void annonce_sa_place_sur_l_accueil() {
        ActiviteAudit carte = new ActiviteAudit(mock(NavigationAudit.class));

        assertThat(carte.prisme()).isEqualTo(Prisme.COLLECTE_PASSAGES);
        assertThat(carte.ordre()).isEqualTo(90);
        assertThat(carte.titre()).isEqualTo("Audit de cohérence");
        assertThat(carte.description())
                .isEqualTo(
                        "Vérifie que fichiers, base et dépôts restent en correspondance : écarts disque / base repérés.");
        assertThat(carte.pageDoc()).isEqualTo("audit");
    }
}
