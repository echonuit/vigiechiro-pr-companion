package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
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
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ObservationStatut;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.VueValidation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Test E2E du parcours P9 « Regrouper les nuits successives par point »**, piloté **sans IHM** :
/// on rejoue le parcours de Samuel **uniquement par la couche métier** (services réels +
/// DAO réels câblés par l'injecteur applicatif [RacineInjecteur]), sur une **base SQLite jetable**
/// (workspace temporaire + [MigrationSchema]). Aucun `FXMLLoader`, aucun `Stage`, aucun `FxRobot` :
/// chaque jalon du parcours est vérifié par son **résultat métier** en base.
///
/// **Mise en garde architecturale.** P9 est une cible **COULD** : il n'existe **aucun service ni
/// ViewModel de « regroupement »** dans le code (pas de `ServiceRegroupement`, pas de méthode
/// `validerSurPeriode(...)`). Ce test n'**invente** donc aucune API : il **orchestre les primitives
/// réelles existantes** (`ServiceSites`, `ServiceValidation`, `PassageDao`) pour reconstituer, de
/// bout en bout, le **résultat métier** que la future vue de validation regroupée produirait. La
/// fusion par espèce et le compteur « N nuits / X espèces / Y observations » sont **calculés dans le
/// test** à partir des observations réellement persistées par [ServiceValidation], faute de service
/// dédié. Le jour où un service de regroupement existera, ces calculs glisseront dans le service et
/// le test l'appellera directement.
///
/// Modélisation conforme à l'application : dans VigieChiro, **chaque nuit importée est un
/// [Passage]** (clé d'unicité R5 = `point / année / n° de passage`). « 4 nuits successives sur un
/// point » se traduit donc par **4 passages** (n° 1 à 4, dates croissantes) rattachés au **même
/// point**. Les passages sont semés à l'état [StatutWorkflow#DEPOSE] (contexte post-P7 : la
/// validation Tadarida n'est ouverte que sur une nuit déposée), avec leurs sessions / séquences /
/// observations, exactement comme le fait le test d'intégration `ServiceValidationTest`.
///
/// Jalons du brief P9 vérifiés :
///
/// 1. **Lister les nuits d'un point** : sélectionner un point fait apparaître ses passages de la
///    saison, **ordonnés chronologiquement** ([PassageDao#findByPoint]), à l'exclusion des passages
///    des autres points (regroupement **par point**).
/// 2. **Sélectionner les nuits successives** : les 4 passages du point forment un **run contigu**
///    (n° 1→4), candidats au regroupement.
/// 3. **Regrouper pour validation** : les observations des 4 nuits sont **fusionnées et triées par
///    espèce**, et le **compteur** « 4 nuits, X espèces détectées, Y observations » est exact.
/// 4. **Valider une espèce sur toute la période** (R18, mode inventaire) : une espèce présente sur
///    les 4 nuits est validée **une fois par nuit du regroupement** ; son verdict couvre alors la
///    totalité de ses détections sur la période, les autres espèces restant non touchées.
class ParcoursRegrouperNuitsParPointE2ETest {

    private static final String ID_USER = "u-e2e-p9";
    private static final String SERIE = "1925492";
    private static final String CARRE = "640380";
    private static final int ANNEE = 2026;

    /// Espèces détectées par nuit (un code de taxon = une observation). Codes semés par la migration
    /// `V02` (Pippip, Nyclei, noise). « Pippip » est présent sur les 4 nuits (cas du brief : une
    /// espèce de la nuit 1 réapparaît les nuits suivantes).
    private static final String[] NUIT_1 = {"Pippip", "Pippip", "Pippip", "Nyclei", "Nyclei"};
    private static final String[] NUIT_2 = {"Pippip", "Pippip", "noise"};
    private static final String[] NUIT_3 = {"Pippip", "Pippip", "Pippip", "Pippip"};
    private static final String[] NUIT_4 = {"Pippip", "Nyclei", "noise", "noise"};

    private Injector injector;
    private Path workspace;
    private PassageDao passageDao;
    private SessionDao sessionDao;
    private EnregistrementOriginalDao originalDao;
    private SequenceDao sequenceDao;
    private ServiceValidation validation;

    private PointDEcoute point;
    private List<Long> idsNuits;
    private Long idNuitAutrePoint;

