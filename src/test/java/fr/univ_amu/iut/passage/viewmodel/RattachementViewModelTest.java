package fr.univ_amu.iut.passage.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [RattachementViewModel] (modale E2.S8). Le [ServicePassage] est mocké :
/// aucune base de données ni disque.
@ExtendWith(MockitoExtension.class)
class RattachementViewModelTest {

  private static final long ID = 7L;

  @Mock private ServicePassage service;
  private RattachementViewModel viewModel;

  @BeforeEach
  void preparer() {
    viewModel = new RattachementViewModel(service);
  }

  private static DetailPassage detail(int numero, int annee, int nombreSequences) {
    return new DetailPassage(
        numero,
        annee,
        "2026-06-20",
        "21:00:00",
        "05:00:00",
        "1925492",
        StatutWorkflow.TRANSFORME,
        Verdict.OK,
        null,
        0L,
        0L,
        nombreSequences,
        0.0);
  }

  @Test
  @DisplayName(
      "ouvrirSur pré-remplit l'année et le n° ; le récap est neutre tant que rien ne change")
  void ouvrir_pre_remplit_et_recap_neutre() {
    when(service.detailPassage(ID)).thenReturn(detail(1, 2026, 30));

    viewModel.ouvrirSur(ID, "040962", "A1");

    assertThat(viewModel.anneeProperty().get()).isEqualTo(2026);
    assertThat(viewModel.numeroPassageProperty().get()).isEqualTo(1);
    assertThat(viewModel.recapProperty().get()).contains("Aucun changement");
  }

  @Test
  @DisplayName("Changer le n° met à jour le récap (quadruplet X → Y + nombre de séquences)")
  void changer_numero_met_a_jour_le_recap() {
    when(service.detailPassage(ID)).thenReturn(detail(1, 2026, 30));
    viewModel.ouvrirSur(ID, "040962", "A1");

    viewModel.numeroPassageProperty().set(2);

    assertThat(viewModel.recapProperty().get())
        .contains("Car040962-2026-Pass1-A1")
        .contains("Car040962-2026-Pass2-A1")
        .contains("30");
  }

  @Test
  @DisplayName("valider délègue à modifierRattachement avec le nouveau préfixe et réussit")
  void valider_delegue_et_reussit() {
    when(service.detailPassage(ID)).thenReturn(detail(1, 2026, 30));
    viewModel.ouvrirSur(ID, "040962", "A1");
    viewModel.numeroPassageProperty().set(2);

    boolean ok = viewModel.valider();

    assertThat(ok).isTrue();
    verify(service).modifierRattachement(ID, new Prefixe("040962", 2026, 2, "A1"));
  }

  @Test
  @DisplayName("valider restitue l'erreur métier (R5) et renvoie false")
  void valider_restitue_l_erreur() {
    when(service.detailPassage(ID)).thenReturn(detail(1, 2026, 30));
    viewModel.ouvrirSur(ID, "040962", "A1");
    viewModel.numeroPassageProperty().set(2);
    doThrow(new RegleMetierException("R5 : un passage n°2 existe déjà"))
        .when(service)
        .modifierRattachement(eq(ID), any());

    boolean ok = viewModel.valider();

    assertThat(ok).isFalse();
    assertThat(viewModel.messageErreurProperty().get()).contains("R5");
  }

  @Test
  @DisplayName("valider refuse un n° de passage < 1 sans appeler le service")
  void valider_refuse_numero_invalide() {
    when(service.detailPassage(ID)).thenReturn(detail(1, 2026, 30));
    viewModel.ouvrirSur(ID, "040962", "A1");
    viewModel.numeroPassageProperty().set(0);

    boolean ok = viewModel.valider();

    assertThat(ok).isFalse();
    assertThat(viewModel.messageErreurProperty().get()).contains("numéro de passage");
    verify(service, never()).modifierRattachement(any(), any());
  }

  @Test
  @DisplayName("valider surface une défaillance disque/base dans le message au lieu de la propager")
  void valider_surface_une_defaillance_operationnelle() {
    when(service.detailPassage(ID)).thenReturn(detail(1, 2026, 30));
    viewModel.ouvrirSur(ID, "040962", "A1");
    viewModel.numeroPassageProperty().set(2);
    doThrow(new UncheckedIOException("Déplacement du dossier impossible", new IOException()))
        .when(service)
        .modifierRattachement(eq(ID), any());

    boolean ok = viewModel.valider();

    assertThat(ok).isFalse();
    assertThat(viewModel.messageErreurProperty().get()).contains("Déplacement");
  }
}
