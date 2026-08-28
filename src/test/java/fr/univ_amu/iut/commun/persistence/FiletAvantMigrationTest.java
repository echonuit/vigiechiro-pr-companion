package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Workspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/// Le **filet posé avant une montée de version** (#2729, lot 1 du chantier de dette #2720).
///
/// Une migration est le seul moment où l'application transforme la base sans que l'utilisateur l'ait
/// demandé : il ouvre l'application après une mise à jour, et le schéma change. L'atomicité (#2728)
/// le protège d'une panne, pas d'une migration qui **réussit** en faisant autre chose que prévu.
///
/// Les tests simulent une base restée à une version antérieure : on retire du registre les versions
/// à rejouer et on défait ce qu'elles avaient fait. V37 est une pure mise à jour de données et V38
/// crée une table : toutes deux se réappliquent proprement.
class FiletAvantMigrationTest {

    private static final String NOM_FILET_V37 = "vigiechiro-avant-migration-V37.db";

    @TempDir
    Path racine;

    private Path workspace;
    private SourceDeDonnees source;

    @BeforeEach
    void preparer() {
        workspace = racine.resolve("ws");
        source = new SourceDeDonnees(new Workspace(workspace));
        new MigrationSchema(source).migrer();
    }

    @Test
    @DisplayName("une montée de version met la base à l'abri, sous le nom de la première migration en attente")
    void montee_de_version_pose_un_filet() throws SQLException {
        redescendreEnVersion37();

        new MigrationSchema(source).migrer();

        assertThat(sauvegardes())
                .as("le filet se range là où la restauration propose de chercher, et son nom dit de"
                        + " quelle montée de version il précède")
                .containsExactly(NOM_FILET_V37);
    }

    @Test
    @DisplayName("le filet porte l'état d'AVANT la migration, et il est restaurable")
    void le_filet_porte_l_etat_d_avant() throws SQLException {
        executer("INSERT INTO user(local_id, display_name) VALUES ('u-1', 'Alice')");
        redescendreEnVersion37();

        new MigrationSchema(source).migrer();

        Path filet = workspace.resolve("sauvegardes").resolve(NOM_FILET_V37);
        assertThat(filet).as("le filet a bien été écrit").exists();
        assertThat(utilisateursDe(filet))
                .as("une vraie base SQLite, avec les données de l'utilisateur")
                .containsExactly("Alice");
        assertThat(tablesDe(filet))
                .as("et l'état d'AVANT : la table que V38 crée n'y est pas, alors qu'elle est dans la" + " base migrée")
                .doesNotContain("point_commune");
        assertThat(tables(source)).contains("point_commune");
    }

    @Test
    @DisplayName("créer une base ne pose aucun filet : il n'y a rien à mettre à l'abri")
    void base_neuve_ne_pose_pas_de_filet() {
        assertThat(workspace.resolve("sauvegardes"))
                .as("les 38 migrations d'une base neuve ne sont pas une montée de version")
                .doesNotExist();
    }

    @Test
    @DisplayName("sans migration en attente, aucun filet n'est posé")
    void rien_en_attente_aucun_filet() {
        new MigrationSchema(source).migrer();

        assertThat(workspace.resolve("sauvegardes"))
                .as("un lancement ordinaire ne doit pas accumuler des copies de la base")
                .doesNotExist();
    }

    @Test
    @DisplayName("deux montées successives ne s'écrasent pas l'une l'autre")
    void deux_filets_ne_s_ecrasent_pas() throws SQLException {
        redescendreEnVersion37();
        new MigrationSchema(source).migrer();
        redescendreEnVersion37();

        new MigrationSchema(source).migrer();

        assertThat(sauvegardes())
                .as("c'est précisément le filet d'avant qu'on voudrait garder")
                .containsExactlyInAnyOrder(NOM_FILET_V37, "vigiechiro-avant-migration-V37-1.db");
    }

    @Test
    @DisplayName("si le filet ne peut pas être posé, la migration n'a pas lieu")
    void filet_impossible_migration_annulee() throws SQLException, IOException {
        redescendreEnVersion37();
        // Un FICHIER là où le dossier des sauvegardes doit être : l'écriture ne peut pas aboutir.
        Files.writeString(workspace.resolve("sauvegardes"), "pas un dossier");

        assertThatThrownBy(() -> new MigrationSchema(source).migrer())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("n'a pas pu être mise à l'abri")
                .as("le message dit quoi faire, faute de quoi il ne reste qu'un démarrage impossible")
                .hasMessageContaining("Libérez de la place");

        assertThat(tables(source))
                .as("avancer sans filet reviendrait à ne le promettre que quand il ne sert à rien")
                .doesNotContain("point_commune");
    }

    /// Ramène la base à l'état « V37 et suivantes pas encore appliquées » : on retire leurs versions du
    /// registre et on **défait ce qu'elles avaient fait**.
    ///
    /// Cette méthode gagne une ligne à **chaque migration ajoutée** au-delà de V37, sans quoi la
    /// remontée rejoue une migration sur un schéma qui la porte déjà - et échoue sur un
    /// « duplicate column » très loin de sa cause. C'est le prix du point de rebroussement figé à V37 ;
    /// il se paie au moment d'écrire la migration, jamais après.
    private void redescendreEnVersion37() throws SQLException {
        executer("DELETE FROM schema_version WHERE version >= 37");
        executer("DROP TABLE IF EXISTS point_commune"); // V38
        executer("ALTER TABLE depot_unite DROP COLUMN echec_definitif"); // V39
        executer("DROP TABLE IF EXISTS point_publie"); // V40
        executer("ALTER TABLE depot_unite DROP COLUMN cause_refus"); // V41
        executer("ALTER TABLE selection_sequence DROP COLUMN verdict_relecteur"); // V42
        executer("ALTER TABLE selection_sequence DROP COLUMN relecteur_pseudo"); // V42
    }

    /// Noms des fichiers du dossier de sauvegardes, **liste vide** s'il n'existe pas : un dossier
    /// absent est un état légitime, qui doit se lire « aucune sauvegarde » et non faire exploser le
    /// test avant qu'il ait pu dire ce qu'il attendait.
    private List<String> sauvegardes() {
        Path dossier = workspace.resolve("sauvegardes");
        if (!Files.isDirectory(dossier)) {
            return List.of();
        }
        try (var fichiers = Files.list(dossier)) {
            return fichiers.map(fichier -> fichier.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException illisible) {
            throw new IllegalStateException("Dossier de sauvegardes illisible : " + dossier, illisible);
        }
    }

    private void executer(String sql) throws SQLException {
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement()) {
            st.execute(sql);
        }
    }

    private static List<String> utilisateursDe(Path base) throws SQLException {
        return colonneTexte(base, "SELECT display_name FROM user");
    }

    private static List<String> tablesDe(Path base) throws SQLException {
        return colonneTexte(base, "SELECT lower(name) FROM sqlite_master WHERE type = 'table'");
    }

    /// Lit une colonne texte dans un fichier de base **arbitraire**, sans passer par la source du
    /// workspace : c'est bien le fichier produit par le filet qu'on veut ouvrir.
    private static List<String> colonneTexte(Path base, String sql) throws SQLException {
        SQLiteDataSource lecture = new SQLiteDataSource();
        lecture.setUrl("jdbc:sqlite:" + base);
        List<String> valeurs = new ArrayList<>();
        try (Connection cx = lecture.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                valeurs.add(rs.getString(1));
            }
        }
        return valeurs;
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
}
