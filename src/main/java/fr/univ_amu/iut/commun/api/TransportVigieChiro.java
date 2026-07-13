package fr.univ_amu.iut.commun.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/// Mécanique HTTP du client VigieChiro (#1284) : authentifier, émettre, et surtout **trier l'issue**
/// de chaque appel en [ReponseApi] (non connecté / injoignable / refusé / succès).
///
/// Extrait de [ClientVigieChiro] : le client **nomme** les points d'accès de l'API et lit leurs
/// réponses ; le transport, lui, sait ce qu'il est advenu de l'appel. Même séparation que
/// [TraitementVigieChiro] en son temps, et même raison : le client était au plafond de la God Class.
///
/// **Authentification** : le token (fourni par [FournisseurToken]) est envoyé en **HTTP Basic**, token
/// en nom d'utilisateur et mot de passe vide, soit `Authorization: Basic base64("<token>:")`
/// (convention du backend Eve).
final class TransportVigieChiro {

    private static final Duration DELAI = Duration.ofSeconds(10);
    /// Délai d'un **téléversement** S3 (envoi d'octets), plus long que les appels JSON courts.
    private static final Duration DELAI_UPLOAD = Duration.ofSeconds(120);
    /// Type de média JSON des échanges avec le backend Eve (`Accept` et `Content-Type`).
    private static final String TYPE_JSON = "application/json";
    /// En-tête HTTP du type de média du corps envoyé (JSON des écritures, mime signé des `PUT` S3).
    private static final String ENTETE_CONTENT_TYPE = "Content-Type";

    private final String baseUrl;
    private final FournisseurToken fournisseurToken;
    private final HttpClient client;

    TransportVigieChiro(String baseUrl, FournisseurToken fournisseurToken) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.fournisseurToken = Objects.requireNonNull(fournisseurToken, "fournisseurToken");
        this.client = HttpClient.newBuilder().connectTimeout(DELAI).build();
    }

    /// **GET authentifié** sur `chemin` (relatif à la base), trié : succès (2xx, corps), non connecté
    /// (pas de jeton : l'appel n'a pas lieu), injoignable (réseau, délai) ou refusé (statut + corps).
    ReponseApi<String> lire(String chemin) {
        Optional<String> entete = enteteAuthorization();
        if (entete.isEmpty()) {
            return ReponseApi.nonConnecte();
        }
        return emettre(() -> HttpRequest.newBuilder(URI.create(baseUrl + chemin))
                .timeout(DELAI)
                .header("Authorization", entete.get())
                .header("Accept", TYPE_JSON)
                .GET()
                .build());
    }

    /// Écriture authentifiée (`POST` / `PATCH`) d'un corps JSON sur `chemin`, triée comme [#lire]. Si
    /// `etag` est non-`null`, ajoute l'en-tête `If-Match` (concurrence optimiste exigée par Eve pour
    /// les mises à jour).
    ReponseApi<String> ecrire(String methode, String chemin, String corpsJson, String etag) {
        Optional<String> entete = enteteAuthorization();
        if (entete.isEmpty()) {
            return ReponseApi.nonConnecte();
        }
        return emettre(() -> {
            HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create(baseUrl + chemin))
                    .timeout(DELAI)
                    .header("Authorization", entete.get())
                    .header("Accept", TYPE_JSON)
                    .header(ENTETE_CONTENT_TYPE, TYPE_JSON)
                    .method(methode, HttpRequest.BodyPublishers.ofString(corpsJson, StandardCharsets.UTF_8));
            if (etag != null) {
                requete.header("If-Match", etag);
            }
            return requete.build();
        });
    }

    /// Télécharge une URL **déjà signée** (S3, #1132) : aucun en-tête `Authorization` (S3 refuse une
    /// authentification surnuméraire, la signature de l'URL fait foi), donc jamais « non connecté ».
    ReponseApi<String> telecharger(String url) {
        return emettre(() ->
                HttpRequest.newBuilder(URI.create(url)).timeout(DELAI).GET().build());
    }

    /// Requête à émettre, construite au dernier moment (sa construction même peut échouer).
    @FunctionalInterface
    private interface RequeteAEmettre {
        HttpRequest requete() throws IOException;
    }

    /// Filet **commun des émissions** : envoie la requête et trie l'issue. Une interruption ou une
    /// panne (réseau, DNS, TLS, délai) devient [ReponseApi.Injoignable] avec sa cause : plus jamais un
    /// silence indistinct.
    private ReponseApi<String> emettre(RequeteAEmettre requete) {
        try {
            return triage(client.send(requete.requete(), HttpResponse.BodyHandlers.ofString()));
        } catch (InterruptedException interrompu) {
            Thread.currentThread().interrupt();
            return ReponseApi.injoignable("appel interrompu");
        } catch (RuntimeException | IOException indisponible) {
            return ReponseApi.injoignable(cause(indisponible));
        }
    }

    /// Corps d'un `PUT` S3, construit au dernier moment : lire un fichier peut échouer (IOException),
    /// et cet échec doit être avalé par le même filet que la panne réseau.
    @FunctionalInterface
    interface CorpsAEnvoyer {
        HttpRequest.BodyPublisher corps() throws IOException;
    }

    /// **PUT** vers une **URL S3 pré-signée** : hors API VigieChiro (aucun en-tête d'auth, l'URL est
    /// déjà signée). Le `Content-Type` doit être le mime attendu par la signature (sinon S3 répond
    /// `SignatureDoesNotMatch`). `true` si 2xx, `false` sinon (fichier illisible compris) : le dépôt
    /// par unité a son propre canal de compte-rendu ([fr.univ_amu.iut.lot.model.DepotVigieChiro]).
    boolean deposerVersS3(String urlSignee, CorpsAEnvoyer corps, String mime) {
        try {
            HttpRequest requete = HttpRequest.newBuilder(URI.create(urlSignee))
                    .timeout(DELAI_UPLOAD)
                    .header(ENTETE_CONTENT_TYPE, mime)
                    .PUT(corps.corps())
                    .build();
            HttpResponse<Void> reponse = client.send(requete, HttpResponse.BodyHandlers.discarding());
            return reponse.statusCode() >= 200 && reponse.statusCode() < 300;
        } catch (InterruptedException interrompu) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException | IOException indisponible) {
            return false;
        }
    }

    /// Triage **pur** d'une réponse reçue : 2xx exploitable, tout autre statut est un refus qui garde
    /// son statut et son corps. Testable sans réseau.
    static ReponseApi<String> triage(int statut, String corps) {
        return statut >= 200 && statut < 300 ? ReponseApi.succes(corps) : ReponseApi.refuse(statut, corps);
    }

    private static ReponseApi<String> triage(HttpResponse<String> reponse) {
        return triage(reponse.statusCode(), reponse.body());
    }

    /// Cause lisible d'une indisponibilité, pour le message « VigieChiro injoignable : ... ».
    static String cause(Exception indisponible) {
        if (indisponible instanceof HttpTimeoutException) {
            return "délai d'attente dépassé";
        }
        String message = indisponible.getMessage();
        return message == null || message.isBlank() ? indisponible.getClass().getSimpleName() : message;
    }

    /// En-tête `Authorization` (`Basic base64("<token>:")`), ou vide si aucun token (non connecté).
    Optional<String> enteteAuthorization() {
        return fournisseurToken.token().map(TransportVigieChiro::basic);
    }

    private static String basic(String token) {
        String encode = Base64.getEncoder().encodeToString((token + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + encode;
    }
}
