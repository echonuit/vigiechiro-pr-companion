package fr.univ_amu.iut.diagnostic.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.Completude;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
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
                coherence,
                Completude.INCONNUE);
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
        assertThat(viewModel.retourProperty().get().present())
                .as("aucun retour à afficher")
                .isFalse();
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
        CoherenceHoraire coherence = new CoherenceHoraire(
                true,
                LocalTime.of(21, 58),
                LocalTime.of(5, 48),
                LocalTime.of(21, 28),
                LocalTime.of(6, 18),
                LocalTime.of(21, 28),
                LocalTime.of(6, 18),
                true,
                true,
                CoherenceHoraire.Couverture.COUVERTE);
        when(service.diagnostiquer(ID_PASSAGE)).thenReturn(diagnostic(serie(), 43.5, 5.4, coherence));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.coherenceHoraireDisponibleProperty().get()).isTrue();
        assertThat(viewModel.fenetreNuitProperty().get()).contains("21:58").contains("05:48");
        // Une nuit qui couvre la fenêtre du protocole reçoit une INFORMATION, et non le silence :
        // le protocole est un plancher, et le dépasser est un fait qu'on rapporte sans en faire un
        // défaut. C'est ce que #4988 devra rendre visuellement distinct d'un avertissement.
        assertThat(viewModel.alerteHorsNuitProperty().get().severite()).isEqualTo(Severite.INFO);
        assertThat(viewModel.alerteHorsNuitProperty().get().texte()).contains("couvre la fenêtre du protocole");
    }

    @Test
    @DisplayName("#4987 : une plage qui ne couvre pas la fenêtre exigée produit un avertissement")
    void coherence_horaires_hors_nuit() {
        CoherenceHoraire coherence = new CoherenceHoraire(
                true,
                LocalTime.of(21, 58),
                LocalTime.of(5, 48),
                LocalTime.of(21, 28),
                LocalTime.of(6, 18),
                LocalTime.of(22, 30),
                LocalTime.of(5, 30),
                // 22:30 après 21:28 et 05:30 avant 06:18 : AUCUN des deux bords n'est couvert.
                false,
                false,
                CoherenceHoraire.Couverture.INCOMPLETE);
        when(service.diagnostiquer(ID_PASSAGE)).thenReturn(diagnostic(serie(), 43.5, 5.4, coherence));

        viewModel.ouvrirSur(ID_PASSAGE);

        // #5200 : l'alerte nomme le BORD qui manque. Les horaires sont sur la ligne des plages
        // juste au-dessus ; les répéter ici les dirait deux fois à deux lignes d'écart. Ce qu'elle
        // apporte est ce qu'aucun chiffre ne dit : de quel côté ça manque, et donc quoi faire
        // autrement.
        assertThat(viewModel.alerteHorsNuitProperty().get().texte())
                .as("cette nuit commence trop tard ET s'arrête trop tôt : l'alerte doit le DIRE,"
                        + " et non se contenter de rappeler la règle, ni prétendre qu'un côté va bien")
                .contains("commencent moins de 30 minutes avant le coucher")
                .contains("s'arrêtent moins de 30 minutes après le lever")
                .doesNotContain("bien");
        // #2050 : la sévérité est portée par la donnée, plus par la classe CSS ni le FontIcon figés du FXML.
        assertThat(viewModel.alerteHorsNuitProperty().get().severite()).isEqualTo(Severite.AVERTISSEMENT);
    }

    @Test
    @DisplayName("#5200 : les quatre cas de couverture, chacun disant ce qui manque")
    void les_quatre_cas_de_couverture() {
        // Trois façons de ne pas couvrir la fenêtre, et elles n'appellent pas la même correction :
        // partir plus tôt, s'arrêter plus tard, ou les deux. Une alerte unique les confondait.
        assertThat(DiagnosticViewModel.libelleEcart(coherenceDe(true, false)).texte())
                .as("début tenu, fin trop tôt")
                .contains("s'arrêtent moins de 30 minutes après le lever")
                .contains("le début est bien couvert");

        assertThat(DiagnosticViewModel.libelleEcart(coherenceDe(false, true)).texte())
                .as("début trop tard, fin tenue")
                .contains("commencent moins de 30 minutes avant le coucher")
                .contains("la fin est bien couverte");

        assertThat(DiagnosticViewModel.libelleEcart(coherenceDe(false, false)).texte())
                .as("aucun des deux bords tenu : la phrase ne doit pas prétendre qu'un côté va bien")
                .contains("commencent moins de 30 minutes avant le coucher")
                .contains("s'arrêtent moins de 30 minutes après le lever")
                .doesNotContain("bien");

        // Le quatrième cas est COUVERTE, et il n'alerte pas : le protocole est un plancher, le
        // dépasser n'est pas un défaut.
        assertThat(DiagnosticViewModel.libelleEcart(coherenceDe(true, true)).severite())
                .as("les deux bords tenus : une information, jamais un avertissement")
                .isNotEqualTo(Severite.AVERTISSEMENT);
    }

    /// Une cohérence dont on choisit les deux bords ; les heures ne servent pas ici.
    private static CoherenceHoraire coherenceDe(boolean debutTenu, boolean finTenue) {
        return new CoherenceHoraire(
                true,
                LocalTime.of(21, 58),
                LocalTime.of(5, 48),
                LocalTime.of(21, 28),
                LocalTime.of(6, 18),
                LocalTime.of(22, 30),
                LocalTime.of(5, 30),
                debutTenu,
                finTenue,
                debutTenu && finTenue ? CoherenceHoraire.Couverture.COUVERTE : CoherenceHoraire.Couverture.INCOMPLETE);
    }

    @Test
    @DisplayName("#548 : cohérence indisponible (pas de GPS/horaires) → ni fenêtre ni alerte")
    void coherence_horaires_indisponible() {
        when(service.diagnostiquer(ID_PASSAGE))
                .thenReturn(diagnostic(SerieClimatique.absente(), null, null, CoherenceHoraire.indisponible()));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.coherenceHoraireDisponibleProperty().get()).isFalse();
        assertThat(viewModel.fenetreNuitProperty().get()).isEmpty();
        assertThat(viewModel.alerteHorsNuitProperty().get()).isEqualTo(RetourOperation.AUCUN);
    }

    @Test
    @DisplayName("Un passage introuvable est restitué dans le message et laisse l'état vide")
    void passage_introuvable() {
        when(service.diagnostiquer(99L)).thenThrow(new RegleMetierException("Passage introuvable : 99"));

        viewModel.ouvrirSur(99L);

        assertThat(viewModel.retourProperty().get().texte()).contains("introuvable");
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
        assertThat(viewModel.retourProperty().get().texte()).contains("introuvable");
    }
}
