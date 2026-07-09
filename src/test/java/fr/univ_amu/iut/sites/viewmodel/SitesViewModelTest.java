package fr.univ_amu.iut.sites.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
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

/// Tests du [SitesViewModel] sur une base SQLite jetable (`@TempDir` + [MigrationSchema]),
/// sans IHM : on vérifie la logique de présentation (cartes, compteurs, fraîcheur, sous-titre,
/// état vide). L'[HorlogeFigee] rend la fraîcheur déterministe.
class SitesViewModelTest {

    private static final String ID_USER = "u-1";
    private static final LocalDate JOUR_FIXE = LocalDate.of(2026, 5, 31);

    @TempDir
    Path dossier;

    private ServiceSites service;
    private PassageDao passageDao;
    private PointDao pointDao;
    private EnregistreurDao enregistreurDao;
    private LienVigieChiroDao liens;
    private SitesViewModel viewModel;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        SiteDao siteDao = new SiteDao(source);
        pointDao = new PointDao(source);
        passageDao = new PassageDao(source);
        enregistreurDao = new EnregistreurDao(source);
        enregistreurDao.insert(new Enregistreur("1925492", "V1.01", null));
        liens = new LienVigieChiroDao(source);
        HorlogeFigee horloge = new HorlogeFigee(JOUR_FIXE);
        service = new ServiceSites(siteDao, pointDao, passageDao, horloge);
        viewModel = new SitesViewModel(service, passageDao, horloge, liens, ID_USER);
    }

    @Test
    @DisplayName("Sans aucun site, le ViewModel signale l'état vide")
    void etat_vide_sans_site() {
        viewModel.rafraichir();

        assertThat(viewModel.cartes()).isEmpty();
        assertThat(viewModel.videProperty().get()).isTrue();
    }

    @Test
    @DisplayName("Un site relié à VigieChiro porte le flag « enregistré sur la plateforme », les autres non")
    void carte_signale_l_enregistrement_plateforme() {
        Site relie = service.creerSite("640380", "Relié", Protocole.STANDARD, null, ID_USER);
        service.creerSite("810123", "Local seul", Protocole.STANDARD, null, ID_USER);
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(relie.id()), "obj-vc-1"));

        viewModel.rafraichir();

        assertThat(viewModel.cartes())
                .filteredOn(carte -> carte.site().numeroCarre().equals("640380"))
                .singleElement()
                .extracting(CarteSite::enregistreSurPlateforme)
                .isEqualTo(true);
        assertThat(viewModel.cartes())
                .filteredOn(carte -> carte.site().numeroCarre().equals("810123"))
                .singleElement()
                .extracting(CarteSite::enregistreSurPlateforme)
                .isEqualTo(false);
    }

    @Test
    @DisplayName("Une carte agrège points, codes et compteur de passages de l'année")
    void carte_agrege_points_et_passages() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        PointDEcoute a1 = service.ajouterPoint(site.id(), "A1", 43.5, 5.4, "Chêne");
        service.ajouterPoint(site.id(), "B2", null, null, null);
        insererPassage(a1, 1, "2026-05-29", Verdict.OK);
        insererPassage(a1, 2, "2026-05-20", null);

        viewModel.rafraichir();

        assertThat(viewModel.videProperty().get()).isFalse();
        assertThat(viewModel.cartes()).hasSize(1);
        CarteSite carte = viewModel.cartes().getFirst();
        assertThat(carte.nombrePoints()).isEqualTo(2);
        assertThat(carte.codesPoints()).isEqualTo("A1 · B2");
        assertThat(carte.passagesDeLAnnee()).isEqualTo(2);
        assertThat(carte.passagesAVerifier()).isEqualTo(1);
        assertThat(carte.aDesPassagesAVerifier()).isTrue();
    }

    @Test
    @DisplayName("La fraîcheur dérive de la date du dernier passage")
    void fraicheur_du_dernier_passage() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        PointDEcoute a1 = service.ajouterPoint(site.id(), "A1", null, null, null);
        insererPassage(a1, 1, "2026-05-29", Verdict.OK);

        viewModel.rafraichir();

        assertThat(viewModel.cartes().getFirst().fraicheur()).isEqualTo(Fraicheur.FRAIS);
        assertThat(viewModel.cartes().getFirst().libelleFraicheur()).contains("il y a 2 j");
    }

    @Test
    @DisplayName("Un site sans passage est froid et marqué « jamais utilisé »")
    void site_sans_passage_est_froid() {
        Site site = service.creerSite("013570", null, Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(site.id(), "A1", null, null, null);

        viewModel.rafraichir();

        CarteSite carte = viewModel.cartes().getFirst();
        assertThat(carte.fraicheur()).isEqualTo(Fraicheur.FROID);
        assertThat(carte.passagesDeLAnnee()).isZero();
        assertThat(carte.libelleFraicheur()).isEqualTo("Aucun passage");
    }

    @Test
    @DisplayName("Le sous-titre récapitule sites et passages de l'année")
    void sous_titre_recapitulatif() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        PointDEcoute a1 = service.ajouterPoint(site.id(), "A1", null, null, null);
        insererPassage(a1, 1, "2026-05-29", Verdict.OK);

        viewModel.rafraichir();

        assertThat(viewModel.sousTitreProperty().get()).isEqualTo("1 site déclaré · 1 passage enregistré en 2026");
    }

    @Test
    @DisplayName("creerSite ajoute le site et rafraîchit la liste")
    void creer_site_rafraichit() {
        viewModel.rafraichir();

        viewModel.creerSite("752204", "ZAC Nord", Protocole.STANDARD, null);

        assertThat(viewModel.cartes()).hasSize(1);
        assertThat(viewModel.cartes().getFirst().site().numeroCarre()).isEqualTo("752204");
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
