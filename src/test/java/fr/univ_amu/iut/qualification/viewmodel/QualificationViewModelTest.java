package fr.univ_amu.iut.qualification.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import fr.univ_amu.iut.qualification.model.PreCheckNuit.Feu;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires du noyau de [QualificationViewModel] (pré-check + verdict différé). Le
/// [ServiceQualification] est mocké (Mockito) : on vérifie le mapping des 3 feux, l'activation du
/// bouton selon le verdict choisi, l'enregistrement (état `ENREGISTRE`), l'avertissement R14 et la
/// restitution d'un passage introuvable, sans base de données.
@ExtendWith(MockitoExtension.class)
class QualificationViewModelTest {

  private static final long ID_PASSAGE = 42L;

  @Mock private ServiceQualification service;
  private QualificationViewModel viewModel;

  @BeforeEach
  void preparer() {
    viewModel = new QualificationViewModel(service);
  }

  @Test
  @DisplayName("ouvrirSur mappe le pré-check en 3 feux + indicateur d'anomalie")
  void ouvrir_mappe_le_precheck() {
    when(service.precheck(ID_PASSAGE))
        .thenReturn(new PreCheckNuit.Diagnostic(Feu.VERT, Feu.ORANGE, Feu.ROUGE));

    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.feuCouvertureProperty().get()).isEqualTo(Feu.VERT);
    assertThat(viewModel.feuNombreProperty().get()).isEqualTo(Feu.ORANGE);
    assertThat(viewModel.feuRenommageProperty().get()).isEqualTo(Feu.ROUGE);
    assertThat(viewModel.preCheckAnomalieProperty().get()).isTrue();
    assertThat(viewModel.messageProperty().get()).isEmpty();
  }

  @Test
  @DisplayName("Sans verdict décisif, l'enregistrement est refusé sans toucher au service")
  void verdict_manquant_refuse() {
    assertThat(viewModel.peutEnregistrer().get()).isFalse();
    viewModel.choisirVerdict(Verdict.A_VERIFIER);
    assertThat(viewModel.peutEnregistrer().get()).isFalse();

    viewModel.enregistrer();

    assertThat(viewModel.messageProperty().get()).contains("verdict");
    verify(service, never()).enregistrerVerdict(any(), any(), any());
  }

  @Test
  @DisplayName("Enregistrer un verdict OK persiste et passe l'état à ENREGISTRE")
  void enregistrer_ok() {
    when(service.precheck(ID_PASSAGE))
        .thenReturn(new PreCheckNuit.Diagnostic(Feu.VERT, Feu.VERT, Feu.VERT));
    when(service.estAJeter(ID_PASSAGE)).thenReturn(false);
    viewModel.ouvrirSur(ID_PASSAGE);
    viewModel.commentaireProperty().set("Beaux contacts de pipistrelle.");
    viewModel.choisirVerdict(Verdict.OK);

    assertThat(viewModel.peutEnregistrer().get()).isTrue();
    viewModel.enregistrer();

    verify(service).enregistrerVerdict(ID_PASSAGE, Verdict.OK, "Beaux contacts de pipistrelle.");
    assertThat(viewModel.etatVerdictProperty().get()).isEqualTo(EtatVerdict.ENREGISTRE);
    assertThat(viewModel.avertissementAJeterProperty().get()).isEmpty();
    assertThat(viewModel.messageProperty().get()).isEmpty();
  }

  @Test
  @DisplayName("Un verdict « à jeter » enregistré déclenche l'avertissement R14")
  void verdict_a_jeter_avertit() {
    when(service.precheck(ID_PASSAGE))
        .thenReturn(new PreCheckNuit.Diagnostic(Feu.VERT, Feu.VERT, Feu.VERT));
    when(service.estAJeter(ID_PASSAGE)).thenReturn(true);
    viewModel.ouvrirSur(ID_PASSAGE);
    viewModel.choisirVerdict(Verdict.A_JETER);

    viewModel.enregistrer();

    assertThat(viewModel.etatVerdictProperty().get()).isEqualTo(EtatVerdict.ENREGISTRE);
    assertThat(viewModel.avertissementAJeterProperty().get()).contains("à jeter");
  }

  @Test
  @DisplayName("Un commentaire vide est transmis comme null (commentaire existant conservé)")
  void commentaire_vide_devient_null() {
    when(service.precheck(ID_PASSAGE))
        .thenReturn(new PreCheckNuit.Diagnostic(Feu.VERT, Feu.VERT, Feu.VERT));
    when(service.estAJeter(ID_PASSAGE)).thenReturn(false);
    viewModel.ouvrirSur(ID_PASSAGE);
    viewModel.choisirVerdict(Verdict.DOUTEUX);

    viewModel.enregistrer();

    verify(service).enregistrerVerdict(eq(ID_PASSAGE), eq(Verdict.DOUTEUX), isNull());
  }

  @Test
  @DisplayName("Un passage introuvable à l'ouverture est restitué dans le message")
  void passage_introuvable() {
    when(service.precheck(ID_PASSAGE))
        .thenThrow(new RegleMetierException("Passage introuvable : 42"));

    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.messageProperty().get()).contains("introuvable");
    assertThat(viewModel.feuCouvertureProperty().get()).isNull();
  }
}
