package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
public class ServiceValidation {

    /// Fin de citation `« … ».` des messages d'erreur métier (guillemet fermant + point).
    private static final String GUILLEMET_FERMANT = " ».";

    private final ResultatsIdentificationDao resultatsDao;
    private final ObservationDao observationDao;
    private final TaxonDao taxonDao;
    private final SessionDao sessionDao;
    private final SequenceDao sequenceDao;
    private final ParserCsvTadarida parser;
    private final ExportVuCsv export;
    private final UniteDeTravail uniteDeTravail;
    private final Horloge horloge;

    public ServiceValidation(
            ResultatsIdentificationDao resultatsDao,
            ObservationDao observationDao,
            TaxonDao taxonDao,
            SessionDao sessionDao,
            SequenceDao sequenceDao,
            ParserCsvTadarida parser,
            ExportVuCsv export,
            UniteDeTravail uniteDeTravail,
            Horloge horloge) {
        this.resultatsDao = Objects.requireNonNull(resultatsDao, "resultatsDao");
        this.observationDao = Objects.requireNonNull(observationDao, "observationDao");
        this.taxonDao = Objects.requireNonNull(taxonDao, "taxonDao");
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.export = Objects.requireNonNull(export, "export");
        this.uniteDeTravail = Objects.requireNonNull(uniteDeTravail, "uniteDeTravail");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    // ---------------------------------------------------------------------------------------------
    // Import (E7.S1)
    // ---------------------------------------------------------------------------------------------

    /// Nombre total d'observations (compteur du tableau de bord d'accueil).
    public long compterObservations() {
        return observationDao.compter();
    }

    /// **Espèces observées** par l'utilisateur, rattachées à leur passage (#323) : une entrée par couple
    /// (espèce, passage), du plus récent au plus ancien. Sert à la recherche globale (4ᵉ groupe). L'espèce
    /// est le taxon validé sinon la proposition Tadarida ; les pseudo-taxons bruit/oiseau sont exclus.
    public List<EspeceObservee> especesObservees(String idUtilisateur) {
        return observationDao.especesObserveesParUtilisateur(idUtilisateur);
    }

    /// Observations **marquées référence** de l'utilisateur (#audio) : la **source « Références »** de la
    /// vue audio unifiée (corpus de sons de référence, ex-bibliothèque). Façade sur [ObservationDao].
    public List<Observation> references(String idUtilisateur) {
        return observationDao.referencesDeLUtilisateur(idUtilisateur);
    }

    // ---------------------------------------------------------------------------------------------
    // Vue audio unifiée (#audio) : projections LigneObservationAudio par source, façades sur le DAO
    // ---------------------------------------------------------------------------------------------

    /// Lignes audio d'**un passage** (source `ParPassage` de la vue audio unifiée).
    public List<LigneObservationAudio> lignesAudioDuPassage(Long idPassage) {
        return observationDao.lignesAudioDuPassage(idPassage);
    }

    /// Lignes audio d'**un lot de passages** (source `ParPassages`, multisite filtré).
    public List<LigneObservationAudio> lignesAudioDesPassages(List<Long> idPassages) {
        return observationDao.lignesAudioDesPassages(idPassages);
    }

    /// Lignes audio d'**une espèce** d'un utilisateur (source `ParEspece`), filtre `statut` optionnel.
    public List<LigneObservationAudio> lignesAudioDeLEspece(
            String idUtilisateur, String codeEspece, StatutObservation statut) {
        return observationDao.lignesAudioDeLEspece(idUtilisateur, codeEspece, statut);
    }

    /// Lignes audio du **corpus de référence** d'un utilisateur (source `References`).
    public List<LigneObservationAudio> lignesAudioReferences(String idUtilisateur) {
        return observationDao.lignesAudioReferences(idUtilisateur);
    }

    /// Identifiant du jeu de résultats Tadarida d'un passage (`null` si aucun import), pour activer
    /// l'export `_Vu` de la source `ParPassage` sans recharger toute la vue de validation.
    public Optional<Long> resultatsDuPassage(Long idPassage) {
        return resultatsDao.findByPassage(idPassage).map(ResultatsIdentification::id);
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
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(cheminCsv, "cheminCsv");

        ResultatParseTadarida parse = parser.parser(cheminCsv);
        SessionDEnregistrement session = sessionDao
                .trouverParPassage(idPassage)
                .orElseThrow(() -> new RegleMetierException("Aucune session d'enregistrement pour le passage "
                        + idPassage
                        + " : importez d'abord la nuit (P2) avant les résultats Tadarida."));

        Map<String, Long> sequenceParNom = indexerSequences(session.id());
        Set<String> taxonsConnus = chargerCodesTaxons();

        // Lignes **importables** : séquence audio en base ET taxon Tadarida renseigné (`taxon_tadarida`
        // est NOT NULL en base ; Tadarida en assigne toujours un, une ligne sans taxon est un CSV invalide).
        // Tout le reste est ignoré (audio non fourni — cas courant d'un échantillon — ou ligne sans taxon)
        // plutôt que de faire échouer l'import en bloc ou de laisser planter l'insertion.
        List<LigneObservation> retenues = parse.lignes().stream()
                .filter(ligne -> sequenceParNom.containsKey(cleSequence(ligne.nomSequence())))
                .filter(ligne ->
                        ligne.taxonTadarida() != null && !ligne.taxonTadarida().isBlank())
                .toList();
        int ignorees = parse.lignes().size() - retenues.size();
        if (retenues.isEmpty()) {
            throw new RegleMetierException("Séquence d'écoute introuvable : aucune des "
                    + parse.lignes().size()
                    + " observations du CSV n'est importable (séquence audio absente, ou ligne sans taxon)."
                    + " Importez d'abord la nuit de ce passage (carré, année, n° de passage et point doivent"
                    + " correspondre au nom du fichier Tadarida).");
        }

        // Tolérance taxons : auto-souches pour les codes Tadarida hors référentiel des lignes retenues.
        Set<String> taxonsAutoCrees = taxonsHorsReferentiel(retenues, taxonsConnus);
        Set<String> taxonsApresImport = new HashSet<>(taxonsConnus);
        taxonsApresImport.addAll(taxonsAutoCrees);

        ResultatsIdentification aCreer = new ResultatsIdentification(
                null,
                cheminCsv.toString(),
                parse.format().libelle(),
                horloge.maintenant().toString(),
                idPassage);

        // Souches + jeu de résultats + observations dans une **seule transaction** (atomicité, FK).
        ResultatsIdentification[] insere = {null};
        uniteDeTravail.executer(connexion -> {
            taxonDao.enregistrerHorsReferentiel(connexion, taxonsAutoCrees);
            insere[0] = resultatsDao.insert(connexion, aCreer);
            observationDao.insererTout(
                    connexion, construireObservations(retenues, sequenceParNom, taxonsApresImport, insere[0].id()));
        });
        return new BilanImport(insere[0], retenues.size(), ignorees, taxonsAutoCrees.size());
    }

    /// Codes Tadarida hors référentiel parmi `lignes` : taxon principal (stocké tel quel → FK obligatoire)
    /// et taxon observateur (la décision de l'observateur, à préserver), absents de `taxonsConnus`.
    private static Set<String> taxonsHorsReferentiel(List<LigneObservation> lignes, Set<String> taxonsConnus) {
        Set<String> manquants = new LinkedHashSet<>();
        for (LigneObservation ligne : lignes) {
            ajouterSiInconnu(manquants, ligne.taxonTadarida(), taxonsConnus);
            ajouterSiInconnu(manquants, ligne.taxonObservateur(), taxonsConnus);
        }
        return manquants;
    }

    private static void ajouterSiInconnu(Set<String> cible, String code, Set<String> taxonsConnus) {
        if (code != null && !taxonsConnus.contains(code)) {
            cible.add(code);
        }
    }

    private List<Observation> construireObservations(
            List<LigneObservation> lignes,
            Map<String, Long> sequenceParNom,
            Set<String> taxonsConnus,
            Long idResultats) {
        List<Observation> aInserer = new ArrayList<>();
        for (LigneObservation ligne : lignes) {
            Long idSequence = sequenceParNom.get(cleSequence(ligne.nomSequence()));
            aInserer.add(new Observation(
                    null,
                    idSequence,
                    ligne.debutS(),
                    ligne.finS(),
                    ligne.frequenceMedianeHz(),
                    ligne.taxonTadarida(),
                    ligne.probTadarida(),
                    codeOuNull(ligne.taxonAutreTadarida(), taxonsConnus),
                    codeOuNull(ligne.taxonObservateur(), taxonsConnus),
                    ligne.probObservateur(),
                    null,
                    false,
                    ligne.modeValidation(),
                    idResultats));
        }
        return aInserer;
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
        Observation observation = chargerObservation(idObservation);
        Double prob = observation.probObservateur();
        if (prob == null) {
            prob = observation.probTadarida() != null ? observation.probTadarida() : 1.0;
        }
        return majObservateur(observation, observation.taxonTadarida(), prob, ModeValidation.MANUEL);
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
        Observation o = chargerObservation(idObservation);
        Observation mise = new Observation(
                o.id(),
                o.idSequence(),
                o.debutS(),
                o.finS(),
                o.frequenceMedianeHz(),
                o.taxonTadarida(),
                o.probTadarida(),
                o.taxonAutreTadarida(),
                o.taxonObservateur(),
                o.probObservateur(),
                o.commentaire(),
                reference,
                o.modeValidation(),
                o.idResultats());
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
        Objects.requireNonNull(idPassage, "idPassage");
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
                            .map(s -> cleSequence(s.nomFichier()))
                            .orElseThrow(
                                    () -> new RegleMetierException("Séquence " + id + " introuvable pour l'export.")));
            lignes.add(new LigneObservation(
                    nom,
                    o.debutS(),
                    o.finS(),
                    o.frequenceMedianeHz(),
                    o.taxonTadarida(),
                    o.probTadarida(),
                    o.taxonAutreTadarida(),
                    o.taxonObservateur(),
                    o.probObservateur(),
                    o.modeValidation()));
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
        Observation mise = new Observation(
                o.id(),
                o.idSequence(),
                o.debutS(),
                o.finS(),
                o.frequenceMedianeHz(),
                o.taxonTadarida(),
                o.probTadarida(),
                o.taxonAutreTadarida(),
                taxonObservateur,
                probObservateur,
                o.commentaire(),
                o.reference(),
                mode,
                o.idResultats());
        observationDao.update(mise);
        return mise;
    }

