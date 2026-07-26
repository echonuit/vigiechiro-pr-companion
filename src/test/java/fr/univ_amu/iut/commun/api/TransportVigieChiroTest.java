package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Mécanique HTTP et **tri des issues** (#1284) : chaque cause d'échec a sa variante [ReponseApi],
/// plus jamais un silence indistinct. Aucun appel réseau réel : pas de jeton et URL injoignable
/// exercent les deux premières variantes, le triage des statuts est une fonction pure (JPMS interdit
/// un serveur HTTP local en test).
class TransportVigieChiroTest {

    private static final FournisseurToken SANS_TOKEN = Optional::empty;
    private static final FournisseurToken TOKEN_ABC = () -> Optional.of("abc");

    @Test
    @DisplayName("enteteAuthorization : Basic base64(token:) ; token en username, mot de passe vide")
    void entete_authorization() {
        TransportVigieChiro transport = new TransportVigieChiro("http://localhost:1", TOKEN_ABC);

        // base64("abc:") = "YWJjOg=="
        assertThat(transport.enteteAuthorization()).contains("Basic YWJjOg==");
    }

    @Test
    @DisplayName("sans jeton → NonConnecte, sans même toucher le réseau (le silence légitime)")
    void sans_jeton_est_non_connecte() {
        TransportVigieChiro transport = new TransportVigieChiro("http://localhost:1", SANS_TOKEN);

        assertThat(transport.enteteAuthorization()).isEmpty();
        assertThat(transport.lire("/moi")).isInstanceOf(ReponseApi.NonConnecte.class);
        assertThat(transport.ecrire("POST", "/fichiers", "{}", null)).isInstanceOf(ReponseApi.NonConnecte.class);
    }

    @Test
    @DisplayName("URL injoignable → Injoignable avec cause, sans lever : plus jamais un faux « vide »")
    void hors_ligne_est_injoignable() {
        TransportVigieChiro transport = new TransportVigieChiro("http://localhost:1/api/v1", TOKEN_ABC);

        assertThat(transport.lire("/moi")).isInstanceOf(ReponseApi.Injoignable.class);
        assertThat(transport.ecrire("PATCH", "/participations/p1", "{}", "e1"))
                .isInstanceOf(ReponseApi.Injoignable.class);
        assertThat(transport.telecharger("http://localhost:1/s3/signe")).isInstanceOf(ReponseApi.Injoignable.class);
    }

    @Test
    @DisplayName("triage : 2xx → Succes (corps), tout autre statut → Refuse (statut + corps conservés)")
    void triage_des_statuts() {
        assertThat(TransportVigieChiro.triage(200, "{\"ok\":1}")).isEqualTo(ReponseApi.succes("{\"ok\":1}"));
        assertThat(TransportVigieChiro.triage(201, "cree")).isEqualTo(ReponseApi.succes("cree"));

        // Le 422 de #1277 (max_results=1000) : l'information qui était jetée est désormais conservée.
        assertThat(TransportVigieChiro.triage(422, "{\"_issues\": {\"max_results\": \"...\"}}"))
                .isEqualTo(ReponseApi.refuse(422, "{\"_issues\": {\"max_results\": \"...\"}}"));
        assertThat(TransportVigieChiro.triage(403, "interdit")).isEqualTo(ReponseApi.refuse(403, "interdit"));
        assertThat(TransportVigieChiro.triage(500, "boom")).isEqualTo(ReponseApi.refuse(500, "boom"));
    }

    @Test
    @DisplayName("cause : un délai dépassé est nommé ; les autres pannes gardent leur message")
    void cause_lisible() {
        assertThat(TransportVigieChiro.cause(new HttpTimeoutException("request timed out")))
                .isEqualTo("délai d'attente dépassé");
        assertThat(TransportVigieChiro.cause(new java.net.ConnectException("Connection refused")))
                .isEqualTo("Connection refused");
        assertThat(TransportVigieChiro.cause(new IllegalStateException())).isEqualTo("IllegalStateException");
    }

    @Test
    @DisplayName("#1845 : la sévérité se décide à l'émission — anomalie visible, échange nominal au détail")
    void severite_decidee_a_l_emission() {
        assertThat(TransportVigieChiro.niveauDe(ReponseApi.succes("{}"))).isEqualTo(Level.FINE);
        assertThat(TransportVigieChiro.niveauDe(ReponseApi.nonConnecte()))
                .as("un appel non émis faute de jeton n'est pas une anomalie")
                .isEqualTo(Level.FINE);
        assertThat(TransportVigieChiro.niveauDe(ReponseApi.injoignable("délai dépassé")))
                .isEqualTo(Level.WARNING);
        assertThat(TransportVigieChiro.niveauDe(ReponseApi.refuse(422, "boom"))).isEqualTo(Level.WARNING);
    }

    @Test
    @DisplayName("#1845 : le résumé porte méthode, chemin, issue et durée — et le corps d'un REFUS")
    void resume_consigne_l_essentiel() {
        String refus = TransportVigieChiro.resume(
                "PATCH", "/participations/p1", ReponseApi.refuse(422, "{\"_issues\": {\"numero\": \"invalid\"}}"), 12);

        assertThat(refus)
                .as("l'explication du serveur est l'élément le plus diagnostique : c'est elle qui manquait")
                .contains("PATCH")
                .contains("/participations/p1")
                .contains("422")
                .contains("invalid")
                .contains("12 ms");
    }

    @Test
    @DisplayName("#1845 : le corps d'un refus est TRONQUÉ — un journal n'est pas un déversoir")
    void resume_tronque_un_corps_volumineux() {
        String enorme = "x".repeat(5000);

        String resume = TransportVigieChiro.resume("GET", "/moi", ReponseApi.refuse(500, enorme), 3);

        assertThat(resume)
                .as("le corps est coupé et l'ellipse le signale ; la durée reste en fin de ligne")
                .hasSizeLessThan(500)
                .contains("…")
                .endsWith("(3 ms)");
    }

    @Test
    @DisplayName("#1845 : un échange consigné ne porte JAMAIS le jeton (journal joignable à un rapport)")
    void journal_ne_porte_jamais_le_jeton() {
        Logger journal = Logger.getLogger(TransportVigieChiro.class.getName());
        List<LogRecord> captures = new ArrayList<>();
        Handler capteur = new Handler() {
            @Override
            public void publish(LogRecord record) {
                captures.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        capteur.setLevel(Level.ALL);
        journal.addHandler(capteur);
        journal.setLevel(Level.ALL);
        try {
            // Injoignable (localhost:1) : l'échange EST consigné, avec sa cause.
            new TransportVigieChiro("http://localhost:1/api/v1", TOKEN_ABC).lire("/moi");
        } finally {
            journal.removeHandler(capteur);
        }

        assertThat(captures).as("l'échange réseau laisse enfin une trace").isNotEmpty();
        LogRecord trace = captures.get(0);
        assertThat(trace.getLevel()).isEqualTo(Level.WARNING);
        assertThat(trace.getMessage()).contains("GET").contains("/api/v1/moi").contains("injoignable");
        assertThat(trace.getMessage())
                .as("ni le jeton « abc » ni son encodage Basic ne doivent apparaître")
                .doesNotContain("abc")
                .doesNotContain("YWJjOg==")
                .doesNotContain("Basic");
    }

    @Test
    @DisplayName("deposerVersS3 : hors-ligne ou corps illisible → false, sans lever")
    void depot_s3_degrade_en_booleen() {
        // Politique sans attente : l'URL injoignable épuise les tentatives instantanément (sinon le
        // réessai réel dormirait plusieurs secondes, #2354).
        TransportVigieChiro transport = new TransportVigieChiro(
                "http://localhost:1/api/v1", TOKEN_ABC, HttpClient.newHttpClient(), sansAttente());

        assertThat(transport.deposerVersS3(
                        "http://localhost:1/s3/signe",
                        () -> HttpRequest.BodyPublishers.ofByteArray(new byte[] {1}),
                        "audio/x-wav"))
                .isFalse();
        assertThat(transport.deposerVersS3(
                        "http://localhost:1/s3/signe",
                        () -> {
                            throw new IOException("fichier illisible");
                        },
                        "application/zip"))
                .isFalse();
    }

    @Test
    @DisplayName("#2354 : une coupure momentanée est réessayée, puis le PUT S3 réussit")
    void depot_s3_reessaie_une_coupure_puis_reussit() throws Exception {
        HttpResponse<Void> ok = reponse(200, Map.of());
        HttpClient client = mock(HttpClient.class);
        doThrow(new IOException("paquet perdu")).doReturn(ok).when(client).send(any(), any());
        List<Duration> attentes = new ArrayList<>();
        TransportVigieChiro transport =
                new TransportVigieChiro("http://s3.exemple/api/v1", TOKEN_ABC, client, sansAttente(attentes));

        boolean depose = transport.deposerVersS3("http://s3.exemple/signe", octetUnique(), "application/zip");

        assertThat(depose).as("la seconde tentative aboutit").isTrue();
        assertThat(attentes).as("une seule attente : une coupure, une reprise").hasSize(1);
    }

    @Test
    @DisplayName("#2354 : un refus définitif (4xx) du PUT S3 n'est jamais rejoué")
    void depot_s3_ne_reessaie_pas_un_refus_definitif() throws Exception {
        // 403 SignatureDoesNotMatch : rejouer ne le rendra pas valide.
        HttpResponse<Void> refus = reponse(403, Map.of());
        HttpClient client = mock(HttpClient.class);
        doReturn(refus).when(client).send(any(), any());
        List<Duration> attentes = new ArrayList<>();
        TransportVigieChiro transport =
                new TransportVigieChiro("http://s3.exemple/api/v1", TOKEN_ABC, client, sansAttente(attentes));

        boolean depose = transport.deposerVersS3("http://s3.exemple/signe", octetUnique(), "application/zip");

        assertThat(depose).isFalse();
        assertThat(attentes).as("aucune attente : un 4xx ne se rejoue pas").isEmpty();
    }

    @Test
    @DisplayName("#2354 : le PUT S3 respecte le Retry-After du serveur (503 → attente imposée → succès)")
    void depot_s3_respecte_retry_after() throws Exception {
        HttpResponse<Void> occupe = reponse(503, Map.of("Retry-After", List.of("2")));
        HttpResponse<Void> ok = reponse(200, Map.of());
        HttpClient client = mock(HttpClient.class);
        doReturn(occupe).doReturn(ok).when(client).send(any(), any());
        List<Duration> attentes = new ArrayList<>();
        TransportVigieChiro transport =
                new TransportVigieChiro("http://s3.exemple/api/v1", TOKEN_ABC, client, sansAttente(attentes));

        boolean depose = transport.deposerVersS3("http://s3.exemple/signe", octetUnique(), "application/zip");

        assertThat(depose).isTrue();
        assertThat(attentes).as("le délai du serveur fait autorité").containsExactly(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("#2354 : une coupure persistante épuise les tentatives du profil premier plan")
    void depot_s3_epuise_les_tentatives_sur_coupure_persistante() throws Exception {
        HttpClient client = mock(HttpClient.class);
        doThrow(new IOException("réseau à terre")).when(client).send(any(), any());
        List<Duration> attentes = new ArrayList<>();
        TransportVigieChiro transport =
                new TransportVigieChiro("http://s3.exemple/api/v1", TOKEN_ABC, client, sansAttente(attentes));

        boolean depose = transport.deposerVersS3("http://s3.exemple/signe", octetUnique(), "application/zip");

        assertThat(depose).isFalse();
        assertThat(attentes).as("premier plan : 4 tentatives, donc 3 attentes").hasSize(3);
    }

    @Test
    @DisplayName("#2354 : deposerPartie rend Succes portant l'ETag S3 (guillemets retirés)")
    void depot_partie_rend_l_etag() throws Exception {
        HttpResponse<Void> ok = reponse(200, Map.of("ETag", List.of("\"etag-abc\"")));
        HttpClient client = mock(HttpClient.class);
        doReturn(ok).when(client).send(any(), any());
        TransportVigieChiro transport =
                new TransportVigieChiro("http://s3.exemple/api/v1", TOKEN_ABC, client, sansAttente());

        ReponseApi<String> issue = transport.deposerPartie(
                "http://s3.exemple/part-1", octetUnique(), "application/zip", SuiviReprise.SILENCIEUX);

        assertThat(issue).isEqualTo(ReponseApi.succes("etag-abc"));
    }

    @Test
    @DisplayName("#2354 : deposerPartie réessaie une coupure momentanée, puis rend l'ETag")
    void depot_partie_reessaie_puis_reussit() throws Exception {
        HttpResponse<Void> ok = reponse(200, Map.of("ETag", List.of("etag-2")));
        HttpClient client = mock(HttpClient.class);
        doThrow(new IOException("coupure")).doReturn(ok).when(client).send(any(), any());
        List<Duration> attentes = new ArrayList<>();
        TransportVigieChiro transport =
                new TransportVigieChiro("http://s3.exemple/api/v1", TOKEN_ABC, client, sansAttente(attentes));

        ReponseApi<String> issue = transport.deposerPartie(
                "http://s3.exemple/part-1", octetUnique(), "application/zip", SuiviReprise.SILENCIEUX);

        assertThat(issue).isEqualTo(ReponseApi.succes("etag-2"));
        assertThat(attentes).hasSize(1);
    }

    /// Corps d'un octet, reconstruit à chaque tentative (le publisher n'est pas rejouable une fois lu).
    private static TransportVigieChiro.CorpsAEnvoyer octetUnique() {
        return () -> HttpRequest.BodyPublishers.ofByteArray(new byte[] {1});
    }

    /// Politique sans vraie attente (aléa nul), pour des tests instantanés.
    private static PolitiqueReessai sansAttente() {
        return new PolitiqueReessai(delai -> {}, () -> 0.0);
    }

    /// Politique sans vraie attente qui **note** les durées demandées, pour compter les reprises.
    private static PolitiqueReessai sansAttente(List<Duration> attentes) {
        return new PolitiqueReessai(attentes::add, () -> 0.0);
    }

    private static HttpResponse<Void> reponse(int statut, Map<String, List<String>> entetes) {
        HttpResponse<Void> reponse = mock(HttpResponse.class);
        when(reponse.statusCode()).thenReturn(statut);
        when(reponse.headers()).thenReturn(HttpHeaders.of(entetes, (nom, valeur) -> true));
        return reponse;
    }
}
