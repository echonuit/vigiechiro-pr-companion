package fr.univ_amu.iut.validation.model.dao;

import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.EspeceObservee;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/// DAO de l'entité [Observation] (table `observation`, clé auto-incrémentée).
///
/// Démontre plusieurs points :
///
/// - le mapping de l'énum [ModeValidation] (colonne `validation_mode`, `null` →
///   [ModeValidation#NON_VALIDE]) et du booléen `is_reference` (`INTEGER` 0/1) ;
/// - les colonnes numériques **nullable** : `REAL` lus via `rs.getObject`, `INTEGER` nullable
///   (`median_freq_hz`) lu via `getInt` + [ResultSet#wasNull()] (robuste quel que soit le type
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
    private static final String DEBUT_CTE = "WITH obs AS (";
    private static final String ALIAS_STATUT = " AS statut,";

    private static final String SQL_INSERT = "INSERT INTO observation"
            + " (sequence_id, start_time_s, end_time_s, median_freq_hz, taxon_tadarida,"
            + " prob_tadarida, taxon_other_tadarida, taxon_observer, prob_observer, user_comment,"
            + " is_reference, validation_mode, results_id)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final RowMapper<Observation> MAPPER = rs -> new Observation(
            rs.getLong("id"),
            rs.getLong("sequence_id"),
            (Double) rs.getObject("start_time_s"),
            (Double) rs.getObject("end_time_s"),
            entierNullable(rs, "median_freq_hz"),
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
            + " t.vernacular_name_fr AS vern, p.id AS passage_id, ms.square_number AS carre,"
            + " lp.code AS point, p.year AS annee, p.passage_number AS num, p.recording_date AS date_enr"
            + " FROM observation o"
            + " JOIN taxon t ON t.code = COALESCE(o.taxon_observer, o.taxon_tadarida)"
            + " JOIN listening_sequence ls ON o.sequence_id = ls.id"
            + " JOIN recording_session rs ON ls.session_id = rs.id"
            + " JOIN passage p ON rs.passage_id = p.id"
            + " JOIN listening_point lp ON p.point_id = lp.id"
            + " JOIN monitoring_site ms ON lp.site_id = ms.id"
            + " WHERE ms.user_id = ? AND t.code NOT IN ('noise', 'piaf')"
            + " ORDER BY p.year DESC, p.passage_number DESC, t.vernacular_name_fr";

    private static final RowMapper<EspeceObservee> MAPPER_ESPECE = rs -> new EspeceObservee(
            rs.getString("code"),
            rs.getString("latin"),
            rs.getString("vern"),
            rs.getLong(COL_PASSAGE_ID),
            rs.getString(COL_CARRE),
            rs.getString("point"),
            rs.getInt("annee"),
            rs.getInt("num"),
            rs.getString(COL_DATE_ENR));

    /// **Inventaire par espèce** (#analyse) : agrège les observations de l'utilisateur par espèce
    /// (`COALESCE(observateur, tadarida)`, pseudo-taxons exclus), filtrées par `statut` (`null` = tous).
    /// Compteurs : détections, passages/carrés/points distincts, période. Statut dérivé en SQL (CASE),
    /// fidèle à [ServiceValidation#statut].
    public List<EspeceAgregee> inventaireParEspece(String idUtilisateur, StatutObservation statut) {
        String filtre = statut == null ? null : statut.name();
        return projeter(SQL_PAR_ESPECE, MAPPER_ESPECE_AGREGEE, idUtilisateur, filtre, filtre);
    }

    /// **Inventaire par carré** (#analyse) : agrège par carré (site), filtré par `statut` (`null` = tous).
    /// Donne la **richesse** (nombre d'espèces distinctes) et le total de détections par carré.
    public List<CarreEspeces> inventaireParCarre(String idUtilisateur, StatutObservation statut) {
        String filtre = statut == null ? null : statut.name();
        return projeter(SQL_PAR_CARRE, MAPPER_CARRE_ESPECES, idUtilisateur, filtre, filtre);
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
    /// non touchée ; observateur = Tadarida avec probabilité → validée ; sinon (égal sans prob) non
    /// touchée ; observateur ≠ Tadarida → corrigée. Facteur commun aux projections (#analyse).
    private static final String CASE_STATUT = "CASE"
            + "   WHEN o.taxon_observer IS NULL THEN 'NON_TOUCHEE'"
            + "   WHEN o.taxon_observer = o.taxon_tadarida AND o.prob_observer IS NOT NULL THEN 'VALIDEE'"
            + "   WHEN o.taxon_observer = o.taxon_tadarida THEN 'NON_TOUCHEE'"
            + "   ELSE 'CORRIGEE'"
            + " END";

    /// Chaîne de jointures `observation → … → monitoring_site` (le contexte d'un relevé), partagée par
    /// toutes les projections transverses.
    private static final String DE_OBSERVATION_AU_SITE = " FROM observation o"
            + " JOIN listening_sequence ls ON o.sequence_id = ls.id"
            + " JOIN recording_session rs ON ls.session_id = rs.id"
            + " JOIN passage p ON rs.passage_id = p.id"
            + " JOIN listening_point lp ON p.point_id = lp.id"
            + " JOIN monitoring_site ms ON lp.site_id = ms.id";

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

    private static final String SQL_PAR_ESPECE = CTE_OBSERVATIONS
            + " SELECT obs.taxon_code AS code, t.latin_name AS latin, t.vernacular_name_fr AS vern,"
            + " g.name AS groupe, COUNT(*) AS nb_obs, COUNT(DISTINCT obs.passage_id) AS nb_passages,"
            + " COUNT(DISTINCT obs.carre) AS nb_carres, COUNT(DISTINCT obs.point_id) AS nb_points,"
            + " MIN(obs.annee) AS annee_min, MAX(obs.annee) AS annee_max"
            + " FROM obs"
            + " JOIN taxon t ON t.code = obs.taxon_code"
            + " LEFT JOIN taxonomic_group g ON g.id = t.group_id"
            + " WHERE (? IS NULL OR obs.statut = ?)"
            + " GROUP BY obs.taxon_code, t.latin_name, t.vernacular_name_fr, g.name"
            + " ORDER BY nb_obs DESC, t.vernacular_name_fr";

    private static final RowMapper<EspeceAgregee> MAPPER_ESPECE_AGREGEE = rs -> new EspeceAgregee(
            rs.getString("code"),
            rs.getString("latin"),
            rs.getString("vern"),
            rs.getString("groupe"),
            rs.getInt("nb_obs"),
            rs.getInt("nb_passages"),
            rs.getInt("nb_carres"),
            rs.getInt("nb_points"),
            rs.getInt("annee_min"),
            rs.getInt("annee_max"));

    private static final String SQL_PAR_CARRE = CTE_OBSERVATIONS
            + " SELECT obs.carre AS carre, obs.nom_site AS nom_site,"
            + " COUNT(DISTINCT obs.taxon_code) AS richesse, COUNT(*) AS nb_obs,"
            + " MIN(obs.annee) AS annee_min, MAX(obs.annee) AS annee_max"
            + " FROM obs"
            + " WHERE (? IS NULL OR obs.statut = ?)"
            + " GROUP BY obs.carre, obs.nom_site"
            + " ORDER BY richesse DESC, obs.carre";

    private static final RowMapper<CarreEspeces> MAPPER_CARRE_ESPECES = rs -> new CarreEspeces(
            rs.getString(COL_CARRE),
            rs.getString(COL_NOM_SITE),
            rs.getInt("richesse"),
            rs.getInt("nb_obs"),
            rs.getInt("annee_min"),
            rs.getInt("annee_max"));

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
            rs.getLong("seq"),
            rs.getLong(COL_PASSAGE_ID),
            rs.getInt("num_passage"),
            rs.getInt("annee"),
            rs.getString(COL_DATE_ENR),
            rs.getString(COL_CARRE),
            rs.getString("point_code"),
            rs.getString(COL_NOM_SITE),
            rs.getString("tadarida"),
            (Double) rs.getObject(COL_PROB_TADARIDA),
            rs.getString("observer"),
            (Double) rs.getObject(COL_PROB_OBSERVER),
            StatutObservation.valueOf(rs.getString("statut")));

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
            + " o.is_reference AS is_reference, o.user_comment AS commentaire, o.median_freq_hz AS frequence"
            + DE_OBSERVATION_AU_SITE
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
            rs.getLong("seq"),
            rs.getLong(COL_PASSAGE_ID),
            rs.getInt("num_passage"),
            rs.getString(COL_DATE_ENR),
            rs.getString(COL_CARRE),
            rs.getString("point_code"),
            rs.getString(COL_NOM_SITE),
            rs.getString("tadarida"),
            (Double) rs.getObject(COL_PROB_TADARIDA),
            rs.getString("observer"),
            (Double) rs.getObject(COL_PROB_OBSERVER),
            StatutObservation.valueOf(rs.getString("statut")),
            rs.getInt("is_reference") != 0,
            rs.getString("commentaire"),
            entierNullable(rs, "frequence"));

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

    @Override
    public Observation insert(Observation observation) {
        long id = insererEtRecupererCle(SQL_INSERT, valeurs(observation));
        return new Observation(
                id,
                observation.idSequence(),
                observation.debutS(),
                observation.finS(),
                observation.frequenceMedianeHz(),
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
        try (Connection connexion = source.getConnection();
                PreparedStatement ps = connexion.prepareStatement(SQL_INSERT)) {
            boolean autoCommitInitial = connexion.getAutoCommit();
            connexion.setAutoCommit(false);
            try {
                for (Observation observation : observations) {
                    lier(ps, valeurs(observation));
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
            throw new DataAccessException("Échec de l'insertion en lot d'observations", e);
        }
    }

    @Override
    public void update(Observation observation) {
        executerMaj(
                "UPDATE observation SET sequence_id = ?, start_time_s = ?, end_time_s = ?,"
                        + " median_freq_hz = ?, taxon_tadarida = ?, prob_tadarida = ?, taxon_other_tadarida = ?,"
                        + " taxon_observer = ?, prob_observer = ?, user_comment = ?, is_reference = ?,"
                        + " validation_mode = ?, results_id = ? WHERE id = ?",
                observation.idSequence(),
                observation.debutS(),
                observation.finS(),
                observation.frequenceMedianeHz(),
                observation.taxonTadarida(),
                observation.probTadarida(),
                observation.taxonAutreTadarida(),
                observation.taxonObservateur(),
                observation.probObservateur(),
                observation.commentaire(),
                observation.reference() ? 1 : 0,
                observation.modeValidation().libelle(),
                observation.idResultats(),
                observation.id());
    }

    /// Valeurs positionnelles de [#SQL_INSERT], dans l'ordre des colonnes.
    private static Object[] valeurs(Observation observation) {
        return new Object[] {
            observation.idSequence(),
            observation.debutS(),
            observation.finS(),
            observation.frequenceMedianeHz(),
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
}
