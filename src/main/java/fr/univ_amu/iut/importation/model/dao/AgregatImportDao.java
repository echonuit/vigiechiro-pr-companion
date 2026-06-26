package fr.univ_amu.iut.importation.model.dao;

import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.importation.model.PassageExistant;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.Micro;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ReleveClimatique;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// Écrivain transactionnel de l'**agrégat d'import** : enregistreur, micro, passage, session,
/// journal, relevé climatique, originaux et séquences, écrits **tout ou rien** (O7).
///
/// **Pourquoi un DAO dédié à la feature `importation` ?** L'import crée d'un seul tenant un
/// agrégat qui s'étend sur des tables « possédées » par la feature `passage`. Or les DAO de
/// `passage` (`PassageDao`, `SessionDao`…) n'exposent que des écritures **auto-commit** (chacune
/// ouvre sa propre connexion) : les envelopper dans une `UniteDeTravail` ne donnerait **pas** une
/// vraie transaction (cf. SERVICE-CONVENTIONS §2.5, piège du « database is locked »). On applique
/// donc l'option 2 de la convention : un DAO propriétaire de l'agrégat expose des écritures **«
/// connection-aware »** qui réutilisent la connexion transactionnelle fournie par
/// `UniteDeTravail.executer(cx -> …)`. Le SQL reste dans `model.dao` (convention IMPL §3), et la
/// feature `passage` n'est pas modifiée.
///
/// Toutes les méthodes d'écriture prennent une [Connection] et déclarent `throws SQLException` :
/// elles sont conçues pour être appelées **à l'intérieur** du bloc `UniteDeTravail.executer`. La
/// seule lecture ([#passageExistePour]) ouvre sa propre connexion (pré-contrôle R5 avant d'engager
/// l'import) et n'entre donc pas dans la transaction.
public class AgregatImportDao {

    private final SourceDeDonnees source;

    public AgregatImportDao(SourceDeDonnees source) {
        this.source = java.util.Objects.requireNonNull(source, "source");
    }

    // ---------------------------------------------------------------------------
    // Lecture hors transaction : pré-contrôle d'unicité R5
    // ---------------------------------------------------------------------------

    /// `true` si un passage existe déjà pour ce quadruplet `(point, année, n° de passage)` (R5).
    /// Permet de refuser un réimport en doublon **avant** de copier/transformer quoi que ce soit.
    public boolean passageExistePour(Long idPoint, int annee, int numeroPassage) {
        String sql = "SELECT 1 FROM passage WHERE point_id = ? AND year = ? AND passage_number = ?";
        try (Connection cx = source.getConnection();
                PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setObject(1, idPoint);
            ps.setInt(2, annee);
            ps.setInt(3, numeroPassage);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Échec du pré-contrôle d'unicité R5 : " + sql, e);
        }
    }

