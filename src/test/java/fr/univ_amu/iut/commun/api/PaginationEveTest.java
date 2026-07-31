package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Boucle de pagination Eve (#1150) : `PaginationEve.parcourir` accumule **toutes** les pages
/// jusqu'à la première page vide, alors que l'ancien code ne lisait que la première (troncature
/// silencieuse au-delà de `max_results`). Depuis #1284 le parcours est **tout-ou-rien** : un échec
/// en cours de route rend l'issue, jamais un préfixe des pages déjà lues. On alimente la boucle avec
/// des pages en mémoire (le **transport** est éprouvé ailleurs).
/// Survivants PIT **assumés** (lus un par un, mesure du 2026-07-31 : 41 tués sur 45) : les deux bornes
/// `total <= 0` (ici et dans [LotPagine#pagesAnnoncees]) - avec un total nul, la branche mutée calcule
/// la même chose ; l'incrément de la boucle, qui part à l'infini et n'est visible que par le délai ; et
/// le retour d'une liste **déjà vide** dans [LocalitesVigieChiro]. Aucun de ces quatre ne change un
/// comportement observable : les tester demanderait d'affirmer que rien ne se passe.
class PaginationEveTest {

    private static final Map<Integer, String> PAGES = Map.of(
            1,
            "{\"_items\":[{\"_id\":\"p1\",\"site\":{\"_id\":\"s1\",\"titre\":\"A-100001\"}},"
                    + "{\"_id\":\"p2\",\"site\":{\"_id\":\"s2\",\"titre\":\"B-100002\"}}]}",
            2,
            "{\"_items\":[{\"_id\":\"p3\",\"site\":{\"_id\":\"s1\",\"titre\":\"A-100001\"}}]}");

    /// Corps de la page demandée, une page vide (`_items` vide) au-delà des pages connues.
    private static ReponseApi<String> page(int numero) {
        return ReponseApi.succes(PAGES.getOrDefault(numero, "{\"_items\":[]}"));
    }

    @Test
    @DisplayName("requete : on ne demande jamais plus de 100 éléments par page (au-delà, Eve refuse : 422)")
    void requete_respecte_le_plafond_eve() {
        // Régression #1277 : on demandait 1000. Eve ne tronque pas, il REJETTE la requête (422) ; le
        // transport dégradant proprement tout échec HTTP en Optional.empty(), la boucle s'arrêtait dès la
        // première page et la collection revenait VIDE, en silence : plus aucune observation importée,
        // plus aucune participation à rattacher, plus aucun site rapproché. Depuis #1284, un tel refus
        // reviendrait de toute façon en Refuse(422), plus jamais en collection vide.
        assertThat(PaginationEve.TAILLE_PAGE)
                .as("maximum accepté par le Paginator d'Eve (vigiechiro/xin/snippets.py)")
                .isLessThanOrEqualTo(100);

        assertThat(PaginationEve.requete(1)).isEqualTo("?max_results=100&page=1");
        assertThat(PaginationEve.requete(49)).isEqualTo("?max_results=100&page=49");
    }

    @Test
    @DisplayName("parcourir : union de toutes les pages, arrêt à la première page aux _items vides")
    void union_des_pages() {
        ReponseApi<List<ParticipationVigieChiro>> tout =
                PaginationEve.parcourir(500, PaginationEveTest::page, ParticipationsVigieChiro::participations);

        assertThat(tout.enOptionnel().orElseThrow())
                .extracting(ParticipationVigieChiro::id)
                .containsExactly("p1", "p2", "p3");
    }

    @Test
    @DisplayName("parcourir : échec à la page 2 → l'issue de la page fautive, JAMAIS le préfixe déjà lu (#1284)")
    void echec_en_cours_de_parcours_rend_l_issue() {
        // Avant #1284, une panne à la page 2 rendait la page 1 comme si la collection était complète :
        // un préfixe silencieux, la variante pire-que-vide de #1277 (c'est le faux négatif qui a fait
        // exploser le contrat live sur un getFirst()).
        ReponseApi<List<ParticipationVigieChiro>> injoignable = PaginationEve.parcourir(
                500,
                numero -> numero == 1 ? page(1) : ReponseApi.injoignable("délai d'attente dépassé"),
                ParticipationsVigieChiro::participations);
        ReponseApi<List<ParticipationVigieChiro>> refuse = PaginationEve.parcourir(
                500,
                numero -> numero == 1 ? page(1) : ReponseApi.refuse(422, "{}"),
                ParticipationsVigieChiro::participations);

        assertThat(injoignable).isEqualTo(ReponseApi.injoignable("délai d'attente dépassé"));
        assertThat(refuse).isEqualTo(ReponseApi.refuse(422, "{}"));
    }

    @Test
    @DisplayName("parcourir : les sites s'accumulent sur toutes les pages (dédup inter-pages à l'appelant)")
    void sites_accumules_sur_plusieurs_pages() {
        ReponseApi<List<SiteVigieChiro>> sites =
                PaginationEve.parcourir(500, PaginationEveTest::page, ParticipationsVigieChiro::sites);

        // page 1 → [s1, s2], page 2 → [s1] : l'accumulation brute contient s1 deux fois. C'est
        // `mesSites` qui déduplique par `_id` (putIfAbsent) ; ici on documente l'entrée à dédupliquer.
        assertThat(sites.enOptionnel().orElseThrow())
                .extracting(SiteVigieChiro::id)
                .containsExactly("s1", "s2", "s1");
    }

    @Test
    @DisplayName("parcourir suivi : chaque page est notifiée avec le nombre total de pages (_meta.total)")
    void suivi_notifie_le_total_de_pages() {
        // _meta.total = 150 éléments, 100 par page -> 2 pages. Le total est lu sur la PREMIÈRE page et
        // rapporté à chacune, pour une barre déterminée « page XX/YY » (#1534).
        Map<Integer, String> pages = Map.of(
                1,
                "{\"_meta\":{\"total\":150},\"_items\":[{\"_id\":\"p1\",\"site\":{\"_id\":\"s1\","
                        + "\"titre\":\"A-100001\"}}]}",
                2,
                "{\"_items\":[{\"_id\":\"p2\",\"site\":{\"_id\":\"s2\",\"titre\":\"B-100002\"}}]}");
        List<String> vus = new ArrayList<>();

        PaginationEve.parcourir(
                500,
                numero -> ReponseApi.succes(pages.getOrDefault(numero, "{\"_items\":[]}")),
                ParticipationsVigieChiro::participations,
                (page, totalPages) -> vus.add(page + "/" + totalPages));

        assertThat(vus).containsExactly("1/2", "2/2");
    }

    @Test
    @DisplayName("#3002 : un parcours ÉPUISÉ se dit complet, et rapporte le total annoncé")
    void parcours_epuise_est_complet() {
        Map<Integer, String> pages = Map.of(
                1,
                "{\"_meta\":{\"total\":3},\"_items\":[{\"_id\":\"p1\",\"site\":{\"_id\":\"s1\","
                        + "\"titre\":\"A-100001\"}}]}",
                2,
                "{\"_items\":[{\"_id\":\"p2\",\"site\":{\"_id\":\"s2\",\"titre\":\"B-100002\"}}]}");

        ReponseApi<LotPagine<ParticipationVigieChiro>> issue = PaginationEve.parcourirBorne(
                500,
                numero -> ReponseApi.succes(pages.getOrDefault(numero, "{\"_items\":[]}")),
                ParticipationsVigieChiro::participations,
                (page, totalPages) -> {});

        LotPagine<ParticipationVigieChiro> lot =
                ((ReponseApi.Succes<LotPagine<ParticipationVigieChiro>>) issue).valeur();
        assertThat(lot.complet())
                .as("la boucle s'est arrêtée sur une page vide : c'est la collection entière")
                .isTrue();
        assertThat(lot.elements()).hasSize(2);
        assertThat(lot.pagesLues()).isEqualTo(2);
        assertThat(lot.totalAnnonce()).isEqualTo(3);
    }

    @Test
    @DisplayName("#3002 : un parcours arrêté au PLAFOND se dit incomplet - sans quoi il mentirait comme #1277")
    void parcours_au_plafond_est_incomplet() {
        // Le mode de panne que ce drapeau existe pour empêcher : avec « --pages 1 » sur une collection de
        // 20 517 sites, l'appelant tiendrait 100 éléments et les annoncerait comme le tout.
        ReponseApi<LotPagine<ParticipationVigieChiro>> issue = PaginationEve.parcourirBorne(
                1,
                numero -> ReponseApi.succes("{\"_meta\":{\"total\":250},\"_items\":[{\"_id\":\"p" + numero
                        + "\",\"site\":{\"_id\":\"s1\",\"titre\":\"A-100001\"}}]}"),
                ParticipationsVigieChiro::participations,
                (page, totalPages) -> {});

        LotPagine<ParticipationVigieChiro> lot =
                ((ReponseApi.Succes<LotPagine<ParticipationVigieChiro>>) issue).valeur();
        assertThat(lot.complet())
                .as("la boucle s'est arrêtée parce qu'elle avait épuisé SON plafond, pas la collection")
                .isFalse();
        assertThat(lot.pagesLues()).isEqualTo(1);
        assertThat(lot.totalAnnonce()).isEqualTo(250);
        assertThat(lot.pagesAnnoncees())
                .as("250 éléments à 100 par page : trois pages annoncées, une seule lue")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("#3002 : un total PILE sur une frontière de page ne fabrique pas de page fantôme")
    void total_sur_une_frontiere_de_page() {
        // 200 éléments à 100 par page, c'est DEUX pages, pas trois. Le cas limite du calcul d'arrondi
        // (« +99 puis division ») : avec 250 il tolère une erreur de plus ou moins un, avec 200 non.
        ReponseApi<LotPagine<ParticipationVigieChiro>> issue = PaginationEve.parcourirBorne(
                1,
                numero -> ReponseApi.succes("{\"_meta\":{\"total\":200},\"_items\":[{\"_id\":\"p1\","
                        + "\"site\":{\"_id\":\"s1\",\"titre\":\"A-100001\"}}]}"),
                ParticipationsVigieChiro::participations,
                (page, totalPages) -> {});

        LotPagine<ParticipationVigieChiro> lot =
                ((ReponseApi.Succes<LotPagine<ParticipationVigieChiro>>) issue).valeur();
        assertThat(lot.pagesAnnoncees())
                .as("200 sur 100 par page : exactement deux pages")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("#3002 : le suivi reçoit le nombre de pages exact, y compris sur une frontière")
    void le_suivi_recoit_le_total_exact() {
        List<String> vus = new ArrayList<>();

        PaginationEve.parcourirBorne(
                2,
                numero -> ReponseApi.succes("{\"_meta\":{\"total\":200},\"_items\":[{\"_id\":\"p" + numero
                        + "\",\"site\":{\"_id\":\"s1\",\"titre\":\"A-100001\"}}]}"),
                ParticipationsVigieChiro::participations,
                (page, totalPages) -> vus.add(page + "/" + totalPages));

        assertThat(vus).as("la progression annonce deux pages, ni une ni trois").containsExactly("1/2", "2/2");
    }

    @Test
    @DisplayName("#3002 : le tout-ou-rien survit à la borne - un refus en page 2 ne rend aucun préfixe")
    void parcours_borne_reste_tout_ou_rien() {
        ReponseApi<LotPagine<ParticipationVigieChiro>> issue = PaginationEve.parcourirBorne(
                500,
                numero -> numero == 1
                        ? ReponseApi.succes(
                                "{\"_items\":[{\"_id\":\"p1\",\"site\":{\"_id\":\"s1\"," + "\"titre\":\"A-100001\"}}]}")
                        : ReponseApi.refuse(503, "service indisponible"),
                ParticipationsVigieChiro::participations,
                (page, totalPages) -> {});

        assertThat(issue)
                .as("la page 1 lue ne doit pas ressortir comme si la collection s'arrêtait là")
                .isInstanceOf(ReponseApi.Refuse.class);
        assertThat(((ReponseApi.Refuse<LotPagine<ParticipationVigieChiro>>) issue).statut())
                .isEqualTo(503);
    }
}
