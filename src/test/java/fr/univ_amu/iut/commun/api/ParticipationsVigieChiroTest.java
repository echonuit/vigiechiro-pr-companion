package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Lecture de `GET /moi/participations` ([ParticipationsVigieChiro]) : deux projections d'un même corps —
/// les **sites** rattachés et les **participations** elles-mêmes. Fonctions pures, tolérantes, sans réseau.
class ParticipationsVigieChiroTest {

    @Test
    @DisplayName("sites : site verrouillé, carré extrait du titre, points [lat,lon] depuis les localités")
    void sites_depuis_participations() {
        // Forme réelle (extrait de GET /moi/participations : site embarqué + localités).
        String corps = "{\"_items\":[{\"_id\":\"p1\",\"site\":{"
                + "\"_id\":\"5eb12120cbe7410011f0a97f\",\"titre\":\"Vigiechiro - Point Fixe-130711\","
                + "\"localites\":["
                + "{\"nom\":\"Z1\",\"geometries\":{\"type\":\"GeometryCollection\",\"geometries\":"
                + "[{\"type\":\"Point\",\"coordinates\":[43.5221,5.4658]}]}},"
                + "{\"nom\":\"Z41\",\"geometries\":{\"type\":\"GeometryCollection\",\"geometries\":"
                + "[{\"type\":\"Point\",\"coordinates\":[43.5145,5.4513]}]}}]}}]}";

        List<SiteVigieChiro> sites = ParticipationsVigieChiro.sites(corps);

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
    @DisplayName("sites : dédup par site, participation sans site ignorée, illisible → vide")
    void sites_depuis_participations_tolerant() {
        String memeSiteDeuxFois = "{\"_items\":["
                + "{\"_id\":\"p1\",\"site\":{\"_id\":\"s1\",\"titre\":\"A-100001\"}},"
                + "{\"_id\":\"p2\",\"site\":{\"_id\":\"s1\",\"titre\":\"A-100001\"}},"
                + "{\"_id\":\"p3\"}]}"; // p3 sans site -> ignorée

        assertThat(ParticipationsVigieChiro.sites(memeSiteDeuxFois))
                .extracting(SiteVigieChiro::id)
                .containsExactly("s1");
        assertThat(ParticipationsVigieChiro.sites("nope")).isEmpty();
    }

    @Test
    @DisplayName("participations : id + point + date + titre du site ; sans _id ignorée, illisible → vide")
    void participations_liste() {
        String corps = "{\"_items\":["
                + "{\"_id\":\"6a49\",\"point\":\"Z41\",\"date_debut\":\"2026-07-03T19:00:00+00:00\","
                + "\"site\":{\"_id\":\"s1\",\"titre\":\"Vigiechiro - Point Fixe-130711\"}},"
                + "{\"point\":\"X\"}]}"; // sans _id -> ignorée

        assertThat(ParticipationsVigieChiro.participations(corps))
                .containsExactly(new ParticipationVigieChiro(
                        "6a49", "Z41", "2026-07-03T19:00:00+00:00", "Vigiechiro - Point Fixe-130711"));
        assertThat(ParticipationsVigieChiro.participations("nope")).isEmpty();
    }
}
