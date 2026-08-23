package fr.univ_amu.iut.multisite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.ReleveTraitementDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.multisite.model.CarreAgrege;
import fr.univ_amu.iut.multisite.model.FiltresMultisite;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.PointAgrege;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.model.TriMultisite;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import fr.univ_amu.iut.passage.model.dao.CampagneDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.di.SitesModule;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/// Tests du service [ServiceMultisite] sur une base SQLite jetable (`@TempDir` + [MigrationSchema]). La
/// topologie est semée par [JeuDeDonneesPassage] (plusieurs nuits qui **partagent** leur site et leur
/// point, grâce au trouver-ou-créer de la fixture), et les DAO du service sont résolus par un injecteur
/// Guice : d'où l'absence de tout semis manuel de passage ici.
///
/// L'[HorlogeFigee] au 2026-05-31 garde déterministe la vue « saison courante » (année 2026). Le seul
/// DAO fourni localement est [ResultatsIdentificationDao] (feature `validation`), absent de cet injecteur
/// partiel de `multisite`.
///
/// Jeu de données (utilisateur `u-1`), ordre de lecture par défaut (site, point, année, n°) :
///
/// ```
///   #1  640380 / A1 / 2025 / 1  → Transformé , OK
///   #2  640380 / A1 / 2026 / 1  → Vérifié    , Douteux
///   #3  640380 / B2 / 2026 / 1  → Importé    , (non vérifié)
///   #4  640381 / A1 / 2026 / 1  → Déposé     , OK
///   #5  640381 / A1 / 2026 / 2  → Vérifié    , À jeter
/// ```
class ServiceMultisiteTest {

    private static final String ID_USER = "u-1";

    @TempDir
    Path dossier;

    private ServiceMultisite service;
    private Injector injecteur;
    private SourceDeDonnees source;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", dossier.toString());
        // Injecteur minimal : juste de quoi résoudre les DAO du service. Pas de `MultisiteModule` (on ne
        // veut pas la carte d'accueil et son chrome), et `ResultatsIdentificationDao` fourni localement.
        injecteur = Guice.createInjector(
                new CommunModule(),
                new PersistenceModule(),
                new SitesModule(),
                new PassageModule(),
                new AbstractModule() {
                    @Provides
                    ResultatsIdentificationDao fournirResultatsDao(SourceDeDonnees source) {
                        return new ResultatsIdentificationDao(source);
                    }
                });
        source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        semer(source, 1, 2025, "2025-06-20", StatutWorkflow.TRANSFORME, Verdict.OK, "640380", "Étang", "A1");
        semer(source, 1, 2026, "2026-06-20", StatutWorkflow.VERIFIE, Verdict.DOUTEUX, "640380", "Étang", "A1");
        semer(source, 1, 2026, "2026-06-21", StatutWorkflow.IMPORTE, null, "640380", "Étang", "B2");
        semer(source, 1, 2026, "2026-06-22", StatutWorkflow.DEPOSE, Verdict.OK, "640381", "Forêt", "A1");
        semer(source, 2, 2026, "2026-08-20", StatutWorkflow.VERIFIE, Verdict.A_JETER, "640381", "Forêt", "A1");

        // Horloge **figée** (déterminisme de la vue « saison ») : le service est assemblé à partir des DAO
        // résolus par l'injecteur, sans jamais construire de DAO de passage ici.
        service = new ServiceMultisite(
                injecteur.getInstance(SiteDao.class),
                injecteur.getInstance(PointDao.class),
                injecteur.getInstance(PassageDao.class),
                injecteur.getInstance(ReleveTraitementDao.class),
                injecteur.getInstance(ResultatsIdentificationDao.class),
                injecteur.getInstance(PointCommuneDao.class),
                Optional.empty(),
                new HorlogeFigee(LocalDate.of(2026, 5, 31)));
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private static void semer(
            SourceDeDonnees source,
            int numero,
            int annee,
            String date,
            StatutWorkflow statut,
            Verdict verdict,
            String carre,
            String nomSite,
            String point) {
        JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre(carre)
                .nomSite(nomSite)
                .point(point)
                .nuit(numero, annee, date)
                .statut(statut)
                .verdict(verdict)
                .semer();
    }

