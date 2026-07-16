package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.model.CompteurValidations;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.validation.model.dao.MessageObservationDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// Service métier de la feature `validation` : valide les résultats d'identification Tadarida
/// (parcours P7, épopée E7). Suit le patron du service de référence `ServiceSites` : pure Java
/// testable, dépendances reçues par constructeur, distinction règles soft / règles dures, dates
/// via [Horloge].
///
/// ## Responsabilités
///
/// - **Import en masse** ([#importer(Long, Path)]) : parse un CSV Tadarida via
///   [ParserCsvTadarida], crée le [ResultatsIdentification] (format détecté + horodatage), résout
///   chaque ligne sur sa séquence d'écoute en base, puis insère les [Observation] en une seule
///   transaction (`ObservationDao.insererTout`). Les détections sont raccrochées à la séquence
///   par **nom de fichier** (le CSV ne porte pas la clé technique).
/// - **Validation / correction** ([#valider(Long)] R15, [#corriger(Long, String, Double)] R16) :
///   renseigne le taxon observateur en `manuel` (R24).
/// - **Modes de revue** ([#validerSelonMode(Long, ModeRevue)] R18) : en `INVENTAIRE`, valider une
///   espèce propage automatiquement (`auto`, R24) la décision aux autres détections non touchées
///   de la même espèce ; en `ACTIVITE`, aucune propagation.
/// - **Export `_Vu`** ([#exporter(Long, Path, boolean)] R17) : reconstitue un CSV réinjectable
///   via [ExportVuCsv], les lignes non touchées conservant leurs colonnes Tadarida à l'identique.
/// - **Statut dérivé** ([#statut(Observation)]) : NON_TOUCHEE / VALIDEE / CORRIGEE.
///
/// ## Dépendances inter-features
///
/// Le service lit les DAO de la feature `passage` ([SessionDao], [SequenceDao]) pour raccrocher
/// les observations à leurs séquences. Le sens `validation → passage` reste acyclique (contrôlé
/// par `ArchitectureTest`).
///
/// ## Import tolérant et règles dures
///
/// L'import ([#importer(Long, Path)]) est **tolérant** : les lignes dont la séquence audio est absente de
/// la base sont **ignorées**, et les taxons Tadarida hors référentiel sont **auto-enregistrés en souches**
/// plutôt que de lever (cf. la méthode pour le détail). Restent **durs** (lèvent une [RegleMetierException],
/// rien n'est laissé à demi-écrit) : un **passage sans session** et un CSV dont **aucune** ligne n'est
/// importable.
public class ServiceValidation implements CompteurValidations {

    /// Fin de citation `« … ».` des messages d'erreur métier (guillemet fermant + point).
    private static final String GUILLEMET_FERMANT = " ».";

    /// Nom du paramètre `idPassage` (messages de `Objects.requireNonNull`).
    private static final String PARAM_ID_PASSAGE = "idPassage";

    private final ResultatsIdentificationDao resultatsDao;
    private final ObservationDao observationDao;
    private final TaxonDao taxonDao;
    private final SequenceDao sequenceDao;
    private final ParserCsvTadarida parser;
    private final ExportVuCsv export;
    private final PreservationValidations preservation;
    private final FilsDiscussionVigieChiro fils;
    private final EtatAncragePassage ancrage;
    private final MessageObservationDao messageDao;

    /// Cœur d'import, extrait pour cohésion (plafond GodClass) : porte l'invariant « un seul jeu par
    /// passage » et l'orchestration transactionnelle. Le service n'en est plus qu'une façade.
    private final NoyauImportObservations noyau;

