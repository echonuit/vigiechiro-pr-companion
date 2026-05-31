package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Alerte;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.ResultatVerification;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Service métier transverse de la feature {@code passage} : création d'un passage, vérifications de
 * protocole (R3/R4), pilotage du workflow et pose du verdict. Calqué sur le service de référence
 * {@code ServiceSites} (cf. SERVICE-CONVENTIONS).
 *
 * <p>Principes repris du patron :
 *
 * <ul>
 *   <li><b>Pure Java, sans aucun import JavaFX</b> : la logique vit en {@code passage.model}, l'IHM
 *       viendra par-dessus (contrôlé par {@code ArchitectureTest}).
 *   <li><b>Reçoit ses dépendances par constructeur</b> ({@link PassageDao}, {@link
 *       MoteurWorkflowPassage}, {@link Horloge}), assemblées par {@code PassageModule} en
 *       production et instanciées à la main dans les tests.
 *   <li><b>Distingue règles soft et dures</b> : R5 (unicité du quadruplet) et les transitions de
 *       workflow interdites lèvent une {@link RegleMetierException} ; R3 (fenêtre saisonnière) et
 *       R4 (intervalle &lt; 1 mois) renvoient un {@link ResultatVerification} d'alertes <b>non
 *       bloquantes</b>.
 *   <li><b>Dates via l'{@link Horloge} injectée</b> : aucune {@code LocalDate.now()} en dur (tests
 *       déterministes).
 * </ul>
 *
 * <p><b>Découplage inter-feature assumé.</b> Les règles R3/R4 ne concernent que les sites en mode
 * {@link Protocole#STANDARD} ({@code PointFixeStandard}). Le service <b>ne résout pas</b> le
 * protocole en remontant {@code passage → point → site} : cela créerait une dépendance {@code
 * passage → sites} alors que {@code sites → passage} existe déjà ({@code ServiceSites} lit {@code
 * PassageDao}), donc un <b>cycle</b> que {@code ArchitectureTest} refuse. Le {@link Protocole} est
 * donc <b>passé en paramètre</b> par l'appelant (le {@code viewmodel}, qui connaît le site courant)
 * — exactement comme {@code ServiceSites.rappelsProtocole(Protocole)}.
 */
public class ServicePassage {

  private final PassageDao passageDao;
  private final MoteurWorkflowPassage moteur;
  private final Horloge horloge;

  public ServicePassage(PassageDao passageDao, MoteurWorkflowPassage moteur, Horloge horloge) {
    this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
    this.moteur = Objects.requireNonNull(moteur, "moteur");
    this.horloge = Objects.requireNonNull(horloge, "horloge");
  }

  /**
   * Crée un passage à l'état initial {@link StatutWorkflow#IMPORTE}, sans verdict.
   *
   * <ul>
   *   <li>R5 (dur) : refuse si le quadruplet {@code (point, année, n° de passage)} existe déjà —
   *       pré-vérifié via {@link PassageDao#trouverParPointAnneePassage} (filet : contrainte {@code
   *       UNIQUE} du schéma).
   *   <li>Année : déduite de la date d'enregistrement. Si {@code dateEnregistrement} est {@code
   *       null}, on prend la date du jour de l'{@link Horloge} (déterministe en test).
   * </ul>
   *
   * @param idPoint point d'écoute rattaché (FK {@code listening_point.id})
   * @param idEnregistreur n° de série de l'enregistreur (FK {@code recorder.serial_number})
   * @param numeroPassage n° de passage dans l'année (typiquement 1 ou 2)
   * @param dateEnregistrement date du soir d'enregistrement, ou {@code null} pour « aujourd'hui »
   * @return le passage inséré, avec son {@code id} auto-généré
   * @throws RegleMetierException si le quadruplet existe déjà (R5)
   */
  public Passage creerPassage(
      Long idPoint,
      String idEnregistreur,
      int numeroPassage,
      LocalDate dateEnregistrement,
      String heureDebut,
      String heureFin,
      String parametresAcquisition,
      String commentaire,
      String donneesMeteo) {
    Objects.requireNonNull(idPoint, "idPoint");
    LocalDate date = dateEnregistrement != null ? dateEnregistrement : horloge.aujourdhui();
    int annee = date.getYear();
    exigerQuadrupletUnique(idPoint, annee, numeroPassage); // R5
    Passage aCreer =
        new Passage(
            null,
            numeroPassage,
            annee,
            date.toString(),
            heureDebut,
            heureFin,
            parametresAcquisition,
            StatutWorkflow.IMPORTE,
            null,
            commentaire,
            donneesMeteo,
            null,
            idPoint,
            idEnregistreur);
    return passageDao.insert(aCreer);
  }

  /**
   * Vérifications de protocole non bloquantes (R3 + R4) à présenter à l'utilisateur après saisie
   * d'un passage. Accumule les alertes des deux règles dans un seul {@link ResultatVerification}
   * (patron d'accumulation immuable et fluente, cf. SERVICE-CONVENTIONS §2.3).
   *
   * <p>Sur un site {@link Protocole#RECHERCHE}, les deux règles sont muettes : le résultat est
   * conforme.
   */
  public ResultatVerification verifierProtocole(Passage passage, Protocole protocole) {
    ResultatVerification resultat = verifierFenetreSaisonniere(passage, protocole);
    for (Alerte alerte : verifierIntervalleEntrePassages(passage, protocole).alertes()) {
      resultat = resultat.avec(alerte);
    }
    return resultat;
  }

  /**
   * R3 (soft, {@code PointFixeStandard} uniquement) : le passage 1 est attendu entre le 15 juin et
   * le 31 juillet, le passage 2 entre le 15 août et le 30 septembre. Hors fenêtre → alerte non
   * bloquante. Sur {@link Protocole#RECHERCHE}, ou pour un n° de passage sans fenêtre définie
   * (autre que 1 ou 2), la règle est muette.
   */
  public ResultatVerification verifierFenetreSaisonniere(Passage passage, Protocole protocole) {
    Objects.requireNonNull(passage, "passage");
    if (protocole != Protocole.STANDARD || passage.dateEnregistrement() == null) {
      return ResultatVerification.ok();
    }
    Optional<Fenetre> fenetre = fenetrePour(passage.numeroPassage(), passage.annee());
    if (fenetre.isEmpty()) {
      return ResultatVerification.ok();
    }
    LocalDate date = LocalDate.parse(passage.dateEnregistrement());
    if (fenetre.get().contient(date)) {
      return ResultatVerification.ok();
    }
    return ResultatVerification.de(
        Alerte.soft(
            "R3 : le passage n°"
                + passage.numeroPassage()
                + " du "
                + date
                + " est hors de la fenêtre attendue ["
                + fenetre.get().debut()
                + " → "
                + fenetre.get().fin()
                + "] pour un site PointFixeStandard. Alerte non bloquante."));
  }

  /**
   * R4 (soft, {@code PointFixeStandard} uniquement) : l'intervalle conseillé entre les deux
   * passages d'un même point dans la même année est d'au moins 1 mois. Si un autre passage du même
   * point (même année, n° différent) est à moins d'un mois, une alerte non bloquante est émise.
   *
   * <p>Granularité : la règle est évaluée <b>par point d'écoute</b> (et non par site). C'est la
   * maille atteignable depuis la feature {@code passage} sans dépendre de {@code sites} (cf. la
   * note de découplage de cette classe) ; un passage appartenant à exactement un point, comparer
   * ses frères de point est une lecture fidèle de la règle. Sur {@link Protocole#RECHERCHE},
   * muette.
   */
  public ResultatVerification verifierIntervalleEntrePassages(
      Passage passage, Protocole protocole) {
    Objects.requireNonNull(passage, "passage");
    if (protocole != Protocole.STANDARD || passage.dateEnregistrement() == null) {
      return ResultatVerification.ok();
    }
    LocalDate dateCourante = LocalDate.parse(passage.dateEnregistrement());
    ResultatVerification resultat = ResultatVerification.ok();
    for (Passage autre : passageDao.findByPoint(passage.idPoint())) {
      if (estLeMemePassage(autre, passage)
          || autre.numeroPassage() == passage.numeroPassage()
          || autre.annee() != passage.annee()
          || autre.dateEnregistrement() == null) {
        continue;
      }
      LocalDate dateAutre = LocalDate.parse(autre.dateEnregistrement());
      if (intervalleInferieurAUnMois(dateCourante, dateAutre)) {
        resultat =
            resultat.avec(
                Alerte.soft(
                    "R4 : moins d'un mois entre le passage n°"
                        + passage.numeroPassage()
                        + " ("
                        + dateCourante
                        + ") et le passage n°"
                        + autre.numeroPassage()
                        + " ("
                        + dateAutre
                        + ") sur ce point. Intervalle conseillé ≥ 1 mois. Alerte non bloquante."));
      }
    }
    return resultat;
  }

  /**
   * Fait avancer un passage à l'étape suivante du workflow (cf. {@link MoteurWorkflowPassage}).
   *
   * @throws RegleMetierException si le passage est déjà au statut terminal ({@link
   *     StatutWorkflow#DEPOSE})
   */
  public Passage avancerStatut(Passage passage) {
    Objects.requireNonNull(passage, "passage");
    StatutWorkflow suivant =
        moteur
            .suivant(passage.statutWorkflow())
            .orElseThrow(
                () ->
                    new RegleMetierException(
                        "Le passage est déjà au statut terminal « "
                            + passage.statutWorkflow().libelle()
                            + " » : aucune transition possible."));
    return changerStatut(passage, suivant);
  }

  /**
   * Applique une transition de workflow explicite après l'avoir validée.
   *
   * <p>Le passage à {@link StatutWorkflow#DEPOSE} horodate automatiquement {@code deposeLe} via
   * l'{@link Horloge} ({@code maintenant()}, déterministe en test).
   *
   * @return le passage mis à jour (persisté)
   * @throws RegleMetierException si la transition n'est pas le passage à l'étape suivante
   */
  public Passage changerStatut(Passage passage, StatutWorkflow nouveauStatut) {
    Objects.requireNonNull(passage, "passage");
    Objects.requireNonNull(nouveauStatut, "nouveauStatut");
    moteur.exigerTransitionAutorisee(passage.statutWorkflow(), nouveauStatut);
    String deposeLe =
        nouveauStatut == StatutWorkflow.DEPOSE
            ? horloge.maintenant().toString()
            : passage.deposeLe();
    Passage misAJour =
        new Passage(
            passage.id(),
            passage.numeroPassage(),
            passage.annee(),
            passage.dateEnregistrement(),
            passage.heureDebut(),
            passage.heureFin(),
            passage.parametresAcquisition(),
            nouveauStatut,
            passage.verdictVerification(),
            passage.commentaire(),
            passage.donneesMeteo(),
            deposeLe,
            passage.idPoint(),
            passage.idEnregistreur());
    passageDao.update(misAJour);
    return misAJour;
  }

  /**
   * Pose (ou met à jour) le verdict de vérification d'un passage (R13 : verdict {@code À vérifier}
   * / {@code OK} / {@code Douteux} / {@code À jeter}, saisi par l'utilisateur après écoute).
   *
   * <p>Invariant dur : un passage déjà {@link StatutWorkflow#DEPOSE} ne peut plus être re-jugé (son
   * verdict est figé une fois déposé sur Vigie-Chiro).
   *
   * @return le passage mis à jour (persisté)
   * @throws RegleMetierException si le passage est déjà déposé
   */
  public Passage poserVerdict(Passage passage, Verdict verdict) {
    Objects.requireNonNull(passage, "passage");
    Objects.requireNonNull(verdict, "verdict");
    if (passage.statutWorkflow() == StatutWorkflow.DEPOSE) {
      throw new RegleMetierException(
          "Verdict figé : un passage déposé ne peut plus changer de verdict de vérification.");
    }
    Passage misAJour =
        new Passage(
            passage.id(),
            passage.numeroPassage(),
            passage.annee(),
            passage.dateEnregistrement(),
            passage.heureDebut(),
            passage.heureFin(),
            passage.parametresAcquisition(),
            passage.statutWorkflow(),
            verdict,
            passage.commentaire(),
            passage.donneesMeteo(),
            passage.deposeLe(),
            passage.idPoint(),
            passage.idEnregistreur());
    passageDao.update(misAJour);
    return misAJour;
  }

  private void exigerQuadrupletUnique(Long idPoint, int annee, int numeroPassage) {
    if (passageDao.trouverParPointAnneePassage(idPoint, annee, numeroPassage).isPresent()) {
      throw new RegleMetierException(
          "R5 : un passage n°"
              + numeroPassage
              + " existe déjà pour ce point en "
              + annee
              + " (le quadruplet point/année/n° de passage doit être unique).");
    }
  }

  private static boolean estLeMemePassage(Passage a, Passage b) {
    return a.id() != null && a.id().equals(b.id());
  }

  /** Vrai si les deux dates sont distantes de strictement moins d'un mois calendaire (R4). */
  private static boolean intervalleInferieurAUnMois(LocalDate a, LocalDate b) {
    LocalDate plusTot = a.isAfter(b) ? b : a;
    LocalDate plusTard = a.isAfter(b) ? a : b;
    return plusTot.plusMonths(1).isAfter(plusTard);
  }

  private static Optional<Fenetre> fenetrePour(int numeroPassage, int annee) {
    return switch (numeroPassage) {
      case 1 -> Optional.of(new Fenetre(LocalDate.of(annee, 6, 15), LocalDate.of(annee, 7, 31)));
      case 2 -> Optional.of(new Fenetre(LocalDate.of(annee, 8, 15), LocalDate.of(annee, 9, 30)));
      default -> Optional.empty();
    };
  }

  /** Fenêtre saisonnière fermée [debut, fin] pour la vérification R3. */
  private record Fenetre(LocalDate debut, LocalDate fin) {
    boolean contient(LocalDate date) {
      return !date.isBefore(debut) && !date.isAfter(fin);
    }
  }
}
