package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.PlafondLecture;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Mécanique HTTP et **tri des issues** (#1284) : chaque cause d'échec a sa variante [ReponseApi],
/// plus jamais un silence indistinct. Aucun appel réseau réel : pas de jeton et URL injoignable
/// exercent les deux premières variantes, le triage des statuts est une fonction pure (JPMS interdit
/// un serveur HTTP local en test).
///
/// Survivants PIT **assumés** sur [JournalEchange] (15 mutants tués sur 17, relevés à la clôture du
/// lot #2722) : dans `extrait`, le libellé « (corps vide) » remplacé par une chaîne vide, et la borne
/// de troncature décalée d'un caractère. Deux détails de mise en forme d'une ligne de journal, qu'un
/// test épinglerait sans rien protéger.
///
/// Les trois autres étaient de **vrais trous**, et concernaient tous ce que le journal **promet** : que
/// l'exception soit attachée à la trace d'une anomalie, et que la durée consignée soit une mesure
/// plausible plutôt qu'une soustraction d'horloge fautive.
class TransportVigieChiroTest {

    private static final FournisseurToken SANS_TOKEN = Optional::empty;
    private static final FournisseurToken TOKEN_ABC = () -> Optional.of("abc");

    /// La **forme réelle** d'une URL signée : `resources/fichiers.py:188` du miroir de l'API les construit
    /// toutes en `https://<bucket>.s3.amazonaws.com/<objet>`, schéma codé en dur.
    ///
    /// Ces fixtures déposaient auparavant vers `http://s3.exemple/…`, du HTTP en clair sur un canal que
    /// la plateforme sert toujours en `https` : elles ne ressemblaient pas à ce qu'elles prétendaient
    /// éprouver, et le garde de #2734 les a refusées à juste titre.
    private static final String URL_S3_SIGNEE =
            "https://vigiechiro.s3.amazonaws.com/5f2b?AWSAccessKeyId=AK&Expires=1&Signature=abc";

    private static final String URL_S3_PARTIE =
            "https://vigiechiro.s3.amazonaws.com/5f2b?partNumber=1&AWSAccessKeyId=AK&Signature=abc";

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
        assertThat(transport.ecrire("POST", "/fichiers", "{}", null, TransportVigieChiro.Rejeu.INTERDIT))
                .isInstanceOf(ReponseApi.NonConnecte.class);
    }

    @Test
    @DisplayName("URL injoignable → Injoignable avec cause, sans lever : plus jamais un faux « vide »")
    void hors_ligne_est_injoignable() {
        TransportVigieChiro transport = new TransportVigieChiro("http://localhost:1/api/v1", TOKEN_ABC);

        assertThat(transport.lire("/moi")).isInstanceOf(ReponseApi.Injoignable.class);
        assertThat(transport.ecrire("PATCH", "/participations/p1", "{}", "e1", TransportVigieChiro.Rejeu.INTERDIT))
                .isInstanceOf(ReponseApi.Injoignable.class);

        // Le téléchargement visait la même chose, mais son URL était en http : depuis #2734 elle serait
        // refusée avant tout appel, et le test prouverait le garde au lieu de la panne réseau. On passe
        // donc par l'échappatoire prévue pour les instances de développement, ce qui l'éprouve aussi.
        System.setProperty(UrlSigneeAdmise.PROPRIETE_HOTES, "localhost");
        try {
            assertThat(transport.telecharger("https://localhost:1/s3/signe"))
                    .isInstanceOf(ReponseApi.Injoignable.class);
        } finally {
            System.clearProperty(UrlSigneeAdmise.PROPRIETE_HOTES);
        }
    }

    @Test
    @DisplayName("#2734 : une URL de stockage en clair est refusée sans qu'aucun appel ne parte")
    void url_signee_en_clair_refusee_sans_appel() {
        // Aucun client HTTP n'est fourni : si le transport tentait l'appel, il partirait pour de vrai.
        TransportVigieChiro transport = new TransportVigieChiro("https://api.exemple/api/v1", TOKEN_ABC);

        ReponseApi<String> issue = transport.telecharger("http://ailleurs.example/s3/signe");

        assertThat(issue).isInstanceOf(ReponseApi.Refuse.class);
        assertThat(issue.estReessayable())
                .as("insister ne rendra pas une URL en clair acceptable")
                .isFalse();
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
        assertThat(JournalEchange.cause(new HttpTimeoutException("request timed out")))
                .isEqualTo("délai d'attente dépassé");
        assertThat(JournalEchange.cause(new java.net.ConnectException("Connection refused")))
                .isEqualTo("Connection refused");
        assertThat(JournalEchange.cause(new IllegalStateException())).isEqualTo("IllegalStateException");
    }

    @Test
    @DisplayName("#1845 : la sévérité se décide à l'émission, anomalie visible, échange nominal au détail")
    void severite_decidee_a_l_emission() {
        assertThat(JournalEchange.niveauDe(ReponseApi.succes("{}"))).isEqualTo(Level.FINE);
        assertThat(JournalEchange.niveauDe(ReponseApi.nonConnecte()))
                .as("un appel non émis faute de jeton n'est pas une anomalie")
                .isEqualTo(Level.FINE);
        assertThat(JournalEchange.niveauDe(ReponseApi.injoignable("délai dépassé")))
                .isEqualTo(Level.WARNING);
        assertThat(JournalEchange.niveauDe(ReponseApi.refuse(422, "boom"))).isEqualTo(Level.WARNING);
    }

    @Test
    @DisplayName("#1845 : le résumé porte méthode, chemin, issue et durée, et le corps d'un REFUS")
    void resume_consigne_l_essentiel() {
        String refus = JournalEchange.resume(
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
    @DisplayName("#1845 : le corps d'un refus est TRONQUÉ, un journal n'est pas un déversoir")
    void resume_tronque_un_corps_volumineux() {
        String enorme = "x".repeat(5000);

        String resume = JournalEchange.resume("GET", "/moi", ReponseApi.refuse(500, enorme), 3);

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
        // Une anomalie emporte SA CAUSE : sans l'exception attachée, la trace dit qu'il y a eu un
        // problème sans dire lequel, et le diagnostic reprend là où #1844 l'avait laissé.
        assertThat(trace.getThrown())
                .as("l'exception qui a causé l'échec est attachée à la trace")
                .isNotNull();
        // La durée consignée est une MESURE : on s'en sert pour distinguer un serveur lent d'un serveur
        // muet. Une soustraction d'horloge fautive donnerait un nombre astronomique que personne ne
        // regarderait, et la trace mentirait sans se trahir.
        assertThat(dureeConsignee(trace.getMessage()))
                .as("la durée d'un appel vers localhost:1 tient dans une minute")
                .isBetween(0L, 60_000L);
    }

    /// La durée en millisecondes que porte un résumé d'échange (« … (12 ms) »).
    private static long dureeConsignee(String resume) {
        java.util.regex.Matcher trouve =
                java.util.regex.Pattern.compile("\\((\\d+) ms\\)").matcher(resume);
        assertThat(trouve.find()).as("le résumé porte une durée : %s", resume).isTrue();
        return Long.parseLong(trouve.group(1));
    }

    @Test
    @DisplayName("#1845 : un échange NOMINAL est consigné sans exception attachée")
    void journal_d_un_echange_sans_echec() {
        Logger journal = Logger.getLogger(TransportVigieChiro.class.getName());
        List<LogRecord> captures = new ArrayList<>();
        Handler capteur = capteurVers(captures);
        capteur.setLevel(Level.ALL);
        journal.addHandler(capteur);
        journal.setLevel(Level.ALL);
        try {
            // Refusée avant tout appel (#2734) : une issue sans exception, donc une trace sans cause.
            new TransportVigieChiro("https://api.exemple/api/v1", TOKEN_ABC)
                    .deposerVersS3(
                            "http://ailleurs.example/signe",
                            () -> HttpRequest.BodyPublishers.ofString("x"),
                            "application/zip");
        } finally {
            journal.removeHandler(capteur);
        }

        // Le refus local n'émet rien : il n'y a pas d'échange à consigner, et c'est bien ainsi - une
        // trace sans appel ferait croire à un aller-retour réseau qui n'a pas eu lieu.
        assertThat(captures)
                .as("un refus qui n'émet aucun appel ne consigne aucun échange")
                .isEmpty();
    }

    private static Handler capteurVers(List<LogRecord> captures) {
        return new Handler() {
            @Override
            public void publish(LogRecord record) {
                captures.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
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

        boolean depose = transport.deposerVersS3(URL_S3_SIGNEE, octetUnique(), "application/zip");

        assertThat(depose).as("la seconde tentative aboutit").isTrue();
        assertThat(attentes).as("une seule attente : une coupure, une reprise").hasSize(1);
    }

    @Test
    @DisplayName("#2619 : une LECTURE coupée est réessayée, puis aboutit")
    void lecture_reessaie_une_coupure_puis_reussit() throws Exception {
        HttpResponse<InputStream> ok = reponseTexte(200, "{\"_id\":\"u-1\"}");
        HttpClient client = mock(HttpClient.class);
        doThrow(new IOException("paquet perdu")).doReturn(ok).when(client).send(any(), any());
        List<Duration> attentes = new ArrayList<>();
        TransportVigieChiro transport =
                new TransportVigieChiro("http://exemple/api/v1", TOKEN_ABC, client, sansAttente(attentes));

        ReponseApi<String> reponse = transport.lire("/moi");

        // Le pare-chocs existait, il ne servait que les écritures du dépôt. Depuis que la synchronisation
        // balaie tout un compte, une coupure d'une seconde faisait ressortir une nuit « non récupérée ».
        assertThat(reponse).isInstanceOf(ReponseApi.Succes.class);
        assertThat(attentes).as("une coupure, une reprise").hasSize(1);
    }

    @Test
    @DisplayName("#2677 : une écriture qui CRÉE n'est jamais rejouée, même sur une coupure réseau")
    void ecriture_qui_cree_n_est_jamais_rejouee() throws Exception {
        HttpClient client = mock(HttpClient.class);
        doThrow(new IOException("paquet perdu")).when(client).send(any(), any());
        List<Duration> attentes = new ArrayList<>();
        TransportVigieChiro transport =
                new TransportVigieChiro("http://exemple/api/v1", TOKEN_ABC, client, sansAttente(attentes));

        ReponseApi<String> reponse =
                transport.ecrire("POST", "/sites/s1/participations", "{}", null, TransportVigieChiro.Rejeu.INTERDIT);

        // Une panne réseau ne dit pas si le serveur a agi : la requête a pu arriver, la participation
        // être créée, et la RÉPONSE se perdre. Insister échangerait une erreur visible contre un doublon
        // silencieux sur la plateforme, qu'aucune route ne permet de retirer.
        assertThat(reponse).isInstanceOf(ReponseApi.Injoignable.class);
        assertThat(attentes)
                .as("aucune reprise : le réessai n'est jamais aveugle (ADR 2354, règle 1)")
                .isEmpty();
        verify(client, times(1)).send(any(), any());
    }

    @Test
    @DisplayName("#2677 : une écriture de valeur absolue, elle, est réessayée")
    void ecriture_idempotente_est_reessayee() throws Exception {
        HttpResponse<InputStream> ok = reponseTexte(200, "{}");
        HttpClient client = mock(HttpClient.class);
        doThrow(new IOException("paquet perdu")).doReturn(ok).when(client).send(any(), any());
        List<Duration> attentes = new ArrayList<>();
        TransportVigieChiro transport =
                new TransportVigieChiro("http://exemple/api/v1", TOKEN_ABC, client, sansAttente(attentes));

        ReponseApi<String> reponse =
                transport.ecrire("PATCH", "/donnees/d1/observations/0", "{}", null, TransportVigieChiro.Rejeu.AUTORISE);

        assertThat(reponse).isInstanceOf(ReponseApi.Succes.class);
        assertThat(attentes)
                .as("poser deux fois la même valeur donne le même état")
                .hasSize(1);
    }

    @Test
    @DisplayName("#2677 : le Retry-After du serveur fait autorité hors S3 aussi (règle 3 de l'ADR)")
    void retry_after_fait_autorite_sur_une_lecture() throws Exception {
        HttpResponse<InputStream> tropVite = reponseTexte(429, "trop vite", Map.of("Retry-After", List.of("7")));
        HttpResponse<InputStream> ok = reponseTexte(200, "{}");
        HttpClient client = mock(HttpClient.class);
        doReturn(tropVite).doReturn(ok).when(client).send(any(), any());
        List<Duration> attentes = new ArrayList<>();
        TransportVigieChiro transport =
                new TransportVigieChiro("http://exemple/api/v1", TOKEN_ABC, client, sansAttente(attentes));

        transport.lire("/moi/participations");

        assertThat(attentes)
                .as("c'est le serveur qui sait : notre temporisation calculée ne doit pas la remplacer")
                .containsExactly(Duration.ofSeconds(7));
    }

    @Test
    @DisplayName("#2619 : une lecture refusée (4xx) n'est jamais rejouée")
    void lecture_ne_reessaie_pas_un_refus_definitif() throws Exception {
        // 401 : le jeton est mort, il ne ressuscitera pas à la seconde tentative.
        HttpResponse<InputStream> refus = reponseTexte(401, "{}");
        HttpClient client = mock(HttpClient.class);
        doReturn(refus).when(client).send(any(), any());
        List<Duration> attentes = new ArrayList<>();
        TransportVigieChiro transport =
                new TransportVigieChiro("http://exemple/api/v1", TOKEN_ABC, client, sansAttente(attentes));

        transport.lire("/moi");

        assertThat(attentes).as("aucune attente : un 4xx ne se rejoue pas").isEmpty();
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

        boolean depose = transport.deposerVersS3(URL_S3_SIGNEE, octetUnique(), "application/zip");

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

        boolean depose = transport.deposerVersS3(URL_S3_SIGNEE, octetUnique(), "application/zip");

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

        boolean depose = transport.deposerVersS3(URL_S3_SIGNEE, octetUnique(), "application/zip");

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

        ReponseApi<String> issue =
                transport.deposerPartie(URL_S3_PARTIE, octetUnique(), "application/zip", SuiviReprise.SILENCIEUX);

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

        ReponseApi<String> issue =
                transport.deposerPartie(URL_S3_PARTIE, octetUnique(), "application/zip", SuiviReprise.SILENCIEUX);

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

    @AfterEach
    void rendreLePlafond() {
        System.clearProperty(PlafondLecture.PROPRIETE_CORPS);
    }

    @Test
    @DisplayName("Un corps qui ANNONCE plus que le plafond est refusé sans être lu (#3222)")
    void corps_annonce_hors_plafond_refuse_sans_lire() throws Exception {
        System.setProperty(PlafondLecture.PROPRIETE_CORPS, "1024");
        InputStream refuseDEtreLu = new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("un corps annoncé hors plafond ne doit pas être lu");
            }
        };
        HttpResponse<InputStream> enorme =
                reponseFlux(200, refuseDEtreLu, Map.of("Content-Length", List.of("999999999")));
        HttpClient client = mock(HttpClient.class);
        doReturn(enorme).when(client).send(any(), any());
        List<Duration> attentes = new ArrayList<>();
        TransportVigieChiro transport =
                new TransportVigieChiro("http://exemple/api/v1", TOKEN_ABC, client, sansAttente(attentes));

        ReponseApi<String> reponse = transport.lire("/donnees");

        assertThat(reponse).isInstanceOf(ReponseApi.Refuse.class);
        assertThat(((ReponseApi.Refuse<String>) reponse).corps())
                .contains("/donnees")
                .contains("-Dvigiechiro.reseau.corps.max-octets=");
        assertThat(attentes)
                .as("une réponse trop grosse le restera : il n'y a rien à rejouer")
                .isEmpty();
        verify(client, times(1)).send(any(), any());
    }

    @Test
    @DisplayName("Un corps SANS Content-Length est compté en cours de lecture, et refusé au franchissement")
    void corps_par_blocs_refuse_au_franchissement() throws Exception {
        System.setProperty(PlafondLecture.PROPRIETE_CORPS, "1024");
        // Encodage par blocs : le serveur n'annonce aucune taille, le premier garde n'a rien à lire.
        HttpResponse<InputStream> parBlocs = reponseTexte(200, "x".repeat(5000));
        HttpClient client = mock(HttpClient.class);
        doReturn(parBlocs).when(client).send(any(), any());
        List<Duration> attentes = new ArrayList<>();
        TransportVigieChiro transport =
                new TransportVigieChiro("http://exemple/api/v1", TOKEN_ABC, client, sansAttente(attentes));

        ReponseApi<String> reponse = transport.lire("/moi/participations");

        assertThat(reponse).isInstanceOf(ReponseApi.Refuse.class);
        assertThat(((ReponseApi.Refuse<String>) reponse).corps()).contains("/moi/participations");
        assertThat(attentes).isEmpty();
    }

    /// Politique sans vraie attente qui **note** les durées demandées, pour compter les reprises.
    private static PolitiqueReessai sansAttente(List<Duration> attentes) {
        return new PolitiqueReessai(attentes::add, () -> 0.0);
    }

    /// Réponse **texte** : ce que rendent les lectures, là où [#reponse] sert les PUT S3 sans corps.
    ///
    /// Le corps est rendu en **flux** depuis #3222 : le transport le lit sous plafond, donc par blocs,
    /// et ne peut plus recevoir une chaîne déjà chargée.
    private static HttpResponse<InputStream> reponseTexte(int statut, String corps) {
        return reponseTexte(statut, corps, Map.of());
    }

    /// Variante **avec en-têtes**, pour exercer le `Retry-After` d'un `429` (#2677).
    private static HttpResponse<InputStream> reponseTexte(int statut, String corps, Map<String, List<String>> entetes) {
        byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
        HttpResponse<InputStream> reponse = mock(HttpResponse.class);
        when(reponse.statusCode()).thenReturn(statut);
        // Un flux NEUF a chaque appel : une reponse servie deux fois (reprise) rendrait sinon un flux
        // deja consomme, donc un corps vide - et un test vert pour la mauvaise raison.
        when(reponse.body()).thenAnswer(appel -> new ByteArrayInputStream(octets));
        when(reponse.headers()).thenReturn(HttpHeaders.of(entetes, (nom, valeur) -> true));
        return reponse;
    }

    /// Réponse dont le **corps est un flux fourni par le test** : c'est ce qui permet de vérifier qu'un
    /// corps annoncé hors plafond n'est **pas lu**, en donnant un flux qui refuse de l'être.
    private static HttpResponse<InputStream> reponseFlux(
            int statut, InputStream corps, Map<String, List<String>> entetes) {
        HttpResponse<InputStream> reponse = mock(HttpResponse.class);
        when(reponse.statusCode()).thenReturn(statut);
        when(reponse.body()).thenReturn(corps);
        when(reponse.headers()).thenReturn(HttpHeaders.of(entetes, (nom, valeur) -> true));
        return reponse;
    }

    private static HttpResponse<Void> reponse(int statut, Map<String, List<String>> entetes) {
        HttpResponse<Void> reponse = mock(HttpResponse.class);
        when(reponse.statusCode()).thenReturn(statut);
        when(reponse.headers()).thenReturn(HttpHeaders.of(entetes, (nom, valeur) -> true));
        return reponse;
    }
}
