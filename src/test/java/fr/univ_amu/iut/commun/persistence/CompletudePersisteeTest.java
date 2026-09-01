package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Completude;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La complétude d'une nuit survit à l'écriture en base (#5030, `V45`).
///
/// Elle était calculée à l'import et **persistée nulle part** : le diagnostic, qui s'ouvre plus tard
/// sur un passage en base, ne pouvait pas la retrouver. Ce banc éprouve les deux bouts du fil, et
/// surtout le **report** : ce que rend une ligne écrite avant la migration.
class CompletudePersisteeTest {

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private JournalDuCapteurDao dao;
    private long prochainPassage = 1;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        dao = new JournalDuCapteurDao(source);
    }

    @Test
    @DisplayName("#5030 : les trois états font l'aller-retour, sans se confondre")
    void les_trois_etats_font_l_aller_retour() {
        for (Completude etat : Completude.values()) {
            long idSession = seedSession("session-" + etat);

            JournalDuCapteur ecrit = dao.insert(new JournalDuCapteur(null, "LogPR1.txt", null, null, etat, idSession));
            JournalDuCapteur relu = dao.trouverParSession(idSession).orElseThrow();

            assertThat(ecrit.completude()).as("ce que l'insertion rend").isEqualTo(etat);
            assertThat(relu.completude())
                    .as("ce que la base rend, sur %s", etat)
                    .isEqualTo(etat);
        }
    }

    @Test
    @DisplayName("#5030 : une ligne d'avant la migration se relit INCONNUE, jamais complète")
    void une_ligne_heritee_se_relit_inconnue() {
        // C'est le contrôle qui compte. Une base d'avant `V45` porte `night_completeness` à NULL, et
        // le rabattre sur COMPLETE referait au REPORT le défaut que #4990 a corrigé au CALCUL :
        // l'absence de preuve lue comme une preuve, avec le badge le plus rassurant sur la nuit dont
        // on sait le moins.
        long idSession = seedSession("session-heritee");
        executer("INSERT INTO sensor_log (file_path, parsed_events, detected_anomalies, session_id)"
                + " VALUES ('LogPR1.txt', NULL, NULL, " + idSession + ")");

        JournalDuCapteur relu = dao.trouverParSession(idSession).orElseThrow();

        assertThat(relu.completude()).isEqualTo(Completude.INCONNUE);
    }

    @Test
    @DisplayName("#5030 : une valeur que cette version ne connaît pas se lit « inconnue », sans lever")
    void une_valeur_inconnue_ne_leve_pas() {
        // Une base écrite par une version plus récente, ou une valeur abîmée à la main. La lecture ne
        // doit ni lever - la nuit resterait inaccessible - ni rabattre sur une valeur plausible : elle
        // dit « je ne sais pas », ce qui est exactement vrai.
        long idSession = seedSession("session-venue-d-ailleurs");
        executer("INSERT INTO sensor_log (file_path, parsed_events, detected_anomalies,"
                + " night_completeness, session_id)"
                + " VALUES ('LogPR1.txt', NULL, NULL, 'INTERROMPUE_AU_MILIEU', " + idSession + ")");

        JournalDuCapteur relu = dao.trouverParSession(idSession).orElseThrow();

        assertThat(relu.completude()).isEqualTo(Completude.INCONNUE);
    }

    @Test
    @DisplayName("#5030 : la mise à jour conserve la complétude, elle ne la remet pas à zéro")
    void la_mise_a_jour_conserve_la_completude() {
        long idSession = seedSession("session-mise-a-jour");
        JournalDuCapteur ecrit =
                dao.insert(new JournalDuCapteur(null, "LogPR1.txt", null, null, Completude.TRONQUEE, idSession));

        dao.update(new JournalDuCapteur(ecrit.id(), "LogPR1.txt", "[]", "[]", ecrit.completude(), idSession));

        assertThat(dao.trouverParSession(idSession).orElseThrow().completude())
                .as("l'`UPDATE` porte cinq valeurs pour cinq paramètres : un décalage y passerait la"
                        + " session à la place de la complétude, et la relecture rendrait INCONNUE")
                .isEqualTo(Completude.TRONQUEE);
    }

    /// Une session d'enregistrement minimale, seule dépendance de `sensor_log`.
    ///
    /// `passage_id` est NOT NULL **et UNIQUE** - une session par passage - et pointe un passage qu'on
    /// ne sème pas : les clés étrangères sont coupées le temps du semis, comme le fait
    /// `BackfillVerdictMigrationTest`. Ce banc éprouve une colonne et sa relecture, pas l'intégrité
    /// référentielle. D'où un passage distinct par session, faute de quoi la deuxième insertion tombe
    /// sur la contrainte d'unicité.
    private long seedSession(String racine) {
        long idPassage = prochainPassage++;
        executer("INSERT INTO recording_session (root_path, passage_id) VALUES ('" + racine + "', " + idPassage + ")");
        return dernierId("recording_session");
    }

    private void executer(String sql) {
        try (Connection cnx = source.getConnection();
                Statement st = cnx.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF");
            st.executeUpdate(sql);
        } catch (SQLException echec) {
            throw new IllegalStateException("Semis impossible : " + sql, echec);
        }
    }

    private long dernierId(String table) {
        try (Connection cnx = source.getConnection();
                Statement st = cnx.createStatement();
                var rs = st.executeQuery("SELECT MAX(id) FROM " + table)) {
            return rs.getLong(1);
        } catch (SQLException echec) {
            throw new IllegalStateException("Lecture d'identifiant impossible sur " + table, echec);
        }
    }
}
