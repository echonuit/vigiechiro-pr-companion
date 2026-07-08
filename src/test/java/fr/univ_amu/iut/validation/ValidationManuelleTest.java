package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
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

/// Validation **manuelle** d'une séquence non identifiée : crée une observation manuelle (sans taxon ni jeu
/// de résultats Tadarida, migration V13), en **remplace** le taxon si on re-valide (au plus une par
/// séquence), et refuse un taxon inconnu. La chaîne de FK (site → point → passage → session → original →
/// séquence) est semée directement en SQL, comme `ObservationDaoTest` (pas de DAO dédié ici).
class ValidationManuelleTest {

    @TempDir
    Path dossier;

    private ObservationDao observationDao;
    private ValidationManuelle validationManuelle;
    private long idSequence;

    @BeforeEach
    void preparer() throws SQLException {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
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
            long idSession =
                    insererCle(cx, "INSERT INTO recording_session(root_path, passage_id) VALUES ('/ws', ?)", idPassage);
            long idOriginal = insererCle(
                    cx,
                    "INSERT INTO original_recording(file_name, file_path, session_id)"
                            + " VALUES ('a.wav', '/ws/bruts/a.wav', ?)",
                    idSession);
            idSequence = insererCle(
                    cx,
                    "INSERT INTO listening_sequence(file_name, original_recording_id, file_path,"
                            + " session_id) VALUES ('a_000.wav', ?, '/ws/transformes/a_000.wav', ?)",
                    idOriginal,
                    idSession);
        }
        observationDao = new ObservationDao(source);
        validationManuelle = new ValidationManuelle(observationDao, new TaxonDao(source));
    }

    @Test
    @DisplayName("Valider une séquence non identifiée crée une observation SANS Tadarida (mode manuel)")
    void valider_cree_une_observation_manuelle() {
        Observation manuelle = validationManuelle.valider(idSequence, "Pippip");

        assertThat(manuelle.idSequence()).isEqualTo(idSequence);
        assertThat(manuelle.taxonObservateur()).isEqualTo("Pippip");
        assertThat(manuelle.taxonTadarida()).as("aucune proposition Tadarida").isNull();
        assertThat(manuelle.idResultats()).as("aucun jeu de résultats Tadarida").isNull();
        assertThat(manuelle.modeValidation()).isEqualTo(ModeValidation.MANUEL);
        assertThat(observationDao.observationManuelleDeLaSequence(idSequence)).isPresent();
    }

    @Test
    @DisplayName("Re-valider la même séquence REMPLACE le taxon (au plus une observation manuelle)")
    void valider_deux_fois_remplace_le_taxon() {
        validationManuelle.valider(idSequence, "Pippip");
        validationManuelle.valider(idSequence, "Nyclei");

        assertThat(observationDao.findBySequence(idSequence))
                .as("une seule observation manuelle pour la séquence")
                .singleElement()
                .extracting(Observation::taxonObservateur)
                .isEqualTo("Nyclei");
    }

    @Test
    @DisplayName("Valider vers un taxon inconnu est refusé (dur)")
    void valider_taxon_inconnu_refuse() {
        assertThatThrownBy(() -> validationManuelle.valider(idSequence, "ZZZZZZ"))
                .isInstanceOf(RegleMetierException.class);
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
