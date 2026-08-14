package fr.univ_amu.iut.sites.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FournisseurToken;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.PublicationPoint;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.PointPublieDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
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
    private SourceDeDonnees source;
    private PassageDao passageDao;
    private PointDao pointDao;
    private SiteDetailViewModel viewModel;
    private LienVigieChiroDao liens;
    private PointPublieDao publies;
    private HorlogeFigee horloge;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        SiteDao siteDao = new SiteDao(source);
        pointDao = new PointDao(source);
        passageDao = new PassageDao(source);
        new EnregistreurDao(source).insert(new Enregistreur("1925492", "V1.01", null));
        horloge = new HorlogeFigee(LocalDate.of(2026, 5, 31));
        service = new ServiceSites(siteDao, pointDao, passageDao, horloge, new PointCommuneDao(source), () -> {});
        liens = new LienVigieChiroDao(source);
        publies = new PointPublieDao(source);
        viewModel = avecPublication(publicationAvecJeton("jeton-de-test"));
    }

    /// ViewModel muni (ou non) de la publication de points (#3458).
    private SiteDetailViewModel avecPublication(Optional<PublicationPoint> publication) {
        return new SiteDetailViewModel(
                service,
                passageDao,
                horloge,
                new PortailVigieChiro(liens),
                liens,
                new PublicationDepuisLaFiche(publies, liens, publication));
    }

    /// Publication réelle, branchée sur un fournisseur de jeton contrôlé : `null` simule « pas connecté ».
    /// Aucun appel réseau n'a lieu tant qu'on ne publie pas ; ces tests s'arrêtent au **garde**.
    private Optional<PublicationPoint> publicationAvecJeton(String jeton) {
        FournisseurToken token = () -> Optional.ofNullable(jeton);
        return Optional.of(new PublicationPoint(new ClientVigieChiro(token), publies, token));
    }

    @Test
    @DisplayName("#734 : le statut plateforme suit les correspondances (absent, enregistré, verrouillé)")
    void statut_plateforme_suit_les_correspondances() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        viewModel.chargerSite(site);
        assertThat(viewModel.statutPlateformeProperty().get())
                .as("aucune correspondance : le carré n'est pas connu de la plateforme")
                .isEqualTo(StatutPlateforme.ABSENT);

        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(site.id()), "6a4961f5", false));
        viewModel.rafraichir();
        assertThat(viewModel.statutPlateformeProperty().get())
                .as("correspondance non verrouillée : enregistré, mais le dépôt reste impossible")
                .isEqualTo(StatutPlateforme.ENREGISTRE);
        assertThat(viewModel.statutPlateformeProperty().get().depotPossible()).isFalse();

        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(site.id()), "6a4961f5", true));
        viewModel.rafraichir();
        assertThat(viewModel.statutPlateformeProperty().get())
                .as("correspondance verrouillée : c'est l'état FAVORABLE, celui qui autorise le dépôt")
                .isEqualTo(StatutPlateforme.VERROUILLE);
        assertThat(viewModel.statutPlateformeProperty().get().depotPossible()).isTrue();
    }

    @Test
    @DisplayName("#1124 : lienPortail vide tant que le site n'est pas rattaché, URL après rattachement")
    void lien_portail_suit_le_rattachement() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        viewModel.chargerSite(site);
        assertThat(viewModel.lienPortailProperty().get())
                .as("site non rattaché")
                .isEmpty();

        liens.upsert(
                new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(site.id()), "5eb12120cbe7410011f0a97f"));
        viewModel.rafraichir();

        assertThat(viewModel.lienPortailProperty().get())
                .isEqualTo("https://vigiechiro.herokuapp.com/#/sites/5eb12120cbe7410011f0a97f");
    }

    @Test
    @CasDeRecette("S1-18")
    @DisplayName("La fiche d'identité reprend carré, département dérivé, protocole et création")
    void fiche_identite() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, "Aix", ID_USER);

        viewModel.chargerSite(site);

        assertThat(viewModel.titreProperty().get()).isEqualTo("Carré 640380 - Étang");
        assertThat(viewModel.numeroCarreProperty().get()).isEqualTo("640380");
        assertThat(viewModel.departementProperty().get()).isEqualTo("64");
        assertThat(viewModel.protocoleProperty().get()).isEqualTo("PointFixeStandard");
        assertThat(viewModel.sousTitreProperty().get()).contains("Aix").contains("Protocole");
    }

    @Test
    @CasDeRecette("S1-20")
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
        // Un seul point géolocalisé (B2 sans GPS) → pas de distance au plus proche.
        assertThat(carteA1.distanceProche()).isEmpty();
    }

    @Test
    @DisplayName("Distance au point le plus proche et alerte si sous le seuil (#154)")
    void distance_et_alerte_proximite() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        // A1 et B2 séparés d'environ 100 m (≈ 0,0009° de latitude) → sous le seuil de 200 m.
        service.ajouterPoint(site.id(), "A1", 43.5000, 5.4000, null);
        service.ajouterPoint(site.id(), "B2", 43.5009, 5.4000, null);
        // C3 éloigné (≈ 1 km) → au-dessus du seuil.
        service.ajouterPoint(site.id(), "C3", 43.5090, 5.4000, null);

        viewModel.chargerSite(site);

        CartePoint a1 = viewModel.points().stream()
                .filter(c -> c.point().code().equals("A1"))
                .findFirst()
                .orElseThrow();
        assertThat(a1.distanceProche()).hasValueSatisfying(d -> assertThat(d).isCloseTo(100, within(10.0)));
        assertThat(a1.tropProche()).isTrue();

        CartePoint c3 = viewModel.points().stream()
                .filter(c -> c.point().code().equals("C3"))
                .findFirst()
                .orElseThrow();
        // Le plus proche de C3 est B2 (≈ 900 m) → au-dessus du seuil, pas d'alerte.
        assertThat(c3.tropProche()).isFalse();
    }

    @Test
    @CasDeRecette("S1-21")
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
        assertThat(viewModel.passagesDeLAnneeProperty().get())
                .as("#2036 : le ViewModel dit « à vérifier » mais ne décide plus du rendu - pas de glyphe")
                .contains("dont 1 à vérifier")
                .doesNotContain("⚠");
    }

    @Test
    @CasDeRecette("S1-19")
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
    @DisplayName("#1738 : les points RAPATRIÉS non utilisés sont masqués par défaut, révélables à la demande")
    void points_rapatries_non_utilises_masques() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        PointDEcoute utilise = service.ajouterPoint(site.id(), "A1", null, null, null);
        service.ajouterPointSynchronise(site.id(), "Z2", null, null, null); // rapatrié, jamais utilisé
        service.ajouterPointSynchronise(site.id(), "Z3", null, null, null); // idem
        insererPassage(utilise, 1, "2026-04-22", Verdict.OK);

        viewModel.chargerSite(site);

        // Par défaut : le point qui SERT est affiché ; les deux rapatriés non utilisés sont masqués.
        assertThat(viewModel.points()).extracting(c -> c.point().code()).containsExactly("A1");
        assertThat(viewModel.nombrePointsMasquesProperty().get()).isEqualTo(2);

        // Révélation : tous les points rapatriés apparaissent, l'ordre (par code) étant préservé.
        viewModel.afficherTousLesPointsProperty().set(true);
        assertThat(viewModel.points()).extracting(c -> c.point().code()).containsExactly("A1", "Z2", "Z3");
        assertThat(viewModel.nombrePointsMasquesProperty().get())
                .as("le compte des masqués ne dépend pas de l'affichage")
                .isEqualTo(2);

        // Repli : retour au seul point affiché par défaut.
        viewModel.afficherTousLesPointsProperty().set(false);
        assertThat(viewModel.points()).extracting(c -> c.point().code()).containsExactly("A1");
    }

    @Test
    @DisplayName("#1738 : un point AJOUTÉ À LA MAIN reste visible même sans passage (seuls les rapatriés se masquent)")
    void point_ajoute_a_la_main_reste_visible() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(site.id(), "A1", null, null, null); // manuel, jamais utilisé
        service.ajouterPointSynchronise(site.id(), "Z2", null, null, null); // rapatrié, jamais utilisé

        viewModel.chargerSite(site);

        assertThat(viewModel.points())
                .as("le point manuel A1 reste affiché ; seul le rapatrié Z2 est masqué")
                .extracting(c -> c.point().code())
                .containsExactly("A1");
        assertThat(viewModel.nombrePointsMasquesProperty().get()).isEqualTo(1);
    }

    @Test
    @DisplayName("#3458 : une carte sait si SON point a été publié, sans une requête par carte")
    void la_carte_porte_l_etat_publie() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        PointDEcoute a1 = service.ajouterPoint(site.id(), "A1", 43.5, 5.4, null);
        service.ajouterPoint(site.id(), "B2", 43.6, 5.5, null);
        publies.marquer(a1.id());

        viewModel.chargerSite(site);

        assertThat(viewModel.points())
                .as("l'état vient de la table de présence, pas du drapeau `synchronise` qui dit l'inverse")
                .extracting(c -> c.point().code(), CartePoint::publie)
                .containsExactlyInAnyOrder(tuple("A1", true), tuple("B2", false));
    }

    @Test
    @DisplayName("#3458 : un carré VERROUILLÉ ne grise PAS l'action, même s'il refusera son propriétaire")
    void un_carre_verrouille_ne_grise_pas_la_publication() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        PointDEcoute a1 = service.ajouterPoint(site.id(), "A1", 43.5, 5.4, null);
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(site.id()), "6a4961f5", true));
        viewModel.chargerSite(site);

        // `PUT /sites/{id}/localites` refuse le PROPRIÉTAIRE d'un carré verrouillé, mais accepte un
        // participant VALIDÉ sur le protocole, verrouillé ou non. Les liens de site venant de
        // `/moi/participations` et non de `/moi/sites` (#718), Companion ne sait pas dans quel cas il est.
        // Griser ici bloquerait le participant validé, à qui la plateforme dit oui : le refus se rend
        // compte, il ne se devine pas.
        assertThat(viewModel.empechementPublication(carteDe(a1)))
                .as("verrouillé n'est PAS un empêchement connu de nous")
                .isEmpty();
        assertThat(viewModel.statutPlateformeProperty().get())
                .as("et pourtant le statut, lui, dit bien verrouillé : les deux lectures diffèrent")
                .isEqualTo(StatutPlateforme.VERROUILLE);
    }

    @Test
    @DisplayName("#3458 : les empêchements se nomment dans l'ordre où ils bloquent le chemin")
    void les_empechements_disent_quoi_faire() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        PointDEcoute sansGps = service.ajouterPoint(site.id(), "A1", null, null, null);

        viewModel = avecPublication(publicationAvecJeton(null));
        viewModel.chargerSite(site);
        assertThat(viewModel.empechementPublication(carteDe(sansGps)))
                .as("sans jeton, parler de coordonnées serait du bruit : c'est la connexion qui bloque")
                .hasValueSatisfying(motif -> assertThat(motif).contains("Connectez-vous"));

        viewModel = avecPublication(publicationAvecJeton("jeton-de-test"));
        viewModel.chargerSite(site);
        assertThat(viewModel.empechementPublication(carteDe(sansGps)))
                .as("connecté, mais le carré n'est pas déclaré : rien à quoi rattacher le point")
                .hasValueSatisfying(motif -> assertThat(motif).contains("pas encore enregistré"));

        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(site.id()), "6a4961f5", false));
        viewModel.rafraichir();
        assertThat(viewModel.empechementPublication(carteDe(sansGps)))
                .as("reste le point lui-même : une localité Vigie-Chiro exige des coordonnées")
                .hasValueSatisfying(motif -> assertThat(motif).contains("coordonnées"));
    }

    @Test
    @DisplayName("#3458 : sans la feature installée, la fiche n'offre aucune publication")
    void sans_la_feature_la_fiche_n_offre_rien() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        PointDEcoute a1 = service.ajouterPoint(site.id(), "A1", 43.5, 5.4, null);
        viewModel = avecPublication(Optional.empty());
        viewModel.chargerSite(site);

        assertThat(viewModel.publicationInstallee()).isFalse();
        assertThat(viewModel.empechementPublication(carteDe(a1))).isNotEmpty();
        assertThatThrownBy(() -> viewModel.publier(carteDe(a1)))
                .as("publier sans garde est une faute de câblage, pas une issue à rendre compte")
                .isInstanceOf(IllegalStateException.class);
    }

    /// La carte du point de code donné, telle que le ViewModel vient de la composer.
    private CartePoint carteDe(PointDEcoute point) {
        return viewModel.points().stream()
                .filter(carte -> carte.point().id().equals(point.id()))
                .findFirst()
                .orElseThrow();
    }

    /// `surLePoint` : ces tests construisent plusieurs points eux-mêmes et rattachent leur nuit à l'un
    /// d'eux. La fixture résout le point par son code ; ici c'est un identifiant qu'on lui donne (#2989).
    private void insererPassage(PointDEcoute point, int numeroPassage, String date, Verdict verdict) {
        JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .surLePoint(point.id())
                .enregistreur("1925492")
                .nuit(numeroPassage, 2026, date)
                .heures("21:00:00", "05:00:00")
                .statut(StatutWorkflow.TRANSFORME)
                .verdict(verdict)
                .semerPassage();
    }
}
