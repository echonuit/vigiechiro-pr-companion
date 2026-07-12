package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Boucle de pagination Eve (#1150) : `PaginationEve.parcourir` accumule **toutes** les pages
/// jusqu'à la première page vide, alors que l'ancien code ne lisait que la première (troncature
/// silencieuse au-delà de `max_results`). On alimente la boucle avec des pages en mémoire (le
/// **transport** est éprouvé ailleurs) et on vérifie l'accumulation et les deux conditions d'arrêt.
class PaginationEveTest {

    private static final Map<Integer, String> PAGES = Map.of(
            1,
            "{\"_items\":[{\"_id\":\"p1\",\"site\":{\"_id\":\"s1\",\"titre\":\"A-100001\"}},"
                    + "{\"_id\":\"p2\",\"site\":{\"_id\":\"s2\",\"titre\":\"B-100002\"}}]}",
            2,
            "{\"_items\":[{\"_id\":\"p3\",\"site\":{\"_id\":\"s1\",\"titre\":\"A-100001\"}}]}");

    /// Corps de la page demandée, une page vide (`_items` vide) au-delà des pages connues.
    private static Optional<String> page(int numero) {
        return Optional.of(PAGES.getOrDefault(numero, "{\"_items\":[]}"));
    }

    @Test
    @DisplayName("parcourir : union de toutes les pages, arrêt à la première page aux _items vides")
    void union_des_pages() {
        List<ParticipationVigieChiro> tout =
                PaginationEve.parcourir(500, PaginationEveTest::page, ParticipationsVigieChiro::participations);

        assertThat(tout).extracting(ParticipationVigieChiro::id).containsExactly("p1", "p2", "p3");
    }

    @Test
    @DisplayName("parcourir : page indisponible (corps vide) → on renvoie ce qui a déjà été collecté")
    void arret_sur_page_indisponible() {
        List<ParticipationVigieChiro> tout = PaginationEve.parcourir(
                500, numero -> numero == 1 ? page(1) : Optional.empty(), ParticipationsVigieChiro::participations);

        assertThat(tout).extracting(ParticipationVigieChiro::id).containsExactly("p1", "p2");
    }

    @Test
    @DisplayName("parcourir : les sites s'accumulent sur toutes les pages (dédup inter-pages à l'appelant)")
    void sites_accumules_sur_plusieurs_pages() {
        List<SiteVigieChiro> sites =
                PaginationEve.parcourir(500, PaginationEveTest::page, ParticipationsVigieChiro::sites);

        // page 1 → [s1, s2], page 2 → [s1] : l'accumulation brute contient s1 deux fois. C'est
        // `mesSites` qui déduplique par `_id` (putIfAbsent) ; ici on documente l'entrée à dédupliquer.
        assertThat(sites).extracting(SiteVigieChiro::id).containsExactly("s1", "s2", "s1");
    }
}
