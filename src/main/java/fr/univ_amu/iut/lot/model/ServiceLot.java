package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.ResultatVerification;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.util.List;
import java.util.Objects;

/**
 * Service métier de la feature {@code lot} : prépare et trace le dépôt d'un passage sur Vigie-Chiro
 * (parcours P4, épopée E4). Suit le patron du service de référence {@code ServiceSites} : pure Java
 * testable, dépendances reçues par constructeur, distinction règles soft / règles dures.
 *
 * <p>Deux étapes du parcours P4 :
 *
 * <ul>
 *   <li>{@link #preparerLot(Long)} (E4.S1 + E4.S2) : contrôle R14 + cohérence ({@link
 *       VerificationCoherence}), assemble le récapitulatif ({@link Lot}) et fait passer le passage
 *       à {@link StatutWorkflow#PRET_A_DEPOSER}. Refuse (exception) tout passage « À jeter » (R14)
 *       ou incohérent.
 *   <li>{@link #marquerDepose(Long)} (E4.S3) : une fois le téléversement manuel effectué, marque le
 *       passage {@link StatutWorkflow#DEPOSE} et horodate {@code deposited_at} via l'{@link
 *       Horloge} injectée (déterministe en test).
 * </ul>
 *
 * <p>Les transitions de statut sont déléguées au {@link MoteurWorkflowPassage} (engin pur de la
 * feature {@code passage}) : on ne réécrit pas la progression linéaire et on garantit qu'un dépôt
 * suit bien une préparation (pas de saut d'étape ni de retour arrière).
 *
 * <p><b>Aucun accès réseau</b> : l'application ne dialogue jamais avec le portail Vigie-Chiro, le
 * dépôt est manuel (le service se contente de préparer le dossier et de tracer la date déclarée).
 */
public class ServiceLot {

  private final PassageDao passageDao;
  private final SessionDao sessionDao;
  private final SequenceDao sequenceDao;
  private final VerificationCoherence verification;
  private final MoteurWorkflowPassage moteurWorkflow;
  private final Horloge horloge;

  public ServiceLot(
      PassageDao passageDao,
      SessionDao sessionDao,
      SequenceDao sequenceDao,
      VerificationCoherence verification,
      MoteurWorkflowPassage moteurWorkflow,
      Horloge horloge) {
    this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
    this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
    this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
    this.verification = Objects.requireNonNull(verification, "verification");
    this.moteurWorkflow = Objects.requireNonNull(moteurWorkflow, "moteurWorkflow");
    this.horloge = Objects.requireNonNull(horloge, "horloge");
  }

  /**
   * Prépare le lot à déposer d'un passage (E4.S1 + E4.S2).
   *
   * <ul>
   *   <li>R14 (dur, bloquant) : un passage au verdict {@link Verdict#A_JETER} est refusé d'emblée.
   *   <li>Cohérence (dur) : si au moins un contrôle de {@link VerificationCoherence} est bloquant
   *       (transformation incomplète, préfixe non conforme, journal absent…), la préparation est
   *       refusée. Les alertes <i>soft</i> (relevé absent, R20) ne bloquent pas.
   *   <li>Transition : le passage passe à {@link StatutWorkflow#PRET_A_DEPOSER}.
   * </ul>
   *
   * @return le récapitulatif du lot (séquences, volume, chemin du dossier prêt)
   * @throws RegleMetierException si le passage est introuvable, « À jeter » (R14) ou incohérent
   */
  public Lot preparerLot(Long idPassage) {
    Passage passage = chargerPassage(idPassage);
    exigerNonAJeter(passage); // R14

    ResultatVerification coherence = verification.verifier(passage);
    if (coherence.estBloquant()) {
      throw new RegleMetierException(
          "Préparation du lot impossible : "
              + String.join(
                  " ", coherence.alertesBloquantes().stream().map(a -> a.message()).toList()));
    }

    SessionDEnregistrement session =
        sessionDao
            .trouverParPassage(idPassage)
            .orElseThrow(
                () ->
                    new RegleMetierException(
                        "Session d'enregistrement introuvable pour le passage " + idPassage + "."));
    List<SequenceDEcoute> sequences = sequenceDao.findBySession(session.id());

    // Transition de statut déléguée au moteur de workflow du passage (Vérifié → Prêt à déposer).
    moteurWorkflow.exigerTransitionAutorisee(
        passage.statutWorkflow(), StatutWorkflow.PRET_A_DEPOSER);
    passageDao.update(
        avecStatutEtDepot(passage, StatutWorkflow.PRET_A_DEPOSER, passage.deposeLe()));

    return new Lot(idPassage, session.cheminRacine(), sequences, session.volumeSequencesOctets());
  }

  /**
   * Marque un passage comme déposé après téléversement manuel (E4.S3) : statut {@link
   * StatutWorkflow#DEPOSE} et horodatage {@code deposited_at} lu de l'{@link Horloge}.
   *
   * @return le passage mis à jour (statut déposé, date posée)
   * @throws RegleMetierException si le passage est introuvable ou « À jeter » (R14)
   */
  public Passage marquerDepose(Long idPassage) {
    Passage passage = chargerPassage(idPassage);
    exigerNonAJeter(passage); // R14 : un « À jeter » ne peut jamais être déposé.

    // Transition de statut déléguée au moteur de workflow (Prêt à déposer → Déposé) : impose donc
    // qu'un lot ait d'abord été préparé.
    moteurWorkflow.exigerTransitionAutorisee(passage.statutWorkflow(), StatutWorkflow.DEPOSE);
    Passage depose =
        avecStatutEtDepot(passage, StatutWorkflow.DEPOSE, horloge.maintenant().toString());
    passageDao.update(depose);
    return depose;
  }

  private Passage chargerPassage(Long idPassage) {
    return passageDao
        .findById(idPassage)
        .orElseThrow(() -> new RegleMetierException("Passage introuvable : " + idPassage));
  }

  private static void exigerNonAJeter(Passage passage) {
    if (passage.verdictVerification() == Verdict.A_JETER) {
      throw new RegleMetierException(
          "R14 : le passage n°"
              + passage.numeroPassage()
              + " ("
              + passage.annee()
              + ") porte le verdict « À jeter » et ne peut pas rejoindre un lot prêt à déposer.");
    }
  }

  /** Reconstruit le passage (record immuable) avec un nouveau statut et une date de dépôt. */
  private static Passage avecStatutEtDepot(
      Passage passage, StatutWorkflow statut, String deposeLe) {
    return new Passage(
        passage.id(),
        passage.numeroPassage(),
        passage.annee(),
        passage.dateEnregistrement(),
        passage.heureDebut(),
        passage.heureFin(),
        passage.parametresAcquisition(),
        statut,
        passage.verdictVerification(),
        passage.commentaire(),
        passage.donneesMeteo(),
        deposeLe,
        passage.idPoint(),
        passage.idEnregistreur());
  }
}
