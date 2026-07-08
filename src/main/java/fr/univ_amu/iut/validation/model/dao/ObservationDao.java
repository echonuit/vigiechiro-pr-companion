package fr.univ_amu.iut.validation.model.dao;

import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.EspeceObservee;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/// DAO de l'entité [Observation] (table `observation`, clé auto-incrémentée).
///
/// Démontre plusieurs points :
///
/// - le mapping de l'énum [ModeValidation] (colonne `validation_mode`, `null` →
///   [ModeValidation#NON_VALIDE]) et du booléen `is_reference` (`INTEGER` 0/1) ;
/// - les colonnes numériques **nullable** : `REAL` lus via `rs.getObject`, `INTEGER` nullable
///   (`median_freq_khz`) lu via `getInt` + [ResultSet#wasNull()] (robuste quel que soit le type
///   retourné par le pilote) ;
/// - un **insert en lot** ([#insererTout(List)]) regroupé en une seule transaction
///   (`addBatch`/`executeBatch`) : l'import d'un CSV Tadarida crée des centaines d'observations
///   d'un coup, un aller-retour par ligne serait coûteux ;
/// - la requête métier [#findByResults(Long)] (toutes les observations d'un jeu de résultats).
public class ObservationDao extends DaoGenerique<Observation, Long> {

    /// Noms de colonnes projetées et fragments SQL partagés par les mappers/CTE transverses
    /// (factorisés pour éviter la duplication de littéraux entre projections analyse et audio).
    private static final String COL_CARRE = "carre";

    private static final String COL_PROB_TADARIDA = "prob_tadarida";
    private static final String COL_PROB_OBSERVER = "prob_observer";
    private static final String COL_PASSAGE_ID = "passage_id";
    private static final String COL_DATE_ENR = "date_enr";
    private static final String COL_NOM_SITE = "nom_site";
    private static final String COL_GROUPE = "groupe";
    private static final String COL_ANNEE = "annee";
    private static final String COL_STATUT = "statut";
    private static final String DEBUT_CTE = "WITH obs AS (";
    private static final String ALIAS_STATUT = " AS statut,";

    /// Alias de colonnes projetés par plusieurs mappers audio (séquence, n° de passage, code du point).
    private static final String COL_SEQ = "seq";
    private static final String COL_NUM_PASSAGE = "num_passage";
    private static final String COL_POINT_CODE = "point_code";

    /// Jointures communes `listening_sequence (ls) → recording_session → passage → point → site` : le
    /// **contexte d'un relevé**, partagé par toutes les projections transverses (observations, espèces,
    /// séquences non identifiées). L'alias `ls` doit être introduit par le fragment appelant.
    private static final String JOIN_SESSION_AU_SITE = " JOIN recording_session rs ON ls.session_id = rs.id"
            + " JOIN passage p ON rs.passage_id = p.id"
            + " JOIN listening_point lp ON p.point_id = lp.id"
            + " JOIN monitoring_site ms ON lp.site_id = ms.id";

    private static final String SQL_INSERT = "INSERT INTO observation"
            + " (sequence_id, start_time_s, end_time_s, median_freq_khz, taxon_tadarida,"
            + " prob_tadarida, taxon_other_tadarida, taxon_observer, prob_observer, user_comment,"
            + " is_reference, validation_mode, results_id)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE = "UPDATE observation SET sequence_id = ?, start_time_s = ?,"
            + " end_time_s = ?, median_freq_khz = ?, taxon_tadarida = ?, prob_tadarida = ?,"
            + " taxon_other_tadarida = ?, taxon_observer = ?, prob_observer = ?, user_comment = ?,"
            + " is_reference = ?, validation_mode = ?, results_id = ? WHERE id = ?";

    private static final RowMapper<Observation> MAPPER = rs -> new Observation(
            rs.getLong("id"),
            rs.getLong("sequence_id"),
            (Double) rs.getObject("start_time_s"),
            (Double) rs.getObject("end_time_s"),
            entierNullable(rs, "median_freq_khz"),
            rs.getString("taxon_tadarida"),
            (Double) rs.getObject(COL_PROB_TADARIDA),
            rs.getString("taxon_other_tadarida"),
            rs.getString("taxon_observer"),
            (Double) rs.getObject(COL_PROB_OBSERVER),
            rs.getString("user_comment"),
            rs.getInt("is_reference") != 0,
            ModeValidation.parLibelle(rs.getString("validation_mode")),
            rs.getLong("results_id"));

