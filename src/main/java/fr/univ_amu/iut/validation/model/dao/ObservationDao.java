package fr.univ_amu.iut.validation.model.dao;

import fr.univ_amu.iut.commun.model.CertitudeObservateur;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.Observation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/// DAO de l'entité [Observation] (table `observation`, clé auto-incrémentée) : **CRUD et écritures en
/// lot** du cycle de validation. Les projections transverses vivent dans [ProjectionsAnalyseDao]
/// (analyse/espèces) et [ProjectionsAudioDao] (vue audio unifiée) (#1193) ; les fragments SQL et
/// lectures partagés dans [FragmentsSqlObservation].
///
/// Démontre plusieurs points :
///
/// - le mapping de l'énum [ModeValidation] (colonne `validation_mode`, `null` →
///   [ModeValidation#NON_VALIDE]) et du booléen `is_reference` (`INTEGER` 0/1) ;
/// - un **insert en lot** ([#insererTout(List)]) regroupé en une seule transaction
///   (`addBatch`/`executeBatch`) : l'import d'un CSV Tadarida crée des centaines d'observations
///   d'un coup, un aller-retour par ligne serait coûteux ;
/// - la requête métier [#findByResults(Long)] (toutes les observations d'un jeu de résultats).
public class ObservationDao extends DaoGenerique<Observation, Long> {

    private static final String SQL_INSERT = "INSERT INTO observation"
            + " (sequence_id, start_time_s, end_time_s, median_freq_khz, taxon_tadarida,"
            + " prob_tadarida, taxon_other_tadarida, taxon_observer, prob_observer, user_comment,"
            + " is_reference, validation_mode, results_id, is_doubtful,"
            + " vigiechiro_data_id, vigiechiro_obs_index, observer_certainty,"
            + " taxon_validator, validator_certainty)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE = "UPDATE observation SET sequence_id = ?, start_time_s = ?,"
            + " end_time_s = ?, median_freq_khz = ?, taxon_tadarida = ?, prob_tadarida = ?,"
            + " taxon_other_tadarida = ?, taxon_observer = ?, prob_observer = ?, user_comment = ?,"
            + " is_reference = ?, validation_mode = ?, results_id = ?, is_doubtful = ?,"
            + " vigiechiro_data_id = ?, vigiechiro_obs_index = ?, observer_certainty = ?,"
            + " taxon_validator = ?, validator_certainty = ? WHERE id = ?";

    /// Mapper de l'entité (lecture `SELECT *` de la table), sur les lectures nullables partagées
    /// de [FragmentsSqlObservation].
    private static final RowMapper<Observation> MAPPER = rs -> new Observation(
            rs.getLong("id"),
            rs.getLong("sequence_id"),
            (Double) rs.getObject("start_time_s"),
            (Double) rs.getObject("end_time_s"),
            FragmentsSqlObservation.entierNullable(rs, "median_freq_khz"),
            rs.getString("taxon_tadarida"),
            (Double) rs.getObject(FragmentsSqlObservation.COL_PROB_TADARIDA),
            rs.getString("taxon_other_tadarida"),
            rs.getString("taxon_observer"),
            (Double) rs.getObject(FragmentsSqlObservation.COL_PROB_OBSERVER),
            rs.getString("user_comment"),
            rs.getInt(FragmentsSqlObservation.COL_IS_REFERENCE) != 0,
            ModeValidation.parLibelle(rs.getString("validation_mode")),
            FragmentsSqlObservation.longNullable(rs, "results_id"), // null : observation manuelle
            rs.getInt(FragmentsSqlObservation.COL_IS_DOUBTFUL) != 0,
            rs.getString("vigiechiro_data_id"),
            FragmentsSqlObservation.entierNullable(rs, "vigiechiro_obs_index"),
            CertitudeObservateur.depuisTexte(rs.getString("observer_certainty")),
            rs.getString("taxon_validator"),
            CertitudeObservateur.depuisTexte(rs.getString("validator_certainty")));

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

    /// Observations **revues** d'un passage (taxon observateur posé), avec leur ancrage plateforme et
    /// leur certitude : le vivier de la **publication des corrections** (#723) ; le service classe
    /// ensuite poussables / à compléter / sans ancrage. La chaîne `observation → séquence → session`
    /// suffit (pas besoin du site). Tri par id (stable, ordre d'import).
    public List<Observation> revuesDuPassage(Long idPassage) {
        return query(
                "SELECT o.* FROM observation o"
                        + " JOIN listening_sequence ls ON o.sequence_id = ls.id"
                        + " JOIN recording_session rs ON ls.session_id = rs.id"
                        + " WHERE rs.passage_id = ? AND o.taxon_observer IS NOT NULL"
                        + " ORDER BY o.id",
                MAPPER,
                idPassage);
    }

    /// L'**observation manuelle** d'une séquence (celle sans jeu de résultats Tadarida, `results_id IS
    /// NULL`), ou vide s'il n'y en a pas encore. Sert à la validation manuelle : mettre à jour l'existante
    /// plutôt qu'en créer une seconde. (Au plus une par séquence, garantie par le service.)
    public Optional<Observation> observationManuelleDeLaSequence(long idSequence) {
        return query(
                        "SELECT * FROM observation WHERE sequence_id = ? AND results_id IS NULL ORDER BY id",
                        MAPPER,
                        idSequence)
                .stream()
                .findFirst();
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
                observation.idResultats(),
                observation.douteux(),
                observation.idDonneeVigieChiro(),
                observation.indiceVigieChiro(),
                observation.certitudeObservateur(),
                observation.taxonValidateur(),
                observation.certitudeValidateur());
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
            observation.idResultats(),
            observation.douteux() ? 1 : 0,
            observation.idDonneeVigieChiro(),
            observation.indiceVigieChiro(),
            jeton(observation.certitudeObservateur()),
            observation.taxonValidateur(),
            jeton(observation.certitudeValidateur())
        };
    }

    /// Jeton persisté d'une certitude (`SUR` / `PROBABLE` / `POSSIBLE`), ou `null` si non renseignée :
    /// l'observateur et le validateur partagent le même domaine fermé (contrat #1203).
    private static String jeton(CertitudeObservateur certitude) {
        return certitude == null ? null : certitude.jeton();
    }
}
