package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.FenetreObserveeNuit;
import fr.univ_amu.iut.passage.model.MeteoReleve;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.ServiceConditionsPassage;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests d'intégration de [ServicePassage#detailPassage] sur une base SQLite jetable (`@TempDir` +
/// [MigrationSchema]). Vérifie l'agrégation du passage et de sa session (volumes, durée enregistrée,
/// nombre de séquences), **sans jointure `sites`** (carré/code fournis par la navigation).
class ServicePassageDetailTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";

    @TempDir
    Path dossier;

    private ServicePassage service;
    private ServiceConditionsPassage conditions;
    private PassageDao passageDao;
    private SessionDao sessionDao;
    private EnregistrementOriginalDao originalDao;
    private SequenceDao sequenceDao;
    private EnregistreurDao enregistreurDao;
    private Long idPoint;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-01-01", ID_USER));
        idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "A1", 43.5, 5.4, null, site.id()))
                .id();
        enregistreurDao = new EnregistreurDao(source);
        enregistreurDao.insert(new Enregistreur(SERIE, "V1.01", null));
        passageDao = new PassageDao(source);
        sessionDao = new SessionDao(source);
        originalDao = new EnregistrementOriginalDao(source);
        sequenceDao = new SequenceDao(source);
        service = new ServicePassage(
                passageDao,
                new MoteurWorkflowPassage(),
                new HorlogeFigee(LocalDate.of(2026, 6, 22)),
                sessionDao,
                sequenceDao,
                new ServiceDisponibiliteAudio(sessionDao, sequenceDao, new Workspace(dossier)));
        conditions = new ServiceConditionsPassage(
                passageDao,
                new MaterielMicroDao(source),
                enregistreurDao,
                idPoint -> Optional.empty(),
                (lat, lon, jour, debut, fin) -> Optional.empty(),
                new FenetreObserveeNuit(sessionDao, new EnregistrementOriginalDao(source), sequenceDao));
    }

    @Test
    @DisplayName("#1892 definirHoraires : une nuit SANS preuve se corrige à la main")
    void definir_horaires_nuit_sans_preuve() {
        Passage passage = insererPassage(1, StatutWorkflow.IMPORTE);

        Passage corrige = conditions.definirHoraires(passage.id(), "21:00", "06:00");

        assertThat(corrige.heureDebut()).isEqualTo("21:00");
        assertThat(corrige.heureFin()).isEqualTo("06:00");
    }

    @Test
    @DisplayName("#1892 definirHoraires : une fin AVANT le début est normale (la nuit franchit minuit)")
    void definir_horaires_a_cheval_sur_minuit() {
        Passage passage = insererPassage(2, StatutWorkflow.IMPORTE);

        // C'est le cas nominal d'une nuit : refuser cette saisie interdirait de décrire une vraie nuit.
        assertThat(conditions.definirHoraires(passage.id(), "21:30", "05:15").heureFin())
                .isEqualTo("05:15");
    }

    @Test
    @DisplayName("#1892 definirHoraires : une fin ÉGALE au début est refusée (elle deviendrait 24 h)")
    void definir_horaires_fin_egale_debut_refusee() {
        Passage passage = insererPassage(3, StatutWorkflow.IMPORTE);

        // Deux bornes identiques ne délimitent aucune nuit, et l'envoi y voit une nuit à cheval sur
        // minuit : la règle « la fin ne suit pas le début » ajoute un jour. C'est ainsi que les nuits de
        // 24 h de #1860 sont apparues sur la plateforme.
        assertThatThrownBy(() -> conditions.definirHoraires(passage.id(), "15:00", "15:00"))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("24 heures");
    }

    @Test
    @DisplayName("#1892 definirHoraires : une heure illisible dit LAQUELLE des deux bornes est en cause")
    void definir_horaires_heure_illisible() {
        Passage passage = insererPassage(4, StatutWorkflow.IMPORTE);

        assertThatThrownBy(() -> conditions.definirHoraires(passage.id(), "21:00", "minuit"))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("de fin")
                .hasMessageContaining("minuit");
    }

    private Passage insererPassage(int numero, StatutWorkflow statut) {
        return passageDao.insert(new Passage(
                null,
                numero,
                2026,
                "2026-06-22",
                "20:25:00",
                "07:47:00",
                null,
                statut,
                null,
                null,
                null,
                null,
                idPoint,
                SERIE));
    }

    private void insererSequence(Long idSession, double duree) {
        EnregistrementOriginal original = originalDao.insert(new EnregistrementOriginal(
                null, "o-" + duree + ".wav", "/ws/bruts/o.wav", duree, 384000, null, idSession));
        sequenceDao.insert(new SequenceDEcoute(
                null, "s-" + duree + ".wav", original.id(), 0, 0.0, duree, "/ws/x.wav", true, idSession));
    }

    @Test
    @DisplayName("#1828 definirEnregistreur : pose le n° saisi et CRÉE la ligne recorder (clé étrangère)")
    void definir_enregistreur_cree_la_ligne_recorder() {
        Passage passage = insererPassage(9, StatutWorkflow.DEPOSE);

        Passage modifie = conditions.definirEnregistreur(passage.id(), "  1925492  ");

        assertThat(modifie.idEnregistreur())
                .as("le numéro saisi est normalisé puis posé sur le passage")
                .isEqualTo("1925492");
        assertThat(passageDao.findById(passage.id()).orElseThrow().idEnregistreur())
                .as("et il est bien persisté")
                .isEqualTo("1925492");
        assertThat(enregistreurDao.findById("1925492"))
                .as("la ligne recorder est créée : sans elle, la clé étrangère NOT NULL refuserait l'écriture")
                .isPresent();
    }

    @Test
    @DisplayName("#1828 definirEnregistreur : un champ laissé vide ne touche à rien (le schéma exige un n°)")
    void definir_enregistreur_vide_ne_change_rien() {
        Passage passage = insererPassage(10, StatutWorkflow.DEPOSE);

        assertThat(conditions.definirEnregistreur(passage.id(), "   ").idEnregistreur())
                .as("vider n'est pas possible : la nuit garde ce qu'elle avait")
                .isEqualTo(SERIE);
    }

    @Test
    @DisplayName("detailPassage agrège le passage et les volumes/durée/nombre de sa session")
    void detail_agrege_la_session() {
        Passage passage = insererPassage(2, StatutWorkflow.TRANSFORME);
        SessionDEnregistrement session =
                sessionDao.insert(new SessionDEnregistrement(null, "/ws/session", 4096L, 1024L, passage.id()));
        insererSequence(session.id(), 5.0);
        insererSequence(session.id(), 7.5);

        DetailPassage detail = service.detailPassage(passage.id());

        assertThat(detail.numeroPassage()).isEqualTo(2);
        assertThat(detail.annee()).isEqualTo(2026);
        assertThat(detail.heureDebut()).isEqualTo("20:25:00");
        assertThat(detail.heureFin()).isEqualTo("07:47:00");
        assertThat(detail.idEnregistreur()).isEqualTo(SERIE);
        assertThat(detail.statut()).isEqualTo(StatutWorkflow.TRANSFORME);
        assertThat(detail.verdict()).isNull();
        assertThat(detail.volumeOriginauxOctets()).isEqualTo(4096L);
        assertThat(detail.volumeSequencesOctets()).isEqualTo(1024L);
        assertThat(detail.nombreSequences()).isEqualTo(2);
        assertThat(detail.dureeEnregistreeSecondes()).isEqualTo(12.5);
    }

    @Test
    @DisplayName("detailPassage sans session renvoie des volumes et une durée nuls")
    void detail_sans_session() {
        Passage passage = insererPassage(1, StatutWorkflow.IMPORTE);

        DetailPassage detail = service.detailPassage(passage.id());

        assertThat(detail.nombreSequences()).isZero();
        assertThat(detail.volumeOriginauxOctets()).isZero();
        assertThat(detail.volumeSequencesOctets()).isZero();
        assertThat(detail.dureeEnregistreeSecondes()).isZero();
    }

    @Test
    @DisplayName("#106 : température de début de nuit optionnelle (null par défaut), définie puis effacée")
    void temperature_optionnelle() {
        Passage passage = insererPassage(3, StatutWorkflow.TRANSFORME);

        assertThat(service.detailPassage(passage.id()).meteo().temperatureDebutNuit())
                .as("température non renseignée par défaut")
                .isNull();

        conditions.definirMeteo(passage.id(), new MeteoReleve(8.5, null, null, null));
        assertThat(service.detailPassage(passage.id()).meteo().temperatureDebutNuit())
                .isEqualTo(8.5);

        conditions.definirMeteo(passage.id(), MeteoReleve.VIDE); // saisie vide → effacement
        assertThat(service.detailPassage(passage.id()).meteo().temperatureDebutNuit())
                .isNull();
    }

    @Test
    @DisplayName("#106 : definirMeteo préserve les autres clés météo de weather_data")
    void temperature_preserve_les_autres_cles() {
        Passage passage = passageDao.insert(new Passage(
                null,
                4,
                2026,
                "2026-06-22",
                "20:25:00",
                "07:47:00",
                null,
                StatutWorkflow.TRANSFORME,
                null,
                null,
                "{\"hygro\":80}", // une autre clé météo déjà présente
                null,
                idPoint,
                SERIE));

        conditions.definirMeteo(passage.id(), new MeteoReleve(8.5, null, null, null));

        String meteo = passageDao.findById(passage.id()).orElseThrow().donneesMeteo();
        assertThat(meteo).contains("\"hygro\":80").contains("\"tempDebut\":8.5");
        assertThat(service.detailPassage(passage.id()).meteo().temperatureDebutNuit())
                .isEqualTo(8.5);
    }

    @Test
    @DisplayName("detailPassage sur un passage introuvable lève une RegleMetierException")
    void detail_passage_introuvable() {
        assertThatThrownBy(() -> service.detailPassage(999L))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    @DisplayName("cheminSession renvoie le dossier racine ; marquerOriginauxPurges met le volume bruts à 0")
    void chemin_session_et_marquage_purge() {
        Passage passage = insererPassage(5, StatutWorkflow.TRANSFORME);
        sessionDao.insert(new SessionDEnregistrement(null, "/ws/Car640380-2026-Pass5-A1", 8192L, 1024L, passage.id()));

        assertThat(service.cheminSession(passage.id())).contains(Path.of("/ws/Car640380-2026-Pass5-A1"));
        assertThat(service.detailPassage(passage.id()).volumeOriginauxOctets()).isEqualTo(8192L);

        service.marquerOriginauxPurges(passage.id());

        assertThat(service.detailPassage(passage.id()).volumeOriginauxOctets())
                .as("après purge des bruts/, le volume des originaux tombe à 0 (l'écoute reste possible)")
                .isZero();
    }

    @Test
    @DisplayName("Sans session : cheminSession est vide et marquerOriginauxPurges est sans effet (ne lève pas)")
    void chemin_session_sans_session() {
        Passage passage = insererPassage(6, StatutWorkflow.IMPORTE);

        assertThat(service.cheminSession(passage.id())).isEmpty();
        service.marquerOriginauxPurges(passage.id()); // idempotent, aucun effet
    }
}
