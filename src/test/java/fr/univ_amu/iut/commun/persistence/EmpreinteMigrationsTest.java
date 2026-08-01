package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Workspace;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// L'**empreinte** que chaque migration laisse dans `schema_version` (#2729, lot 1 du chantier de
/// dette #2720).
///
/// Un script déjà appliqué ne se rejoue jamais : le modifier après coup fait diverger en silence les
/// bases qui l'ont subi dans sa première version de celles qui naissent avec la seconde. Rien ne le
/// signalait. Ces tests vérifient que la dérive est désormais un **refus au démarrage**, et que ce
/// refus arrive **avant** d'avoir migré quoi que ce soit par-dessus.
///
/// Une modification de script ne se simule pas depuis un test (les scripts vivent dans les
/// ressources), mais elle n'a pas besoin de l'être : ce que le migrateur compare, c'est l'empreinte
/// **inscrite en base** et celle du script. Fausser la première dit exactement la même chose que
/// modifier le second.
class EmpreinteMigrationsTest {

    @TempDir
    Path racine;

    @Test
    @DisplayName("une base neuve garde l'empreinte de chaque migration qu'elle a appliquée")
    void base_neuve_garde_une_empreinte_par_migration() throws SQLException {
        SourceDeDonnees source = migrerUneBaseNeuve();

        Map<Integer, String> empreintes = empreintesEnBase(source);

        assertThat(empreintes).hasSize(MigrationSchema.MIGRATIONS.length);
        assertThat(empreintes.values())
                .as("chaque migration laisse un SHA-256 en hexadécimal, aucune n'est laissée vide")
                .allSatisfy(empreinte -> assertThat(empreinte).matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("l'empreinte porte sur les instructions, pas sur le texte du fichier")
    void empreinte_des_instructions_et_non_du_fichier() throws SQLException {
        SourceDeDonnees source = migrerUneBaseNeuve();
        String contenu = lireRessource("/db/migration/V33__passage_campagne.sql");

        String inscrite = empreintesEnBase(source).get(33);

        assertThat(inscrite)
                .as("l'empreinte est celle des instructions, telles que le découpage les produit")
                .isEqualTo(sha256(String.join(";", instructions(contenu))));
        assertThat(inscrite)
                .as("et donc PAS celle du fichier brut : V33 porte cinq lignes de commentaire, les"
                        + " corriger ne change rien à ce que la base reçoit et ne doit pas faire refuser"
                        + " le démarrage")
                .isNotEqualTo(sha256(contenu));
    }

    @Test
    @DisplayName("un script modifié après avoir été appliqué fait refuser le démarrage")
    void script_modifie_apres_coup_fait_refuser() throws SQLException {
        SourceDeDonnees source = migrerUneBaseNeuve();
        fausserEmpreinte(source, 7);

        assertThatThrownBy(() -> new MigrationSchema(source).migrer())
                .isInstanceOf(DataAccessException.class)
                .as("le refus nomme le script en cause")
                .hasMessageContaining("V07__renommer_median_freq_khz.sql")
                .as("et dit quoi faire, sinon il ne reste qu'un démarrage impossible")
                .hasMessageContaining("Rétablissez");
    }

    @Test
    @DisplayName("le refus nomme toutes les migrations qui ont dérivé, pas seulement la première")
    void le_refus_nomme_toutes_les_derives() throws SQLException {
        SourceDeDonnees source = migrerUneBaseNeuve();
        fausserEmpreinte(source, 7);
        fausserEmpreinte(source, 26);

        assertThatThrownBy(() -> new MigrationSchema(source).migrer())
                .hasMessageContaining("2 migrations")
                .hasMessageContaining("V07__renommer_median_freq_khz.sql")
                .hasMessageContaining("V26__validation_expert.sql");
    }

    @Test
    @DisplayName("le refus arrive avant d'appliquer la moindre migration en attente")
    void le_refus_precede_toute_application() throws SQLException {
        SourceDeDonnees source = migrerUneBaseNeuve();
        fausserEmpreinte(source, 7);
        executer(source, "DELETE FROM schema_version WHERE version = 38");

        assertThatThrownBy(() -> new MigrationSchema(source).migrer())
                .hasMessageContaining("V07__renommer_median_freq_khz.sql");

        assertThat(empreintesEnBase(source))
                .as("V38 était redevenue en attente : elle ne doit pas avoir été appliquée par-dessus"
                        + " une base dont on vient de dire qu'on ne la comprend plus")
                .doesNotContainKey(38);
    }

    @Test
    @DisplayName("une base migrée avant les empreintes est étalonnée au premier lancement")
    void base_sans_empreinte_est_etalonnee() throws SQLException {
        SourceDeDonnees source = migrerUneBaseNeuve();
        Map<Integer, String> avant = empreintesEnBase(source);
        executer(source, "ALTER TABLE schema_version DROP COLUMN checksum");

        new MigrationSchema(source).migrer();

        assertThat(empreintesEnBase(source))
                .as("l'étalonnage fige ce que les scripts disent aujourd'hui, pour que toute dérive"
                        + " ultérieure se voie")
                .containsExactlyInAnyOrderEntriesOf(avant);
    }

    @Test
    @DisplayName("l'étalonnage n'écrase jamais une empreinte déjà inscrite")
    void etalonnage_n_ecrase_pas_une_empreinte_existante() throws SQLException {
        SourceDeDonnees source = migrerUneBaseNeuve();
        String inscrite = empreintesEnBase(source).get(7);

        try (Connection cx = source.getConnection()) {
            new RegistreMigrations(source).etalonner(cx, Map.of(7, "empreinte-de-remplacement"));
        }

        assertThat(empreintesEnBase(source).get(7))
                .as("une empreinte connue fait foi : l'étalonnage ne comble que les trous, sinon il"
                        + " effacerait la mémoire qu'il est censé constituer")
                .isEqualTo(inscrite);
    }

    private SourceDeDonnees migrerUneBaseNeuve() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(racine.resolve("ws")));
        new MigrationSchema(source).migrer();
        return source;
    }

    private static void fausserEmpreinte(SourceDeDonnees source, int version) throws SQLException {
        executer(source, "UPDATE schema_version SET checksum = 'autre-chose' WHERE version = " + version);
    }

    private static void executer(SourceDeDonnees source, String sql) throws SQLException {
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement()) {
            st.execute(sql);
        }
    }

    private static Map<Integer, String> empreintesEnBase(SourceDeDonnees source) throws SQLException {
        Map<Integer, String> empreintes = new LinkedHashMap<>();
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery("SELECT version, checksum FROM schema_version")) {
            while (rs.next()) {
                empreintes.put(rs.getInt(1), rs.getString(2));
            }
        }
        return empreintes;
    }

    private static String lireRessource(String chemin) {
        try (InputStream in = MigrationSchema.class.getResourceAsStream(chemin)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Migration illisible : " + chemin, e);
        }
    }

    /// Même découpage que le migrateur, réécrit ici pour dire le contrat depuis l'extérieur : lignes
    /// de commentaire retirées, script coupé sur les `;`.
    private static String[] instructions(String sql) {
        StringBuilder sansCommentaires = new StringBuilder();
        for (String ligne : sql.split("\n")) {
            if (!ligne.strip().startsWith("--")) {
                sansCommentaires.append(ligne).append('\n');
            }
        }
        return Arrays.stream(sansCommentaires.toString().split(";"))
                .map(String::strip)
                .filter(instruction -> !instruction.isEmpty())
                .toArray(String[]::new);
    }

    private static String sha256(String contenu) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(contenu.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
