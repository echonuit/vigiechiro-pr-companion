package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import fr.univ_amu.iut.commun.model.CarreCandidat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Lecture de `GET /grille_stoc/cercle` **avec sa géométrie** (#4610).
///
/// Deux choses s'y jouent que la lecture du seul numéro ne pose pas : l'ordre des coordonnées, qu'une
/// inversion rendrait plausible plutôt que fausse, et l'ordre des candidats, que le serveur ne garantit
/// pas là où nous en avons le plus besoin.
class ReponsesGrilleStocTest {

    @Test
    @DisplayName("carresProches : le centre est un Point GeoJSON, donc [lon, lat] - et l'inverser"
            + " enverrait le carré à des milliers de kilomètres (#4610)")
    void carres_stoc_proches_lisent_le_centre_dans_l_ordre_geojson() {
        // Le vrai risque n'est pas de mal calculer, c'est de lire les deux nombres à l'envers : la
        // distance resterait un nombre plausible. Ici le centre EST la position demandée, donc la seule
        // lecture correcte rend zéro ; l'inverse rend des centaines de kilomètres. Leçon de #1277.
        String corps = "{\"_items\":[{\"_id\":\"g1\",\"numero\":\"40110\",\"centre\":{"
                + "\"type\":\"Point\",\"coordinates\":[6.293767,44.44544392]}}],\"_meta\":{\"total\":1}}";

        List<CarreCandidat> candidats = ReponsesGrilleStoc.carresProches(corps, 44.44544392, 6.293767);

        assertThat(candidats).singleElement().satisfies(candidat -> {
            assertThat(candidat.numero())
                    .as("six chiffres, comme partout ailleurs")
                    .isEqualTo("040110");
            assertThat(candidat.distanceMetres()).isCloseTo(0, within(1.0));
        });
    }

    @Test
    @DisplayName("carresProches : à distance ÉGALE, l'ordre est le nôtre et il est stable - par numéro")
    void carres_stoc_proches_departagent_l_egalite_par_le_numero() {
        // C'est tout l'objet de #4610 : le `$near` de MongoDB trie par distance croissante, mais à
        // distance égale il ne garantit RIEN, et 5 m de décalage font basculer son premier. Un ordre
        // qui dépend du serveur ne peut pas servir de verdict. Les deux centres ci-dessous encadrent la
        // position à 1 000 m chacun, par construction.
        String corps = "{\"_items\":["
                + "{\"_id\":\"b\",\"numero\":\"40111\",\"centre\":{\"type\":\"Point\","
                + "\"coordinates\":[6.318933,44.44544392]}},"
                + "{\"_id\":\"a\",\"numero\":\"40110\",\"centre\":{\"type\":\"Point\","
                + "\"coordinates\":[6.293767,44.44544392]}}"
                + "],\"_meta\":{\"total\":2}}";

        List<CarreCandidat> candidats = ReponsesGrilleStoc.carresProches(corps, 44.44544392, 6.30635);

        assertThat(candidats)
                .extracting(CarreCandidat::numero)
                .as("le serveur les rendait dans l'autre ordre : le nôtre ne dépend pas de lui")
                .containsExactly("040110", "040111");
        assertThat(candidats.get(1).distanceMetres() - candidats.get(0).distanceMetres())
                .as("l'égalité est stricte : c'est elle qui rend le premier arbitraire")
                .isCloseTo(0, within(1.0));
    }

    @Test
    @DisplayName("carresProches : un élément sans centre lisible est écarté, la réponse reste utilisable")
    void carres_stoc_proches_ecartent_ce_qui_n_a_pas_de_centre() {
        String corps = "{\"_items\":["
                + "{\"_id\":\"g1\",\"numero\":\"40110\"},"
                + "{\"_id\":\"g2\",\"numero\":\"40111\",\"centre\":{\"type\":\"Point\","
                + "\"coordinates\":[6.318933]}},"
                + "{\"_id\":\"g3\",\"numero\":\"130711\",\"centre\":{\"type\":\"Point\","
                + "\"coordinates\":[5.4474,43.5298]}}"
                + "],\"_meta\":{\"total\":3}}";

        assertThat(ReponsesGrilleStoc.carresProches(corps, 43.5298, 5.4474))
                .extracting(CarreCandidat::numero)
                .containsExactly("130711");
    }
}
