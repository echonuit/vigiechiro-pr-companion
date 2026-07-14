package fr.univ_amu.iut.recherche.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.ResultatRecherche;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.TypeResultat;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.multisite.model.EtatAnalyse;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.validation.model.EspeceObservee;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAnalyseDao;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de [ServiceRechercheGlobale] (#144) avec services mockés : correspondance sites/points/passages,
/// tolérance casse/accents, requête vide, et plafond par type.
class ServiceRechercheGlobaleTest {

    private static final String UTILISATEUR = "u-1";

    private ServiceSites services;
    private ServiceMultisite multisite;
    private ProjectionsAnalyseDao projections;
    private ServiceRechercheGlobale recherche;

    private static EspeceObservee espece(String code, String latin, String vern, long idPassage) {
        return new EspeceObservee(code, latin, vern, "Chiroptères", idPassage, "640380", "A1", 2026, 2, "2026-06-21");
    }

    private static Site site(Long id, String numeroCarre, String nom) {
        return new Site(id, numeroCarre, nom, Protocole.STANDARD, null, "2026-01-01", UTILISATEUR);
    }

    private static LignePassage passage(Long id, String carre, String point, int annee, int numero, String date) {
        return new LignePassage(
                id, carre, point, annee, numero, date, StatutWorkflow.DEPOSE, Verdict.OK, EtatAnalyse.SANS_OBJET, null);
    }

    @BeforeEach
    void preparer() {
        services = mock(ServiceSites.class);
        multisite = mock(ServiceMultisite.class);
        projections = mock(ProjectionsAnalyseDao.class);
        lenient().when(services.listerSites(anyString())).thenReturn(List.of());
        lenient()
                .when(services.listerPoints(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(List.of());
        lenient().when(multisite.listerPassages(anyString())).thenReturn(List.of());
        lenient().when(projections.especesObserveesParUtilisateur(anyString())).thenReturn(List.of());
        recherche = new ServiceRechercheGlobale(services, multisite, projections, UTILISATEUR);
    }

    @Test
    @DisplayName("une requête vide ou blanche ne retourne aucun résultat")
    void requete_vide_aucun_resultat() {
        assertThat(recherche.rechercher("")).isEmpty();
        assertThat(recherche.rechercher("   ")).isEmpty();
    }

    @Test
    @DisplayName("trouve un site par nom, insensible à la casse et aux accents")
    void trouve_site_par_nom_tolerant() {
        when(services.listerSites(UTILISATEUR)).thenReturn(List.of(site(1L, "640380", "Étang de la Tuilière")));

        List<ResultatRecherche> resultats = recherche.rechercher("tuiliere");

        assertThat(resultats).singleElement().satisfies(r -> {
            assertThat(r.type()).isEqualTo(TypeResultat.SITE);
            assertThat(r.libelle()).isEqualTo("Étang de la Tuilière");
            assertThat(r.numeroCarre()).isEqualTo("640380");
        });
    }

    @Test
    @DisplayName("trouve un point par code, avec le contexte du site")
    void trouve_point_par_code() {
        when(services.listerSites(UTILISATEUR)).thenReturn(List.of(site(1L, "640380", "Étang")));
        when(services.listerPoints(1L)).thenReturn(List.of(new PointDEcoute(9L, "A1", 43.3, -0.3, "lisière", 1L)));

        List<ResultatRecherche> points = recherche.rechercher("a1").stream()
                .filter(r -> r.type() == TypeResultat.POINT)
                .toList();

        assertThat(points).singleElement().satisfies(r -> {
            assertThat(r.libelle()).isEqualTo("640380 / A1");
            assertThat(r.numeroCarre()).isEqualTo("640380");
            assertThat(r.codePoint()).isEqualTo("A1");
        });
    }

    @Test
    @DisplayName("trouve un passage par n° de passage, date ou code, en portant l'idPassage")
    void trouve_passage_par_numero_et_date() {
        when(multisite.listerPassages(UTILISATEUR))
                .thenReturn(List.of(passage(42L, "640380", "A1", 2026, 12, "2026-06-21")));

        assertThat(recherche.rechercher("12")).anySatisfy(r -> {
            assertThat(r.type()).isEqualTo(TypeResultat.PASSAGE);
            assertThat(r.idPassage()).isEqualTo(42L);
        });
        assertThat(recherche.rechercher("2026-06")).anyMatch(r -> r.type() == TypeResultat.PASSAGE);
    }

    @Test
    @DisplayName("les points sont plafonnés ; listerPoints n'est plus appelé une fois le plafond atteint")
    void points_plafonnes_et_court_circuites() {
        // Beaucoup de sites (noms qui ne matchent PAS « zone ») ayant chacun un point dont la description
        // matche : seuls les points correspondent, donc seul leur plafond peut déclencher le court-circuit.
        List<Site> sites = IntStream.range(0, ServiceRechercheGlobale.MAX_PAR_TYPE + 5)
                .mapToObj(i -> site((long) i, "64038" + i, "Site " + i))
                .toList();
        when(services.listerSites(UTILISATEUR)).thenReturn(sites);
        when(services.listerPoints(anyLong()))
                .thenReturn(List.of(new PointDEcoute(1L, "A1", 43.0, -0.3, "zone humide", 1L)));

        long points = recherche.rechercher("zone").stream()
                .filter(r -> r.type() == TypeResultat.POINT)
                .count();

        assertThat(points).isEqualTo(ServiceRechercheGlobale.MAX_PAR_TYPE);
        // Court-circuit (#312 P3) : une fois 8 points trouvés, on n'interroge plus listerPoints.
        verify(services, times(ServiceRechercheGlobale.MAX_PAR_TYPE)).listerPoints(anyLong());
    }

    @Test
    @DisplayName("le nombre de passages retournés est plafonné par type")
    void passages_plafonnes() {
        List<LignePassage> beaucoup = IntStream.range(0, ServiceRechercheGlobale.MAX_PAR_TYPE + 5)
                .mapToObj(i -> passage((long) i, "640380", "A1", 2026, i, "2026-06-21"))
                .toList();
        when(multisite.listerPassages(UTILISATEUR)).thenReturn(beaucoup);

        long passages = recherche.rechercher("640380").stream()
                .filter(r -> r.type() == TypeResultat.PASSAGE)
                .count();

        assertThat(passages).isEqualTo(ServiceRechercheGlobale.MAX_PAR_TYPE);
    }

    @Test
    @DisplayName("#323 : trouve une espèce par nom vernaculaire/latin/code, et porte l'idPassage à ouvrir")
    void trouve_espece_par_nom() {
        when(projections.especesObserveesParUtilisateur(UTILISATEUR))
                .thenReturn(List.of(espece("Pippip", "Pipistrellus pipistrellus", "Pipistrelle commune", 42L)));

        // par nom vernaculaire (tolérant casse/accents), par nom latin, par code.
        for (String requete : List.of("pipistrelle", "pipistrellus", "pippip")) {
            assertThat(recherche.rechercher(requete))
                    .as("requête « %s »", requete)
                    .anySatisfy(r -> {
                        assertThat(r.type()).isEqualTo(TypeResultat.ESPECE);
                        assertThat(r.libelle()).contains("Pipistrelle commune").contains("Pippip");
                        assertThat(r.details())
                                .as("le taxon parent ouvre le détail, comme la colonne « Taxon parent » du portail")
                                .startsWith("Chiroptères · ");
                        assertThat(r.idPassage())
                                .as("ouvre le passage où l'espèce a été observée")
                                .isEqualTo(42L);
                        assertThat(r.numeroCarre()).isEqualTo("640380");
                        assertThat(r.codePoint()).isEqualTo("A1");
                    });
        }
    }

    @Test
    @DisplayName("#323 : une espèce vue dans plusieurs passages donne une entrée par passage, plafonnée")
    void especes_une_entree_par_passage_plafonnees() {
        List<EspeceObservee> beaucoup = IntStream.range(0, ServiceRechercheGlobale.MAX_PAR_TYPE + 5)
                .mapToObj(i -> espece("Pippip", "Pipistrellus pipistrellus", "Pipistrelle commune", (long) i))
                .toList();
        when(projections.especesObserveesParUtilisateur(UTILISATEUR)).thenReturn(beaucoup);

        long especes = recherche.rechercher("pipistrelle").stream()
                .filter(r -> r.type() == TypeResultat.ESPECE)
                .count();

        assertThat(especes).isEqualTo(ServiceRechercheGlobale.MAX_PAR_TYPE);
    }
}
