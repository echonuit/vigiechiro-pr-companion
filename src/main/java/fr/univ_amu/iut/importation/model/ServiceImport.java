package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.importation.model.dao.AgregatImportDao;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.Micro;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ReleveClimatique;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service métier de la feature {@code importation} : orchestre le parcours d'import P2 d'une nuit
 * d'enregistrement, de la carte SD jusqu'à l'agrégat persisté. Calqué sur le service de référence
 * {@code ServiceSites} (cf. SERVICE-CONVENTIONS).
 *
 * <p><b>Enchaînement</b> (chaque étape est déléguée à un moteur dédié, le service ne fait
 * qu'orchestrer) :
 *
 * <ol>
 *   <li><b>Inspecter</b> ({@link InspecteurDossier}) : lecture seule de la SD (R9), parsing du
 *       journal LogPR, détection des originaux et du relevé climatique.
 *   <li><b>Copier</b> ({@link CopieProtegee}) : SD → workspace, sans jamais écrire sur la source
 *       (R9). Les originaux vont dans {@code bruts/}, le journal et le relevé à la racine de la
 *       session (R22).
 *   <li><b>Renommer</b> ({@link Renommeur}) : préfixe R6 appliqué aux originaux copiés (R7 conserve
 *       le suffixe).
 *   <li><b>Transformer</b> ({@link TransformationAudio}) : expansion ×10 et découpage en séquences
 *       de 5 s, déterministe (R10/R11), dans {@code transformes/}.
 *   <li><b>Persister</b> : l'agrégat complet (passage, session, originaux, séquences, enregistreur,
 *       micro, journal, relevé) est écrit <b>tout ou rien</b> dans une {@link UniteDeTravail} (O7),
 *       via {@link AgregatImportDao} (écritures « connection-aware »).
 * </ol>
 *
 * <p><b>Découplage inter-feature.</b> Le service dépend de {@code commun..} et des entités/DAO de
 * {@code passage} (cf. ArchUnit). Il <b>ne dépend pas</b> de {@code sites} : c'est l'appelant (le
 * {@code viewmodel}, qui connaît le site et le point courants) qui construit le {@link Prefixe} R6
 * (carré + année + n° de passage + code de point) et fournit l'{@code idPoint}. Même philosophie
 * que {@code ServicePassage}, qui reçoit le {@code Protocole} en paramètre pour éviter une arête
 * {@code passage → sites}.
 *
 * <p><b>Statuts (workflow).</b> Un passage naît {@link StatutWorkflow#IMPORTE} ; comme l'import
 * inclut la transformation réussie, l'agrégat est committé directement au statut {@link
 * StatutWorkflow#TRANSFORME} (état final d'un import complet). La vérification (R12/R13) le fera
 * ensuite avancer.
 *
 * <p><b>Limite connue (non transactionnelle côté disque).</b> Si la persistance échoue, la
 * transaction SQL est annulée (base cohérente, O7), mais les fichiers déjà copiés/transformés
 * restent dans le workspace. Ces opérations étant idempotentes et déterministes (R11), un réimport
 * réécrit les mêmes fichiers sans dommage ; la base reste la source de vérité.
 */
public class ServiceImport {

  /** Heure de repli si le journal ne renseigne pas la fenêtre d'acquisition ({@code NOT NULL}). */
  private static final String HEURE_INCONNUE = "00:00:00";

  /**
   * Référence de micro inscrite quand le journal ne nomme aucun modèle (colonne {@code model_ref}
   * obligatoire). Le journal LogPR fournit la bande passante et la sensibilité, mais pas la
   * référence commerciale du micro : on inscrit donc un libellé explicite (cf. point
   * d'intégration).
   */
  private static final String MODELE_MICRO_NON_JOURNALISE = "Micro PR (modèle non journalisé)";

  private final InspecteurDossier inspecteur;
  private final CopieProtegee copie;
  private final Renommeur renommeur;
  private final TransformationAudio transformation;
  private final AgregatImportDao agregatDao;
  private final UniteDeTravail uniteDeTravail;
  private final Workspace workspace;
  private final Horloge horloge;

  public ServiceImport(
      InspecteurDossier inspecteur,
      CopieProtegee copie,
      Renommeur renommeur,
      TransformationAudio transformation,
      AgregatImportDao agregatDao,
      UniteDeTravail uniteDeTravail,
      Workspace workspace,
      Horloge horloge) {
    this.inspecteur = Objects.requireNonNull(inspecteur, "inspecteur");
    this.copie = Objects.requireNonNull(copie, "copie");
    this.renommeur = Objects.requireNonNull(renommeur, "renommeur");
    this.transformation = Objects.requireNonNull(transformation, "transformation");
    this.agregatDao = Objects.requireNonNull(agregatDao, "agregatDao");
    this.uniteDeTravail = Objects.requireNonNull(uniteDeTravail, "uniteDeTravail");
    this.workspace = Objects.requireNonNull(workspace, "workspace");
    this.horloge = Objects.requireNonNull(horloge, "horloge");
  }

  /**
   * Inspecte (lecture seule) le dossier SD sans rien importer : utile pour prévisualiser le contenu
   * et afficher anomalies/état de nommage avant de lancer l'import.
   */
  public RapportInspection inspecter(Path dossierSource) {
    return inspecteur.inspecter(dossierSource);
  }

  /**
   * Importe une nuit d'enregistrement depuis {@code dossierSource} (carte SD) vers le workspace,
   * pour le point {@code idPoint}, selon le {@link Prefixe} R6 fourni par l'appelant.
   *
   * @param dossierSource racine du dossier de carte SD (lecture seule, R9)
   * @param idPoint identifiant du point d'écoute rattaché (FK {@code listening_point.id})
   * @param prefixe préfixe R6 (carré + année + n° de passage + code de point), construit par
   *     l'appelant qui connaît le site et le point
   * @return un compte rendu de l'import (agrégat persisté + anomalies du journal)
   * @throws RegleMetierException si un passage existe déjà pour ce quadruplet (R5), si le journal
   *     LogPR est absent (enregistreur non identifiable), ou si aucun original n'est présent
   */
  public ResultatImport importer(Path dossierSource, Long idPoint, Prefixe prefixe) {
    Objects.requireNonNull(dossierSource, "dossierSource");
    Objects.requireNonNull(idPoint, "idPoint");
    Objects.requireNonNull(prefixe, "prefixe");

    // R5 : on refuse le doublon AVANT de copier/transformer quoi que ce soit.
    if (agregatDao.passageExistePour(idPoint, prefixe.annee(), prefixe.numeroPassage())) {
      throw new RegleMetierException(
          "R5 : un passage n°"
              + prefixe.numeroPassage()
              + " existe déjà pour ce point en "
              + prefixe.annee()
              + " (le quadruplet point/année/n° de passage doit être unique).");
    }

    RapportInspection rapport = inspecteur.inspecter(dossierSource);
    JournalParse journal =
        rapport
            .journalOptionnel()
            .orElseThrow(
                () ->
                    new RegleMetierException(
                        "Journal LogPR introuvable dans "
                            + dossierSource
                            + " : l'enregistreur ne peut pas être identifié."));
    if (rapport.originaux().isEmpty()) {
      throw new RegleMetierException(
          "Aucun enregistrement original (.wav) à importer dans " + dossierSource + ".");
    }

    String nomSession = prefixe.nomDossierSession();
    Path dossierSession = workspace.dossierSession(nomSession);
    Path dossierBruts = workspace.dossierBruts(nomSession);
    Path dossierTransformes = workspace.dossierTransformes(nomSession);

    // 1) Copie protégée SD -> workspace (R9). Originaux dans bruts/, journal + relevé à la racine.
    for (Path original : rapport.originaux()) {
      copie.copierVers(original, dossierBruts);
    }
    Path cheminJournalCopie = copie.copierVers(rapport.cheminJournal(), dossierSession);
    Path cheminReleveCopie =
        rapport.aUnReleveClimatique()
            ? copie.copierVers(rapport.cheminReleveClimatique(), dossierSession)
            : null;

    // 2) Renommage R6/R7 sur la copie, puis 3) transformation R10/R11.
    List<Path> originauxRenommes = renommeur.renommer(dossierBruts, prefixe);
    List<TransformationOriginal> transformations = new ArrayList<>();
    for (Path original : originauxRenommes) {
      transformations.add(transformation.transformer(original, dossierTransformes, prefixe));
    }

    // 4) Construction des entités de l'agrégat.
    Enregistreur enregistreur =
        new Enregistreur(journal.numeroSerie(), journal.versionModele(), null);
    Micro micro = construireMicro(journal);
    Passage passage = construirePassage(journal, idPoint, prefixe);
    long volumeOriginaux = volumeTotal(originauxRenommes);
    long volumeSequences =
        transformations.stream()
            .flatMap(t -> t.sequences().stream())
            .mapToLong(SequenceProduite::octets)
            .sum();
    SessionDEnregistrement session =
        new SessionDEnregistrement(
            null, dossierSession.toString(), volumeOriginaux, volumeSequences, null);
    JournalDuCapteur journalEntite =
        new JournalDuCapteur(
            null,
            cheminJournalCopie.toString(),
            journal.evenementsJson(),
            journal.anomaliesJson(),
            null);
    ReleveClimatique releveEntite =
        cheminReleveCopie == null
            ? null
            : new ReleveClimatique(null, cheminReleveCopie.toString(), null, null);

    // 5) Persistance atomique de l'agrégat (O7 : tout ou rien).
    long[] ids = new long[2]; // [idPassage, idSession]
    uniteDeTravail.executer(
        cx -> {
          agregatDao.upsertEnregistreur(cx, enregistreur);
          if (micro != null) {
            agregatDao.insererMicroSiAbsent(cx, micro);
          }
          ids[0] = agregatDao.insererPassage(cx, passage);
          ids[1] = agregatDao.insererSession(cx, ids[0], session);
          agregatDao.insererJournal(cx, ids[1], journalEntite);
          if (releveEntite != null) {
            agregatDao.insererReleve(cx, ids[1], releveEntite);
          }
          for (TransformationOriginal t : transformations) {
            EnregistrementOriginal original =
                new EnregistrementOriginal(
                    null,
                    t.nomOriginal(),
                    t.cheminOriginal().toString(),
                    t.dureeSourceSecondes(),
                    t.frequenceSourceHz(),
                    t.sha256(),
                    null);
            long idOriginal = agregatDao.insererOriginal(cx, ids[1], original);
            for (SequenceProduite sp : t.sequences()) {
              SequenceDEcoute sequence =
                  new SequenceDEcoute(
                      null,
                      sp.nomFichier(),
                      null,
                      sp.index(),
                      sp.offsetSourceSecondes(),
                      sp.dureeSecondes(),
                      sp.chemin().toString(),
                      false,
                      null);
              agregatDao.insererSequence(cx, ids[1], idOriginal, sequence);
            }
          }
        });

    Passage passagePersiste = avecId(passage, ids[0]);
    SessionDEnregistrement sessionPersistee =
        new SessionDEnregistrement(
            ids[1], session.cheminRacine(), volumeOriginaux, volumeSequences, ids[0]);
    int nombreSequences = transformations.stream().mapToInt(t -> t.sequences().size()).sum();
    return new ResultatImport(
        passagePersiste,
        sessionPersistee,
        journal.numeroSerie(),
        transformations.size(),
        nombreSequences,
        journal.anomalies());
  }

  private Passage construirePassage(JournalParse journal, Long idPoint, Prefixe prefixe) {
    String date =
        journal.dateDebut() != null
            ? journal.dateDebut().toString()
            : horloge.aujourdhui().toString();
    String heureDebut = journal.heureDebut() != null ? journal.heureDebut() : HEURE_INCONNUE;
    String heureFin = journal.heureFin() != null ? journal.heureFin() : HEURE_INCONNUE;
    return new Passage(
        null,
        prefixe.numeroPassage(),
        prefixe.annee(),
        date,
        heureDebut,
        heureFin,
        journal.parametresAcquisitionJson(),
        StatutWorkflow.TRANSFORME,
        null,
        null,
        null,
        null,
        idPoint,
        journal.numeroSerie());
  }

  /** Micro déduit du journal : créé seulement si le journal porte des paramètres micro (R20). */
  private Micro construireMicro(JournalParse journal) {
    if (journal.bandePassante() == null && journal.sensibilite() == null) {
      return null;
    }
    return new Micro(
        null,
        MODELE_MICRO_NON_JOURNALISE,
        journal.bandePassante(),
        journal.sensibilite(),
        null,
        null,
        true,
        "Micro déduit du journal LogPR (modèle non journalisé).",
        journal.numeroSerie());
  }

  private static Passage avecId(Passage p, long id) {
    return new Passage(
        id,
        p.numeroPassage(),
        p.annee(),
        p.dateEnregistrement(),
        p.heureDebut(),
        p.heureFin(),
        p.parametresAcquisition(),
        p.statutWorkflow(),
        p.verdictVerification(),
        p.commentaire(),
        p.donneesMeteo(),
        p.deposeLe(),
        p.idPoint(),
        p.idEnregistreur());
  }

  private static long volumeTotal(List<Path> fichiers) {
    long total = 0;
    try {
      for (Path fichier : fichiers) {
        total += Files.size(fichier);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Calcul du volume des originaux impossible", e);
    }
    return total;
  }
}
