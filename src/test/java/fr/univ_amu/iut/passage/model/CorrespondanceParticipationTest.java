package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.MeteoDepot;
import fr.univ_amu.iut.commun.api.ParticipationADeposer;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Correspondance **pure** passage ↔ participation (axe 4) : construction du corps API (push, dates RFC 1123
/// UTC + météo + configuration) et retraduction météo/config (pull), sans réseau ni base.
class CorrespondanceParticipationTest {

    @Test
    @DisplayName("versParticipation : point, dates RFC 1123 UTC (franchit minuit), météo, config complète")
    void vers_participation() {
        Passage passage = passage("{\"vent\":\"FAIBLE\",\"couvertureNuageuse\":\"DE_25_A_50\"}");
        MaterielMicro micro = new MaterielMicro(42L, PositionMicro.CANOPEE, 4.0, "ICS");

        ParticipationADeposer p = CorrespondanceParticipation.versParticipation("Z41", passage, micro);

        assertThat(p.point()).isEqualTo("Z41");
        // Dates revérifiées par aller-retour vers le fuseau local (déterministe quel que soit le fuseau).
        assertThat(instantLocal(p.dateDebut())).isEqualTo(LocalDateTime.of(2026, 7, 3, 21, 0));
        assertThat(instantLocal(p.dateFin())).isEqualTo(LocalDateTime.of(2026, 7, 4, 5, 0)); // franchit minuit
        assertThat(p.meteo().vent()).isEqualTo("FAIBLE");
        assertThat(p.meteo().couverture()).isEqualTo("25-50");
        assertThat(p.configuration())
                .containsEntry("detecteur_enregistreur_type", "PassiveRecorder")
                .containsEntry("detecteur_enregistreur_numserie", "1997632")
                .containsEntry("micro0_type", "ICS")
                .containsEntry("micro0_position", "CANOPEE")
                .containsEntry("micro0_hauteur", "4");
    }

    @Test
    @DisplayName("versParticipation : sans météo ni micro → meteo null, config réduite au détecteur")
    void vers_participation_minimale() {
        ParticipationADeposer p =
                CorrespondanceParticipation.versParticipation("Z41", passage(null), MaterielMicro.vide(42L));

        assertThat(p.meteo()).isNull();
        assertThat(p.configuration())
                .containsOnlyKeys("detecteur_enregistreur_type", "detecteur_enregistreur_numserie");
    }

    @Test
    @DisplayName("fusionnerMeteo : met à jour vent/couverture depuis l'API, PRÉSERVE les températures locales")
    void fusionner_meteo_preserve_temperatures() {
        MeteoReleve local = new MeteoReleve(12.0, 8.0, Vent.NUL, CouvertureNuageuse.DE_0_A_25);

        MeteoReleve fusion = CorrespondanceParticipation.fusionnerMeteo(local, new MeteoDepot("FORT", "75-100"));

        assertThat(fusion.temperatureDebutNuit()).isEqualTo(12.0);
        assertThat(fusion.temperatureFinNuit()).isEqualTo(8.0);
        assertThat(fusion.vent()).isEqualTo(Vent.FORT);
        assertThat(fusion.couvertureNuageuse()).isEqualTo(CouvertureNuageuse.DE_75_A_100);
    }

    @Test
    @DisplayName("fusionnerMeteo : météo distante null → relevé local inchangé")
    void fusionner_meteo_distant_null() {
        MeteoReleve local = new MeteoReleve(12.0, null, Vent.FAIBLE, null);
        assertThat(CorrespondanceParticipation.fusionnerMeteo(local, null)).isEqualTo(local);
    }

    @Test
    @DisplayName("microDepuis : mappe micro0_* vers MaterielMicro ; valeurs absentes → null")
    void micro_depuis_config() {
        MaterielMicro micro = CorrespondanceParticipation.microDepuis(
                42L, Map.of("micro0_type", "ICS", "micro0_position", "CANOPEE", "micro0_hauteur", "4"));

        assertThat(micro.typeMicro()).isEqualTo("ICS");
        assertThat(micro.positionMicro()).isEqualTo(PositionMicro.CANOPEE);
        assertThat(micro.hauteurMetres()).isEqualTo(4.0);

        MaterielMicro vide = CorrespondanceParticipation.microDepuis(42L, Map.of());
        assertThat(vide.typeMicro()).isNull();
        assertThat(vide.positionMicro()).isNull();
        assertThat(vide.hauteurMetres()).isNull();
    }

    private static Passage passage(String donneesMeteo) {
        // Nuit du 3→4 juillet : début 21:00, fin 05:00 (franchit minuit).
        return new Passage(
                42L,
                1,
                2026,
                "2026-07-03",
                "21:00:00",
                "05:00:00",
                null,
                StatutWorkflow.TRANSFORME,
                null,
                null,
                donneesMeteo,
                null,
                7L,
                "1997632");
    }

    private static LocalDateTime instantLocal(String rfc1123) {
        return ZonedDateTime.parse(rfc1123, DateTimeFormatter.RFC_1123_DATE_TIME)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
