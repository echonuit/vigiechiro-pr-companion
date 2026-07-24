package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Workspace;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Garde-fous du **catalogue** de migrations (#1262).
///
/// `migrer()` retient les migrations déjà appliquées **par numéro de version** (table `schema_version`),
/// pas par nom de fichier. Deux migrations qui partageraient un numéro constitueraient donc un piège
/// silencieux : la première appliquée enregistrerait la version, et **la seconde serait ignorée à
/// jamais** — sa table ne serait jamais créée, sans le moindre message. Le cas n'est pas théorique : il
/// s'est présenté le jour même entre ce chantier et #1139, deux branches parallèles réclamant `V21`.
///
/// Ces tests transforment cette collision en **échec de build**, au lieu d'une base incomplète chez
/// l'utilisateur.
class MigrationSchemaTest {

    @TempDir
    Path racine;

    @Test
    @DisplayName("chaque migration porte un numéro de version UNIQUE (sinon l'une d'elles serait ignorée)")
    void numeros_de_version_uniques() {
        List<Integer> versions = new ArrayList<>();
        for (String fichier : MigrationSchema.MIGRATIONS) {
            versions.add(Integer.parseInt(fichier.substring(1, fichier.indexOf("__"))));
        }

        assertThat(versions)
                .as("deux migrations de même numéro : la seconde ne serait JAMAIS appliquée (schema_version"
                        + " ne retient que le numéro). Renuméroter la nouvelle venue.")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("chaque migration listée existe bien dans les ressources (nom exact, casse comprise)")
    void fichiers_presents() {
        for (String fichier : MigrationSchema.MIGRATIONS) {
            assertThat(MigrationSchema.class.getResourceAsStream("/db/migration/" + fichier))
                    .as("migration listée mais absente des ressources : %s", fichier)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("les migrations sont listées dans l'ordre croissant des versions (l'ordre d'exécution fait foi)")
    void ordre_croissant() {
        List<Integer> versions = new ArrayList<>();
        for (String fichier : MigrationSchema.MIGRATIONS) {
            versions.add(Integer.parseInt(fichier.substring(1, fichier.indexOf("__"))));
        }

        assertThat(versions).isSorted();
    }

    @Test
    @DisplayName("migrer() est idempotent : rejouer sur une base à jour n'applique rien et ne casse rien")
    void migration_idempotente() throws SQLException {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(racine.resolve("ws")));
        new MigrationSchema(source).migrer();

        new MigrationSchema(source).migrer(); // second passage : tout est déjà appliqué

        assertThat(versionsEnBase(source))
                .as("une ligne par migration du catalogue, sans doublon")
                .hasSize(MigrationSchema.MIGRATIONS.length);
        assertThat(tables(source))
                .as("la dernière migration en date a bien créé sa table")
                .contains("participation_traitement");
    }

    @Test
    @DisplayName("V31 retire les colonnes mortes archived_at / originals_purged_at de recording_session (#2429)")
    void colonnes_mortes_retirees() throws SQLException {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(racine.resolve("ws")));
        new MigrationSchema(source).migrer();

        assertThat(colonnes(source, "recording_session"))
                .as("« archivé » et « purgé » sont devenus des états observés (#2258) : les marqueurs V24/V25"
                        + " ne sont plus lus ni écrits, leurs colonnes doivent avoir disparu du schéma")
                .doesNotContain("archived_at", "originals_purged_at");
    }

    private static List<Integer> versionsEnBase(SourceDeDonnees source) throws SQLException {
        List<Integer> versions = new ArrayList<>();
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery("SELECT version FROM schema_version")) {
            while (rs.next()) {
                versions.add(rs.getInt(1));
            }
        }
        return versions;
    }

    private static List<String> tables(SourceDeDonnees source) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
            while (rs.next()) {
                tables.add(rs.getString(1).toLowerCase(Locale.ROOT));
            }
        }
        return tables;
    }

    private static List<String> colonnes(SourceDeDonnees source, String table) throws SQLException {
        List<String> colonnes = new ArrayList<>();
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                colonnes.add(rs.getString("name").toLowerCase(Locale.ROOT));
            }
        }
        return colonnes;
    }
}
