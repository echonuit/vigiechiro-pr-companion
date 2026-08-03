package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Workspace;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/// Ce que devient la base quand une **restauration échoue après l'échange** (#2730, lot 1 du
/// chantier de dette #2720).
///
/// La restauration remplaçait le fichier **puis** migrait. Une migration qui refuse laissait donc une
/// base ni restaurée ni migrée, sur laquelle l'application ne démarre plus. Le filet
/// (`vigiechiro.db.avant-restauration`) existait, mais le remettre était un geste manuel que rien
/// n'annonçait.
///
/// Le cas est devenu bien plus probable depuis #2729 : la migration a maintenant deux raisons de
/// refuser (empreinte de script divergente, filet impossible à poser) là où elle n'échouait qu'en cas
/// de panne. Ces tests fabriquent la première : une sauvegarde dont le registre annonce une empreinte
/// qui ne correspond plus à son script.
class RetourArriereRestaurationTest {

    @TempDir
    Path racine;

    private Path workspaceDir;
    private SourceDeDonnees source;
    private ServiceSauvegarde service;

    @BeforeEach
    void preparer() {
        workspaceDir = racine.resolve("ws");
        source = new SourceDeDonnees(new Workspace(workspaceDir));
        new MigrationSchema(source).migrer();
        service = new ServiceSauvegarde(source, new HorlogeFigee(LocalDateTime.of(2026, 8, 3, 9, 0)));
    }

    @Test
    @DisplayName("une migration qui échoue rend la base d'avant, données comprises")
    void migration_qui_echoue_rend_la_base_d_avant() throws SQLException {
        Path sauvegarde = service.sauvegarder(racine.resolve("sauvegardes"));
        abimerLesEmpreintes(sauvegarde);
        // L'état courant, celui qui ne doit pas disparaître : un utilisateur ajouté APRÈS la sauvegarde.
        executer("INSERT INTO user(local_id, display_name) VALUES ('u-apres', 'Alice')");

        assertThatThrownBy(() -> service.restaurer(sauvegarde))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("mise à jour de son schéma a échoué")
                .as("le message doit rassurer sur l'état de la base, sinon il annonce une catastrophe")
                .hasMessageContaining("rien n'est perdu");

        assertThat(utilisateurs())
                .as("la base rendue est celle d'AVANT la restauration, pas la sauvegarde à moitié posée")
                .containsExactly("Alice");
        assertThatCode(() -> new MigrationSchema(source).migrer())
                .as("et elle est de nouveau utilisable : le retour arrière ne laisse pas de journal périmé")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sans base préalable, le fichier restauré est retiré : on repart comme avant")
    void sans_base_prealable_le_fichier_est_retire() throws SQLException {
        Path sauvegarde = service.sauvegarder(racine.resolve("sauvegardes"));
        abimerLesEmpreintes(sauvegarde);
        // Un poste neuf : aucune base, donc aucun filet à remettre.
        Path vierge = racine.resolve("poste-neuf");
        SourceDeDonnees sourceVierge = new SourceDeDonnees(new Workspace(vierge));
        ServiceSauvegarde surPosteNeuf =
                new ServiceSauvegarde(sourceVierge, new HorlogeFigee(LocalDateTime.of(2026, 8, 3, 9, 0)));

        assertThatThrownBy(() -> surPosteNeuf.restaurer(sauvegarde))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("vous repartez comme avant");

        assertThat(vierge.resolve(Workspace.FICHIER_BASE))
                .as("l'état initial était « pas de base » : c'est celui-là qu'on rend")
                .doesNotExist();
    }

    @Test
    @DisplayName("une sauvegarde écrite par une version plus récente est refusée AVANT tout remplacement")
    void sauvegarde_trop_recente_refusee() throws SQLException {
        Path sauvegarde = service.sauvegarder(racine.resolve("sauvegardes"));
        inscrireUneVersionInconnue(sauvegarde);
        executer("INSERT INTO user(local_id, display_name) VALUES ('u-apres', 'Alice')");

        assertThatThrownBy(() -> service.restaurer(sauvegarde))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("version plus récente")
                .as("le refus doit dire que l'état local est intact, c'est ce qui décide de la suite")
                .hasMessageContaining("Rien n'a été touché");

        assertThat(utilisateurs()).containsExactly("Alice");
        assertThat(workspaceDir.resolve(Workspace.FICHIER_BASE + ".avant-restauration"))
                .as("le filet n'a même pas eu à être posé : le refus précède l'échange")
                .doesNotExist();
    }

    @Test
    @DisplayName("une restauration qui aboutit reste une restauration")
    void restauration_nominale_inchangee() throws SQLException {
        executer("INSERT INTO user(local_id, display_name) VALUES ('u-1', 'Alice')");
        Path sauvegarde = service.sauvegarder(racine.resolve("sauvegardes"));
        executer("INSERT INTO user(local_id, display_name) VALUES ('u-2', 'Bob')");

        service.restaurer(sauvegarde);

        assertThat(utilisateurs())
                .as("le rattrapage ne doit pas rendre la restauration frileuse")
                .containsExactly("Alice");
    }

    /// Casse l'empreinte d'une migration **dans la sauvegarde** : au moment de migrer, le registre
    /// annoncera un script qui ne correspond plus au sien, et la migration refusera (#2729). C'est la
    /// panne la plus réaliste, celle d'un script modifié après coup.
    private static void abimerLesEmpreintes(Path sauvegarde) throws SQLException {
        surLaSauvegarde(sauvegarde, "UPDATE schema_version SET checksum = 'empreinte-qui-ne-correspond-a-rien'");
    }

    /// Inscrit une version que cette application ne connaît pas : la sauvegarde vient d'un binaire
    /// plus récent.
    private static void inscrireUneVersionInconnue(Path sauvegarde) throws SQLException {
        surLaSauvegarde(
                sauvegarde, "INSERT INTO schema_version(version, applied_at) VALUES (9999, '2027-01-01T00:00')");
    }

    private static void surLaSauvegarde(Path sauvegarde, String sql) throws SQLException {
        SQLiteDataSource ecriture = new SQLiteDataSource();
        ecriture.setUrl("jdbc:sqlite:" + sauvegarde);
        try (Connection cx = ecriture.getConnection();
                Statement st = cx.createStatement()) {
            st.execute(sql);
        }
    }

    private void executer(String sql) throws SQLException {
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement()) {
            st.execute(sql);
        }
    }

    private List<String> utilisateurs() throws SQLException {
        List<String> noms = new ArrayList<>();
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery("SELECT display_name FROM user ORDER BY local_id")) {
            while (rs.next()) {
                noms.add(rs.getString(1));
            }
        }
        return noms;
    }
}