    public ServiceValidation(
            ResultatsIdentificationDao resultatsDao,
            ObservationDao observationDao,
            TaxonDao taxonDao,
            SessionDao sessionDao,
            SequenceDao sequenceDao,
            ParserCsvTadarida parser,
            ExportVuCsv export,
            UniteDeTravail uniteDeTravail,
            Horloge horloge,
            MessageObservationDao messageDao) {
        this.resultatsDao = Objects.requireNonNull(resultatsDao, "resultatsDao");
        this.observationDao = Objects.requireNonNull(observationDao, "observationDao");
        this.taxonDao = Objects.requireNonNull(taxonDao, "taxonDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.export = Objects.requireNonNull(export, "export");
        this.messageDao = Objects.requireNonNull(messageDao, "messageDao");
        this.preservation = new PreservationValidations(resultatsDao, observationDao);
        this.fils = new FilsDiscussionVigieChiro(observationDao, messageDao, uniteDeTravail);
        this.ancrage = new EtatAncragePassage(resultatsDao, observationDao);
        this.noyau = new NoyauImportObservations(
                resultatsDao, observationDao, taxonDao, sessionDao, sequenceDao, uniteDeTravail, horloge, preservation);
    }

    // ---------------------------------------------------------------------------------------------
    // Import (E7.S1)
    // ---------------------------------------------------------------------------------------------

    /// **Fil de discussion** d'une observation (#1417) : ce que l'observateur et le validateur du MNHN se
    /// sont dit à propos de cette détection, dans l'ordre du serveur. Vide si personne n'a écrit — le cas
    /// courant. Lecture seule : le fil est un reflet du serveur, rafraîchi à chaque import.
    public List<MessageObservation> filDeLObservation(Long idObservation) {
        Objects.requireNonNull(idObservation, "idObservation");
        return messageDao.filDeLObservation(idObservation);
    }

    /// Nombre total d'observations (compteur du tableau de bord d'accueil).
    public long compterObservations() {
        return observationDao.compter();
    }

    /// Identifiant du jeu de résultats Tadarida d'un passage (`null` si aucun import), pour activer
    /// l'export `_Vu` de la source `ParPassage` sans recharger toute la vue de validation.
    public Optional<Long> resultatsDuPassage(Long idPassage) {
        return resultatsDao.findByPassage(idPassage).map(ResultatsIdentification::id);
    }

    /// Le passage a-t-il **au moins une** observation sans ancrage plateforme (`idDonneeVigieChiro ==
    /// null`) ? Cas d'un passage reconstruit par CSV (#1565), dont l'ancrage — requis pour publier des
    /// corrections — n'est acquis qu'à la réactivation (#1571). Délègue à [EtatAncragePassage].
    public boolean ancrageManquant(Long idPassage) {
        return ancrage.manquant(idPassage);
    }

    /// Le passage n'a-t-il **aucune observation ancrée** à la plateforme (toutes `idDonneeVigieChiro ==
    /// null`) ? État d'un passage reconstruit par CSV non réactivé, où rien n'est publiable : l'IHM grise
    /// donc proactivement l'action de publication (#1596). Distinct de [#ancrageManquant] (au moins une
    /// manquante) : ici il faut qu'**aucune** ne soit ancrée, sinon une publication partielle reste
    /// possible. Délègue à [EtatAncragePassage].
    public boolean aucunAncrage(Long idPassage) {
        return ancrage.aucun(idPassage);
    }

    /// Importe les résultats Tadarida d'un passage, **en mode tolérant** : parse le CSV, crée les
    /// résultats d'identification et insère les observations raccrochées à leurs séquences.
    ///
    /// Un CSV Tadarida réel référence souvent des segments dont **l'audio n'a pas été conservé** et des
    /// **taxons hors du référentiel** semé. Plutôt que de tout rejeter, l'import garde ce qu'il peut :
    /// - les lignes dont la **séquence audio est absente** de la base sont **ignorées** (comptées) ;
    /// - les **taxons inconnus** (parmi les lignes retenues) sont **auto-enregistrés en souches**
    ///   ([TaxonDao#enregistrerHorsReferentiel]) pour respecter la FK, plutôt que de lever.
    ///
    /// Reste **dur** : un passage **sans session** lève (il faut importer la nuit d'abord), et un CSV
    /// dont **aucune** ligne n'a de séquence en base lève aussi (rien à importer). Le [BilanImport]
    /// restitue le détail (importées / ignorées / taxons hors référentiel).
    ///
    /// @param idPassage passage annoté (doit posséder une session d'enregistrement)
    /// @param cheminCsv chemin du fichier `*-observations.csv` ou `_Vu.csv` (R23)
    /// @return le bilan de l'import (résultats créés + compteurs)
    /// @throws RegleMetierException si le passage n'a pas de session, ou si aucune séquence du CSV
    /// n'existe en base
    public BilanImport importer(Long idPassage, Path cheminCsv) {
        return importerInterne(idPassage, cheminCsv, false);
    }

    /// Réimporte un CSV Tadarida sur un passage **déjà importé**, en **remplaçant** l'ancien jeu de
    /// façon **atomique** : le nouveau CSV est d'abord parsé et validé, puis l'ancien jeu (et ses
    /// observations, cascade) est supprimé **dans la même transaction** que l'insertion du nouveau.
    /// Si le nouveau CSV est invalide (parse en échec, ou aucune ligne importable), rien n'est
    /// supprimé : l'ancien jeu et ses observations sont **conservés intacts**.
    ///
    /// @param idPassage passage déjà annoté (le jeu existant, s'il y en a un, est remplacé)
    /// @param cheminCsv nouveau fichier CSV à importer
    /// @return le bilan du nouvel import
    /// @throws RegleMetierException si le passage n'a pas de session, ou si aucune séquence du CSV
    /// n'existe en base (dans ce cas l'ancien jeu reste en place)
    public BilanImport reimporter(Long idPassage, Path cheminCsv) {
        return importerInterne(idPassage, cheminCsv, true);
    }

    private BilanImport importerInterne(Long idPassage, Path cheminCsv, boolean remplacer) {
        Objects.requireNonNull(cheminCsv, "cheminCsv");
        ResultatParseTadarida parse = parser.parser(cheminCsv);
        return noyau.importer(
                idPassage, parse.lignes(), cheminCsv.toString(), parse.format().libelle(), remplacer);
    }

    /// Importe les résultats **VigieChiro** d'une participation (#719, axe 4.2) sur un passage dont la
    /// nuit (audio) est déjà importée : convertit les [DonneeVigieChiro] en lignes d'observation et suit
    /// le même cœur d'import que le CSV Tadarida ([NoyauImportObservations#importer]) : rattachement aux
    /// séquences par nom de fichier, résolution des taxons, préservation des validations observateur.
    ///
    /// @param idPassage passage cible (sa nuit doit avoir été importée : séquences présentes)
    /// @param donnees résultats de la participation (`GET /participations/#id/donnees`)
    /// @param remplacer remplace le jeu existant (en préservant les validations observateur) si `true`
    public BilanImport importerDepuisVigieChiro(Long idPassage, List<DonneeVigieChiro> donnees, boolean remplacer) {
        Objects.requireNonNull(donnees, "donnees");
        BilanImport bilan = noyau.importer(
                idPassage,
                ConversionDonneesVigieChiro.enLignes(donnees),
                ResultatsIdentification.SOURCE_VIGIECHIRO,
                "VigieChiro",
                remplacer);
        // Le fil de discussion ne peut pas voyager dans une LigneObservation (1-N) ni être écrit pendant
        // l'insertion en lot (les clés générées ne sont pas récupérées) : il s'écrit ici, une fois les
        // observations en base, rapprochées du serveur par leur ancrage plateforme (#1417).
        fils.enregistrer(bilan.resultats().id(), donnees);
        return bilan;
    }

    /// **Reconstruction instantanée par CSV** (#1565) : importe les observations d'un CSV Tadarida brut
    /// téléchargé depuis VigieChiro (`participation-<id>-observations.csv`) sur un passage dont les
    /// séquences ont déjà été recréées. Même cœur d'import que le CSV local ([#importer(Long, Path)]), mais
    /// à partir du **contenu** déjà en mémoire (pas de fichier sur disque). Les observations naissent
    /// **sans ancrage** : le CSV ne porte pas d'`_id`, l'ancrage plateforme est acquis à la réactivation
    /// (#1571).
    ///
    /// @param idPassage passage cible (sa session doit exister : ses séquences ont été recréées)
    /// @param contenuCsv contenu du CSV d'observations
    /// @param remplacer remplace le jeu existant (en préservant les validations observateur) si `true`
    public BilanImport importerContenuCsv(Long idPassage, String contenuCsv, boolean remplacer) {
        Objects.requireNonNull(contenuCsv, "contenuCsv");
        ResultatParseTadarida parse = parser.parser(contenuCsv);
        return noyau.importer(
                idPassage,
                parse.lignes(),
                ResultatsIdentification.SOURCE_VIGIECHIRO,
                parse.format().libelle(),
                remplacer);
    }

    /// Noms de séquences (fichiers) **distincts** d'un CSV Tadarida brut, dans l'ordre d'apparition (#1565) :
    /// le passage reconstruit s'en sert pour recréer ses lignes de séquences **avant** l'import (qui ignore
    /// les lignes sans séquence de même nom). Aucune écriture.
    public List<String> nomsSequencesCsv(String contenuCsv) {
        Objects.requireNonNull(contenuCsv, "contenuCsv");
        return parser.parser(contenuCsv).lignes().stream()
                .map(LigneObservation::nomSequence)
                .filter(nom -> nom != null && !nom.isBlank())
                .distinct()
                .toList();
    }

    /// Nombre d'observations **validées** du passage (cf. [#estValidee(Observation)]) : le travail de
    /// validation qui serait perdu si le passage était supprimé ou écrasé. Implémente le port socle
    /// [CompteurValidations] injecté par les features `passage` et `importation` pour leurs confirmations
    /// destructives.
    @Override
    public int menaceesPourPassage(Long idPassage) {
        Objects.requireNonNull(idPassage, PARAM_ID_PASSAGE);
        return (int) preservation.compterValidees(idPassage);
    }

    // ---------------------------------------------------------------------------------------------
    // Validation / correction (E7.S2 ; R15, R16, R24)
    // ---------------------------------------------------------------------------------------------

    /// Valide une observation « en un clic » (R15) : taxon observateur = taxon Tadarida,
    /// probabilité observateur renseignée (reprise de la probabilité Tadarida, ou 1.0 à défaut),
    /// mode `manuel` (R24).
    ///
    /// @throws RegleMetierException si l'observation est introuvable
    public Observation valider(Long idObservation) {
        Observation mise = valideeManuellement(chargerObservation(idObservation));
        observationDao.update(mise);
        return mise;
    }

    /// Observation **validée à la main** (R15, sans I/O) : retient la proposition Tadarida en `manuel`, avec
    /// la probabilité observateur si présente, sinon Tadarida, sinon `1.0`. Partagé par [#valider(Long)] et
    /// la validation **en lot** [#validerLot(List)].
    private static Observation valideeManuellement(Observation observation) {
        Double prob = observation.probObservateur();
        if (prob == null) {
            prob = observation.probTadarida() != null ? observation.probTadarida() : 1.0;
        }
        return observation.avecObservateur(observation.taxonTadarida(), prob, ModeValidation.MANUEL);
    }

    /// Corrige une observation (R16) : saisit un taxon observateur **différent** du taxon Tadarida,
    /// en mode `manuel` (R24).
    ///
    /// @param codeTaxonObservateur taxon retenu par l'observateur (doit exister en base)
    /// @param probObservateur probabilité saisie (optionnelle)
    /// @throws RegleMetierException si l'observation est introuvable ou si le taxon est inconnu
    public Observation corriger(Long idObservation, String codeTaxonObservateur, Double probObservateur) {
        Observation observation = chargerObservation(idObservation);
        if (codeTaxonObservateur == null
                || taxonDao.findById(codeTaxonObservateur).isEmpty()) {
            throw new RegleMetierException("Taxon observateur inconnu : « " + codeTaxonObservateur + GUILLEMET_FERMANT);
        }
        return majObservateur(observation, codeTaxonObservateur, probObservateur, ModeValidation.MANUEL);
    }

    /// **Marque ou retire** une observation du corpus de **référence** (`is_reference`, P10/#audio) :
    /// archivage transverse depuis n'importe quelle source de la vue audio. N'altère ni le taxon ni le
    /// statut de revue (orthogonal à valider/corriger).
    ///
    /// @param reference `true` pour marquer comme référence, `false` pour la retirer
    /// @return l'observation relue, à jour
    /// @throws RegleMetierException si l'observation est introuvable
    public Observation marquerReference(Long idObservation, boolean reference) {
        Observation mise = chargerObservation(idObservation).avecReference(reference);
        observationDao.update(mise);
        return mise;
    }

    /// Enregistre (ou efface) le **commentaire libre** d'une observation (`user_comment`). Un texte vide ou
    /// blanc **efface** le commentaire (remis à `null`), sinon il est enregistré après `strip()`. Même patron
    /// que [#marquerReference] : un seul champ modifié sur le record immuable, écrit via `update`.
    ///
    /// @param idObservation identifiant de l'observation à commenter
    /// @param texte commentaire à enregistrer, ou vide/`null` pour l'effacer
    /// @return l'observation relue, à jour
    /// @throws RegleMetierException si l'observation est introuvable
    public Observation commenter(Long idObservation, String texte) {
        Observation mise = chargerObservation(idObservation).avecCommentaire(texte);
        observationDao.update(mise);
        return mise;
    }

    /// Valide une observation selon le [ModeRevue] (R18, R24).
    ///
    /// - `ACTIVITE` : valide la seule observation visée (en `manuel`).
    /// - `INVENTAIRE` : valide l'observation visée (en `manuel`), puis propage la décision aux
    ///   autres observations **non touchées** de la même espèce Tadarida dans le même jeu de
    ///   résultats (en `auto`).
    ///
    /// @return les observations affectées (la première étant celle validée à la main)
    /// @throws RegleMetierException si l'observation est introuvable
    public List<Observation> validerSelonMode(Long idObservation, ModeRevue mode) {
        Objects.requireNonNull(mode, "mode");
        Observation pivot = valider(idObservation);
        List<Observation> affectees = new ArrayList<>();
        affectees.add(pivot);
        if (mode == ModeRevue.INVENTAIRE) {
            for (Observation autre : observationDao.findByResults(pivot.idResultats())) {
                if (!autre.id().equals(pivot.id())
                        && autre.taxonObservateur() == null
                        && pivot.taxonTadarida().equals(autre.taxonTadarida())) {
                    Double prob = autre.probTadarida() != null ? autre.probTadarida() : 1.0;
                    affectees.add(majObservateur(autre, autre.taxonTadarida(), prob, ModeValidation.AUTO));
                }
            }
        }
        return affectees;
    }

    /// Statut dérivé d'une observation (R15/R16/R17) : la **décision de l'observateur est portée par la
    /// présence d'un `taxon_observer`**, pas par la forme de sa probabilité. NON_TOUCHEE (pas de taxon
    /// observateur), VALIDEE (taxon observateur = Tadarida), CORRIGEE (taxon observateur différent).
    ///
    /// La probabilité de l'observateur n'entre **pas** dans le statut : un _Vu réel peut porter un code de
    /// confiance **textuel** (« SUR »), lu comme probabilité inconnue par [ParserCsvTadarida] ; exiger une
    /// probabilité numérique ferait alors apparaître une observation pourtant validée comme « non revue ».
    public StatutObservation statut(Observation observation) {
        Objects.requireNonNull(observation, "observation");
        if (observation.taxonObservateur() == null) {
            return StatutObservation.NON_TOUCHEE;
        }
        return observation.taxonObservateur().equals(observation.taxonTadarida())
                ? StatutObservation.VALIDEE
                : StatutObservation.CORRIGEE;
    }

    /// Charge la **vue de validation** d'un passage pour M-Vision-Tadarida : le jeu de résultats
    /// Tadarida associé (s'il a été importé) et ses observations, chacune avec son [#statut] de
    // revue.
    ///
    /// Renvoie une vue **vide** (`idResultats` null, liste vide) si aucun CSV Tadarida n'a encore été
    /// importé pour le passage — l'écran affiche alors un état vide plutôt que de lever.
    public VueValidation chargerValidation(Long idPassage) {
        Objects.requireNonNull(idPassage, PARAM_ID_PASSAGE);
        Optional<ResultatsIdentification> resultats = resultatsDao.findByPassage(idPassage);
        if (resultats.isEmpty()) {
            return new VueValidation(null, List.of());
        }
        Long idResultats = resultats.get().id();
        List<ObservationStatut> observations = observationDao.findByResults(idResultats).stream()
                .map(observation -> new ObservationStatut(observation, statut(observation)))
                .toList();
        return new VueValidation(idResultats, observations);
    }

    /// Liste tous les taxons connus en base (pour le sélecteur de correction de l'observateur, R16).
    /// L'ordre est celui du DAO ; le tri d'affichage est laissé à la couche présentation.
    public List<Taxon> taxonsDisponibles() {
        return taxonDao.findAll();
    }

    /// Chemin du fichier audio (séquence transformée, R22) d'une observation, à partir de sa séquence
    /// d'écoute source. Sert à l'écoute dans M-Vision-Tadarida (E7.S3). Vide si la séquence est
    /// introuvable ou si `idSequence` est `null`.
    public Optional<Path> cheminAudio(Long idSequence) {
        if (idSequence == null) {
            return Optional.empty();
        }
        return sequenceDao.findById(idSequence).map(sequence -> Path.of(sequence.cheminFichier()));
    }

    // ---------------------------------------------------------------------------------------------
    // Export _Vu (E7.S3 ; R17, R24)
    // ---------------------------------------------------------------------------------------------

    /// Écrit le CSV `_Vu` réinjectable d'un jeu de résultats (R17). Les observations non touchées
    /// conservent leurs colonnes Tadarida ; les validées/corrigées reflètent le taxon observateur.
    ///
    /// @param inclureMode `true` pour ajouter la colonne `validation_mode` (R24)
    /// @return le chemin du fichier écrit
    public Path exporter(Long idResultats, Path destination, boolean inclureMode) {
        export.ecrire(destination, lignesAExporter(idResultats), inclureMode);
        return destination;
    }

    /// Sérialise le CSV `_Vu` d'un jeu de résultats en chaîne (golden master / aperçu).
    public String exporterVersChaine(Long idResultats, boolean inclureMode) {
        return export.versChaine(lignesAExporter(idResultats), inclureMode);
    }

    /// Reconstitue les [LigneObservation] d'un jeu de résultats (séquence relue par son nom).
    public List<LigneObservation> lignesAExporter(Long idResultats) {
        Map<Long, String> nomParSequence = new HashMap<>();
        List<LigneObservation> lignes = new ArrayList<>();
        for (Observation o : observationDao.findByResults(idResultats)) {
            String nom = nomParSequence.computeIfAbsent(
                    o.idSequence(),
                    id -> sequenceDao
                            .findById(id)
                            .map(s -> NoyauImportObservations.cleSequence(s.nomFichier()))
                            .orElseThrow(
                                    () -> new RegleMetierException("Séquence " + id + " introuvable pour l'export.")));
            lignes.add(new LigneObservation(
                    nom,
                    o.debutS(),
                    o.finS(),
                    o.frequenceMedianeKHz(),
                    o.taxonTadarida(),
                    o.probTadarida(),
                    o.taxonAutreTadarida(),
                    o.taxonObservateur(),
                    o.probObservateur(),
                    o.modeValidation(),
                    o.idDonneeVigieChiro(),
                    o.indiceVigieChiro(),
                    o.certitudeObservateur(),
                    o.taxonValidateur(),
                    o.certitudeValidateur()));
        }
        return lignes;
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers privés
    // ---------------------------------------------------------------------------------------------

    private Observation chargerObservation(Long idObservation) {
        return observationDao
                .findById(idObservation)
                .orElseThrow(() -> new RegleMetierException("Observation introuvable : " + idObservation + "."));
    }

    /// Met à jour le triplet observateur (taxon, probabilité, mode) d'une observation et la relit.
    private Observation majObservateur(
            Observation o, String taxonObservateur, Double probObservateur, ModeValidation mode) {
        Observation mise = o.avecObservateur(taxonObservateur, probObservateur, mode);
        observationDao.update(mise);
        return mise;
    }
}
