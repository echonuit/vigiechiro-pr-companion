package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
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
        TransportVigieChiro transport = new TransportVigieChiro("http://localhost:1/api/v1", TOKEN_ABC);

        assertThat(transport.deposerVersS3(
                        "http://localhost:1/s3/signe",
                        () -> HttpRequest.BodyPublishers.ofByteArray(new byte[] {1}),
                        "audio/x-wav"))
                .isFalse();
        assertThat(transport.deposerVersS3(
                        "http://localhost:1/s3/signe",
                        () -> {
                            throw new java.io.IOException("fichier illisible");
                        },
                        "application/zip"))
                .isFalse();
    }
}
