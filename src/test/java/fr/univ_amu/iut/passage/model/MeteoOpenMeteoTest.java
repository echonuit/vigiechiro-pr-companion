package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests du client Open-Meteo (#547) : le **parsing** d'une réponse figée (aucun réseau) et la
/// **dégradation propre** hors-ligne. On ne teste pas d'appel réseau réel.
class MeteoOpenMeteoTest {

    private static final String REPONSE = "{\"latitude\":43.4,\"longitude\":-1.5,"
            + "\"hourly_units\":{\"temperature_2m\":\"°C\",\"windspeed_10m\":\"km/h\",\"cloudcover\":\"%\"},"
            + "\"hourly\":{"
            + "\"time\":[\"2026-06-20T20:00\",\"2026-06-20T21:00\",\"2026-06-21T05:00\"],"
            + "\"temperature_2m\":[15.0,13.5,8.2],"
            + "\"windspeed_10m\":[10.0,12.4,5.0],"
            + "\"cloudcover\":[20,40,75]}}";

    @Test
    @DisplayName("parse aligne les heures début/fin sur le tableau horaire et lit vent + couverture")
    void parse_aligne_les_heures() {
        Optional<MeteoReleve> releve = MeteoOpenMeteo.parse(REPONSE, "2026-06-20T21:00", "2026-06-21T05:00");

        assertThat(releve).contains(new MeteoReleve(13.5, 8.2, 12.4, 40.0));
    }

    @Test
    @DisplayName("parse : une heure absente du tableau donne une grandeur nulle (les autres sont lues)")
    void parse_heure_absente_donne_null() {
        Optional<MeteoReleve> releve = MeteoOpenMeteo.parse(REPONSE, "2026-06-20T21:00", "2026-06-21T23:00");

        assertThat(releve).isPresent();
        assertThat(releve.get().temperatureDebutNuit()).isEqualTo(13.5);
        assertThat(releve.get().temperatureFinNuit()).as("05:00 absent → null").isNull();
    }

    @Test
    @DisplayName("parse : corps vide ou non-JSON → aucune grandeur → empty")
    void parse_corps_invalide_est_empty() {
        assertThat(MeteoOpenMeteo.parse("{}", "2026-06-20T21:00", "2026-06-21T05:00"))
                .isEmpty();
        assertThat(MeteoOpenMeteo.parse("pas du json", "x", "y")).isEmpty();
    }

    @Test
    @DisplayName("pour : hors-ligne (URL injoignable) → empty, sans lever")
    void pour_hors_ligne_est_empty() {
        FournisseurMeteo horsLigne = new MeteoOpenMeteo("http://localhost:1/v1/archive");

        Optional<MeteoReleve> releve =
                horsLigne.pour(43.4, -1.5, LocalDate.of(2026, 6, 20), LocalTime.of(21, 30), LocalTime.of(5, 15));

        assertThat(releve).isEmpty();
    }
}