    @Test
    @DisplayName("#152 : agrège les carrés pour la carte (points, statut dominant, comptes)")
    void agreger_pour_carte() {
        List<CarreAgrege> carres = service.agregerPourCarte(ID_USER);
        assertThat(carres).extracting(CarreAgrege::numeroCarre).containsExactlyInAnyOrder("640380", "640381");

        CarreAgrege c640380 = carres.stream()
                .filter(c -> c.numeroCarre().equals("640380"))
                .findFirst()
                .orElseThrow();
        assertThat(c640380.nomConvivial()).isEqualTo("Étang");
        assertThat(c640380.nombrePassages()).isEqualTo(3);
        assertThat(c640380.points()).extracting(PointAgrege::codePoint).containsExactlyInAnyOrder("A1", "B2");

        PointAgrege a1 = c640380.points().stream()
                .filter(p -> p.codePoint().equals("A1"))
                .findFirst()
                .orElseThrow();
        assertThat(a1.nombrePassages()).isEqualTo(2);
        assertThat(a1.statutDominant())
                .as("statut du passage le plus récent (1/2026)")
                .isEqualTo(StatutWorkflow.VERIFIE);
        assertThat(a1.estGeolocalise()).as("pas de GPS dans le seed").isFalse();

        // Point A1 du carré 640381 : DEPOSE (passage 1) puis VERIFIE (passage 2) → dominant = le plus récent.
        CarreAgrege c640381 = carres.stream()
                .filter(c -> c.numeroCarre().equals("640381"))
                .findFirst()
                .orElseThrow();
        assertThat(c640381.points().get(0).statutDominant())
                .as("n° de passage 2 plus récent que 1, même année")
                .isEqualTo(StatutWorkflow.VERIFIE);
    }

    // --- Agrégation + tri par défaut ---

    @Test
    @DisplayName("Agrège tous les passages de tous les sites, dans l'ordre de lecture par défaut")
    void agrege_tous_les_passages() {
        List<LignePassage> lignes = service.listerPassages(ID_USER);

        assertThat(lignes).hasSize(5);
        assertThat(lignes)
                .extracting(
                        LignePassage::numeroCarre,
                        LignePassage::codePoint,
                        LignePassage::annee,
                        LignePassage::numeroPassage)
                .containsExactly(
                        tuple("640380", "A1", 2025, 1),
                        tuple("640380", "A1", 2026, 1),
                        tuple("640380", "B2", 2026, 1),
                        tuple("640381", "A1", 2026, 1),
                        tuple("640381", "A1", 2026, 2));
    }

    @Test
    @DisplayName("#3175 : le nom convivial du site remonte dans la ligne, apparié à SON carré")
    void nom_du_site_remonte() {
        // Le nom convivial et le numéro de carré désignent le MÊME objet : `monitoring_site` porte les
        // deux colonnes. Ce que ce test protège n'est pas la présence du champ mais son appariement :
        // un nom pris sur le site voisin passerait toute autre vérification.
        assertThat(service.listerPassages(ID_USER))
                .extracting(LignePassage::numeroCarre, LignePassage::nomSite)
                .containsOnly(tuple("640380", "Étang"), tuple("640381", "Forêt"));
    }

