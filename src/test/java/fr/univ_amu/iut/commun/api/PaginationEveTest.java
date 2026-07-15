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
}
