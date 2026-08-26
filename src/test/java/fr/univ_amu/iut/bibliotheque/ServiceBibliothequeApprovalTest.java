package fr.univ_amu.iut.bibliotheque;

import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
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
import org.approvaltests.Approvals;
import org.approvaltests.reporters.QuietReporter;
import org.approvaltests.reporters.UseReporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Test *golden* du CSV récapitulatif (ApprovalTests). Le format exporté étant
/// **déterministe** (colonnes et lignes triées, ni horodatage ni hash, cf. SERVICE-CONVENTIONS
/// §5), on fige le rendu attendu dans un fichier `*.approved.txt` : toute dérive du format
/// casse le test.
///
/// [QuietReporter] : aucun outil graphique n'est lancé en cas d'écart (CI headless).
class ServiceBibliothequeApprovalTest {

    @TempDir
    Path dossier;

    private SequenceDao sequenceDao;
    private ObservationDao observationDao;
    private ServiceBibliotheque service;
    private long idResultats;

    @BeforeEach
    void preparer() throws SQLException {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();

        long idSession;
        long idOriginal;
        try (Connection cx = source.getConnection()) {
            // Utilisateur, site, point et enregistreur viennent de la fixture, avec le passage.
            // Le SQL d'origine écrivait le protocole « Point fixe standard », un libellé que
            // `Protocole` ne connaît plus (il stocke « PointFixeStandard ») : la ligne était illisible par
            // `SiteDao`, et personne ne s'en apercevait parce que ce test ne relisait jamais le site.
            long idPassage = JeuDeDonneesPassage.dans(source)
                    .utilisateur("u-1")
                    .carre("640380")
                    .point("A1")
                    .enregistreur("SN-1")
                    .nuit(1, 2026, "2026-06-20")
                    .heures("21:00", "05:00")
                    .statut(StatutWorkflow.IMPORTE)
                    .semerPassage()
                    .idPassage();
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
        long idAlpha = sequenceDao
                .insert(new SequenceDEcoute(
                        null, "a_000.wav", idOriginal, 0, 0.0, 5.0, "/ws/transformes/a_000.wav", false, idSession))
                .id();
        long idBeta = sequenceDao
                .insert(new SequenceDEcoute(
                        null, "b_000.wav", idOriginal, 1, 0.0, 5.0, "/ws/transformes/b_000.wav", false, idSession))
                .id();
        long idGamma = sequenceDao
                .insert(new SequenceDEcoute(
                        null, "c_000.wav", idOriginal, 2, 0.0, 5.0, "/ws/transformes/c_000.wav", false, idSession))
                .id();

        observationDao.insert(reference(idBeta, "Nyclei", null, 22000, "noctule claire"));
        observationDao.insert(reference(idAlpha, "Pippip", "Pippip", 45, "signal net"));
        observationDao.insert(reference(idGamma, "Pippip", "Rhihip", 30000, null));
        observationDao.insert(nonReference(idAlpha, "Tadten"));

        service = new ServiceBibliotheque(observationDao, sequenceDao);
    }

    @Test
    @DisplayName("Le CSV récapitulatif exporté correspond au golden figé")
    @UseReporter(QuietReporter.class)
    void exporte_le_csv_recapitulatif_golden() {
        Approvals.verify(service.exporterBibliotheque().versCsv());
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
                idResultats,
                false,
                null,
                null,
                null,
                null,
                null);
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
                idResultats,
                false,
                null,
                null,
                null,
                null,
                null);
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
