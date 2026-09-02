package fr.univ_amu.iut.diagnostic.model;

import fr.univ_amu.iut.commun.model.Completude;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.MeteoPassage;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/// Service métier de la feature `diagnostic` (parcours P6, épopée E6) : lit l'état
/// matériel/technique d'une nuit **déjà importée** et l'expose pour l'onglet « Diagnostic » de la
/// fiche passage.
///
/// **Lecture seule, sans re-parsing lourd** : le service ne relit ni les originaux ni le journal
/// `LogPR` brut ; il exploite ce qui a été persisté à l'import :
///
/// - les colonnes JSON `sensor_log.parsed_events` / `detected_anomalies` via
///   [AnalyseAnomalies] (R19) ;
/// - la série climatique relue du fichier `THLog` via [LectureThLog] (R20) ;
/// - les coordonnées GPS du point d'écoute via le [PointDao] de la feature `sites`.
///
/// **Dépendances inter-features**, toutes en lecture seule : `diagnostic → passage.model.dao`
/// (passage, session, journal, relevé) et `diagnostic → sites.model.dao` (point/GPS).
public class ServiceDiagnostic {

    private final PassageDao passageDao;
    private final SessionDao sessionDao;
    private final JournalDuCapteurDao journalDao;
    private final ReleveClimatiqueDao releveDao;
    private final PointDao pointDao;
    private final Horloge horloge;

    public ServiceDiagnostic(
            PassageDao passageDao,
            SessionDao sessionDao,
            JournalDuCapteurDao journalDao,
            ReleveClimatiqueDao releveDao,
            PointDao pointDao,
            Horloge horloge) {
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.journalDao = Objects.requireNonNull(journalDao, "journalDao");
        this.releveDao = Objects.requireNonNull(releveDao, "releveDao");
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    /// Construit le diagnostic d'un passage importé.
    ///
    /// @param idPassage identifiant du passage à diagnostiquer
    /// @return l'état consolidé (anomalies R19, série climatique R20, GPS, horodatage)
    /// @throws RegleMetierException si le passage ou sa session d'enregistrement est introuvable
    public Diagnostic diagnostiquer(Long idPassage) {
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException("Passage introuvable : " + idPassage));
        SessionDEnregistrement session = sessionDao
                .trouverParPassage(idPassage)
                .orElseThrow(() -> new RegleMetierException(
                        "Session d'enregistrement introuvable pour le passage " + idPassage + "."));
        Long idSession = session.id();

        // R19 : anomalies/évènements du journal (1:1 session) ; analyse vide si le journal manque.
        // Le journal est relu UNE fois : il porte les anomalies et, depuis #5084, la complétude de sa
        // nuit. Cette ligne la jetait, faute qu'elle existât quand elle a été écrite (#5093).
        Optional<JournalDuCapteur> journal = journalDao.trouverParSession(idSession);
        AnalyseAnomalies anomalies =
                journal.map(AnalyseAnomalies::depuisJournal).orElseGet(AnalyseAnomalies::vide);
        // Pas de journal, pas d'information : INCONNUE, et surtout pas COMPLETE. C'est la règle que
        // #5071 a établie au calcul et #5084 au report ; elle tient ici une troisième fois.
        Completude completude = journal.map(JournalDuCapteur::completude).orElse(Completude.INCONNUE);

        // R20 : relevé climatique optionnel ; absence explicitement signalée, sinon série relue du
        // THLog.
        SerieClimatique climat = releveDao
                .trouverParSession(idSession)
                .map(releve -> SerieClimatique.presente(LectureThLog.lire(chemin(releve.cheminFichier()))))
                .orElseGet(SerieClimatique::absente);

        // GPS depuis le point d'écoute (feature sites) ; nullable si point introuvable ou non
        // géolocalisé.
        Double latitude = null;
        Double longitude = null;
        PointDEcoute point = pointDao.findById(passage.idPoint()).orElse(null);
        if (point != null) {
            latitude = point.latitude();
            longitude = point.longitude();
        }

        // Cohérence horaires (#548) : la fenêtre nocturne réelle au point sert à repérer un
        // couverture de la fenêtre exigée. Indisponible sans GPS ou sans horaires (dégradation propre).
        CoherenceHoraire coherence = AnalyseCoherenceHoraire.analyser(
                latitude, longitude, passage.dateEnregistrement(), passage.heureDebut(), passage.heureFin());

        return new Diagnostic(
                idPassage,
                idSession,
                passage.idEnregistreur(),
                anomalies,
                climat,
                latitude,
                longitude,
                horloge.maintenant(),
                MeteoPassage.temperatureDebutNuit(passage.donneesMeteo()),
                coherence,
                completude);
    }

    private static Path chemin(String valeur) {
        return valeur == null || valeur.isBlank() ? null : Path.of(valeur);
    }
}
