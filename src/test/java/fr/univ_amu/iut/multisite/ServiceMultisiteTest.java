package fr.univ_amu.iut.multisite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.multisite.model.CarreAgrege;
import fr.univ_amu.iut.multisite.model.FiltresMultisite;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.PointAgrege;
import fr.univ_amu.iut.multisite.model.SavedView;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.model.TriMultisite;
import fr.univ_amu.iut.multisite.model.dao.SavedViewDao;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests du service [ServiceMultisite] sur une base SQLite jetable (`@TempDir` +
/// [MigrationSchema]), comme les `*DaoTest`. On instancie les vrais DAO des features `sites`
/// et `passage` et une [HorlogeFigee], puis on sème plusieurs sites / points /
/// passages pour vérifier l'agrégation, les filtres, le tri et le CRUD des vues sauvegardées.
///
/// Jeu de données (utilisateur `u-1`) — ordre de lecture par défaut (site, point, année,
/// n°) :
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
    private static final String SERIE = "1925492";

    @TempDir
    Path dossier;

    private ServiceMultisite service;
    private SavedViewDao savedViewDao;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));

        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        PassageDao passageDao = new PassageDao(source);
        savedViewDao = new SavedViewDao(source);
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));

        Site siteA = siteDao.insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2025-01-01", ID_USER));
        Site siteB = siteDao.insert(new Site(null, "640381", "Forêt", Protocole.STANDARD, null, "2025-01-01", ID_USER));
        PointDEcoute pa1 = pointDao.insert(new PointDEcoute(null, "A1", null, null, null, siteA.id()));
        PointDEcoute pb2 = pointDao.insert(new PointDEcoute(null, "B2", null, null, null, siteA.id()));
        PointDEcoute pBa1 = pointDao.insert(new PointDEcoute(null, "A1", null, null, null, siteB.id()));

        semerPassage(passageDao, 1, 2025, "2025-06-20", StatutWorkflow.TRANSFORME, Verdict.OK, pa1.id());
        semerPassage(passageDao, 1, 2026, "2026-06-20", StatutWorkflow.VERIFIE, Verdict.DOUTEUX, pa1.id());
        semerPassage(passageDao, 1, 2026, "2026-06-21", StatutWorkflow.IMPORTE, null, pb2.id());
        semerPassage(passageDao, 1, 2026, "2026-06-22", StatutWorkflow.DEPOSE, Verdict.OK, pBa1.id());
        semerPassage(passageDao, 2, 2026, "2026-08-20", StatutWorkflow.VERIFIE, Verdict.A_JETER, pBa1.id());

        service = new ServiceMultisite(
                savedViewDao, siteDao, pointDao, passageDao, new HorlogeFigee(LocalDate.of(2026, 5, 31)));
    }

    private void semerPassage(
            PassageDao dao, int numero, int annee, String date, StatutWorkflow statut, Verdict verdict, Long idPoint) {
        dao.insert(new Passage(
                null,
                numero,
                annee,
                date,
                "21:00:00",
                "05:00:00",
                null,
                statut,
                verdict,
                null,
                null,
                null,
                idPoint,
                SERIE));
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
        List<LignePassage> lignes = service.listerPassages(ID_USER, new FiltresMultisite("640380", null, null, 2026));

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

    // --- CRUD des vues sauvegardées ---

    @Test
    @DisplayName("Enregistrer puis recharger une vue restitue les mêmes critères")
    void enregistrer_puis_charger_une_vue() {
        FiltresMultisite filtres = new FiltresMultisite("640380", StatutWorkflow.VERIFIE, Verdict.DOUTEUX, 2026);

        SavedView vue = service.enregistrerVue("Mes nuits douteuses", filtres);

        assertThat(vue.id()).isNotNull();
        assertThat(service.chargerVue(vue.id())).isEqualTo(filtres);
    }

    @Test
    @DisplayName("Appliquer une vue sauvegardée rejoue ses filtres sur la vue agrégée")
    void appliquer_une_vue() {
        SavedView vue = service.enregistrerVue(
                "640380 vérifiés 2026", new FiltresMultisite("640380", StatutWorkflow.VERIFIE, null, 2026));

        List<LignePassage> lignes = service.appliquerVue(ID_USER, vue.id());

        assertThat(lignes).singleElement().satisfies(l -> {
            assertThat(l.numeroCarre()).isEqualTo("640380");
            assertThat(l.statut()).isEqualTo(StatutWorkflow.VERIFIE);
            assertThat(l.annee()).isEqualTo(2026);
        });
    }

    @Test
    @DisplayName("Lister les vues restitue celles enregistrées")
    void lister_les_vues() {
        service.enregistrerVue("Vue A", FiltresMultisite.parAnnee(2026));
        service.enregistrerVue("Vue B", FiltresMultisite.parSite("640381"));

        assertThat(service.listerVues()).extracting(SavedView::nom).containsExactlyInAnyOrder("Vue A", "Vue B");
    }

    @Test
    @DisplayName("Mettre à jour une vue change son nom et ses critères")
    void mettre_a_jour_une_vue() {
        SavedView vue = service.enregistrerVue("Brouillon", FiltresMultisite.aucun());

        service.mettreAJourVue(vue.id(), "Définitive", FiltresMultisite.parVerdict(Verdict.A_JETER));

        assertThat(service.chargerVue(vue.id())).isEqualTo(FiltresMultisite.parVerdict(Verdict.A_JETER));
        assertThat(savedViewDao.findById(vue.id()).orElseThrow().nom()).isEqualTo("Définitive");
    }

    @Test
    @DisplayName("Supprimer une vue la retire (rechargement refusé)")
    void supprimer_une_vue() {
        SavedView vue = service.enregistrerVue("À supprimer", FiltresMultisite.aucun());

        service.supprimerVue(vue.id());

        assertThatThrownBy(() -> service.chargerVue(vue.id()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    @DisplayName("Charger une vue inexistante est refusé (RegleMetierException)")
    void charger_vue_inexistante() {
        assertThatThrownBy(() -> service.chargerVue(9999L))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("introuvable");
    }
}