    @BeforeEach
    void preparer() throws Exception {
        // Base jetable : un workspace temporaire par test → injecteur et DB SQLite isolés. La
        // propriété doit être posée AVANT de construire l'injecteur (qui y lit l'emplacement de la base).
        workspace = Files.createTempDirectory("vc-e2e-p9");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        // Application mono-utilisateur : le seul utilisateur en base désigne « idUtilisateurCourant ».
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Samuel"));

        // DAO réels adossés à la MÊME source que les services de l'injecteur (un seul fichier SQLite) :
        // ce que ces DAO écrivent, les services de l'injecteur le relisent, et inversement.
        passageDao = new PassageDao(source);
        sessionDao = new SessionDao(source);
        originalDao = new EnregistrementOriginalDao(source);
        sequenceDao = new SequenceDao(source);
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));

        ServiceSites sites = injector.getInstance(ServiceSites.class);
        validation = injector.getInstance(ServiceValidation.class);

        Site site = sites.creerSite(CARRE, "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        point = sites.ajouterPoint(site.id(), "A1", 43.5298, 5.4474, null);
        PointDEcoute autrePoint = sites.ajouterPoint(site.id(), "B2", null, null, null);

        // 4 nuits successives sur le point A1 (dates croissantes, n° 1→4) + 1 nuit sur un AUTRE point
        // (B2), qui ne doit jamais entrer dans le regroupement « par point » de A1.
        idsNuits = new ArrayList<>();
        idsNuits.add(semerNuit(point.id(), 1, "2026-06-20", "n1", NUIT_1));
        idsNuits.add(semerNuit(point.id(), 2, "2026-06-21", "n2", NUIT_2));
        idsNuits.add(semerNuit(point.id(), 3, "2026-06-22", "n3", NUIT_3));
        idsNuits.add(semerNuit(point.id(), 4, "2026-06-23", "n4", NUIT_4));
        idNuitAutrePoint = semerNuit(autrePoint.id(), 1, "2026-06-22", "autre", new String[] {"Pippip"});
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("P9 jalons 1-2 : sélectionner un point liste ses nuits triées chronologiquement, isolées par point")
    void point_liste_ses_nuits_successives_triees_chronologiquement() {
        // Jalon 1 — Sélection du point : ses passages de la saison apparaissent, ordonnés
        // chronologiquement (findByPoint trie par (année, n° de passage), aligné sur les dates).
        List<Passage> nuits = passageDao.findByPoint(point.id());
        assertThat(nuits).hasSize(4);
        assertThat(nuits).allSatisfy(p -> assertThat(p.annee()).isEqualTo(ANNEE));
        assertThat(nuits)
                .extracting(Passage::dateEnregistrement)
                .containsExactly("2026-06-20", "2026-06-21", "2026-06-22", "2026-06-23");

        // L'ordre chronologique coïncide bien avec l'ordre des n° de passage (run contigu 1→4).
        assertThat(nuits).extracting(Passage::numeroPassage).containsExactly(1, 2, 3, 4);

        // Isolation « par point » : la nuit semée sur l'autre point (B2) n'est PAS dans le groupe de A1.
        assertThat(nuits).extracting(Passage::id).doesNotContain(idNuitAutrePoint);

        // Jalon 2 — Sélection des nuits successives : les 4 passages du point sont les candidats au
        // regroupement (toutes sur le même point, le critère de fusion de P9).
        assertThat(nuits).extracting(Passage::idPoint).containsOnly(point.id());
        assertThat(idsNuits)
                .containsExactlyInAnyOrderElementsOf(
                        nuits.stream().map(Passage::id).toList());
    }

    @Test
    @DisplayName("P9 jalon 3 : regrouper fusionne les observations des 4 nuits par espèce avec le bon compteur")
    void regrouper_les_nuits_fusionne_les_observations_par_espece() {
        // Jalon 3 — Regroupement : on agrège les observations réellement persistées par
        // ServiceValidation sur les 4 nuits du point, puis on les trie/compte par espèce (le service
        // de regroupement dédié n'existe pas encore : cf. la mise en garde de classe).
        TreeMap<String, Integer> comptesParEspece = new TreeMap<>();
        Set<String> nuitsAvecObservations = new LinkedHashSet<>();
        int totalObservations = 0;
        for (Long idNuit : idsNuits) {
            VueValidation vue = validation.chargerValidation(idNuit);
            for (ObservationStatut os : vue.observations()) {
                String espece = os.observation().taxonTadarida();
                comptesParEspece.put(espece, comptesParEspece.getOrDefault(espece, 0) + 1);
                nuitsAvecObservations.add(String.valueOf(idNuit));
                totalObservations++;
            }
        }

        // Compteur du brief « N nuits, X espèces détectées, Y observations au total ».
        assertThat(nuitsAvecObservations).as("4 nuits regroupées").hasSize(4);
        assertThat(comptesParEspece.keySet())
                .as("espèces fusionnées et triées (ordre naturel du code)")
                .containsExactly("Nyclei", "Pippip", "noise");
        assertThat(totalObservations).as("observations au total sur la période").isEqualTo(16);

        // Détail par espèce : Pippip présent sur les 4 nuits (10), Nyclei (3), noise (3).
        assertThat(comptesParEspece)
                .containsEntry("Pippip", 10)
                .containsEntry("Nyclei", 3)
                .containsEntry("noise", 3);
    }

    @Test
    @DisplayName("P9 jalon 4 : valider une espèce sur la période regroupée couvre toutes ses nuits (R18 inventaire)")
    void valider_une_espece_sur_toute_la_periode_regroupee() {
        // Jalon 4 — Validation regroupée : Samuel valide « Pippip » une seule fois par nuit du
        // regroupement. En mode inventaire (R18), chaque validation propage en auto aux autres Pippip
        // de la même nuit ; en chaînant sur les 4 nuits, son verdict couvre toute la période.
        for (Long idNuit : idsNuits) {
            Observation unPippip = vueDe(idNuit).observations().stream()
                    .map(ObservationStatut::observation)
                    .filter(o -> "Pippip".equals(o.taxonTadarida()))
                    .findFirst()
                    .orElseThrow();
            validation.validerSelonMode(unPippip.id(), ModeRevue.INVENTAIRE);
        }

        // Résultat métier sur l'ensemble du regroupement : toutes les détections « Pippip » des 4
        // nuits sont validées (10), les autres espèces restent non touchées (6).
        long pippipValidees = 0;
        long autresNonTouchees = 0;
        for (Long idNuit : idsNuits) {
            for (ObservationStatut os : vueDe(idNuit).observations()) {
                if ("Pippip".equals(os.observation().taxonTadarida())) {
                    assertThat(os.statut()).isEqualTo(StatutObservation.VALIDEE);
                    pippipValidees++;
                } else {
                    assertThat(os.statut()).isEqualTo(StatutObservation.NON_TOUCHEE);
                    autresNonTouchees++;
                }
            }
        }
        assertThat(pippipValidees).as("Pippip validé sur toute la période").isEqualTo(10);
        assertThat(autresNonTouchees).as("Nyclei et noise non touchés").isEqualTo(6);
    }

    /// Recharge la vue de validation d'une nuit (jeu de résultats Tadarida + observations + statut).
    private VueValidation vueDe(Long idPassage) {
        return validation.chargerValidation(idPassage);
    }

    /// Sème une nuit déposée sur un point : passage (DEPOSE) + session + une séquence d'écoute + import
    /// d'un CSV Tadarida décrivant une observation par code de taxon fourni. Renvoie l'id du passage.
    ///
    /// Calque la mise en place d'intégration de `ServiceValidationTest` (mêmes constructeurs de
    /// `Passage`, `SessionDEnregistrement`, `EnregistrementOriginal`, `SequenceDEcoute`).
    private Long semerNuit(Long idPoint, int numeroPassage, String date, String prefixe, String[] taxons)
            throws Exception {
        Passage passage = passageDao.insert(new Passage(
                null,
                numeroPassage,
                ANNEE,
                date,
                "20:00:00",
                "06:00:00",
                null,
                StatutWorkflow.DEPOSE,
                null,
                null,
                null,
                null,
                idPoint,
                SERIE));
        SessionDEnregistrement session =
                sessionDao.insert(new SessionDEnregistrement(null, "/ws/" + prefixe, null, null, passage.id()));
        String base = prefixe + "seqA";
        EnregistrementOriginal original = originalDao.insert(new EnregistrementOriginal(
                null, base + ".wav", "/ws/bruts/" + base + ".wav", 5.0, 384000, null, session.id()));
        sequenceDao.insert(new SequenceDEcoute(
                null,
                base + "_000.wav",
                original.id(),
                0,
                0.0,
                5.0,
                "/ws/transformes/" + base + "_000.wav",
                false,
                session.id()));

        Path csv = ecrireCsvTadarida(prefixe, base + "_000", taxons);
        validation.importer(passage.id(), csv);
        return passage.id();
    }

    /// Écrit un petit CSV Tadarida « Brut » (en-tête + une ligne d'observation par taxon), toutes les
    /// lignes pointant la même séquence par son nom (sans extension). Format identique à celui que
    /// `ServiceValidationTest` donne à parser.
    private Path ecrireCsvTadarida(String nom, String nomSequence, String[] taxons) throws Exception {
        StringBuilder contenu = new StringBuilder(ligne(
                "nom du fichier",
                "temps_debut",
                "temps_fin",
                "frequence_mediane",
                "tadarida_taxon",
                "tadarida_probabilite",
                "tadarida_taxon_autre",
                "observateur_taxon",
                "observateur_probabilite",
                "validateur_taxon",
                "validateur_probabilite"));
        for (String taxon : taxons) {
            contenu.append(ligne(nomSequence, "0.4", "4.1", "45", taxon, "0.80", "", "", "", "", ""));
        }
        Path fichier = workspace.resolve(nom + "_observations.csv");
        Files.writeString(fichier, contenu.toString(), StandardCharsets.UTF_8);
        return fichier;
    }

    /// Une ligne CSV : champs entre guillemets, séparés par `;`, terminée par un retour à la ligne.
    private static String ligne(String... champs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < champs.length; i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append('"').append(champs[i]).append('"');
        }
        return sb.append('\n').toString();
    }
}
