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
    @DisplayName("#3806 : le verdict ne renvoie plus à une synchronisation qui ne ramènera pas ce carré")
    void le_verdict_ne_renvoie_plus_a_la_synchronisation() {
        when(client.chercherCarre("130711"))
                .thenReturn(
                        ReponseApi.succes(List.of(new SiteVigieChiro("6a49", "Vigiechiro - Point Fixe-130711", true))));

        String message = recherche.chercher("130711").message();

        // « Récupérer depuis Vigie-Chiro » part des participations : elle n'atteint que les carrés où une
        // nuit est déjà déposée. Sur un carré fraîchement activé - le cas même de #3458 - le geste ne fait
        // rien, et l'utilisateur en conclut que l'application est cassée, ou redéclare le carré.
        assertThat(message).as("ne plus envoyer vers un geste inopérant").doesNotContain("Mes sites");
        // Et déclarer n'est pas l'erreur : préparer une nuit opportuniste commence par là. Ce qui manquait
        // au geste, c'est le rattachement.
        assertThat(message)
                .as("ne plus interdire ce que le parcours opportuniste exige")
                .doesNotContain("Ne le redéclarez pas");
        assertThat(message).containsIgnoringCase("récupér");
    }

    @Test
    @DisplayName("#3458 : hors connexion, on ne SAIT PAS - et surtout on ne dit pas « il est libre »")
    void hors_connexion_on_ne_sait_pas() {
        when(client.chercherCarre("130711")).thenReturn(ReponseApi.nonConnecte());

        // Confondre « je ne sais pas » et « il n'existe pas » ferait déclarer un carré déjà pris à
        // quelqu'un qui croit avoir vérifié : exactement la panne que cette classe évite.
        RechercheCarreExistant.Verdict verdict = recherche.chercher("130711");
        assertThat(verdict).isInstanceOf(RechercheCarreExistant.Verdict.Indisponible.class);

        // Le titre de ce test promettait « on ne dit pas il est libre » ; seul le TYPE du verdict
        // était contrôlé. Un message qui aurait ajouté « il n'existe pas encore » serait passé (#3914).
        assertThat(verdict.message())
                .as("le texte doit dire l'ignorance, jamais l'absence")
                .contains("PAS été vérifié")
                .doesNotContain("n'existe pas")
                .doesNotContain("vous pouvez le déclarer");
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