    /// Index nom de séquence (sans extension) → id, pour les séquences d'une session.
    private Map<String, Long> indexerSequences(Long idSession) {
        Map<String, Long> index = new HashMap<>();
        for (SequenceDEcoute sequence : sequenceDao.findBySession(idSession)) {
            index.put(cleSequence(sequence.nomFichier()), sequence.id());
        }
        return index;
    }

    private Set<String> chargerCodesTaxons() {
        Set<String> codes = new HashSet<>();
        taxonDao.findAll().forEach(t -> codes.add(t.code()));
        return codes;
    }

    /// Renvoie le code s'il est connu (FK valide), sinon `null` (ex. liste multi-valuée).
    private static String codeOuNull(String code, Set<String> codesConnus) {
        return code != null && codesConnus.contains(code) ? code : null;
    }

    /// Clé de raccrochage d'une séquence : nom de fichier sans extension. Le CSV Tadarida nomme les
    /// séquences sans extension (`…_000`), alors que la base stocke le nom complet (`…_000.wav`,
    /// R8). On compare donc sur la base du nom.
    private static String cleSequence(String nomFichier) {
        if (nomFichier == null) {
            return null;
        }
        String trim = nomFichier.trim();
        int point = trim.lastIndexOf('.');
        return point < 0 ? trim : trim.substring(0, point);
    }
}
