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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/// Service métier de la feature `importation` : orchestre le parcours d'import P2 d'une nuit
/// d'enregistrement, de la carte SD jusqu'à l'agrégat persisté. Calqué sur le service de référence
/// `ServiceSites` (cf. SERVICE-CONVENTIONS).
///
/// **Enchaînement** (chaque étape est déléguée à un moteur dédié, le service ne fait
/// qu'orchestrer) :
///
/// 1. **Inspecter** ([InspecteurDossier]) : lecture seule de la SD (R9), parsing du journal LogPR,
/// détection des originaux et du relevé climatique.
/// 2. **Copier** ([CopieProtegee]) : SD → workspace, sans jamais écrire sur la source (R9). Les
/// originaux vont dans `bruts/`, le journal et le relevé à la racine de la session (R22).
/// 3. **Renommer** ([Renommeur]) : préfixe R6 appliqué aux originaux copiés (R7 conserve le
/// suffixe).
/// 4. **Transformer** ([TransformationAudio]) : expansion ×10 et découpage en séquences de 5 s,
/// déterministe (R10/R11), dans `transformes/`.
/// 5. **Persister** : l'agrégat complet (passage, session, originaux, séquences, enregistreur,
/// micro, journal, relevé) est écrit **tout ou rien** dans une [UniteDeTravail] (O7), via
/// [AgregatImportDao] (écritures « connection-aware »).
///
/// **Découplage inter-feature.** Le service dépend de `commun..` et des entités/DAO de `passage`
/// (cf. ArchUnit). Il **ne dépend pas** de `sites` : c'est l'appelant (le `viewmodel`, qui connaît
/// le site et le point courants) qui construit le [Prefixe] R6 (carré + année + n° de passage +
/// code de point) et fournit l'`idPoint`. Même philosophie que `ServicePassage`, qui reçoit le
/// `Protocole` en paramètre pour éviter une arête `passage → sites`.
///
/// **Statuts (workflow).** Un passage naît [StatutWorkflow#IMPORTE] ; comme l'import inclut la
/// transformation réussie, l'agrégat est committé directement au statut
/// [StatutWorkflow#TRANSFORME] (état final d'un import complet). La vérification (R12/R13) le fera
/// ensuite avancer.
///
/// **Limite connue (non transactionnelle côté disque).** Si la persistance échoue, la transaction
/// SQL est annulée (base cohérente, O7), mais les fichiers déjà copiés/transformés restent dans le
/// workspace. Ces opérations étant idempotentes et déterministes (R11), un réimport réécrit les
/// mêmes fichiers sans dommage ; la base reste la source de vérité.
public class ServiceImport {

    /// Heure de repli si le journal ne renseigne pas la fenêtre d'acquisition (`NOT NULL`).
    private static final String HEURE_INCONNUE = "00:00:00";

    /// Référence de micro inscrite quand le journal ne nomme aucun modèle (colonne `model_ref`
    /// obligatoire). Le journal LogPR fournit la bande passante et la sensibilité, mais pas la
    /// référence commerciale du micro : on inscrit donc un libellé explicite (cf. point
    /// d'intégration).
    private static final String MODELE_MICRO_NON_JOURNALISE = "Micro PR (modèle non journalisé)";

    /// Plafond de découpages audio **simultanés** (#12) : les threads virtuels sont bornés au nombre de
    /// cœurs disponibles, car chaque `transformer` charge un WAV complet en mémoire. Limite le pic
    /// mémoire (≈ ce nombre de PCM en vol) sans brider le débit CPU. Lu une fois au chargement.
    private static final int PARALLELISME_DECOUPAGE = Runtime.getRuntime().availableProcessors();

    private final InspecteurDossier inspecteur;
    private final CopieProtegee copie;
    private final Renommeur renommeur;
    private final TransformationAudio transformation;
    private final AgregatImportDao agregatDao;
    private final UniteDeTravail uniteDeTravail;
    private final Workspace workspace;
    private final Horloge horloge;

