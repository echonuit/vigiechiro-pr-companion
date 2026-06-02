package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.validation.model.ExportVuCsv;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ParserCsvTadarida;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Complément ciblé (Mockito) : isole la **logique de décision** du
/// [ServiceValidation] indépendamment de la base (cf. `ServiceSitesMockTest`). On mocke les DAO
/// pour vérifier le statut dérivé (R15/R16/R17), le comportement de
/// [ServiceValidation#valider(Long)] (R24) et le refus dur de
/// [ServiceValidation#corriger(Long, String, Double)] sur taxon inconnu.
@ExtendWith(MockitoExtension.class)
class ServiceValidationMockTest {

  @Mock ResultatsIdentificationDao resultatsDao;
  @Mock ObservationDao observationDao;
  @Mock TaxonDao taxonDao;
  @Mock SessionDao sessionDao;
  @Mock SequenceDao sequenceDao;
  @Mock UniteDeTravail uniteDeTravail;

  private ServiceValidation service() {
    return new ServiceValidation(
        resultatsDao,
        observationDao,
        taxonDao,
        sessionDao,
        sequenceDao,
        new ParserCsvTadarida(),
        new ExportVuCsv(),
        uniteDeTravail,
        new HorlogeFigee(LocalDate.of(2026, 5, 31)));
  }

  private static Observation observation(String taxonTadarida, String observateur, Double probObs) {
    return new Observation(
        1L,
        10L,
        0.0,
        5.0,
        45,
        taxonTadarida,
        0.8,
        null,
        observateur,
        probObs,
        null,
        false,
        ModeValidation.NON_VALIDE,
        100L);
  }

  @Test
  @DisplayName("statut : pas de taxon observateur → NON_TOUCHEE (R17)")
  void statut_non_touchee() {
    assertThat(service().statut(observation("Pippip", null, null)))
        .isEqualTo(StatutObservation.NON_TOUCHEE);
  }

  @Test
  @DisplayName("statut : taxon observateur = Tadarida et prob renseignée → VALIDEE (R15)")
  void statut_validee() {
    assertThat(service().statut(observation("Pippip", "Pippip", 0.9)))
        .isEqualTo(StatutObservation.VALIDEE);
  }

  @Test
  @DisplayName("statut : taxon observateur différent → CORRIGEE (R16)")
  void statut_corrigee() {
    assertThat(service().statut(observation("noise", "Pippip", 0.9)))
        .isEqualTo(StatutObservation.CORRIGEE);
  }

  @Test
  @DisplayName("valider : recopie le taxon Tadarida, mode manuel (R24), une seule écriture")
  void valider_recopie_le_taxon_tadarida() {
    when(observationDao.findById(1L)).thenReturn(Optional.of(observation("Pippip", null, null)));

    Observation validee = service().valider(1L);

    assertThat(validee.taxonObservateur()).isEqualTo("Pippip");
    assertThat(validee.probObservateur()).isEqualTo(0.8); // reprend la prob Tadarida
    assertThat(validee.modeValidation()).isEqualTo(ModeValidation.MANUEL);
    verify(observationDao).update(any(Observation.class));
  }

  @Test
  @DisplayName("corriger : un taxon observateur inconnu est refusé sans écriture")
  void corriger_taxon_inconnu_court_circuite_l_ecriture() {
    when(observationDao.findById(1L)).thenReturn(Optional.of(observation("noise", null, null)));
    when(taxonDao.findById("ZZZZZZ")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service().corriger(1L, "ZZZZZZ", 0.5))
        .isInstanceOf(RegleMetierException.class);
    verify(observationDao, never()).update(any(Observation.class));
  }

  @Test
  @DisplayName("corriger : un taxon connu et différent met à jour l'observateur")
  void corriger_taxon_connu() {
    when(observationDao.findById(1L)).thenReturn(Optional.of(observation("noise", null, null)));
    when(taxonDao.findById("Pippip"))
        .thenReturn(Optional.of(new Taxon("Pippip", "Pipistrellus pipistrellus", null, 1L)));

    Observation corrigee = service().corriger(1L, "Pippip", 0.99);

    assertThat(corrigee.taxonObservateur()).isEqualTo("Pippip");
    assertThat(service().statut(corrigee)).isEqualTo(StatutObservation.CORRIGEE);
    verify(observationDao).update(any(Observation.class));
  }
}
