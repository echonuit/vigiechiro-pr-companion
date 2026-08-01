package fr.univ_amu.iut.commun.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/// Ce que la base a **retenu** des migrations : la table `schema_version`, son contenu et sa forme.
///
/// C'est le **registre du migrateur**, pas une table du domaine, et cette distinction a une
/// conséquence pratique : sa colonne d'empreinte est posée **ici**, par introspection, et non par un
/// script `V0n__*.sql`. Sur une base neuve, V01 à V38 s'appliquent avant toute migration future ;
/// une colonne posée par un V39 n'existerait pas encore au moment où V01 inscrit sa version (#2729).
///
/// La lecture ne modifie rien : c'est aux écritures, qui se font dans la transaction de leur
/// migration, d'assurer la forme du registre.
class RegistreMigrations {

    private final SourceDeDonnees source;

    RegistreMigrations(SourceDeDonnees source) {
        this.source = source;
    }

    /// Versions déjà appliquées et leur empreinte, dans l'ordre où la base les a inscrites.
    ///
    /// Une valeur `null` dit « appliquée, empreinte inconnue » : c'est le cas des migrations passées
    /// avant la mise en place des empreintes, et celui d'une base dont le registre n'a pas encore la
    /// colonne. Registre absent (premier lancement) : dictionnaire vide.
    Map<Integer, String> lire() {
        try (Connection cx = source.getConnection()) {
            if (!tablePresente(cx)) {
                return Map.of();
            }
            return lireVersions(cx, colonneEmpreintePresente(cx));
        } catch (SQLException e) {
            throw new DataAccessException("Lecture impossible du registre des migrations", e);
        }
    }

    /// Inscrit une version et l'empreinte du script qui vient d'être appliqué, **sur la connexion de
    /// la transaction de cette migration** : les deux écritures passent ou échouent ensemble (#2728).
    void inscrire(Connection cx, int version, String empreinte) throws SQLException {
        assurerColonneEmpreinte(cx);
        String sql = "INSERT OR IGNORE INTO schema_version(version, applied_at, checksum) VALUES (?, ?, ?)";
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setInt(1, version);
            ps.setString(2, LocalDateTime.now().toString());
            ps.setString(3, empreinte);
            ps.executeUpdate();
        }
    }

    /// Donne une empreinte aux migrations qui n'en ont pas, sans jamais en écraser une existante
    /// (`WHERE checksum IS NULL`).
    ///
    /// C'est un **étalonnage**, pas une vérification : il fige ce que les scripts disent aujourd'hui
    /// pour que toute dérive ultérieure se voie. Il ne peut rien affirmer sur le passé, puisque rien
    /// n'a gardé trace de ce qui avait été appliqué.
    void etalonner(Connection cx, Map<Integer, String> empreintesParVersion) throws SQLException {
        assurerColonneEmpreinte(cx);
        String sql = "UPDATE schema_version SET checksum = ? WHERE version = ? AND checksum IS NULL";
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            for (Map.Entry<Integer, String> entree : empreintesParVersion.entrySet()) {
                ps.setString(1, entree.getValue());
                ps.setInt(2, entree.getKey());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void assurerColonneEmpreinte(Connection cx) throws SQLException {
        if (colonneEmpreintePresente(cx)) {
            return;
        }
        try (Statement st = cx.createStatement()) {
            st.execute("ALTER TABLE schema_version ADD COLUMN checksum TEXT");
        }
    }

    private static Map<Integer, String> lireVersions(Connection cx, boolean avecEmpreinte) throws SQLException {
        String sql = avecEmpreinte
                ? "SELECT version, checksum FROM schema_version"
                : "SELECT version, NULL FROM schema_version";
        Map<Integer, String> retenues = new LinkedHashMap<>();
        try (Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                retenues.put(rs.getInt(1), rs.getString(2));
            }
        }
        return retenues;
    }

    private static boolean tablePresente(Connection cx) throws SQLException {
        String sql = "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'schema_version'";
        try (Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        }
    }

    private static boolean colonneEmpreintePresente(Connection cx) throws SQLException {
        try (Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA table_info(schema_version)")) {
            while (rs.next()) {
                if ("checksum".equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
