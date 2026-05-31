package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.model.Alerte;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.ResultatVerification;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// Moteur de vérification de cohérence d'un passage avant préparation d'un lot (parcours
/// P4, story E4.S1). Rejoue tous les contrôles affichés dans la maquette M-Lot et les
/// restitue sous forme d'un [ResultatVerification] (cumul d'alertes) que l'IHM utilise pour
/// afficher chaque ligne ✓/✗ et pour activer/désactiver le bouton de dépôt.
///
/// Contrôles, dans l'ordre des règles métier :
///
/// - **R14** (bloquant) : un passage au verdict [Verdict#A_JETER] ne peut pas rejoindre un
///   lot. Restitué ici comme alerte bloquante pour l'affichage ; le refus dur est levé par
///   [ServiceLot].
/// - **Transformation** (bloquant, R10) : une session existe, des séquences d'écoute sont
///   présentes et **chaque** enregistrement original a été transformé en au moins une
///   séquence.
/// - **Préfixe** (bloquant, R6/R7/R8) : tous les enregistrements originaux et toutes les
///   séquences portent le préfixe attendu `Car<carré>-<année>-Pass<n>-<point>-`.
/// - **Journal du capteur** (bloquant) : un `sensor_log` accompagne la session.
/// - **Relevé climatique** (soft, R20) : son absence est *signalée sans bloquer* (sonde non
///   installée ou défaillante) ; le dépôt reste possible.
///
/// Pure logique métier (aucun import JavaFX). Reçoit ses DAO par constructeur, à la manière
/// du service de référence `ServiceSites`. Dépendances inter-feature en lecture seule
/// autorisées : `lot → passage` (séquences, originaux, journal, relevé, session) et
/// `lot → sites` (point et site, pour calculer le préfixe attendu) ; le graphe reste
/// acyclique.
public class VerificationCoherence {

  private final SiteDao siteDao;
  private final PointDao pointDao;
  private final SessionDao sessionDao;
  private final EnregistrementOriginalDao originalDao;
  private final SequenceDao sequenceDao;
  private final JournalDuCapteurDao journalDao;
  private final ReleveClimatiqueDao releveDao;

  public VerificationCoherence(
      SiteDao siteDao,
      PointDao pointDao,
      SessionDao sessionDao,
      EnregistrementOriginalDao originalDao,
      SequenceDao sequenceDao,
      JournalDuCapteurDao journalDao,
      ReleveClimatiqueDao releveDao) {
    this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
    this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
    this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
    this.originalDao = Objects.requireNonNull(originalDao, "originalDao");
    this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
    this.journalDao = Objects.requireNonNull(journalDao, "journalDao");
    this.releveDao = Objects.requireNonNull(releveDao, "releveDao");
  }

  /// Vérifie qu'un passage est prêt à déposer et renvoie le cumul d'alertes (vide si tout
  /// est conforme). `estBloquant()` vaut `true` dès qu'au moins un contrôle dur échoue :
  /// dans ce cas, l'IHM désactive le bouton de dépôt et [ServiceLot] refuse la préparation.
  ///
  /// @param passage le passage à contrôler (avec son `id` persisté)
  public ResultatVerification verifier(Passage passage) {
    Objects.requireNonNull(passage, "passage");
    ResultatVerification resultat = ResultatVerification.ok();

    // R14 : verdict « À jeter » bloquant (restitué pour l'affichage ; refus dur côté ServiceLot).
    if (passage.verdictVerification() == Verdict.A_JETER) {
      resultat =
          resultat.avec(
              Alerte.bloquante(
                  "R14 : ce passage porte le verdict « À jeter » et ne peut pas être inclus dans un"
                      + " lot prêt à déposer."));
    }

    Optional<SessionDEnregistrement> sessionOpt = sessionDao.trouverParPassage(passage.id());
    if (sessionOpt.isEmpty()) {
      // Sans session, aucun des contrôles suivants n'est calculable : on s'arrête là.
      return resultat.avec(
          Alerte.bloquante(
              "Aucune session d'enregistrement n'est rattachée à ce passage : importez et"
                  + " transformez la nuit avant de préparer le lot."));
    }
    SessionDEnregistrement session = sessionOpt.get();
    List<EnregistrementOriginal> originaux = originalDao.findBySession(session.id());
    List<SequenceDEcoute> sequences = sequenceDao.findBySession(session.id());

    resultat = verifierTransformation(resultat, originaux, sequences);
    resultat = verifierPrefixe(resultat, passage, originaux, sequences);
    resultat = verifierJournalEtReleve(resultat, session);

    return resultat;
  }

