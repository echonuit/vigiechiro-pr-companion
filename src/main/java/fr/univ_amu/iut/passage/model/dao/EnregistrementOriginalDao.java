package fr.univ_amu.iut.passage.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/// DAO de l'entité [EnregistrementOriginal] (table `original_recording`).
///
/// Colonnes numériques nullables : `duration_s` (`REAL`) est lue via `rs.getObject(...)`, et
/// `sample_rate_hz` (`INTEGER`) via [#lireIntNullable( ResultSet, String)] (le pilote SQLite
/// pouvant renvoyer `Integer` ou `Long` selon la magnitude). On distingue ainsi une valeur absente
/// d'un zéro. Rattaché à une session (`ON DELETE CASCADE`).
public class EnregistrementOriginalDao extends DaoGenerique<EnregistrementOriginal, Long> {

    private static final RowMapper<EnregistrementOriginal> MAPPER = rs -> new EnregistrementOriginal(
            rs.getLong("id"),
            rs.getString("file_name"),
            rs.getString("file_path"),
            (Double) rs.getObject("duration_s"),
            lireIntNullable(rs, "sample_rate_hz"),
            rs.getString("sha256"),
            rs.getLong("session_id"),
            lireLongNullable(rs, "size_bytes"));

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

    public EnregistrementOriginalDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "original_recording";
    }

    @Override
    protected String colonneCle() {
        return "id";
    }

    @Override
    protected RowMapper<EnregistrementOriginal> mapper() {
        return MAPPER;
    }

    /// Enregistrements originaux d'une session, triés par nom de fichier.
    public List<EnregistrementOriginal> findBySession(Long idSession) {
        return query("SELECT * FROM original_recording WHERE session_id = ? ORDER BY file_name", MAPPER, idSession);
    }

    @Override
    public EnregistrementOriginal insert(EnregistrementOriginal original) {
        try (Connection connexion = source.getConnection()) {
            return insert(connexion, original);
        } catch (SQLException e) {
            throw new DataAccessException("Échec de l'insertion d'un enregistrement original", e);
        }
    }

    /// Variante **transactionnelle** : insère sur la connexion fournie (sans l'ouvrir ni la commiter), pour
    /// grouper l'original **et ses milliers de séquences** dans une seule transaction (#1522). Propage la
    /// [SQLException] à l'[fr.univ_amu.iut.commun.persistence.UniteDeTravail].
    public EnregistrementOriginal insert(Connection connexion, EnregistrementOriginal original) throws SQLException {
        long id = insererEtRecupererCle(
                connexion,
                "INSERT INTO original_recording"
                        + " (file_name, file_path, duration_s, sample_rate_hz, sha256, session_id, size_bytes)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                original.nomFichier(),
                original.cheminFichier(),
                original.dureeSecondes(),
                original.frequenceEchantillonnageHz(),
                original.sha256(),
                original.idSession(),
                original.tailleOctets());
        return new EnregistrementOriginal(
                id,
                original.nomFichier(),
                original.cheminFichier(),
                original.dureeSecondes(),
                original.frequenceEchantillonnageHz(),
                original.sha256(),
                original.idSession(),
                original.tailleOctets());
    }

    @Override
    public void update(EnregistrementOriginal original) {
        executerMaj(
                "UPDATE original_recording SET"
                        + " file_name = ?, file_path = ?, duration_s = ?, sample_rate_hz = ?, sha256 = ?,"
                        + " session_id = ?, size_bytes = ?"
                        + " WHERE id = ?",
                original.nomFichier(),
                original.cheminFichier(),
                original.dureeSecondes(),
                original.frequenceEchantillonnageHz(),
                original.sha256(),
                original.idSession(),
                original.tailleOctets(),
                original.id());
    }

    /// Originaux **sans taille** (`size_bytes` NULL) : cible du rétro-remplissage (#1299), qui relit
    /// les fichiers encore présents (bruts non purgés) pour poser la taille rétroactivement.
    public List<EnregistrementOriginal> sansTaille() {
        return query("SELECT * FROM original_recording WHERE size_bytes IS NULL", MAPPER);
    }

    /// Renseigne la taille d'un original (rétro-remplissage ciblé, sans réécrire les autres colonnes).
    public void majTaille(long idOriginal, long tailleOctets) {
        executerMaj("UPDATE original_recording SET size_bytes = ? WHERE id = ?", tailleOctets, idOriginal);
    }
}
