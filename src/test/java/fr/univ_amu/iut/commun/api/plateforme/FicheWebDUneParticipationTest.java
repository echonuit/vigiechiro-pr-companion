package fr.univ_amu.iut.commun.api.plateforme;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.recette.Attente;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// Un navigateur ouvre la fiche web d'une participation, connecté comme l'observatrice qui la possède
/// (#6061, sous-chantier #5645).
///
/// C'est sur cette fiche que les lots suivants relisent ce que Companion écrit. Le navigateur, sa
/// connexion et sa façon de lire sont dans [NavigateurDeLaPlateforme].
///
/// Les valeurs lues viennent de `etat-de-depart.json` : en changer une fait rougir ce banc.
@Tag("plateforme-de-test")
class FicheWebDUneParticipationTest {

    private static NavigateurDeLaPlateforme navigateur;

    @BeforeAll
    static void ouvrirLaFiche() {
        navigateur = NavigateurDeLaPlateforme.connecteComme("observatrice");
        navigateur.ouvrir("participations/" + PlateformeDeTest.acces().id("participations:nuit-traitee"));
    }

    @AfterAll
    static void fermerLeNavigateur() {
        if (navigateur != null) {
            navigateur.close();
        }
    }

    @Test
    @DisplayName("#6061 : la fiche de la nuit traitée s'ouvre, et dit le point et l'observatrice que l'état déclare")
    void la_fiche_dit_ce_que_l_etat_de_depart_declare() {
        attendreLaFiche();

        assertThat(navigateur.texteDe("body"))
                .as("la fiche doit nommer l'observatrice et le point que `etat-de-depart.json` déclare")
                .contains("effectuée par Observatrice")
                .contains("Point Z1");
    }

    @Test
    @DisplayName("#6061 : la session est celle de l'observatrice, et la charte ne s'interpose pas")
    void la_session_est_celle_de_l_observatrice() {
        attendreLaFiche();

        assertThat(navigateur.nombreDe("#charteModal.in"))
                .as("la charte s'ouvre tant que l'utilisatrice ne l'a pas acceptée : l'état de départ la"
                        + " déclare acceptée pour l'observatrice, sans quoi elle couvrirait la fiche")
                .isZero();
        assertThat(navigateur.texteDe("nav"))
                .as("la barre de navigation nomme l'utilisatrice connectée")
                .contains("Observatrice");
    }

    private static void attendreLaFiche() {
        Attente.que(
                () -> navigateur.texteDe("body").contains("effectuée par"),
                () -> "la fiche de la participation n'a pas paru. La page disait : " + navigateur.texteDe("body"),
                NavigateurDeLaPlateforme.DELAI_D_UNE_PAGE_MS);
    }
}
