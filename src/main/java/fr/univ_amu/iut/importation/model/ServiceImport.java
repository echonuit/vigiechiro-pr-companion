package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.CompteurValidations;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.importation.model.dao.AgregatImportDao;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    /// Plafond de traitements de fichiers **simultanés** (#12 découpage, #948 copie protégée) : les
    /// threads virtuels sont bornés au nombre de cœurs disponibles. Côté découpage, chaque `transformer`
    /// charge un WAV complet en mémoire (pic ≈ ce nombre de PCM en vol) ; côté copie, cela évite de
    /// saturer le support source (SD) de lectures simultanées. Lu une fois au chargement.
    private static final int PARALLELISME_FICHIERS = Runtime.getRuntime().availableProcessors();

    private final InspecteurDossier inspecteur;
    private final AgregatImportDao agregatDao;
    private final Workspace workspace;
    private final CompteurValidations compteurValidations;
    private final ServiceSauvegarde serviceSauvegarde;

    /// Passerelle de synchronisation VigieChiro (axe 4), **optionnelle** : présente seulement dans l'app
    /// complète (avec la connexion). Sert à créer la participation **dès l'import** (best-effort) ; absente
    /// des injecteurs partiels / tests → la création est simplement omise.
    private final Optional<SynchronisationParticipation> synchronisation;

    /// Cœur d'exécution (copie/transformation/persistance par nuit), extrait pour garder le service en
    /// façade (verrou, inspection, requêtes). Partagé par l'import mono-nuit et multi-nuits.
    private final MoteurImport moteur;

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
            Horloge horloge,
            CompteurValidations compteurValidations,
            ServiceSauvegarde serviceSauvegarde,
            Optional<SynchronisationParticipation> synchronisation) {
        this.inspecteur = Objects.requireNonNull(inspecteur, "inspecteur");
        this.agregatDao = Objects.requireNonNull(agregatDao, "agregatDao");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.compteurValidations = Objects.requireNonNull(compteurValidations, "compteurValidations");
        this.serviceSauvegarde = Objects.requireNonNull(serviceSauvegarde, "serviceSauvegarde");
        this.synchronisation = Objects.requireNonNull(synchronisation, "synchronisation");
        PreparationOriginaux preparation = new PreparationOriginaux(copie, renommeur, PARALLELISME_FICHIERS);
        DecoupageParallele decoupage = new DecoupageParallele(transformation, PARALLELISME_FICHIERS);
        FabriqueEntitesImport fabriqueEntites = new FabriqueEntitesImport(horloge);
        this.moteur =
                new MoteurImport(copie, preparation, decoupage, fabriqueEntites, agregatDao, uniteDeTravail, workspace);
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
    /// une annulation lève [OperationAnnuleeException] et **nettoie la session partielle** sur disque
    /// (aucun passage n'est persisté tant que la transformation n'est pas finie, persistance atomique O7
    /// → pas de demi-état en base). Même contrat transactionnel et mêmes règles que les autres variantes.
    public ResultatImport importer(
            Path dossierSource, Long idPoint, Prefixe prefixe, Consumer<Progression> progres, JetonAnnulation jeton) {
        return importer(dossierSource, idPoint, prefixe, progres, jeton, true);
    }

    /// Variante avec choix de **conservation des originaux** (#…) : quand `conserverOriginaux` est
    /// `false`, les WAV ne sont **pas copiés** dans `bruts/` — ils sont lus et transformés directement
    /// depuis la source (R9, lecture seule), afin d'économiser l'espace disque (une nuit d'originaux peut
    /// peser plusieurs Go). `true` reproduit le comportement historique (copie protégée dans `bruts/`).
    /// Mêmes règles métier, même contrat transactionnel O7.
    public ResultatImport importer(
            Path dossierSource,
            Long idPoint,
            Prefixe prefixe,
            Consumer<Progression> progres,
            JetonAnnulation jeton,
            boolean conserverOriginaux) {
        return importer(dossierSource, idPoint, prefixe, progres, jeton, conserverOriginaux, SuiviFichiers.inerte());
    }

    /// Variante avec **suivi par fichier** (#947) : `suiviFichiers` est notifié au fil de la copie et de
    /// la transformation de chaque original (plan de la nuit, démarrages, fins, rejets #155), en
    /// complément de la progression globale. Notifié hors-thread et **dans le désordre** (transformation
    /// parallèle #12) — la couche IHM relaie au fil JavaFX et cible les lignes par numéro. Mêmes règles
    /// métier, même contrat transactionnel que les autres variantes.
    public ResultatImport importer(
            Path dossierSource,
            Long idPoint,
            Prefixe prefixe,
            Consumer<Progression> progres,
            JetonAnnulation jeton,
            boolean conserverOriginaux,
            SuiviFichiers suiviFichiers) {
        exigerParametresCommuns(dossierSource, idPoint, progres, jeton);
        Objects.requireNonNull(prefixe, "prefixe");
        exigerSuiviFichiers(suiviFichiers);

        return sousVerrouImport(
                dossierSource, idPoint, prefixe, progres, jeton, false, conserverOriginaux, suiviFichiers);
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
            boolean ecraser,
            boolean conserverOriginaux,
            SuiviFichiers suiviFichiers) {
        if (!importEnCours.compareAndSet(false, true)) {
            throw new RegleMetierException("Un import est déjà en cours : attendez sa fin avant d'en lancer un autre.");
        }
        try {
            ResultatImport resultat = ecraser
                    ? RemplacementSession.autourDe(
                            workspace.dossierSession(prefixe.nomDossierSession()),
                            () -> executerImportProtege(
                                    dossierSource,
                                    idPoint,
                                    prefixe,
                                    progres,
                                    jeton,
                                    true,
                                    conserverOriginaux,
                                    suiviFichiers))
                    : executerImportProtege(
                            dossierSource, idPoint, prefixe, progres, jeton, false, conserverOriginaux, suiviFichiers);
            // La nuit est persistée (transaction O7 committée) : on crée sa participation VigieChiro au plus
            // tôt (best-effort), pour que le dépôt la réutilise ensuite (pas de doublon).
            creerParticipationSiPossible(resultat.passage().id());
            return resultat;
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

    /// Premier **bloc de `taille` n° de passage consécutifs libres** pour ce point et cette année : n° de
    /// départ N tel que N..N+taille-1 soient tous libres. Sert à proposer une base d'auto-numérotation en
    /// import **multi-nuits** (n° consécutifs) qui **comble les trous** sans collision (#…).
    public int prochainBlocPassagesLibre(Long idPoint, int annee, int taille) {
        return agregatDao.prochainBlocPassagesLibre(idPoint, annee, taille);
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

    /// Aperçu de ce qu'un **écrasement** du passage existant à ce quadruplet `(point, année, n° de passage)`
    /// supprimerait (#214), pour rendre la confirmation tangible : nombre de **séquences** (régénérées à
    /// l'identique au réimport) et nombre de **validations observateur** définitivement perdues (délégué au
    /// port socle [CompteurValidations]). [ApercuEcrasement#VIDE] si aucun passage à ce quadruplet.
    public ApercuEcrasement apercuEcrasement(Long idPoint, int annee, int numeroPassage) {
        int sequences = agregatDao.compterSequencesDuPassage(idPoint, annee, numeroPassage);
        int validations = agregatDao
                .idPassageAuQuadruplet(idPoint, annee, numeroPassage)
                .map(compteurValidations::menaceesPourPassage)
                .orElse(0);
        return new ApercuEcrasement(sequences, validations);
    }

    /// **Écrase** le passage existant à ce quadruplet (suppression **destructive** en cascade : session,
    /// originaux, séquences, journal, relevé) **puis** importe la nuit, sous le même verrou
    /// anti-concurrent (#54) que [#importer]. À n'appeler qu'après **double confirmation** côté IHM (#214).
    ///
    /// **Filet de sécurité (#148)** : une **sauvegarde automatique** de la base est écrite **avant** la
    /// suppression destructive. Si elle échoue (disque plein…), l'exception remonte et l'écrasement
    /// **n'a pas lieu** : on ne détruit jamais sans copie de secours.
    ///
    /// @return le compte rendu de l'import qui suit l'écrasement
    public ResultatImport ecraserEtImporter(
            Path dossierSource, Long idPoint, Prefixe prefixe, Consumer<Progression> progres, JetonAnnulation jeton) {
        return ecraserEtImporter(dossierSource, idPoint, prefixe, progres, jeton, true);
    }

    /// Variante **écrasement** avec choix de [#importer(Path, Long, Prefixe, Consumer, JetonAnnulation, boolean)
    /// conservation des originaux] : `conserverOriginaux = false` transforme directement depuis la source
    /// sans peupler `bruts/`. À n'appeler qu'après **double confirmation** côté IHM (#214).
    public ResultatImport ecraserEtImporter(
            Path dossierSource,
            Long idPoint,
            Prefixe prefixe,
            Consumer<Progression> progres,
            JetonAnnulation jeton,
            boolean conserverOriginaux) {
        return ecraserEtImporter(
                dossierSource, idPoint, prefixe, progres, jeton, conserverOriginaux, SuiviFichiers.inerte());
    }

    /// Variante **écrasement** avec [#importer(Path, Long, Prefixe, Consumer, JetonAnnulation, boolean,
    /// SuiviFichiers) suivi par fichier] (#947). À n'appeler qu'après **double confirmation** côté IHM (#214).
    public ResultatImport ecraserEtImporter(
            Path dossierSource,
            Long idPoint,
            Prefixe prefixe,
            Consumer<Progression> progres,
            JetonAnnulation jeton,
            boolean conserverOriginaux,
            SuiviFichiers suiviFichiers) {
        exigerParametresCommuns(dossierSource, idPoint, progres, jeton);
        Objects.requireNonNull(prefixe, "prefixe");
        exigerSuiviFichiers(suiviFichiers);
        serviceSauvegarde.sauvegarder(serviceSauvegarde.dossierParDefaut());
        return sousVerrouImport(
                dossierSource, idPoint, prefixe, progres, jeton, true, conserverOriginaux, suiviFichiers);
    }

    /// Importe **plusieurs nuits** d'une même carte SD en **un passage par nuit** (même point, n° de
    /// passage propres portés par chaque [NuitAImporter], date propre à la nuit), sous **un seul** verrou
    /// anti-concurrent (#54). Chemin cible quand un enregistreur a tourné plusieurs nuits d'affilée : une
    /// **unique inspection** de la carte, puis une transformation + persistance atomique (O7) **par nuit**.
    ///
    /// **Échec rapide (R5)** : tous les n° de passage sont vérifiés **libres avant** la première copie ;
    /// aucun demi-groupe n'est amorcé si l'un est déjà pris. **Atomicité par nuit** : chaque nuit est une
    /// transaction distincte, donc si la nuit *i* échoue, les nuits *0..i-1* déjà importées demeurent.
    /// La progression (#33) est agrégée sur l'ensemble (« Nuit i/N · … »), l'annulation (#146) est
    /// vérifiée entre deux nuits.
    ///
    /// @param prefixeBase gabarit de [Prefixe] (carré/année/codePoint communs) ; le n° de passage de
    ///     chaque nuit provient de la [NuitAImporter] correspondante
    /// @param nuits nuits à importer (≥ 1), dans l'ordre où les passages seront créés
    public ResultatImportMultiNuits importerNuits(
            Path dossierSource,
            Long idPoint,
            Prefixe prefixeBase,
            List<NuitAImporter> nuits,
            boolean conserverOriginaux,
            Consumer<Progression> progres,
            JetonAnnulation jeton) {
        return importerNuits(
                dossierSource, idPoint, prefixeBase, nuits, conserverOriginaux, progres, jeton, SuiviFichiers.inerte());
    }

    /// Variante **multi-nuits** avec [#importer(Path, Long, Prefixe, Consumer, JetonAnnulation, boolean,
    /// SuiviFichiers) suivi par fichier] (#947) : le plan est **replanifié à chaque nuit** (une table par
    /// nuit en cours), en phase avec la progression agrégée « Nuit i/N · … ».
    public ResultatImportMultiNuits importerNuits(
            Path dossierSource,
            Long idPoint,
            Prefixe prefixeBase,
            List<NuitAImporter> nuits,
            boolean conserverOriginaux,
            Consumer<Progression> progres,
            JetonAnnulation jeton,
            SuiviFichiers suiviFichiers) {
        exigerParametresCommuns(dossierSource, idPoint, progres, jeton);
        Objects.requireNonNull(prefixeBase, "prefixeBase");
        Objects.requireNonNull(nuits, "nuits");
        exigerSuiviFichiers(suiviFichiers);
        if (nuits.isEmpty()) {
            throw new RegleMetierException("Aucune nuit à importer : cochez au moins une nuit.");
        }
        if (!importEnCours.compareAndSet(false, true)) {
            throw new RegleMetierException("Un import est déjà en cours : attendez sa fin avant d'en lancer un autre.");
        }
        try {
            // Inspection **unique** de la carte, partagée par toutes les nuits (portée par le contexte) ;
            // le découpage réel « une nuit = un passage » est exécuté par le moteur.
            RapportInspection rapport = inspecteur.inspecter(dossierSource);
            boolean sansJournal = rapport.journalOptionnel().isEmpty();
            JournalParse journal =
                    rapport.journalOptionnel().orElseGet(() -> JournalDeRepli.depuis(rapport.originaux()));
            ContexteImport ctx = new ContexteImport(
                    rapport,
                    journal,
                    sansJournal,
                    dossierSource,
                    idPoint,
                    conserverOriginaux,
                    false,
                    jeton,
                    suiviFichiers);
            ResultatImportMultiNuits resultat = moteur.importerNuits(ctx, prefixeBase, nuits, progres);
            // Une participation VigieChiro par nuit persistée (best-effort), réutilisée au dépôt.
            resultat.parNuit()
                    .forEach(nuit -> creerParticipationSiPossible(nuit.passage().id()));
            return resultat;
        } finally {
            importEnCours.set(false);
        }
    }

    /// Corps de l'import (inspection, copie protégée R9, renommage R6/R7, transformation R10/R11,
    /// persistance atomique O7), exécuté **sous le verrou anti-concurrent** posé par [#importer].
    private ResultatImport executerImportProtege(
            Path dossierSource,
            Long idPoint,
            Prefixe prefixe,
            Consumer<Progression> progres,
            JetonAnnulation jeton,
            boolean ecraser,
            boolean conserverOriginaux,
            SuiviFichiers suiviFichiers) {
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

        // Import mono-nuit : une seule nuit = tous les originaux. La date de la nuit vient des horodatages
        // RÉELS des WAV (date de soirée, bascule à midi), calculée par `partitionNuits()` — déjà la source de
        // vérité de l'import multi-nuits. On NE date PLUS d'après le journal : le LogPR est circulaire et sa
        // 1re ligne est celle du DÉPLOIEMENT (nuit 1), si bien qu'un dossier de passe ultérieure héritait à
        // tort de la nuit 1 (collision de date constatée au dépôt). Repli sur `journal.dateDebut()` seulement
        // si aucune nuit horodatée n'a pu être déduite (WAV sans horodatage exploitable).
        List<NuitDetectee> nuits = rapport.partitionNuits();
        LocalDate dateNuit = nuits.size() == 1 ? nuits.getFirst().dateNuit() : journal.dateDebut();
        ContexteImport ctx = new ContexteImport(
                rapport,
                journal,
                sansJournal,
                dossierSource,
                idPoint,
                conserverOriginaux,
                ecraser,
                jeton,
                suiviFichiers);
        return moteur.importerUneNuit(ctx, prefixe, rapport.originaux(), dateNuit, progres);
    }

    /// Crée la participation VigieChiro du passage fraîchement importé, **au mieux** : si l'observateur est
    /// connecté et le site rattaché/verrouillé, la participation est créée tôt (le dépôt la réutilise alors,
    /// sans doublon). Hors connexion (aucun token → création silencieusement refusée côté client) ou site non
    /// rattaché ([RegleMetierException] avalée), l'import **reste un succès** : la participation sera créée en
    /// repli au dépôt. Best-effort et silencieux ; la passerelle est absente des injecteurs sans connexion.
    private void creerParticipationSiPossible(Long idPassage) {
        synchronisation.ifPresent(sync -> {
            try {
                sync.creerPour(idPassage);
            } catch (RegleMetierException horsPortee) {
                // Site non rattaché / point manquant : non bloquant pour l'import (repli au dépôt).
            }
        });
    }

    /// Rejette (NPE) tout paramètre commun manquant d'un import, factorisé pour éviter la duplication : les
    /// trois points d'entrée ([#importer], [#ecraserEtImporter], [#importerNuits]) partagent ces quatre
    /// paramètres. Les paramètres spécifiques (préfixe, nuits) sont vérifiés par chaque appelant.
    /// Rejette (NPE) un suivi par fichier manquant (#947), factorisé entre les trois variantes qui le
    /// reçoivent (les variantes sans suivi passent déjà [SuiviFichiers#inerte()]).
    private static void exigerSuiviFichiers(SuiviFichiers suiviFichiers) {
        Objects.requireNonNull(suiviFichiers, "suiviFichiers");
    }

    private static void exigerParametresCommuns(
            Path dossierSource, Long idPoint, Consumer<Progression> progres, JetonAnnulation jeton) {
        Objects.requireNonNull(dossierSource, "dossierSource");
        Objects.requireNonNull(idPoint, "idPoint");
        Objects.requireNonNull(progres, "progres");
        Objects.requireNonNull(jeton, "jeton");
    }
}
