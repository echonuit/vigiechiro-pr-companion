package fr.univ_amu.iut.validation.model.dao;

import fr.univ_amu.iut.commun.persistence.ProjectionGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.EspeceObservee;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;

/// Projections **analyse / espèces** de la table `observation` (#1193) : les lectures transverses
/// « quelles espèces, où, combien » servies aux features `analyse` (Espèces & observations) et
/// `recherche` (recherche globale). Extraites d'[ObservationDao] (qui garde le CRUD et les écritures
/// en lot) pour séparer les familles de requêtes par client. **Lecture seule** : aucune écriture ici.
///
/// Les fragments SQL (jointures de contexte, statut dérivé, alias) sont partagés avec les autres DAO
/// de la table via [FragmentsSqlObservation].
public class ProjectionsAnalyseDao extends ProjectionGenerique {

    private static final String SQL_ESPECES = "SELECT DISTINCT t.code AS code, t.latin_name AS latin,"
            + " t.vernacular_name_fr AS vern, g.name AS groupe, p.id AS passage_id, ms.square_number AS carre,"
            + " lp.code AS point, p.year AS annee, p.passage_number AS num, p.recording_date AS date_enr"
            + " FROM observation o"
            + " JOIN taxon t ON t.code = COALESCE(o.taxon_observer, o.taxon_tadarida)"
            + " LEFT JOIN taxonomic_group g ON g.id = t.group_id"
            + " JOIN listening_sequence ls ON o.sequence_id = ls.id"
            + FragmentsSqlObservation.JOIN_SESSION_AU_SITE
            + " WHERE ms.user_id = ? AND t.code NOT IN ('noise', 'piaf')"
            + " ORDER BY p.year DESC, p.passage_number DESC, t.vernacular_name_fr";

    private static final RowMapper<EspeceObservee> MAPPER_ESPECE = rs -> new EspeceObservee(
            rs.getString("code"),
            rs.getString("latin"),
            rs.getString("vern"),
            rs.getString(FragmentsSqlObservation.COL_GROUPE),
            rs.getLong(FragmentsSqlObservation.COL_PASSAGE_ID),
            rs.getString(FragmentsSqlObservation.COL_CARRE),
            rs.getString("point"),
            rs.getInt(FragmentsSqlObservation.COL_ANNEE),
            rs.getInt("num"),
            rs.getString(FragmentsSqlObservation.COL_DATE_ENR));

    /// CTE commune : une ligne **par observation** de l'utilisateur, avec l'espèce retenue, le statut
    /// dérivé et le contexte (passage/carré/point/année). Les pseudo-taxons bruit/oiseau sont exclus.
    private static final String CTE_OBSERVATIONS = FragmentsSqlObservation.DEBUT_CTE
            + " SELECT COALESCE(o.taxon_observer, o.taxon_tadarida) AS taxon_code,"
            + FragmentsSqlObservation.CASE_STATUT
            + FragmentsSqlObservation.ALIAS_STATUT
            + " p.id AS passage_id, p.year AS annee, ms.square_number AS carre,"
            + " ms.friendly_name AS nom_site, lp.id AS point_id"
            + FragmentsSqlObservation.DE_OBSERVATION_AU_SITE
            + FragmentsSqlObservation.FILTRE_UTILISATEUR_HORS_PSEUDO
            + ")";

