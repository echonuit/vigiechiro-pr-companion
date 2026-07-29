package fr.univ_amu.iut.commun.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.univ_amu.iut.commun.model.Commune;
import fr.univ_amu.iut.commun.model.PositionGeo;
import fr.univ_amu.iut.commun.model.ResolveurCommune;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Résolution de commune par l'**API Géo** (`geo.api.gouv.fr`) : point-dans-polygone officiel,
/// gratuit, sans clé (#2791). Appelle `GET /communes?lat=…&lon=…&fields=nom,code&format=json` et lit
/// la première commune du tableau (le point est contenu dans une seule commune ; l'API en renvoie
/// zéro en mer ou hors de France).
///
/// **Best-effort** intégral, comme [ClientGbif] : toute erreur (hors ligne, statut inattendu, corps
/// illisible) renvoie [Optional#empty()] - la commune restera absente et le rattrapage la comblera.
/// Délai aligné sur le transport VigieChiro (10 s).
public final class ResolveurCommuneApiGeo implements ResolveurCommune {

    private static final Logger LOG = Logger.getLogger(ResolveurCommuneApiGeo.class.getName());

    private static final String BASE = "https://geo.api.gouv.fr";

    private static final Duration DELAI = Duration.ofSeconds(10);

    private final String baseUrl;
    private final HttpClient client;

    public ResolveurCommuneApiGeo() {
        this(BASE, HttpClient.newBuilder().connectTimeout(DELAI).build());
    }

    /// Constructeur d'injection : URL et client fournis, pour que les tests simulent une réponse
    /// (Mockito sur [HttpClient]) sans réseau réel - JPMS interdisant un serveur HTTP local en test.
    ResolveurCommuneApiGeo(String baseUrl, HttpClient client) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public Optional<Commune> resoudre(PositionGeo position) {
        if (position == null) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(baseUrl + "/communes?lat=" + position.latitude() + "&lon=" + position.longitude()
                    + "&fields=nom,code&format=json");
            HttpRequest requete = HttpRequest.newBuilder(uri)
                    .timeout(DELAI)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> reponse = client.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() != 200) {
                return Optional.empty();
            }
            return lire(reponse.body());
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException echec) {
            LOG.log(Level.FINE, echec, () -> "Résolution de commune impossible pour " + position);
            return Optional.empty();
        }
    }

    /// Lit la première commune du corps JSON de l'API Géo (tableau `[{"nom":…,"code":…}]`), ou vide
    /// si le tableau est vide ou le corps illisible. Statique et sans réseau : testable sur une
    /// réponse figée, comme `MeteoOpenMeteo.parse`.
    static Optional<Commune> lire(String json) {
        try {
            JsonArray communes = JsonParser.parseString(json).getAsJsonArray();
            if (communes.isEmpty()) {
                return Optional.empty();
            }
            JsonObject premiere = communes.get(0).getAsJsonObject();
            if (!premiere.has("nom") || !premiere.has("code")) {
                return Optional.empty();
            }
            return Optional.of(new Commune(
                    premiere.get("nom").getAsString(), premiere.get("code").getAsString()));
        } catch (RuntimeException corpsIllisible) {
            LOG.log(Level.FINE, "Réponse de l'API Géo illisible", corpsIllisible);
            return Optional.empty();
        }
    }
}
