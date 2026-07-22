package fr.univ_amu.iut.passage.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import java.sql.Connection;
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

    private static final String SQL_MAJ_ORIGINAL =
            "UPDATE listening_sequence SET original_recording_id = ? WHERE id = ?";

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
            lireHorodatage(rs, "recorded_at"),
            lireLongNullable(rs, "size_bytes"),
            rs.getString("content_fingerprint"));

    /// Lit une colonne `INTEGER` nullable en [Integer], en préservant le `null`.
    private static Integer lireIntNullable(ResultSet rs, String colonne) throws SQLException {
        Object valeur = rs.getObject(colonne);
        return valeur == null ? null : ((Number) valeur).intValue();
    }

    /// Lit une colonne `INTEGER` nullable en [Long], en préservant le `null`.
    private static Long lireLongNullable(ResultSet rs, String colonne) throws SQLException {
        Object valeur = rs.getObject(colonne);
        return valeur == null ? null : ((Number) valeur).longValue();
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
        try (Connection connexion = source.getConnection()) {
            return insert(connexion, sequence);
        } catch (SQLException e) {
            throw new DataAccessException("Échec de l'insertion d'une séquence", e);
        }
    }

    /// Variante **transactionnelle** : insère sur la connexion fournie (sans l'ouvrir ni la commiter), pour
    /// grouper des **milliers de séquences en une seule transaction** au lieu d'un commit (et un fsync) par
    /// ligne - la reconstruction d'une nuit en crée des milliers (#1522). Propage la [SQLException] à
    /// l'[fr.univ_amu.iut.commun.persistence.UniteDeTravail] qui décide du commit / rollback.
    public SequenceDEcoute insert(Connection connexion, SequenceDEcoute sequence) throws SQLException {
        long id = insererEtRecupererCle(
                connexion,
                "INSERT INTO listening_sequence"
                        + " (file_name, original_recording_id, source_index, source_offset_s, duration_s,"
                        + " file_path, in_selection, session_id, recorded_at, size_bytes, content_fingerprint)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                sequence.nomFichier(),
                sequence.idEnregistrementOriginal(),
                sequence.indexSource(),
                sequence.offsetSourceSecondes(),
                sequence.dureeSecondes(),
                sequence.cheminFichier(),
                sequence.dansSelection() ? 1 : 0,
                sequence.idSession(),
                texteHorodatage(sequence.horodatageCapture()),
                sequence.tailleOctets(),
                sequence.empreinte());
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
                sequence.horodatageCapture(),
                sequence.tailleOctets(),
                sequence.empreinte());
    }

    @Override
    public void update(SequenceDEcoute sequence) {
        executerMaj(
                "UPDATE listening_sequence SET"
                        + " file_name = ?, original_recording_id = ?, source_index = ?, source_offset_s = ?,"
                        + " duration_s = ?, file_path = ?, in_selection = ?, session_id = ?, recorded_at = ?,"
                        + " size_bytes = ?, content_fingerprint = ?"
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
                sequence.tailleOctets(),
                sequence.empreinte(),
                sequence.id());
    }

    /// Séquences **sans horodatage de capture** (`recorded_at` NULL) : cible du backfill applicatif (#530),
    /// qui re-parse leur nom de fichier pour renseigner l'heure rétroactivement.
    public List<SequenceDEcoute> sansHorodatage() {
        return query("SELECT * FROM listening_sequence WHERE recorded_at IS NULL", MAPPER);
    }

    /// Renseigne l'horodatage de capture d'une séquence (backfill ciblé, sans réécrire les autres colonnes).
    /// Fait pointer une séquence sur un **autre fichier** (#2255) : c'est le geste de la réactivation
    /// « par référence », où rien n'est copié et où la base suit l'audio là où l'utilisateur le garde.
    /// Le fichier a été **vérifié** avant d'arriver ici (cascade #1309) : on ne réécrit jamais un chemin
    /// sur la foi d'un nom.
    public void majChemin(long idSequence, java.nio.file.Path chemin) {
        executerMaj(
                "UPDATE listening_sequence SET file_path = ? WHERE id = ?",
                chemin.toAbsolutePath().toString(),
                idSequence);
    }

    public void majHorodatage(long idSequence, LocalDateTime horodatage) {
        executerMaj(
                "UPDATE listening_sequence SET recorded_at = ? WHERE id = ?", texteHorodatage(horodatage), idSequence);
    }

    /// Variante transactionnelle de [#majHorodatage(long, java.time.LocalDateTime)] : écrit sur la
    /// **connexion fournie**, pour grouper le rétro-remplissage d'un démarrage dans une seule
    /// [fr.univ_amu.iut.commun.persistence.UniteDeTravail] plutôt que d'auto-commiter chaque ligne.
    public void majHorodatage(Connection connexion, long idSequence, LocalDateTime horodatage) throws SQLException {
        executerMaj(
                connexion,
                "UPDATE listening_sequence SET recorded_at = ? WHERE id = ?",
                texteHorodatage(horodatage),
                idSequence);
    }

    /// Séquences **sans empreinte** (`content_fingerprint` NULL) : cible du rétro-remplissage (#1299),
    /// qui relit les fichiers encore présents sur disque pour poser taille et empreinte rétroactivement.
    public List<SequenceDEcoute> sansEmpreinte() {
        return query("SELECT * FROM listening_sequence WHERE content_fingerprint IS NULL", MAPPER);
    }

    /// Séquences **sans empreinte** d'une session (variante ciblée de [#sansEmpreinte()], pour capturer
    /// l'identité d'un passage juste avant son archivage, #1300).
    public List<SequenceDEcoute> sansEmpreinteDeSession(Long idSession) {
        return query(
                "SELECT * FROM listening_sequence WHERE content_fingerprint IS NULL AND session_id = ?",
                MAPPER,
                idSession);
    }

    /// Renseigne taille et empreinte d'une séquence (rétro-remplissage ciblé, sans réécrire les autres
    /// colonnes).
    public void majEmpreinte(long idSequence, long tailleOctets, String empreinte) {
        executerMaj(
                "UPDATE listening_sequence SET size_bytes = ?, content_fingerprint = ? WHERE id = ?",
                tailleOctets,
                empreinte,
                idSequence);
    }

    /// Réattache une séquence à un autre enregistrement original (#1651) : l'hydratation d'un passage
    /// reconstruit remplace le placeholder par les vrais originaux régénérés, et rebranche chaque séquence
    /// sur le sien. Maj ciblée, sans réécrire les autres colonnes.
    public void majOriginal(long idSequence, long idEnregistrementOriginal) {
        executerMaj(SQL_MAJ_ORIGINAL, idEnregistrementOriginal, idSequence);
    }

    /// Variante transactionnelle de [#majOriginal(long, long)] : écrit sur la **connexion fournie**, pour
    /// grouper des milliers de rattachements dans une seule [fr.univ_amu.iut.commun.persistence.UniteDeTravail]
    /// plutôt que d'auto-commiter chacun.
    public void majOriginal(Connection connexion, long idSequence, long idEnregistrementOriginal) throws SQLException {
        executerMaj(connexion, SQL_MAJ_ORIGINAL, idEnregistrementOriginal, idSequence);
    }
}
