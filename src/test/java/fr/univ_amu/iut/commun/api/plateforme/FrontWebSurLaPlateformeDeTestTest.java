package fr.univ_amu.iut.commun.api.plateforme;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// La plateforme de test sert le front web de Vigie-Chiro, et ce front atteint l'API (#6060).
///
/// C'est le premier pas vers une fiche web relue par un test (#5645) : sans navigateur encore, ce banc
/// lit ce qu'un navigateur chargerait, la page d'entrée et le script qui porte l'adresse de l'API.
///
/// Le second cas lit cette adresse DANS le script construit, au lieu de la supposer. Une construction
/// qui figerait l'adresse amont, `http://localhost:8080`, le ferait rougir : c'est celle d'une API que
/// le navigateur ne joindrait pas.
@Tag("plateforme-de-test")
class FrontWebSurLaPlateformeDeTestTest {

    private static final Pattern SCRIPT_DE_L_APPLICATION = Pattern.compile("scripts/scripts\\.[0-9a-f]+\\.js");

    private static final Pattern ADRESSE_DE_L_API = Pattern.compile("API_DOMAIN:\"([^\"]*)\"");

    @Test
    @DisplayName("#6060 : la page d'entrée du front se lit à l'adresse que la plateforme rend")
    void la_page_d_entree_du_front_se_lit_a_son_adresse() throws IOException, InterruptedException {
        HttpResponse<String> page = lire(URI.create(PlateformeDeTest.acces().urlDuFront()), null);

        assertThat(page.statusCode())
                .as("la racine de l'API doit servir le front construit")
                .isEqualTo(200);
        assertThat(page.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("text/html"));
        assertThat(page.body()).as("la page d'entrée du front amont").contains("<title>Vigiechiro");
    }

    @Test
    @DisplayName("#6060 : le front atteint l'API par l'adresse que son script construit emploie")
    void le_front_atteint_l_api_par_l_adresse_qu_il_emploie() throws IOException, InterruptedException {
        PlateformeDeTest.Acces acces = PlateformeDeTest.acces();
        URI front = URI.create(acces.urlDuFront());
        String page = lire(front, null).body();
        Matcher script = SCRIPT_DE_L_APPLICATION.matcher(page);
        assertThat(script.find())
                .as("la page d'entrée charge le script de l'application")
                .isTrue();

        Matcher adresse = ADRESSE_DE_L_API.matcher(
                lire(front.resolve(script.group()), null).body());
        assertThat(adresse.find())
                .as("le script construit porte `SETTINGS.API_DOMAIN`")
                .isTrue();
        URI api = front.resolve(adresse.group(1));

        assertThat(api)
                .as("résolue depuis la page, l'adresse du front doit mener à l'API de la plateforme")
                .isEqualTo(URI.create(acces.urlDeBase()));
        HttpResponse<String> moi = lire(URI.create(api + "/moi"), acces.jeton("observatrice"));
        assertThat(moi.statusCode())
                .as("l'API, jointe par l'adresse du front, reconnaît l'observatrice")
                .isEqualTo(200);
    }

    /// Un GET, avec le jeton en `Basic` comme le fait `TransportVigieChiro` quand il est donné.
    private static HttpResponse<String> lire(URI adresse, String jeton) throws IOException, InterruptedException {
        HttpRequest.Builder requete = HttpRequest.newBuilder(adresse);
        if (jeton != null) {
            String paire = Base64.getEncoder().encodeToString((jeton + ":").getBytes(StandardCharsets.UTF_8));
            requete.header("Authorization", "Basic " + paire);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(requete.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
