package fr.univ_amu.iut.sites.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests du [SiteDetailViewModel] sur base SQLite jetable, sans IHM : fiche d'identité, cartes de
/// points, tableau des passages (tri, libellés) et garde-fous de suppression.
class SiteDetailViewModelTest {

    private static final String ID_USER = "u-1";

    @TempDir
    Path dossier;

    private ServiceSites service;
    private PassageDao passageDao;
    private PointDao pointDao;
    private SiteDetailViewModel viewModel;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        SiteDao siteDao = new SiteDao(source);
        pointDao = new PointDao(source);
        passageDao = new PassageDao(source);
        new EnregistreurDao(source).insert(new Enregistreur("1925492", "V1.01", null));
        HorlogeFigee horloge = new HorlogeFigee(LocalDate.of(2026, 5, 31));
        service = new ServiceSites(siteDao, pointDao, passageDao, horloge);
        viewModel = new SiteDetailViewModel(service, pointDao, passageDao, horloge);
    }

    @Test
    @DisplayName("La fiche d'identité reprend carré, département dérivé, protocole et création")
    void fiche_identite() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, "Aix", ID_USER);

        viewModel.chargerSite(site);

        assertThat(viewModel.titreProperty().get()).isEqualTo("Carré 640380 — Étang");
        assertThat(viewModel.numeroCarreProperty().get()).isEqualTo("640380");
        assertThat(viewModel.departementProperty().get()).isEqualTo("64");
        assertThat(viewModel.protocoleProperty().get()).isEqualTo("PointFixeStandard");
        assertThat(viewModel.sousTitreProperty().get()).contains("Aix").contains("Protocole");
    }

    @Test
    @DisplayName("Les cartes de points portent l'état GPS et le compteur de passages")
    void cartes_points_gps_et_compteur() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        PointDEcoute a1 = service.ajouterPoint(site.id(), "A1", 43.5, 5.4, "Chêne");
        service.ajouterPoint(site.id(), "B2", null, null, null);
        insererPassage(a1, 1, "2026-04-22", Verdict.OK);

        viewModel.chargerSite(site);

        assertThat(viewModel.points()).hasSize(2);
        CartePoint carteA1 = viewModel.points().getFirst();
        assertThat(carteA1.point().code()).isEqualTo("A1");
        assertThat(carteA1.gpsPresent()).isTrue();
        assertThat(carteA1.nombrePassages()).isEqualTo(1);
        assertThat(viewModel.points().get(1).gpsPresent()).isFalse();
    }

    @Test
    @DisplayName("Le tableau des passages est trié du plus récent au plus ancien")
    void passages_tries_par_date_decroissante() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        PointDEcoute a1 = service.ajouterPoint(site.id(), "A1", null, null, null);
        insererPassage(a1, 1, "2026-04-22", Verdict.OK);
        insererPassage(a1, 2, "2026-06-22", null);

        viewModel.chargerSite(site);

        assertThat(viewModel.passages()).hasSize(2);
        LignePassage premiere = viewModel.passages().getFirst();
        assertThat(premiere.date()).isEqualTo("2026-06-22");
        assertThat(premiere.codePoint()).isEqualTo("A1");
        assertThat(premiere.verdictLibelle()).isEqualTo("— à vérifier");
        assertThat(premiere.enregistreur()).isEqualTo("PR 1925492");
        assertThat(viewModel.passagesDeLAnneeProperty().get()).contains("dont 1 à vérifier");
    }

    @Test
    @DisplayName("Tant qu'un passage est rattaché, la suppression du site est impossible et refusée")
    void suppression_site_refusee_avec_passage() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        PointDEcoute a1 = service.ajouterPoint(site.id(), "A1", null, null, null);
        insererPassage(a1, 1, "2026-04-22", Verdict.OK);

        viewModel.chargerSite(site);

        assertThat(viewModel.suppressionPossibleProperty().get()).isFalse();
        assertThatThrownBy(viewModel::supprimerSite).isInstanceOf(RegleMetierException.class);
    }

    @Test
    @DisplayName("Un site sans passage peut être supprimé")
    void suppression_site_possible_sans_passage() {
        Site site = service.creerSite("013570", null, Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(site.id(), "A1", null, null, null);

        viewModel.chargerSite(site);

        assertThat(viewModel.suppressionPossibleProperty().get()).isTrue();
        viewModel.supprimerSite();
        assertThat(service.listerSites(ID_USER)).isEmpty();
    }

    @Test
    @DisplayName("Supprimer un point sans passage le retire ; avec passage, c'est refusé")
    void suppression_point_garde_fou() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        PointDEcoute a1 = service.ajouterPoint(site.id(), "A1", null, null, null);
        PointDEcoute b2 = service.ajouterPoint(site.id(), "B2", null, null, null);
        insererPassage(a1, 1, "2026-04-22", Verdict.OK);
        viewModel.chargerSite(site);

        viewModel.supprimerPoint(b2);
        assertThat(viewModel.points()).extracting(c -> c.point().code()).containsExactly("A1");

        assertThatThrownBy(() -> viewModel.supprimerPoint(a1))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("A1");
    }

    @Test
    @DisplayName("Modifier la fiche met à jour carré, nom et protocole, et recharge l'écran")
    void modifier_site_met_a_jour_la_fiche() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, "Aix", ID_USER);
        viewModel.chargerSite(site);

        viewModel.modifierSite("130010", "Calanques", Protocole.RECHERCHE, "Marseille");

        assertThat(viewModel.numeroCarreProperty().get()).isEqualTo("130010");
        assertThat(viewModel.titreProperty().get()).isEqualTo("Carré 130010 — Calanques");
        assertThat(viewModel.protocoleProperty().get()).isEqualTo("PointFixeRecherche");
        assertThat(service.listerSites(ID_USER)).singleElement().satisfies(enregistre -> {
            assertThat(enregistre.numeroCarre()).isEqualTo("130010");
            assertThat(enregistre.nomConvivial()).isEqualTo("Calanques");
            assertThat(enregistre.protocole()).isEqualTo(Protocole.RECHERCHE);
            assertThat(enregistre.commentaire()).isEqualTo("Marseille");
        });
    }

    @Test
    @DisplayName("Renommer un site sans changer son carré ne déclenche pas de faux conflit d'unicité")
    void modifier_site_meme_carre_pas_de_conflit() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        viewModel.chargerSite(site);

        viewModel.modifierSite("640380", "Nouveau nom", Protocole.STANDARD, null);

        assertThat(viewModel.titreProperty().get()).isEqualTo("Carré 640380 — Nouveau nom");
    }

    @Test
    @DisplayName("Modifier vers un carré déjà pris par un autre site est refusé (R5)")
    void modifier_site_carre_deja_pris_refuse() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        service.creerSite("130010", "Calanques", Protocole.STANDARD, null, ID_USER);
        viewModel.chargerSite(site);

        assertThatThrownBy(() -> viewModel.modifierSite("130010", "Étang", Protocole.STANDARD, null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("130010");
    }

    private void insererPassage(PointDEcoute point, int numeroPassage, String date, Verdict verdict) {
        passageDao.insert(new Passage(
                null,
                numeroPassage,
                2026,
                date,
                "21:00:00",
                "05:00:00",
                null,
                StatutWorkflow.TRANSFORME,
                verdict,
                null,
                null,
                null,
                point.id(),
                "1925492"));
    }
}
