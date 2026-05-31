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

/// Service métier transverse de la feature `passage` : création d'un passage, vérifications de
/// protocole (R3/R4), pilotage du workflow et pose du verdict. Calqué sur le service de référence
/// `ServiceSites` (cf. SERVICE-CONVENTIONS).
///
/// Principes repris du patron :
///
/// - **Pure Java, sans aucun import JavaFX** : la logique vit en `passage.model`, l'IHM viendra
/// par-dessus (contrôlé par `ArchitectureTest`).
/// - **Reçoit ses dépendances par constructeur** ([PassageDao], [MoteurWorkflowPassage],
/// [Horloge]), assemblées par `PassageModule` en production et instanciées à la main dans les
/// tests.
/// - **Distingue règles soft et dures** : R5 (unicité du quadruplet) et les transitions de
/// workflow interdites lèvent une [RegleMetierException] ; R3 (fenêtre saisonnière) et R4
/// (intervalle < 1 mois) renvoient un [ResultatVerification] d'alertes **non bloquantes**.
/// - **Dates via l'[Horloge] injectée** : aucune `LocalDate.now()` en dur (tests déterministes).
///
/// **Découplage inter-feature assumé.** Les règles R3/R4 ne concernent que les sites en mode
/// [Protocole#STANDARD] (`PointFixeStandard`). Le service **ne résout pas** le protocole en
/// remontant `passage → point → site` : cela créerait une dépendance `passage → sites` alors que
/// `sites → passage` existe déjà (`ServiceSites` lit `PassageDao`), donc un **cycle** que
/// `ArchitectureTest` refuse. Le [Protocole] est donc **passé en paramètre** par l'appelant (le
/// `viewmodel`, qui connaît le site courant) — exactement comme
/// `ServiceSites.rappelsProtocole(Protocole)`.
public class ServicePassage {

  /// Nom du paramètre `passage` (messages `requireNonNull`).
  private static final String PASSAGE = "passage";

  private final PassageDao passageDao;
  private final MoteurWorkflowPassage moteur;
  private final Horloge horloge;

  public ServicePassage(PassageDao passageDao, MoteurWorkflowPassage moteur, Horloge horloge) {
    this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
    this.moteur = Objects.requireNonNull(moteur, "moteur");
    this.horloge = Objects.requireNonNull(horloge, "horloge");
  }

  /// Crée un passage à l'état initial [StatutWorkflow#IMPORTE], sans verdict.
  ///
  /// - R5 (dur) : refuse si le quadruplet `(point, année, n° de passage)` existe déjà —
  /// pré-vérifié via [PassageDao#trouverParPointAnneePassage] (filet : contrainte `UNIQUE` du
  /// schéma).
  /// - Année : déduite de la date d'enregistrement. Si `dateEnregistrement` est `null`, on prend
  /// la date du jour de l'[Horloge] (déterministe en test).
  ///
  /// @param idPoint point d'écoute rattaché (FK `listening_point.id`)
  /// @param idEnregistreur n° de série de l'enregistreur (FK `recorder.serial_number`)
  /// @param numeroPassage n° de passage dans l'année (typiquement 1 ou 2)
  /// @param dateEnregistrement date du soir d'enregistrement, ou `null` pour « aujourd'hui »
  /// @return le passage inséré, avec son `id` auto-généré
  /// @throws RegleMetierException si le quadruplet existe déjà (R5)
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

  /// Vérifications de protocole non bloquantes (R3 + R4) à présenter à l'utilisateur après saisie
  /// d'un passage. Accumule les alertes des deux règles dans un seul [ResultatVerification]
  /// (patron d'accumulation immuable et fluente, cf. SERVICE-CONVENTIONS §2.3).
  ///
  /// Sur un site [Protocole#RECHERCHE], les deux règles sont muettes : le résultat est conforme.
  public ResultatVerification verifierProtocole(Passage passage, Protocole protocole) {
    ResultatVerification resultat = verifierFenetreSaisonniere(passage, protocole);
    for (Alerte alerte : verifierIntervalleEntrePassages(passage, protocole).alertes()) {
      resultat = resultat.avec(alerte);
    }
    return resultat;
  }

  /// R3 (soft, `PointFixeStandard` uniquement) : le passage 1 est attendu entre le 15 juin et le
  /// 31 juillet, le passage 2 entre le 15 août et le 30 septembre. Hors fenêtre → alerte non
  /// bloquante. Sur [Protocole#RECHERCHE], ou pour un n° de passage sans fenêtre définie (autre
  /// que 1 ou 2), la règle est muette.
  public ResultatVerification verifierFenetreSaisonniere(Passage passage, Protocole protocole) {
    Objects.requireNonNull(passage, PASSAGE);
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

  /// R4 (soft, `PointFixeStandard` uniquement) : l'intervalle conseillé entre les deux passages
  /// d'un même point dans la même année est d'au moins 1 mois. Si un autre passage du même point
  /// (même année, n° différent) est à moins d'un mois, une alerte non bloquante est émise.
  ///
  /// Granularité : la règle est évaluée **par point d'écoute** (et non par site). C'est la maille
  /// atteignable depuis la feature `passage` sans dépendre de `sites` (cf. la note de découplage
  /// de cette classe) ; un passage appartenant à exactement un point, comparer ses frères de point
  /// est une lecture fidèle de la règle. Sur [Protocole#RECHERCHE], muette.
  public ResultatVerification verifierIntervalleEntrePassages(
      Passage passage, Protocole protocole) {
    Objects.requireNonNull(passage, PASSAGE);
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

  /// Fait avancer un passage à l'étape suivante du workflow (cf. [MoteurWorkflowPassage]).
  ///
  /// @throws RegleMetierException si le passage est déjà au statut terminal
  /// ([StatutWorkflow#DEPOSE])
  public Passage avancerStatut(Passage passage) {
    Objects.requireNonNull(passage, PASSAGE);
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

  /// Applique une transition de workflow explicite après l'avoir validée.
  ///
  /// Le passage à [StatutWorkflow#DEPOSE] horodate automatiquement `deposeLe` via l'[Horloge]
  /// (`maintenant()`, déterministe en test).
  ///
  /// @return le passage mis à jour (persisté)
  /// @throws RegleMetierException si la transition n'est pas le passage à l'étape suivante
  public Passage changerStatut(Passage passage, StatutWorkflow nouveauStatut) {
    Objects.requireNonNull(passage, PASSAGE);
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

  /// Pose (ou met à jour) le verdict de vérification d'un passage (R13 : verdict `À vérifier` /
  /// `OK` / `Douteux` / `À jeter`, saisi par l'utilisateur après écoute).
  ///
  /// Invariant dur : un passage déjà [StatutWorkflow#DEPOSE] ne peut plus être re-jugé (son
  /// verdict est figé une fois déposé sur Vigie-Chiro).
  ///
  /// @return le passage mis à jour (persisté)
  /// @throws RegleMetierException si le passage est déjà déposé
  public Passage poserVerdict(Passage passage, Verdict verdict) {
    Objects.requireNonNull(passage, PASSAGE);
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

  /// Vrai si les deux dates sont distantes de strictement moins d'un mois calendaire (R4).
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

  /// Fenêtre saisonnière fermée [debut, fin] pour la vérification R3.
  private record Fenetre(LocalDate debut, LocalDate fin) {
    boolean contient(LocalDate date) {
      return !date.isBefore(debut) && !date.isAfter(fin);
    }
  }
}
