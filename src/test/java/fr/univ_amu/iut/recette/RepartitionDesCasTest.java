package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Éprouve le tri des cas de recette entre les trois bacs (#3764).
///
/// ## Ce que ces tests montrent que le dépôt ne montre pas
///
/// Le décompte réel se construit en balayant le classpath : il ne contient que les situations que le
/// dépôt contient **aujourd'hui**. Or celle qui compte - un cas perceptif que du code prétend
/// asserter - n'y est justement pas, et n'y sera jamais volontairement. Ces tests la fabriquent,
/// pour que le jour où elle arrivera par accident, le garde ait déjà été vu la refuser.
class RepartitionDesCasTest {

    private static final Set<String> AUCUNE_MARQUE = Set.of();

    @Test
    @DisplayName("Un cas que rien ne cite est non couvert")
    void un_cas_que_rien_ne_cite_est_non_couvert() {
        RepartitionDesCas tri = RepartitionDesCas.repartir(Set.of("S1-12"), AUCUNE_MARQUE, Map.of());

        assertThat(tri.nonCouverts()).containsExactly("S1-12");
        assertThat(tri.assertes()).isEmpty();
        assertThat(tri.perceptifs()).isEmpty();
    }

    @Test
    @DisplayName("Un cas qu'une assertion tranche est compté parmi les assertés")
    void un_cas_asserte_est_compte() {
        RepartitionDesCas tri =
                RepartitionDesCas.repartir(Set.of("S1-02"), AUCUNE_MARQUE, cite("S1-02", Jugement.AUTOMATIQUE));

        assertThat(tri.assertes()).containsExactly("S1-02");
        assertThat(tri.desaccords()).isEmpty();
    }

    @Test
    @DisplayName("Un cas perceptif n'est pas compté parmi les assertés, même joué")
    void un_cas_perceptif_joue_n_est_pas_asserte() {
        // Le coeur du dispositif. Un scénario perceptif CITE son cas - c'est le seul lien vers le
        // script - et sans cette règle le garde le compterait prouvé alors que personne ne l'a
        // regardé.
        RepartitionDesCas tri =
                RepartitionDesCas.repartir(Set.of("S1-26"), Set.of("S1-26"), cite("S1-26", Jugement.HUMAIN));

        assertThat(tri.assertes()).isEmpty();
        assertThat(tri.perceptifs()).containsExactly("S1-26");
        assertThat(tri.desaccords()).isEmpty();
    }

    @Test
    @DisplayName("Un cas perceptif que rien ne joue encore attend un regard, pas un test à écrire")
    void un_cas_perceptif_sans_scenario_attend_un_regard() {
        // L'état du jour de S1-26 et S1-27 : le script les réserve à l'oeil, aucun scénario ne les
        // joue. Les ranger « non couverts » les mettrait dans la file des tests à écrire, alors
        // qu'il n'y a rien à y écrire.
        RepartitionDesCas tri = RepartitionDesCas.repartir(Set.of("S1-27"), Set.of("S1-27"), Map.of());

        assertThat(tri.perceptifs()).containsExactly("S1-27");
        assertThat(tri.nonCouverts()).isEmpty();
    }

    @Test
    @DisplayName("Un cas perceptif que du code prétend asserter est un désaccord")
    void le_code_qui_pretend_asserter_un_cas_perceptif_est_un_desaccord() {
        RepartitionDesCas tri =
                RepartitionDesCas.repartir(Set.of("S1-26"), Set.of("S1-26"), cite("S1-26", Jugement.AUTOMATIQUE));

        assertThat(tri.desaccords()).containsExactly(Map.entry("S1-26", Jugement.AUTOMATIQUE));
        assertThat(tri.assertes())
                .as("la contradiction se signale, elle ne se résout pas dans le sens qui gonfle le compteur")
                .isEmpty();
    }

    @Test
    @DisplayName("Un test qui se déclare humain sur un cas que le script ne marque pas est un désaccord")
    void un_test_humain_sur_un_cas_non_marque_est_un_desaccord() {
        RepartitionDesCas tri =
                RepartitionDesCas.repartir(Set.of("S1-15"), AUCUNE_MARQUE, cite("S1-15", Jugement.HUMAIN));

        assertThat(tri.desaccords()).containsExactly(Map.entry("S1-15", Jugement.HUMAIN));
        assertThat(tri.nonCouverts())
                .as("rien ne le tranche : ni assertion, ni regard que le script aurait demandé")
                .containsExactly("S1-15");
    }

    @Test
    @DisplayName("Un identifiant cité mais qu'aucun script ne déclare n'entre dans aucun bac")
    void un_cas_cite_mais_non_declare_n_entre_dans_aucun_bac() {
        // Sinon une citation fautive - un identifiant inventé ou renuméroté - gonflerait le compte
        // des couverts. C'est le premier devoir de CorrespondanceRecetteTest de la refuser.
        RepartitionDesCas tri =
                RepartitionDesCas.repartir(Set.of("S1-02"), AUCUNE_MARQUE, cite("S1-99", Jugement.AUTOMATIQUE));

        assertThat(tri.assertes()).isEmpty();
        assertThat(tri.perceptifs()).isEmpty();
        assertThat(tri.nonCouverts()).containsExactly("S1-02");
    }

    @Test
    @DisplayName("Les trois bacs se partagent les cas déclarés sans en perdre ni en compter deux fois")
    void les_trois_bacs_partitionnent_les_cas_declares() {
        // L'invariant qui rend le rapport lisible : « 22 assertés, 2 perceptifs, 18 non couverts »
        // ne veut rien dire si la somme peut dépasser le total, ou lui manquer.
        Set<String> declares = Set.of("S1-02", "S1-12", "S1-26", "S1-27");
        RepartitionDesCas tri = RepartitionDesCas.repartir(
                declares,
                Set.of("S1-26", "S1-27"),
                Map.of("S1-02", Set.of(Jugement.AUTOMATIQUE), "S1-26", Set.of(Jugement.HUMAIN)));

        assertThat(tri.assertes()).doesNotContainAnyElementsOf(tri.perceptifs());
        assertThat(tri.assertes()).doesNotContainAnyElementsOf(tri.nonCouverts());
        assertThat(tri.perceptifs()).doesNotContainAnyElementsOf(tri.nonCouverts());
        assertThat(tri.assertes().size()
                        + tri.perceptifs().size()
                        + tri.nonCouverts().size())
                .isEqualTo(declares.size());
    }

    private static Map<String, Set<Jugement>> cite(String cas, Jugement jugement) {
        return Map.of(cas, Set.of(jugement));
    }
}