    /// Plus petit n° de passage **libre** (≥ 1) pour ce point et cette année : le **premier entier non
    /// utilisé**, en comblant les trous (p. ex. avec les passages 1 et 3, propose 2 ; sans trou, équivaut
    /// à `MAX + 1`). Sert à **proposer** un n° non utilisé lorsque l'utilisateur tombe sur un doublon R5
    /// au rattachement (#108).
    public int prochainNumeroPassageLibre(Long idPoint, int annee) {
        String sql = "SELECT passage_number FROM passage WHERE point_id = ? AND year = ?";
        Set<Integer> utilises = new HashSet<>();
        try (Connection cx = source.getConnection();
                PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setObject(1, idPoint);
            ps.setInt(2, annee);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    utilises.add(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Échec du calcul du prochain n° de passage libre : " + sql, e);
        }
        int numero = 1;
        while (utilises.contains(numero)) {
            numero++;
        }
        return numero;
    }

    /// Passages **déjà en base** pour la même nuit (même enregistreur + même date d'enregistrement),
    /// quel que soit le point ou le n° de passage : sert à détecter à l'inspection qu'une nuit a déjà
    /// été importée (#147), même rattachée ailleurs que le quadruplet R5 visé. Liste vide si aucune.
    public List<PassageExistant> passagesDeLaNuit(String idEnregistreur, String dateNuit) {
        String sql = "SELECT p.passage_number, p.year, ms.square_number AS carre, lp.code AS point_code"
                + " FROM passage p"
                + " JOIN listening_point lp ON lp.id = p.point_id"
                + " JOIN monitoring_site ms ON ms.id = lp.site_id"
                + " WHERE p.recorder_id = ? AND p.recording_date = ?"
                + " ORDER BY p.year, p.passage_number";
        List<PassageExistant> resultats = new ArrayList<>();
        try (Connection cx = source.getConnection();
                PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setString(1, idEnregistreur);
            ps.setString(2, dateNuit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultats.add(new PassageExistant(
                            rs.getInt("passage_number"),
                            rs.getInt("year"),
                            rs.getString("carre"),
                            rs.getString("point_code")));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Échec de la détection de nuit déjà importée : " + sql, e);
        }
        return resultats;
    }

    /// Doublons de nuit à **reporter** (#214/#147) : passages déjà en base pour cette nuit (même série +
    /// date), **sauf** en mode écrasement (`ecraser` vrai), où l'opération est un remplacement et non un
    /// doublon. Centralise ici la décision (le DAO possède déjà les lectures de nuit) pour garder
    /// `ServiceImport` cohésif. Liste vide si `ecraser`, ou si série/date est nulle.
    public List<PassageExistant> doublonsDeNuitPourRapport(boolean ecraser, String numeroSerie, String dateNuit) {
        if (ecraser || numeroSerie == null || dateNuit == null) {
            return List.of();
        }
        return passagesDeLaNuit(numeroSerie, dateNuit);
    }

    /// Nombre de séquences d'écoute du passage existant à ce quadruplet `(point, année, n° de passage)` :
    /// affiché dans la confirmation d'écrasement (#214) pour rendre tangible ce qui sera supprimé. Zéro si
    /// le passage n'existe pas.
    public int compterSequencesDuPassage(Long idPoint, int annee, int numeroPassage) {
        String sql = "SELECT COUNT(*) FROM listening_sequence ls"
                + " JOIN recording_session rs ON rs.id = ls.session_id"
                + " JOIN passage p ON p.id = rs.passage_id"
                + " WHERE p.point_id = ? AND p.year = ? AND p.passage_number = ?";
        try (Connection cx = source.getConnection();
                PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setObject(1, idPoint);
            ps.setInt(2, annee);
            ps.setInt(3, numeroPassage);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Échec du comptage des séquences du passage : " + sql, e);
        }
    }

    // ---------------------------------------------------------------------------
    // Écritures « connection-aware » (à appeler dans UniteDeTravail.executer)
    // ---------------------------------------------------------------------------

    /// **Supprime** le passage existant à ce quadruplet `(point, année, n° de passage)` : opération
    /// **destructive** de l'écrasement (#214). Grâce à `ON DELETE CASCADE` (PRAGMA `foreign_keys = ON`),
    /// la suppression efface en cascade la session, les originaux, les séquences, le journal et le relevé.
    ///
    /// **Transactionnelle** : exécutée sur la connexion de l'unité de travail, dans la **même transaction**
    /// que l'insertion du nouveau passage (#214) : le remplacement est tout-ou-rien (jamais l'ancien perdu
    /// sans le nouveau, jamais un doublon transitoire).
    ///
    /// @return `true` si un passage a été supprimé, `false` si aucun n'existait à ce quadruplet
    public boolean supprimerPassageAuQuadruplet(Connection cx, Long idPoint, int annee, int numeroPassage)
            throws SQLException {
        try (PreparedStatement ps =
                cx.prepareStatement("DELETE FROM passage WHERE point_id = ? AND year = ? AND passage_number = ?")) {
            ps.setObject(1, idPoint);
            ps.setInt(2, annee);
            ps.setInt(3, numeroPassage);
            return ps.executeUpdate() > 0;
        }
    }

    /// Upsert de l'enregistreur sur sa clé naturelle `serial_number` (même patron que
    /// `EnregistreurDao`, mais sur la connexion transactionnelle). Rencontré deux fois, ses
    /// métadonnées sont rafraîchies plutôt que dupliquées.
    public void upsertEnregistreur(Connection cx, Enregistreur enregistreur) throws SQLException {
        try (PreparedStatement ps =
                cx.prepareStatement("INSERT INTO recorder (serial_number, model_version, comment) VALUES (?, ?, ?)"
                        + " ON CONFLICT(serial_number) DO UPDATE SET"
                        + " model_version = excluded.model_version, comment = excluded.comment")) {
            ps.setString(1, enregistreur.numeroSerie());
            ps.setString(2, enregistreur.versionModele());
            ps.setString(3, enregistreur.commentaire());
            ps.executeUpdate();
        }
    }

    /// Insère le micro porté par l'enregistreur **seulement s'il n'en a pas déjà un actif**
    /// (historisation : un seul `is_active = 1` par enregistreur). Idempotent au réimport : on ne
    /// crée pas un doublon de micro à chaque nuit.
    public void insererMicroSiAbsent(Connection cx, Micro micro) throws SQLException {
        if (aUnMicroActif(cx, micro.idEnregistreur())) {
            return;
        }
        try (PreparedStatement ps = cx.prepareStatement("INSERT INTO microphone"
                + " (model_ref, bandwidth, sensitivity, commissioned_at, decommissioned_at,"
                + " is_active, comment, recorder_id)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, micro.modeleReference());
            ps.setString(2, micro.bandePassante());
            ps.setString(3, micro.sensibilite());
            ps.setString(4, micro.miseEnServiceLe());
            ps.setString(5, micro.retireLe());
            ps.setInt(6, micro.actif() ? 1 : 0);
            ps.setString(7, micro.commentaire());
            ps.setString(8, micro.idEnregistreur());
            ps.executeUpdate();
        }
    }

    private boolean aUnMicroActif(Connection cx, String idEnregistreur) throws SQLException {
        try (PreparedStatement ps =
                cx.prepareStatement("SELECT 1 FROM microphone WHERE recorder_id = ? AND is_active = 1")) {
            ps.setString(1, idEnregistreur);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /// Insère le passage (FK `point_id`, `recorder_id`) et renvoie sa clé générée.
    public long insererPassage(Connection cx, Passage passage) throws SQLException {
        try (PreparedStatement ps = cx.prepareStatement(
                "INSERT INTO passage"
                        + " (passage_number, year, recording_date, start_time, end_time,"
                        + " acquisition_params, workflow_status, verification_verdict, comment,"
                        + " weather_data, deposited_at, point_id, recorder_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, passage.numeroPassage());
            ps.setInt(2, passage.annee());
            ps.setString(3, passage.dateEnregistrement());
            ps.setString(4, passage.heureDebut());
            ps.setString(5, passage.heureFin());
            ps.setString(6, passage.parametresAcquisition());
            ps.setString(7, passage.statutWorkflow().libelle());
            ps.setString(
                    8,
                    passage.verdictVerification() == null
                            ? null
                            : passage.verdictVerification().libelle());
            ps.setString(9, passage.commentaire());
            ps.setString(10, passage.donneesMeteo());
            ps.setString(11, passage.deposeLe());
            ps.setObject(12, passage.idPoint());
            ps.setString(13, passage.idEnregistreur());
            return executerEtRecupererCle(ps, "passage");
        }
    }

    /// Insère la session 1:1 du passage et renvoie sa clé générée.
    public long insererSession(Connection cx, long idPassage, SessionDEnregistrement session) throws SQLException {
        try (PreparedStatement ps = cx.prepareStatement(
                "INSERT INTO recording_session"
                        + " (root_path, originals_total_bytes, sequences_total_bytes, passage_id)"
                        + " VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, session.cheminRacine());
            ps.setObject(2, session.volumeOriginauxOctets());
            ps.setObject(3, session.volumeSequencesOctets());
            ps.setLong(4, idPassage);
            return executerEtRecupererCle(ps, "recording_session");
        }
    }

    /// Insère le journal du capteur 1:1 de la session (sensor_log).
    public void insererJournal(Connection cx, long idSession, JournalDuCapteur journal) throws SQLException {
        try (PreparedStatement ps =
                cx.prepareStatement("INSERT INTO sensor_log (file_path, parsed_events, detected_anomalies, session_id)"
                        + " VALUES (?, ?, ?, ?)")) {
            ps.setString(1, journal.cheminFichier());
            ps.setString(2, journal.evenementsParses());
            ps.setString(3, journal.anomaliesDetectees());
            ps.setLong(4, idSession);
            ps.executeUpdate();
        }
    }

    /// Insère le relevé climatique 0:1 de la session (climate_log), s'il existe (R20).
    public void insererReleve(Connection cx, long idSession, ReleveClimatique releve) throws SQLException {
        try (PreparedStatement ps =
                cx.prepareStatement("INSERT INTO climate_log (file_path, measurements, session_id) VALUES (?, ?, ?)")) {
            ps.setString(1, releve.cheminFichier());
            ps.setString(2, releve.mesures());
            ps.setLong(3, idSession);
            ps.executeUpdate();
        }
    }

    /// Insère un enregistrement original de la session et renvoie sa clé générée.
    public long insererOriginal(Connection cx, long idSession, EnregistrementOriginal original) throws SQLException {
        try (PreparedStatement ps = cx.prepareStatement(
                "INSERT INTO original_recording"
                        + " (file_name, file_path, duration_s, sample_rate_hz, sha256, session_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, original.nomFichier());
            ps.setString(2, original.cheminFichier());
            ps.setObject(3, original.dureeSecondes());
            ps.setObject(4, original.frequenceEchantillonnageHz());
            ps.setString(5, original.sha256());
            ps.setLong(6, idSession);
            return executerEtRecupererCle(ps, "original_recording");
        }
    }

    /// Insère une séquence d'écoute rattachée à sa session et à son original source (R8/R10).
    public void insererSequence(Connection cx, long idSession, long idOriginal, SequenceDEcoute sequence)
            throws SQLException {
        try (PreparedStatement ps = cx.prepareStatement("INSERT INTO listening_sequence"
                + " (file_name, original_recording_id, source_index, source_offset_s, duration_s,"
                + " file_path, in_selection, session_id)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, sequence.nomFichier());
            ps.setLong(2, idOriginal);
            ps.setObject(3, sequence.indexSource());
            ps.setObject(4, sequence.offsetSourceSecondes());
            ps.setObject(5, sequence.dureeSecondes());
            ps.setString(6, sequence.cheminFichier());
            ps.setInt(7, sequence.dansSelection() ? 1 : 0);
            ps.setLong(8, idSession);
            ps.executeUpdate();
        }
    }

    private static long executerEtRecupererCle(PreparedStatement ps, String table) throws SQLException {
        ps.executeUpdate();
        try (ResultSet cles = ps.getGeneratedKeys()) {
            if (cles.next()) {
                return cles.getLong(1);
            }
            throw new SQLException("Aucune clé générée pour l'insertion dans " + table + ".");
        }
    }
}
