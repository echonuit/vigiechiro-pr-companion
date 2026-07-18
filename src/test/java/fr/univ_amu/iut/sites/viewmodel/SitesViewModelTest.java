package fr.univ_amu.iut.sites.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.RapportSynchro;
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
import fr.univ_amu.iut.sites.model.SynchronisationSites;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
        viewModel = new SitesViewModel(service, passageDao, horloge, liens, ID_USER, Optional.empty());
    }

    @Test
    @DisplayName("#1045 : passerelle absente → synchronisation indisponible, appel sans effet ni erreur")
    void synchro_indisponible() {
        assertThat(viewModel.peutRecuperer()).isFalse();
        assertThat(viewModel.synchroniserDepuisVigieChiro()).isEmpty();
    }

    @Test
    @DisplayName("#1045 : la synchronisation recharge les cartes (sites créés par le pull) et pose le message")
    void synchro_recharge_et_message() {
        SynchronisationSites sync = mock(SynchronisationSites.class);
        when(sync.synchroniser()).thenAnswer(invocation -> {
            // Le pull crée un site local (comportement réel de RapprochementSites) : le rechargement doit le voir.
            service.creerSite("640380", "Étang de Berre", Protocole.STANDARD, null, ID_USER);
            return List.of(new RapportSynchro("sites", 3));
        });
        SitesViewModel vm =
                new SitesViewModel(service, passageDao, new HorlogeFigee(JOUR_FIXE), liens, ID_USER, Optional.of(sync));

        assertThat(vm.peutRecuperer()).isTrue();
        // Parcours du déport #1212 : le travail (pull + relecture) se joue hors du fil JavaFX,
        // l'application des cartes et du message sur le fil JavaFX.
        vm.appliquerSynchro(vm.synchroniserEtRecharger());

        assertThat(vm.cartes())
                .as("le rechargement voit le site créé par le pull")
                .hasSize(1);
        assertThat(vm.messageSynchroProperty().get()).isEqualTo("3 sites synchronisés depuis Vigie-Chiro.");
    }

    @Test
    @DisplayName("#1808 : le pull annonce aussi les passages rapatriés (squelettes de nuits, #1662)")
    void synchro_rapatrie_sites_et_passages() {
        SynchronisationSites sync = mock(SynchronisationSites.class);
        // Le bouton rejoue désormais la structure des sites PUIS ses dépendants : le rapport des passages
        // (ServiceReconstructionPassages, phase DEPENDANTE) s'ajoute à celui des sites.
        when(sync.synchroniser())
                .thenReturn(List.of(new RapportSynchro("sites", 1), new RapportSynchro("passage(s) rapatrié(s)", 5)));
        SitesViewModel vm =
                new SitesViewModel(service, passageDao, new HorlogeFigee(JOUR_FIXE), liens, ID_USER, Optional.of(sync));

        vm.appliquerSynchro(vm.synchroniserEtRecharger());

        assertThat(vm.messageSynchroProperty().get())
                .isEqualTo("1 site synchronisé, 5 passage(s) rapatrié(s) depuis Vigie-Chiro.");
    }

    @Test
    @DisplayName("#1045 : rien récupéré (hors connexion, aucun site distant) → message explicite, pas un silence")
    void synchro_sans_resultat() {
        SynchronisationSites sync = mock(SynchronisationSites.class);
        when(sync.synchroniser()).thenReturn(List.of());
        SitesViewModel vm =
                new SitesViewModel(service, passageDao, new HorlogeFigee(JOUR_FIXE), liens, ID_USER, Optional.of(sync));

        vm.appliquerSynchro(vm.synchroniserEtRecharger());

        assertThat(vm.messageSynchroProperty().get()).contains("Aucun site distant");
    }

    @Test
    @DisplayName("#1212 : un échec de la synchronisation est routé vers son message, jamais un silence")
    void erreur_de_synchro_surfacee() {
        viewModel.signalerErreurSynchro(new RuntimeException("Vigie-Chiro injoignable"));

        assertThat(viewModel.messageSynchroProperty().get())
                .contains("a échoué")
                .contains("Vigie-Chiro injoignable");
    }

    @Test
    @DisplayName("#795 : un échec de chargement des sites est routé vers messageErreur (au lieu d'être avalé)")
    void erreur_de_chargement_surfacee() {
        ServiceSites serviceKo = mock(ServiceSites.class);
        when(serviceKo.listerSites(ID_USER)).thenThrow(new RuntimeException("base indisponible"));
        SitesViewModel vm = new SitesViewModel(
                serviceKo, passageDao, new HorlogeFigee(JOUR_FIXE), liens, ID_USER, Optional.empty());

        vm.rafraichir();

        assertThat(vm.messageErreurProperty().get())
                .as("l'échec est surfacé au lieu de remonter non capturé")
                .contains("base indisponible");
    }

    @Test
    @DisplayName("Sans aucun site, le ViewModel signale l'état vide")
    void etat_vide_sans_site() {
        viewModel.rafraichir();

        assertThat(viewModel.cartes()).isEmpty();
        assertThat(viewModel.videProperty().get()).isTrue();
    }

    @Test
    @DisplayName("La carte reflète le statut plateforme : absent / enregistré / verrouillé")
    void carte_reflete_le_statut_plateforme() {
        Site enregistre = service.creerSite("640380", "Enregistré", Protocole.STANDARD, null, ID_USER);
        Site verrouille = service.creerSite("111111", "Verrouillé", Protocole.STANDARD, null, ID_USER);
        service.creerSite("810123", "Local seul", Protocole.STANDARD, null, ID_USER);
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(enregistre.id()), "obj-1", false));
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(verrouille.id()), "obj-2", true));

        viewModel.rafraichir();

        assertThat(statutDe("640380")).isEqualTo(StatutPlateforme.ENREGISTRE);
        assertThat(statutDe("111111")).isEqualTo(StatutPlateforme.VERROUILLE);
        assertThat(statutDe("810123")).isEqualTo(StatutPlateforme.ABSENT);
    }

    private StatutPlateforme statutDe(String numeroCarre) {
        return viewModel.cartes().stream()
                .filter(carte -> carte.site().numeroCarre().equals(numeroCarre))
                .map(CarteSite::statutPlateforme)
                .findFirst()
                .orElseThrow();
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
    @DisplayName("#1750 : la carte résume les points rapatriés non utilisés au lieu de les égrener")
    void carte_resume_les_points_rapatries_non_utilises() {
        Site site = service.creerSite("130711", "Point Fixe", Protocole.STANDARD, null, ID_USER);
        PointDEcoute z41 = service.ajouterPoint(site.id(), "Z41", null, null, null); // manuel, utilisé
        service.ajouterPointSynchronise(site.id(), "Z1", null, null, null); // rapatrié, non utilisé
        service.ajouterPointSynchronise(site.id(), "Z2", null, null, null); // rapatrié, non utilisé
        insererPassage(z41, 1, "2026-05-29", Verdict.OK);

        viewModel.rafraichir();

        CarteSite carte = viewModel.cartes().getFirst();
        assertThat(carte.nombrePoints())
                .as("le compte total reste factuel : 3 points existent")
                .isEqualTo(3);
        assertThat(carte.codesPoints())
                .as("le point utilisé est mis en avant ; les 2 rapatriés non utilisés sont résumés")
                .isEqualTo("Z41  (+ 2 rapatriés)");
    }

    @Test
    @DisplayName("#1750 : un point ajouté à la main reste égrené même sans passage")
    void carte_garde_les_points_manuels_meme_sans_passage() {
        Site site = service.creerSite("130711", "Point Fixe", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(site.id(), "M1", null, null, null); // manuel, non utilisé
        service.ajouterPointSynchronise(site.id(), "Z1", null, null, null); // rapatrié, non utilisé

        viewModel.rafraichir();

        assertThat(viewModel.cartes().getFirst().codesPoints())
                .as("M1 (manuel) reste ; seul Z1 (rapatrié) est résumé")
                .isEqualTo("M1  (+ 1 rapatrié)");
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
