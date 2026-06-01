package fr.univ_amu.iut.passage.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [PassageViewModel] (fiche d'identité + stepper de statut + stats). Le
/// [ServicePassage] est mocké : aucune base de données.
@ExtendWith(MockitoExtension.class)
class PassageViewModelTest {

  private static final long ID_PASSAGE = 42L;
  private static final ContexteSite CONTEXTE = new ContexteSite("640380", "A1", "Étang");

  @Mock private ServicePassage service;
  private PassageViewModel viewModel;

  @BeforeEach
  void preparer() {
    viewModel = new PassageViewModel(service);
  }

  private static DetailPassage detail(StatutWorkflow statut) {
    return new DetailPassage(
        2,
        2026,
        "2026-06-22",
        "20:25:00",
        "07:47:00",
        "1925492",
        statut,
        Verdict.OK,
        null,
        4096L,
        1024L,
        30,
        150.0);
  }

  @Test
  @DisplayName("ouvrirSur mappe la fiche d'identité (contexte site + données du passage)")
  void ouvrir_mappe_la_fiche() {
    when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.TRANSFORME));

    viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

    assertThat(viewModel.titreContexteProperty().get())
        .contains("640380")
        .contains("A1")
        .contains("N° 2")
        .contains("2026");
    assertThat(viewModel.plageHoraireProperty().get()).contains("20:25:00").contains("07:47:00");
    assertThat(viewModel.enregistreurProperty().get()).isEqualTo("PR 1925492");
    assertThat(viewModel.statutProperty().get()).isEqualTo(StatutWorkflow.TRANSFORME);
    assertThat(viewModel.nombreSequencesProperty().get()).isEqualTo(30);
    assertThat(viewModel.verdictProperty().get()).isEqualTo(Verdict.OK);
    assertThat(viewModel.volumeBrutsProperty().get()).isEqualTo("4 Ko");
    assertThat(viewModel.volumeTransformesProperty().get()).isEqualTo("1 Ko");
    assertThat(viewModel.dureeAudibleProperty().get()).isEqualTo("2 min 30 s");
    assertThat(viewModel.messageProperty().get()).isEmpty();
  }

  @Test
  @DisplayName("Le stepper reflète le statut courant (franchies / courante / à venir)")
  void stepper_reflete_le_statut() {
    when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.VERIFIE));

    viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

    assertThat(viewModel.etapes()).hasSize(5);
    assertThat(viewModel.etapes().get(0))
        .isEqualTo(new EtapeWorkflow(StatutWorkflow.IMPORTE, EtatEtape.FRANCHIE));
    assertThat(viewModel.etapes().get(1))
        .isEqualTo(new EtapeWorkflow(StatutWorkflow.TRANSFORME, EtatEtape.FRANCHIE));
    assertThat(viewModel.etapes().get(2))
        .isEqualTo(new EtapeWorkflow(StatutWorkflow.VERIFIE, EtatEtape.COURANTE));
    assertThat(viewModel.etapes().get(3))
        .isEqualTo(new EtapeWorkflow(StatutWorkflow.PRET_A_DEPOSER, EtatEtape.A_VENIR));
    assertThat(viewModel.etapes().get(4))
        .isEqualTo(new EtapeWorkflow(StatutWorkflow.DEPOSE, EtatEtape.A_VENIR));
  }

  @Test
  @DisplayName("La vérification est indisponible tant que la nuit n'est pas transformée")
  void verification_indisponible_avant_transformation() {
    when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.IMPORTE));

    viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

    assertThat(viewModel.verificationDisponibleProperty().get()).isFalse();
  }

  @Test
  @DisplayName("La vérification est disponible dès que la nuit est transformée")
  void verification_disponible_des_transforme() {
    when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.TRANSFORME));

    viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

    assertThat(viewModel.verificationDisponibleProperty().get()).isTrue();
  }

  @Test
  @DisplayName("La validation Tadarida est verrouillée tant que le passage n'est pas déposé")
  void validation_verrouillee_avant_depot() {
    when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.VERIFIE));

    viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

    assertThat(viewModel.validationVerrouilleeProperty().get()).isTrue();
  }

  @Test
  @DisplayName("La validation Tadarida est déverrouillée une fois le passage déposé")
  void validation_deverrouillee_si_depose() {
    when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.DEPOSE));

    viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

    assertThat(viewModel.validationVerrouilleeProperty().get()).isFalse();
  }

  @Test
  @DisplayName("Un passage introuvable est restitué dans le message et laisse l'état vide")
  void passage_introuvable() {
    when(service.detailPassage(99L))
        .thenThrow(new RegleMetierException("Passage introuvable : 99"));

    viewModel.ouvrirSur(99L, CONTEXTE);

    assertThat(viewModel.messageProperty().get()).contains("introuvable");
    assertThat(viewModel.statutProperty().get()).isNull();
    assertThat(viewModel.etapes()).isEmpty();
  }

  @Test
  @DisplayName("supprimer délègue au service avec l'identifiant du passage courant")
  void supprimer_delegue_au_service() {
    when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.VERIFIE));
    viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

    viewModel.supprimer();

    verify(service).supprimer(ID_PASSAGE);
  }
}
