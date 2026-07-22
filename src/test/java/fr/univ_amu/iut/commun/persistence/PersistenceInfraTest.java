package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests de l'infrastructure de persistance : migration/idempotence et atomicité transactionnelle.
class PersistenceInfraTest {

    @TempDir
    Path dossier;

    @Test
    @DisplayName("#28 : V03 crée les index de perf et la sélection des observations passe par l'index (plus de SCAN)")
    void index_de_performance_crees() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();

        assertThat(nomsIndex(source))
                .contains(
                        "idx_obs_results",
                        "idx_passage_recorder",
                        "idx_passage_status",
                        "idx_original_session",
                        "idx_seq_original",
                        "idx_point_site");

        assertThat(planRequete(source, "SELECT * FROM observation WHERE results_id = 1 ORDER BY id"))
                .as("la sélection des observations (O5) doit utiliser idx_obs_results, plus de SCAN")
                .contains("USING INDEX idx_obs_results");
    }

    private static Set<String> nomsIndex(SourceDeDonnees source) {
        Set<String> noms = new HashSet<>();
        try (Connection c = source.getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'index'")) {
            while (rs.next()) {
                noms.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new DataAccessException("liste des index", e);
        }
        return noms;
    }

    private static String planRequete(SourceDeDonnees source, String sql) {
        StringBuilder plan = new StringBuilder();
        try (Connection c = source.getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("EXPLAIN QUERY PLAN " + sql)) {
            while (rs.next()) {
                plan.append(rs.getString("detail")).append('\n');
            }
        } catch (SQLException e) {
            throw new DataAccessException("plan d'exécution", e);
        }
        return plan.toString();
    }

    private int compterTaxons(SourceDeDonnees source) {
        try (Connection c = source.getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM taxon")) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (SQLException e) {
            throw new DataAccessException("comptage taxons", e);
        }
    }

    @Test
    @DisplayName("La base est créée si absente puis réutilisée (migration idempotente)")
    void base_creee_puis_reutilisee() {
        Workspace workspace = new Workspace(dossier);
        SourceDeDonnees premiere = new SourceDeDonnees(workspace);
        new MigrationSchema(premiere).migrer();

        assertThat(Files.exists(workspace.cheminBaseDeDonnees())).isTrue();
        int apresPremiere = compterTaxons(premiere);
        assertThat(apresPremiere)
                .as("référentiel officiel Tadarida semé (V02 fil rouge + V05 France)")
                .isGreaterThan(300);

        // Réouverture (simule un redémarrage) : re-migrer ne doit pas re-seeder (INSERT OR IGNORE).
        SourceDeDonnees seconde = new SourceDeDonnees(workspace);
        new MigrationSchema(seconde).migrer();
        assertThat(compterTaxons(seconde))
                .as("pas de doublons après re-migration")
                .isEqualTo(apresPremiere);
    }

    @Test
    @DisplayName("Une transaction qui échoue est intégralement annulée (rollback)")
    void transaction_rollback_atomique() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        UniteDeTravail uniteDeTravail = new UniteDeTravail(source);

        assertThatThrownBy(() -> uniteDeTravail.executer(connexion -> {
                    try (PreparedStatement ps = connexion.prepareStatement("INSERT INTO monitoring_site"
                            + " (square_number, protocol, created_at, user_id)"
                            + " VALUES ('999999', 'PointFixeStandard', '2026-01-01', 'u-1')")) {
                        ps.executeUpdate();
                    }
                    throw new IllegalStateException("panne simulée après insertion");
                }))
                .isInstanceOf(DataAccessException.class);

        assertThat(new SiteDao(source).findByUtilisateur("u-1"))
                .as("le rollback doit avoir annulé l'insertion du site")
                .isEmpty();
    }

    @Test
    @DisplayName("Une transaction réussie est bien validée (commit)")
    void transaction_commit() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        SiteDao siteDao = new SiteDao(source);

        new UniteDeTravail(source).executer(connexion -> {
            try (PreparedStatement ps = connexion.prepareStatement("INSERT INTO monitoring_site"
                    + " (square_number, protocol, created_at, user_id)"
                    + " VALUES ('111111', 'PointFixeStandard', '2026-01-01', 'u-1')")) {
                ps.executeUpdate();
            }
        });

        assertThat(siteDao.findByUtilisateur("u-1"))
                .extracting(Site::numeroCarre)
                .containsExactly("111111");
    }

    @Test
    @DisplayName("#1038 : base hors du dossier de travail - c'est SON dossier qui est créé, pas la racine")
    void base_hors_workspace_cree_son_propre_dossier() {
        // La création paresseuse visait la racine du workspace. Depuis que la base peut vivre ailleurs,
        // créer la racine n'ouvre plus rien : c'est le dossier de la BASE qu'il faut, et lui seul suffit.
        Path coffre = dossier.resolve("coffre").resolve("archives");
        Workspace workspace = new Workspace(dossier.resolve("travail"), coffre.resolve("vigiechiro.db"));

        SourceDeDonnees source = new SourceDeDonnees(workspace);
        new MigrationSchema(source).migrer();

        assertThat(coffre.resolve("vigiechiro.db"))
                .as("la base s'ouvre dans un dossier qui n'existait pas : il a fallu le créer")
                .isRegularFile();
        assertThat(dossier.resolve("travail").resolve("vigiechiro.db"))
                .as("et rien n'est écrit à l'ancien emplacement")
                .doesNotExist();
    }
}
