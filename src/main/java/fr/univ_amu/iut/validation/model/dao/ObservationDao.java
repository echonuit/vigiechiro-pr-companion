package fr.univ_amu.iut.validation.model.dao;

import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.Observation;
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

  private static final String SQL_INSERT =
      "INSERT INTO observation"
          + " (sequence_id, start_time_s, end_time_s, median_freq_hz, taxon_tadarida,"
          + " prob_tadarida, taxon_other_tadarida, taxon_observer, prob_observer, user_comment,"
          + " is_reference, validation_mode, results_id)"
          + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  private static final RowMapper<Observation> MAPPER =
      rs ->
          new Observation(
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
    return query(
        "SELECT * FROM observation WHERE sequence_id = ? ORDER BY start_time_s",
        MAPPER,
        idSequence);
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
  public void insererTout(Connection connexion, List<Observation> observations)
      throws SQLException {
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
