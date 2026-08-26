package fr.univ_amu.iut.commun.api;

import fr.univ_amu.iut.commun.model.EntreeTropVolumineuse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

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

    /// Libellé du geste S3 dans le journal : le dépôt ne passe pas par [#emettre] (corps binaire, délai
    /// long), il se consigne donc lui-même.
    private static final String GESTE_S3 = "PUT (S3)";

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

    /// Politique de réessai (#2354, ADR 2354) : le pare-chocs qui absorbe une coupure momentanée.
    private final PolitiqueReessai politique;

    TransportVigieChiro(String baseUrl, FournisseurToken fournisseurToken) {
        this(
                baseUrl,
                fournisseurToken,
                HttpClient.newBuilder().connectTimeout(DELAI).build(),
                PolitiqueReessai.systeme());
    }

    /// Constructeur d'injection (#2354) : le client HTTP et la politique de réessai sont fournis, pour
    /// que les tests simulent un envoi (échec réseau puis succès, `Retry-After`) sans réseau réel -
    /// JPMS interdisant un serveur HTTP local en test.
    TransportVigieChiro(
            String baseUrl, FournisseurToken fournisseurToken, HttpClient client, PolitiqueReessai politique) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.fournisseurToken = Objects.requireNonNull(fournisseurToken, "fournisseurToken");
        this.client = Objects.requireNonNull(client, "client");
        this.politique = Objects.requireNonNull(politique, "politique");
    }

    /// **GET authentifié** sur `chemin` (relatif à la base), trié : succès (2xx, corps), non connecté
    /// (pas de jeton : l'appel n'a pas lieu), injoignable (réseau, délai) ou refusé (statut + corps).
    ReponseApi<String> lire(String chemin) {
        return lire(chemin, null);
    }

    /// Variante **renonçable** (#2686) : `renoncer` - le drapeau d'annulation coopératif de l'appelant,
    /// ou `null` s'il n'en offre pas - est consulté pendant les temporisations de reprise. Sans lui, une
    /// reprise est un trou où « Annuler » ne fait rien, alors qu'un balayage paginé le relaie
    /// précisément page par page (#1522).
    ReponseApi<String> lire(String chemin, BooleanSupplier renoncer) {
        Optional<String> entete = enteteAuthorization();
        if (entete.isEmpty()) {
            return ReponseApi.nonConnecte();
        }
        return emettre(
                Rejeu.AUTORISE,
                renoncer,
                () -> HttpRequest.newBuilder(URI.create(baseUrl + chemin))
                        .timeout(DELAI)
                        .header("Authorization", entete.get())
                        .header("Accept", TYPE_JSON)
                        .GET()
                        .build());
    }

    /// Écriture authentifiée (`POST` / `PATCH`) d'un corps JSON sur `chemin`, triée comme [#lire]. Si
    /// `etag` est non-`null`, ajoute l'en-tête `If-Match` (concurrence optimiste exigée par Eve pour
    /// les mises à jour).
    /// `rejeu` dit ce qu'un rejeu ferait **côté serveur** : l'appelant l'arbitre, le transport l'applique.
    ReponseApi<String> ecrire(String methode, String chemin, String corpsJson, String etag, Rejeu rejeu) {
        Optional<String> entete = enteteAuthorization();
        if (entete.isEmpty()) {
            return ReponseApi.nonConnecte();
        }
        return emettre(rejeu, () -> {
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
        Optional<String> refus = UrlSigneeAdmise.motifDeRefus(url);
        if (refus.isPresent()) {
            return ReponseApi.refuse(0, refus.get());
        }
        return emettre(
                Rejeu.AUTORISE,
                () -> HttpRequest.newBuilder(URI.create(url))
                        .timeout(DELAI)
                        .GET()
                        .build());
    }

    /// Requête à émettre, construite au dernier moment (sa construction même peut échouer).
    @FunctionalInterface
    private interface RequeteAEmettre {
        HttpRequest requete() throws IOException;
    }

    /// Ce qu'un rejeu ferait à l'**état du serveur**, déclaré par l'appelant. Ni la méthode HTTP ni le
    /// bon sens ne suffisent à le déduire : `PUT /…/messages` **empile** par `$push` côté serveur, donc
    /// un `PUT` peut parfaitement ne pas être idempotent. C'est pourquoi l'EPIC #2350 exige un périmètre
    /// « **arbitré appel par appel**, et documenté », et non une règle par verbe.
    ///
    /// Deux familles de raisons de refuser, et c'est pourquoi la décision est nommée plutôt que sa cause :
    /// le rejeu **duplique** (une création, un message empilé), ou il **écrase** (un `PATCH` rejoué repose
    /// des valeurs absolues par-dessus ce qu'un autre poste a écrit entre-temps, et rien ne l'en empêche :
    /// la plateforme ignore `If-Match` là où on le croyait exigé, mesuré en #4523).
    enum Rejeu {
        /// Rejouer redonne le même état et la même réponse : lectures, suppression, valeur absolue.
        AUTORISE,
        /// Ne pas rejouer. Le mode de panne visé n'est pas exotique : la requête arrive, le serveur agit,
        /// et c'est la **réponse** qui se perd. Sans clé d'idempotence, rien ne distingue alors un rejeu
        /// d'une demande neuve.
        INTERDIT
    }

    /// Filet **commun des émissions** : envoie la requête, trie l'issue et la **consigne** (#1845). Une
    /// interruption ou une panne (réseau, DNS, TLS, délai) devient [ReponseApi.Injoignable] avec sa cause :
    /// plus jamais un silence indistinct.
    private ReponseApi<String> emettre(Rejeu rejeu, RequeteAEmettre requete) {
        return emettre(rejeu, null, requete);
    }

    /// Variante qui honore le **renoncement** de l'appelant pendant les temporisations (#2686).
    private ReponseApi<String> emettre(Rejeu rejeu, BooleanSupplier renoncer, RequeteAEmettre requete) {
        if (rejeu == Rejeu.INTERDIT) {
            // Règle 1 de l'ADR 2354 : le réessai n'est jamais aveugle. Une panne réseau ne dit pas si le
            // serveur a agi ; insister sur une création, c'est échanger une erreur visible contre un
            // doublon silencieux sur la plateforme, qu'aucune route ne permet de retirer (#2677).
            return uneTentative(requete).reponse();
        }
        // Réessai gradué sur TOUTES les émissions rejouables, pas seulement sur les écritures du dépôt
        // (#2619). INSISTANT parce que dans ce produit, aucune lecture n'est un sondage automatique
        // - « on n'interroge le serveur que quand l'utilisateur le demande » (#1338) - donc il y a
        // toujours quelqu'un qui attend. Le jour où une tâche périodique apparaîtra, elle devra demander
        // BREF explicitement : c'est elle qui amplifierait un incident en insistant, pas un écran.
        //
        // Le suivi est SILENCIEUX ici : le transport n'a pas de canal vers l'écran. La reprise se voit
        // dans le journal, via l'issue de chaque tentative.
        return renoncer == null
                ? politique.executer(
                        PolitiqueReessai.Profil.INSISTANT, SuiviReprise.SILENCIEUX, () -> uneTentative(requete))
                : politique.executer(
                        PolitiqueReessai.Profil.INSISTANT,
                        SuiviReprise.SILENCIEUX,
                        renoncer,
                        () -> uneTentative(requete));
    }

    /// Une émission, rendue **avec** le délai que le serveur a éventuellement imposé (`Retry-After`).
    /// Sans lui, la règle 3 de l'ADR 2354 - « `Retry-After` fait autorité, c'est lui qui sait » - ne
    /// valait plus que pour le `PUT` S3 : partout ailleurs un `429` d'Eve était rejoué sur notre
    /// temporisation calculée, en ignorant le délai demandé (#2677).
    private PolitiqueReessai.Issue<String> uneTentative(RequeteAEmettre requete) {
        long debut = System.nanoTime();
        String methode = "?";
        String chemin = "?";
        try {
            HttpRequest envoi = requete.requete();
            methode = envoi.method();
            chemin = envoi.uri().getPath();
            HttpResponse<InputStream> http = client.send(envoi, HttpResponse.BodyHandlers.ofInputStream());
            ReponseApi<String> reponse = triage(http.statusCode(), CorpsReponse.sousPlafond(http, chemin));
            JournalEchange.consigner(methode, chemin, reponse, debut, null);
            return new PolitiqueReessai.Issue<>(reponse, retryAfter(http));
        } catch (EntreeTropVolumineuse trop) {
            // Refus DÉFINITIF, pas panne : réémettre une réponse trop grosse la redonnerait trop grosse.
            // Un `Refuse` hors 429/5xx n'est pas réessayable, la politique de reprise s'arrête donc ici.
            ReponseApi<String> reponse = ReponseApi.refuse(0, trop.getMessage());
            JournalEchange.consigner(methode, chemin, reponse, debut, trop);
            return PolitiqueReessai.Issue.de(reponse);
        } catch (InterruptedException interrompu) {
            Thread.currentThread().interrupt();
            ReponseApi<String> reponse = ReponseApi.injoignable("appel interrompu");
            JournalEchange.consigner(methode, chemin, reponse, debut, interrompu);
            return PolitiqueReessai.Issue.de(reponse);
        } catch (RuntimeException | IOException indisponible) {
            ReponseApi<String> reponse = ReponseApi.injoignable(JournalEchange.cause(indisponible));
            JournalEchange.consigner(methode, chemin, reponse, debut, indisponible);
            return PolitiqueReessai.Issue.de(reponse);
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
    ///
    /// Sans suivi de reprise : le réessai reste silencieux (le câblage de la mention discrète vers le
    /// suivi de dépôt vient au lot suivant).
    ReponseApi<String> deposerVersS3(String urlSignee, CorpsAEnvoyer corps, String mime) {
        return deposerVersS3(urlSignee, corps, mime, SuiviReprise.SILENCIEUX);
    }

    /// Variante **réessayée** (#2354) : une coupure momentanée sur un gros téléversement (`PUT` S3 de
    /// plusieurs dizaines de Mo depuis une connexion mobile) ne doit pas coûter l'unité. Le `PUT` est
    /// **idempotent** (même URL signée, même clé, même objet), donc sûr à rejouer ; profil INSISTANT,
    /// car le dépôt est attendu. `Retry-After` du serveur fait autorité (cf. [PolitiqueReessai]). `suivi`
    /// est prévenu avant chaque nouvelle tentative (mention discrète).
    ReponseApi<String> deposerVersS3(String urlSignee, CorpsAEnvoyer corps, String mime, SuiviReprise suivi) {
        return deposerPartie(urlSignee, corps, mime, suivi);
    }

    /// **PUT** d'une **partie** multipart (#2354) vers son URL S3 signée, réessayé comme un dépôt entier
    /// (idempotent, INSISTANT, `Retry-After`). Rend l'issue triée : un succès **porte l'`ETag`** de la
    /// partie (requis pour recoller l'objet à la finalisation), un échec sa cause.
    ReponseApi<String> deposerPartie(String urlSignee, CorpsAEnvoyer corps, String mime, SuiviReprise suivi) {
        return politique.executer(
                PolitiqueReessai.Profil.INSISTANT, suivi, suivi::renonce, () -> uneDepose(urlSignee, corps, mime));
    }

    /// Un **unique** envoi S3 : construit la requête, l'émet, la consigne, et rend l'issue **avec** le
    /// délai que le serveur a éventuellement imposé (`Retry-After`), pour que la politique en tienne
    /// compte. Un succès porte l'`ETag` S3 (utile au multipart ; ignoré par le dépôt entier, qui ne lit
    /// que la variante de l'issue). Une panne réseau ou un fichier illisible devient une issue
    /// [ReponseApi.Injoignable] (réessayable), un statut hors 2xx un [ReponseApi.Refuse] (429/5xx seulement).
    private PolitiqueReessai.Issue<String> uneDepose(String urlSignee, CorpsAEnvoyer corps, String mime) {
        Optional<String> refus = UrlSigneeAdmise.motifDeRefus(urlSignee);
        if (refus.isPresent()) {
            // Refus AVANT d'ouvrir la moindre connexion : les octets d'une nuit ne partent pas vers un
            // hôte inattendu, fût-ce pour s'y voir refuser. Un `Refuse` hors 429/5xx n'est pas
            // réessayable, donc la politique de reprise s'arrête ici plutôt que d'insister.
            return PolitiqueReessai.Issue.de(ReponseApi.refuse(0, refus.get()));
        }
        long debut = System.nanoTime();
        String chemin = "?";
        try {
            HttpRequest requete = HttpRequest.newBuilder(URI.create(urlSignee))
                    .timeout(DELAI_UPLOAD)
                    .header(ENTETE_CONTENT_TYPE, mime)
                    .PUT(corps.corps())
                    .build();
            // Chemin SEUL : une URL S3 pré-signée porte sa signature dans sa requête (#1845).
            chemin = requete.uri().getPath();
            HttpResponse<Void> http = client.send(requete, HttpResponse.BodyHandlers.discarding());
            ReponseApi<String> reponse = http.statusCode() >= 200 && http.statusCode() < 300
                    ? ReponseApi.succes(etag(http))
                    : triage(http.statusCode(), "");
            JournalEchange.consigner(GESTE_S3, chemin, reponse, debut, null);
            return new PolitiqueReessai.Issue<>(reponse, retryAfter(http));
        } catch (InterruptedException interrompu) {
            Thread.currentThread().interrupt();
            ReponseApi<String> reponse = ReponseApi.injoignable("appel interrompu");
            JournalEchange.consigner(GESTE_S3, chemin, reponse, debut, interrompu);
            return PolitiqueReessai.Issue.de(reponse);
        } catch (RuntimeException | IOException indisponible) {
            ReponseApi<String> reponse = ReponseApi.injoignable(JournalEchange.cause(indisponible));
            JournalEchange.consigner(GESTE_S3, chemin, reponse, debut, indisponible);
            return PolitiqueReessai.Issue.de(reponse);
        }
    }

    /// Le délai que le serveur demande d'attendre avant un nouvel essai (`Retry-After` en **secondes**),
    /// s'il l'envoie sous forme entière. La forme alternative (date HTTP) est ignorée : le backoff prend
    /// alors le relais. On ne fait donc jamais attendre plus longtemps qu'un serveur ne l'a demandé.
    private static Optional<Duration> retryAfter(HttpResponse<?> reponse) {
        return reponse.headers()
                .firstValue("Retry-After")
                .map(String::strip)
                .filter(valeur -> !valeur.isEmpty() && valeur.chars().allMatch(Character::isDigit))
                .map(Long::parseLong)
                .map(Duration::ofSeconds);
    }

    /// L'`ETag` rendu par S3 au `PUT` (guillemets retirés), ou vide s'il manque. S3 le renvoie à chaque
    /// `PUT` réussi ; il est requis pour recoller les parties d'un multipart à la finalisation (#2354).
    private static String etag(HttpResponse<?> reponse) {
        return reponse.headers().firstValue("ETag").orElse("").replace("\"", "");
    }

    /// Triage **pur** d'une réponse reçue : 2xx exploitable, tout autre statut est un refus qui garde
    /// son statut et son corps. Testable sans réseau.
    static ReponseApi<String> triage(int statut, String corps) {
        return statut >= 200 && statut < 300 ? ReponseApi.succes(corps) : ReponseApi.refuse(statut, corps);
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