    @Test
    @DisplayName("#2355 : le nom de la campagne du passage remonte dans la ligne, vide pour les autres")
    void campagne_du_passage_remonte() {
        // Chaîne réelle : on crée une campagne et on y rattache la première nuit (640380 / A1 / 2025).
        PassageDao passages = injecteur.getInstance(PassageDao.class);
        HorlogeFigee horloge = new HorlogeFigee(LocalDate.of(2026, 5, 31));
        ServiceCampagne campagnes = new ServiceCampagne(new CampagneDao(source), passages, horloge);
        Campagne suivi = campagnes.creerCampagne("Suivi ENS", 2026, null);
        campagnes.rattacherPassage(service.listerPassages(ID_USER).getFirst().idPassage(), suivi.id());

        assertThat(serviceAvec(campagnes, horloge).listerPassages(ID_USER))
                .extracting(LignePassage::campagne)
                .containsExactly("Suivi ENS", null, null, null, null);
    }

    @Test
    @DisplayName("#2355 : tri par campagne, alphabétique, nuits non rattachées en dernier")
    void tri_par_campagne() {
        PassageDao passages = injecteur.getInstance(PassageDao.class);
        HorlogeFigee horloge = new HorlogeFigee(LocalDate.of(2026, 5, 31));
        ServiceCampagne campagnes = new ServiceCampagne(new CampagneDao(source), passages, horloge);
        // Créées dans l'ordre inverse de l'alphabet, pour que le tri ne puisse pas réussir par hasard.
        Campagne zeta = campagnes.creerCampagne("Zeta", 2026, null);
        Campagne alpha = campagnes.creerCampagne("Alpha", 2026, null);
        List<LignePassage> ordreDeLecture = service.listerPassages(ID_USER);
        campagnes.rattacherPassage(ordreDeLecture.get(0).idPassage(), zeta.id());
        campagnes.rattacherPassage(ordreDeLecture.get(1).idPassage(), alpha.id());

        List<LignePassage> lignes = serviceAvec(campagnes, horloge)
                .listerPassages(ID_USER, FiltresMultisite.aucun(), TriMultisite.PAR_CAMPAGNE);

        assertThat(lignes).extracting(LignePassage::campagne).containsExactly("Alpha", "Zeta", null, null, null);
    }

    @Test
    @DisplayName("#2355 : filtre par campagne, correspondance partielle, insensible à la casse")
    void filtre_par_campagne() {
        PassageDao passages = injecteur.getInstance(PassageDao.class);
        HorlogeFigee horloge = new HorlogeFigee(LocalDate.of(2026, 5, 31));
        ServiceCampagne campagnes = new ServiceCampagne(new CampagneDao(source), passages, horloge);
        Campagne ens = campagnes.creerCampagne("Suivi ENS", 2026, null);
        List<LignePassage> ordreDeLecture = service.listerPassages(ID_USER);
        campagnes.rattacherPassage(ordreDeLecture.get(0).idPassage(), ens.id());
        ServiceMultisite avecCampagnes = serviceAvec(campagnes, horloge);

        assertThat(avecCampagnes.listerPassages(ID_USER, FiltresMultisite.parCampagne("ens")))
                .as("fragment en minuscules d'un nom en majuscules")
                .singleElement()
                .extracting(LignePassage::campagne)
                .isEqualTo("Suivi ENS");
        assertThat(avecCampagnes.listerPassages(ID_USER, FiltresMultisite.parCampagne("Suivi")))
                .hasSize(1);
        assertThat(avecCampagnes.listerPassages(ID_USER, FiltresMultisite.parCampagne("Inconnue")))
                .isEmpty();
    }

    @Test
    @DisplayName("#2355 : filtrer sur une campagne écarte les nuits non rattachées")
    void filtre_campagne_ecarte_les_non_rattachees() {
        PassageDao passages = injecteur.getInstance(PassageDao.class);
        HorlogeFigee horloge = new HorlogeFigee(LocalDate.of(2026, 5, 31));
        ServiceCampagne campagnes = new ServiceCampagne(new CampagneDao(source), passages, horloge);
        Campagne ens = campagnes.creerCampagne("Suivi ENS", 2026, null);
        campagnes.rattacherPassage(service.listerPassages(ID_USER).getFirst().idPassage(), ens.id());

        // Les 4 autres nuits n'ont pas de campagne : elles ne sont retenues par aucun filtre de campagne.
        assertThat(serviceAvec(campagnes, horloge).listerPassages(ID_USER, FiltresMultisite.parCampagne("i")))
                .as("« i » figure dans « Suivi ENS » mais les non rattachées restent dehors")
                .singleElement()
                .extracting(LignePassage::campagne)
                .isEqualTo("Suivi ENS");
    }

