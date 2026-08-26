package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Le découpage des lectures par lot (#4251).
///
/// Elle n'avait **aucun test**, et son commentaire annonçait une raison **fausse** : que SQLite
/// refuserait au-delà de quelques centaines de paramètres liés. Mesuré sur le pilote embarqué,
/// cinquante mille passent. Le découpage borne la **taille de la requête**, rien de plus.
///
/// Ce que ces cas éprouvent reste utile, et c'est même le seul vrai risque : qu'un découpage **perde
/// des identifiants en silence**. Une lecture tronquée ne lève rien - elle rend moins de lignes, et
/// l'écran affiche un inventaire incomplet sans que rien ne rougisse.
class LotsDeParametresTest {

    @Nested
    @DisplayName("Ce que le découpage rend")
    class CeQueLeDecoupageRend {

        @Test
        @DisplayName("Une collection vide ne rend AUCUNE tranche : l'appelant ne lance alors aucune requête")
        void vide_ne_rend_aucune_tranche() {
            assertThat(LotsDeParametres.decouper(List.of())).isEmpty();
        }

        @Test
        @DisplayName("Sous la borne, une seule tranche qui porte tout")
        void sous_la_borne_une_seule_tranche() {
            List<Long> ids = suite(499);

            List<List<Long>> tranches = LotsDeParametres.decouper(ids);

            assertThat(tranches).hasSize(1);
            assertThat(tranches.get(0)).isEqualTo(ids);
        }

        @Test
        @DisplayName("⚠️ Au-delà de la borne, plusieurs tranches - et AUCUN identifiant ne se perd")
        void au_dela_de_la_borne_rien_ne_se_perd() {
            List<Long> ids = suite(1201);

            List<List<Long>> tranches = LotsDeParametres.decouper(ids);

            // Le mode de panne qu'on redoute n'est pas une exception : c'est une lecture qui rend des
            // lignes EN MOINS, sans rien signaler. Un écran afficherait alors un inventaire tronqué.
            assertThat(tranches.stream().flatMap(List::stream).toList())
                    .as("réunies, les tranches doivent redonner exactement ce qu'on a demandé")
                    .isEqualTo(ids);
            assertThat(tranches).allSatisfy(tranche -> assertThat(tranche).isNotEmpty());
        }

        @Test
        @DisplayName("Les tranches restent d'une taille bornée, quelle que soit la demande")
        void les_tranches_restent_bornees() {
            List<List<Long>> tranches = LotsDeParametres.decouper(suite(5000));

            // Ce cas affirmait « ne dépasse pas ce que SQLite accepte de lier », avec 999 pour borne.
            // Il encodait une hypothèse au lieu de l'éprouver, et l'hypothèse était fausse : le pilote
            // embarqué accepte cinquante mille paramètres. Ce qu'on garde ici est la seule propriété que
            // la classe promet vraiment - des tranches bornées, donc des requêtes de taille bornée.
            assertThat(tranches).allSatisfy(tranche -> assertThat(tranche).hasSizeLessThanOrEqualTo(1000));
        }

        @Test
        @DisplayName("Les doublons sont écartés : deux points du même site ne demandent pas deux fois la même ligne")
        void les_doublons_sont_ecartes() {
            List<Long> avecDoublons = new ArrayList<>(List.of(7L, 7L, 8L, 7L, 8L));

            List<List<Long>> tranches = LotsDeParametres.decouper(avecDoublons);

            assertThat(tranches.stream().flatMap(List::stream).toList()).containsExactly(7L, 8L);
        }

        @Test
        @DisplayName("L'ordre demandé est conservé : une lecture par lot ne réordonne pas ce qu'elle sert")
        void l_ordre_est_conserve() {
            List<List<Long>> tranches = LotsDeParametres.decouper(List.of(30L, 10L, 20L));

            assertThat(tranches.stream().flatMap(List::stream).toList()).containsExactly(30L, 10L, 20L);
        }
    }

    private static List<Long> suite(int combien) {
        List<Long> ids = new ArrayList<>(combien);
        for (long i = 0; i < combien; i++) {
            ids.add(i);
        }
        return ids;
    }
}
