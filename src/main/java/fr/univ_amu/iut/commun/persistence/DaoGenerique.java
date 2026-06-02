package fr.univ_amu.iut.commun.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Base mutualisée des DAO concrets : centralise l'ouverture de connexion, la liaison des
/// paramètres
/// [PreparedStatement] et l'itération du [ResultSet].
///
/// Un DAO concret fournit son nom de table, sa colonne clé et son [RowMapper] ; il hérite
/// alors gratuitement de [#findAll()], [#findById(Object)] et [#delete(Object)].
/// Seules les écritures dépendantes des colonnes (`insert`/`update`) restent à écrire,
/// en s'appuyant sur les helpers [#executerMaj(String, Object...)] et
/// [#insererEtRecupererCle(String, Object...)].
///
/// **Aucun SQL métier ici** : cette classe est purement technique (couche `commun.persistence`). Le
/// SQL des entités vit dans les DAO de chaque feature.
///
/// @param <T> type d'entité
/// @param <ID> type de clé primaire
public abstract class DaoGenerique<T, ID> implements Dao<T, ID> {

  protected final SourceDeDonnees source;

  protected DaoGenerique(SourceDeDonnees source) {
    this.source = source;
  }

  /// Nom de la table SQL sous-jacente (ex. `"monitoring_site"`).
  protected abstract String table();

  /// Nom de la colonne clé primaire (ex. `"id"` ou `"local_id"`).
  protected abstract String colonneCle();

  /// Convertit une ligne de résultat en entité.
  protected abstract RowMapper<T> mapper();

  @Override
  public List<T> findAll() {
    return query("SELECT * FROM " + table() + " ORDER BY " + colonneCle(), mapper());
  }

  @Override
  public Optional<T> findById(ID id) {
    return queryUnique(
        "SELECT * FROM " + table() + " WHERE " + colonneCle() + " = ?", mapper(), id);
  }

  @Override
  public void delete(ID id) {
    executerMaj("DELETE FROM " + table() + " WHERE " + colonneCle() + " = ?", id);
  }

  /// Exécute un `SELECT` et renvoie une entité mappée par ligne.
  protected List<T> query(String sql, RowMapper<T> mapper, Object... parametres) {
    List<T> resultats = new ArrayList<>();
    try (Connection connexion = source.getConnection();
        PreparedStatement ps = connexion.prepareStatement(sql)) {
      lier(ps, parametres);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          resultats.add(mapper.mapper(rs));
        }
      }
    } catch (SQLException e) {
      throw new DataAccessException("Échec de la requête : " + sql, e);
    }
    return resultats;
  }

  /// Exécute un `SELECT` attendu sur 0 ou 1 ligne.
  protected Optional<T> queryUnique(String sql, RowMapper<T> mapper, Object... parametres) {
    try (Connection connexion = source.getConnection();
        PreparedStatement ps = connexion.prepareStatement(sql)) {
      lier(ps, parametres);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(mapper.mapper(rs)) : Optional.empty();
      }
    } catch (SQLException e) {
      throw new DataAccessException("Échec de la requête : " + sql, e);
    }
  }

  /// Exécute un `INSERT`/`UPDATE`/`DELETE` et renvoie le nombre de lignes.
  protected int executerMaj(String sql, Object... parametres) {
    try (Connection connexion = source.getConnection();
        PreparedStatement ps = connexion.prepareStatement(sql)) {
      lier(ps, parametres);
      return ps.executeUpdate();
    } catch (SQLException e) {
      throw new DataAccessException("Échec de la mise à jour : " + sql, e);
    }
  }

  /// Exécute un `INSERT` et renvoie la clé auto-générée (`INTEGER PRIMARY KEY AUTOINCREMENT`). Pour
  /// une table à clé naturelle, utiliser [#executerMaj(String, Object...)] à la place.
  protected long insererEtRecupererCle(String sql, Object... parametres) {
    try (Connection connexion = source.getConnection();
        PreparedStatement ps = connexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      lier(ps, parametres);
      ps.executeUpdate();
      try (ResultSet cles = ps.getGeneratedKeys()) {
        if (cles.next()) {
          return cles.getLong(1);
        }
        throw new DataAccessException("Aucune clé générée pour : " + sql, null);
      }
    } catch (SQLException e) {
      throw new DataAccessException("Échec de l'insertion : " + sql, e);
    }
  }

  /// Variante transactionnelle : insère sur la **connexion fournie** (sans l'ouvrir ni la
  /// commiter) pour grouper l'écriture avec d'autres dans une même [UniteDeTravail]. Propage la
  /// [SQLException] afin que l'unité de travail décide du commit / rollback.
  protected long insererEtRecupererCle(Connection connexion, String sql, Object... parametres)
      throws SQLException {
    try (PreparedStatement ps = connexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      lier(ps, parametres);
      ps.executeUpdate();
      try (ResultSet cles = ps.getGeneratedKeys()) {
        if (cles.next()) {
          return cles.getLong(1);
        }
        throw new DataAccessException("Aucune clé générée pour : " + sql, null);
      }
    }
  }

  /// Lie les paramètres positionnels (1-based), en gérant explicitement les valeurs nulles.
  private static void lier(PreparedStatement ps, Object... parametres) throws SQLException {
    for (int i = 0; i < parametres.length; i++) {
      Object valeur = parametres[i];
      if (valeur == null) {
        ps.setString(i + 1, null);
      } else {
        ps.setObject(i + 1, valeur);
      }
    }
  }
}