    /// Même service, mais avec la feature `campagne` **active**.
    private ServiceMultisite serviceAvec(ServiceCampagne campagnes, HorlogeFigee horloge) {
        return new ServiceMultisite(
                injecteur.getInstance(SiteDao.class),
                injecteur.getInstance(PointDao.class),
                injecteur.getInstance(PassageDao.class),
                injecteur.getInstance(ReleveTraitementDao.class),
                injecteur.getInstance(ResultatsIdentificationDao.class),
                injecteur.getInstance(PointCommuneDao.class),
                Optional.of(campagnes),
                horloge);
    }

    @Test
    @DisplayName("#2355 : feature campagne coupée, la colonne reste vide sans rien casser")
    void campagne_desactivee_colonne_vide() {
        assertThat(service.listerPassages(ID_USER))
                .extracting(LignePassage::campagne)
                .containsOnlyNulls();
    }

    @Test
    @DisplayName("Chaque ligne porte statut et verdict (verdict null si non vérifié)")
    void chaque_ligne_porte_statut_et_verdict() {
        List<LignePassage> lignes = service.listerPassages(ID_USER);

        assertThat(lignes.get(2).statut()).isEqualTo(StatutWorkflow.IMPORTE);
        assertThat(lignes.get(2).verdict())
                .as("passage non vérifié → verdict null")
                .isNull();
        assertThat(lignes.get(4).verdict()).isEqualTo(Verdict.A_JETER);
    }

    @Test
    @DisplayName("Un utilisateur sans site obtient une vue vide")
    void utilisateur_sans_site_vue_vide() {
        assertThat(service.listerPassages("inconnu")).isEmpty();
    }

    // --- Filtres ---

    @Test
    @DisplayName("Filtre par site : ne conserve que les passages du carré demandé")
    void filtre_par_site() {
        List<LignePassage> lignes = service.listerPassages(ID_USER, FiltresMultisite.parSite("640380"));

        assertThat(lignes).hasSize(3).allMatch(l -> l.numeroCarre().equals("640380"));
    }

    @Test
    @DisplayName("Filtre par statut : ne conserve que les passages dans ce statut")
    void filtre_par_statut() {
        List<LignePassage> lignes = service.listerPassages(ID_USER, FiltresMultisite.parStatut(StatutWorkflow.VERIFIE));

        assertThat(lignes).hasSize(2).allMatch(l -> l.statut() == StatutWorkflow.VERIFIE);
    }

    @Test
    @DisplayName("Filtre par verdict : ne conserve que les passages portant ce verdict")
    void filtre_par_verdict() {
        List<LignePassage> lignes = service.listerPassages(ID_USER, FiltresMultisite.parVerdict(Verdict.OK));

        assertThat(lignes)
                .hasSize(2)
                .extracting(LignePassage::numeroCarre, LignePassage::annee)
                .containsExactly(tuple("640380", 2025), tuple("640381", 2026));
    }

    @Test
    @DisplayName("Filtre par année : ne conserve que les passages de l'année demandée")
    void filtre_par_annee() {
        assertThat(service.listerPassages(ID_USER, FiltresMultisite.parAnnee(2026)))
                .hasSize(4);
        assertThat(service.listerPassages(ID_USER, FiltresMultisite.parAnnee(2025)))
                .hasSize(1);
    }

