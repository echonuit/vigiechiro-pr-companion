package fr.univ_amu.iut.commun.api;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// **Suite de contrat API « vivante »** (#axe 4, dépôt VigieChiro) : documentation exécutable de notre
/// compréhension actuelle de l'API REST VigieChiro (backend Python-Eve). Elle **tape l'API réelle** et
/// vérifie deux choses d'un coup : (a) que l'API **n'a pas bougé** (schéma, formats, domaines de valeurs),
/// et (b) que **notre client** (`ClientVigieChiro` + parseurs) la lit toujours correctement.
///
/// **Hors CI / hors build par défaut** : taguée `@Tag("api-live")`, exclue par `surefire.excludedGroups`.
/// Lancement **manuel** :
/// ```
/// ./mvnw -Papi-live test -Dvigiechiro.token=XXXX
/// ```
/// Sans `-Dvigiechiro.token`, toute la suite se **skippe** proprement (`Assumptions`), jamais d'échec
/// accidentel. Le token 14 j se récupère via le marque-page `localStorage['auth-session-token']`.
///
/// Les `@DisplayName` **sont** la documentation. La forme précise des objets vit dans les JSON Schema
/// partagés (`src/test/resources/vigiechiro/*.schema.json`), validés ici et réutilisés par la collection
/// Postman (une seule source de vérité, pas de contrat dupliqué).
///
/// Ce fichier ne couvre que le **mode lecture** (idempotent, sûr). Les contrats d'**écriture** (POST/PATCH,
/// probes ZIP et site) arrivent en mode opt-in `-Dvigiechiro.write=true`.
@Tag("api-live")
@DisplayName("Contrat API VigieChiro (live, lecture) — documentation vivante du schéma")
class ContratApiVigieChiroLiveTest {

    private static String baseUrl;
    private static String token;

    @BeforeAll
    static void configurer() {
        token = System.getProperty("vigiechiro.token");
        assumeTrue(
                token != null && !token.isBlank(),
                "Suite de contrat API ignorée : fournir -Dvigiechiro.token=… (profil -Papi-live).");
        baseUrl = System.getProperty("vigiechiro.baseUrl", "https://vigiechiro.herokuapp.com/api/v1");
        RestAssured.baseURI = baseUrl;
    }

    /// Requête authentifiée : Basic `base64("<token>:")` — token en *username*, mot de passe vide, comme
    /// [ClientVigieChiro.enteteAuthorization].
    private static RequestSpecification api() {
        return given().auth().preemptive().basic(token, "");
    }

    @Test
    @DisplayName("GET /moi : le profil de l'observateur connecté est accessible (le token est valide)")
    void moi_est_accessible() {
        api().when().get("/moi").then().statusCode(200);
    }

    @Test
    @DisplayName("GET /moi/participations : collection Eve `_items` ; chaque participation porte _id, point,"
            + " date_debut (ISO +00:00) et _etag")
    void liste_participations() {
        api().when()
                .get("/moi/participations")
                .then()
                .statusCode(200)
                .body("_items", not(empty()))
                .body("_items[0]._id", notNullValue())
                .body("_items[0].point", notNullValue())
                .body("_items[0].date_debut", endsWith("+00:00"))
                .body("_items[0]._etag", notNullValue());
    }

    @Test
    @DisplayName("GET /participations/{id} : conforme au JSON Schema participation"
            + " (meteo/configuration/traitement optionnels, enums vent/couverture, dates ISO +00:00, _etag)")
    void participation_respecte_le_schema() {
        String id = api().when()
                .get("/moi/participations")
                .then()
                .statusCode(200)
                .extract()
                .path("_items[0]._id");

        api().when()
                .get("/participations/{id}", id)
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("vigiechiro/participation.schema.json"));
    }

    @Test
    @DisplayName("Dérive client : ClientVigieChiro.mesParticipations() lit encore la collection réelle")
    void client_lit_les_participations() {
        ClientVigieChiro client = new ClientVigieChiro(baseUrl, () -> Optional.of(token));

        List<ParticipationVigieChiro> participations = client.mesParticipations();

        assertThat(participations)
                .as("le parseur ParticipationsVigieChiro lit la réponse réelle")
                .isNotEmpty();
        assertThat(participations.getFirst().point())
                .as("point (code localité) non nul")
                .isNotNull();
    }

    @Test
    @DisplayName("Dérive client : ClientVigieChiro.participation(id) lit le détail réel (_etag présent)")
    void client_lit_le_detail_participation() {
        ClientVigieChiro client = new ClientVigieChiro(baseUrl, () -> Optional.of(token));
        String id = client.mesParticipations().getFirst().id();

        ParticipationDetail detail = client.participation(id).orElseThrow();

        assertThat(detail.etag())
                .as("_etag présent (requis en If-Match pour un futur PATCH)")
                .isNotNull();
        assertThat(detail.point()).as("point (code localité) non nul").isNotNull();
    }
}
