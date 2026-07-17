package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests du moteur parallèle générique (#1779), extrait du découpage de l'import. On vérifie ses garanties
/// de contrat - **ordre des résultats**, **progression k/N**, **borne de concurrence** et **annulation** -
/// indépendamment de tout usage métier.
class ExecutionParalleleTest {

    @Test
    @DisplayName("Applique la tâche à tous les éléments et rend les résultats DANS L'ORDRE de l'entrée")
    void applique_a_tous_et_preserve_l_ordre() {
        ExecutionParallele moteur = new ExecutionParallele(4);
        List<Integer> entree = IntStream.rangeClosed(1, 10).boxed().toList();

        List<Integer> resultats =
                moteur.cartographier(entree, "Calcul", x -> x * x, progres -> {}, JetonAnnulation.neutre());

        assertThat(resultats)
                .as("l'ordre d'entrée est préservé malgré le parallélisme")
                .containsExactly(1, 4, 9, 16, 25, 36, 49, 64, 81, 100);
    }

    @Test
    @DisplayName("Émet un point « libellé k/N » par élément terminé, le dernier à 100 %")
    void progression_un_point_par_element_jusqu_a_cent() {
        ExecutionParallele moteur = new ExecutionParallele(4);
        // Le moteur émet la progression sous verrou : les ajouts sont sérialisés, une ArrayList suffit.
        List<Progression> points = new ArrayList<>();

        moteur.cartographier(List.of("a", "b", "c"), "Régénération", s -> s, points::add, JetonAnnulation.neutre());

        assertThat(points)
                .extracting(Progression::libelle)
                .containsExactlyInAnyOrder("Régénération 1/3", "Régénération 2/3", "Régénération 3/3");
        assertThat(points.get(points.size() - 1).fraction())
                .as("le dernier point émis est k=N, donc la barre atteint 100 %")
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("Une liste vide ne lance rien et rend une liste vide (aucune division par zéro)")
    void liste_vide_ne_fait_rien() {
        List<Progression> points = new ArrayList<>();

        List<String> resultats = new ExecutionParallele(4)
                .cartographier(List.<String>of(), "Régénération", s -> s, points::add, JetonAnnulation.neutre());

        assertThat(resultats).isEmpty();
        assertThat(points).isEmpty();
    }

    @Test
    @DisplayName("Un parallélisme inférieur à 1 est refusé à la construction")
    void parallelisme_invalide_rejete() {
        assertThatThrownBy(() -> new ExecutionParallele(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("au moins 1");
    }

    @Test
    @DisplayName("parallelisme=1 sérialise : jamais deux tâches de front")
    void parallelisme_un_serialise() {
        AtomicInteger enCours = new AtomicInteger();
        AtomicInteger maxConcurrence = new AtomicInteger();

        new ExecutionParallele(1)
                .cartographier(
                        List.of(1, 2, 3, 4),
                        "T",
                        x -> {
                            maxConcurrence.accumulateAndGet(enCours.incrementAndGet(), Math::max);
                            dormir(20);
                            enCours.decrementAndGet();
                            return x;
                        },
                        progres -> {},
                        JetonAnnulation.neutre());

        assertThat(maxConcurrence.get())
                .as("le sémaphore à 1 permis interdit tout recouvrement")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Le parallélisme est réel : N tâches tournent de front quand le facteur le permet")
    void parallelisme_permet_la_concurrence() {
        int facteur = 3;
        CountDownLatch tousArrives = new CountDownLatch(facteur);

        List<Boolean> ontVuLesAutres = new ExecutionParallele(facteur)
                .cartographier(
                        List.of(1, 2, 3),
                        "T",
                        x -> {
                            tousArrives.countDown();
                            return attendre(tousArrives);
                        },
                        progres -> {},
                        JetonAnnulation.neutre());

        assertThat(ontVuLesAutres)
                .as("chaque tâche atteint la barrière : les 3 s'exécutent simultanément (sinon délai d'attente)")
                .containsExactly(true, true, true);
    }

    @Test
    @DisplayName("Un jeton déjà annulé arrête tout dès le premier point de contrôle, sans exécuter la tâche")
    void jeton_deja_annule_propage_l_annulation() {
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler();
        AtomicInteger appels = new AtomicInteger();

        assertThatThrownBy(() -> new ExecutionParallele(4)
                        .cartographier(
                                List.of(1, 2, 3, 4),
                                "T",
                                x -> {
                                    appels.incrementAndGet();
                                    return x;
                                },
                                progres -> {},
                                jeton))
                .isInstanceOf(OperationAnnuleeException.class);
        assertThat(appels.get())
                .as("l'annulation est consultée AVANT le travail : la tâche ne s'exécute pas")
                .isZero();
    }

    private static boolean attendre(CountDownLatch latch) {
        try {
            return latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
