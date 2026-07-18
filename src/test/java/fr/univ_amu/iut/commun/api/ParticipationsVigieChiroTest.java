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

    @Test
    @DisplayName("detail : _id + _etag + dates + météo + configuration + état traitement (schéma canonique)")
    void detail_participation() {
        String corps = "{\"_id\":\"6a49\",\"_etag\":\"etag123\",\"point\":\"Z41\","
                + "\"date_debut\":\"2026-07-03T19:00:00+00:00\",\"date_fin\":\"2026-07-04T04:00:00+00:00\","
                + "\"meteo\":{\"vent\":\"FAIBLE\",\"couverture\":\"0-25\","
                + "\"temperature_debut\":18,\"temperature_fin\":11},"
                + "\"configuration\":{\"detecteur_enregistreur_type\":\"PassiveRecorder\","
                + "\"micro0_type\":\"ICS\",\"micro0_hauteur\":\"4\"},"
                + "\"traitement\":{\"etat\":\"FINI\"}}";

        ParticipationDetail detail = ParticipationsVigieChiro.detail(corps).orElseThrow();

        assertThat(detail.id()).isEqualTo("6a49");
        assertThat(detail.etag()).isEqualTo("etag123");
        assertThat(detail.point()).isEqualTo("Z41");
        assertThat(detail.dateDebut()).isEqualTo("2026-07-03T19:00:00+00:00");
        assertThat(detail.dateFin()).isEqualTo("2026-07-04T04:00:00+00:00");
        assertThat(detail.meteo().vent()).isEqualTo("FAIBLE");
        assertThat(detail.meteo().couverture()).isEqualTo("0-25");
        // #1844 : les températures sont lues comme le reste. « Récupérer depuis VigieChiro » s'en sert -
        // les oublier ici les ferait disparaître en silence du formulaire.
        assertThat(detail.meteo().temperatureDebut()).isEqualTo(18);
        assertThat(detail.meteo().temperatureFin()).isEqualTo(11);
        assertThat(detail.configuration())
                .containsEntry("detecteur_enregistreur_type", "PassiveRecorder")
                .containsEntry("micro0_type", "ICS")
                .containsEntry("micro0_hauteur", "4");
        assertThat(detail.traitement().etat()).isEqualTo(EtatTraitement.FINI);
        assertThat(detail.traitement().resultatsDisponibles()).isTrue();
    }

    @Test
    @DisplayName("detail : le bloc traitement complet (dates, trace d'erreur, compteur d'essais) est restitué")
    void detail_traitement_complet() {
        // Échec rattrapé par le serveur : RETRY porte la trace et le compteur d'essais.
        String corps = "{\"_id\":\"6a49\",\"traitement\":{\"etat\":\"RETRY\",\"retry\":1,"
                + "\"date_planification\":\"2026-07-05T08:00:00+00:00\","
                + "\"date_debut\":\"2026-07-05T08:12:00+00:00\","
                + "\"date_fin\":\"2026-07-05T08:20:00+00:00\","
                + "\"message\":\"Traceback: boom\"}}";

        Traitement traitement =
                ParticipationsVigieChiro.detail(corps).orElseThrow().traitement();

        assertThat(traitement.etat()).isEqualTo(EtatTraitement.RETRY);
        assertThat(traitement.datePlanification()).isEqualTo("2026-07-05T08:00:00+00:00");
        assertThat(traitement.dateDebut()).isEqualTo("2026-07-05T08:12:00+00:00");
        assertThat(traitement.dateFin()).isEqualTo("2026-07-05T08:20:00+00:00");
        assertThat(traitement.message()).isEqualTo("Traceback: boom");
        assertThat(traitement.retry()).isEqualTo(1);
        // Un RETRY est un échec *rattrapé* : le serveur travaille encore, l'utilisateur n'a rien à faire.
        assertThat(traitement.enAttente()).isTrue();
        assertThat(traitement.enEchec()).isFalse();
    }

    @Test
    @DisplayName("detail : les 5 états du serveur sont reconnus ; un état inconnu est toléré (traitement inconnu)")
    void detail_tous_les_etats() {
        assertThat(etatLu("PLANIFIE")).isEqualTo(EtatTraitement.PLANIFIE);
        assertThat(etatLu("EN_COURS")).isEqualTo(EtatTraitement.EN_COURS);
        assertThat(etatLu("FINI")).isEqualTo(EtatTraitement.FINI);
        assertThat(etatLu("ERREUR")).isEqualTo(EtatTraitement.ERREUR);
        assertThat(etatLu("RETRY")).isEqualTo(EtatTraitement.RETRY);

        // Le serveur peut introduire un état sans nous prévenir : on ne lève pas, on l'ignore.
        assertThat(etatLu("ETAT_INEDIT")).isNull();
        assertThat(ParticipationsVigieChiro.detail("{\"_id\":\"x\",\"traitement\":{\"etat\":\"ETAT_INEDIT\"}}")
                        .orElseThrow()
                        .traitement()
                        .estInconnu())
                .isTrue();
    }

    @Test
    @DisplayName("detail : compteur d'essais non numérique ignoré (lecture tolérante)")
    void detail_retry_illisible() {
        Traitement traitement = ParticipationsVigieChiro.detail(
                        "{\"_id\":\"x\",\"traitement\":{\"etat\":\"ERREUR\",\"retry\":\"beaucoup\"}}")
                .orElseThrow()
                .traitement();

        assertThat(traitement.retry()).isNull();
        assertThat(traitement.enEchec()).isTrue();
    }

    @Test
    @DisplayName("detail : météo/config/traitement absents → null/vide ; sans _id ou illisible → Optional vide")
    void detail_participation_minimale_et_tolerante() {
        // Participation fraîchement créée : ni météo, ni configuration, ni traitement, ni _etag lisible.
        ParticipationDetail minimal = ParticipationsVigieChiro.detail("{\"_id\":\"x\",\"point\":\"Z1\"}")
                .orElseThrow();
        assertThat(minimal.meteo()).isNull();
        assertThat(minimal.configuration()).isEmpty();
        assertThat(minimal.etag()).isNull();
        // Jamais calculée : le bloc traitement est absent, pas null (l'appelant n'a pas à s'en méfier).
        assertThat(minimal.traitement()).isEqualTo(Traitement.absent());
        assertThat(minimal.traitement().estInconnu()).isTrue();
        assertThat(minimal.traitement().resultatsDisponibles()).isFalse();

        assertThat(ParticipationsVigieChiro.detail("{\"point\":\"Z1\"}")).isEmpty(); // sans _id
        assertThat(ParticipationsVigieChiro.detail("pas du json")).isEmpty();
    }

    /// État lu depuis un corps minimal ne portant que `traitement.etat`, ou `null` s'il est inconnu.
    private static EtatTraitement etatLu(String etat) {
        return ParticipationsVigieChiro.detail("{\"_id\":\"x\",\"traitement\":{\"etat\":\"" + etat + "\"}}")
                .orElseThrow()
                .traitement()
                .etat();
    }
}
