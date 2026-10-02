package fr.univ_amu.iut.commun.api.plateforme;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// La cible déclarée produit la bonne plateforme, dans les deux sens (#5746).
///
/// Une sonde live qui lirait la cible nationale sous le profil de test ne rougirait pas : sans jeton,
/// elle se sauterait en entier, et un saut sort vert. Ce cas-ci ne saute pas.
@Tag("plateforme-de-test")
class CibleLiveTest {

    @Test
    @DisplayName("#5746 : sous le profil de test, la cible est la plateforme de test, verrous ouverts")
    void le_profil_de_test_designe_la_plateforme_de_test() {
        PlateformeDeTest.Acces acces = PlateformeDeTest.acces();

        CibleLive cible = CibleLive.declaree();

        assertThat(cible)
                .as(
                        "`-Pplateforme-de-test` pose `vigiechiro.cible=%s` ; la cible doit être celle que"
                                + " `PlateformeDeTest` rend, et rien n'y est à protéger",
                        CibleLive.PLATEFORME_DE_TEST)
                .isEqualTo(new CibleLive(
                        acces.urlDeBase(),
                        acces.jeton("observatrice"),
                        true,
                        true,
                        acces.id("participations:nuit-vierge"),
                        acces.id("participations:nuit-traitee")));
    }

    @Test
    @DisplayName("#5746 : sans la propriété, la cible reste la nationale, verrous fermés")
    void sans_la_propriete_la_cible_reste_la_nationale() {
        String cibleDuProfil = System.getProperty("vigiechiro.cible");
        System.clearProperty("vigiechiro.cible");
        System.setProperty("vigiechiro.token", "JETONDELANATIONALE");
        try {
            CibleLive cible = CibleLive.declaree();

            assertThat(cible.urlDeBase()).isEqualTo("https://vigiechiro.herokuapp.com/api/v1");
            assertThat(cible.jeton()).isEqualTo("JETONDELANATIONALE");
            assertThat(cible.ecritureOuverte())
                    .as("un oubli de `vigiechiro.write` laisse les écritures fermées")
                    .isFalse();
            assertThat(cible.messageOuvert()).isFalse();
        } finally {
            System.clearProperty("vigiechiro.token");
            if (cibleDuProfil != null) {
                System.setProperty("vigiechiro.cible", cibleDuProfil);
            }
        }
    }
}
