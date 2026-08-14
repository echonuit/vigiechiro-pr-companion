package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// « Ce carré existe-t-il déjà sur Vigie-Chiro ? » (#3458), la question que l'utilisateur ne pouvait
/// poser que depuis le portail.
class RechercheCarreExistantTest {

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);
    private final RechercheCarreExistant recherche = new RechercheCarreExistant(client);

    @Test
    @DisplayName("#3458 : aucun site pour ce carré, il est libre")
    void aucun_site_le_carre_est_libre() {
        when(client.chercherCarre("999999")).thenReturn(ReponseApi.succes(List.of()));

        assertThat(recherche.chercher("999999")).isInstanceOf(RechercheCarreExistant.Verdict.Inexistant.class);
    }

    @Test
    @DisplayName("#3458 : le carré est déjà déclaré, et le verdict dit sous quel PROTOCOLE")
    void un_carre_deja_declare_dit_son_protocole() {
        when(client.chercherCarre("130711"))
                .thenReturn(
                        ReponseApi.succes(List.of(new SiteVigieChiro("6a49", "Vigiechiro - Point Fixe-130711", true))));

        assertThat(recherche.chercher("130711"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                        RechercheCarreExistant.Verdict.DejaDeclare.class))
                .satisfies(deja -> assertThat(deja.titres())
                        // Un même carré porte un site par protocole : « il existe » sans dire lequel
                        // n'aide pas à décider quoi faire.
                        .containsExactly("Vigiechiro - Point Fixe-130711"));
    }

    @Test
    @DisplayName("#3458 : hors connexion, on ne SAIT PAS - et surtout on ne dit pas « il est libre »")
    void hors_connexion_on_ne_sait_pas() {
        when(client.chercherCarre("130711")).thenReturn(ReponseApi.nonConnecte());

        // Confondre « je ne sais pas » et « il n'existe pas » ferait déclarer un carré déjà pris à
        // quelqu'un qui croit avoir vérifié : exactement la panne que cette classe évite.
        assertThat(recherche.chercher("130711")).isInstanceOf(RechercheCarreExistant.Verdict.Indisponible.class);
    }

    @Test
    @DisplayName("#3458 : plateforme injoignable ou refus, même prudence")
    void injoignable_ou_refus_meme_prudence() {
        when(client.chercherCarre("130711")).thenReturn(ReponseApi.injoignable("timeout"));
        assertThat(recherche.chercher("130711")).isInstanceOf(RechercheCarreExistant.Verdict.Indisponible.class);

        when(client.chercherCarre("130711")).thenReturn(ReponseApi.refuse(403, ""));
        assertThat(recherche.chercher("130711")).isInstanceOf(RechercheCarreExistant.Verdict.Indisponible.class);
    }
}
