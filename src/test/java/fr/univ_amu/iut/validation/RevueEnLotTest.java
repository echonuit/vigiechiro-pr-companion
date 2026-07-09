package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
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

/// Actions de revue **en lot** (#479) : valider / corriger / marquer référence sur une liste d'ids, en une
/// transaction. Base SQLite jetable (taxons fil rouge semés par V02).
class RevueEnLotTest {

    @TempDir
    Path dossier;

    private ObservationDao observationDao;
    private RevueEnLot revueEnLot;
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
        revueEnLot = new RevueEnLot(observationDao, new TaxonDao(source));
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
                        false))
                .id();
    }

    /// Statut dérivé (comme la projection) : non touché si pas de taxon observateur ; validé si égal au
    /// taxon Tadarida ; corrigé sinon.
    private StatutObservation statut(long id) {
        Observation o = observationDao.findById(id).orElseThrow();
        if (o.taxonObservateur() == null) {
            return StatutObservation.NON_TOUCHEE;
        }
        return o.taxonObservateur().equals(o.taxonTadarida()) ? StatutObservation.VALIDEE : StatutObservation.CORRIGEE;
    }

    @Test
    @DisplayName("validerLot valide exactement les ids visés (mode Activité, sans propagation) et compte")
    void valider_lot_traite_les_ids_vises() {
        long pippip = inserer("Pippip");
        long nyclei = inserer("Nyclei");
        long noise = inserer("noise");

        int traites = revueEnLot.valider(List.of(pippip, nyclei));

        assertThat(traites).isEqualTo(2);
        assertThat(statut(pippip)).isEqualTo(StatutObservation.VALIDEE);
        assertThat(statut(nyclei)).isEqualTo(StatutObservation.VALIDEE);
        // Le 3e n'est pas dans le lot : aucune propagation ne l'a touché.
        assertThat(statut(noise)).isEqualTo(StatutObservation.NON_TOUCHEE);
        assertThat(observationDao.findById(pippip).orElseThrow().taxonObservateur())
                .isEqualTo("Pippip");
    }

    @Test
    @DisplayName("marquerReferenceLot marque puis retire un lot")
    void marquer_reference_lot() {
        long a = inserer("Pippip");
        long b = inserer("Nyclei");

        assertThat(revueEnLot.marquerReference(List.of(a, b), true)).isEqualTo(2);
        assertThat(observationDao.findById(a).orElseThrow().reference()).isTrue();
        assertThat(observationDao.findById(b).orElseThrow().reference()).isTrue();

        revueEnLot.marquerReference(List.of(a), false);
        assertThat(observationDao.findById(a).orElseThrow().reference()).isFalse();
    }

    @Test
    @DisplayName("corrigerLot retient un taxon sur tout le lot ; un taxon inconnu est refusé sans rien écrire")
    void corriger_lot_et_taxon_inconnu() {
        long a = inserer("noise");
        long b = inserer("noise");

        assertThat(revueEnLot.corriger(List.of(a, b), "Pippip")).isEqualTo(2);
        assertThat(observationDao.findById(a).orElseThrow().taxonObservateur()).isEqualTo("Pippip");
        assertThat(statut(b)).isEqualTo(StatutObservation.CORRIGEE);

        long c = inserer("noise");
        assertThatThrownBy(() -> revueEnLot.corriger(List.of(c), "ZZZZZZ")).isInstanceOf(RegleMetierException.class);
        assertThat(statut(c)).as("taxon inconnu refusé avant toute écriture").isEqualTo(StatutObservation.NON_TOUCHEE);
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
