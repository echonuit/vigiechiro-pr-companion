package fr.univ_amu.iut.multisite.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.view.carte.DonneesCarte;
import fr.univ_amu.iut.multisite.model.CarreAgrege;
import fr.univ_amu.iut.multisite.model.PointAgrege;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests **purs** (sans IHM) du [ConstructeurDonneesCarte] (#152) : traduction des agrégats domaine en
/// [DonneesCarte] — points géolocalisés colorés par statut, emprises des carrés, et gestion des points
/// sans GPS.
class ConstructeurDonneesCarteTest {

    @Test
    @DisplayName("traduit les points géolocalisés (libellé + couleur par statut) et trace l'emprise")
    void traduit_points_geolocalises_et_emprise() {
        CarreAgrege carre = new CarreAgrege(
                "640380",
                "Étang",
                List.of(
                        new PointAgrege("A1", 43.30, -0.36, 2, StatutWorkflow.VERIFIE),
                        new PointAgrege("B2", null, null, 1, StatutWorkflow.IMPORTE)), // sans GPS
                3);

        DonneesCarte donnees = ConstructeurDonneesCarte.depuis(List.of(carre));

        // Seul A1 (géolocalisé) donne un marqueur ; le carré est tracé (emprise autour de A1).
        assertThat(donnees.points()).hasSize(1);
        assertThat(donnees.points().get(0).libelle()).isEqualTo("640380 / A1");
        assertThat(donnees.points().get(0).couleur())
                .isEqualTo(ConstructeurDonneesCarte.couleurStatut(StatutWorkflow.VERIFIE));
        assertThat(donnees.carres()).extracting(c -> c.numeroCarre()).containsExactly("640380");
    }

    @Test
    @DisplayName("un carré sans aucun point géolocalisé n'est ni marqué ni tracé")
    void carre_sans_point_geolocalise_n_est_pas_trace() {
        CarreAgrege carre = new CarreAgrege("640381", null, List.of(new PointAgrege("A1", null, null, 0, null)), 0);

        DonneesCarte donnees = ConstructeurDonneesCarte.depuis(List.of(carre));

        assertThat(donnees.points()).isEmpty();
        assertThat(donnees.carres())
                .as("aucun point géolocalisé → pas d'emprise")
                .isEmpty();
    }

    @Test
    @DisplayName("la couleur de statut est distincte par état et jamais nulle")
    void couleur_par_statut_distincte() {
        assertThat(ConstructeurDonneesCarte.couleurStatut(StatutWorkflow.DEPOSE))
                .isNotEqualTo(ConstructeurDonneesCarte.couleurStatut(StatutWorkflow.IMPORTE));
        assertThat(ConstructeurDonneesCarte.couleurStatut(null))
                .as("aucun passage → couleur neutre, pas null")
                .isNotNull();
    }
}
