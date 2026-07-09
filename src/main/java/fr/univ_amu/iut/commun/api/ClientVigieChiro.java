package fr.univ_amu.iut.commun.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
        return get("/moi/participations")
                .map(ReponsesVigieChiro::sitesDepuisParticipations)
                .orElseGet(List::of);
    }

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
                    .header("Accept", "application/json")
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