  /// R10 : des séquences existent et chaque original a au moins une séquence dérivée.
  private ResultatVerification verifierTransformation(
      ResultatVerification resultat,
      List<EnregistrementOriginal> originaux,
      List<SequenceDEcoute> sequences) {
    if (sequences.isEmpty()) {
      return resultat.avec(
          Alerte.bloquante(
              "Aucune séquence d'écoute : la transformation des enregistrements originaux (R10)"
                  + " n'a pas été effectuée."));
    }
    Set<Long> originauxTransformes =
        sequences.stream()
            .map(SequenceDEcoute::idEnregistrementOriginal)
            .collect(Collectors.toSet());
    long nonTransformes =
        originaux.stream().filter(o -> !originauxTransformes.contains(o.id())).count();
    if (nonTransformes > 0) {
      return resultat.avec(
          Alerte.bloquante(
              nonTransformes
                  + " enregistrement(s) original(aux) n'ont pas encore été transformé(s) en"
                  + " séquences d'écoute (R10)."));
    }
    return resultat;
  }

  /// R6/R7/R8 : le préfixe attendu est présent sur tous les originaux et toutes les séquences.
  private ResultatVerification verifierPrefixe(
      ResultatVerification resultat,
      Passage passage,
      List<EnregistrementOriginal> originaux,
      List<SequenceDEcoute> sequences) {
    Optional<Prefixe> prefixeOpt = prefixeAttendu(passage);
    if (prefixeOpt.isEmpty()) {
      return resultat.avec(
          Alerte.bloquante(
              "Impossible de calculer le préfixe attendu : le point d'écoute ou le site du passage"
                  + " est introuvable."));
    }
    String prefixe = prefixeOpt.get().prefixeFichier();
    long nonConformes =
        sequences.stream()
                .map(SequenceDEcoute::nomFichier)
                .filter(nom -> !commencePar(nom, prefixe))
                .count()
            + originaux.stream()
                .map(EnregistrementOriginal::nomFichier)
                .filter(nom -> !commencePar(nom, prefixe))
                .count();
    if (nonConformes > 0) {
      return resultat.avec(
          Alerte.bloquante(
              "Préfixe « "
                  + prefixe
                  + " » manquant ou non conforme sur "
                  + nonConformes
                  + " fichier(s) (R6, R7, R8)."));
    }
    return resultat;
  }

  /// Journal obligatoire (bloquant) ; relevé climatique optionnel (soft, R20).
  private ResultatVerification verifierJournalEtReleve(
      ResultatVerification resultat, SessionDEnregistrement session) {
    ResultatVerification cumul = resultat;
    if (journalDao.trouverParSession(session.id()).isEmpty()) {
      cumul =
          cumul.avec(
              Alerte.bloquante(
                  "Journal du capteur (LogPR<n>.txt) absent : il doit accompagner les séquences"
                      + " déposées."));
    }
    if (releveDao.trouverParSession(session.id()).isEmpty()) {
      cumul =
          cumul.avec(
              Alerte.soft(
                  "Relevé climatique absent (R20) : sonde non installée ou défaillante. Le dépôt"
                      + " reste possible."));
    }
    return cumul;
  }

  /// Préfixe `Car<carré>-<année>-Pass<n>-<point>-` attendu, calculé depuis le point et le
  /// site.
  private Optional<Prefixe> prefixeAttendu(Passage passage) {
    return pointDao
        .findById(passage.idPoint())
        .flatMap(
            (PointDEcoute point) ->
                siteDao
                    .findById(point.idSite())
                    .map(
                        (Site site) ->
                            new Prefixe(
                                site.numeroCarre(),
                                passage.annee(),
                                passage.numeroPassage(),
                                point.code())));
  }

  private static boolean commencePar(String nomFichier, String prefixe) {
    return nomFichier != null && nomFichier.startsWith(prefixe);
  }
}
