package fr.univ_amu.iut.qualification.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.qualification.model.ContexteVerification;
import fr.univ_amu.iut.qualification.model.SelectionDEcoute;
import fr.univ_amu.iut.qualification.model.SequenceEnSelection;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [SelectionEcouteViewModel] (bandeau identité + liste de la sélection +
/// progression + (re)génération). Le [ServiceQualification] est mocké : aucune base de données.
@ExtendWith(MockitoExtension.class)
class SelectionEcouteViewModelTest {

  private static final long ID_PASSAGE = 42L;
  private static final long ID_SELECTION = 7L;

  @Mock private ServiceQualification service;
  private SelectionEcouteViewModel viewModel;

  @BeforeEach
  void preparer() {
    viewModel = new SelectionEcouteViewModel(service);
  }

  /// Stub des trois lectures déclenchées par `ouvrirSur` : contexte (identité de la nuit),
  /// ouverture (idempotente) de la sélection, et détail de `n` séquences (non écoutées).
  private void stubOuverture(int n) {
    when(service.chargerContexte(anyLong()))
        .thenReturn(
            new ContexteVerification(
                "640380",
                "A1",
                2,
                2026,
                "2026-06-22",
                "20:25:00",
                "07:47:00",
                n,
                n * 5.0,
                StatutWorkflow.TRANSFORME,
                null));
    when(service.ouvrirVerification(anyLong()))
        .thenReturn(
            new SelectionDEcoute(
                ID_SELECTION, MethodeSelection.REPARTITION_TEMPORELLE, n, ID_PASSAGE));
    List<SequenceEnSelection> lignes = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      SequenceDEcoute sequence =
          new SequenceDEcoute(
              (long) i, "PaRec_" + i + ".wav", null, i, 0.0, 5.0, "/ws/seq" + i + ".wav", true, 1L);
      lignes.add(new SequenceEnSelection(sequence, i, false));
    }
    when(service.detaillerSelection(anyLong())).thenReturn(lignes);
  }

  @Test
  @DisplayName("ouvrirSur peuple le bandeau de contexte et la liste de la sélection")
  void ouvrir_peuple_bandeau_et_liste() {
    stubOuverture(20);

    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.titreContexteProperty().get()).contains("640380").contains("A1");
    assertThat(viewModel.volumetrieProperty().get()).contains("20 séquences");
    assertThat(viewModel.lignes()).hasSize(20);
    assertThat(viewModel.messageProperty().get()).isEmpty();
  }

  @Test
  @DisplayName("Sélectionner une séquence met à jour le chemin du fichier courant")
  void selectionner_met_a_jour_le_chemin() {
    stubOuverture(5);
    viewModel.ouvrirSur(ID_PASSAGE);

    viewModel.selectionner(viewModel.lignes().get(0));

    assertThat(viewModel.cheminSequenceCouranteProperty().get()).isNotNull();
    assertThat(viewModel.cheminSequenceCouranteProperty().get().toString()).endsWith("seq0.wav");
  }

  @Test
  @DisplayName("Marquer la séquence courante écoutée fait avancer la progression")
  void marquer_courante_avance_la_progression() {
    stubOuverture(4);
    viewModel.ouvrirSur(ID_PASSAGE);
    assertThat(viewModel.progressionProperty().get()).isZero();

    viewModel.selectionner(viewModel.lignes().get(0));
    viewModel.marquerCouranteEcoutee();

    verify(service).marquerSequenceEcoutee(ID_SELECTION, 0L);
    assertThat(viewModel.progressionProperty().get()).isEqualTo(0.25);
    assertThat(viewModel.progressionTexteProperty().get()).contains("1 / 4");
    assertThat(viewModel.lignes().get(0).ecoutee()).isTrue();
  }

  @Test
  @DisplayName("Régénérer recharge la liste avec la méthode/taille choisies et remet à zéro")
  void regenerer_recharge_et_remet_progression_a_zero() {
    stubOuverture(4);
    when(service.creerSelection(anyLong(), any(MethodeSelection.class), anyInt()))
        .thenReturn(new SelectionDEcoute(8L, MethodeSelection.ALEATOIRE, 4, ID_PASSAGE));
    viewModel.ouvrirSur(ID_PASSAGE);
    viewModel.selectionner(viewModel.lignes().get(0));
    viewModel.marquerCouranteEcoutee();
    assertThat(viewModel.progressionProperty().get()).isEqualTo(0.25);

    viewModel.methodeProperty().set(MethodeSelection.ALEATOIRE);
    viewModel.tailleProperty().set(4);
    viewModel.regenerer();

    verify(service).creerSelection(ID_PASSAGE, MethodeSelection.ALEATOIRE, 4);
    assertThat(viewModel.progressionProperty().get()).isZero();
  }
}