    @Test
    @DisplayName("Filtres combinés (ET logique) : site ET année")
    void filtres_combines() {
        List<LignePassage> lignes =
                service.listerPassages(ID_USER, new FiltresMultisite("640380", null, null, 2026, null, null));

        assertThat(lignes).hasSize(2).allMatch(l -> l.numeroCarre().equals("640380") && l.annee() == 2026);
    }

    @Test
    @DisplayName("Vue de la saison courante : année lue de l'horloge (2026)")
    void vue_saison_courante() {
        assertThat(service.listerPassagesDeLaSaison(ID_USER)).hasSize(4).allMatch(l -> l.annee() == 2026);
    }

    // --- Tri ---

    @Test
    @DisplayName("Tri par année : la plus ancienne d'abord")
    void tri_par_annee() {
        List<LignePassage> lignes = service.listerPassages(ID_USER, FiltresMultisite.aucun(), TriMultisite.PAR_ANNEE);

        assertThat(lignes.get(0).annee()).isEqualTo(2025);
    }

    @Test
    @DisplayName("Tri par statut : suit l'ordre de progression du workflow")
    void tri_par_statut() {
        List<LignePassage> lignes = service.listerPassages(ID_USER, FiltresMultisite.aucun(), TriMultisite.PAR_STATUT);

        assertThat(lignes)
                .extracting(LignePassage::statut)
                .containsExactly(
                        StatutWorkflow.IMPORTE,
                        StatutWorkflow.TRANSFORME,
                        StatutWorkflow.VERIFIE,
                        StatutWorkflow.VERIFIE,
                        StatutWorkflow.DEPOSE);
    }

    @Test
    @DisplayName("Tri par verdict : passages non vérifiés (verdict null) en dernier")
    void tri_par_verdict_nulls_en_dernier() {
        List<LignePassage> lignes = service.listerPassages(ID_USER, FiltresMultisite.aucun(), TriMultisite.PAR_VERDICT);

        assertThat(lignes.get(0).verdict()).isEqualTo(Verdict.OK);
        assertThat(lignes.get(lignes.size() - 1).verdict())
                .as("verdict null trié en dernier")
                .isNull();
    }

    @Test
    @DisplayName("#4271 : les deux lectures de l'écran lisent par LOT, pas site par site")
    void l_ecran_lit_par_lot() {
        PointDao points = Mockito.spy(injecteur.getInstance(PointDao.class));
        PassageDao passages = Mockito.spy(injecteur.getInstance(PassageDao.class));
        ServiceMultisite surveille = new ServiceMultisite(
                injecteur.getInstance(SiteDao.class),
                points,
                passages,
                injecteur.getInstance(ReleveTraitementDao.class),
                injecteur.getInstance(ResultatsIdentificationDao.class),
                injecteur.getInstance(PointCommuneDao.class),
                Optional.empty(),
                new HorlogeFigee(LocalDate.of(2026, 5, 31)));
        Mockito.clearInvocations(points, passages);

        surveille.listerPassages(ID_USER, FiltresMultisite.aucun(), TriMultisite.PAR_ANNEE);
        surveille.agregerPourCarte(ID_USER);

        // ⚠️ Le garde compte des REQUÊTES, pas des millisecondes : un butoir en temps se noierait dans la
        // variance de la machine. Le défaut mesuré (#4271) : une requête par site pour ses points, puis
        // une par point pour ses passages, dans les DEUX lectures - 160 ms à soixante carrés, 360 ms à
        // cent cinquante, là où la lecture par lot ne bouge pas.
        //
        // Deux appels attendus par lecture par lot : `listerPassages` et `agregerPourCarte` parcourent
        // la même topologie, chacune pour son compte.
        Mockito.verify(points, Mockito.never()).findBySite(Mockito.any());
        Mockito.verify(passages, Mockito.never()).findByPoint(Mockito.any());
        Mockito.verify(points, Mockito.times(2)).findParSites(Mockito.any());
        Mockito.verify(passages, Mockito.times(2)).findParPoints(Mockito.any());
    }
}
