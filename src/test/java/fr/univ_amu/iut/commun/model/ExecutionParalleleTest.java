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

    @Test
    @DisplayName("L'échec d'une tâche remonte TEL QUEL, sans être enveloppé")
    void echec_d_une_tache_remonte_tel_quel() {
        // Contrat annoncé en tête de `ExecutionParallele` et jamais gardé jusqu'ici (#2039). Il est le
        // pivot de la migration du découpage : c'est parce que le socle propage que `DecoupageParallele`
        // peut convertir ses rejets DANS la tâche et laisser le reste tomber.
        IllegalStateException panne = new IllegalStateException("disque plein");

        assertThatThrownBy(() -> new ExecutionParallele(4)
                        .cartographier(
                                List.of(1, 2, 3, 4),
                                "T",
                                x -> {
                                    if (x == 3) {
                                        throw panne;
                                    }
                                    return x;
                                },
                                progres -> {},
                                JetonAnnulation.neutre()))
                .as("l'appelant doit pouvoir filtrer sur son propre type d'exception")
                .isSameAs(panne);
    }

    @Test
    @DisplayName("Une tâche qui échoue n'empêche pas les autres déjà lancées de s'achever")
    void un_echec_laisse_les_taches_deja_lancees_s_achever() {
        // La Javadoc promet que les tâches soumises s'achèvent avant que l'exception ne remonte. Sans ce
        // test, rien n'empêcherait de « corriger » le socle en abattant tout au premier échec - ce qui
        // laisserait des travaux à moitié écrits sur le disque.
        //
        // Le test doit rendre l'échec VISIBLE : une première version, dont les tâches étaient
        // instantanées, passait même en ajoutant `cancel(true)` sur les restantes. Annulées ou non,
        // elles avaient déjà fini. Il faut donc que les autres soient encore EN VOL quand l'échec
        // survient - d'où l'attente croisée ci-dessous, et le sommeil qu'une interruption tronquerait.
        AtomicInteger achevees = new AtomicInteger();
        CountDownLatch lesTroisOntDemarre = new CountDownLatch(3);

        assertThatThrownBy(() -> new ExecutionParallele(4)
                        .cartographier(
                                List.of(1, 2, 3, 4),
                                "T",
                                x -> {
                                    if (x == 1) {
                                        // N'échoue qu'une fois les trois autres parties : sinon l'échec
                                        // pourrait précéder leur démarrage, et le test ne prouverait rien.
                                        attendre(lesTroisOntDemarre);
                                        throw new IllegalStateException("échec de la première");
                                    }
                                    lesTroisOntDemarre.countDown();
                                    if (!dormirJusquAuBout()) {
                                        return x; // interrompue : le travail n'est PAS compté comme achevé
                                    }
                                    achevees.incrementAndGet();
                                    return x;
                                },
                                progres -> {},
                                JetonAnnulation.neutre()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(achevees.get())
                .as("les trois autres tâches vont à leur terme : l'exécuteur est fermé avant la remontée")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("Une annulation EN VOL arrête les tâches restantes au lieu de les laisser toutes passer")
    void annulation_en_vol_arrete_les_restantes() {
        // Le test existant n'annule qu'AVANT le départ. Celui-ci exerce l'annulation en cours de route.
        //
        // C'est la PREMIÈRE tâche à passer, quelle qu'elle soit, qui annule - et non un élément désigné
        // d'avance. Une première version ciblait l'élément 2 en supposant que les tâches passent dans
        // l'ordre de soumission ; elle a échoué avec 4 exécutions au lieu de 2. Le `Semaphore` n'est pas
        // équitable et six threads virtuels se disputent le créneau : l'ordre de passage n'est pas celui
        // de la liste, et le socle ne l'a jamais promis (seul l'ordre des RÉSULTATS est garanti).
        JetonAnnulation jeton = new JetonAnnulation();
        AtomicInteger executees = new AtomicInteger();

        assertThatThrownBy(() -> new ExecutionParallele(1)
                        .cartographier(
                                List.of(1, 2, 3, 4, 5, 6),
                                "T",
                                x -> {
                                    if (executees.incrementAndGet() == 1) {
                                        jeton.annuler();
                                    }
                                    return x;
                                },
                                progres -> {},
                                jeton))
                .isInstanceOf(OperationAnnuleeException.class);

        assertThat(executees.get())
                .as("les cinq tâches restantes butent sur le point de contrôle et ne travaillent pas")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Phase de pipeline : la fraction part du décalage et n'atteint 100 % qu'au bout du pipeline")
    void phase_de_pipeline_decale_la_fraction() {
        // Deux originaux copiés, sur un pipeline de quatre étapes (copie puis transformation). La copie
        // doit s'arrêter à la moitié : c'est tout l'objet de #2039. Avec la forme autonome, le deuxième
        // élément amenait la barre à 100 % alors que la moitié du travail restait à faire.
        List<Progression> points = new ArrayList<>();

        new ExecutionParallele(1)
                .cartographier(
                        List.of("a", "b"),
                        termine -> "Copie " + termine.faits() + "/" + termine.total(),
                        new EchelleProgression(0, 4),
                        (index, element) -> element,
                        points::add,
                        JetonAnnulation.neutre());

        assertThat(points).extracting(Progression::fraction).containsExactly(0.25, 0.5);
        assertThat(points).extracting(Progression::libelle).containsExactly("Copie 1/2", "Copie 2/2");
    }

    @Test
    @DisplayName("Phase de pipeline : la seconde phase reprend au décalage laissé par la première")
    void seconde_phase_reprend_au_decalage() {
        List<Progression> points = new ArrayList<>();

        new ExecutionParallele(1)
                .cartographier(
                        List.of("a", "b"),
                        termine -> "Transformation " + termine.faits() + "/" + termine.total(),
                        new EchelleProgression(2, 4),
                        (index, element) -> element,
                        points::add,
                        JetonAnnulation.neutre());

        assertThat(points)
                .as("la barre reprend à 50 % et finit à 100 %")
                .extracting(Progression::fraction)
                .containsExactly(0.75, 1.0);
    }

    @Test
    @DisplayName("Le libellé peut dépendre du RÉSULTAT, connu seulement après la tâche")
    void libelle_derive_du_resultat() {
        // « (déjà présent) » de PreparationOriginaux ne se sait qu'une fois la copie examinée : c'est
        // pourquoi le socle passe le résultat au constructeur de libellé, et pas seulement l'élément.
        List<String> libelles = new ArrayList<>();

        new ExecutionParallele(1)
                .cartographier(
                        List.of("neuf", "deja-la"),
                        termine -> "Copie " + termine.element() + (termine.resultat() ? " (déjà présent)" : ""),
                        EchelleProgression.autonome(2),
                        (index, element) -> element.startsWith("deja"),
                        progres -> libelles.add(progres.libelle()),
                        JetonAnnulation.neutre());

        // Sans ordre imposé : le socle garantit l'ordre des RÉSULTATS, pas celui des points de
        // progression, qui suivent l'ordre d'achèvement. Ce que ce test prouve, c'est que le libellé
        // voit le résultat - pas qui a fini le premier.
        assertThat(libelles).containsExactlyInAnyOrder("Copie neuf", "Copie deja-la (déjà présent)");
    }

    @Test
    @DisplayName("La tâche reçoit l'index de son élément, dans l'ordre de la liste")
    void la_tache_recoit_l_index() {
        // DecoupageParallele en tire un dossier temporaire isolé par original, PreparationOriginaux un
        // numéro de plan. Sans l'index, les deux devraient pré-zipper leur liste.
        List<String> indexes = new ExecutionParallele(4)
                .cartographier(
                        List.of("a", "b", "c"),
                        termine -> "T",
                        EchelleProgression.autonome(3),
                        (index, element) -> index + ":" + element,
                        progres -> {},
                        JetonAnnulation.neutre());

        assertThat(indexes).containsExactly("0:a", "1:b", "2:c");
    }

    @Test
    @DisplayName("Une échelle incohérente est refusée à la construction, pas silencieusement corrigée")
    void echelle_incoherente_refusee() {
        assertThatThrownBy(() -> new EchelleProgression(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EchelleProgression(-1, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EchelleProgression(5, 4))
                .as("un décalage au-delà du total produirait une fraction supérieure à 1")
                .isInstanceOf(IllegalArgumentException.class);
    }

    /// Sommeil bref, **interruptible**. Rend `false` si la tâche a été interrompue : c'est ainsi que
    /// [#un_echec_laisse_les_taches_deja_lancees_s_achever] distingue une tâche menée à son terme d'une
    /// tâche abattue en vol par un `cancel(true)`.
    private static boolean dormirJusquAuBout() {
        try {
            Thread.sleep(300);
            return true;
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            return false;
        }
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
