package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Lecture d'une page du **catalogue des sites** (`GET /sites`).
///
/// Le point qui justifie ce lecteur séparé : le **verrouillage se lit**, là où le lecteur de
/// participations le **déduit** (`true` en dur, juste pour un site atteint par une participation).
/// Réutiliser ce raccourci ici annoncerait la plateforme entière comme verrouillée.
///
/// Les corps sont réduits mais fidèles à ce que rend l'API (relevé sur la collection réelle).
class SitesVigieChiroTest {

    private static final String DEUX_SITES = """
            {"_items":[
              {"_id":"55b9","titre":"Vigiechiro - Point Fixe-130711","verrouille":true,
               "observateur":"6a12",
               "localites":[{"nom":"Z1","geometries":{"geometries":[{"coordinates":[43.52,5.46]}]}},
                            {"nom":"Z2","geometries":{"geometries":[{"coordinates":[43.51,5.45]}]}}]},
              {"_id":"55c0","titre":"Vigiechiro - Routier-42","localites":[]}
            ]}""";

    @Test
    @DisplayName("Un site du catalogue rend son identifiant, son titre, son carré et ses points")
    void lit_un_site_complet() {
        List<SiteVigieChiro> sites = SitesVigieChiro.sites(DEUX_SITES);

        assertThat(sites).hasSize(2);
        SiteVigieChiro premier = sites.get(0);
        assertThat(premier.id()).isEqualTo("55b9");
        assertThat(premier.numeroCarre())
                .as("le carré se devine du titre : six chiffres isolés")
                .isEqualTo("130711");
        assertThat(premier.observateur()).isEqualTo("6a12");
        assertThat(premier.points()).extracting(PointVigieChiro::code).containsExactly("Z1", "Z2");
    }

    @Test
    @DisplayName("Le verrouillage est LU du document, jamais déduit : un site sans le champ n'est pas verrouillé")
    void le_verrouillage_se_lit_et_ne_se_deduit_pas() {
        List<SiteVigieChiro> sites = SitesVigieChiro.sites(DEUX_SITES);

        assertThat(sites.get(0).verrouille())
                .as("le document le dit : verrouillé")
                .isTrue();
        assertThat(sites.get(1).verrouille())
                .as("le document ne dit rien : on ne présume pas un carré fermé. C'est toute la différence "
                        + "avec le lecteur de participations, qui code « true » en dur (juste là-bas, faux ici)")
                .isFalse();
    }

    @Test
    @DisplayName("Un site sans observateur rend « null », et non la chaîne vide")
    void observateur_absent_rend_null() {
        // La nuance sort telle quelle en « --json » : « null » dit *absent*, la chaîne vide dirait
        // *présent mais anonyme*. Et l'identifiant sert à comparer au profil connecté pour savoir si
        // le carré est celui d'un tiers : une chaîne vide serait une valeur qui ne correspond jamais,
        // là où l'absence est une information à part entière.
        List<SiteVigieChiro> sites = SitesVigieChiro.sites(DEUX_SITES);

        assertThat(sites.get(1).observateur()).isNull();
    }

    @Test
    @DisplayName("Les coordonnées suivent l'ordre VigieChiro [lat, lon], et non le [lon, lat] GeoJSON")
    void les_coordonnees_sont_lat_puis_lon() {
        PointVigieChiro point =
                SitesVigieChiro.sites(DEUX_SITES).get(0).points().get(0);

        // 43,52 est une latitude (Aix), 5,46 une longitude : les inverser placerait le point en Somalie.
        assertThat(point.latitude()).isEqualTo(43.52);
        assertThat(point.longitude()).isEqualTo(5.46);
    }

    @Test
    @DisplayName("Une localité malformée est ignorée, le site reste lisible")
    void localite_malformee_ignoree() {
        String corps = """
                {"_items":[{"_id":"55b9","titre":"Site-130711","localites":[
                  {"nom":"Z1","geometries":{"geometries":[{"coordinates":[43.5,5.4]}]}},
                  {"nom":"SansPosition"},
                  {"geometries":{"geometries":[{"coordinates":[43.5,5.4]}]}}
                ]}]}""";

        List<SiteVigieChiro> sites = SitesVigieChiro.sites(corps);

        assertThat(sites).hasSize(1);
        assertThat(sites.get(0).points())
                .as("un point sans nom ou sans position ne se place ni sur une carte ni dans un filtre")
                .extracting(PointVigieChiro::code)
                .containsExactly("Z1");
    }

    @Test
    @DisplayName("Un site sans titre n'a pas de carré : rien n'est deviné")
    void site_sans_titre_na_pas_de_carre() {
        List<SiteVigieChiro> sites = SitesVigieChiro.sites("{\"_items\":[{\"_id\":\"55b9\"}]}");

        assertThat(sites).hasSize(1);
        assertThat(sites.get(0).numeroCarre())
                .as("le carré se lit dans le titre : sans titre, il n'y a rien à en tirer")
                .isNull();
        assertThat(sites.get(0).points()).isEmpty();
    }

    @Test
    @DisplayName("Un document sans identifiant est écarté ; un corps illisible rend une liste vide")
    void documents_inexploitables_ecartes() {
        assertThat(SitesVigieChiro.sites("{\"_items\":[{\"titre\":\"Sans identifiant\"},\"pas un objet\"]}"))
                .as("sans « _id », le site ne peut être ni désigné ni dédupliqué")
                .isEmpty();
        assertThat(SitesVigieChiro.sites("ceci n'est pas du JSON"))
                .as("corps illisible : liste vide, que la pagination lit comme une fin de collection")
                .isEmpty();
        assertThat(SitesVigieChiro.sites("{\"_meta\":{\"total\":0}}")).isEmpty();
    }
}