    /// Verrou anti-import-concurrent (#54) : le service étant un **singleton** partagé, il refuse un
    /// second import tant qu'un autre tourne. Filet « pire cas » indépendant de l'IHM (deux
    /// copies/transformations simultanées corromperaient le workspace).
    private final AtomicBoolean importEnCours = new AtomicBoolean(false);

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

    /// Inspecte (lecture seule) le dossier SD sans rien importer : utile pour prévisualiser le
    /// contenu et afficher anomalies/état de nommage avant de lancer l'import.
    public RapportInspection inspecter(Path dossierSource) {
        return inspecteur.inspecter(dossierSource);
    }

    /// Importe une nuit d'enregistrement depuis `dossierSource` (carte SD) vers le workspace, pour
    /// le point `idPoint`, selon le [Prefixe] R6 fourni par l'appelant.
    ///
    /// @param dossierSource racine du dossier de carte SD (lecture seule, R9)
    /// @param idPoint identifiant du point d'écoute rattaché (FK `listening_point.id`)
    /// @param prefixe préfixe R6 (carré + année + n° de passage + code de point), construit par
    /// l'appelant qui connaît le site et le point
    /// @return un compte rendu de l'import (agrégat persisté + anomalies du journal)
    /// @throws RegleMetierException si un passage existe déjà pour ce quadruplet (R5), si le journal
    /// LogPR est absent (enregistreur non identifiable), ou si aucun original n'est présent
    public ResultatImport importer(Path dossierSource, Long idPoint, Prefixe prefixe) {
        return importer(dossierSource, idPoint, prefixe, progression -> {});
    }

    /// Variante avec **suivi de progression** (story #33) : `progres` est notifié au fil de la copie
    /// puis de la transformation des originaux (fraction globale 0→1, libellé « Copie X/N » puis
    /// « Transformation X/N »). Appelé sur le fil d'exécution de l'import — la couche IHM relaie au fil
    /// JavaFX. Même contrat transactionnel et mêmes règles métier que la variante sans callback.
    public ResultatImport importer(Path dossierSource, Long idPoint, Prefixe prefixe, Consumer<Progression> progres) {
        Objects.requireNonNull(dossierSource, "dossierSource");
        Objects.requireNonNull(idPoint, "idPoint");
        Objects.requireNonNull(prefixe, "prefixe");
        Objects.requireNonNull(progres, "progres");

        // Garde anti-import-concurrent (#54) : un seul import à la fois sur ce service singleton, quelle
        // que soit l'IHM. On acquiert le verrou avant tout travail et on le relâche dans le finally.
        if (!importEnCours.compareAndSet(false, true)) {
            throw new RegleMetierException("Un import est déjà en cours : attendez sa fin avant d'en lancer un autre.");
        }
        try {
            return executerImportProtege(dossierSource, idPoint, prefixe, progres);
        } finally {
            importEnCours.set(false);
        }
    }