    /// Observations enrichies (#537 étape 4) : la CTE `obs` (une ligne par observation, contexte + statut
    /// dérivé) jointe au taxon pour le nom latin/vernaculaire et le groupe. **Aucun** filtre de statut ni
    /// `GROUP BY` : le filtrage et l'agrégation se font côté client (`AgregationAnalyse`).
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
            rs.getString(FragmentsSqlObservation.COL_GROUPE),
            StatutObservation.valueOf(rs.getString(FragmentsSqlObservation.COL_STATUT)),
            rs.getLong(FragmentsSqlObservation.COL_PASSAGE_ID),
            rs.getInt(FragmentsSqlObservation.COL_ANNEE),
            rs.getString(FragmentsSqlObservation.COL_CARRE),
            rs.getString(FragmentsSqlObservation.COL_NOM_SITE),
            rs.getLong("point_id"));

    /// CTE de **détail** : une ligne par observation (clé, séquence, contexte passage/carré/point, les deux
    /// taxons et probabilités, statut dérivé), pour le panneau « observations d'une espèce » (#analyse).
    private static final String CTE_DETAIL = FragmentsSqlObservation.DEBUT_CTE
            + " SELECT o.id AS id, o.sequence_id AS seq,"
            + " COALESCE(o.taxon_observer, o.taxon_tadarida) AS taxon_code,"
            + FragmentsSqlObservation.CASE_STATUT
            + FragmentsSqlObservation.ALIAS_STATUT
            + " p.id AS passage_id, p.passage_number AS num_passage, p.year AS annee,"
            + " p.recording_date AS date_enr, ms.square_number AS carre, ms.friendly_name AS nom_site,"
            + " lp.code AS point_code, o.taxon_tadarida AS tadarida, o.prob_tadarida AS prob_tadarida,"
            + " o.taxon_observer AS observer, o.prob_observer AS prob_observer"
            + FragmentsSqlObservation.DE_OBSERVATION_AU_SITE
            + FragmentsSqlObservation.FILTRE_UTILISATEUR_HORS_PSEUDO
            + ")";

    private static final String SQL_OBSERVATIONS_ESPECE = CTE_DETAIL
            + " SELECT * FROM obs"
            + " WHERE obs.taxon_code = ? AND (? IS NULL OR obs.statut = ?)"
            + " ORDER BY obs.annee DESC, obs.date_enr, obs.point_code";

    private static final RowMapper<ObservationEspece> MAPPER_OBSERVATION_ESPECE = rs -> new ObservationEspece(
            rs.getLong("id"),
            rs.getLong(FragmentsSqlObservation.COL_SEQ),
            rs.getLong(FragmentsSqlObservation.COL_PASSAGE_ID),
            rs.getInt(FragmentsSqlObservation.COL_NUM_PASSAGE),
            rs.getInt(FragmentsSqlObservation.COL_ANNEE),
            rs.getString(FragmentsSqlObservation.COL_DATE_ENR),
            rs.getString(FragmentsSqlObservation.COL_CARRE),
            rs.getString(FragmentsSqlObservation.COL_POINT_CODE),
            rs.getString(FragmentsSqlObservation.COL_NOM_SITE),
            rs.getString("tadarida"),
            (Double) rs.getObject(FragmentsSqlObservation.COL_PROB_TADARIDA),
            rs.getString(FragmentsSqlObservation.COL_OBSERVER),
            (Double) rs.getObject(FragmentsSqlObservation.COL_PROB_OBSERVER),
            StatutObservation.valueOf(rs.getString(FragmentsSqlObservation.COL_STATUT)));

    public ProjectionsAnalyseDao(SourceDeDonnees source) {
        super(source);
    }

    /// Projection des **espèces observées par l'utilisateur, rattachées à leur passage** (#323). Une ligne
    /// par couple (espèce, passage) — `DISTINCT` car un passage peut contenir plusieurs observations de la
    /// même espèce. L'espèce est le taxon **validé** s'il existe, sinon la proposition Tadarida. Les
    /// **pseudo-taxons** `noise` (bruit) et `piaf` (oiseau) sont exclus : ce ne sont pas des espèces de
    /// chiroptères. Triée du passage le plus récent au plus ancien (le plafond garde les plus récents).
    public List<EspeceObservee> especesObserveesParUtilisateur(String idUtilisateur) {
        return projeter(SQL_ESPECES, MAPPER_ESPECE, idUtilisateur);
    }

    /// **Observations enrichies** de l'utilisateur (#analyse, #537 étape 4) : une ligne [ObservationAnalyse]
    /// par observation (`COALESCE(observateur, tadarida)`, pseudo-taxons exclus), avec l'espèce (latin,
    /// vernaculaire, groupe), le statut dérivé (CASE, fidèle à `ServiceValidation#statut`) et le contexte
    /// (passage, année, carré, site, point). **Sans filtre de statut** : le filtrage (statut, texte…) et
    /// l'agrégation (par espèce / par carré) se font **côté client** via `AgregationAnalyse`.
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
}
