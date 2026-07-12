package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Construction des corps JSON des écritures VigieChiro ([RequetesVigieChiro], #142) : `snake_case`,
/// champs `null` omis, blocs `meteo` / `configuration` imbriqués. Fonctions pures (aucun réseau).
class RequetesVigieChiroTest {

    @Test
    @DisplayName("participation : snake_case, meteo + configuration imbriqués, numero/commentaire présents")
    void participation_complete() {
        ParticipationADeposer participation = new ParticipationADeposer(
                "Z41",
                "2026-07-03T19:00:00Z",
                "2026-07-04T04:00:00Z",
                new MeteoDepot("FAIBLE", "0-25"),
                Map.of("micro0_type", "ICS"),
                "nuit de démonstration");

        JsonObject corps = JsonParser.parseString(RequetesVigieChiro.participation(participation))
                .getAsJsonObject();

        // Pas de champ `numero` : l'API Eve le refuse (422). Vérifié explicitement plus bas.
        assertThat(corps.has("numero")).isFalse();
        assertThat(corps.get("point").getAsString()).isEqualTo("Z41");
        assertThat(corps.get("date_debut").getAsString()).isEqualTo("2026-07-03T19:00:00Z");
        assertThat(corps.get("date_fin").getAsString()).isEqualTo("2026-07-04T04:00:00Z");
        assertThat(corps.get("commentaire").getAsString()).isEqualTo("nuit de démonstration");
        assertThat(corps.getAsJsonObject("meteo").get("vent").getAsString()).isEqualTo("FAIBLE");
        assertThat(corps.getAsJsonObject("meteo").get("couverture").getAsString())
                .isEqualTo("0-25");
        assertThat(corps.getAsJsonObject("configuration").get("micro0_type").getAsString())
                .isEqualTo("ICS");
    }

    @Test
    @DisplayName("participation : champs null (commentaire, meteo, configuration) omis du corps")
    void participation_champs_null_omis() {
        ParticipationADeposer participation =
                new ParticipationADeposer("Z41", "2026-07-03T19:00:00Z", "2026-07-04T04:00:00Z", null, null, null);

        JsonObject corps = JsonParser.parseString(RequetesVigieChiro.participation(participation))
                .getAsJsonObject();

        assertThat(corps.has("commentaire")).isFalse();
        assertThat(corps.has("meteo")).isFalse();
        assertThat(corps.has("configuration")).isFalse();
        assertThat(corps.get("point").getAsString()).isEqualTo("Z41");
    }

    @Test
    @DisplayName("miseAJourParticipation : omet `point`, garde dates/météo/configuration, champs null omis")
    void mise_a_jour_omet_point() {
        ParticipationADeposer maj = new ParticipationADeposer(
                "Z41",
                "2026-07-03T19:00:00Z",
                "2026-07-04T04:00:00Z",
                new MeteoDepot("FAIBLE", "0-25"),
                Map.of("micro0_type", "ICS"),
                null);

        JsonObject corps = JsonParser.parseString(RequetesVigieChiro.miseAJourParticipation(maj))
                .getAsJsonObject();

        // `point` retiré : la localité identifie la participation, non modifiable via l'app.
        assertThat(corps.has("point")).isFalse();
        // Métadonnées synchronisables conservées.
        assertThat(corps.get("date_debut").getAsString()).isEqualTo("2026-07-03T19:00:00Z");
        assertThat(corps.get("date_fin").getAsString()).isEqualTo("2026-07-04T04:00:00Z");
        assertThat(corps.getAsJsonObject("meteo").get("vent").getAsString()).isEqualTo("FAIBLE");
        assertThat(corps.getAsJsonObject("configuration").get("micro0_type").getAsString())
                .isEqualTo("ICS");
        // Champ null (commentaire) omis.
        assertThat(corps.has("commentaire")).isFalse();
    }

    @Test
    @DisplayName("fichier : titre + multipart:false + lien_participation, sans mime (déduit par l'API)")
    void fichier_upload_simple() {
        JsonObject corps = JsonParser.parseString(
                        RequetesVigieChiro.fichier("Car130711-2026-Pass1-Z41_000.wav", "part-42"))
                .getAsJsonObject();

        assertThat(corps.get("titre").getAsString()).isEqualTo("Car130711-2026-Pass1-Z41_000.wav");
        assertThat(corps.get("multipart").getAsBoolean()).isFalse();
        // #984 : sans ce lien, le fichier monte sur S3 mais reste ORPHELIN (compute « extrait 0 fichier »).
        assertThat(corps.get("lien_participation").getAsString()).isEqualTo("part-42");
        assertThat(corps.has("mime")).isFalse();
    }

    @Test
    @DisplayName("finalisation : objet JSON vide")
    void finalisation_vide() {
        assertThat(RequetesVigieChiro.finalisation()).isEqualTo("{}");
    }
}
