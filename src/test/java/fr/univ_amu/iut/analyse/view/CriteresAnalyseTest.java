package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.VueSauvegardee;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests unitaires (purs, sans JavaFX) du catalogue de critères analyse : la **recherche texte** est
/// insensible casse/accents et couvre le taxon retenu, le vernaculaire, le latin, le n° de carré et le nom
/// de site. Les éditeurs (listes déroulantes des puces) sont exercés par le test de vue.
class CriteresAnalyseTest {

    private final BiPredicate<ObservationAnalyse, String> recherche = CriteresAnalyse.rechercheTexte();

    private static ObservationAnalyse obs(String taxon, String vern, String latin, String carre, String site) {
        return new ObservationAnalyse(
                taxon, latin, vern, "Chiroptères", StatutObservation.VALIDEE, 1L, 2026, carre, site, 10L, null, null);
    }

    @Test
    @DisplayName("La recherche couvre la commune du point (#2791)")
    void recherche_couvre_la_commune() {
        ObservationAnalyse o = new ObservationAnalyse(
                "Rhifer",
                "Rhinolophus ferrumequinum",
                "Grand rhinolophe",
                "Chiroptères",
                StatutObservation.VALIDEE,
                1L,
                2026,
                "130711",
                "Jardin de Serge",
                10L,
                "Aix-en-Provence",
                null);
        assertThat(recherche.test(o, "aix")).isTrue();
        assertThat(recherche.test(o, "marseille")).isFalse();
    }

    @Test
    @DisplayName("#3151 : la recherche couvre le CODE DU POINT, que la table affiche")
    void recherche_couvre_le_code_du_point() {
        // La documentation partagée promet que le champ de recherche « cherche dans les colonnes en
        // texte libre de l'écran ». La table des observations montre une colonne « Point » que la
        // recherche ignorait, alors que les quatre autres écrans la cherchent : la promesse était
        // fausse ici seule.
        ObservationAnalyse o = new ObservationAnalyse(
                "Rhifer",
                "Rhinolophus ferrumequinum",
                "Grand rhinolophe",
                "Chiroptères",
                StatutObservation.VALIDEE,
                1L,
                2026,
                "130711",
                "Jardin de Serge",
                10L,
                "Aix-en-Provence",
                "Z41");

        assertThat(recherche.test(o, "z41")).isTrue();
        assertThat(recherche.test(o, "z99"))
                .as("sans exclusion vérifiée, ce test passerait même si la recherche ne filtrait rien")
                .isFalse();
    }

    @Test
    @DisplayName("La recherche texte est insensible à la casse et aux accents")
    void recherche_insensible_casse_accents() {
        ObservationAnalyse o =
                obs("Pippip", "Pipistrelle commune", "Pipistrellus pipistrellus", "640380", "Étang du Vaccarès");
        assertThat(recherche.test(o, "PIPISTRELLE")).isTrue();
        assertThat(recherche.test(o, "vaccares")).isTrue(); // accent ignoré (Vaccarès)
        assertThat(recherche.test(o, "etang")).isTrue();
    }

    @Test
    @DisplayName("La recherche couvre le taxon retenu, le latin et le n° de carré ; sinon aucune correspondance")
    void recherche_couvre_les_champs_cles() {
        ObservationAnalyse o = obs("Nyclei", "Noctule de Leisler", "Nyctalus leisleri", "640381", "Marais");
        assertThat(recherche.test(o, "nyclei")).isTrue(); // taxon retenu
        assertThat(recherche.test(o, "nyctalus")).isTrue(); // nom latin
        assertThat(recherche.test(o, "640381")).isTrue(); // n° de carré
        assertThat(recherche.test(o, "absent")).isFalse();
    }

    @Test
    @DisplayName("Les vues par défaut (revue + catégories du référentiel) portent les bons filtres")
    void vues_par_defaut_portent_les_bons_filtres() {
        List<VueSauvegardee> vues = CriteresAnalyse.vuesParDefaut();

        assertThat(vues)
                .extracting(VueSauvegardee::nom)
                .containsExactly("Tout", "À valider", "Validées", "Chiroptères", "Autres", "Espèces prioritaires");
        assertThat(vues).allSatisfy(vue -> {
            assertThat(vue.id())
                    .as("vue par défaut : jamais persistée (lecture seule)")
                    .isNull();
            assertThat(vue.feature()).isEqualTo("analyse");
        });
        // Chaque vue porte le bon critère + la bonne valeur d'énumération (un nom d'enum erroné ferait un
        // filtre no-op silencieux) ; « Tout » ne filtre rien.
        assertThat(descripteur(vues, "Tout")).doesNotContain("statut", "groupe");
        assertThat(descripteur(vues, "À valider")).contains("statut", StatutObservation.NON_TOUCHEE.name());
        assertThat(descripteur(vues, "Validées")).contains("statut", StatutObservation.VALIDEE.name());
        assertThat(descripteur(vues, "Chiroptères")).contains("groupe", "Chiroptères");
        // Critère BOOLÉEN (#2353) : la clé sans valeur suffit, la présence de la puce filtre.
        assertThat(descripteur(vues, "Espèces prioritaires")).contains("a_enjeu");
        // Les catégories du référentiel (V05) partitionnent l'inventaire : Tadarida détecte aussi des
        // orthoptères et des micromammifères, qui s'intercalaient sans cela au rang des chiroptères.
        // « Autres » cumule les catégories non-chiroptères : c'est le choix multiple (#2615) qui le rend
        // possible, un critère à valeur unique n'aurait couvert qu'une catégorie en promettant le reste.
        assertThat(descripteur(vues, "Autres"))
                .contains("groupe", "Orthoptères et cigales", "Autres mammifères", "Oiseaux");
    }

    private static String descripteur(List<VueSauvegardee> vues, String nom) {
        return vues.stream()
                .filter(vue -> nom.equals(vue.nom()))
                .findFirst()
                .orElseThrow()
                .descripteurJson();
    }
}
