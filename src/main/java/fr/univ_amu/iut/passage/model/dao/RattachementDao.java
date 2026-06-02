package fr.univ_amu.iut.passage.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/// Écritures **connection-aware** de la modification rétroactive du rattachement d'un passage
/// (E2.S8) : nouveau quadruplet (année + n° de passage) et re-préfixage des chemins persistés.
///
/// Comme [fr.univ_amu.iut.importation.model.dao.AgregatImportDao], et pour la même raison
/// (SERVICE-CONVENTIONS §2.5), les méthodes prennent une [Connection] et sont conçues pour être
/// appelées **dans** un bloc `UniteDeTravail.executer(cx -> …)` : la mise à jour des sept tables
/// (passage, session, originaux, séquences, journal, relevé, résultats Tadarida) est ainsi **tout
/// ou rien**.
///
/// Le re-préfixage des chemins se fait par `replace(colonne, ancienDossier, nouveauDossier)` : le
/// nom de dossier de session (`Car<carré>-<année>-Pass<n>-<point>`) apparaît verbatim dans chaque
/// chemin stocké — comme segment de répertoire et, pour les fichiers préfixés, comme préfixe de nom
/// — si bien qu'un seul remplacement reproduit exactement le re-préfixage disque de
/// [fr.univ_amu.iut.passage.model.ReprefixeurSession].
public class RattachementDao {

  /// Met à jour le quadruplet du passage (année + n° de passage). Connection-aware.
  public void majQuadruplet(Connection cx, long idPassage, int annee, int numeroPassage)
      throws SQLException {
    try (PreparedStatement ps =
        cx.prepareStatement("UPDATE passage SET year = ?, passage_number = ? WHERE id = ?")) {
      ps.setInt(1, annee);
      ps.setInt(2, numeroPassage);
      ps.setLong(3, idPassage);
      ps.executeUpdate();
    }
  }

  /// Réécrit les chemins persistés qui pointent dans le dossier de la session, en remplaçant
  /// l'ancien nom de dossier par le nouveau : session, originaux, séquences, journal, relevé.
  ///
  /// Le CSV des résultats Tadarida (`identification_results`) n'est réécrit que s'il est
  /// **effectivement sous l'ancienne racine** (`ancienneRacine`) : `ServiceValidation` stocke le
  /// chemin fourni tel quel (R23 le situe sous `transformes/`, mais il peut être externe et
  /// n'aurait alors pas été déplacé sur disque). Connection-aware.
  public void reprefixerChemins(
      Connection cx,
      long idPassage,
      long idSession,
      String ancienneRacine,
      String ancienDossier,
      String nouveauDossier)
      throws SQLException {
    chemin(
        cx,
        "UPDATE recording_session SET root_path = replace(root_path, ?, ?) WHERE id = ?",
        idSession,
        ancienDossier,
        nouveauDossier);
    nomEtChemin(
        cx,
        "UPDATE original_recording SET file_path = replace(file_path, ?, ?),"
            + " file_name = replace(file_name, ?, ?) WHERE session_id = ?",
        idSession,
        ancienDossier,
        nouveauDossier);
    nomEtChemin(
        cx,
        "UPDATE listening_sequence SET file_path = replace(file_path, ?, ?),"
            + " file_name = replace(file_name, ?, ?) WHERE session_id = ?",
        idSession,
        ancienDossier,
        nouveauDossier);
    chemin(
        cx,
        "UPDATE sensor_log SET file_path = replace(file_path, ?, ?) WHERE session_id = ?",
        idSession,
        ancienDossier,
        nouveauDossier);
    chemin(
        cx,
        "UPDATE climate_log SET file_path = replace(file_path, ?, ?) WHERE session_id = ?",
        idSession,
        ancienDossier,
        nouveauDossier);
    try (PreparedStatement ps =
        cx.prepareStatement(
            "UPDATE identification_results SET file_path = replace(file_path, ?, ?)"
                + " WHERE passage_id = ? AND instr(file_path, ?) = 1")) {
      ps.setString(1, ancienDossier);
      ps.setString(2, nouveauDossier);
      ps.setLong(3, idPassage);
      ps.setString(4, ancienneRacine);
      ps.executeUpdate();
    }
  }

  private static void chemin(Connection cx, String sql, long cle, String ancien, String nouveau)
      throws SQLException {
    try (PreparedStatement ps = cx.prepareStatement(sql)) {
      ps.setString(1, ancien);
      ps.setString(2, nouveau);
      ps.setLong(3, cle);
      ps.executeUpdate();
    }
  }

  private static void nomEtChemin(
      Connection cx, String sql, long idSession, String ancien, String nouveau)
      throws SQLException {
    try (PreparedStatement ps = cx.prepareStatement(sql)) {
      ps.setString(1, ancien);
      ps.setString(2, nouveau);
      ps.setString(3, ancien);
      ps.setString(4, nouveau);
      ps.setLong(5, idSession);
      ps.executeUpdate();
    }
  }
}
