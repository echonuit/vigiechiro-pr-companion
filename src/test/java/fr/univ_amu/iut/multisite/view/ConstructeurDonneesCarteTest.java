package fr.univ_amu.iut.multisite.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.view.carte.DonneesCarte;
import fr.univ_amu.iut.commun.view.carte.EmpriseCarre;
import fr.univ_amu.iut.commun.view.carte.FournisseurEmpriseCarre;
import fr.univ_amu.iut.commun.view.carte.PointGeo;
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
                        new PointAgrege("A1", 43.4010, -1.5740, 2, StatutWorkflow.VERIFIE),
                        new PointAgrege("B2", null, null, 1, StatutWorkflow.IMPORTE)), // sans GPS
                3);

        DonneesCarte donnees = ConstructeurDonneesCarte.depuis(List.of(carre));

        // A1 (géolocalisé) → marqueur réel ; B2 (sans GPS) → marqueur approché au centre du carré (#153).
        assertThat(donnees.points()).hasSize(2);
        PointGeo reel = donnees.points().stream()
                .filter(p -> !p.approximatif())
                .findFirst()
                .orElseThrow();
        PointGeo approche = donnees.points().stream()
                .filter(PointGeo::approximatif)
                .findFirst()
                .orElseThrow();
        assertThat(reel.libelle()).isEqualTo("640380 / A1");
        assertThat(reel.couleur()).isEqualTo(ConstructeurDonneesCarte.couleurStatut(StatutWorkflow.VERIFIE));
        assertThat(approche.libelle()).isEqualTo("640380 / B2");
        assertThat(donnees.carres()).extracting(c -> c.numeroCarre()).containsExactly("640380");
    }

    @Test
    @DisplayName("un carré hors référentiel et sans point géolocalisé n'est ni marqué ni tracé")
    void carre_sans_point_geolocalise_n_est_pas_trace() {
        // Carré ABSENT du carroyage officiel (999999) : on retombe sur le repli, qui sans point géolocalisé
        // ne peut rien ancrer. (Un carré DU référentiel, lui, est tracé même sans point — cf. carroyage #325.)
        CarreAgrege carre = new CarreAgrege("999999", null, List.of(new PointAgrege("A1", null, null, 0, null)), 0);

        DonneesCarte donnees = ConstructeurDonneesCarte.depuis(List.of(carre));

        assertThat(donnees.points()).isEmpty();
        assertThat(donnees.carres())
                .as("hors référentiel et aucun point géolocalisé → pas d'emprise")
                .isEmpty();
    }

    @Test
    @DisplayName("#153 : un point sans GPS d'un carré officiel est placé (approché) au centre du carré")
    void point_sans_gps_place_au_centre_du_carre() {
        // 640380 est dans le référentiel carrenat : son emprise (et donc son centre) vient du centroïde
        // national, indépendamment des points. Le point sans GPS y est posé au centre, marqué approximatif.
        CarreAgrege carre = new CarreAgrege("640380", "Étang", List.of(new PointAgrege("A1", null, null, 1, null)), 1);

        DonneesCarte donnees = ConstructeurDonneesCarte.depuis(List.of(carre));

        EmpriseCarre emprise =
                FournisseurEmpriseCarre.parDefaut().emprise("640380", List.of()).orElseThrow();
        assertThat(donnees.points()).hasSize(1);
        PointGeo approche = donnees.points().get(0);
        assertThat(approche.approximatif())
                .as("position approchée, pas un GPS réel")
                .isTrue();
        assertThat(approche.latitude()).isEqualTo(emprise.latCentre());
        assertThat(approche.longitude()).isEqualTo(emprise.lonCentre());
        assertThat(approche.infobulle()).contains("Position approximative");
        assertThat(donnees.carres())
                .as("le carré reste calé sur le carroyage officiel (#325)")
                .extracting(c -> c.numeroCarre())
                .containsExactly("640380");
    }

    @Test
    @DisplayName("#153 : plusieurs points sans GPS d'un carré sont désempilés (positions distinctes, dans l'emprise)")
    void plusieurs_points_sans_gps_desempiles() {
        CarreAgrege carre = new CarreAgrege(
                "640380",
                "Étang",
                List.of(
                        new PointAgrege("A1", null, null, 1, null),
                        new PointAgrege("B2", null, null, 1, null),
                        new PointAgrege("C3", null, null, 1, null)),
                3);

        DonneesCarte donnees = ConstructeurDonneesCarte.depuis(List.of(carre));

        EmpriseCarre emprise =
                FournisseurEmpriseCarre.parDefaut().emprise("640380", List.of()).orElseThrow();
        assertThat(donnees.points()).hasSize(3).allMatch(PointGeo::approximatif);
        assertThat(donnees.points().stream()
                        .map(p -> p.latitude() + "," + p.longitude())
                        .distinct()
                        .count())
                .as("trois positions distinctes (désempilées en éventail)")
                .isEqualTo(3);
        assertThat(donnees.points())
                .as("toutes à l'intérieur de la maille")
                .allSatisfy(p -> assertThat(emprise.contient(p.latitude(), p.longitude()))
                        .isTrue());
    }

    @Test
    @DisplayName("#153 : hors référentiel mais ancré par un point GPS, un point sans GPS est placé au centre du repli")
    void point_sans_gps_place_au_centre_du_repli() {
        // 999999 absent du carroyage : l'emprise vient du repli autour du seul point géolocalisé (A1).
        // Le point sans GPS (B2) est tout de même situé, au centre de ce repli, marqué approximatif.
        CarreAgrege carre = new CarreAgrege(
                "999999",
                null,
                List.of(
                        new PointAgrege("A1", 43.4010, -1.5740, 1, StatutWorkflow.VERIFIE),
                        new PointAgrege("B2", null, null, 1, null)),
                2);

        DonneesCarte donnees = ConstructeurDonneesCarte.depuis(List.of(carre));

        assertThat(donnees.points()).hasSize(2);
        assertThat(donnees.points()).filteredOn(PointGeo::approximatif).hasSize(1);
        assertThat(donnees.points()).filteredOn(p -> !p.approximatif()).hasSize(1);
    }

    @Test
    @DisplayName("#327 : un carré officiel SANS GPS, mais tracé, compte dans l'échelle de densité")
    void carre_officiel_sans_gps_participe_a_la_densite() {
        // 640380 : 80 passages, AUCUN point GPS → tracé via le carroyage officiel (donc visible).
        CarreAgrege sansGps =
                new CarreAgrege("640380", "Actif", List.of(new PointAgrege("A1", null, null, 80, null)), 80);
        // 640381 : 8 passages, géolocalisé.
        CarreAgrege geo = new CarreAgrege(
                "640381", "Calme", List.of(new PointAgrege("B1", 43.4040, -1.5470, 8, StatutWorkflow.DEPOSE)), 8);

        DonneesCarte donnees = ConstructeurDonneesCarte.depuis(List.of(sansGps, geo));

        // Le carré sans GPS (le plus actif) doit être le plus foncé, et fixer le maximum de l'échelle.
        assertThat(opaciteDuCarre(donnees, "640380"))
                .as("le carré officiel sans GPS participe à la normalisation (max)")
                .isEqualTo(ConstructeurDonneesCarte.couleurDensite(80, 80).getOpacity())
                .isGreaterThan(opaciteDuCarre(donnees, "640381"));
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
                "640380", "Calme", List.of(new PointAgrege("A1", 43.4010, -1.5740, 1, StatutWorkflow.IMPORTE)), 1);
        CarreAgrege actif = new CarreAgrege(
                "640381", "Actif", List.of(new PointAgrege("B1", 43.4040, -1.5470, 9, StatutWorkflow.DEPOSE)), 9);

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
                "640380", "Visible", List.of(new PointAgrege("A1", 43.4010, -1.5740, 9, StatutWorkflow.DEPOSE)), 9);

        DonneesCarte donnees = ConstructeurDonneesCarte.depuis(List.of(fantome, visible));

        // Le carré visible est le plus actif PARMI les traçables → opacité maximale (et non pâlie par les 80).
        assertThat(opaciteDuCarre(donnees, "640380"))
                .as("normalisation sur les carrés traçables, pas sur le fantôme invisible")
                .isEqualTo(ConstructeurDonneesCarte.couleurDensite(9, 9).getOpacity());
    }

    @Test
    @DisplayName("info-bulle d'un carré : nom + numéro, passages, points GPS vs à localiser, répartition statuts")
    void infobulle_carre_resume_les_stats() {
        CarreAgrege carre = new CarreAgrege(
                "640380",
                "Étang",
                List.of(
                        new PointAgrege("A1", 43.4010, -1.5740, 2, StatutWorkflow.VERIFIE),
                        new PointAgrege("B2", null, null, 1, StatutWorkflow.IMPORTE)), // sans GPS → au centre
                3);

        DonneesCarte donnees = ConstructeurDonneesCarte.depuis(List.of(carre));

        // Le décompte distingue le point géolocalisé du point sans GPS (affiché au centre), au lieu de
        // l'ignorer (#153 : cohérent avec les marqueurs réellement visibles).
        assertThat(donnees.carres().get(0).infobulle())
                .contains("Étang (640380)")
                .contains("3 passages")
                .contains("1 point GPS")
                .contains("1 à localiser")
                .contains(StatutWorkflow.VERIFIE.libelle() + " ×1") // A1 géolocalisé
                .contains(StatutWorkflow.IMPORTE.libelle() + " ×1"); // B2 sans GPS, désormais compté
    }

    @Test
    @DisplayName("#153 : info-bulle cohérente quand TOUS les points d'un carré sont sans GPS (pas « 0 point »)")
    void infobulle_carre_tous_points_sans_gps() {
        CarreAgrege carre = new CarreAgrege(
                "640380",
                "Étang",
                List.of(
                        new PointAgrege("A1", null, null, 1, StatutWorkflow.IMPORTE),
                        new PointAgrege("B2", null, null, 2, StatutWorkflow.IMPORTE)),
                3);

        DonneesCarte donnees = ConstructeurDonneesCarte.depuis(List.of(carre));

        assertThat(donnees.carres().get(0).infobulle())
                .as("ne dit pas « 0 point » alors que 2 marqueurs approximatifs sont visibles")
                .doesNotContain("0 point")
                .contains("2 points à localiser (sans GPS)")
                .contains(StatutWorkflow.IMPORTE.libelle() + " ×2");
    }

    @Test
    @DisplayName("info-bulle d'un point : libellé, total de passages et statut dominant")
    void infobulle_point_resume_les_stats() {
        CarreAgrege carre = new CarreAgrege(
                "640380", "Étang", List.of(new PointAgrege("A1", 43.4010, -1.5740, 2, StatutWorkflow.VERIFIE)), 2);

        DonneesCarte donnees = ConstructeurDonneesCarte.depuis(List.of(carre));

        assertThat(donnees.points().get(0).infobulle())
                .contains("640380 / A1")
                .contains("2 passages")
                .contains(StatutWorkflow.VERIFIE.libelle());
    }

    private static double opaciteDuCarre(DonneesCarte donnees, String numeroCarre) {
        return donnees.carres().stream()
                .filter(c -> c.numeroCarre().equals(numeroCarre))
                .map(c -> c.remplissage().getOpacity())
                .findFirst()
                .orElseThrow();
    }
}
