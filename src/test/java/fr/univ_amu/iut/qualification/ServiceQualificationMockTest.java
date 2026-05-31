package fr.univ_amu.iut.qualification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.qualification.model.GenerateurSelection;
import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import fr.univ_amu.iut.qualification.model.dao.SelectionDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Complément ciblé (Mockito) : isole la règle R13 d'enregistrement du verdict, indépendamment de la
 * base. On vérifie que le verdict est accepté <b>sans consulter l'état d'écoute</b> de la sélection
 * (aucun seuil obligatoire) et que la transition de statut est bien appliquée. Le test réel sur
 * {@code @TempDir} ({@link ServiceQualificationTest}) reste le mode par défaut.
 */
@ExtendWith(MockitoExtension.class)
class ServiceQualificationMockTest {

  @Mock private SelectionDao selectionDao;
  @Mock private SequenceDao sequenceDao;
  @Mock private SessionDao sessionDao;
  @Mock private EnregistrementOriginalDao originalDao;
  @Mock private PassageDao passageDao;
  @Mock private PointDao pointDao;
  @Mock private SiteDao siteDao;
  @Mock private UniteDeTravail uniteDeTravail;

  private ServiceQualification service;

  @BeforeEach
  void preparer() {
    service =
        new ServiceQualification(
            selectionDao,
            sequenceDao,
            sessionDao,
            originalDao,
            passageDao,
            pointDao,
            siteDao,
            new GenerateurSelection(),
            new PreCheckNuit(),
            uniteDeTravail);
  }

  private static Passage passageTransforme(long id) {
    return new Passage(
        id,
        1,
        2026,
        "2026-06-20",
        "20:00:00",
        "06:00:00",
        null,
        StatutWorkflow.TRANSFORME,
        Verdict.A_VERIFIER,
        null,
        null,
        null,
        10L,
        "1925492");
  }

  @Test
  @DisplayName("R13 : le verdict est enregistré sans consulter l'écoute (aucun seuil obligatoire)")
  void verdict_sans_consulter_l_ecoute() {
    when(passageDao.findById(1L)).thenReturn(Optional.of(passageTransforme(1L)));

    service.enregistrerVerdict(1L, Verdict.OK, "RAS");

    verify(passageDao)
        .update(
            argThat(
                p ->
                    p.statutWorkflow() == StatutWorkflow.VERIFIE
                        && p.verdictVerification() == Verdict.OK));
    // R13 : aucune lecture de la jonction / de l'état d'écoute pour décider du verdict.
    verifyNoInteractions(selectionDao, sequenceDao, sessionDao);
  }

  @Test
  @DisplayName("Un verdict À vérifier (sentinelle) est refusé avant toute écriture")
  void verdict_sentinelle_court_circuite_l_ecriture() {
    assertThatThrownBy(() -> service.enregistrerVerdict(1L, Verdict.A_VERIFIER, null))
        .isInstanceOf(IllegalArgumentException.class);

    verify(passageDao, never()).update(any());
  }

  @Test
  @DisplayName("Passage introuvable : le verdict lève une RegleMetierException")
  void verdict_passage_introuvable() {
    when(passageDao.findById(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.enregistrerVerdict(9L, Verdict.OK, null))
        .isInstanceOf(RegleMetierException.class);

    verify(passageDao, never()).update(any());
  }
}
