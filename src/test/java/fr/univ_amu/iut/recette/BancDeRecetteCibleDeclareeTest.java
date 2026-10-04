package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.plateforme.PlateformeDeTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// Sous le profil de test, les voies du banc vers « la plateforme » mènent à la plateforme de test (#5793).
///
/// Un scénario connecté sert les deux cibles : il demande « la plateforme », et le banc lit la cible que
/// le profil déclare. Les scénarios connectés ne tournent qu'au tournage, jamais sur une demande : sans
/// ce cas, une régression de cette lecture ne ferait rougir aucune demande de fusion.
@Tag("plateforme-de-test")
class BancDeRecetteCibleDeclareeTest {

    @Test
    @DisplayName("#5793 : connecteALaPlateforme() vise la plateforme de test, et y dépose le jeton")
    void connecter_vise_la_plateforme_de_test() {
        BancDeRecette banc = BancDeRecette.surLeChrome().connecteALaPlateforme();

        assertThat(banc.viseLaPlateformeDeTest())
                .as("la cible déclarée par le profil")
                .isTrue();
        assertThat(banc.deposeLeJetonDeTest())
                .as("la modale le revérifie seule, comme sur la nationale")
                .isTrue();
    }

    @Test
    @DisplayName("#5793 : parleALaPlateforme() vise la plateforme de test, sans rien déposer")
    void parler_vise_la_plateforme_de_test_sans_deposer() {
        BancDeRecette banc = BancDeRecette.surLeChrome().parleALaPlateforme();

        assertThat(banc.viseLaPlateformeDeTest()).isTrue();
        assertThat(banc.deposeLeJetonDeTest())
                .as("le scénario colle le jeton lui-même")
                .isFalse();
    }

    @Test
    @DisplayName("#5793 : le jeton rendu au scénario est celui que la plateforme de test a frappé")
    void le_jeton_rendu_est_celui_de_la_plateforme_de_test() {
        assertThat(BancDeRecette.jetonDeLaPlateforme())
                .isEqualTo(PlateformeDeTest.acces().jeton(BancDeRecette.UTILISATRICE_DU_TOURNAGE));
    }
}
