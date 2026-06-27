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

    @Test
    @DisplayName("la densité fonce le remplissage : plus de passages → plus opaque, borné au carré le plus actif")
    void couleur_densite_croit_avec_les_passages() {
        // Un carré peu actif est plus transparent qu'un carré actif (même max de référence).
        assertThat(ConstructeurDonneesCarte.couleurDensite(1, 10).getOpacity())
                .as("peu de passages → faible opacité")
                .isLessThan(ConstructeurDonneesCarte.couleurDensite(8, 10).getOpacity());
        // Le carré le plus actif atteint l'opacité maximale ; aucun passage nulle part → opacité minimale.
        assertThat(ConstructeurDonneesCarte.couleurDensite(10, 10).getOpacity())
                .isGreaterThan(ConstructeurDonneesCarte.couleurDensite(0, 0).getOpacity());
    }

    @Test
    @DisplayName("entre deux carrés, le plus fréquenté est tracé plus foncé (densité relative)")
    void emprise_du_carre_le_plus_actif_est_plus_opaque() {
        CarreAgrege calme = new CarreAgrege(
                "640380", "Calme", List.of(new PointAgrege("A1", 43.30, -0.36, 1, StatutWorkflow.IMPORTE)), 1);
        CarreAgrege actif = new CarreAgrege(
                "640381", "Actif", List.of(new PointAgrege("B1", 43.50, -0.20, 9, StatutWorkflow.DEPOSE)), 9);

        DonneesCarte donnees = ConstructeurDonneesCarte.depuis(List.of(calme, actif));

        double opaciteCalme = opaciteDuCarre(donnees, "640380");
        double opaciteActif = opaciteDuCarre(donnees, "640381");
        assertThat(opaciteActif)
                .as("le carré le plus fréquenté est tracé plus foncé")
                .isGreaterThan(opaciteCalme);
    }

    @Test
    @DisplayName("un carré très actif mais sans GPS (invisible) ne pâlit pas les carrés affichés")
    void carre_invisible_n_influence_pas_la_densite() {
        // 80 passages mais aucun point géolocalisé → jamais tracé, donc hors normalisation de densité.
        CarreAgrege fantome =
                new CarreAgrege("130001", "Fantôme", List.of(new PointAgrege("X", null, null, 80, null)), 80);
        CarreAgrege visible = new CarreAgrege(
                "640380", "Visible", List.of(new PointAgrege("A1", 43.30, -0.36, 9, StatutWorkflow.DEPOSE)), 9);

        DonneesCarte donnees = ConstructeurDonneesCarte.depuis(List.of(fantome, visible));

        // Le carré visible est le plus actif PARMI les traçables → opacité maximale (et non pâlie par les 80).
        assertThat(opaciteDuCarre(donnees, "640380"))
                .as("normalisation sur les carrés traçables, pas sur le fantôme invisible")
                .isEqualTo(ConstructeurDonneesCarte.couleurDensite(9, 9).getOpacity());
    }

    private static double opaciteDuCarre(DonneesCarte donnees, String numeroCarre) {
        return donnees.carres().stream()
                .filter(c -> c.numeroCarre().equals(numeroCarre))
                .map(c -> c.remplissage().getOpacity())
                .findFirst()
                .orElseThrow();
    }
}
