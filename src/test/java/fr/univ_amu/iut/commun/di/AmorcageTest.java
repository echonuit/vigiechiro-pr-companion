package fr.univ_amu.iut.commun.di;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.nio.file.Files;
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

/// La phase d'amorçage « migrer, puis composer » (#2187, ADR 1038).
///
/// L'ordre lui-même - migration avant lecture des drapeaux - ne devient observable qu'avec une
/// migration portant sur une clé `feature.*`, qui n'existe pas encore. Ce que ces tests épinglent est
/// donc le contrat de chaque entrée sur une base **absente** : l'application graphique la crée, la CLI
/// ne la crée pas. C'est cette différence qui garantit qu'une aide CLI ne laisse aucun fichier.
///
/// ⚠️ Les assertions qui interrogent l'injecteur restent **dans** la portée de la propriété
/// `vigiechiro.workspace`. `CommunModule` résout `Workspace` **paresseusement** (`Workspace.resolu()`
/// au premier `getInstance`) : hors de cette portée, l'injecteur retomberait sur le workspace par
/// défaut - c'est-à-dire la vraie base de qui lance le build. En production la propriété est posée une
/// fois pour tout le processus, donc migration et injecteur voient le même workspace ; ici il faut la
/// maintenir à la main.
class AmorcageTest {

    private static final String PROP_WORKSPACE = "vigiechiro.workspace";
    private static final String FICHIER_BASE = "vigiechiro.db";
    private static final String TABLE_MIGREE = "app_setting";

    @Test
    @DisplayName("migrerPuisComposer : base absente créée et migrée, injecteur fonctionnel (application graphique)")
    void migrer_puis_composer_cree_et_migre(@TempDir Path racine) throws Exception {
        surWorkspace(racine, () -> {
            Injector injecteur = Amorcage.migrerPuisComposer();

            assertThat(racine.resolve(FICHIER_BASE))
                    .as("l'application graphique a toujours besoin d'une base ouvrable : elle la crée")
                    .isRegularFile();
            assertThat(tables(injecteur.getInstance(SourceDeDonnees.class)))
                    .as("la base est migrée avant que la composition ne lise les drapeaux (#2187)")
                    .contains(TABLE_MIGREE);
            assertThat(injecteur.getInstance(Workspace.class).racine()).isEqualTo(racine.toAbsolutePath());
        });
    }

    @Test
    @DisplayName("migrerSiPresente : base absente laissée absente - une aide CLI ne crée aucun fichier")
    void migrer_si_presente_ne_cree_rien(@TempDir Path racine) throws Exception {
        surWorkspace(racine, Amorcage::migrerSiPresente);

        assertThat(racine.resolve(FICHIER_BASE))
                .as("sur une base absente il n'y a ni schéma à migrer ni drapeau à périmer : ne rien créer")
                .doesNotExist();
        assertThat(racine).isEmptyDirectory();
    }

    @Test
    @DisplayName("migrerSiPresente : base présente mais non migrée - elle est mise à jour")
    void migrer_si_presente_migre_une_base_existante(@TempDir Path racine) throws Exception {
        // Une base « présente mais vide » : le fichier existe, sans aucune table. C'est l'état d'une
        // installation dont la base a été créée par un ancien code puis jamais migrée.
        Files.createDirectories(racine);
        try (Connection cx = new SourceDeDonnees(new Workspace(racine)).getConnection();
                Statement st = cx.createStatement()) {
            st.execute("PRAGMA user_version = 0");
        }
        assertThat(racine.resolve(FICHIER_BASE)).isRegularFile();
        assertThat(tables(new SourceDeDonnees(new Workspace(racine)))).doesNotContain(TABLE_MIGREE);

        surWorkspace(racine, Amorcage::migrerSiPresente);

        assertThat(tables(new SourceDeDonnees(new Workspace(racine))))
                .as("la base existait : migrerSiPresente doit l'avoir mise à jour")
                .contains(TABLE_MIGREE);
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

    /// Corps de test susceptible de lever (accès SQL, composition).
    @FunctionalInterface
    private interface CorpsTest {
        void executer() throws Exception;
    }

    /// Exécute `corps` avec `vigiechiro.workspace` posé sur `racine` (priorité la plus forte, cf.
    /// `Workspace.resolu()`), puis restaure exactement ce qui était là. La configuration d'amorçage est
    /// déjà détournée vers `target/` par Surefire, donc seule la propriété compte ici.
    private static void surWorkspace(Path racine, CorpsTest corps) throws Exception {
        String avant = System.getProperty(PROP_WORKSPACE);
        try {
            System.setProperty(PROP_WORKSPACE, racine.toString());
            corps.executer();
        } finally {
            if (avant == null) {
                System.clearProperty(PROP_WORKSPACE);
            } else {
                System.setProperty(PROP_WORKSPACE, avant);
            }
        }
    }
}
