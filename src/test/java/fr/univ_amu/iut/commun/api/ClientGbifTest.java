package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Résolution GBIF d'un nom latin en clé d'usage (#922), et **corps lu sous plafond** (#3222).
///
/// Cette classe n'avait aucun test : son client HTTP était construit en dur, donc rien ne pouvait la
/// mettre en situation. #3222 y a posé la même couture d'injection que [ResolveurCommuneApiGeo], et
/// ces cas en sont la contrepartie - on ne modifie pas une classe qu'on ne peut pas éprouver.
///
/// Le contrat est **best-effort** : toute anomalie rend une résolution vide, et l'appelant retombe sur
/// la page de recherche. Chaque cas ci-dessous vérifie donc qu'un échec ne remonte **pas** en
/// exception.
class ClientGbifTest {

    @AfterEach
    void rendreLePlafond() {
        System.clearProperty("vigiechiro.reseau.corps.max-octets");
    }

    @Test
    @DisplayName("Une correspondance rend la clé d'usage")
    void correspondance_rend_la_cle() throws Exception {
        ClientGbif gbif = new ClientGbif(repondant(200, "{\"matchType\":\"EXACT\",\"usageKey\":2432598}", Map.of()));

        assertThat(gbif.cleUsage("Pipistrellus pipistrellus")).contains(2432598L);
    }

    @Test
    @DisplayName("Un matchType NONE ne rend aucune clé, même si le corps en porte une")
    void aucune_correspondance() throws Exception {
        ClientGbif gbif = new ClientGbif(repondant(200, "{\"matchType\":\"NONE\",\"usageKey\":1}", Map.of()));

        assertThat(gbif.cleUsage("Chose inexistante")).isEmpty();
    }

    @Test
    @DisplayName("Un statut non 200 rend une résolution vide, sans exception")
    void statut_non_200() throws Exception {
        ClientGbif gbif = new ClientGbif(repondant(503, "", Map.of()));

        assertThat(gbif.cleUsage("Pipistrellus pipistrellus")).isEmpty();
    }

    @Test
    @DisplayName("Un nom vide n'appelle pas le réseau")
    void nom_vide_sans_appel() throws Exception {
        HttpClient client = mock(HttpClient.class);
        doThrow(new IOException("aucun appel ne doit partir")).when(client).send(any(), any());

        assertThat(new ClientGbif(client).cleUsage("   ")).isEmpty();
    }

    @Test
    @DisplayName("Un corps au-delà du plafond ne fait pas tomber la résolution, il la rend vide (#3222)")
    void corps_hors_plafond_degrade_sans_lever() throws Exception {
        System.setProperty("vigiechiro.reseau.corps.max-octets", "16");
        ClientGbif gbif = new ClientGbif(repondant(200, "{\"matchType\":\"EXACT\",\"usageKey\":2432598}", Map.of()));

        assertThat(gbif.cleUsage("Pipistrellus pipistrellus"))
                .as("le refus retombe dans le filet best-effort : une fiche d'espèce est un confort")
                .isEmpty();
    }

    private static HttpClient repondant(int statut, String corps, Map<String, List<String>> entetes) throws Exception {
        byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
        HttpResponse<InputStream> reponse = mock(HttpResponse.class);
        when(reponse.statusCode()).thenReturn(statut);
        when(reponse.body()).thenAnswer(appel -> new ByteArrayInputStream(octets));
        when(reponse.headers()).thenReturn(HttpHeaders.of(entetes, (nom, valeur) -> true));
        HttpClient client = mock(HttpClient.class);
        doReturn(reponse).when(client).send(any(), any());
        return client;
    }
}
