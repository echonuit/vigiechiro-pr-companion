package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.CertitudeObservateur;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.SaisieCertitude;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Saisie de la **certitude observateur** (#1139) : pose / effacement, unitaire et en lot atomique.
/// Base SQLite jetable (taxons fil rouge semés par V02). La certitude est **vide par défaut** et ne
/// change que par ce geste explicite : aucune action de revue ne la pose en douce.
class SaisieCertitudeTest {

    @TempDir
    Path dossier;

    private ObservationDao observationDao;
    private SaisieCertitude saisieCertitude;
    private long idSequence;
    private long idResultats;

    @BeforeEach
    void preparer() throws SQLException {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        try (Connection cx = source.getConnection()) {
            executer(cx, "INSERT INTO user(local_id, display_name) VALUES ('u-1', 'Testeur')");
            long idSite = cle(
                    cx,
                    "INSERT INTO monitoring_site(square_number, protocol, created_at, user_id)"
                            + " VALUES ('640380', 'Point fixe standard', '2026-05-01', 'u-1')");
            long idPoint = cle(cx, "INSERT INTO listening_point(code, site_id) VALUES ('A1', " + idSite + ")");
            executer(cx, "INSERT INTO recorder(serial_number) VALUES ('SN-1')");
            long idPassage = cle(
                    cx,
                    "INSERT INTO passage(passage_number, year, recording_date, start_time, end_time,"
                            + " workflow_status, point_id, recorder_id)"
                            + " VALUES (1, 2026, '2026-06-20', '21:00', '05:00', 'Importé', " + idPoint + ", 'SN-1')");
            long idSession =
                    cle(cx, "INSERT INTO recording_session(root_path, passage_id) VALUES ('/ws', " + idPassage + ")");
            long idOriginal = cle(
                    cx,
                    "INSERT INTO original_recording(file_name, file_path, session_id)"
                            + " VALUES ('a.wav', '/ws/bruts/a.wav', " + idSession + ")");
            idSequence = cle(
                    cx,
                    "INSERT INTO listening_sequence(file_name, original_recording_id, file_path, session_id)"
                            + " VALUES ('a_000.wav', " + idOriginal + ", '/ws/transformes/a_000.wav', " + idSession
                            + ")");
            idResultats = cle(
                    cx,
                    "INSERT INTO identification_results(file_path, detected_format, imported_at, passage_id)"
                            + " VALUES ('/ws/transformes/obs.csv', 'Vu', '2026-06-21', " + idPassage + ")");
        }
        observationDao = new ObservationDao(source);
        saisieCertitude = new SaisieCertitude(observationDao);
    }

    private long inserer(String taxonTadarida) {
        return observationDao
                .insert(new Observation(
                        null,
                        idSequence,
                        null,
                        null,
                        null,
                        taxonTadarida,
                        0.8,
                        null,
                        null,
                        null,
                        null,
                        false,
                        ModeValidation.NON_VALIDE,
                        idResultats,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null))
                .id();
    }

    private CertitudeObservateur certitude(long id) {
        return observationDao.findById(id).orElseThrow().certitudeObservateur();
    }

    @Test
    @DisplayName("vide par défaut ; poser écrit le jeton, reposer le remplace, null l'efface")
    void poser_remplacer_effacer() {
        long id = inserer("Pippip");
        assertThat(certitude(id))
                .as("jamais préremplie (miroir du site VigieChiro)")
                .isNull();

        saisieCertitude.poser(id, CertitudeObservateur.PROBABLE);
        assertThat(certitude(id)).isEqualTo(CertitudeObservateur.PROBABLE);

        saisieCertitude.poser(id, CertitudeObservateur.SUR);
        assertThat(certitude(id)).as("une certitude se remplace").isEqualTo(CertitudeObservateur.SUR);

        saisieCertitude.poser(id, null);
        assertThat(certitude(id)).as("l'effacement reste possible localement").isNull();
    }

    @Test
    @DisplayName("poser en lot traite exactement les ids visés, en une transaction")
    void poser_lot() {
        long a = inserer("Pippip");
        long b = inserer("Nyclei");
        long horsLot = inserer("noise");

        assertThat(saisieCertitude.poser(List.of(a, b), CertitudeObservateur.POSSIBLE))
                .isEqualTo(2);

        assertThat(certitude(a)).isEqualTo(CertitudeObservateur.POSSIBLE);
        assertThat(certitude(b)).isEqualTo(CertitudeObservateur.POSSIBLE);
        assertThat(certitude(horsLot)).as("hors du lot : intouchée").isNull();
    }

    @Test
    @DisplayName("la pose ne touche que la certitude : taxons, référence et douteux inchangés")
    void ne_touche_que_la_certitude() {
        long id = inserer("Pippip");

        saisieCertitude.poser(id, CertitudeObservateur.SUR);

        Observation relue = observationDao.findById(id).orElseThrow();
        assertThat(relue.taxonTadarida()).isEqualTo("Pippip");
        assertThat(relue.taxonObservateur()).isNull();
        assertThat(relue.reference()).isFalse();
        assertThat(relue.douteux()).isFalse();
        assertThat(relue.modeValidation()).isEqualTo(ModeValidation.NON_VALIDE);
    }

    @Test
    @DisplayName("observation introuvable : refus avant toute écriture")
    void observation_introuvable() {
        long existante = inserer("Pippip");

        assertThatThrownBy(() -> saisieCertitude.poser(List.of(existante, 999L), CertitudeObservateur.SUR))
                .isInstanceOf(RegleMetierException.class);
        assertThat(certitude(existante))
                .as("le lot est refusé avant la transaction : rien d'écrit")
                .isNull();
    }

    // --- utilitaires de semis SQL ---

    private static void executer(Connection cx, String sql) throws SQLException {
        try (Statement st = cx.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private static long cle(Connection cx, String sql) throws SQLException {
        try (PreparedStatement ps = cx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet cles = ps.getGeneratedKeys()) {
                cles.next();
                return cles.getLong(1);
            }
        }
    }
}
