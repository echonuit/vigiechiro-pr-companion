package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import fr.univ_amu.iut.commun.model.Workspace;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// Ce qui reste de la base quand une migration est **interrompue en plein script** (#2728, lot 1 du
/// chantier de dette #2720).
///
/// Le cas nominal est couvert ailleurs ([MigrationSchemaTest] applique et rejoue le catalogue entier).
/// Ce qui ne l'était pas, c'est la panne au milieu : coupure de courant, processus tué, disque plein.
/// Elle laissait un schéma **partiellement** modifié qu'aucune ligne de `schema_version` ne décrivait,
/// et le lancement suivant rejouait le script depuis le début sur ce schéma déjà à moitié migré. Les
/// scripts n'étant pas idempotents (aucun `IF NOT EXISTS` en V01, deux `ADD COLUMN` en V26),
/// l'application ne redémarrait plus du tout.
///
/// Chaque test simule la panne avec une [SourceDeDonnees] dont la N-ième instruction échoue, puis
/// vérifie les **deux moitiés** de la propriété : rien de partiel ne subsiste, et un lancement suivant
/// en bonne santé migre la base jusqu'au bout.
class MigrationInterrompueTest {

    /// Rang global (toutes migrations confondues) de la 3e instruction de V26, qui crée la table
    /// `observation_message`. Les deux instructions qui la précèdent dans ce script sont des
    /// `ALTER TABLE observation ADD COLUMN` : sans transaction, elles survivent à la panne et le rejeu
    /// bute sur « duplicate column name ». C'est le rang le plus révélateur du catalogue.
    private static final int TROISIEME_INSTRUCTION_DE_V26 = 821;

    /// Rang global de la 1re instruction de V26 : un `ALTER TABLE … ADD COLUMN … REFERENCES …` tenant
    /// sur une seule ligne de 78 caractères, de quoi éprouver la troncature du message.
    private static final int PREMIERE_INSTRUCTION_DE_V26 = TROISIEME_INSTRUCTION_DE_V26 - 2;

    @TempDir
    Path racine;

    @ParameterizedTest(name = "panne à l''instruction n°{0} de V01")
    @ValueSource(ints = {1, 5, 12, 19, 23})
    @DisplayName("une panne pendant la création du schéma ne laisse aucune table derrière elle")
    void panne_pendant_v01_ne_laisse_aucune_table(int rangFatal) throws SQLException {
        Workspace workspace = new Workspace(racine.resolve("ws"));

        assertThatThrownBy(() -> new MigrationSchema(new SourceQuiTombe(workspace, rangFatal)).migrer())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("V01__schema.sql");

        assertThat(tables(new SourceDeDonnees(workspace)))
                .as("la migration a été annulée : aucune des tables créées avant la panne ne subsiste")
                .isEmpty();
    }

    @ParameterizedTest(name = "panne à l''instruction n°{0}")
    @ValueSource(ints = {1, 5, 12, 19, 23, 25, TROISIEME_INSTRUCTION_DE_V26})
    @DisplayName("après une panne, le lancement suivant migre la base jusqu'au bout")
    void apres_une_panne_le_lancement_suivant_va_au_bout(int rangFatal) throws SQLException {
        Workspace workspace = new Workspace(racine.resolve("ws"));
        assertThatThrownBy(() -> new MigrationSchema(new SourceQuiTombe(workspace, rangFatal)).migrer())
                .isInstanceOf(DataAccessException.class);

        SourceDeDonnees saine = new SourceDeDonnees(workspace);
        new MigrationSchema(saine).migrer();

        assertThat(versionsEnBase(saine))
                .as("le rejeu repart d'un schéma net : les migrations du catalogue s'appliquent, et"
                        + " chacune s'inscrit sous SON numéro (une version mal écrite ferait tout rejouer"
                        + " au lancement suivant)")
                .containsExactlyInAnyOrderElementsOf(versionsDuCatalogue());
    }

