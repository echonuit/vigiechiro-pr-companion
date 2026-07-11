package fr.univ_amu.iut.commun.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Client HTTP de l'**API REST VigieChiro** (backend Eve, base `…/api/v1`, cf. #142). Socle réseau
/// destiné à être réutilisé par les features (identité, sites, taxons, participations, fichiers).
///
/// Calqué sur [fr.univ_amu.iut.passage.model.MeteoOpenMeteo] : `java.net.http`, timeout court et
/// **dégradation propre** — absence de token, réponse non-`200` ou panne réseau sont converties en
/// [Optional#empty()] ; aucune exception ne remonte à l'IHM. Le **transport** vit ici ; la **lecture**
/// des réponses JSON est déléguée à [ReponsesVigieChiro] (fonctions pures, testables sans réseau).
///
/// **Authentification** : le token (fourni par [FournisseurToken]) est envoyé en **HTTP Basic**, token
/// en nom d'utilisateur et mot de passe vide, soit `Authorization: Basic base64("<token>:")`
/// (convention du backend Eve).
public final class ClientVigieChiro {

    private static final String URL_DEFAUT = "https://vigiechiro.herokuapp.com/api/v1";
    private static final Duration DELAI = Duration.ofSeconds(10);
    /// Délai d'un **téléversement** S3 (envoi d'octets), plus long que les appels JSON courts.
    private static final Duration DELAI_UPLOAD = Duration.ofSeconds(120);
    /// Type de média JSON des échanges avec le backend Eve (`Accept` et `Content-Type`).
    private static final String TYPE_JSON = "application/json";

    /// En-tête HTTP du type de média du corps envoyé (JSON des écritures, mime signé des `PUT` S3).
    private static final String ENTETE_CONTENT_TYPE = "Content-Type";

    /// Préfixe de chemin de l'API des participations (`GET .../donnees`, `GET .../#id`, `PATCH .../#id`).
    private static final String CHEMIN_PARTICIPATIONS = "/participations/";
    /// Garde-fou de pagination (`GET …/donnees`) : une participation a des milliers de fichiers, jamais
    /// des centaines de milliers ; on plafonne le nombre de pages pour éviter toute boucle.
    private static final int PAGES_MAX = 500;

    private final String baseUrl;
    private final FournisseurToken fournisseurToken;
    private final HttpClient client;

    public ClientVigieChiro(FournisseurToken fournisseurToken) {
        this(URL_DEFAUT, fournisseurToken);
    }

