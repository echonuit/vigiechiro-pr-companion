package fr.univ_amu.iut.commun.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Client de l'API **GBIF** : résout un nom latin en **clé d'usage** (`usageKey`), pour ouvrir la fiche
/// d'espèce `https://www.gbif.org/species/{clé}` au lieu d'une page de recherche (#922).
///
/// Appelle `GET https://api.gbif.org/v1/species/match?name=<nom latin>` et lit `usageKey` (rejeté si
/// `matchType` vaut `NONE`). **Best-effort** : toute erreur (réseau, réponse inattendue, aucune
/// correspondance) renvoie [Optional#empty()] : l'appelant retombe alors sur la recherche.
public final class ClientGbif {

    private static final Logger LOG = Logger.getLogger(ClientGbif.class.getName());

    private static final String MATCH = "https://api.gbif.org/v1/species/match?name=";

    private static final Duration DELAI = Duration.ofSeconds(5);

    private final HttpClient client;

    public ClientGbif() {
        this(HttpClient.newBuilder().connectTimeout(DELAI).build());
    }

    /// Constructeur d'injection : le client HTTP est fourni, pour que les tests simulent une réponse
    /// (Mockito sur [HttpClient]) sans réseau réel - JPMS interdisant un serveur HTTP local en test.
    /// Même patron que [ResolveurCommuneApiGeo], et posé pour la même raison : cette classe n'avait
    /// aucun test, faute d'une couture par où l'éprouver.
    ClientGbif(HttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    /// Clé d'usage GBIF du taxon `nomLatin`, ou vide si non résolu (aucune correspondance ou erreur).
    public Optional<Long> cleUsage(String nomLatin) {
        if (nomLatin == null || nomLatin.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(MATCH + URLEncoder.encode(nomLatin.strip(), StandardCharsets.UTF_8));
            HttpRequest requete =
                    HttpRequest.newBuilder(uri).timeout(DELAI).GET().build();
            HttpResponse<InputStream> reponse = client.send(requete, HttpResponse.BodyHandlers.ofInputStream());
            // Corps lu sous plafond (#3222) : GBIF est un service tiers, sa réponse est une entrée
            // externe comme une autre. Un dépassement retombe dans le filet ci-dessous, donc en
            // résolution vide : cette recherche est un confort, pas une garantie.
            String corps = CorpsReponse.sousPlafond(reponse, "GBIF " + nomLatin);
            if (reponse.statusCode() != 200) {
                return Optional.empty();
            }
            JsonObject json = JsonParser.parseString(corps).getAsJsonObject();
            boolean correspond = json.has("matchType")
                    && !"NONE".equals(json.get("matchType").getAsString());
            if (correspond && json.has("usageKey")) {
                return Optional.of(json.get("usageKey").getAsLong());
            }
            return Optional.empty();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException echec) {
            LOG.log(Level.FINE, echec, () -> "Résolution GBIF impossible pour « " + nomLatin + " »");
            return Optional.empty();
        }
    }
}
