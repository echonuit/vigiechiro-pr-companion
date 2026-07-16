package fr.univ_amu.iut.diagnostic.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.diagnostic.model.AnalyseAnomalies;
import fr.univ_amu.iut.diagnostic.model.CoherenceHoraire;
import fr.univ_amu.iut.diagnostic.model.Diagnostic;
import fr.univ_amu.iut.diagnostic.model.MesureClimatique;
import fr.univ_amu.iut.diagnostic.model.SerieClimatique;
import fr.univ_amu.iut.diagnostic.model.ServiceDiagnostic;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [DiagnosticViewModel] (série climatique + anomalies + évènements). Le
/// [ServiceDiagnostic] est mocké : aucune base de données.
@ExtendWith(MockitoExtension.class)
class DiagnosticViewModelTest {

    private static final long ID_PASSAGE = 42L;

    @Mock
    private ServiceDiagnostic service;

    private DiagnosticViewModel viewModel;

    @BeforeEach
    void preparer() {
        viewModel = new DiagnosticViewModel(service);
    }

    private static Diagnostic diagnostic(SerieClimatique climat, Double lat, Double lon) {
        return diagnostic(climat, lat, lon, CoherenceHoraire.indisponible());
    }

    private static Diagnostic diagnostic(SerieClimatique climat, Double lat, Double lon, CoherenceHoraire coherence) {
        return new Diagnostic(
                ID_PASSAGE,
                7L,
                "1925492",
                new AnalyseAnomalies(List.of("Réveil non programmé à 03:12"), List.of("Démarrage 20:25")),
                climat,
                lat,
                lon,
                LocalDateTime.of(2026, 6, 23, 8, 0),
                8.5,
                coherence);
    }

    private static SerieClimatique serie() {
        return SerieClimatique.presente(List.of(
                new MesureClimatique(LocalDate.of(2026, 6, 22), LocalTime.of(22, 0), 18.5, 72),
                new MesureClimatique(LocalDate.of(2026, 6, 23), LocalTime.of(2, 0), 14.0, 88)));
    }

    @Test
    @DisplayName("ouvrirSur mappe l'enregistreur, la série climatique, les anomalies et évènements")
    void ouvrir_mappe_le_diagnostic() {
        when(service.diagnostiquer(ID_PASSAGE)).thenReturn(diagnostic(serie(), 43.5, 5.4));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.enregistreurProperty().get()).isEqualTo("PR 1925492");
        assertThat(viewModel.mesures()).hasSize(2);
        assertThat(viewModel.anomalies()).containsExactly("Réveil non programmé à 03:12");
        assertThat(viewModel.evenements()).containsExactly("Démarrage 20:25");
        assertThat(viewModel.releveClimatiqueAbsentProperty().get()).isFalse();
        assertThat(viewModel.gpsDisponibleProperty().get()).isTrue();
        assertThat(viewModel.temperatureProperty().get())
                .as("#106 : température de début de nuit affichée au diagnostic")
                .isEqualTo("8,5 °C");
        assertThat(viewModel.messageProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("Rouvrir le même passage ne duplique pas les listes (setAll, pas addAll)")
    void ouvrir_deux_fois_ne_duplique_pas() {
        when(service.diagnostiquer(ID_PASSAGE)).thenReturn(diagnostic(serie(), 43.5, 5.4));

        viewModel.ouvrirSur(ID_PASSAGE);
        viewModel.ouvrirSur(ID_PASSAGE); // 2e ouverture : REMPLACE, ne cumule pas

        assertThat(viewModel.mesures()).hasSize(2);
        assertThat(viewModel.anomalies()).hasSize(1);
        assertThat(viewModel.evenements()).hasSize(1);
    }

    @Test
    @DisplayName("Un relevé climatique absent est signalé (R20) et la série reste vide")
    void releve_climatique_absent() {
        when(service.diagnostiquer(ID_PASSAGE)).thenReturn(diagnostic(SerieClimatique.absente(), null, null));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.releveClimatiqueAbsentProperty().get()).isTrue();
        assertThat(viewModel.gpsDisponibleProperty().get()).isFalse();
        assertThat(viewModel.mesures()).isEmpty();
    }

    @Test
    @DisplayName("#548 : cohérence horaires disponible → fenêtre nocturne exposée, sans alerte si tout est nocturne")
    void coherence_horaires_nominale() {
        CoherenceHoraire coherence =
                new CoherenceHoraire(true, LocalTime.of(21, 58), LocalTime.of(5, 48), false, false);
        when(service.diagnostiquer(ID_PASSAGE)).thenReturn(diagnostic(serie(), 43.5, 5.4, coherence));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.coherenceHoraireDisponibleProperty().get()).isTrue();
        assertThat(viewModel.fenetreNuitProperty().get()).contains("21:58").contains("05:48");
        assertThat(viewModel.alerteHorsNuitProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("#548 : démarrage et arrêt hors nuit → une alerte « hors nuit » est produite")
    void coherence_horaires_hors_nuit() {
        CoherenceHoraire coherence = new CoherenceHoraire(true, LocalTime.of(21, 58), LocalTime.of(5, 48), true, true);
        when(service.diagnostiquer(ID_PASSAGE)).thenReturn(diagnostic(serie(), 43.5, 5.4, coherence));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.alerteHorsNuitProperty().get())
                .contains("Hors nuit")
                .contains("diurne");
    }

    @Test
    @DisplayName("#548 : cohérence indisponible (pas de GPS/horaires) → ni fenêtre ni alerte")
    void coherence_horaires_indisponible() {
        when(service.diagnostiquer(ID_PASSAGE))
                .thenReturn(diagnostic(SerieClimatique.absente(), null, null, CoherenceHoraire.indisponible()));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.coherenceHoraireDisponibleProperty().get()).isFalse();
        assertThat(viewModel.fenetreNuitProperty().get()).isEmpty();
        assertThat(viewModel.alerteHorsNuitProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("Un passage introuvable est restitué dans le message et laisse l'état vide")
    void passage_introuvable() {
        when(service.diagnostiquer(99L)).thenThrow(new RegleMetierException("Passage introuvable : 99"));

        viewModel.ouvrirSur(99L);

        assertThat(viewModel.messageProperty().get()).contains("introuvable");
        assertThat(viewModel.enregistreurProperty().get()).isEmpty();
        assertThat(viewModel.mesures()).isEmpty();
    }

    @Test
    @DisplayName("Une réouverture qui échoue nettoie le diagnostic du passage précédent")
    void ouvrir_en_echec_nettoie_l_etat_precedent() {
        when(service.diagnostiquer(ID_PASSAGE)).thenReturn(diagnostic(serie(), 43.5, 5.4));
        when(service.diagnostiquer(99L)).thenThrow(new RegleMetierException("Passage introuvable : 99"));
        viewModel.ouvrirSur(ID_PASSAGE);
        assertThat(viewModel.mesures()).isNotEmpty();

        viewModel.ouvrirSur(99L);

        assertThat(viewModel.mesures()).isEmpty();
        assertThat(viewModel.anomalies()).isEmpty();
        assertThat(viewModel.enregistreurProperty().get()).isEmpty();
        assertThat(viewModel.messageProperty().get()).contains("introuvable");
    }
}