    @Test
    @DisplayName("une panne au milieu de V26 ne laisse ni ses colonnes ni sa version")
    void panne_au_milieu_de_v26_ne_laisse_rien() throws SQLException {
        Workspace workspace = new Workspace(racine.resolve("ws"));
        SourceQuiTombe source = new SourceQuiTombe(workspace, TROISIEME_INSTRUCTION_DE_V26);

        assertThatThrownBy(() -> new MigrationSchema(source).migrer())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("V26__validation_expert.sql")
                .as("le message situe la panne dans le script, sinon il faut relire les 4 instructions")
                .hasMessageContaining("n°3")
                .as("et il cite l'instruction, pour la reconnaître sans ouvrir le fichier")
                .hasMessageContaining("CREATE TABLE observation_message");

        SourceDeDonnees relecture = new SourceDeDonnees(workspace);
        assertThat(versionsEnBase(relecture))
                .as("les 25 migrations qui ont abouti restent acquises, la 26e n'a pas eu lieu")
                .containsExactlyInAnyOrderElementsOf(versionsDuCatalogue().subList(0, 25));
        assertThat(colonnes(relecture, "observation"))
                .as("les deux ADD COLUMN qui précédaient la panne ont été annulés avec elle")
                .doesNotContain("taxon_validator", "validator_certainty");
        assertThat(tables(relecture)).doesNotContain("observation_message");
    }

    @Test
    @DisplayName("une instruction longue est citée tronquée, pas déversée en entier")
    void instruction_longue_citee_tronquee() {
        Workspace workspace = new Workspace(racine.resolve("ws"));
        SourceQuiTombe source = new SourceQuiTombe(workspace, PREMIERE_INSTRUCTION_DE_V26);

        Throwable echec = catchThrowable(() -> new MigrationSchema(source).migrer());

        assertThat(echec).isInstanceOf(DataAccessException.class);
        assertThat(echec.getMessage())
                .as("l'instruction fautive est reconnaissable")
                .contains("ALTER TABLE observation ADD COLUMN taxon_validator")
                .as("mais un script peut porter des instructions de plusieurs lignes : le message en"
                        + " garde de quoi situer, pas le corps entier")
                .contains("…")
                .doesNotContain("REFERENCES taxon(code)");
    }

    /// Les numéros de version que le catalogue doit inscrire, dans l'ordre du catalogue.
    private static List<Integer> versionsDuCatalogue() {
        List<Integer> versions = new ArrayList<>();
        for (String fichier : MigrationSchema.MIGRATIONS) {
            versions.add(Integer.parseInt(fichier.substring(1, fichier.indexOf("__"))));
        }
        return versions;
    }

    /// Source de données dont la N-ième instruction de script échoue, pour simuler une coupure au
    /// milieu d'une migration.
    ///
    /// Elle enveloppe les connexions de sa classe mère dans un mandataire qui compte les
    /// `Statement.execute(String)` : c'est exactement ce qu'exécute le lecteur de scripts, et rien
    /// d'autre (les lectures de `schema_version` passent par `executeQuery`, l'inscription de version
    /// par un `PreparedStatement`). Le compte est donc celui des instructions de migration.
    private static final class SourceQuiTombe extends SourceDeDonnees {

        private final int rangFatal;
        private int rang;

        SourceQuiTombe(Workspace workspace, int rangFatal) {
            super(workspace);
            this.rangFatal = rangFatal;
        }

        @Override
        public Connection getConnection() {
            Connection vraie = super.getConnection();
            return (Connection) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (mandataire, methode, arguments) -> {
                        Object resultat = deleguer(vraie, methode, arguments);
                        return resultat instanceof Statement st && !(resultat instanceof PreparedStatement)
                                ? pieger(st)
                                : resultat;
                    });
        }

        private Statement pieger(Statement vrai) {
            return (Statement) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {Statement.class}, (mandataire, methode, arguments) -> {
                        if (estUneInstructionDeScript(methode, arguments) && ++rang == rangFatal) {
                            throw new SQLException("panne simulée à l'instruction n°" + rang);
                        }
                        return deleguer(vrai, methode, arguments);
                    });
        }

        /// Le registre des migrations pose lui-même sa colonne d'empreinte, par un `ALTER TABLE` qui
        /// n'appartient à aucun script (#2729). Le compter décalerait tous les rangs d'un cran à
        /// partir de la première migration.
        private static final String DDL_DU_REGISTRE = "ALTER TABLE schema_version ADD COLUMN checksum TEXT";

        private static boolean estUneInstructionDeScript(Method methode, Object[] arguments) {
            return "execute".equals(methode.getName())
                    && arguments != null
                    && arguments.length == 1
                    && !DDL_DU_REGISTRE.equals(arguments[0]);
        }

        private static Object deleguer(Object cible, Method methode, Object[] arguments) throws Throwable {
            try {
                return methode.invoke(cible, arguments);
            } catch (InvocationTargetException enveloppe) {
                throw enveloppe.getCause();
            }
        }
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
