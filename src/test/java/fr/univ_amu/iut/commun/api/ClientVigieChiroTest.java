package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Façade des points d'accès VigieChiro (#725/#728) : les endpoints encore « dégradation propre »
/// rendent vide/false sur toute issue non-succès (la mécanique HTTP et son tri sont testés dans
/// `TransportVigieChiroTest`, la lecture JSON dans `ReponsesVigieChiroTest`). Aucun appel réseau réel :
/// une URL injoignable suffit.
class ClientVigieChiroTest {

    private static final FournisseurToken SANS_TOKEN = Optional::empty;
    private static final FournisseurToken TOKEN_ABC = () -> Optional.of("abc");

    @Test
    @DisplayName("get / moi sans token → NonConnecte, sans même toucher le réseau")
    void moi_sans_token_est_vide() {
        ClientVigieChiro client = clientHorsLigne("http://localhost:1", SANS_TOKEN);

        assertThat(client.moi()).isInstanceOf(ReponseApi.NonConnecte.class);
    }

    @Test
    @DisplayName("get / moi hors-ligne (URL injoignable) → Injoignable, sans lever")
    void moi_hors_ligne_est_vide() {
        ClientVigieChiro client = clientHorsLigne("http://localhost:1/api/v1", TOKEN_ABC);

        assertThat(client.moi()).isInstanceOf(ReponseApi.Injoignable.class);
    }

    @Test
    @DisplayName("toutes les lectures sans token → NonConnecte, sans toucher le réseau (#1284)")
    void listes_sans_token_sont_vides() {
        ClientVigieChiro client = clientHorsLigne("http://localhost:1/api/v1", SANS_TOKEN);

        assertThat(client.taxons()).isInstanceOf(ReponseApi.NonConnecte.class);
        assertThat(client.mesSites()).isInstanceOf(ReponseApi.NonConnecte.class);
        assertThat(client.mesParticipations()).isInstanceOf(ReponseApi.NonConnecte.class);
        assertThat(client.participation("6a49")).isInstanceOf(ReponseApi.NonConnecte.class);
        assertThat(client.donnees("6a49")).isInstanceOf(ReponseApi.NonConnecte.class);
    }

    @Test
    @DisplayName("participation / donnees hors-ligne → Injoignable, plus jamais un faux « vide » (#1284)")
    void lectures_triees_hors_ligne() {
        ClientVigieChiro client = clientHorsLigne("http://localhost:1/api/v1", TOKEN_ABC);

        assertThat(client.participation("6a49")).isInstanceOf(ReponseApi.Injoignable.class);
        assertThat(client.donnees("6a49")).isInstanceOf(ReponseApi.Injoignable.class);
    }

    @Test
    @DisplayName("écritures sans token → échec « non connecté » explicite, sans toucher le réseau (#1284)")
    void ecritures_sans_token() {
        ClientVigieChiro client = clientHorsLigne("http://localhost:1/api/v1", SANS_TOKEN);

        assertThat(client.creerParticipation("site1", participationMinimale()).id())
                .isEmpty();
        assertThat(client.creerParticipation("site1", participationMinimale()).echec())
                .contains("jeton");
        assertThat(client.modifierParticipation("p1", "etag1", participationMinimale())
                        .id())
                .isEmpty();
        assertThat(client.creerFichier("Car130711-2026-Pass1-Z41_000.wav", "p1"))
                .isInstanceOf(ReponseApi.NonConnecte.class);
        assertThat(client.finaliserFichier("f1")).isInstanceOf(ReponseApi.NonConnecte.class);
    }

    @Test
    @DisplayName("écritures hors-ligne (URL injoignable) → échec « injoignable » explicite, sans lever")
    void ecritures_hors_ligne() {
        ClientVigieChiro client = clientHorsLigne("http://localhost:1/api/v1", TOKEN_ABC);

        assertThat(client.creerParticipation("site1", participationMinimale()).id())
                .isEmpty();
        assertThat(client.creerParticipation("site1", participationMinimale()).echec())
                .contains("injoignable");
        assertThat(client.modifierParticipation("p1", "etag1", participationMinimale())
                        .id())
                .isEmpty();
        assertThat(client.creerFichier("Car130711-2026-Pass1-Z41_000.wav", "p1"))
                .isInstanceOf(ReponseApi.Injoignable.class);
        assertThat(client.finaliserFichier("f1")).isInstanceOf(ReponseApi.Injoignable.class);
    }

