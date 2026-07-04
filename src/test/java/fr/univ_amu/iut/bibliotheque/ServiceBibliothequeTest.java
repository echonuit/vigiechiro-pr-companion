package fr.univ_amu.iut.bibliotheque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.bibliotheque.model.EntreeBiblio;
import fr.univ_amu.iut.bibliotheque.model.ExportBiblioSons;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
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

/// Test « réel » du [ServiceBibliotheque] (mode par défaut : SQLite jetable `@TempDir` +
/// [MigrationSchema], vrais DAO). Vérifie le cœur de la feature `bibliotheque` (P10) :
///
/// - seules les observations marquées `is_reference` sont exportées ;
/// - le récapitulatif CSV reprend les bons champs (taxon retenu, séquence source, fichier,
///   fréquence, commentaire) dans un ordre déterministe ;
/// - le comptage et la liste (dédupliquée) des chemins à copier sont corrects ;
/// - le taxon retenu est l'observateur s'il est saisi, sinon Tadarida ;
/// - une observation de référence pointant une séquence introuvable lève une règle dure.
///
/// La chaîne de FK (passage → session → original, et passage → résultats) n'a pas de DAO dans
/// cette feature : on la sème directement en SQL pour rester autonome (duplication assumée, cf.
/// conventions). Les séquences et observations passent en revanche par leurs vrais DAO.
class ServiceBibliothequeTest {

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private SequenceDao sequenceDao;
    private ObservationDao observationDao;
    private ServiceBibliotheque service;

    private long idResultats;
    private long idSeqAlpha;
    private long idSeqBeta;
    private long idSeqGamma;

    @BeforeEach
    void preparer() throws SQLException {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();

        long idSession;
        long idOriginal;
        try (Connection cx = source.getConnection()) {
            executer(cx, "INSERT INTO user(local_id, display_name) VALUES ('u-1', 'Testeur')");
            long idSite = insererCle(
                    cx,
                    "INSERT INTO monitoring_site(square_number, protocol, created_at, user_id)"
                            + " VALUES ('640380', 'Point fixe standard', '2026-05-01', 'u-1')");
            long idPoint = insererCle(cx, "INSERT INTO listening_point(code, site_id) VALUES ('A1', ?)", idSite);
            executer(cx, "INSERT INTO recorder(serial_number) VALUES ('SN-1')");
            long idPassage = insererCle(
                    cx,
                    "INSERT INTO passage(passage_number, year, recording_date, start_time, end_time,"
                            + " workflow_status, point_id, recorder_id)"
                            + " VALUES (1, 2026, '2026-06-20', '21:00', '05:00', 'Importé', ?, 'SN-1')",
                    idPoint);
            idSession =
                    insererCle(cx, "INSERT INTO recording_session(root_path, passage_id) VALUES ('/ws', ?)", idPassage);
            idOriginal = insererCle(
                    cx,
                    "INSERT INTO original_recording(file_name, file_path, session_id)"
                            + " VALUES ('orig.wav', '/ws/bruts/orig.wav', ?)",
                    idSession);
            idResultats = insererCle(
                    cx,
                    "INSERT INTO identification_results(file_path, detected_format, imported_at,"
                            + " passage_id) VALUES ('/ws/transformes/obs.csv', 'Vu', '2026-06-21', ?)",
                    idPassage);
        }

        sequenceDao = new SequenceDao(source);
        observationDao = new ObservationDao(source);
        idSeqAlpha = sequenceDao
                .insert(sequence("a_000.wav", "/ws/transformes/a_000.wav", 0, idOriginal, idSession))
                .id();
        idSeqBeta = sequenceDao
                .insert(sequence("b_000.wav", "/ws/transformes/b_000.wav", 1, idOriginal, idSession))
                .id();
        idSeqGamma = sequenceDao
                .insert(sequence("c_000.wav", "/ws/transformes/c_000.wav", 2, idOriginal, idSession))
                .id();

        service = new ServiceBibliotheque(observationDao, sequenceDao);
    }

    @Test
    @DisplayName("Seules les observations marquées référence sont exportées")
    void seules_les_references_sont_exportees() {
        // 3 références (Nyclei sans observateur, Pippip validé, Rhihip corrigé) + 2 non-références.
        observationDao.insert(reference(idSeqBeta, "Nyclei", null, 22000, "noctule claire"));
        observationDao.insert(reference(idSeqAlpha, "Pippip", "Pippip", 45, "signal net"));
        observationDao.insert(reference(idSeqGamma, "Pippip", "Rhihip", 30000, null));
        observationDao.insert(nonReference(idSeqAlpha, "Tadten"));
        observationDao.insert(nonReference(idSeqBeta, "Pippip"));

        ExportBiblioSons export = service.exporterBibliotheque();

        assertThat(export.nombre())
                .as("3 observations marquées référence sur 5")
                .isEqualTo(3);
        assertThat(export.entrees())
                .extracting(EntreeBiblio::taxon)
                .as("taxon retenu = observateur si saisi (Rhihip), sinon Tadarida ; trié")
                .containsExactly("Nyclei", "Pippip", "Rhihip");
        assertThat(export.entrees())
                .extracting(EntreeBiblio::taxon)
                .as("aucune observation non-référence (Tadten) ne fuit dans l'export")
                .doesNotContain("Tadten");
    }

