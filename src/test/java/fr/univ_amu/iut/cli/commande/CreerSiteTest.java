package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.cli.GesteAttenduCli;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `creer-site` **demande à la plateforme** avant de créer (#3856).
///
/// ## Le doublon que cette garde empêche
///
/// L'écran empêche depuis #3806 de déclarer un carré que Vigie-Chiro porte déjà : le clic produisait
/// deux sites pour un même carré, dont le local n'est rattaché à rien, et la nuit déposée ensuite
/// échouait loin de sa cause. `creer-site --carre 130711` le fabriquait encore **sans rien demander**.
///
/// ⚠️ Le versant le plus facile à manquer n'est pas le refus, c'est ce qui arrive quand on **ne peut
/// pas** demander : l'application sert sur le terrain, et refuser faute d'avoir pu vérifier la rendrait
/// inutilisable là où elle sert le plus.
class CreerSiteTest {

    private static final String CARRE = "130711";

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);
    private final ServiceSites service = mock(ServiceSites.class);
    private final StringWriter sortie = new StringWriter();
    private final StringWriter erreur = new StringWriter();

    private int executer(String... args) {
        CommandLine ligne = new CommandLine(new CreerSite(service, client, () -> "u-1"));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(erreur, true));
        // Le handler qui traduit un refus métier en code 2 vit dans `Cli` : sans lui, picocli rendrait
        // son code générique (1) et le test mesurerait le harnais, pas la commande.
        ligne.setExecutionExceptionHandler((exception, commande, parse) -> {
            if (exception instanceof RegleMetierException refus) {
                commande.getErr().println("Refus : " + GesteAttenduCli.message(refus));
                return 2;
            }
            throw exception;
        });
        return ligne.execute(args);
    }

    private static SiteVigieChiro pointFixe() {
        return new SiteVigieChiro("6a49", "Vigiechiro - Point Fixe-" + CARRE, true);
    }

    private void siteCree() {
        when(service.creerSite(anyString(), any(), any(), any(), anyString()))
                .thenReturn(new Site(7L, CARRE, null, Protocole.STANDARD, null, "2026-08-16", "u-1"));
    }

    @Test
    @DisplayName("#3856 : le carré existe en Point Fixe là-bas → refus, et RIEN n'est créé")
    void refuse_un_carre_qui_existe_deja_en_point_fixe() {
        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.succes(List.of(pointFixe())));

        int code = executer("--carre", CARRE);

        assertThat(code).isEqualTo(2);
        assertThat(erreur.toString()).contains("existe déjà").contains("Récupérer ce carré");
        verify(service, never()).creerSite(anyString(), any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("#3856 : le carré n'y existe que sous un AUTRE protocole → on crée")
    void cree_quand_le_carre_n_existe_pas_en_point_fixe() {
        when(client.chercherCarre(CARRE))
                .thenReturn(
                        ReponseApi.succes(List.of(new SiteVigieChiro("6a50", "Vigie-chiro - Routier-" + CARRE, true))));
        siteCree();

        assertThat(executer("--carre", CARRE)).isZero();

        // Companion ne gère que le Point Fixe : il n'y a aucun homologue à rattacher, donc aucun doublon.
        verify(service).creerSite(anyString(), any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("#3856 : plateforme injoignable → on crée QUAND MÊME, et on dit que rien n'a été vérifié")
    void cree_hors_connexion_en_le_disant() {
        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.injoignable("timeout"));
        siteCree();

        assertThat(executer("--carre", CARRE)).isZero();

        // Ni silence - qui laisserait croire à une vérification réussie - ni refus, qui rendrait la
        // commande inutilisable sur le terrain.
        assertThat(erreur.toString()).contains("non vérifié").contains("ne sera pas rattaché");
        verify(service).creerSite(anyString(), any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("#3856 : « --sans-verification » ne fait partir AUCUNE requête")
    void sans_verification_ne_demande_rien() {
        siteCree();

        assertThat(executer("--carre", CARRE, "--sans-verification")).isZero();

        verify(client, never()).chercherCarre(anyString());
        verify(service).creerSite(anyString(), any(), any(), any(), anyString());
    }
}
