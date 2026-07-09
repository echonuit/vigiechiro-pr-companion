package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Lecture des réponses JSON VigieChiro ([ReponsesVigieChiro]) : fonctions pures `String` → record,
/// **tolérantes** (JSON illisible / incomplet → vide, jamais d'exception). Aucun réseau. Le transport
/// HTTP est testé à part dans `ClientVigieChiroTest`.
class ReponsesVigieChiroTest {

    @Test
    @DisplayName("profil lit _id / pseudo / role d'un profil complet")
    void profil_complet() {
        String corps = "{\"_id\":\"698ddf3d\",\"pseudo\":\"Sébastien\",\"role\":\"Observateur\","
                + "\"donnees_publiques\":true}";

        assertThat(ReponsesVigieChiro.profil(corps))
                .contains(new ProfilVigieChiro("698ddf3d", "Sébastien", "Observateur"));
    }

    @Test
    @DisplayName("profil tolère les champs absents ou null (hors _id)")
    void profil_champs_absents() {
        Optional<ProfilVigieChiro> profil = ReponsesVigieChiro.profil("{\"_id\":\"x\",\"pseudo\":null}");

        assertThat(profil).isPresent();
        assertThat(profil.orElseThrow().id()).isEqualTo("x");
        assertThat(profil.orElseThrow().pseudo()).isNull();
        assertThat(profil.orElseThrow().role()).isNull();
    }

    @Test
    @DisplayName("profil : sans _id → vide ; JSON illisible → vide (jamais d'exception)")
    void profil_invalide_est_vide() {
        assertThat(ReponsesVigieChiro.profil("{\"pseudo\":\"x\"}")).isEmpty();
        assertThat(ReponsesVigieChiro.profil("pas du json")).isEmpty();
        assertThat(ReponsesVigieChiro.profil("[]")).isEmpty();
    }

    @Test
    @DisplayName("taxons lit _id / libelle_court / libelle_long depuis la clé _items")
    void taxons_liste() {
        String corps = "{\"_items\":["
                + "{\"_id\":\"5a1\",\"libelle_court\":\"Pippip\",\"libelle_long\":\"Pipistrellus pipistrellus\"},"
                + "{\"_id\":\"5a2\",\"libelle_court\":\"Barbar\",\"libelle_long\":\"Barbastella barbastellus\"}]}";

        assertThat(ReponsesVigieChiro.taxons(corps))
                .containsExactly(
                        new TaxonVigieChiro("5a1", "Pippip", "Pipistrellus pipistrellus"),
                        new TaxonVigieChiro("5a2", "Barbar", "Barbastella barbastellus"));
    }

    @Test
    @DisplayName("taxons : élément sans _id ou sans libelle_court ignoré, libelle_long absent → null")
    void taxons_tolerant() {
        String corps = "{\"_items\":["
                + "{\"_id\":\"5a1\",\"libelle_court\":\"Pippip\"},"
                + "{\"libelle_court\":\"SansId\"},"
                + "{\"_id\":\"5a3\"}]}";

        assertThat(ReponsesVigieChiro.taxons(corps)).containsExactly(new TaxonVigieChiro("5a1", "Pippip", null));
    }

    @Test
    @DisplayName("taxons : corps illisible ou forme inattendue → liste vide (jamais d'exception)")
    void taxons_illisible() {
        assertThat(ReponsesVigieChiro.taxons("pas du json")).isEmpty();
        assertThat(ReponsesVigieChiro.taxons("{\"autre\":1}")).isEmpty();
    }

    @Test
    @DisplayName("sitesDepuisParticipations : site verrouillé, carré extrait du titre, points [lat,lon]")
    void sites_depuis_participations() {
        // Forme réelle (extrait de GET /moi/participations : site embarqué + localités).
        String corps = "{\"_items\":[{\"_id\":\"p1\",\"site\":{"
                + "\"_id\":\"5eb12120cbe7410011f0a97f\",\"titre\":\"Vigiechiro - Point Fixe-130711\","
                + "\"localites\":["
                + "{\"nom\":\"Z1\",\"geometries\":{\"type\":\"GeometryCollection\",\"geometries\":"
                + "[{\"type\":\"Point\",\"coordinates\":[43.5221,5.4658]}]}},"
                + "{\"nom\":\"Z41\",\"geometries\":{\"type\":\"GeometryCollection\",\"geometries\":"
                + "[{\"type\":\"Point\",\"coordinates\":[43.5145,5.4513]}]}}]}}]}";

        List<SiteVigieChiro> sites = ReponsesVigieChiro.sitesDepuisParticipations(corps);

        assertThat(sites).hasSize(1);
        SiteVigieChiro site = sites.getFirst();
        assertThat(site.id()).isEqualTo("5eb12120cbe7410011f0a97f");
        assertThat(site.titre()).isEqualTo("Vigiechiro - Point Fixe-130711");
        assertThat(site.verrouille())
                .as("une participation existe -> site verrouillé")
                .isTrue();
        assertThat(site.numeroCarre()).isEqualTo("130711");
        // Ordre [lat, lon] : coordinates[0] = latitude (43.5 = Aix), [1] = longitude.
        assertThat(site.points())
                .containsExactly(
                        new PointVigieChiro("Z1", 43.5221, 5.4658), new PointVigieChiro("Z41", 43.5145, 5.4513));
    }

    @Test
    @DisplayName("sitesDepuisParticipations : dédup par site, participation sans site ignorée, illisible → vide")
    void sites_depuis_participations_tolerant() {
        String memeSiteDeuxFois = "{\"_items\":["
                + "{\"_id\":\"p1\",\"site\":{\"_id\":\"s1\",\"titre\":\"A-100001\"}},"
                + "{\"_id\":\"p2\",\"site\":{\"_id\":\"s1\",\"titre\":\"A-100001\"}},"
                + "{\"_id\":\"p3\"}]}"; // p3 sans site -> ignorée

        assertThat(ReponsesVigieChiro.sitesDepuisParticipations(memeSiteDeuxFois))
                .extracting(SiteVigieChiro::id)
                .containsExactly("s1");
        assertThat(ReponsesVigieChiro.sitesDepuisParticipations("nope")).isEmpty();
    }
}
