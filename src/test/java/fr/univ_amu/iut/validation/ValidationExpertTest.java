package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.MessageVigieChiro;
import fr.univ_amu.iut.commun.api.ObservationVigieChiro;
import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.ExportVuCsv;
import fr.univ_amu.iut.validation.model.MessageObservation;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ParserCsvTadarida;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.dao.MessageObservationDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// **Validation d'expert** (#1417, EPIC #1154) : l'avis du validateur du MNHN et le fil de discussion,
/// du serveur jusqu'à la base, sur une base SQLite jetable et les vrais DAO.
///
/// Ce que ces tests protègent : jusqu'ici, l'application recevait ces champs à **chaque** import et les
/// jetait. Elle présentait donc la correction de l'observateur comme le dernier mot, alors qu'un expert
/// avait pu la réviser — une **perte silencieuse**, exactement ce que l'EPIC #1154 traque.
class ValidationExpertTest {

    private static final String ID_USER = "u-1";
    private static final String SEQUENCE = "Car640380-2026-Pass2-Z1-PaRec_20260422_220529_000";
    private static final String ID_DONNEE = "d1";

    @TempDir
    Path dossier;

    private ObservationDao observationDao;
    private MessageObservationDao messageDao;
    private TaxonDao taxonDao;
    private ServiceValidation service;
    private long idPassage;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));

        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        PassageDao passageDao = new PassageDao(source);
        SessionDao sessionDao = new SessionDao(source);
        EnregistrementOriginalDao originalDao = new EnregistrementOriginalDao(source);
        SequenceDao sequenceDao = new SequenceDao(source);
        EnregistreurDao enregistreurDao = new EnregistreurDao(source);
        taxonDao = new TaxonDao(source);
        observationDao = new ObservationDao(source);
        messageDao = new MessageObservationDao(source);

        Site site = siteDao.insert(new Site(null, "640380", "Test", Protocole.STANDARD, null, "2026-04-01", ID_USER));
        PointDEcoute point = pointDao.insert(new PointDEcoute(null, "Z1", null, null, null, site.id()));
        enregistreurDao.insert(new Enregistreur("1925492", "V1.01", null));
        Passage passage = passageDao.insert(new Passage(
                null,
                2,
                2026,
                "2026-04-22",
                "20:00:00",
                "06:00:00",
                null,
                StatutWorkflow.DEPOSE,
                null,
                null,
                null,
                null,
                point.id(),
                "1925492"));
        idPassage = passage.id();
        SessionDEnregistrement session =
                sessionDao.insert(new SessionDEnregistrement(null, "/ws/session", null, null, idPassage));
        EnregistrementOriginal original = originalDao.insert(new EnregistrementOriginal(
                null, SEQUENCE + ".wav", "/ws/bruts/" + SEQUENCE + ".wav", 5.0, 384000, null, session.id()));
        sequenceDao.insert(new SequenceDEcoute(
                null,
                SEQUENCE + ".wav",
                original.id(),
                0,
                0.0,
                5.0,
                "/ws/transformes/" + SEQUENCE + ".wav",
                false,
                session.id()));

        service = new ServiceValidation(
                new fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao(source),
                observationDao,
                taxonDao,
                sessionDao,
                sequenceDao,
                new ParserCsvTadarida(),
                new ExportVuCsv(),
                new UniteDeTravail(source),
                new HorlogeFigee(LocalDate.of(2026, 5, 31)),
                messageDao,
                new LienVigieChiroDao(source));
    }

    @Test
    @DisplayName("#1417 : l'avis du validateur est persisté à côté de celui de l'observateur — les trois"
            + " avis coexistent, aucun n'écrase l'autre")
    void les_trois_avis_coexistent() {
        BilanImport bilan = importer(observation(0, "Pipkuh", "Pippip", Certitude.POSSIBLE, "Pipnat", List.of()));

        assertThat(bilan.importees()).isEqualTo(1);
        Observation observation =
                observationDao.findByResults(bilan.resultats().id()).getFirst();
        assertThat(observation.taxonTadarida()).as("Tadarida propose").isEqualTo("Pipkuh");
        assertThat(observation.taxonObservateur()).as("l'observateur corrige").isEqualTo("Pippip");
        assertThat(observation.taxonValidateur())
                .as("le validateur tranche : c'est ce mot-là que l'application jetait")
                .isEqualTo("Pipnat");
        assertThat(observation.certitudeObservateur()).isEqualTo(Certitude.POSSIBLE);
        assertThat(observation.certitudeValidateur()).isEqualTo(Certitude.SUR);
    }

    @Test
    @DisplayName("#1417 : un taxon de validateur hors référentiel est auto-enregistré en souche — sans"
            + " quoi la FK ferait taire l'avis qui fait autorité")
    void taxon_validateur_hors_referentiel_devient_une_souche() {
        BilanImport bilan = importer(observation(0, "Pipkuh", null, null, "Especeinconnue", List.of()));

        assertThat(taxonDao.findById("Especeinconnue"))
                .as("le code inconnu du validateur est semé en souche, comme celui de l'observateur")
                .isPresent();
        assertThat(observationDao
                        .findByResults(bilan.resultats().id())
                        .getFirst()
                        .taxonValidateur())
                .as("son verdict est conservé, pas ramené à null par la contrainte de clé étrangère")
                .isEqualTo("Especeinconnue");
    }

    @Test
    @DisplayName("#1417 : le fil de discussion est persisté dans l'ordre du serveur, rattaché à la bonne"
            + " observation par son ancrage plateforme")
    void le_fil_est_persiste_dans_l_ordre_du_serveur() {
        // Deux observations sur la même donnée : seule celle d'indice 1 a un fil. Le rattachement se fait
        // par l'ancrage (_id de la donnée + indice brut), pas par la position dans la liste.
        BilanImport bilan = importer(
                observation(0, "Pipkuh", null, null, null, List.of()),
                observation(
                        1,
                        "Eptser",
                        null,
                        null,
                        null,
                        List.of(
                                new MessageVigieChiro(
                                        "u-validateur",
                                        "Médiane basse pour un Eptser, non ?",
                                        Instant.parse("2026-07-11T21:04:00Z")),
                                new MessageVigieChiro("u-moi", "Je repasse le son.", null))));

        List<Observation> observations =
                observationDao.findByResults(bilan.resultats().id());
        Observation sansFil = trouver(observations, 0);
        Observation avecFil = trouver(observations, 1);

        assertThat(messageDao.filDeLObservation(sansFil.id()))
                .as("aucun fil ouvert sur cette détection : rien en base")
                .isEmpty();
        assertThat(messageDao.filDeLObservation(avecFil.id()))
                .as("l'ordre du serveur ($push) est l'ordre chronologique : il est figé par le rang")
                .extracting(MessageObservation::rang, MessageObservation::auteur, MessageObservation::texte)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0, "u-validateur", "Médiane basse pour un Eptser, non ?"),
                        org.assertj.core.groups.Tuple.tuple(1, "u-moi", "Je repasse le son."));
        assertThat(messageDao.filDeLObservation(avecFil.id()).getFirst().date())
                .as("la date RFC 1123 du serveur est normalisée en instant")
                .isEqualTo(Instant.parse("2026-07-11T21:04:00Z"));
        assertThat(messageDao.filDeLObservation(avecFil.id()).get(1).deMoi("u-moi"))
                .as("« vous » se déduit par comparaison avec l'identifiant du profil connecté, sans appel")
                .isTrue();
    }

    @Test
    @DisplayName("#1417 : au réimport, l'avis du validateur et le fil sont RAFRAÎCHIS (le serveur fait foi),"
            + " alors que la correction de l'observateur est préservée")
    void le_reimport_rafraichit_l_avis_du_validateur_et_preserve_celui_de_l_observateur() {
        importer(observation(
                0,
                "Pipkuh",
                "Pippip",
                Certitude.SUR,
                "Pipnat",
                List.of(new MessageVigieChiro("u-validateur", "Je penche pour Pipnat.", null))));

        // Le validateur a changé d'avis, et le fil s'est allongé. L'observateur, lui, n'a rien retouché :
        // sa correction locale doit survivre.
        BilanImport bilan = service.importerDepuisVigieChiro(
                idPassage,
                List.of(new DonneeVigieChiro(
                        ID_DONNEE,
                        SEQUENCE,
                        List.of(observation(
                                0,
                                "Pipkuh",
                                null,
                                null,
                                "Pippyg",
                                List.of(
                                        new MessageVigieChiro("u-validateur", "Je penche pour Pipnat.", null),
                                        new MessageVigieChiro("u-validateur", "Correction : Pippyg.", null)))))),
                true);

        Observation observation =
                observationDao.findByResults(bilan.resultats().id()).getFirst();
        assertThat(observation.taxonObservateur())
                .as("la décision de l'observateur est une saisie humaine : un réimport ne l'efface pas")
                .isEqualTo("Pippip");
        assertThat(observation.taxonValidateur())
                .as("l'avis du validateur est un reflet du serveur : c'est SON dernier mot qui s'affiche,"
                        + " pas la copie qu'on en gardait")
                .isEqualTo("Pippyg");
        assertThat(messageDao.filDeLObservation(observation.id()))
                .as("le fil est remplacé, pas fusionné : un message n'a pas d'identité stable côté serveur")
                .extracting(MessageObservation::texte)
                .containsExactly("Je penche pour Pipnat.", "Correction : Pippyg.");
    }

    /// Importe `observations` sur l'unique séquence de la fixture, comme le ferait un import VigieChiro.
    private BilanImport importer(ObservationVigieChiro... observations) {
        return service.importerDepuisVigieChiro(
                idPassage, List.of(new DonneeVigieChiro(ID_DONNEE, SEQUENCE, List.of(observations))), false);
    }

    /// Une observation serveur : Tadarida propose `taxonTadarida`, l'observateur retient `taxonObservateur`
    /// (avec sa certitude), le validateur tranche `taxonValidateur` (toujours « sûr » ici).
    private static ObservationVigieChiro observation(
            int indice,
            String taxonTadarida,
            String taxonObservateur,
            Certitude certitudeObservateur,
            String taxonValidateur,
            List<MessageVigieChiro> messages) {
        return new ObservationVigieChiro(
                indice,
                taxonTadarida,
                0.9,
                45.0,
                0.2,
                0.32,
                null,
                taxonObservateur,
                certitudeObservateur,
                taxonValidateur,
                taxonValidateur == null ? null : Certitude.SUR,
                messages);
    }

    private static Observation trouver(List<Observation> observations, int indiceServeur) {
        return observations.stream()
                .filter(observation -> observation.indiceVigieChiro() == indiceServeur)
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune observation d'indice serveur " + indiceServeur));
    }
}