    @Test
    @DisplayName("Le récapitulatif CSV reprend taxon, séquence, fichier, fréquence et commentaire")
    void csv_recapitulatif_correct() {
        observationDao.insert(reference(idSeqBeta, "Nyclei", null, 22000, "noctule claire"));
        observationDao.insert(reference(idSeqAlpha, "Pippip", "Pippip", 45, "signal net"));
        observationDao.insert(reference(idSeqGamma, "Pippip", "Rhihip", 30000, null));

        String csv = service.exporterBibliotheque().versCsv();

        assertThat(csv)
                .startsWith("taxon;sequence source;fichier;frequence;commentaire\n")
                .contains("Nyclei;b_000.wav;/ws/transformes/b_000.wav;22000;noctule claire\n")
                .contains("Pippip;a_000.wav;/ws/transformes/a_000.wav;45;signal net\n")
                .contains("Rhihip;c_000.wav;/ws/transformes/c_000.wav;30000;\n");
        assertThat(csv.lines()).as("en-tête + 3 lignes de données").hasSize(4);
    }

    @Test
    @DisplayName("La liste des fichiers à copier est dédupliquée et dans l'ordre des entrées")
    void chemins_a_copier_dedupliques() {
        // Deux observations de référence sur la MÊME séquence (espèces différentes) → un seul fichier.
        observationDao.insert(reference(idSeqAlpha, "Pippip", "Pippip", 45, "m1"));
        observationDao.insert(reference(idSeqAlpha, "Nyclei", "Nyclei", 40000, "m2"));
        observationDao.insert(reference(idSeqBeta, "Rhihip", "Rhihip", 30000, "m3"));

        ExportBiblioSons export = service.exporterBibliotheque();

        assertThat(export.nombre())
                .as("3 entrées (2 partagent la séquence alpha)")
                .isEqualTo(3);
        assertThat(export.cheminsSequences())
                .as("2 fichiers distincts à copier, sans doublon")
                .containsExactly("/ws/transformes/a_000.wav", "/ws/transformes/b_000.wav");
    }

    @Test
    @DisplayName("Aucune observation référence → export vide (CSV réduit à l'en-tête)")
    void export_vide_quand_aucune_reference() {
        observationDao.insert(nonReference(idSeqAlpha, "Pippip"));

        ExportBiblioSons export = service.exporterBibliotheque();

        assertThat(export.nombre()).isZero();
        assertThat(export.entrees()).isEmpty();
        assertThat(export.cheminsSequences()).isEmpty();
        assertThat(export.versCsv()).isEqualTo("taxon;sequence source;fichier;frequence;commentaire\n");
    }

    @Test
    @DisplayName("Une référence pointant une séquence introuvable lève une règle dure")
    void reference_vers_sequence_introuvable_leve_regle_dure() {
        observationDao.insert(reference(idSeqBeta, "Nyclei", null, 22000, null));
        // On casse l'intégrité : la séquence disparaît sans déclencher la cascade FK qui supprimerait
        // aussi l'observation → on fabrique ainsi une observation de référence orpheline.
        supprimerSequenceSansCascade(idSeqBeta);

        assertThatThrownBy(() -> service.exporterBibliotheque())
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Séquence d'écoute introuvable");
    }

    // --- fabriques d'entités (duplication assumée, chaque test autonome) ----------------------

    private static SequenceDEcoute sequence(String nom, String chemin, int index, long idOriginal, long idSession) {
        return new SequenceDEcoute(null, nom, idOriginal, index, 0.0, 5.0, chemin, false, idSession);
    }

    private Observation reference(
            long idSequence, String tadarida, String observateur, Integer freq, String commentaire) {
        return new Observation(
                null,
                idSequence,
                null,
                null,
                freq,
                tadarida,
                null,
                null,
                observateur,
                observateur == null ? null : 0.95,
                commentaire,
                true,
                ModeValidation.MANUEL,
                idResultats);
    }

    private Observation nonReference(long idSequence, String tadarida) {
        return new Observation(
                null,
                idSequence,
                null,
                null,
                null,
                tadarida,
                null,
                null,
                null,
                null,
                null,
                false,
                ModeValidation.NON_VALIDE,
                idResultats);
    }

    /// Supprime une séquence sans déclencher la cascade vers l'observation (désactive temporairement
    /// `foreign_keys`), pour fabriquer artificiellement l'incohérence « observation orpheline ».
    private void supprimerSequenceSansCascade(long idSequence) {
        try (Connection cx = source.getConnection()) {
            executer(cx, "PRAGMA foreign_keys = OFF");
            try (PreparedStatement ps = cx.prepareStatement("DELETE FROM listening_sequence WHERE id = ?")) {
                ps.setLong(1, idSequence);
                ps.executeUpdate();
            }
            executer(cx, "PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
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
