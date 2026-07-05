package fr.univ_amu.iut.passage.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/// DAO de l'entité [SequenceDEcoute] (table `listening_sequence`).
///
/// Doublement dépendante par FK (`session_id` et `original_recording_id`, toutes deux en
/// `ON DELETE CASCADE`). Le booléen `in_selection` est mappé en `0`/`1` et relu via
/// `rs.getInt(...) != 0` ; les champs numériques optionnels `source_offset_s` et `duration_s`
/// (`REAL`) via `rs.getObject(...)`, et `source_index` (`INTEGER`) via
/// [#lireIntNullable(ResultSet, String)] pour préserver le `null`.
public class SequenceDao extends DaoGenerique<SequenceDEcoute, Long> {

    private static final RowMapper<SequenceDEcoute> MAPPER = rs -> new SequenceDEcoute(
            rs.getLong("id"),
            rs.getString("file_name"),
            rs.getLong("original_recording_id"),
            lireIntNullable(rs, "source_index"),
            (Double) rs.getObject("source_offset_s"),
            (Double) rs.getObject("duration_s"),
            rs.getString("file_path"),
            rs.getInt("in_selection") != 0,
            rs.getLong("session_id"),
            lireHorodatage(rs, "recorded_at"));

    /// Lit une colonne `INTEGER` nullable en [Integer], en préservant le `null`.
    private static Integer lireIntNullable(ResultSet rs, String colonne) throws SQLException {
        Object valeur = rs.getObject(colonne);
        return valeur == null ? null : ((Number) valeur).intValue();
    }

    /// Lit une colonne `TEXT` ISO-8601 nullable en [LocalDateTime] (image de `recorded_at`), `null` si absente.
    private static LocalDateTime lireHorodatage(ResultSet rs, String colonne) throws SQLException {
        String valeur = rs.getString(colonne);
        return valeur == null ? null : LocalDateTime.parse(valeur);
    }

    /// Représentation TEXT ISO-8601 d'un horodatage pour la persistance (`null` conservé).
    private static String texteHorodatage(LocalDateTime horodatage) {
        return horodatage == null ? null : horodatage.toString();
    }

    public SequenceDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "listening_sequence";
    }

    @Override
    protected String colonneCle() {
        return "id";
    }

    @Override
    protected RowMapper<SequenceDEcoute> mapper() {
        return MAPPER;
    }

    /// Séquences d'une session, triées par nom de fichier.
    public List<SequenceDEcoute> findBySession(Long idSession) {
        return query("SELECT * FROM listening_sequence WHERE session_id = ? ORDER BY file_name", MAPPER, idSession);
    }

    /// Séquences issues d'un même enregistrement original, triées par index dans le source.
    public List<SequenceDEcoute> findByOriginal(Long idEnregistrementOriginal) {
        return query(
                "SELECT * FROM listening_sequence WHERE original_recording_id = ? ORDER BY source_index",
                MAPPER,
                idEnregistrementOriginal);
    }

    @Override
    public SequenceDEcoute insert(SequenceDEcoute sequence) {
        long id = insererEtRecupererCle(
                "INSERT INTO listening_sequence"
                        + " (file_name, original_recording_id, source_index, source_offset_s, duration_s,"
                        + " file_path, in_selection, session_id, recorded_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                sequence.nomFichier(),
                sequence.idEnregistrementOriginal(),
                sequence.indexSource(),
                sequence.offsetSourceSecondes(),
                sequence.dureeSecondes(),
                sequence.cheminFichier(),
                sequence.dansSelection() ? 1 : 0,
                sequence.idSession(),
                texteHorodatage(sequence.horodatageCapture()));
        return new SequenceDEcoute(
                id,
                sequence.nomFichier(),
                sequence.idEnregistrementOriginal(),
                sequence.indexSource(),
                sequence.offsetSourceSecondes(),
                sequence.dureeSecondes(),
                sequence.cheminFichier(),
                sequence.dansSelection(),
                sequence.idSession(),
                sequence.horodatageCapture());
    }

    @Override
    public void update(SequenceDEcoute sequence) {
        executerMaj(
                "UPDATE listening_sequence SET"
                        + " file_name = ?, original_recording_id = ?, source_index = ?, source_offset_s = ?,"
                        + " duration_s = ?, file_path = ?, in_selection = ?, session_id = ?, recorded_at = ?"
                        + " WHERE id = ?",
                sequence.nomFichier(),
                sequence.idEnregistrementOriginal(),
                sequence.indexSource(),
                sequence.offsetSourceSecondes(),
                sequence.dureeSecondes(),
                sequence.cheminFichier(),
                sequence.dansSelection() ? 1 : 0,
                sequence.idSession(),
                texteHorodatage(sequence.horodatageCapture()),
                sequence.id());
    }

    /// Séquences **sans horodatage de capture** (`recorded_at` NULL) : cible du backfill applicatif (#530),
    /// qui re-parse leur nom de fichier pour renseigner l'heure rétroactivement.
    public List<SequenceDEcoute> sansHorodatage() {
        return query("SELECT * FROM listening_sequence WHERE recorded_at IS NULL", MAPPER);
    }

    /// Renseigne l'horodatage de capture d'une séquence (backfill ciblé, sans réécrire les autres colonnes).
    public void majHorodatage(long idSequence, LocalDateTime horodatage) {
        executerMaj(
                "UPDATE listening_sequence SET recorded_at = ? WHERE id = ?", texteHorodatage(horodatage), idSequence);
    }
}
