package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Contrôle du carré STOC (#733) : la plateforme est bouchonnée, on vérifie le **verdict** rendu et, avec
/// lui, ce que l'observateur va lire.
///
/// ⚠️ Depuis #1284, un retour `ReponseApi` non bouchonné vaut **`null`** : chaque cas stube explicitement.
class ControleCarreStocTest {

    private static final String CARRE_DECLARE = "130711";
    private static final double LATITUDE = 43.5298;
    private static final double LONGITUDE = 5.4474;

    private ClientVigieChiro client;
    private ControleCarreStoc controle;

    @BeforeEach
    void preparer() {
        client = mock(ClientVigieChiro.class);
        controle = new ControleCarreStoc(client);
    }

    @Test
    @DisplayName("Le point tombe dans le carré déclaré : la saisie est confirmée")
    void carre_concordant() {
        repond(ReponseApi.succes(Optional.of(CARRE_DECLARE)));

        VerdictCarre verdict = controle.confronter(CARRE_DECLARE, LATITUDE, LONGITUDE);

        assertThat(verdict).isInstanceOf(VerdictCarre.Concorde.class);
        assertThat(verdict.alerte()).isFalse();
        assertThat(verdict.message()).contains(CARRE_DECLARE);
    }

    @Test
    @DisplayName("Le point tombe dans un AUTRE carré : la divergence est dite, les deux numéros à l'appui")
    void carre_divergent() {
        repond(ReponseApi.succes(Optional.of("130712")));

        VerdictCarre verdict = controle.confronter(CARRE_DECLARE, LATITUDE, LONGITUDE);

        assertThat(verdict).isEqualTo(new VerdictCarre.Diverge("130712", CARRE_DECLARE));
        assertThat(verdict.alerte())
                .as("c'est le cas qui justifie toute l'issue : une faute de frappe sur le carré contamine"
                        + " ensuite le préfixe de tous les fichiers")
                .isTrue();
        assertThat(verdict.message()).contains("130712").contains(CARRE_DECLARE);
    }

    @Test
    @DisplayName("Aucun carré à cette position : coordonnées probablement fausses (ou inversées)")
    void hors_grille() {
        repond(ReponseApi.succes(Optional.empty()));

        VerdictCarre verdict = controle.confronter(CARRE_DECLARE, LATITUDE, LONGITUDE);

        assertThat(verdict).isInstanceOf(VerdictCarre.HorsGrille.class);
        assertThat(verdict.alerte()).isTrue();
    }

    @Test
    @DisplayName("Hors connexion : SILENCE (le contrôle est un confort, pas une condition de saisie)")
    void hors_connexion_se_tait() {
        repond(new ReponseApi.NonConnecte<>());

        VerdictCarre verdict = controle.confronter(CARRE_DECLARE, LATITUDE, LONGITUDE);

        assertThat(verdict).isInstanceOf(VerdictCarre.Indisponible.class);
        assertThat(verdict.message())
                .as("travailler hors ligne est normal : le dire à chaque frappe serait du bruit")
                .isEmpty();
        assertThat(verdict.alerte()).isFalse();
    }

    @Test
    @DisplayName("Plateforme injoignable ou refus : silence aussi, jamais une accusation à tort")
    void panne_se_tait() {
        repond(new ReponseApi.Injoignable<>("délai dépassé"));
        assertThat(controle.confronter(CARRE_DECLARE, LATITUDE, LONGITUDE).message())
                .isEmpty();

        repond(new ReponseApi.Refuse<>(403, "interdit"));
        assertThat(controle.confronter(CARRE_DECLARE, LATITUDE, LONGITUDE).message())
                .isEmpty();
    }

    private void repond(ReponseApi<Optional<String>> reponse) {
        when(client.carreStoc(LATITUDE, LONGITUDE)).thenReturn(reponse);
    }
}