    @Test
    @DisplayName("televerserVersS3 hors-ligne → false, sans lever (URL S3 déjà signée, sans auth)")
    void upload_s3_hors_ligne_est_false() {
        ClientVigieChiro client = clientHorsLigne("http://localhost:1/api/v1", TOKEN_ABC);

        // L'URL doit franchir le garde de #2734, sinon ce test rendrait « false » pour un refus d'hôte
        // et non pour la panne réseau qu'il prétend éprouver.
        System.setProperty(UrlSigneeAdmise.PROPRIETE_HOTES, "localhost");
        try {
            assertThat(client.televerserVersS3("https://localhost:1/s3/signe", new byte[] {1, 2, 3}, "audio/x-wav")
                            .echec())
                    .as("panne reseau : l issue porte la cause, la ou un booleen la perdait")
                    .isPresent();
        } finally {
            System.clearProperty(UrlSigneeAdmise.PROPRIETE_HOTES);
        }
    }

    @Test
    @DisplayName("#2734 : une URL de stockage en clair fait échouer le téléversement sans appel")
    void upload_s3_en_clair_refuse() {
        ClientVigieChiro client = clientHorsLigne("https://api.exemple/api/v1", TOKEN_ABC);

        assertThat(client.televerserVersS3("http://ailleurs.example/s3/signe", new byte[] {1, 2, 3}, "audio/x-wav")
                        .echec())
                .as("les octets d'une nuit ne partent pas vers un hôte inattendu")
                .isPresent();
    }

    private static ParticipationADeposer participationMinimale() {
        return new ParticipationADeposer("Z41", "2026-07-03T19:00:00Z", "2026-07-04T04:00:00Z", null, null, null);
    }

    @Test
    @DisplayName("journalTraitement : sans token → NonConnecte, hors ligne → Injoignable (#1284)")
    void journal_traitement_degrade_proprement() {
        assertThat(clientHorsLigne("http://localhost:1", SANS_TOKEN).journalTraitement("6a49"))
                .isInstanceOf(ReponseApi.NonConnecte.class);
        assertThat(clientHorsLigne("http://localhost:1", TOKEN_ABC).journalTraitement("6a49"))
                .isInstanceOf(ReponseApi.Injoignable.class);
    }

    @Test
    @DisplayName("accès fichiers (#1565) : accesFichier / piecesJointes / csvObservations dégradent"
            + " proprement : sans token → NonConnecte, hors ligne → Injoignable (#1284)")
    void acces_fichiers_degrade_proprement() {
        ClientVigieChiro sansToken = clientHorsLigne("http://localhost:1/api/v1", SANS_TOKEN);
        assertThat(sansToken.accesFichier("f1")).isInstanceOf(ReponseApi.NonConnecte.class);
        assertThat(sansToken.piecesJointes("6a49", TypePieceJointe.WAV)).isInstanceOf(ReponseApi.NonConnecte.class);
        assertThat(sansToken.csvObservations("6a49")).isInstanceOf(ReponseApi.NonConnecte.class);

        ClientVigieChiro horsLigne = clientHorsLigne("http://localhost:1/api/v1", TOKEN_ABC);
        assertThat(horsLigne.accesFichier("f1")).isInstanceOf(ReponseApi.Injoignable.class);
        assertThat(horsLigne.piecesJointes("6a49", TypePieceJointe.PROCESSING_EXTRA))
                .isInstanceOf(ReponseApi.Injoignable.class);
        assertThat(horsLigne.csvObservations("6a49")).isInstanceOf(ReponseApi.Injoignable.class);
    }

    @Test
    @DisplayName("corrigerObservation (#723) : sans token ou hors ligne → échec EXPLIQUÉ, distinct (#1284)")
    void correction_degrade_proprement() {
        ResultatEcriture sansToken = clientHorsLigne("http://localhost:1", SANS_TOKEN)
                .corrigerObservation("6a4f", 0, "5526", fr.univ_amu.iut.commun.model.Certitude.SUR, true);
        ResultatEcriture horsLigne = clientHorsLigne("http://localhost:1", TOKEN_ABC)
                .corrigerObservation("6a4f", 0, "5526", fr.univ_amu.iut.commun.model.Certitude.SUR, false);

        // Une écriture refusée est expliquée (jamais un booléen opaque), et depuis #1284 la cause est
        // la bonne : « aucun jeton » n'est plus déguisé en panne réseau, et réciproquement.
        assertThat(sansToken.estReussie()).isFalse();
        assertThat(sansToken.echec()).contains("jeton");
        assertThat(horsLigne.estReussie()).isFalse();
        assertThat(horsLigne.echec()).contains("injoignable");
    }

