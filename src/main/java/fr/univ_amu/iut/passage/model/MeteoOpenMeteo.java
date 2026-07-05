package fr.univ_amu.iut.passage.model;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Implémentation d'[FournisseurMeteo] adossée à **Open-Meteo** (API d'archive, **gratuite et sans
/// clé**, #547). Interroge l'historique horaire au point pour la nuit d'enregistrement et en extrait la
/// température de début/fin de nuit, le vent et la couverture nuageuse.
///
/// **Dégradation propre** : timeout court, et toute erreur (hors-ligne, HTTP non 200, JSON inattendu)
/// est convertie en [Optional#empty()] — jamais d'exception remontée (comme le fond de carte OSM).
/// Aucune dépendance ajoutée : `java.net.http` (JDK) + un lecteur JSON minimal (le projet ne `requires`
/// aucune bibliothèque JSON).
///
/// Limite connue : l'API d'**archive** accuse quelques jours de retard ; une nuit très récente peut
/// renvoyer un relevé vide (l'utilisateur saisit alors à la main).
public final class MeteoOpenMeteo implements FournisseurMeteo {

    private static final String URL_ARCHIVE = "https://archive-api.open-meteo.com/v1/archive";
    private static final Duration DELAI = Duration.ofSeconds(5);

    private final String baseUrl;
    private final HttpClient client;

    public MeteoOpenMeteo() {
        this(URL_ARCHIVE);
    }

    /// Constructeur d'injection de l'URL de base (tests hors-ligne : une URL injoignable → `empty`).
    MeteoOpenMeteo(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = HttpClient.newBuilder().connectTimeout(DELAI).build();
    }

    @Override
    public Optional<MeteoReleve> pour(
            double latitude, double longitude, LocalDate date, LocalTime debut, LocalTime fin) {
        LocalDate dateFin = fin.isBefore(debut) ? date.plusDays(1) : date;
        String url = String.format(
                Locale.ROOT,
                "%s?latitude=%.4f&longitude=%.4f&start_date=%s&end_date=%s"
                        + "&hourly=temperature_2m,windspeed_10m,cloudcover&timezone=auto",
                baseUrl,
                latitude,
                longitude,
                date,
                dateFin);
        try {
            HttpRequest requete =
                    HttpRequest.newBuilder(URI.create(url)).timeout(DELAI).GET().build();
            HttpResponse<String> reponse = client.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() != 200) {
                return Optional.empty();
            }
            return parse(reponse.body(), isoHeure(date, debut), isoHeure(dateFin, fin));
        } catch (InterruptedException interrompu) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException | java.io.IOException indisponible) {
            return Optional.empty();
        }
    }

    /// Extrait un [MeteoReleve] du corps JSON Open-Meteo, en alignant les heures cibles `debutIso` /
    /// `finIso` (format `AAAA-MM-JJThh:00`) sur le tableau horaire `hourly.time`. Renvoie `empty` si
    /// aucune grandeur n'a pu être lue. Package-visible : testable sur une réponse figée, sans réseau.
    static Optional<MeteoReleve> parse(String corps, String debutIso, String finIso) {
        List<String> temps = tableau(corps, "time");
        List<String> temperatures = tableau(corps, "temperature_2m");
        List<String> vents = tableau(corps, "windspeed_10m");
        List<String> couvertures = tableau(corps, "cloudcover");
        int iDebut = temps.indexOf("\"" + debutIso + "\"");
        int iFin = temps.indexOf("\"" + finIso + "\"");

        Double tempDebut = valeur(temperatures, iDebut);
        Double tempFin = valeur(temperatures, iFin);
        Double vent = valeur(vents, iDebut);
        Double couverture = valeur(couvertures, iDebut);

        MeteoReleve releve = new MeteoReleve(tempDebut, tempFin, vent, couverture);
        return releve.estVide() ? Optional.empty() : Optional.of(releve);
    }

    /// Contenu d'un tableau JSON plat `"cle":[...]` en jetons bruts (les chaînes gardent leurs
    /// guillemets), ou liste vide s'il est absent. Les tableaux Open-Meteo ne sont pas imbriqués.
    private static List<String> tableau(String json, String cle) {
        Matcher m = Pattern.compile("\"" + cle + "\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(json);
        if (!m.find()) {
            return List.of();
        }
        String contenu = m.group(1).trim();
        if (contenu.isEmpty()) {
            return List.of();
        }
        return List.of(contenu.split("\\s*,\\s*"));
    }

    /// Valeur numérique à l'indice `i` d'un tableau de jetons, ou `null` si l'indice est hors bornes ou
    /// si le jeton n'est pas un nombre fini (`null` Open-Meteo, `NaN`…).
    private static Double valeur(List<String> jetons, int i) {
        if (i < 0 || i >= jetons.size()) {
            return null;
        }
        try {
            double v = Double.parseDouble(jetons.get(i));
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException illisible) {
            return null;
        }
    }

    private static String isoHeure(LocalDate date, LocalTime heure) {
        return String.format(Locale.ROOT, "%sT%02d:00", date, heure.getHour());
    }
}
