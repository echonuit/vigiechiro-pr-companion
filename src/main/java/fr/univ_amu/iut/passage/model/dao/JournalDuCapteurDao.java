package fr.univ_amu.iut.passage.model.dao;

import fr.univ_amu.iut.commun.model.Completude;
import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import java.util.Optional;
import java.util.logging.Logger;

/// DAO de l'entité [JournalDuCapteur] (table `sensor_log`).
///
/// Relation 1:1 avec la session (`session_id` unique), d'où [#trouverParSession(Long)]. Les
/// colonnes JSON `parsed_events` / `detected_anomalies` sont transportées comme du `TEXT` brut.
public class JournalDuCapteurDao extends DaoGenerique<JournalDuCapteur, Long> {

    private static final Logger LOG = Logger.getLogger(JournalDuCapteurDao.class.getName());

    private static final RowMapper<JournalDuCapteur> MAPPER = rs -> new JournalDuCapteur(
            rs.getLong("id"),
            rs.getString("file_path"),
            rs.getString("parsed_events"),
            rs.getString("detected_anomalies"),
            completude(rs.getString("night_completeness")),
            rs.getLong("session_id"));

    /// La complétude relue, ou [Completude#INCONNUE] si la colonne est vide ou porte une valeur que
    /// cette version ne connaît pas.
    ///
    /// Une valeur inconnue **ne se rabat pas sur une valeur plausible** : elle se lit « je ne sais
    /// pas », ce qui est exactement vrai. Rabattre sur `COMPLETE` referait au report le défaut que
    /// #4990 a corrigé au calcul ; rabattre sur `TRONQUEE` accuserait une nuit sur une lecture ratée.
    private static Completude completude(String valeur) {
        if (valeur == null) {
            return Completude.INCONNUE;
        }
        try {
            return Completude.valueOf(valeur);
        } catch (IllegalArgumentException inconnue) {
            LOG.warning(() -> "Complétude illisible en base, lue « inconnue » : " + valeur);
            return Completude.INCONNUE;
        }
    }

    public JournalDuCapteurDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "sensor_log";
    }

    @Override
    protected String colonneCle() {
        return "id";
    }

    @Override
    protected RowMapper<JournalDuCapteur> mapper() {
        return MAPPER;
    }

    /// Le journal du capteur de la session donnée (relation 1:1, `session_id` unique).
    public Optional<JournalDuCapteur> trouverParSession(Long idSession) {
        return queryUnique("SELECT * FROM sensor_log WHERE session_id = ?", MAPPER, idSession);
    }

    @Override
    public JournalDuCapteur insert(JournalDuCapteur journal) {
        long id = insererEtRecupererCle(
                "INSERT INTO sensor_log"
                        + " (file_path, parsed_events, detected_anomalies, night_completeness, session_id)"
                        + " VALUES (?, ?, ?, ?, ?)",
                journal.cheminFichier(),
                journal.evenementsParses(),
                journal.anomaliesDetectees(),
                journal.completude().name(),
                journal.idSession());
        return new JournalDuCapteur(
                id,
                journal.cheminFichier(),
                journal.evenementsParses(),
                journal.anomaliesDetectees(),
                journal.completude(),
                journal.idSession());
    }

    @Override
    public void update(JournalDuCapteur journal) {
        executerMaj(
                "UPDATE sensor_log SET"
                        + " file_path = ?, parsed_events = ?, detected_anomalies = ?,"
                        + " night_completeness = ?, session_id = ?"
                        + " WHERE id = ?",
                journal.cheminFichier(),
                journal.evenementsParses(),
                journal.anomaliesDetectees(),
                journal.completude().name(),
                journal.idSession(),
                journal.id());
    }
}
