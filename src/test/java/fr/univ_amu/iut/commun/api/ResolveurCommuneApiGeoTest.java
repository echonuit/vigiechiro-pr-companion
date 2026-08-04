package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.Commune;
import fr.univ_amu.iut.commun.model.PositionGeo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Résolveur de commune API Géo (#2791) : parseur testé sur des corps figés, chemin réseau simulé
/// par Mockito sur [HttpClient] (aucun appel réel - JPMS interdit un serveur HTTP local en test).
/// Le contrat est **best-effort** : chaque branche d'échec doit rendre vide, jamais lever.
class ResolveurCommuneApiGeoTest {

    private static final String CORPS_AIX = "[{\"nom\":\"Aix-en-Provence\",\"code\":\"13001\"}]";
    private static final PositionGeo AIX = new PositionGeo(43.5297, 5.4474);

    @Test
    @DisplayName("Une réponse nominale donne la commune, nom et code INSEE")
    void lecture_nominale() {
        assertThat(ResolveurCommuneApiGeo.lire(CORPS_AIX)).contains(new Commune("Aix-en-Provence", "13001"));
    }

    @Test
    @DisplayName("Tableau vide (point en mer, hors de France) : vide, sans erreur")
    void tableau_vide() {
        assertThat(ResolveurCommuneApiGeo.lire("[]")).isEmpty();
    }

    @Test
    @DisplayName("Corps illisible ou incomplet : vide, jamais d'exception")
    void corps_illisible() {
        assertThat(ResolveurCommuneApiGeo.lire("pas du json")).isEmpty();
        assertThat(ResolveurCommuneApiGeo.lire("{\"nom\":\"objet, pas tableau\"}"))
                .isEmpty();
        assertThat(ResolveurCommuneApiGeo.lire("[{\"code\":\"13001\"}]"))
                .as("nom absent")
                .isEmpty();
        assertThat(ResolveurCommuneApiGeo.lire("[{\"nom\":\"Aix-en-Provence\"}]"))
                .as("code absent")
                .isEmpty();
    }

    @Test
    @DisplayName("Statut 200 : la commune remonte du corps, via l'URL lat/lon attendue")
    void statut_200_et_url() throws IOException, InterruptedException {
        HttpClient client = clientRepondant(200, CORPS_AIX);

        assertThat(new ResolveurCommuneApiGeo("http://exemple.invalid", client).resoudre(AIX))
                .contains(new Commune("Aix-en-Provence", "13001"));
        verify(client)
                .send(
                        argThat(requete -> requete.uri()
                                .toString()
                                .equals("http://exemple.invalid/communes?lat=43.5297&lon=5.4474"
                                        + "&fields=nom,code&format=json")),
                        any());
    }

    @Test
    @DisplayName("Statut inattendu (500) : vide, sans erreur")
    void statut_inattendu() throws IOException, InterruptedException {
        assertThat(new ResolveurCommuneApiGeo("http://exemple.invalid", clientRepondant(500, "indisponible"))
                        .resoudre(AIX))
                .isEmpty();
    }

    @Test
    @DisplayName("Hors ligne (IOException) : vide, sans erreur - le rattrapage comblera")
    void hors_ligne() throws IOException, InterruptedException {
        HttpClient client = mock(HttpClient.class);
        doThrow(new IOException("réseau coupé")).when(client).send(any(), any());

        assertThat(new ResolveurCommuneApiGeo("http://exemple.invalid", client).resoudre(AIX))
                .isEmpty();
    }

    @Test
    @DisplayName("Position nulle : vide, sans appel réseau")
    void position_nulle() {
        assertThat(new ResolveurCommuneApiGeo().resoudre(null)).isEmpty();
    }

    private static HttpClient clientRepondant(int statut, String corps) throws IOException, InterruptedException {
        byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
        HttpResponse<InputStream> reponse = mock(HttpResponse.class);
        when(reponse.statusCode()).thenReturn(statut);
        // Corps en flux depuis #3222 : lu sous plafond, en-tetes consultes (Content-Length annonce).
        when(reponse.body()).thenAnswer(appel -> new ByteArrayInputStream(octets));
        when(reponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (nom, valeur) -> true));
        HttpClient client = mock(HttpClient.class);
        doReturn(reponse).when(client).send(any(), any());
        return client;
    }
}
