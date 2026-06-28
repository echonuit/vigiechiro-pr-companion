package fr.univ_amu.iut.validation.model.dao;

import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.EspeceObservee;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
            (Double) rs.getObject("prob_tadarida"),
            rs.getString("taxon_other_tadarida"),
            rs.getString("taxon_observer"),
            (Double) rs.getObject("prob_observer"),
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
            rs.getLong("passage_id"),
            rs.getString("carre"),
            rs.getString("point"),
            rs.getInt("annee"),
            rs.getInt("num"),
            rs.getString("date_enr"));

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

    /// CTE commune : une ligne **par observation** de l'utilisateur, avec l'espèce retenue, le statut
    /// dérivé et le contexte (passage/carré/point/année). Les pseudo-taxons bruit/oiseau sont exclus.
    private static final String CTE_OBSERVATIONS = "WITH obs AS ("
            + " SELECT COALESCE(o.taxon_observer, o.taxon_tadarida) AS taxon_code,"
            + " CASE"
            + "   WHEN o.taxon_observer IS NULL THEN 'NON_TOUCHEE'"
            + "   WHEN o.taxon_observer = o.taxon_tadarida AND o.prob_observer IS NOT NULL THEN 'VALIDEE'"
            + "   WHEN o.taxon_observer = o.taxon_tadarida THEN 'NON_TOUCHEE'"
            + "   ELSE 'CORRIGEE'"
            + " END AS statut,"
            + " p.id AS passage_id, p.year AS annee, ms.square_number AS carre,"
            + " ms.friendly_name AS nom_site, lp.id AS point_id"
            + " FROM observation o"
            + " JOIN listening_sequence ls ON o.sequence_id = ls.id"
            + " JOIN recording_session rs ON ls.session_id = rs.id"
            + " JOIN passage p ON rs.passage_id = p.id"
            + " JOIN listening_point lp ON p.point_id = lp.id"
            + " JOIN monitoring_site ms ON lp.site_id = ms.id"
            + " WHERE ms.user_id = ? AND COALESCE(o.taxon_observer, o.taxon_tadarida) NOT IN ('noise', 'piaf'))";

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
            rs.getString("carre"),
            rs.getString("nom_site"),
            rs.getInt("richesse"),
            rs.getInt("nb_obs"),
            rs.getInt("annee_min"),
            rs.getInt("annee_max"));

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