    /// Corps de l'import (inspection, copie protégée R9, renommage R6/R7, transformation R10/R11,
    /// persistance atomique O7), exécuté **sous le verrou anti-concurrent** posé par [#importer].
    private ResultatImport executerImportProtege(
            Path dossierSource, Long idPoint, Prefixe prefixe, Consumer<Progression> progres) {
        // R5 : on refuse le doublon AVANT de copier/transformer quoi que ce soit.
        if (agregatDao.passageExistePour(idPoint, prefixe.annee(), prefixe.numeroPassage())) {
            throw new RegleMetierException("R5 : un passage n°"
                    + prefixe.numeroPassage()
                    + " existe déjà pour ce point en "
                    + prefixe.annee()
                    + " (le quadruplet point/année/n° de passage doit être unique).");
        }

        RapportInspection rapport = inspecteur.inspecter(dossierSource);
        JournalParse journal = rapport.journalOptionnel()
                .orElseThrow(() -> new RegleMetierException("Journal LogPR introuvable dans "
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

        // Progression déterminée (#33) : N copies puis N transformations → 2N étapes au total.
        int nbOriginaux = rapport.originaux().size();
        int totalEtapes = nbOriginaux * 2;
        int faites = 0;

        // 1) Copie protégée SD -> workspace (R9). Originaux dans bruts/, journal + relevé à la racine.
        int indiceCopie = 0;
        for (Path original : rapport.originaux()) {
            copie.copierVers(original, dossierBruts);
            indiceCopie++;
            faites++;
            progres.accept(new Progression("Copie " + indiceCopie + "/" + nbOriginaux, (double) faites / totalEtapes));
        }
        Path cheminJournalCopie = copie.copierVers(rapport.cheminJournal(), dossierSession);
        Path cheminReleveCopie = rapport.aUnReleveClimatique()
                ? copie.copierVers(rapport.cheminReleveClimatique(), dossierSession)
                : null;

        // 2) Renommage R6/R7 sur la copie, puis 3) transformation R10/R11 (découpée en parallèle, #12).
        List<Path> originauxRenommes = renommeur.renommer(dossierBruts, prefixe);
        List<TransformationOriginal> transformations =
                decouperEnParallele(originauxRenommes, dossierTransformes, prefixe, nbOriginaux, totalEtapes, progres);

        // 4) Construction des entités de l'agrégat.
        Enregistreur enregistreur = new Enregistreur(journal.numeroSerie(), journal.versionModele(), null);
        Micro micro = construireMicro(journal);
        Passage passage = construirePassage(journal, idPoint, prefixe);
        long volumeOriginaux = volumeTotal(originauxRenommes);
        long volumeSequences = transformations.stream()
                .flatMap(t -> t.sequences().stream())
                .mapToLong(SequenceProduite::octets)
                .sum();
        SessionDEnregistrement session =
                new SessionDEnregistrement(null, dossierSession.toString(), volumeOriginaux, volumeSequences, null);
        JournalDuCapteur journalEntite = new JournalDuCapteur(
                null, cheminJournalCopie.toString(), journal.evenementsJson(), journal.anomaliesJson(), null);
        ReleveClimatique releveEntite =
                cheminReleveCopie == null ? null : new ReleveClimatique(null, cheminReleveCopie.toString(), null, null);

        // 5) Persistance atomique de l'agrégat (O7 : tout ou rien).
        long[] ids = new long[2]; // [idPassage, idSession]
        uniteDeTravail.executer(cx -> {
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
                EnregistrementOriginal original = new EnregistrementOriginal(
                        null,
                        t.nomOriginal(),
                        t.cheminOriginal().toString(),
                        t.dureeSourceSecondes(),
                        t.frequenceSourceHz(),
                        t.sha256(),
                        null);
                long idOriginal = agregatDao.insererOriginal(cx, ids[1], original);
                for (SequenceProduite sp : t.sequences()) {
                    SequenceDEcoute sequence = new SequenceDEcoute(
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
                new SessionDEnregistrement(ids[1], session.cheminRacine(), volumeOriginaux, volumeSequences, ids[0]);
        int nombreSequences =
                transformations.stream().mapToInt(t -> t.sequences().size()).sum();
        return new ResultatImport(
                passagePersiste,
                sessionPersistee,
                journal.numeroSerie(),
                transformations.size(),
                nombreSequences,
                journal.anomalies());
    }

    /// Découpe (#12) tous les `originaux` **en parallèle** sur des threads virtuels (un par fichier,
    /// Java 25), la concurrence étant **bornée** à [#PARALLELISME_DECOUPAGE] par un [Semaphore] :
    /// `transformer` charge tout le PCM en mémoire et écrit sur disque, donc sans plafond une grosse
    /// nuit (~1572 fichiers) tiendrait trop de WAV en vol → saturation mémoire. Pic mémoire borné ≈
    /// nbCœurs PCM, sans brider le débit CPU.
    ///
    /// L'**ordre d'origine est préservé** (Future récupérés dans l'ordre de soumission) → résultat
    /// déterministe (persistance et tests inchangés). La progression (#33) est émise **sous verrou +
    /// compteur** pour rester appelée un à un (sûre pour tout consommateur), libellés « k/N » monotones.
    private List<TransformationOriginal> decouperEnParallele(
            List<Path> originaux,
            Path dossierTransformes,
            Prefixe prefixe,
            int nbOriginaux,
            int totalEtapes,
            Consumer<Progression> progres) {
        AtomicInteger transfosFaites = new AtomicInteger(0);
        AtomicReference<RuntimeException> echecDecoupage = new AtomicReference<>();
        Object verrouProgression = new Object();
        Semaphore creneaux = new Semaphore(PARALLELISME_DECOUPAGE);
        try (ExecutorService executeur = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<TransformationOriginal>> decoupagesEnCours = originaux.stream()
                    .map(original -> executeur.submit(() -> {
                        creneaux.acquire();
                        try {
                            // Fail-fast (#12) : si un découpage a déjà échoué (n'importe lequel, quel que
                            // soit l'ordre), on n'en lance pas un nouveau et on propage l'échec d'origine
                            // → plus aucun fichier décodé inutilement une fois l'erreur connue.
                            RuntimeException dejaEchoue = echecDecoupage.get();
                            if (dejaEchoue != null) {
                                throw dejaEchoue;
                            }
                            TransformationOriginal resultat =
                                    transformation.transformer(original, dossierTransformes, prefixe);
                            synchronized (verrouProgression) {
                                int faits = transfosFaites.incrementAndGet();
                                progres.accept(new Progression(
                                        "Transformation " + faits + "/" + nbOriginaux,
                                        (double) (nbOriginaux + faits) / totalEtapes));
                            }
                            return resultat;
                        } catch (RuntimeException echec) {
                            echecDecoupage.compareAndSet(null, echec);
                            throw echec;
                        } finally {
                            creneaux.release();
                        }
                    }))
                    .toList();
            try {
                return decoupagesEnCours.stream()
                        .map(ServiceImport::resultatDecoupage)
                        .toList();
            } catch (RuntimeException echec) {
                // Fail-fast (#12) : à la première erreur, on annule les découpages restants (ceux en
                // attente sur le sémaphore s'arrêtent net) au lieu d'attendre toute la nuit avant de
                // remonter l'échec — comme le faisait l'import séquentiel. Sans cela, la fermeture du
                // pool patienterait jusqu'à la fin de tous les originaux déjà soumis.
                decoupagesEnCours.forEach(decoupage -> decoupage.cancel(true));
                throw echec;
            }
        }
    }

    /// Récupère le résultat d'un découpage lancé sur un thread virtuel (#12), en **dévoilant** la
    /// cause d'un éventuel échec : une [RuntimeException] (ex. [UncheckedIOException] d'écriture) est
    /// relancée telle quelle, pour conserver le comportement de l'import séquentiel ; une interruption
    /// restaure le drapeau du thread avant de remonter l'erreur.
    private static TransformationOriginal resultatDecoupage(Future<TransformationOriginal> decoupage) {
        try {
            return decoupage.get();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Découpage audio interrompu.", interruption);
        } catch (ExecutionException echec) {
            if (echec.getCause() instanceof RuntimeException relancable) {
                throw relancable;
            }
            throw new IllegalStateException("Échec du découpage audio.", echec.getCause());
        }
    }

    private Passage construirePassage(JournalParse journal, Long idPoint, Prefixe prefixe) {
        String date = journal.dateDebut() != null
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

    /// Micro déduit du journal : créé seulement si le journal porte des paramètres micro (R20).
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
