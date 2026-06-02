package fr.univ_amu.iut.passage.model.dao;

import fr.univ_amu.iut.passage.model.ReprefixeurSession;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/// Écritures **connection-aware** de la modification rétroactive du rattachement d'un passage
/// (E2.S8) : nouveau quadruplet (année + n° de passage) et re-préfixage des chemins persistés.
///
/// Comme [fr.univ_amu.iut.importation.model.dao.AgregatImportDao], et pour la même raison
/// (SERVICE-CONVENTIONS §2.5), les méthodes prennent une [Connection] et sont conçues pour être
/// appelées **dans** un bloc `UniteDeTravail.executer(cx -> …)` : la mise à jour des sept tables
/// (passage, session, originaux, séquences, journal, relevé, résultats Tadarida) est ainsi **tout
/// ou rien**.
///
/// Chaque chemin est recalculé par [ReprefixeurSession#cheminApres] (relocalisation sous la
/// nouvelle racine, à l'identique du disque), jamais par un `replace` textuel : un parent qui
/// contiendrait par coïncidence le nom de dossier, ou un CSV Tadarida importé d'un chemin externe,
/// ne sont pas corrompus. Le SQL reste dans `model.dao` et ne référence aucune classe d'une autre
/// feature (la table `identification_results` est touchée par requête, sans cycle).
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

  /// Réécrit les chemins persistés sous le dossier de la session : `root_path` (fixé à la nouvelle
  /// racine), `file_path`/`file_name` des originaux et séquences, `file_path` du journal, du relevé
  /// et du CSV Tadarida. Connection-aware. Un chemin hors de l'ancienne racine reste inchangé (cf.
  /// `cheminApres`).
  public void reprefixerChemins(
      Connection cx,
      long idPassage,
      long idSession,
      Path ancienneRacine,
      Path nouvelleRacine,
      String ancienPrefixe,
      String nouveauPrefixe)
      throws SQLException {
    try (PreparedStatement ps =
        cx.prepareStatement("UPDATE recording_session SET root_path = ? WHERE id = ?")) {
      ps.setString(1, nouvelleRacine.toString());
      ps.setLong(2, idSession);
      ps.executeUpdate();
    }
    reprefixerTable(
        cx,
        "SELECT id, file_path FROM original_recording WHERE session_id = ?",
        "UPDATE original_recording SET file_path = ?, file_name = ? WHERE id = ?",
        idSession,
        true,
        ancienneRacine,
        nouvelleRacine,
        ancienPrefixe,
        nouveauPrefixe);
    reprefixerTable(
        cx,
        "SELECT id, file_path FROM listening_sequence WHERE session_id = ?",
        "UPDATE listening_sequence SET file_path = ?, file_name = ? WHERE id = ?",
        idSession,
        true,
        ancienneRacine,
        nouvelleRacine,
        ancienPrefixe,
        nouveauPrefixe);
    reprefixerTable(
        cx,
        "SELECT id, file_path FROM sensor_log WHERE session_id = ?",
        "UPDATE sensor_log SET file_path = ? WHERE id = ?",
        idSession,
        false,
        ancienneRacine,
        nouvelleRacine,
        ancienPrefixe,
        nouveauPrefixe);
    reprefixerTable(
        cx,
        "SELECT id, file_path FROM climate_log WHERE session_id = ?",
        "UPDATE climate_log SET file_path = ? WHERE id = ?",
        idSession,
        false,
        ancienneRacine,
        nouvelleRacine,
        ancienPrefixe,
        nouveauPrefixe);
    reprefixerTable(
        cx,
        "SELECT id, file_path FROM identification_results WHERE passage_id = ?",
        "UPDATE identification_results SET file_path = ? WHERE id = ?",
        idPassage,
        false,
        ancienneRacine,
        nouvelleRacine,
        ancienPrefixe,
        nouveauPrefixe);
  }

  /// Recalcule, pour chaque ligne sélectionnée par `selectSql` (paramètre = `cle`), le `file_path`
  /// via [ReprefixeurSession#cheminApres] et l'écrit avec `updateSql`. `avecNom` ajoute le
  /// `file_name` (basename du nouveau chemin). Les chemins inchangés (hors session) sont ignorés.
  private static void reprefixerTable(
      Connection cx,
      String selectSql,
      String updateSql,
      long cle,
      boolean avecNom,
      Path ancienneRacine,
      Path nouvelleRacine,
      String ancienPrefixe,
      String nouveauPrefixe)
      throws SQLException {
    Map<Long, String> chemins = new LinkedHashMap<>();
    try (PreparedStatement ps = cx.prepareStatement(selectSql)) {
      ps.setLong(1, cle);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          chemins.put(rs.getLong(1), rs.getString(2));
        }
      }
    }
    try (PreparedStatement ps = cx.prepareStatement(updateSql)) {
      for (Map.Entry<Long, String> ligne : chemins.entrySet()) {
        String nouveau =
            ReprefixeurSession.cheminApres(
                ligne.getValue(), ancienneRacine, nouvelleRacine, ancienPrefixe, nouveauPrefixe);
        if (nouveau.equals(ligne.getValue())) {
          continue; // chemin externe à la session : non déplacé sur disque, donc inchangé
        }
        ps.setString(1, nouveau);
        if (avecNom) {
          ps.setString(2, Path.of(nouveau).getFileName().toString());
          ps.setLong(3, ligne.getKey());
        } else {
          ps.setLong(2, ligne.getKey());
        }
        ps.executeUpdate();
      }
    }
  }
}