    @Test
    @DisplayName("#2354 : deposerEnParts découpe, demande une URL par partie, dépose et finalise")
    void depot_en_parts_boucle_complete(@TempDir Path dossier) throws Exception {
        Path fichier = dossier.resolve("Car-1.zip");
        Files.write(fichier, new byte[] {1, 2, 3, 4, 5, 6, 7}); // 7 octets, chunk 3 → 3 parties (3+3+1)

        AtomicInteger putsS3 = new AtomicInteger();
        HttpResponse<Object> urlPartie = reponse(
                200, "{\"s3_signed_url\": \"https://vigiechiro.s3.amazonaws.com/part?Signature=abc\"}", Map.of());
        HttpResponse<Object> s3ok = reponse(200, "", Map.of("ETag", List.of("\"etag-x\"")));
        HttpResponse<Object> finale = reponse(200, "{}", Map.of());
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(), any())).thenAnswer(invocation -> {
            HttpRequest requete = invocation.getArgument(0);
            if ("vigiechiro.s3.amazonaws.com".equals(requete.uri().getHost())) {
                putsS3.incrementAndGet();
                return s3ok;
            }
            return requete.uri().getPath().endsWith("/multipart") ? urlPartie : finale;
        });
        ClientVigieChiro client = clientAvec(http);

        ReponseApi<String> issue =
                client.deposerEnParts("f-1", fichier, "application/zip", 3, fraction -> {}, SuiviReprise.SILENCIEUX);

        assertThat(issue).as("la finalisation aboutit").isInstanceOf(ReponseApi.Succes.class);
        assertThat(putsS3).as("7 octets en chunks de 3 → 3 parties déposées").hasValue(3);
    }

    @Test
    @DisplayName("#2354 : deposerEnParts s'arrête à la première partie refusée (4xx), sans finaliser")
    void depot_en_parts_echoue_sur_une_partie(@TempDir Path dossier) throws Exception {
        Path fichier = dossier.resolve("Car-1.zip");
        Files.write(fichier, new byte[] {1, 2, 3, 4}); // 2 parties de 3+1, la 1re échoue

        AtomicInteger finalisations = new AtomicInteger();
        HttpResponse<Object> urlPartie = reponse(
                200, "{\"s3_signed_url\": \"https://vigiechiro.s3.amazonaws.com/part?Signature=abc\"}", Map.of());
        HttpResponse<Object> s3refus = reponse(403, "", Map.of()); // partie refusée : 4xx, non rejouable
        HttpResponse<Object> finale = reponse(200, "{}", Map.of());
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(), any())).thenAnswer(invocation -> {
            HttpRequest requete = invocation.getArgument(0);
            if ("vigiechiro.s3.amazonaws.com".equals(requete.uri().getHost())) {
                return s3refus;
            }
            if (requete.uri().getPath().endsWith("/multipart")) {
                return urlPartie;
            }
            finalisations.incrementAndGet();
            return finale;
        });
        ClientVigieChiro client = clientAvec(http);

        ReponseApi<String> issue =
                client.deposerEnParts("f-1", fichier, "application/zip", 3, fraction -> {}, SuiviReprise.SILENCIEUX);

        assertThat(issue).as("le refus de la partie est propagé").isInstanceOf(ReponseApi.Refuse.class);
        assertThat(finalisations)
                .as("aucune finalisation quand une partie a échoué")
                .hasValue(0);
    }

    @Test
    @DisplayName("#3002 : sitesPlateforme interroge « /sites » paginé, et borne le nombre de pages demandé")
    void sites_plateforme_interroge_le_catalogue_et_borne_les_pages() throws Exception {
        // Le bornage n'était vérifié nulle part : la commande mocke le client, et le client n'était pas
        // exercé. Un « --pages 0 » ou négatif aurait demandé zéro page, donc rendu une collection vide
        // qu'on aurait prise pour un catalogue vide.
        List<String> chemins = new ArrayList<>();
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(), any())).thenAnswer(appel -> {
            chemins.add(((HttpRequest) appel.getArgument(0)).uri().toString());
            return reponse(200, "{\"_meta\":{\"total\":0},\"_items\":[]}", Map.of());
        });

        ClientVigieChiro client = clientAvec(http);
        client.sitesPlateforme(0, (page, total) -> {});

        assertThat(chemins)
                .as("zéro page demandée est ramenée à une : sans quoi la collection reviendrait vide")
                .hasSize(1);
        assertThat(chemins.get(0))
                .as("le catalogue se lit sur « /sites », paginé au plafond du serveur")
                .contains("/sites?max_results=100&page=1");
    }

    @Test
    @DisplayName("#4576 : carreStoc demande le rayon qu'on lui donne, et non celui du contrôle")
    void carre_stoc_demande_le_rayon_donne() throws Exception {
        // Le rayon de 10 000 m a été choisi pour un CONTRÔLE, où un humain a déjà tapé la vérité à côté
        // et où le carré voisin ne fait que nuancer. Une PROPOSITION se valide sans se relire : le
        // voisin y devient un numéro faux et plausible. Le rayon doit donc pouvoir se serrer.
        List<String> chemins = new ArrayList<>();
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(), any())).thenAnswer(appel -> {
            chemins.add(((HttpRequest) appel.getArgument(0)).uri().toString());
            return reponse(200, "{\"_meta\":{\"total\":0},\"_items\":[]}", Map.of());
        });

        clientAvec(http).carreStoc(43.296482, 5.369780, 1_500);

        assertThat(chemins.get(0))
                .as("le rayon demandé part tel quel dans « r », le $maxDistance du $near")
                .contains("r=1500");
    }

    @Test
    @DisplayName("#4576 : sans rayon, carreStoc demande toujours les 10 000 m du contrôle")
    void carre_stoc_sans_rayon_garde_celui_du_controle() throws Exception {
        // `ControleCarreStoc` appelle sans rayon et doit continuer d'obtenir le sien. La surcharge
        // délègue : si elle déléguait avec une autre constante, le contrôle se mettrait à se taire là
        // où il parlait, sans qu'aucun de ses propres tests ne bronche - ils bouchonnent le client.
        List<String> chemins = new ArrayList<>();
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(), any())).thenAnswer(appel -> {
            chemins.add(((HttpRequest) appel.getArgument(0)).uri().toString());
            return reponse(200, "{\"_meta\":{\"total\":0},\"_items\":[]}", Map.of());
        });

        clientAvec(http).carreStoc(43.296482, 5.369780);

        assertThat(chemins.get(0)).contains("r=10000");
    }

    /// Client sur un transport à `HttpClient` mocké et politique sans vraie attente (tests instantanés).
    private static ClientVigieChiro clientAvec(HttpClient http) {
        return new ClientVigieChiro(new TransportVigieChiro(
                "http://api.exemple/v1", TOKEN_ABC, http, new PolitiqueReessai(d -> {}, () -> 0.0)));
    }

    private static HttpResponse<Object> reponse(int statut, String corps, Map<String, List<String>> entetes) {
        byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
        HttpResponse<Object> reponse = mock(HttpResponse.class);
        when(reponse.statusCode()).thenReturn(statut);
        // Corps en flux depuis #3222 (le transport le lit sous plafond), neuf a chaque appel.
        when(reponse.body()).thenAnswer(appel -> new ByteArrayInputStream(octets));
        when(reponse.headers()).thenReturn(HttpHeaders.of(entetes, (nom, valeur) -> true));
        return reponse;
    }

    /// Client hors-ligne **sans attente**. Depuis que les emissions reessaient (#2619), la politique de
    /// production dort vraiment entre deux tentatives, et cette classe est passee de 5 s a une trentaine.
    /// Le reessai lui-meme est verifie dans `TransportVigieChiroTest` ; ici on teste la facade.
    private static ClientVigieChiro clientHorsLigne(String baseUrl, FournisseurToken jeton) {
        return new ClientVigieChiro(new TransportVigieChiro(
                baseUrl, jeton, HttpClient.newHttpClient(), new PolitiqueReessai(delai -> {}, () -> 0.0)));
    }

    @Test
    @DisplayName("#2686 : le balayage des données relaie le renoncement de l'appelant jusqu'au réessai")
    void le_balayage_relaie_le_renoncement() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any())).thenThrow(new java.io.IOException("coupure"));
        ClientVigieChiro facade = new ClientVigieChiro(
                new TransportVigieChiro("http://localhost:1/api/v1", TOKEN_ABC, client, sansAttente()));
        // Un suivi qui a DÉJÀ renoncé : le cas de l'utilisateur qui clique « Annuler » pendant le
        // téléchargement d'une nuit. Sans le relais `suivi::renonce`, le balayage insisterait encore.
        SuiviPagination annule = new SuiviPagination() {
            @Override
            public void surPage(int page, int totalPages) {
                // Aucune page n'aboutit ici : la première lecture échoue.
            }

            @Override
            public boolean renonce() {
                return true;
            }
        };

        ReponseApi<List<DonneeVigieChiro>> issue = facade.donnees("6a49", annule);

        assertThat(issue).isInstanceOf(ReponseApi.Injoignable.class);
        verify(client, times(1)).send(any(), any());
    }

    /// Politique sans vraie attente (aléa nul), pour des tests instantanés.
    private static PolitiqueReessai sansAttente() {
        return new PolitiqueReessai(delai -> {}, () -> 0.0);
    }
}
