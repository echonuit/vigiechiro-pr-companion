package fr.univ_amu.iut.passage.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/// DAO de l'entité [SessionDEnregistrement] (table `recording_session`).
///
/// Relation 1:1 avec le passage : `passage_id` est unique, d'où la requête métier
/// [#trouverParPassage(Long)] qui renvoie un [Optional]. Les volumes (champs dérivés, nullables)
/// sont lus via [#lireLongNullable(ResultSet, String)] : le pilote SQLite renvoie un `Integer`
/// pour une petite valeur mais un `Long` pour une grande (un volume peut atteindre ~40 Go), on
/// convertit donc systématiquement via [Number] plutôt que par un cast `(Long)` fragile.
public class SessionDao extends DaoGenerique<SessionDEnregistrement, Long> {

    private static final RowMapper<SessionDEnregistrement> MAPPER = rs -> new SessionDEnregistrement(
            rs.getLong("id"),
            rs.getString("root_path"),
            lireLongNullable(rs, "originals_total_bytes"),
            lireLongNullable(rs, "sequences_total_bytes"),
            rs.getLong("passage_id"),
            lireHorodatage(rs, "archived_at"),
            lireHorodatage(rs, "originals_purged_at"));

    /// Lit une colonne `INTEGER` nullable en [Long], en préservant le `null`.
    private static Long lireLongNullable(ResultSet rs, String colonne) throws SQLException {
        Object valeur = rs.getObject(colonne);
        return valeur == null ? null : ((Number) valeur).longValue();
    }

    /// Lit une colonne `TEXT` ISO-8601 nullable en [LocalDateTime] (image d'`archived_at`), `null` si
    /// absente.
    private static LocalDateTime lireHorodatage(ResultSet rs, String colonne) throws SQLException {
        String valeur = rs.getString(colonne);
        return valeur == null ? null : LocalDateTime.parse(valeur);
    }

    public SessionDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "recording_session";
    }

    @Override
    protected String colonneCle() {
        return "id";
    }

    @Override
    protected RowMapper<SessionDEnregistrement> mapper() {
        return MAPPER;
    }

    /// La session du passage donné (relation 1:1, `passage_id` unique).
    public Optional<SessionDEnregistrement> trouverParPassage(Long idPassage) {
        return queryUnique("SELECT * FROM recording_session WHERE passage_id = ?", MAPPER, idPassage);
    }

    /// Déclare la purge des `bruts/` d'une session (#1303) : met le volume des originaux à **zéro**
    /// (la fiche du passage reflète que les originaux ne sont plus conservés) et pose le marqueur
    /// **explicite** `originals_purged_at` : c'est le fait déclaré qui fait taire le contrôle
    /// d'existence des originaux dans l'audit, plus une heuristique de volume.
    public void marquerOriginauxPurges(Long idSession, LocalDateTime horodatage) {
        executerMaj(
                "UPDATE recording_session SET originals_total_bytes = 0, originals_purged_at = ? WHERE id = ?",
                horodatage == null ? null : horodatage.toString(),
                idSession);
    }

    /// Met à **zéro** le volume des séquences (`sequences_total_bytes`) d'une session, après purge de
    /// son audio par l'archivage (#1300) : la fiche du passage reflète alors que les séquences ne
    /// sont plus conservées (miroir de [#marquerOriginauxPurges]).
    public void marquerSequencesPurgees(Long idSession) {
        executerMaj("UPDATE recording_session SET sequences_total_bytes = 0 WHERE id = ?", idSession);
    }

    /// Réécrit le volume des séquences (`sequences_total_bytes`) d'une session, après une
    /// **réactivation** (#1302) : la fiche du passage reflète l'audio revenu sur disque (l'archivage
    /// l'avait mis à zéro).
    public void majVolumeSequences(Long idSession, long octets) {
        executerMaj("UPDATE recording_session SET sequences_total_bytes = ? WHERE id = ?", octets, idSession);
    }

    /// Pose le marqueur **explicite** d'archivage (#1300) : enregistre le geste volontaire, ce qui
    /// distingue un passage archivé d'un passage corrompu aux yeux de l'audit (#1303, #1348). Un
    /// horodatage `null` **efface** le marqueur : c'est ce que fait une réactivation complète
    /// (#1302), le passage n'étant alors plus archivé.
    public void marquerArchivee(Long idSession, LocalDateTime horodatage) {
        executerMaj(
                "UPDATE recording_session SET archived_at = ? WHERE id = ?",
                horodatage == null ? null : horodatage.toString(),
                idSession);
    }

    @Override
    public SessionDEnregistrement insert(SessionDEnregistrement session) {
        long id = insererEtRecupererCle(
                "INSERT INTO recording_session"
                        + " (root_path, originals_total_bytes, sequences_total_bytes, passage_id, archived_at,"
                        + " originals_purged_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                session.cheminRacine(),
                session.volumeOriginauxOctets(),
                session.volumeSequencesOctets(),
                session.idPassage(),
                session.horodatageArchivage() == null
                        ? null
                        : session.horodatageArchivage().toString(),
                session.horodatagePurgeOriginaux() == null
                        ? null
                        : session.horodatagePurgeOriginaux().toString());
        return new SessionDEnregistrement(
                id,
                session.cheminRacine(),
                session.volumeOriginauxOctets(),
                session.volumeSequencesOctets(),
                session.idPassage(),
                session.horodatageArchivage(),
                session.horodatagePurgeOriginaux());
    }

    @Override
    public void update(SessionDEnregistrement session) {
        executerMaj(
                "UPDATE recording_session SET"
                        + " root_path = ?, originals_total_bytes = ?, sequences_total_bytes = ?, passage_id = ?,"
                        + " archived_at = ?, originals_purged_at = ?"
                        + " WHERE id = ?",
                session.cheminRacine(),
                session.volumeOriginauxOctets(),
                session.volumeSequencesOctets(),
                session.idPassage(),
                session.horodatageArchivage() == null
                        ? null
                        : session.horodatageArchivage().toString(),
                session.horodatagePurgeOriginaux() == null
                        ? null
                        : session.horodatagePurgeOriginaux().toString(),
                session.id());
    }
}
