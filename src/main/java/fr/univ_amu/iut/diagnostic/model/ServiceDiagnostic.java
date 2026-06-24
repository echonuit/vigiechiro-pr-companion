package fr.univ_amu.iut.diagnostic.model;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.RegleMetierException;
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

/// Service métier de la feature `diagnostic` (parcours P6, épopée E6) : lit l'état
/// matériel/technique d'une nuit **déjà importée** et l'expose pour l'onglet « Diagnostic » de la
/// fiche passage. Suit le patron du service de référence `ServiceSites` : pure Java testable,
/// dépendances (DAO + [Horloge]) reçues par constructeur avec `requireNonNull`, assemblé par le
/// `*Module` de la feature.
///
/// **Lecture seule, sans re-parsing lourd** : le service ne relit ni les originaux ni le journal
/// `LogPR` brut ; il exploite ce qui a été persisté à l'import :
///
/// - les colonnes JSON `sensor_log.parsed_events` / `detected_anomalies` via
///   [AnalyseAnomalies] (R19) ;
/// - la série climatique relue du fichier `THLog` via [LectureThLog] (R20) ;
/// - les coordonnées GPS du point d'écoute via le [PointDao] de la feature `sites`.
///
/// **Dépendances inter-features** (sens autorisé, graphe acyclique) :
/// `diagnostic → passage.model.dao` (passage, session, journal, relevé) et
/// `diagnostic → sites.model.dao` (point/GPS), toutes en lecture seule. Aucune arête inverse
/// n'est créée.
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
        AnalyseAnomalies anomalies = journalDao
                .trouverParSession(idSession)
                .map(AnalyseAnomalies::depuisJournal)
                .orElseGet(AnalyseAnomalies::vide);

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

        return new Diagnostic(
                idPassage,
                idSession,
                passage.idEnregistreur(),
                anomalies,
                climat,
                latitude,
                longitude,
                horloge.maintenant(),
                MeteoPassage.temperatureDebutNuit(passage.donneesMeteo()));
    }

    private static Path chemin(String valeur) {
        return valeur == null || valeur.isBlank() ? null : Path.of(valeur);
    }
}
