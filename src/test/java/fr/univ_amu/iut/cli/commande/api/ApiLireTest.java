package fr.univ_amu.iut.cli.commande.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.cli.GesteAttenduCli;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `api lire` : l'échappatoire d'exploration, et surtout **ce qu'elle refuse**.
///
/// Un tuyau générique rend à l'utilisateur les pièges que le client encapsule. Les deux qui ne
/// préviennent pas quand on tombe dedans - le plafond de pagination et le filtre ignoré - sont refusés
/// **avant tout appel** : les tests le vérifient en s'assurant que le client n'est même pas sollicité.
class ApiLireTest {

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);
    private final StringWriter sortie = new StringWriter();
    private final StringWriter erreur = new StringWriter();

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.token");
    }

    private int executer(String... args) {
        CommandLine ligne = new CommandLine(new ApiLire(client));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(erreur, true));
        ligne.setExecutionExceptionHandler((exception, commande, parse) -> {
            if (exception instanceof RegleMetierException refus) {
                commande.getErr().println("Refus : " + GesteAttenduCli.message(refus));
                return 2;
            }
            throw exception;
        });
        return ligne.execute(args);
    }

    @Test
    @DisplayName("Le corps de la réponse sort tel quel, sans interprétation")
    void le_corps_sort_tel_quel() {
        when(client.lectureBrute("/sites")).thenReturn(ReponseApi.succes("{\"_items\":[]}"));

        int code = executer("--chemin", "/sites");

        assertThat(code).isZero();
        assertThat(sortie.toString()).contains("{\"_items\":[]}");
    }

    @Test
    @DisplayName("Un chemin sans barre oblique initiale est accepté tel qu'on l'a tapé")
    void chemin_sans_barre_est_normalise() {
        when(client.lectureBrute("/moi")).thenReturn(ReponseApi.succes("{}"));

        assertThat(executer("--chemin", "moi")).isZero();
        verify(client).lectureBrute("/moi");
    }

    @Test
    @DisplayName("--page ajoute la pagination au plafond du serveur, jamais au-delà")
    void page_ajoute_la_pagination() {
        when(client.lectureBrute("/sites?max_results=100&page=2")).thenReturn(ReponseApi.succes("{}"));

        assertThat(executer("--chemin", "/sites", "--page", "2")).isZero();
        verify(client).lectureBrute("/sites?max_results=100&page=2");
    }

    @Test
    @DisplayName("max_results au-delà de 100 : refus AVANT tout appel, avec la raison")
    void max_results_hors_plafond_est_refuse_avant_appel() {
        // Le serveur ne tronque pas, il rejette (422). Laisser passer la requête donnerait un refus
        // serveur illisible là où l'on peut expliquer, et c'est exactement ce qui a coûté #1277.
        int code = executer("--chemin", "/sites?max_results=1000");

        assertThat(code).isEqualTo(2);
        assertThat(erreur.toString()).contains("plafonné à 100").contains("422");
        verify(client, never()).lectureBrute(anyString());
    }

    @Test
    @DisplayName("max_results à 100 pile reste autorisé : c'est le maximum, pas un interdit")
    void max_results_au_plafond_passe() {
        when(client.lectureBrute("/sites?max_results=100")).thenReturn(ReponseApi.succes("{}"));

        assertThat(executer("--chemin", "/sites?max_results=100")).isZero();
    }

    @Test
    @DisplayName("where= : refus AVANT tout appel, parce que ce backend l'accepte puis l'ignore")
    void where_est_refuse_avant_appel() {
        // Le pire des deux pièges : la requête réussit, le filtre ne filtre rien, et le total annoncé
        // ne bouge pas. On croit avoir isolé ce qu'on cherchait.
        int code = executer("--chemin", "/sites?where={\"titre\":\"x\"}");

        assertThat(code).isEqualTo(2);
        assertThat(erreur.toString()).contains("IGNORE").contains("triez chez vous");
        verify(client, never()).lectureBrute(anyString());
    }

    @Test
    @DisplayName("Sans jeton : refus motivé (code 2) portant le geste à taper")
    void non_connecte_refuse() {
        when(client.lectureBrute("/sites")).thenReturn(ReponseApi.nonConnecte());

        assertThat(executer("--chemin", "/sites")).isEqualTo(2);
        assertThat(erreur.toString()).contains("Non connecté").contains("jeton");
    }

    @Test
    @DisplayName("Un refus serveur renvoie vers « api ressources » : le chemin existe-t-il ?")
    void refus_serveur_renvoie_vers_la_carte() {
        when(client.lectureBrute("/inexistant")).thenReturn(ReponseApi.refuse(404, "not found"));

        assertThat(executer("--chemin", "/inexistant")).isEqualTo(2);
        assertThat(erreur.toString()).contains("404").contains("api ressources");
    }

    @Test
    @DisplayName("--token pose le jeton pour la durée de la commande")
    void token_est_pose() {
        when(client.lectureBrute("/moi")).thenReturn(ReponseApi.succes("{}"));

        executer("--chemin", "/moi", "--token", "ABC123");

        assertThat(System.getProperty("vigiechiro.token")).isEqualTo("ABC123");
    }
}
