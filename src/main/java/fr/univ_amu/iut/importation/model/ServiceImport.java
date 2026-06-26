package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.RegleMetierException;
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
import java.util.concurrent.atomic.AtomicBoolean;
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

    /// Plafond de découpages audio **simultanés** (#12) : les threads virtuels sont bornés au nombre de
    /// cœurs disponibles, car chaque `transformer` charge un WAV complet en mémoire. Limite le pic
    /// mémoire (≈ ce nombre de PCM en vol) sans brider le débit CPU. Lu une fois au chargement.
    private static final int PARALLELISME_DECOUPAGE = Runtime.getRuntime().availableProcessors();

    private final InspecteurDossier inspecteur;
    private final CopieProtegee copie;
    private final Renommeur renommeur;
    private final DecoupageParallele decoupage;
    private final AgregatImportDao agregatDao;
    private final UniteDeTravail uniteDeTravail;
    private final Workspace workspace;
    private final FabriqueEntitesImport fabriqueEntites;

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
        this.decoupage = new DecoupageParallele(transformation, PARALLELISME_DECOUPAGE);
        this.agregatDao = Objects.requireNonNull(agregatDao, "agregatDao");
        this.uniteDeTravail = Objects.requireNonNull(uniteDeTravail, "uniteDeTravail");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.fabriqueEntites = new FabriqueEntitesImport(horloge);
    }

    /// Inspecte (lecture seule) le dossier SD sans rien importer : utile pour prévisualiser le
    /// contenu et afficher anomalies/état de nommage avant de lancer l'import.
    public RapportInspection inspecter(Path dossierSource) {
        return inspecteur.inspecter(dossierSource);
    }

    /// Racine du workspace (sur disque) : volume d'accueil d'une éventuelle extraction de zip (#139),
    /// le même que celui où l'import recopie ensuite les fichiers — donc jamais le *tmpfs* RAM `/tmp`.
    public Path racineWorkspace() {
        return workspace.racine();
    }

    /// Importe une nuit d'enregistrement depuis `dossierSource` (carte SD) vers le workspace, pour
    /// le point `idPoint`, selon le [Prefixe] R6 fourni par l'appelant.
    ///
    /// @param dossierSource racine du dossier de carte SD (lecture seule, R9)
    /// @param idPoint identifiant du point d'écoute rattaché (FK `listening_point.id`)
    /// @param prefixe préfixe R6 (carré + année + n° de passage + code de point), construit par
    /// l'appelant qui connaît le site et le point
    /// @return un compte rendu de l'import (agrégat persisté + anomalies du journal)
    /// @throws RegleMetierException si un passage existe déjà pour ce quadruplet (R5) ou si aucun
    /// enregistrement original n'est présent. **Un journal LogPR absent ne bloque plus** (#107) : l'import
    /// se poursuit en mode dégradé (identité déduite des noms de fichiers, trace de journal synthétique).
    public ResultatImport importer(Path dossierSource, Long idPoint, Prefixe prefixe) {
        return importer(dossierSource, idPoint, prefixe, progression -> {});
    }

    /// Variante avec **suivi de progression** (story #33) : `progres` est notifié au fil de la copie
    /// puis de la transformation des originaux (fraction globale 0→1, libellé « Copie X/N » puis
    /// « Transformation X/N »). Appelé sur le fil d'exécution de l'import — la couche IHM relaie au fil
    /// JavaFX. Même contrat transactionnel et mêmes règles métier que la variante sans callback.
    public ResultatImport importer(Path dossierSource, Long idPoint, Prefixe prefixe, Consumer<Progression> progres) {
        return importer(dossierSource, idPoint, prefixe, progres, JetonAnnulation.neutre());
    }

    /// Variante **annulable** (#146) : `jeton` est vérifié entre chaque fichier copié puis transformé ;
    /// une annulation lève [AnnulationImportException] et **nettoie la session partielle** sur disque
    /// (aucun passage n'est persisté tant que la transformation n'est pas finie, persistance atomique O7
    /// → pas de demi-état en base). Même contrat transactionnel et mêmes règles que les autres variantes.
    public ResultatImport importer(
            Path dossierSource, Long idPoint, Prefixe prefixe, Consumer<Progression> progres, JetonAnnulation jeton) {
        Objects.requireNonNull(dossierSource, "dossierSource");
        Objects.requireNonNull(idPoint, "idPoint");
        Objects.requireNonNull(prefixe, "prefixe");
        Objects.requireNonNull(progres, "progres");
        Objects.requireNonNull(jeton, "jeton");

        return sousVerrouImport(dossierSource, idPoint, prefixe, progres, jeton, false);
    }

    /// Exécute un import **sous le verrou anti-concurrent** (#54) : un seul import à la fois sur ce service
    /// singleton. En mode `ecraser` (#214), le réimport se fait sous la protection de [RemplacementSession]
    /// (ancienne session mise de côté, restaurée si l'import échoue) ; la suppression **en base** de
    /// l'ancien passage est différée dans la transaction d'insertion du nouveau. Partagé par [#importer]
    /// et [#ecraserEtImporter].
    private ResultatImport sousVerrouImport(
            Path dossierSource,
            Long idPoint,
            Prefixe prefixe,
            Consumer<Progression> progres,
            JetonAnnulation jeton,
            boolean ecraser) {
        if (!importEnCours.compareAndSet(false, true)) {
            throw new RegleMetierException("Un import est déjà en cours : attendez sa fin avant d'en lancer un autre.");
        }
        try {
            if (ecraser) {
                Path dossierSession = workspace.dossierSession(prefixe.nomDossierSession());
                return RemplacementSession.autourDe(
                        dossierSession,
                        () -> executerImportProtege(dossierSource, idPoint, prefixe, progres, jeton, true));
            }
            return executerImportProtege(dossierSource, idPoint, prefixe, progres, jeton, false);
        } finally {
            importEnCours.set(false);
        }
    }

    /// Pré-contrôle d'unicité R5 **exposé à l'IHM** (#108) : `true` si un passage existe déjà pour ce
    /// quadruplet `(point, année, n° de passage)`. Permet à l'assistant d'avertir **dès le rattachement**,
    /// avant de lancer un import lourd voué à échouer (R5). Tolère un rattachement encore incomplet
    /// (`idPoint` nul ou `numeroPassage < 1`) en répondant `false` (rien à signaler).
    public boolean numeroPassageDejaUtilise(Long idPoint, int annee, int numeroPassage) {
        if (idPoint == null || numeroPassage < 1) {
            return false;
        }
        return agregatDao.passageExistePour(idPoint, annee, numeroPassage);
    }

    /// Prochain n° de passage **libre** pour ce point et cette année (#108) : sert à proposer une
    /// correction en un clic quand le n° saisi est déjà pris.
    public int prochainNumeroPassageLibre(Long idPoint, int annee) {
        return agregatDao.prochainNumeroPassageLibre(idPoint, annee);
    }

    /// Détection de **nuit déjà importée** (#147) **exposée à l'IHM** : passages déjà en base pour le
    /// même enregistreur (`numeroSerie`) à la même date (`dateNuit`, au format ISO `AAAA-MM-JJ`), quel
    /// que soit leur rattachement. Permet d'avertir à l'inspection qu'on s'apprête à réimporter une nuit
    /// déjà présente (l'import en créerait un nouveau passage). Liste vide si `numeroSerie`/`dateNuit` est
    /// nul ou si aucune nuit ne correspond.
    public List<PassageExistant> nuitDejaImportee(String numeroSerie, String dateNuit) {
        if (numeroSerie == null || dateNuit == null) {
            return List.of();
        }
        return agregatDao.passagesDeLaNuit(numeroSerie, dateNuit);
    }

    /// Nombre de séquences du passage existant à ce quadruplet `(point, année, n° de passage)`, pour
    /// rendre tangible ce qu'un **écrasement** supprimerait (#214). Zéro si aucun passage à ce quadruplet
    /// (l'appelant, `ControleNumeroPassage`, ne sollicite ce compte que pour un quadruplet déjà avéré).
    public int compterSequencesDuPassageExistant(Long idPoint, int annee, int numeroPassage) {
        return agregatDao.compterSequencesDuPassage(idPoint, annee, numeroPassage);
    }

    /// **Écrase** le passage existant à ce quadruplet (suppression **destructive** en cascade : session,
    /// originaux, séquences, journal, relevé) **puis** importe la nuit, sous le même verrou
    /// anti-concurrent (#54) que [#importer]. À n'appeler qu'après **double confirmation** côté IHM (#214).
    ///
    /// @return le compte rendu de l'import qui suit l'écrasement
    public ResultatImport ecraserEtImporter(
            Path dossierSource, Long idPoint, Prefixe prefixe, Consumer<Progression> progres, JetonAnnulation jeton) {
        Objects.requireNonNull(dossierSource, "dossierSource");
        Objects.requireNonNull(idPoint, "idPoint");
        Objects.requireNonNull(prefixe, "prefixe");
        Objects.requireNonNull(progres, "progres");
        Objects.requireNonNull(jeton, "jeton");
        return sousVerrouImport(dossierSource, idPoint, prefixe, progres, jeton, true);
    }

    /// Corps de l'import (inspection, copie protégée R9, renommage R6/R7, transformation R10/R11,
    /// persistance atomique O7), exécuté **sous le verrou anti-concurrent** posé par [#importer].
    private ResultatImport executerImportProtege(
            Path dossierSource,
            Long idPoint,
            Prefixe prefixe,
            Consumer<Progression> progres,
            JetonAnnulation jeton,
            boolean ecraser) {
        // R5 : on refuse le doublon AVANT de copier/transformer quoi que ce soit. En mode écrasement (#214)
        // le doublon est au contraire attendu : on ne refuse pas, l'ancien passage sera supprimé dans la
        // transaction d'insertion du nouveau (remplacement atomique).
        if (!ecraser && agregatDao.passageExistePour(idPoint, prefixe.annee(), prefixe.numeroPassage())) {
            throw new RegleMetierException("Un passage n°"
                    + prefixe.numeroPassage()
                    + " existe déjà pour ce point en "
                    + prefixe.annee()
                    + " (le quadruplet point/année/n° de passage doit être unique).");
        }

        RapportInspection rapport = inspecteur.inspecter(dossierSource);
        if (rapport.originaux().isEmpty()) {
            throw new RegleMetierException(
                    "Aucun enregistrement original (.wav) à importer dans " + dossierSource + ".");
        }
        // Mode dégradé (#107) : un journal LogPR absent ne bloque plus l'import. À défaut, on reconstitue
        // une identité de repli depuis les noms des WAV (`PaRecPR<série>_<date>_…`) et on dépose une
        // **trace synthétique** de journal portant une anomalie explicite, pour ne pas coincer l'aval
        // (la préparation du lot exige une trace de journal). L'avertissement « aucun journal » est par
        // ailleurs porté par l'inspection (non bloquant).
        boolean sansJournal = rapport.journalOptionnel().isEmpty();
        JournalParse journal = rapport.journalOptionnel().orElseGet(() -> JournalDeRepli.depuis(rapport.originaux()));

        String nomSession = prefixe.nomDossierSession();
        Path dossierSession = workspace.dossierSession(nomSession);
        Path dossierBruts = workspace.dossierBruts(nomSession);
        Path dossierTransformes = workspace.dossierTransformes(nomSession);

        // Progression déterminée (#33) : N copies puis N transformations → 2N étapes au total.
        int nbOriginaux = rapport.originaux().size();
        int totalEtapes = nbOriginaux * 2;

        // Annulation (#146) : la copie et la transformation se font dans un dossier de session neuf ;
        // une annulation lève AnnulationImportException et on supprime la session partielle. Comme la
        // persistance est atomique en fin de course (O7), aucun passage n'est créé → pas de demi-état.
        List<Path> originauxRenommes;
        List<ResultatDecoupage> resultatsDecoupage;
        Path cheminJournalCopie;
        Path cheminReleveCopie;
        try {
            // 1) Copie protégée SD -> workspace (R9) : originaux dans bruts/ (reprise #231 : copie sautée
            //    si déjà présent), journal + relevé à la racine de la session.
            copierOriginaux(rapport.originaux(), dossierBruts, prefixe, totalEtapes, progres, jeton);
            cheminJournalCopie = sansJournal
                    ? JournalDeRepli.ecrireTraceSynthetique(dossierSession)
                    : copie.copierVers(rapport.cheminJournal(), dossierSession);
            cheminReleveCopie = rapport.aUnReleveClimatique()
                    ? copie.copierVers(rapport.cheminReleveClimatique(), dossierSession)
                    : null;

            // 2) Renommage R6/R7 sur la copie, puis 3) transformation R10/R11 (découpée en parallèle, #12,
            //    résiliente #155 : un original invalide est rejeté et consigné, pas bloquant).
            originauxRenommes = renommeur.renommer(dossierBruts, prefixe);
            resultatsDecoupage = decoupage.decouper(
                    originauxRenommes, dossierTransformes, prefixe, nbOriginaux, totalEtapes, progres, jeton);
            // Re-vérification après la phase de transformation : une annulation pendant la DERNIÈRE
            // transformation (postérieure à son propre point de contrôle) doit aussi stopper l'import.
            jeton.leverSiAnnule();
        } catch (RuntimeException echec) {
            // Annulation (#146) OU erreur fatale (p. ex. écriture workspace impossible, #155) : on nettoie
            // la session partielle et on remonte — pas de demi-état sur disque ni de passage persisté.
            supprimerSessionPartielle(dossierSession);
            throw echec;
        }

        // Dimension doublon (#214/#147) : passages déjà en base pour cette nuit (même série + date) AVANT
        // cet import (vide en écrasement : c'est un remplacement, pas un doublon). Décision déléguée au DAO,
        // qui possède déjà les lectures de nuit, pour garder l'orchestrateur cohésif. Date null-safe via
        // String.valueOf (→ "null", sans correspondance).
        List<PassageExistant> doublonsNuit = agregatDao.doublonsDeNuitPourRapport(
                ecraser, journal.numeroSerie(), String.valueOf(journal.dateDebut()));

        // Bilan d'import résilient (#155) : tri transformés / rejetés + rapport, délégué à la fabrique.
        RapportImportFabrique.BilanImport bilan =
                RapportImportFabrique.bilan(dossierSource, rapport, resultatsDecoupage, doublonsNuit);
        List<TransformationOriginal> transformations = bilan.transformations();
        if (transformations.isEmpty()) {
            supprimerSessionPartielle(dossierSession);
            throw new RegleMetierException("Aucun enregistrement original n'a pu être importé : "
                    + bilan.rejets().size()
                    + " fichier(s) rejeté(s) (ex. « "
                    + (bilan.rejets().isEmpty() ? "" : bilan.rejets().get(0).erreur())
                    + " »).");
        }
        RapportImport rapportImport = bilan.rapport();

        // 4) Construction des entités de l'agrégat.
        Enregistreur enregistreur = new Enregistreur(journal.numeroSerie(), journal.versionModele(), null);
        Micro micro = fabriqueEntites.micro(journal);
        Passage passage = fabriqueEntites.passage(journal, idPoint, prefixe);
        long volumeOriginaux = volumeTotal(originauxRenommes);
        long volumeSequences = transformations.stream()
                .flatMap(t -> t.sequences().stream())
                .mapToLong(SequenceProduite::octets)
                .sum();
        SessionDEnregistrement session =
                new SessionDEnregistrement(null, dossierSession.toString(), volumeOriginaux, volumeSequences, null);
        // Entité journal **uniforme** : en mode dégradé, le journal de repli porte déjà des évènements
        // vides et l'anomalie « import dégradé », donc on construit la trace de la même façon (#107).
        JournalDuCapteur journalEntite = new JournalDuCapteur(
                null, cheminJournalCopie.toString(), journal.evenementsJson(), journal.anomaliesJson(), null);
        ReleveClimatique releveEntite =
                cheminReleveCopie == null ? null : new ReleveClimatique(null, cheminReleveCopie.toString(), null, null);

        // Dernière fenêtre d'annulation : juste avant le point de non-retour (la persistance). Au-delà,
        // le passage est créé. Cette vérification nettoie elle-même la session (hors du bloc try/catch).
        verifierAnnulation(jeton, dossierSession);

        // 5) Persistance atomique de l'agrégat (O7 : tout ou rien).
        long[] ids = new long[2]; // [idPassage, idSession]
        uniteDeTravail.executer(cx -> {
            // Écrasement (#214) : on supprime l'ancien passage au quadruplet DANS la même transaction que
            // l'insertion du nouveau, donc tout-ou-rien (ON DELETE CASCADE pour session/originaux/séquences).
            if (ecraser) {
                agregatDao.supprimerPassageAuQuadruplet(cx, idPoint, prefixe.annee(), prefixe.numeroPassage());
            }
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
                journal.anomalies(),
                rapportImport);
    }

    /// Copie protégée (R9) des originaux vers `dossierBruts`, en émettant la progression « Copie X/N ·
    /// fichier ». Vérifie l'annulation (#146) entre deux fichiers.
    ///
    /// **Reprise sécurisée (#231)** : un original n'est sauté que si une version renommée existe **et**
    /// que son empreinte SHA-256 est **identique à celle de la source SD** — contenu vérifié, pas
    /// seulement le nom ni la taille. Un fichier absent, périmé ou corrompu (même nom, session orpheline
    /// incohérente) est re-copié : on ne persiste jamais un agrégat sur des fichiers douteux. Sauter une
    /// copie **fidèle** évite au passage le conflit de renommage qu'une re-copie déclencherait.
    private void copierOriginaux(
            List<Path> originaux,
            Path dossierBruts,
            Prefixe prefixe,
            int totalEtapes,
            Consumer<Progression> progres,
            JetonAnnulation jeton) {
        int nbOriginaux = originaux.size();
        int indiceCopie = 0;
        for (Path original : originaux) {
            jeton.leverSiAnnule(); // arrêt au plus tôt, entre deux fichiers
            // Copie **directement au nom final** (R6) : pas d'état intermédiaire au nom d'origine, donc
            // aucun doublon ni conflit lors du renommage si une version renommée traînait déjà (reprise).
            Path cible = dossierBruts.resolve(
                    Renommeur.nomApresRenommage(original.getFileName().toString(), prefixe));
            boolean dejaFidele =
                    Files.isRegularFile(cible) && Empreintes.sha256Hex(cible).equals(Empreintes.sha256Hex(original));
            if (!dejaFidele) {
                copie.copier(original, cible); // écrase une cible corrompue (REPLACE_EXISTING + vérif R9)
            }
            indiceCopie++;
            progres.accept(new Progression(
                    "Copie " + indiceCopie + "/" + nbOriginaux + " · " + original.getFileName()
                            + (dejaFidele ? " (déjà présent)" : ""),
                    (double) indiceCopie / totalEtapes));
        }
    }

    /// Supprime la **session partielle** (dossier `bruts/`+`transformes/` en cours de constitution)
    /// laissée par un import **annulé** (#146), pour ne pas accumuler des fichiers à moitié copiés.
    /// Best-effort (cf. [ExtracteurZip#supprimerRecursivement]).
    private static void supprimerSessionPartielle(Path dossierSession) {
        ExtracteurZip.supprimerRecursivement(dossierSession);
    }

    /// Vérifie l'annulation **hors du bloc try/catch de nettoyage** (#146) : si annulé, supprime la
    /// session partielle puis lève [AnnulationImportException]. Utilisé juste avant la persistance, où le
    /// `catch` couvrant la copie/transformation ne s'applique plus.
    private static void verifierAnnulation(JetonAnnulation jeton, Path dossierSession) {
        if (jeton.estAnnule()) {
            supprimerSessionPartielle(dossierSession);
            throw new AnnulationImportException();
        }
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
