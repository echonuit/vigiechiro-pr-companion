package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie l'[EphemerideSolaire] contre des **valeurs de référence connues** (heures UTC issues de
/// sources astronomiques publiques), avec une tolérance de 5 minutes cohérente avec l'approximation
/// NOAA, et couvre les cas limites : équinoxe (jour ≈ nuit), jour et nuit polaires, déterminisme.
class EphemerideSolaireTest {

    private static final double PARIS_LAT = 48.8566;
    private static final double PARIS_LON = 2.3522;

    private static void proche(Optional<LocalTime> obtenu, LocalTime attendu) {
        assertThat(obtenu).isPresent();
        assertThat(obtenu.orElseThrow()).isCloseTo(attendu, within(5, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("Paris au solstice d'été : lever ≈ 03:48 UTC, coucher ≈ 19:58 UTC (soit 05:48 / 21:58 heure locale)")
    void paris_solstice_ete() {
        LocalDate jour = LocalDate.of(2026, 6, 21);
        proche(EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, jour), LocalTime.of(3, 48));
        proche(EphemerideSolaire.coucher(PARIS_LAT, PARIS_LON, jour), LocalTime.of(19, 58));
    }

    @Test
    @DisplayName("Paris au solstice d'hiver : lever ≈ 07:42 UTC, coucher ≈ 15:57 UTC")
    void paris_solstice_hiver() {
        LocalDate jour = LocalDate.of(2026, 12, 21);
        proche(EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, jour), LocalTime.of(7, 42));
        proche(EphemerideSolaire.coucher(PARIS_LAT, PARIS_LON, jour), LocalTime.of(15, 57));
    }

    @Test
    @DisplayName("Londres au solstice d'été : lever ≈ 03:44 UTC, coucher ≈ 20:22 UTC")
    void londres_solstice_ete() {
        LocalDate jour = LocalDate.of(2026, 6, 21);
        proche(EphemerideSolaire.lever(51.5074, -0.1278, jour), LocalTime.of(3, 44));
        proche(EphemerideSolaire.coucher(51.5074, -0.1278, jour), LocalTime.of(20, 22));
    }

    @Test
    @DisplayName("Équateur à l'équinoxe de printemps : journée d'environ 12 heures")
    void equateur_equinoxe_journee_de_douze_heures() {
        LocalDate jour = LocalDate.of(2026, 3, 20);
        LocalTime lever = EphemerideSolaire.lever(0.0, 0.0, jour).orElseThrow();
        LocalTime coucher = EphemerideSolaire.coucher(0.0, 0.0, jour).orElseThrow();
        long dureeJourMinutes = ChronoUnit.MINUTES.between(lever, coucher);
        assertThat(dureeJourMinutes).isBetween(11L * 60, 13L * 60);
    }

    @Test
    @DisplayName("Le coucher suit le lever (même jour UTC) aux latitudes tempérées")
    void coucher_apres_lever() {
        LocalDate jour = LocalDate.of(2026, 6, 21);
        LocalTime lever = EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, jour).orElseThrow();
        LocalTime coucher =
                EphemerideSolaire.coucher(PARIS_LAT, PARIS_LON, jour).orElseThrow();
        assertThat(coucher).isAfter(lever);
    }

    @Test
    @DisplayName("Jour polaire (Longyearbyen, solstice d'été) : le soleil ne se couche pas → vide")
    void jour_polaire_pas_de_coucher() {
        LocalDate jour = LocalDate.of(2026, 6, 21);
        assertThat(EphemerideSolaire.lever(78.22, 15.65, jour)).isEmpty();
        assertThat(EphemerideSolaire.coucher(78.22, 15.65, jour)).isEmpty();
    }

    @Test
    @DisplayName("Nuit polaire (Longyearbyen, solstice d'hiver) : le soleil ne se lève pas → vide")
    void nuit_polaire_pas_de_lever() {
        LocalDate jour = LocalDate.of(2026, 12, 21);
        assertThat(EphemerideSolaire.lever(78.22, 15.65, jour)).isEmpty();
        assertThat(EphemerideSolaire.coucher(78.22, 15.65, jour)).isEmpty();
    }

    @Test
    @DisplayName("Heure locale (Europe/Paris) au solstice d'été : coucher ≈ 21:58, lever ≈ 05:48")
    void paris_solstice_ete_heure_locale() {
        LocalDate jour = LocalDate.of(2026, 6, 21);
        ZoneId paris = ZoneId.of("Europe/Paris");
        proche(EphemerideSolaire.coucherLocal(PARIS_LAT, PARIS_LON, jour, paris), LocalTime.of(21, 58));
        proche(EphemerideSolaire.leverLocal(PARIS_LAT, PARIS_LON, jour, paris), LocalTime.of(5, 48));
    }

    @Test
    @DisplayName("Heure locale : jour polaire → vide (propagé depuis le calcul UTC)")
    void heure_locale_jour_polaire_vide() {
        LocalDate jour = LocalDate.of(2026, 6, 21);
        ZoneId oslo = ZoneId.of("Europe/Oslo");
        assertThat(EphemerideSolaire.coucherLocal(78.22, 15.65, jour, oslo)).isEmpty();
        assertThat(EphemerideSolaire.leverLocal(78.22, 15.65, jour, oslo)).isEmpty();
    }

    @Test
    @DisplayName("Déterministe : mêmes entrées → même résultat")
    void deterministe() {
        LocalDate jour = LocalDate.of(2026, 6, 21);
        assertThat(EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, jour))
                .isEqualTo(EphemerideSolaire.lever(PARIS_LAT, PARIS_LON, jour));
    }
}