    /// Constructeur d'injection de l'URL de base (tests hors-ligne : une URL injoignable donne `empty`).
    ClientVigieChiro(String baseUrl, FournisseurToken fournisseurToken) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.fournisseurToken = Objects.requireNonNull(fournisseurToken, "fournisseurToken");
        this.client = HttpClient.newBuilder().connectTimeout(DELAI).build();
    }

    /// Profil de l'utilisateur connecté (`GET /moi`), ou vide si non connecté / indisponible.
    public Optional<ProfilVigieChiro> moi() {
        return get("/moi").flatMap(ReponsesVigieChiro::profil);
    }

    /// Référentiel officiel des taxons (`GET /taxons/liste`, résumé non paginé : `_id` + libellés).
    /// Liste vide si non connecté / indisponible (dégradation propre).
    public List<TaxonVigieChiro> taxons() {
        return get("/taxons/liste").map(ReponsesVigieChiro::taxons).orElseGet(List::of);
    }

    /// Sites rattachés à l'observateur, **dérivés de ses participations** (`GET /moi/participations`).
    /// Liste vide si non connecté / indisponible.
    ///
    /// On ne passe **pas** par `/moi/sites` : celui-ci filtre sur le *propriétaire* du site et renvoie
    /// vide pour un simple participant à un site régional (cf. #718). Chaque participation embarque son
    /// `site` ; on les déduplique par `_id`. La réponse Eve est paginée : on ne lit que la **première
    /// page** (`_items`), suffisante pour la poignée de participations d'un observateur.
    public List<SiteVigieChiro> mesSites() {
        return get("/moi/participations").map(ParticipationsVigieChiro::sites).orElseGet(List::of);
    }

    /// **Participations** de l'observateur (`GET /moi/participations`, axe 4.2) : id + localité + date +
    /// site, pour rattacher à la main un passage à une participation existante (import de résultats sans
    /// dépôt-app préalable). Liste vide si non connecté / indisponible. Première page (`_items`).
    public List<ParticipationVigieChiro> mesParticipations() {
        return get("/moi/participations")
                .map(ParticipationsVigieChiro::participations)
                .orElseGet(List::of);
    }

    /// Participation **détaillée** (`GET /participations/#id`, axe 4) : `_etag` (pour un `PATCH` `If-Match`
    /// concurrent-sûr), dates, météo, configuration matérielle et état du traitement Tadarida. Vide si non
    /// connecté, indisponible, ou participation inconnue.
    public Optional<ParticipationDetail> participation(String id) {
        return get(CHEMIN_PARTICIPATIONS + id).flatMap(ParticipationsVigieChiro::detail);
    }

    /// Résultats Tadarida d'une participation (`GET /participations/#id/donnees`, #719, axe 4.2) : les
    /// fichiers et leurs observations, **toutes pages confondues**. Liste vide si non connecté /
    /// indisponible. La réponse Eve est paginée ; on parcourt les pages (grande taille demandée) jusqu'à
    /// une page vide.
    public List<DonneeVigieChiro> donnees(String participationId) {
        List<DonneeVigieChiro> tout = new ArrayList<>();
        for (int page = 1; page <= PAGES_MAX; page++) {
            Optional<String> corps =
                    get(CHEMIN_PARTICIPATIONS + participationId + "/donnees?max_results=1000&page=" + page);
            if (corps.isEmpty()) {
                break; // non connecté / erreur : on renvoie ce qui a déjà été récupéré
            }
            List<DonneeVigieChiro> lot = DonneesVigieChiro.donnees(corps.get());
            if (lot.isEmpty()) {
                break; // page vide = fin de la pagination
            }
            tout.addAll(lot);
        }
        return tout;
    }

    // ---------------------------------------------------------------------------------------------
    // Écritures (dépôt d'une nuit, #142) : création de participation + upload de fichiers vers S3
    // ---------------------------------------------------------------------------------------------

    /// Crée une **participation** sur un site (`POST /sites/#id/participations`, #142) : renvoie l'`_id` créé,
    /// ou un [ResultatParticipation] portant le **détail de l'échec** (statut HTTP + corps de la réponse
    /// VigieChiro) pour un message exploitable — le dépôt étant une écriture, un refus doit être **expliqué**,
    /// pas silencieusement vide. Prérequis métier : site **verrouillé** côté VigieChiro.
    public ResultatParticipation creerParticipation(String siteId, ParticipationADeposer participation) {
        Optional<ReponseHttp> reponse =
                postDetaille("/sites/" + siteId + "/participations", RequetesVigieChiro.participation(participation));
        if (reponse.isEmpty()) {
            return ResultatParticipation.echouee("VigieChiro injoignable (non connecté, ou réseau indisponible).");
        }
        ReponseHttp r = reponse.get();
        if (!estSucces(r.statut())) {
            return ResultatParticipation.echouee("HTTP " + r.statut() + " — " + r.corps());
        }
        return ReponsesVigieChiro.idCree(r.corps())
                .map(ResultatParticipation::reussie)
                .orElseGet(
                        () -> ResultatParticipation.echouee("réponse acceptée mais sans identifiant : " + r.corps()));
    }

    /// **Met à jour** une participation existante (`PATCH /participations/#id`, axe 4) : n'émet que les
    /// métadonnées synchronisables (dates, météo, configuration ; cf. [RequetesVigieChiro#miseAJourParticipation]),
    /// avec l'en-tête `If-Match: <etag>` exigé par Eve (concurrence optimiste). L'`etag` frais se lit via
    /// [#participation]. Renvoie l'`_id` en cas de succès, ou le **détail de l'échec** (statut + corps) — un
    /// refus doit être expliqué. Prérequis : `etag` courant (sinon `412 Precondition Failed`).
    public ResultatParticipation modifierParticipation(String id, String etag, ParticipationADeposer miseAJour) {
        Optional<ReponseHttp> reponse =
                ecrire("PATCH", CHEMIN_PARTICIPATIONS + id, RequetesVigieChiro.miseAJourParticipation(miseAJour), etag);
        if (reponse.isEmpty()) {
            return ResultatParticipation.echouee("VigieChiro injoignable (non connecté, ou réseau indisponible).");
        }
        ReponseHttp r = reponse.get();
        if (!estSucces(r.statut())) {
            return ResultatParticipation.echouee("HTTP " + r.statut() + " — " + r.corps());
        }
        return ResultatParticipation.reussie(id);
    }

    /// Déclare un **fichier** à téléverser (`POST /fichiers`, étape 1/3) : renvoie son `_id` et l'URL S3
    /// pré-signée ([FichierSigne]), ou vide. Le mime n'est pas transmis (déduit de l'extension du titre) ;
    /// le titre doit respecter la convention de nommage VigieChiro (`Car…-Pass…`).
    public Optional<FichierSigne> creerFichier(String titre) {
        return post("/fichiers", RequetesVigieChiro.fichier(titre)).flatMap(ReponsesVigieChiro::fichierSigne);
    }

    /// **PUT** des octets vers l'**URL S3 pré-signée** (étape 2/3) : hors API VigieChiro (aucun en-tête
    /// d'auth, l'URL est déjà signée). Le `Content-Type` doit être le **mime attendu par la signature**
    /// (sinon S3 répond `SignatureDoesNotMatch`). `true` si 2xx, `false` sinon (dégradation propre).
    public boolean televerserVersS3(String urlSignee, byte[] octets, String mime) {
        try {
            HttpRequest requete = HttpRequest.newBuilder(URI.create(urlSignee))
                    .timeout(DELAI_UPLOAD)
                    .header(ENTETE_CONTENT_TYPE, mime)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(octets))
                    .build();
            HttpResponse<Void> reponse = client.send(requete, HttpResponse.BodyHandlers.discarding());
            return estSucces(reponse.statusCode());
        } catch (InterruptedException interrompu) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException | IOException indisponible) {
            return false;
        }
    }

    /// Variante **en flux** de [#televerserVersS3(String, byte[], String)] (#982) : le corps du `PUT` est
    /// **streamé depuis le disque** (`BodyPublishers.ofFile`) au lieu d'être chargé en mémoire — une
    /// archive ZIP de dépôt peut peser ~700 Mo. Mêmes garanties : `true` si 2xx, `false` sinon (fichier
    /// illisible compris).
    public boolean televerserVersS3(String urlSignee, Path fichier, String mime) {
        try {
            HttpRequest requete = HttpRequest.newBuilder(URI.create(urlSignee))
                    .timeout(DELAI_UPLOAD)
                    .header(ENTETE_CONTENT_TYPE, mime)
                    .PUT(HttpRequest.BodyPublishers.ofFile(fichier))
                    .build();
            HttpResponse<Void> reponse = client.send(requete, HttpResponse.BodyHandlers.discarding());
            return estSucces(reponse.statusCode());
        } catch (InterruptedException interrompu) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException | IOException indisponible) {
            return false;
        }
    }

    /// Finalise un fichier téléversé (`POST /fichiers/#id`, étape 3/3) : `true` si accepté, `false` sinon.
    public boolean finaliserFichier(String fichierId) {
        return post("/fichiers/" + fichierId, RequetesVigieChiro.finalisation()).isPresent();
    }

    /// **POST authentifié** d'un corps JSON sur `chemin` : renvoie le corps de la réponse si 2xx, vide
    /// sinon (pas de token, refus, autre statut, réseau indisponible). Pendant en écriture de [#get].
    Optional<String> post(String chemin, String corpsJson) {
        return postDetaille(chemin, corpsJson)
                .filter(reponse -> estSucces(reponse.statut()))
                .map(ReponseHttp::corps);
    }

    /// Variante **détaillée** de [#post] : renvoie la réponse HTTP complète (statut + corps), y compris en
    /// cas de refus (non-2xx), pour construire un message d'erreur exploitable (ex. la création de
    /// participation). Vide **seulement** si non connecté / réseau indisponible.
    Optional<ReponseHttp> postDetaille(String chemin, String corpsJson) {
        return ecrire("POST", chemin, corpsJson, null);
    }

    /// Écriture authentifiée (`POST` / `PATCH`) d'un corps JSON sur `chemin`, renvoyant la réponse HTTP
    /// complète (statut + corps). Si `etag` est non-`null`, ajoute l'en-tête `If-Match` (concurrence
    /// optimiste exigée par Eve pour les mises à jour). Vide **seulement** si non connecté / réseau
    /// indisponible ; un refus (non-2xx) revient avec son statut et son corps.
    private Optional<ReponseHttp> ecrire(String methode, String chemin, String corpsJson, String etag) {
        Optional<String> entete = enteteAuthorization();
        if (entete.isEmpty()) {
            return Optional.empty();
        }
        try {
            HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create(baseUrl + chemin))
                    .timeout(DELAI)
                    .header("Authorization", entete.get())
                    .header("Accept", TYPE_JSON)
                    .header(ENTETE_CONTENT_TYPE, TYPE_JSON)
                    .method(methode, HttpRequest.BodyPublishers.ofString(corpsJson, StandardCharsets.UTF_8));
            if (etag != null) {
                requete.header("If-Match", etag);
            }
            HttpResponse<String> reponse = client.send(requete.build(), HttpResponse.BodyHandlers.ofString());
            return Optional.of(new ReponseHttp(reponse.statusCode(), reponse.body()));
        } catch (InterruptedException interrompu) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException | IOException indisponible) {
            return Optional.empty();
        }
    }

    /// Statut HTTP de **succès** (2xx) : Eve renvoie `200` (finalisation) ou `201` (création), S3 `200`.
    private static boolean estSucces(int statut) {
        return statut >= 200 && statut < 300;
    }

    /// Réponse HTTP brute (statut + corps) d'une écriture, pour remonter le détail d'un refus.
    record ReponseHttp(int statut, String corps) {}

    /// **GET authentifié** sur `chemin` (relatif à la base) : renvoie le corps de la réponse si `200`,
    /// vide dans tous les autres cas (pas de token, `401`, autre non-`200`, réseau indisponible).
    Optional<String> get(String chemin) {
        Optional<String> entete = enteteAuthorization();
        if (entete.isEmpty()) {
            return Optional.empty();
        }
        try {
            HttpRequest requete = HttpRequest.newBuilder(URI.create(baseUrl + chemin))
                    .timeout(DELAI)
                    .header("Authorization", entete.get())
                    .header("Accept", TYPE_JSON)
                    .GET()
                    .build();
            HttpResponse<String> reponse = client.send(requete, HttpResponse.BodyHandlers.ofString());
            return reponse.statusCode() == 200 ? Optional.of(reponse.body()) : Optional.empty();
        } catch (InterruptedException interrompu) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException | IOException indisponible) {
            return Optional.empty();
        }
    }

    /// En-tête `Authorization` (`Basic base64("<token>:")`), ou vide si aucun token (non connecté).
    Optional<String> enteteAuthorization() {
        return fournisseurToken.token().map(ClientVigieChiro::basic);
    }

    private static String basic(String token) {
        String encode = Base64.getEncoder().encodeToString((token + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + encode;
    }
}
