package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.cli.GesteAttenduCli;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `lister-participations-vigiechiro` : elle donne l'identifiant que trois autres commandes réclament
/// et qu'aucune ne fournissait.
class ListerParticipationsVigieChiroTest {

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);
    private final StringWriter sortie = new StringWriter();
    private final StringWriter erreur = new StringWriter();

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.token");
    }

    private int executer(String... args) {
        CommandLine ligne = new CommandLine(new ListerParticipationsVigieChiro(client));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(erreur, true));
        // Même traduction que `Cli` : sans elle, picocli rendrait son code générique et le test
        // mesurerait le harnais plutôt que la commande.
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
    @DisplayName("Le tableau porte l'identifiant, la date, le point et le site")
    void tableau_porte_l_identifiant() {
        when(client.mesParticipations())
                .thenReturn(ReponseApi.succes(List.of(new ParticipationVigieChiro(
                        "6a4961f5842983a29ba25363",
                        "Z41",
                        "2026-07-03T20:25:00+00:00",
                        "Vigiechiro - Point Fixe-130711"))));

        int code = executer();

        assertThat(code).isZero();
        assertThat(sortie.toString())
                .as("c'est cet identifiant que réclament importer-vigiechiro et reconstruire-passage")
                .contains("6a4961f5842983a29ba25363")
                .contains("Z41")
                .contains("130711")
                .contains("1 participation(s)");
        assertThat(sortie.toString())
                .as("la date se lit en jour : l'horodatage complet n'aide pas à choisir une nuit")
                .contains("2026-07-03")
                .doesNotContain("20:25:00");
    }

    @Test
    @DisplayName("Un champ absent s'affiche en tiret plutôt qu'en « null »")
    void champ_absent_devient_un_tiret() {
        when(client.mesParticipations())
                .thenReturn(ReponseApi.succes(List.of(new ParticipationVigieChiro("6a49", null, null, null))));

        executer();

        assertThat(sortie.toString()).contains("6a49").contains("-").doesNotContain("null");
    }

    @Test
    @DisplayName("Aucune participation est une réponse, pas un échec : code 0 et phrase claire")
    void aucune_participation_reste_un_succes() {
        when(client.mesParticipations()).thenReturn(ReponseApi.succes(List.of()));

        assertThat(executer()).isZero();
        assertThat(sortie.toString()).contains("Aucune participation");
    }

    @Test
    @DisplayName("--json rend une enveloppe : le compte, puis les éléments")
    void json_est_une_enveloppe() {
        when(client.mesParticipations())
                .thenReturn(ReponseApi.succes(
                        List.of(new ParticipationVigieChiro("6a49", "Z41", "2026-07-03T20:25:00+00:00", "Site"))));

        executer("--json");

        assertThat(sortie.toString())
                .contains("\"participations\": 1")
                .contains("\"participation\": \"6a49\"")
                .contains("\"elements\"");
    }

    @Test
    @DisplayName("Sans jeton : refus motivé (code 2) portant le geste à taper")
    void non_connecte_refuse() {
        when(client.mesParticipations()).thenReturn(ReponseApi.nonConnecte());

        assertThat(executer()).isEqualTo(2);
        assertThat(erreur.toString()).contains("Non connecté").contains("jeton");
    }

    @Test
    @DisplayName("Injoignable ou refusé : code 2, la cause citée")
    void injoignable_et_refus() {
        when(client.mesParticipations()).thenReturn(ReponseApi.injoignable("délai dépassé"));
        assertThat(executer()).isEqualTo(2);
        assertThat(erreur.toString()).contains("injoignable").contains("délai dépassé");

        when(client.mesParticipations()).thenReturn(ReponseApi.refuse(401, "jeton périmé"));
        assertThat(executer()).isEqualTo(2);
        assertThat(erreur.toString()).contains("401");
    }

    @Test
    @DisplayName("--token pose le jeton pour la durée de la commande")
    void token_est_pose() {
        when(client.mesParticipations()).thenReturn(ReponseApi.succes(List.of()));

        executer("--token", "ABC123");

        assertThat(System.getProperty("vigiechiro.token")).isEqualTo("ABC123");
    }
}
