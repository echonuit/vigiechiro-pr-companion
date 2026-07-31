package fr.univ_amu.iut.cli.commande.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `api ressources` : la carte affichée, et - avec `--sonder` - confrontée au serveur.
class ApiRessourcesTest {

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);
    private final StringWriter sortie = new StringWriter();

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.token");
    }

    private int executer(String... args) {
        CommandLine ligne = new CommandLine(new ApiRessources(client));
        ligne.setOut(new PrintWriter(sortie, true));
        return ligne.execute(args);
    }

    @Test
    @DisplayName("Sans --sonder : la carte s'affiche sans toucher au réseau")
    void la_carte_saffiche_sans_reseau() {
        int code = executer();

        assertThat(code).isZero();
        verify(client, never()).lectureBrute(anyString());
        assertThat(sortie.toString())
                .contains("sites")
                .contains("/moi/participations")
                .contains("taxons");
    }

    @Test
    @DisplayName("La carte dit ce qu'il faut savoir avant d'essayer : notes et pièges")
    void la_carte_porte_ses_avertissements() {
        executer();

        assertThat(sortie.toString())
                .as("le nom « liste » trompe, et l'ignorer coûte une pagination pour rien")
                .contains("/sites/liste");
        assertThat(sortie.toString())
                .as("les trois pièges valent pour toutes les lectures")
                .contains("max_results")
                .contains("where=")
                .contains("tout-ou-rien");
    }

    @Test
    @DisplayName("--sonder confronte la carte au serveur, ressource par ressource")
    void sonder_confronte_la_carte_au_serveur() {
        when(client.lectureBrute(anyString())).thenReturn(ReponseApi.succes("{}"));
        when(client.lectureBrute("/donnees")).thenReturn(ReponseApi.injoignable("délai dépassé"));

        int code = executer("--sonder");

        assertThat(code).isZero();
        assertThat(sortie.toString())
                .contains("[répond]")
                .as("une ressource qui ne répond pas doit se voir, avec sa cause")
                .contains("injoignable : délai dépassé");
    }

    @Test
    @DisplayName("Une ressource dont tous les chemins réclament un identifiant se déclare non sondable")
    void ressource_sans_chemin_sondable() {
        when(client.lectureBrute(anyString())).thenReturn(ReponseApi.succes("{}"));

        executer("--sonder");

        assertThat(sortie.toString())
                .as("fabriquer un identifiant qui n'existe pas rendrait un 404 qui ne dirait rien")
                .contains("non sondable sans identifiant");
    }

    @Test
    @DisplayName("--token pose le jeton pour la durée de la commande")
    void token_est_pose() {
        executer("--token", "ABC123");

        assertThat(System.getProperty("vigiechiro.token")).isEqualTo("ABC123");
    }
}
