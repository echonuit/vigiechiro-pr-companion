package fr.univ_amu.iut.multisite.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.VueSauvegardee;
import fr.univ_amu.iut.multisite.model.EtatAnalyse;
import fr.univ_amu.iut.multisite.model.LignePassage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests unitaires (purs, sans JavaFX) des **vues par défaut** du tableau des passages : noms et ordre,
/// lecture seule (`id` nul), et surtout critères/valeurs des descripteurs — un nom d'énumération erroné
/// produirait un filtre **no-op silencieux**.
class CriteresMultisiteTest {

    @Test
    @DisplayName("La recherche texte couvre la commune du point (#2791)")
    void recherche_couvre_la_commune() {
        LignePassage ligne = new LignePassage(
                1L,
                "130711",
                "A1",
                2026,
                1,
                "2026-06-20",
                StatutWorkflow.IMPORTE,
                null,
                EtatAnalyse.SANS_OBJET,
                null,
                null,
                "Aix-en-Provence");

        assertThat(CriteresMultisite.rechercheTexte().test(ligne, "aix")).isTrue();
        assertThat(CriteresMultisite.rechercheTexte().test(ligne, "marseille")).isFalse();
    }

    @Test
    @DisplayName("Les vues par défaut (Tout / Déposés / Non vérifié / Vérifiés) portent les bons filtres")
    void vues_par_defaut_portent_les_bons_filtres() {
        List<VueSauvegardee> vues = CriteresMultisite.vuesParDefaut();

        assertThat(vues)
                .extracting(VueSauvegardee::nom)
                .containsExactly("Tout", "Résultats à importer", "Déposés", "À réactiver", "Non vérifié", "Vérifiés");
        assertThat(vues).allSatisfy(vue -> {
            assertThat(vue.id())
                    .as("vue par défaut : jamais persistée (lecture seule)")
                    .isNull();
            assertThat(vue.feature()).isEqualTo("multisite");
        });
        // Chaque vue porte le bon critère + la bonne valeur d'énumération ; « Tout » ne filtre rien.
        assertThat(descripteur(vues, "Tout")).doesNotContain("statut", "verdict");
        assertThat(descripteur(vues, "Résultats à importer")).contains("analyse", EtatAnalyse.A_IMPORTER.name());
        assertThat(descripteur(vues, "Déposés")).contains("statut", StatutWorkflow.DEPOSE.name());
        assertThat(descripteur(vues, "Non vérifié")).contains("verdict", Verdict.A_VERIFIER.name());
        assertThat(descripteur(vues, "Vérifiés")).contains("statut", StatutWorkflow.VERIFIE.name());
    }

    private static String descripteur(List<VueSauvegardee> vues, String nom) {
        return vues.stream()
                .filter(vue -> nom.equals(vue.nom()))
                .findFirst()
                .orElseThrow()
                .descripteurJson();
    }
}
