package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Calcul (pur) de l'aperçu du préfixe (R6), extrait de [ImportationViewModel]. On vérifie ici les
/// bords du contrat sans dépendre du format exact de `Prefixe.nommerOriginal` : rattachement
/// incomplet → chaîne vide, rattachement complet → préfixe non vide construit sur un exemple par
/// défaut (le cas « rapport inspecté » est couvert par les tests d'intégration de l'assistant).
class ApercuPrefixeTest {

    private static final Site SITE = new Site(1L, "640380", "Étang", Protocole.STANDARD, null, "2026-01-01", "u-1");
    private static final PointDEcoute POINT = new PointDEcoute(1L, "A1", null, null, null, 1L);

    @Test
    @DisplayName("Aperçu vide tant que le site n'est pas choisi")
    void apercu_vide_sans_site() {
        assertThat(ApercuPrefixe.calculer(null, POINT, 2026, 1, null)).isEmpty();
    }

    @Test
    @DisplayName("Aperçu vide tant que le point n'est pas choisi")
    void apercu_vide_sans_point() {
        assertThat(ApercuPrefixe.calculer(SITE, null, 2026, 1, null)).isEmpty();
    }

    @Test
    @DisplayName("Rattachement complet : aperçu non vide construit sur un exemple par défaut (sans inspection)")
    void apercu_non_vide_avec_rattachement_complet() {
        String apercu = ApercuPrefixe.calculer(SITE, POINT, 2026, 1, null);

        assertThat(apercu).isNotBlank().contains("640380");
    }
}
