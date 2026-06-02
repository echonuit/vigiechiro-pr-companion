package fr.univ_amu.iut.validation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ObservationStatut;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.VueValidation;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [ValidationViewModel] (liste, sélection, détail, compteurs). Le
/// [ServiceValidation] est mocké : aucune base de données.
@ExtendWith(MockitoExtension.class)
class ValidationViewModelTest {

  private static final long ID_PASSAGE = 42L;
  private static final long ID_RESULTATS = 7L;

  @Mock private ServiceValidation service;
  private ValidationViewModel viewModel;

  @BeforeEach
  void preparer() {
    viewModel = new ValidationViewModel(service);
  }

  private static Observation observation(Long id, String taxonObservateur, Double probObservateur) {
    return new Observation(
        id,
        100L + id,
        1.0,
        2.0,
        45000,
        "PIPPIP",
        0.92,
        null,
        taxonObservateur,
        probObservateur,
        null,
        false,
        ModeValidation.MANUEL,
        ID_RESULTATS);
  }

  private static VueValidation vueTrois() {
    // une non touchée, une validée (taxon obs = Tadarida), une corrigée (taxon obs différent)
    return new VueValidation(
        ID_RESULTATS,
        List.of(
            new ObservationStatut(observation(1L, null, null), StatutObservation.NON_TOUCHEE),
            new ObservationStatut(observation(2L, "PIPPIP", 0.9), StatutObservation.VALIDEE),
            new ObservationStatut(observation(3L, "NYCNOC", 0.8), StatutObservation.CORRIGEE)));
  }

  @Test
  @DisplayName("ouvrirSur : charge les observations et calcule les compteurs de revue")
  void ouvrir_charge_et_compte() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());

    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.observations()).hasSize(3);
    assertThat(viewModel.idResultats()).isEqualTo(ID_RESULTATS);
    assertThat(viewModel.nombreTotalProperty().get()).isEqualTo(3);
    assertThat(viewModel.nombreValideesProperty().get()).isEqualTo(1);
    assertThat(viewModel.nombreCorrigeesProperty().get()).isEqualTo(1);
    assertThat(viewModel.progressionProperty().get()).isEqualTo("2 / 3 revues");
    assertThat(viewModel.messageProperty().get()).isEmpty();
  }

  @Test
  @DisplayName("selectionProperty : la sélection alimente le détail, la désélection le vide")
  void selection_alimente_detail() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());
    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.detailProperty().get()).isEmpty();

    viewModel.selectionProperty().set(viewModel.observations().get(1));
    assertThat(viewModel.detailProperty().get())
        .contains("Tadarida : PIPPIP")
        .contains("Observateur : PIPPIP")
        .contains("Statut : Validée");

    viewModel.selectionProperty().set(null);
    assertThat(viewModel.detailProperty().get()).isEmpty();
  }

  @Test
  @DisplayName("détail : un taxon observateur absent est affiché « non renseigné »")
  void detail_non_renseigne() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());
    viewModel.ouvrirSur(ID_PASSAGE);

    viewModel.selectionProperty().set(viewModel.observations().get(0));
    assertThat(viewModel.detailProperty().get())
        .contains("Observateur : non renseigné")
        .contains("Statut : À revoir");
  }

  @Test
  @DisplayName("ouvrirSur : passage sans CSV importé donne une vue vide et un message d'état")
  void ouvrir_sans_resultats() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(new VueValidation(null, List.of()));

    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.observations()).isEmpty();
    assertThat(viewModel.idResultats()).isNull();
    assertThat(viewModel.progressionProperty().get()).isEmpty();
    assertThat(viewModel.messageProperty().get())
        .isEqualTo("Aucun résultat Tadarida importé pour ce passage.");
  }

  @Test
  @DisplayName("ouvrirSur : une erreur de chargement vide l'écran et expose le message")
  void ouvrir_en_erreur() {
    // d'abord un chargement valide, pour vérifier que le second ouvrirSur réinitialise tout
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());
    viewModel.ouvrirSur(ID_PASSAGE);
    viewModel.selectionProperty().set(viewModel.observations().get(0));

    when(service.chargerValidation(ID_PASSAGE))
        .thenThrow(new RegleMetierException("Passage introuvable : 42"));
    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.observations()).isEmpty();
    assertThat(viewModel.idResultats()).isNull();
    assertThat(viewModel.detailProperty().get()).isEmpty();
    assertThat(viewModel.messageProperty().get()).isEqualTo("Passage introuvable : 42");
  }
}
