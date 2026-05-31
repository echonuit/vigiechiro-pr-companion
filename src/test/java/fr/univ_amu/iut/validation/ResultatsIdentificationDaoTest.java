package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// CRUD du [ResultatsIdentificationDao] + contraintes : FK vers le passage, unicité `passage_id`
/// (cardinalité 0:1), et suppression en cascade quand le passage parent disparaît
/// (`ON DELETE CASCADE`).
///
/// La chaîne de FK menant au passage (utilisateur → site → point → enregistreur → passage) n'a
/// pas encore de DAO dédié (autres features) : on la sème ici directement en SQL pour rester
/// autonome.
class ResultatsIdentificationDaoTest {

  @TempDir Path dossier;
  private SourceDeDonnees source;
  private ResultatsIdentificationDao dao;
  private long idPassage;

  @BeforeEach
  void preparer() throws SQLException {
    source = new SourceDeDonnees(new Workspace(dossier));
    new MigrationSchema(source).migrer();
    try (Connection cx = source.getConnection()) {
      executer(cx, "INSERT INTO user(local_id, display_name) VALUES ('u-1', 'Testeur')");
      long idSite =
          insererCle(
              cx,
              "INSERT INTO monitoring_site(square_number, protocol, created_at, user_id)"
                  + " VALUES ('640380', 'Point fixe standard', '2026-05-01', 'u-1')");
      long idPoint =
          insererCle(cx, "INSERT INTO listening_point(code, site_id) VALUES ('A1', ?)", idSite);
      executer(cx, "INSERT INTO recorder(serial_number) VALUES ('SN-1')");
      idPassage =
          insererCle(
              cx,
              "INSERT INTO passage(passage_number, year, recording_date, start_time, end_time,"
                  + " workflow_status, point_id, recorder_id)"
                  + " VALUES (1, 2026, '2026-06-20', '21:00', '05:00', 'Importé', ?, 'SN-1')",
              idPoint);
    }
    dao = new ResultatsIdentificationDao(source);
  }

  private ResultatsIdentification nouveauxResultats() {
    return new ResultatsIdentification(
        null, "transformes/obs.csv", "Vu", "2026-06-21T10:00", idPassage);
  }

  @Test
  @DisplayName("Insérer attribue un id et rend les résultats relisibles")
  void inserer_attribue_un_id_et_rend_les_resultats_relisibles() {
    ResultatsIdentification insere = dao.insert(nouveauxResultats());

    assertThat(insere.id()).as("la clé auto-incrémentée est renseignée").isNotNull();
    ResultatsIdentification relu = dao.findById(insere.id()).orElseThrow();
    assertThat(relu.cheminFichier()).isEqualTo("transformes/obs.csv");
    assertThat(relu.formatDetecte()).isEqualTo("Vu");
    assertThat(relu.idPassage()).isEqualTo(idPassage);
  }

  @Test
  @DisplayName("findByPassage renvoie l'unique jeu de résultats du passage")
  void find_by_passage_renvoie_les_resultats_du_passage() {
    dao.insert(nouveauxResultats());

    assertThat(dao.findByPassage(idPassage)).isPresent();
    assertThat(dao.findByPassage(9999L)).isEmpty();
  }

  @Test
  @DisplayName("Mettre à jour modifie le chemin et le format")
  void mettre_a_jour_modifie_les_champs() {
    ResultatsIdentification insere = dao.insert(nouveauxResultats());

    dao.update(
        new ResultatsIdentification(
            insere.id(), "transformes/obs_Vu.csv", "Brut", "2026-06-22T08:00", idPassage));

    ResultatsIdentification relu = dao.findById(insere.id()).orElseThrow();
    assertThat(relu.cheminFichier()).isEqualTo("transformes/obs_Vu.csv");
    assertThat(relu.formatDetecte()).isEqualTo("Brut");
  }

  @Test
  @DisplayName("Un second jeu de résultats sur le même passage est rejeté (0:1)")
  void unicite_du_passage_est_garantie() {
    dao.insert(nouveauxResultats());

    assertThatThrownBy(() -> dao.insert(nouveauxResultats()))
        .as("passage_id UNIQUE interdit deux jeux de résultats pour un passage")
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  @DisplayName("Un passage inconnu est rejeté (FK active)")
  void clef_etrangere_active_un_passage_inconnu_est_rejete() {
    ResultatsIdentification orphelin =
        new ResultatsIdentification(null, "transformes/obs.csv", "Vu", "2026-06-21T10:00", 9999L);

    assertThatThrownBy(() -> dao.insert(orphelin))
        .as("PRAGMA foreign_keys=ON doit refuser une FK vers un passage absent")
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  @DisplayName("Supprimer le passage supprime ses résultats en cascade")
  void supprimer_le_passage_supprime_les_resultats_en_cascade() throws SQLException {
    dao.insert(nouveauxResultats());
    assertThat(dao.findByPassage(idPassage)).isPresent();

    try (Connection cx = source.getConnection();
        PreparedStatement ps = cx.prepareStatement("DELETE FROM passage WHERE id = ?")) {
      ps.setLong(1, idPassage);
      ps.executeUpdate();
    }

    assertThat(dao.findByPassage(idPassage))
        .as("ON DELETE CASCADE doit avoir supprimé les résultats du passage")
        .isEmpty();
  }

  private static long insererCle(Connection cx, String sql, Object... params) throws SQLException {
    try (PreparedStatement ps = cx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      for (int i = 0; i < params.length; i++) {
        ps.setObject(i + 1, params[i]);
      }
      ps.executeUpdate();
      try (ResultSet cles = ps.getGeneratedKeys()) {
        cles.next();
        return cles.getLong(1);
      }
    }
  }

  private static void executer(Connection cx, String sql) throws SQLException {
    try (Statement st = cx.createStatement()) {
      st.execute(sql);
    }
  }
}