    public ObservationDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "observation";
    }

    @Override
    protected String colonneCle() {
        return "id";
    }

    @Override
    protected RowMapper<Observation> mapper() {
        return MAPPER;
    }

    /// Observations agrégées par un jeu de résultats, triées par id (ordre d'import).
    public List<Observation> findByResults(Long idResultats) {
        return query("SELECT * FROM observation WHERE results_id = ? ORDER BY id", MAPPER, idResultats);
    }

    /// Observations détectées dans une séquence d'écoute donnée, triées par temps de début.
    public List<Observation> findBySequence(Long idSequence) {
        return query("SELECT * FROM observation WHERE sequence_id = ? ORDER BY start_time_s", MAPPER, idSequence);
    }

    /// Observations **marquées référence** (`is_reference`) de l'utilisateur (#audio) : la source
    /// « Références » de la vue audio unifiée (corpus de sons de référence, ex-bibliothèque). Jointure
    /// jusqu'au site pour le périmètre utilisateur ; triées par id.
    public List<Observation> referencesDeLUtilisateur(String idUtilisateur) {
        return query(
                "SELECT o.*" + DE_OBSERVATION_AU_SITE + " WHERE ms.user_id = ? AND o.is_reference = 1 ORDER BY o.id",
                MAPPER,
                idUtilisateur);
    }

    /// Projection des **espèces observées par l'utilisateur, rattachées à leur passage** (#323). Une ligne
    /// par couple (espèce, passage) — `DISTINCT` car un passage peut contenir plusieurs observations de la
    /// même espèce. L'espèce est le taxon **validé** s'il existe, sinon la proposition Tadarida. Les
    /// **pseudo-taxons** `noise` (bruit) et `piaf` (oiseau) sont exclus : ce ne sont pas des espèces de
    /// chiroptères. Triée du passage le plus récent au plus ancien (le plafond garde les plus récents).
    public List<EspeceObservee> especesObserveesParUtilisateur(String idUtilisateur) {
        return projeter(SQL_ESPECES, MAPPER_ESPECE, idUtilisateur);
    }

    private static final String SQL_ESPECES = "SELECT DISTINCT t.code AS code, t.latin_name AS latin,"
            + " t.vernacular_name_fr AS vern, g.name AS groupe, p.id AS passage_id, ms.square_number AS carre,"
            + " lp.code AS point, p.year AS annee, p.passage_number AS num, p.recording_date AS date_enr"
            + " FROM observation o"
            + " JOIN taxon t ON t.code = COALESCE(o.taxon_observer, o.taxon_tadarida)"
            + " LEFT JOIN taxonomic_group g ON g.id = t.group_id"
            + " JOIN listening_sequence ls ON o.sequence_id = ls.id"
            + JOIN_SESSION_AU_SITE
            + " WHERE ms.user_id = ? AND t.code NOT IN ('noise', 'piaf')"
            + " ORDER BY p.year DESC, p.passage_number DESC, t.vernacular_name_fr";

    private static final RowMapper<EspeceObservee> MAPPER_ESPECE = rs -> new EspeceObservee(
            rs.getString("code"),
            rs.getString("latin"),
            rs.getString("vern"),
            rs.getString(COL_GROUPE),
            rs.getLong(COL_PASSAGE_ID),
            rs.getString(COL_CARRE),
            rs.getString("point"),
            rs.getInt(COL_ANNEE),
            rs.getInt("num"),
            rs.getString(COL_DATE_ENR));

    /// **Observations enrichies** de l'utilisateur (#analyse, #537 étape 4) : une ligne [ObservationAnalyse]
    /// par observation (`COALESCE(observateur, tadarida)`, pseudo-taxons exclus), avec l'espèce (latin,
    /// vernaculaire, groupe), le statut dérivé (CASE, fidèle à [ServiceValidation#statut]) et le contexte
    /// (passage, année, carré, site, point). **Sans filtre de statut** : le filtrage (statut, texte…) et
    /// l'agrégation (par espèce / par carré) se font **côté client** via [AgregationAnalyse].
    public List<ObservationAnalyse> observationsAnalyse(String idUtilisateur) {
        return projeter(SQL_OBSERVATIONS_ANALYSE, MAPPER_OBSERVATION_ANALYSE, idUtilisateur);
    }

    /// **Détail d'une espèce** (#analyse) : toutes les observations de l'utilisateur portant sur l'espèce
    /// `codeEspece` (au sens `COALESCE(observateur, tadarida)`), **à travers les passages**, filtrées par
    /// `statut` (`null` = tous). Une ligne [ObservationEspece] par observation, ordonnée année décroissante
    /// puis date/point, avec les clés de navigation (ouvrir le passage) et d'écoute (séquence).
    public List<ObservationEspece> observationsDeLEspece(
            String idUtilisateur, String codeEspece, StatutObservation statut) {
        String filtre = statut == null ? null : statut.name();
        return projeter(SQL_OBSERVATIONS_ESPECE, MAPPER_OBSERVATION_ESPECE, idUtilisateur, codeEspece, filtre, filtre);
    }

    /// Statut de revue **dérivé en SQL** (CASE), fidèle à `ServiceValidation#statut` : pas d'observateur →
    /// non touchée ; observateur = Tadarida → validée ; observateur ≠ Tadarida → corrigée. La décision
    /// tient à la **présence du `taxon_observer`**, pas à sa probabilité (un _Vu réel peut porter une
    /// confiance textuelle « SUR » lue comme prob inconnue). Facteur commun aux projections (#analyse).
    private static final String CASE_STATUT = "CASE"
            + "   WHEN o.taxon_observer IS NULL THEN 'NON_TOUCHEE'"
            + "   WHEN o.taxon_observer = o.taxon_tadarida THEN 'VALIDEE'"
            + "   ELSE 'CORRIGEE'"
            + " END";

    /// Chaîne de jointures `observation → … → monitoring_site` (le contexte d'un relevé), partagée par
    /// toutes les projections transverses.
    private static final String DE_OBSERVATION_AU_SITE =
            " FROM observation o" + " JOIN listening_sequence ls ON o.sequence_id = ls.id" + JOIN_SESSION_AU_SITE;

    /// Périmètre commun : observations **de l'utilisateur** (`?`), pseudo-taxons bruit/oiseau exclus.
    private static final String FILTRE_UTILISATEUR_HORS_PSEUDO =
            " WHERE ms.user_id = ? AND COALESCE(o.taxon_observer, o.taxon_tadarida) NOT IN ('noise', 'piaf')";

    /// CTE commune : une ligne **par observation** de l'utilisateur, avec l'espèce retenue, le statut
    /// dérivé et le contexte (passage/carré/point/année). Les pseudo-taxons bruit/oiseau sont exclus.
    private static final String CTE_OBSERVATIONS = DEBUT_CTE
            + " SELECT COALESCE(o.taxon_observer, o.taxon_tadarida) AS taxon_code,"
            + CASE_STATUT + ALIAS_STATUT
            + " p.id AS passage_id, p.year AS annee, ms.square_number AS carre,"
            + " ms.friendly_name AS nom_site, lp.id AS point_id"
            + DE_OBSERVATION_AU_SITE
            + FILTRE_UTILISATEUR_HORS_PSEUDO
            + ")";

    /// Observations enrichies (#537 étape 4) : la CTE `obs` (une ligne par observation, contexte + statut
    /// dérivé) jointe au taxon pour le nom latin/vernaculaire et le groupe. **Aucun** filtre de statut ni
    /// `GROUP BY` : le filtrage et l'agrégation se font côté client ([AgregationAnalyse]).
    private static final String SQL_OBSERVATIONS_ANALYSE = CTE_OBSERVATIONS
            + " SELECT obs.taxon_code AS code, t.latin_name AS latin, t.vernacular_name_fr AS vern,"
            + " g.name AS groupe, obs.statut AS statut, obs.passage_id AS passage_id, obs.annee AS annee,"
            + " obs.carre AS carre, obs.nom_site AS nom_site, obs.point_id AS point_id"
            + " FROM obs"
            + " JOIN taxon t ON t.code = obs.taxon_code"
            + " LEFT JOIN taxonomic_group g ON g.id = t.group_id";

    private static final RowMapper<ObservationAnalyse> MAPPER_OBSERVATION_ANALYSE = rs -> new ObservationAnalyse(
            rs.getString("code"),
            rs.getString("latin"),
            rs.getString("vern"),
            rs.getString(COL_GROUPE),
            StatutObservation.valueOf(rs.getString(COL_STATUT)),
            rs.getLong(COL_PASSAGE_ID),
            rs.getInt(COL_ANNEE),
            rs.getString(COL_CARRE),
            rs.getString(COL_NOM_SITE),
            rs.getLong("point_id"));

    /// CTE de **détail** : une ligne par observation (clé, séquence, contexte passage/carré/point, les deux
    /// taxons et probabilités, statut dérivé), pour le panneau « observations d'une espèce » (#analyse).
    private static final String CTE_DETAIL = DEBUT_CTE
            + " SELECT o.id AS id, o.sequence_id AS seq,"
            + " COALESCE(o.taxon_observer, o.taxon_tadarida) AS taxon_code,"
            + CASE_STATUT + ALIAS_STATUT
            + " p.id AS passage_id, p.passage_number AS num_passage, p.year AS annee,"
            + " p.recording_date AS date_enr, ms.square_number AS carre, ms.friendly_name AS nom_site,"
            + " lp.code AS point_code, o.taxon_tadarida AS tadarida, o.prob_tadarida AS prob_tadarida,"
            + " o.taxon_observer AS observer, o.prob_observer AS prob_observer"
            + DE_OBSERVATION_AU_SITE
            + FILTRE_UTILISATEUR_HORS_PSEUDO
            + ")";

    private static final String SQL_OBSERVATIONS_ESPECE = CTE_DETAIL
            + " SELECT * FROM obs"
            + " WHERE obs.taxon_code = ? AND (? IS NULL OR obs.statut = ?)"
            + " ORDER BY obs.annee DESC, obs.date_enr, obs.point_code";

    private static final RowMapper<ObservationEspece> MAPPER_OBSERVATION_ESPECE = rs -> new ObservationEspece(
            rs.getLong("id"),
            rs.getLong(COL_SEQ),
            rs.getLong(COL_PASSAGE_ID),
            rs.getInt(COL_NUM_PASSAGE),
            rs.getInt(COL_ANNEE),
            rs.getString(COL_DATE_ENR),
            rs.getString(COL_CARRE),
            rs.getString(COL_POINT_CODE),
            rs.getString(COL_NOM_SITE),
            rs.getString("tadarida"),
            (Double) rs.getObject(COL_PROB_TADARIDA),
            rs.getString("observer"),
            (Double) rs.getObject(COL_PROB_OBSERVER),
            StatutObservation.valueOf(rs.getString(COL_STATUT)));

    // --- Projection unifiée pour la vue audio (#audio) : observations + contexte passage + champs
    // d'archivage (reference/commentaire/fréquence). Pas d'exclusion de pseudo-taxons : la validation
    // revoit toutes les séquences. Une CTE commune, quatre sélections selon la source. ---

    /// CTE commune : une ligne par observation avec son contexte passage/carré/point, ses deux taxons +
    /// probabilités, son statut dérivé, et ses champs d'archivage.
    private static final String CTE_AUDIO = DEBUT_CTE
            + " SELECT o.id AS id, o.sequence_id AS seq,"
            + " COALESCE(o.taxon_observer, o.taxon_tadarida) AS taxon_code,"
            + CASE_STATUT + ALIAS_STATUT
            + " p.id AS passage_id, p.passage_number AS num_passage, p.recording_date AS date_enr,"
            + " ms.square_number AS carre, ms.friendly_name AS nom_site, lp.code AS point_code,"
            + " ms.user_id AS user_id, o.taxon_tadarida AS tadarida, o.prob_tadarida AS prob_tadarida,"
            + " o.taxon_observer AS observer, o.prob_observer AS prob_observer,"
            + " o.is_reference AS is_reference, o.user_comment AS commentaire, o.median_freq_khz AS frequence,"
            + " te.vernacular_name_fr AS nom_espece, tt.vernacular_name_fr AS nom_tadarida,"
            + " g.name AS groupe,"
            + " ls.file_name AS nom_fichier, ls.recorded_at AS recorded_at,"
            + " o.start_time_s AS debut_s, o.end_time_s AS fin_s"
            + DE_OBSERVATION_AU_SITE
            + " LEFT JOIN taxon te ON te.code = COALESCE(o.taxon_observer, o.taxon_tadarida)"
            + " LEFT JOIN taxon tt ON tt.code = o.taxon_tadarida"
            + " LEFT JOIN taxonomic_group g ON g.id = te.group_id"
            + ")";

    private static final String SELECT_AUDIO = CTE_AUDIO + " SELECT * FROM obs WHERE obs.";

    /// Ordre **de revue** commun à toutes les sources audio : **chronologique** (date d'enregistrement
    /// du passage croissante = on revoit la nuit la plus ancienne d'abord), puis par **point d'écoute**
    /// et enfin par **id** (tri stable et déterministe à l'intérieur d'un passage). Choisi pour qu'une
    /// liste de travail se parcoure dans l'ordre naturel du terrain, identique quelle que soit la source
    /// (un passage, un lot multisite, une espèce ou les références) : pas de « plus récent d'abord » ni
    /// d'ordre propre au lot multisite. Verrouillé par `ObservationDaoTest#lignes_audio_ordre_de_revue`.
    private static final String ORDRE_AUDIO = " ORDER BY obs.date_enr, obs.point_code, obs.id";

    private static final RowMapper<LigneObservationAudio> MAPPER_LIGNE_AUDIO = rs -> new LigneObservationAudio(
            rs.getLong("id"),
            rs.getLong(COL_SEQ),
            rs.getLong(COL_PASSAGE_ID),
            rs.getInt(COL_NUM_PASSAGE),
            rs.getString(COL_DATE_ENR),
            rs.getString(COL_CARRE),
            rs.getString(COL_POINT_CODE),
            rs.getString(COL_NOM_SITE),
            rs.getString("tadarida"),
            (Double) rs.getObject(COL_PROB_TADARIDA),
            rs.getString("observer"),
            (Double) rs.getObject(COL_PROB_OBSERVER),
            StatutObservation.valueOf(rs.getString(COL_STATUT)),
            rs.getInt("is_reference") != 0,
            rs.getString("commentaire"),
            entierNullable(rs, "frequence"),
            rs.getString("nom_espece"),
            rs.getString("nom_tadarida"),
            rs.getString(COL_GROUPE),
            rs.getString("nom_fichier"),
            (Double) rs.getObject("debut_s"),
            (Double) rs.getObject("fin_s"),
            heureCaptureDe(rs.getString("recorded_at")));

    /// Source **Passage** : toutes les observations d'un passage (pseudo-taxons compris : on revoit tout).
    public List<LigneObservationAudio> lignesAudioDuPassage(Long idPassage) {
        return projeter(SELECT_AUDIO + "passage_id = ?" + ORDRE_AUDIO, MAPPER_LIGNE_AUDIO, idPassage);
    }

    /// Source **Lot de passages** : observations à travers plusieurs passages (lot filtré du multisite).
    public List<LigneObservationAudio> lignesAudioDesPassages(List<Long> idPassages) {
        if (idPassages.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(idPassages.size(), "?"));
        return projeter(
                SELECT_AUDIO + "passage_id IN (" + placeholders + ")" + ORDRE_AUDIO,
                MAPPER_LIGNE_AUDIO,
                idPassages.toArray());
    }

    /// Source **Espèce** : observations d'une espèce à travers les passages de l'utilisateur, filtrées par
    /// `statut` (`null` = tous).
    public List<LigneObservationAudio> lignesAudioDeLEspece(
            String idUtilisateur, String codeEspece, StatutObservation statut) {
        String filtre = statut == null ? null : statut.name();
        return projeter(
                SELECT_AUDIO + "user_id = ? AND obs.taxon_code = ? AND (? IS NULL OR obs.statut = ?)" + ORDRE_AUDIO,
                MAPPER_LIGNE_AUDIO,
                idUtilisateur,
                codeEspece,
                filtre,
                filtre);
    }

    /// Source **Références** : observations marquées `is_reference` de l'utilisateur (corpus de référence).
    public List<LigneObservationAudio> lignesAudioReferences(String idUtilisateur) {
        return projeter(
                SELECT_AUDIO + "user_id = ? AND obs.is_reference = 1" + ORDRE_AUDIO, MAPPER_LIGNE_AUDIO, idUtilisateur);
    }

    /// Jointures `listening_sequence → … → monitoring_site` : le **contexte** d'une séquence (passage /
    /// carré / point) **sans passer par une observation**. Pivot des séquences non identifiées, dont la
    /// liste part des enregistrements (et non des observations, absentes par définition).
    private static final String DE_SEQUENCE_AU_SITE = " FROM listening_sequence ls" + JOIN_SESSION_AU_SITE;

    /// Séquences d'un passage **sans aucune observation** (présentes sur disque, absentes du CSV Tadarida),
    /// projetées comme des lignes audio « à revoir » **sans taxon** : écoutables (via `idSequence`), mais
    /// non rattachées à une observation. Ordre chronologique (horodatage puis nom de fichier).
    private static final String SQL_LIGNES_NON_IDENTIFIEES = "SELECT ls.id AS seq,"
            + " p.id AS passage_id, p.passage_number AS num_passage, p.recording_date AS date_enr,"
            + " ms.square_number AS carre, ms.friendly_name AS nom_site, lp.code AS point_code,"
            + " ls.file_name AS nom_fichier, ls.recorded_at AS recorded_at"
            + DE_SEQUENCE_AU_SITE
            + " WHERE rs.passage_id = ?"
            + "   AND NOT EXISTS (SELECT 1 FROM observation o WHERE o.sequence_id = ls.id)"
            + " ORDER BY ls.recorded_at, ls.file_name";

    /// Projette une séquence non identifiée en [LigneObservationAudio] : **pas d'observation**
    /// (`idObservation` nul), aucun taxon/probabilité/fréquence Tadarida, statut **à revoir**
    /// ([StatutObservation#NON_TOUCHEE]). Seuls l'`idSequence`, le contexte du passage et l'horodatage
    /// sont renseignés — assez pour situer et écouter la séquence.
    private static final RowMapper<LigneObservationAudio> MAPPER_LIGNE_NON_IDENTIFIEE = rs -> new LigneObservationAudio(
            null, // idObservation : séquence sans observation
            rs.getLong(COL_SEQ),
            rs.getLong(COL_PASSAGE_ID),
            rs.getInt(COL_NUM_PASSAGE),
            rs.getString(COL_DATE_ENR),
            rs.getString(COL_CARRE),
            rs.getString(COL_POINT_CODE),
            rs.getString(COL_NOM_SITE),
            null, // taxonTadarida
            null, // probTadarida
            null, // taxonObservateur
            null, // probObservateur
            StatutObservation.NON_TOUCHEE,
            false, // reference
            null, // commentaire
            null, // frequenceKHz
            null, // nomEspece
            null, // nomTadarida
            null, // groupe
            rs.getString("nom_fichier"),
            null, // debutS
            null, // finS
            heureCaptureDe(rs.getString("recorded_at")));

    /// Source **Non identifiés** : les séquences d'un passage **sans observation Tadarida** (à écouter, en
    /// vue d'une validation manuelle). L'écoute ne dépend pas d'une observation, seulement de la séquence.
    public List<LigneObservationAudio> lignesAudioNonIdentifiees(Long idPassage) {
        return projeter(SQL_LIGNES_NON_IDENTIFIEES, MAPPER_LIGNE_NON_IDENTIFIEE, idPassage);
    }

    @Override
    public Observation insert(Observation observation) {
        long id = insererEtRecupererCle(SQL_INSERT, valeurs(observation));
        return new Observation(
                id,
                observation.idSequence(),
                observation.debutS(),
                observation.finS(),
                observation.frequenceMedianeKHz(),
                observation.taxonTadarida(),
                observation.probTadarida(),
                observation.taxonAutreTadarida(),
                observation.taxonObservateur(),
                observation.probObservateur(),
                observation.commentaire(),
                observation.reference(),
                observation.modeValidation(),
                observation.idResultats());
    }

    /// Variante transactionnelle : insère le lot sur la `connexion` fournie, sans gérer le
    /// commit/rollback (c'est l'unité de travail appelante qui s'en charge). Permet de grouper
    /// l'insertion des observations avec la création de leur jeu de résultats en une seule
    /// transaction (import atomique). Propage la [SQLException].
    public void insererTout(Connection connexion, List<Observation> observations) throws SQLException {
        try (PreparedStatement ps = connexion.prepareStatement(SQL_INSERT)) {
            for (Observation observation : observations) {
                lier(ps, valeurs(observation));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /// Insère un lot d'observations dans une **transaction unique** (tout réussit ou tout est
    /// annulé). Renvoie le nombre de lignes insérées.
    public int insererTout(List<Observation> observations) {
        return ecrireLotTransactionnel(SQL_INSERT, observations, ObservationDao::valeurs);
    }

    /// Exécute un lot d'écritures (`sql` + valeurs positionnelles par observation) dans **une transaction**
    /// (tout ou rien) : autocommit désactivé, `addBatch`/`executeBatch`, `commit`, `rollback` sur erreur.
    /// Factorise les insertions et les mises à jour en lot (#479). Renvoie le nombre de lignes écrites.
    private int ecrireLotTransactionnel(
            String sql, List<Observation> observations, java.util.function.Function<Observation, Object[]> valeurs) {
        try (Connection connexion = source.getConnection();
                PreparedStatement ps = connexion.prepareStatement(sql)) {
            boolean autoCommitInitial = connexion.getAutoCommit();
            connexion.setAutoCommit(false);
            try {
                for (Observation observation : observations) {
                    lier(ps, valeurs.apply(observation));
                    ps.addBatch();
                }
                int total = 0;
                for (int compte : ps.executeBatch()) {
                    total += Math.max(compte, 0);
                }
                connexion.commit();
                return total;
            } catch (SQLException erreur) {
                connexion.rollback();
                throw erreur;
            } finally {
                connexion.setAutoCommit(autoCommitInitial);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Échec de l'écriture en lot d'observations", e);
        }
    }

    @Override
    public void update(Observation observation) {
        executerMaj(SQL_UPDATE, valeursUpdate(observation));
    }

    /// Met à jour un **lot** d'observations dans une **transaction unique** (tout réussit ou tout est
    /// annulé), miroir de [#insererTout(List)]. Sert aux actions groupées de la revue (#479). Renvoie le
    /// nombre de lignes écrites.
    public int updateTout(List<Observation> observations) {
        return ecrireLotTransactionnel(SQL_UPDATE, observations, ObservationDao::valeursUpdate);
    }

    /// Valeurs positionnelles de [#SQL_UPDATE] : les colonnes (comme [#valeurs]) suivies de l'`id` (clause
    /// `WHERE id = ?`).
    private static Object[] valeursUpdate(Observation observation) {
        Object[] colonnes = valeurs(observation);
        Object[] avecId = new Object[colonnes.length + 1];
        System.arraycopy(colonnes, 0, avecId, 0, colonnes.length);
        avecId[colonnes.length] = observation.id();
        return avecId;
    }

    /// Valeurs positionnelles de [#SQL_INSERT], dans l'ordre des colonnes.
    private static Object[] valeurs(Observation observation) {
        return new Object[] {
            observation.idSequence(),
            observation.debutS(),
            observation.finS(),
            observation.frequenceMedianeKHz(),
            observation.taxonTadarida(),
            observation.probTadarida(),
            observation.taxonAutreTadarida(),
            observation.taxonObservateur(),
            observation.probObservateur(),
            observation.commentaire(),
            observation.reference() ? 1 : 0,
            observation.modeValidation().libelle(),
            observation.idResultats()
        };
    }

    /// Lie des paramètres positionnels (1-based) en gérant explicitement les valeurs nulles.
    private static void lier(PreparedStatement ps, Object[] parametres) throws SQLException {
        for (int i = 0; i < parametres.length; i++) {
            if (parametres[i] == null) {
                ps.setString(i + 1, null);
            } else {
                ps.setObject(i + 1, parametres[i]);
            }
        }
    }

    /// Lit un `INTEGER` nullable : `null` si la colonne vaut SQL NULL, sinon sa valeur.
    private static Integer entierNullable(ResultSet rs, String colonne) throws SQLException {
        int valeur = rs.getInt(colonne);
        return rs.wasNull() ? null : valeur;
    }

    /// Instant de capture depuis l'horodatage persisté `recorded_at` (image ISO-8601 d'un [LocalDateTime],
    /// #530) : `null` si la séquence n'est pas horodatée. On projette l'**instant complet** (date + heure)
    /// pour un tri chronologique correct à cheval sur minuit (cf. [LigneObservationAudio#heureCapture]).
    private static LocalDateTime heureCaptureDe(String recordedAt) {
        return recordedAt == null ? null : LocalDateTime.parse(recordedAt);
    }
}
