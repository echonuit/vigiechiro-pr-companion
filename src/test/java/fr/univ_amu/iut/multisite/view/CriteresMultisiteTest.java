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
/// lecture seule (`id` nul), et surtout critères/valeurs des descripteurs : un nom d'énumération erroné
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
                "Aix-en-Provence",
                "Jardin de Serge");

        assertThat(CriteresMultisite.rechercheTexte().test(ligne, "aix")).isTrue();
        assertThat(CriteresMultisite.rechercheTexte().test(ligne, "marseille")).isFalse();
        // Le nom du site est la seconde étiquette du carré (ADR 3157) : la puce le laissait cocher, la
        // recherche ne le laissait pas taper. La projection le porte depuis #3175.
        assertThat(CriteresMultisite.rechercheTexte().test(ligne, "serge"))
                .as("un carré se cherche par son nom comme par son numéro")
                .isTrue();

        // #4019 : depuis que la colonne rend « 20/06/2026 », chercher ce qu'on LIT doit marcher. La
        // forme ISO reste acceptée - elle vit encore dans les exports et dans le JSON de la CLI - mais
        // c'est la forme affichée qui décide, parce que c'est celle qu'on recopie de l'écran.
        assertThat(CriteresMultisite.rechercheTexte().test(ligne, "20/06/2026"))
                .as("on cherche la date telle qu'on la lit dans la colonne")
                .isTrue();
        assertThat(CriteresMultisite.rechercheTexte().test(ligne, "2026-06-20"))
                .as("la forme ISO reste acceptée : elle vit encore dans les exports")
                .isTrue();
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
