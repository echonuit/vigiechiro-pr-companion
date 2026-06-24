package fr.univ_amu.iut.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.JsonSimple;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.diagnostic.model.Diagnostic;
import fr.univ_amu.iut.diagnostic.model.ServiceDiagnostic;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ReleveClimatique;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests de [ServiceDiagnostic] de bout en bout sur une base SQLite jetable (`@TempDir` +
/// [MigrationSchema]), avec les vrais DAO des features `passage`/`sites` et une
/// [HorlogeFigee] (horodatage déterministe). Couvre les trois exigences du parcours P6 :
/// anomalies listées (R19), série climatique lue quand le relevé est présent, absence de relevé
/// signalée (R20).
class ServiceDiagnosticTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";
    private static final LocalDateTime INSTANT = LocalDateTime.of(2026, 5, 31, 9, 0, 0);

    @TempDir
    Path dossier;

    private ServiceDiagnostic service;
    private PassageDao passageDao;
    private SessionDao sessionDao;
    private JournalDuCapteurDao journalDao;
    private ReleveClimatiqueDao releveDao;
    private Long idPassage;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));

        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        Site site = siteDao.insert(new Site(null, "040962", "Étang", Protocole.STANDARD, null, "2026-05-01", ID_USER));
        // Point géolocalisé : on vérifiera que le GPS remonte depuis la feature sites.
        Long idPoint = pointDao.insert(new PointDEcoute(null, "A1", 43.529, 5.447, "lisière", site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));

        passageDao = new PassageDao(source);
        sessionDao = new SessionDao(source);
        journalDao = new JournalDuCapteurDao(source);
        releveDao = new ReleveClimatiqueDao(source);

        idPassage = passageDao
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-04-22",
                        "20:25:00",
                        "07:47:00",
                        null,
                        StatutWorkflow.TRANSFORME,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE))
                .id();

        service = new ServiceDiagnostic(
                passageDao, sessionDao, journalDao, releveDao, pointDao, new HorlogeFigee(INSTANT));
    }

    private Long creerSession() {
        return sessionDao
                .insert(new SessionDEnregistrement(
                        null, dossier.resolve("Car040962-2026-Pass1-A1").toString(), null, null, idPassage))
                .id();
    }

    /// Copie le THLog réel (ressource de test) dans le workspace jetable, comme le ferait l'import.
    private Path copierThLogReel() {
        Path cible = dossier.resolve("PaRecPR" + SERIE + "_THLog.csv");
        try (InputStream in = getClass().getResourceAsStream("PaRecPR1925492_THLog.csv")) {
            Files.copy(in, cible, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new UncheckedIOException("Copie du THLog de test impossible", new java.io.IOException(e));
        }
        return cible;
    }

    @Test
    @DisplayName("#106 : la température de weather_data est exposée dans le Diagnostic (trajet réel)")
    void temperature_depuis_weather_data() {
        creerSession();
        // Renseigne la météo du passage seedé via la colonne weather_data ({"tempDebut":8.5}).
        Passage p = passageDao.findById(idPassage).orElseThrow();
        passageDao.update(new Passage(
                p.id(),
                p.numeroPassage(),
                p.annee(),
                p.dateEnregistrement(),
                p.heureDebut(),
                p.heureFin(),
                p.parametresAcquisition(),
                p.statutWorkflow(),
                p.verdictVerification(),
                p.commentaire(),
                "{\"tempDebut\":8.5}",
                p.deposeLe(),
                p.idPoint(),
                p.idEnregistreur()));

        Diagnostic diagnostic = service.diagnostiquer(idPassage);

        assertThat(diagnostic.temperatureDebutNuit())
                .as("trajet Passage.weather_data → MeteoPassage → Diagnostic")
                .isEqualTo(8.5);
    }

    @Test
    @DisplayName("#106 : météo absente → température null dans le Diagnostic (jamais bloquant)")
    void temperature_absente() {
        creerSession(); // le passage seedé a weather_data = null

        assertThat(service.diagnostiquer(idPassage).temperatureDebutNuit()).isNull();
    }

    @Test
    @DisplayName("R19 : un journal avec anomalies seedées → anomalies listées et classées")
    void anomalies_listees() {
        Long idSession = creerSession();
        List<String> anomalies = List.of(
                "Réveil non programmé : 22/04/26 - 03:00:00 PR1925492 Wakeup",
                "Erreur SD : échec d'écriture sur la carte SD",
                "Batterie faible (15%) : Batteries internes 15%");
        journalDao.insert(new JournalDuCapteur(
                null,
                "LogPR" + SERIE + ".txt",
                JsonSimple.tableau(List.of("### Démarrage PR" + SERIE)),
                JsonSimple.tableau(anomalies),
                idSession));

        Diagnostic diagnostic = service.diagnostiquer(idPassage);

        assertThat(diagnostic.idSession()).isEqualTo(idSession);
        assertThat(diagnostic.numeroSerieEnregistreur()).isEqualTo(SERIE);
        assertThat(diagnostic.anomalies().aDesAnomalies()).isTrue();
        assertThat(diagnostic.anomalies().anomalies()).hasSize(3);
        assertThat(diagnostic.anomalies().reveilsNonProgrammes()).hasSize(1);
        assertThat(diagnostic.anomalies().erreursSD()).hasSize(1);
        assertThat(diagnostic.anomalies().alertesBatterie()).hasSize(1);
        assertThat(diagnostic.genereLe()).isEqualTo(INSTANT);
    }

    @Test
    @DisplayName("Le GPS du diagnostic provient du point d'écoute (feature sites)")
    void gps_depuis_point() {
        Long idSession = creerSession();
        journalDao.insert(new JournalDuCapteur(null, "LogPR.txt", "[]", "[]", idSession));

        Diagnostic diagnostic = service.diagnostiquer(idPassage);

        assertThat(diagnostic.coordonneesGpsDisponibles()).isTrue();
        assertThat(diagnostic.gpsLatitude()).isEqualTo(43.529);
        assertThat(diagnostic.gpsLongitude()).isEqualTo(5.447);
    }

    @Test
    @DisplayName("Relevé climatique présent → série lue depuis le THLog (~1 mesure/600s)")
    void serie_climatique_lue() {
        Long idSession = creerSession();
        journalDao.insert(new JournalDuCapteur(null, "LogPR.txt", "[]", "[]", idSession));
        releveDao.insert(new ReleveClimatique(null, copierThLogReel().toString(), null, idSession));

        Diagnostic diagnostic = service.diagnostiquer(idPassage);

        assertThat(diagnostic.releveClimatiqueAbsent()).isFalse();
        assertThat(diagnostic.climat().present()).isTrue();
        // 70 mesures dans la fixture réelle (71 lignes - entête).
        assertThat(diagnostic.climat().nombreMesures()).isEqualTo(70);
        assertThat(diagnostic.climat().mesures().getFirst().temperatureCelsius())
                .isEqualTo(23.9);
        assertThat(diagnostic.climat().mesures().getFirst().humiditePourcent()).isEqualTo(64);
    }

    @Test
    @DisplayName("R20 : relevé climatique absent → signalé explicitement")
    void releve_absent_signale() {
        Long idSession = creerSession();
        journalDao.insert(new JournalDuCapteur(null, "LogPR.txt", "[]", "[]", idSession));
        // aucun climate_log inséré : la sonde manque.

        Diagnostic diagnostic = service.diagnostiquer(idPassage);

        assertThat(diagnostic.releveClimatiqueAbsent()).isTrue();
        assertThat(diagnostic.climat().present()).isFalse();
        assertThat(diagnostic.climat().mesures()).isEmpty();
    }

    @Test
    @DisplayName("Passage inconnu → RegleMetierException")
    void passage_inconnu() {
        assertThatThrownBy(() -> service.diagnostiquer(9999L))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    @DisplayName("Session d'enregistrement manquante → RegleMetierException")
    void session_manquante() {
        // passage créé en @BeforeEach mais aucune session rattachée.
        assertThatThrownBy(() -> service.diagnostiquer(idPassage))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Session");
    }
}
