package fr.univ_amu.iut.maj.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.univ_amu.iut.maj.model.DerniereVersionPubliee;
import fr.univ_amu.iut.maj.model.NumeroDeVersion;
import fr.univ_amu.iut.maj.model.VersionDisponible;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Lit la dernière version publiée depuis l'**API des Releases GitHub** du dépôt (#2109).
///
/// `GET /repos/{dépôt}/releases/latest` rend la dernière release **publiée** : les brouillons et les
/// pré-versions en sont exclus par GitHub lui-même. C'est exactement ce qu'on veut - le dépôt a déjà
/// produit des brouillons incomplets quand un job d'installeur échouait, et les proposer serait
/// envoyer l'utilisateur vers une version sans artefact pour son système.
///
/// **Best-effort**, conformément au contrat du port : toute défaillance rend [Optional#empty()].
/// L'appel est journalisé au niveau `FINE` et jamais au-dessus : une machine hors ligne est un état
/// normal en sortie de terrain, pas un incident à faire remonter.
///
/// Aucune authentification. La limite de débit anonyme de GitHub (60 requêtes par heure et par IP)
/// est très au-dessus d'un appel par démarrage ; la dépasser rend simplement un `Optional` vide.
public final class DerniereVersionGitHub implements DerniereVersionPubliee {

    private static final Logger LOG = Logger.getLogger(DerniereVersionGitHub.class.getName());

    private static final String DEPOT = "IUTInfoAix-S201/vigiechiro-pr-companion";

    /// Court à dessein : au démarrage, mieux vaut renoncer que faire attendre. Le délai s'applique à
    /// la connexion comme à la requête.
    private static final Duration DELAI = Duration.ofSeconds(4);

    private final URI adresseApi;
    private final HttpClient client;

    public DerniereVersionGitHub() {
        this(URI.create("https://api.github.com/repos/" + DEPOT + "/releases/latest"));
    }

    /// Constructeur d'essai : permet de pointer un serveur local dans les tests, sans toucher au
    /// réseau ni dépendre de la disponibilité de GitHub.
    DerniereVersionGitHub(URI adresseApi) {
        this.adresseApi = adresseApi;
        this.client = HttpClient.newBuilder().connectTimeout(DELAI).build();
    }

    @Override
    public Optional<VersionDisponible> consulter() {
        try {
            HttpRequest requete = HttpRequest.newBuilder(adresseApi)
                    .timeout(DELAI)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();
            HttpResponse<String> reponse = client.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() != 200) {
                LOG.fine(() -> "Consultation des versions : code " + reponse.statusCode());
                return Optional.empty();
            }
            return lire(reponse.body());
        } catch (InterruptedException interruption) {
            // Rétablir le drapeau : l'appelant (une tâche de fond au démarrage) doit pouvoir s'arrêter.
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException | java.io.IOException echec) {
            LOG.log(Level.FINE, echec, () -> "Consultation des versions impossible");
            return Optional.empty();
        }
    }

    /// Extrait le numéro et l'adresse de la réponse, ou rend vide si elle n'a pas la forme attendue.
    ///
    /// Visible du test à dessein : c'est ici que vivent tous les cas de réponse inattendue, et le
    /// dépôt ne peut pas monter de serveur local en test - `jdk.httpserver` n'est pas lisible depuis
    /// ce module (contrainte JPMS connue). Les exercer directement vaut mieux que de ne pas les
    /// exercer.
    Optional<VersionDisponible> lire(String corps) {
        JsonObject json = JsonParser.parseString(corps).getAsJsonObject();
        if (!json.has("tag_name") || !json.has("html_url")) {
            return Optional.empty();
        }
        return NumeroDeVersion.lire(json.get("tag_name").getAsString())
                .map(numero ->
                        new VersionDisponible(numero, json.get("html_url").getAsString()));
    }
}
