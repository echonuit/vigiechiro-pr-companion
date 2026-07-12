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
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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

    // ---------------------------------------------------------------------------------------------
    // PROBES en écriture (opt-in -Dvigiechiro.write=true) : remplacent les spikes jetables. Elles
    // ÉCRIVENT sur la plateforme (fichier d'essai, PATCH quasi no-op) — à lancer sciemment, jamais
    // en veille périodique. Leur échec est un VERDICT documenté, pas forcément une régression.
    // ---------------------------------------------------------------------------------------------

    /// Garde des probes d'écriture : ignorées sans l'opt-in explicite.
    private static void supposerEcritureAutorisee() {
        assumeTrue(
                Boolean.getBoolean("vigiechiro.write"),
                "Probe d'écriture ignorée : opt-in -Dvigiechiro.write=true (écrit sur la plateforme).");
    }

    @Test
    @DisplayName("PROBE #984 (pilier B) : un ZIP déclaré + téléversé (application/zip) + finalisé est-il"
            + " accepté ? (échec = verdict WAV confirmé)")
    void probe_zip_vs_wav() {
        supposerEcritureAutorisee();
        ClientVigieChiro client = new ClientVigieChiro(baseUrl, () -> Optional.of(token));

        Optional<FichierSigne> signe = client.creerFichier("Car000000-2026-Pass1-Z0-probe.zip", "probe");
        assertThat(signe)
                .as("déclaration d'un .zip : refusée = verdict immédiat (les .zip ne sont pas des titres"
                        + " valides), le dépôt reste en WAV")
                .isPresent();

        boolean televerse = client.televerserVersS3(signe.get().urlSignee(), zipDEssai(), "application/zip");
        assertThat(televerse).as("PUT S3 du zip (mime signé application/zip)").isTrue();

        boolean finalise = client.finaliserFichier(signe.get().id());
        assertThat(finalise)
                .as("finalisation du zip : true = la plateforme ACCEPTE un zip comme fichier de"
                        + " participation. Vérifier ensuite à la main (après traitement Tadarida) que"
                        + " donnees() contient les fichiers DÉZIPPÉS avant de basculer le dépôt sur les"
                        + " archives (#984).")
                .isTrue();
    }

    @Test
    @DisplayName("PROBE Phase 4 (#941) : PATCH /sites/{id} (localites, If-Match) est-il autorisé à"
            + " l'observateur ? (échec 403/405 = push point→site abandonné)")
    void probe_patch_site_localites() {
        supposerEcritureAutorisee();
        // Site d'une participation de l'observateur (site régional : il y participe sans en être
        // propriétaire — le cas réel du terrain).
        String idSite = api().when()
                .get("/moi/participations")
                .then()
                .statusCode(200)
                .extract()
                .path("_items[0].site._id");

        var reponseSite = api().when().get("/sites/{id}", idSite).then().statusCode(200);
        String etag = reponseSite.extract().path("_etag");
        List<Map<String, Object>> localites = reponseSite.extract().path("localites");
        assertThat(etag).as("_etag du site (requis en If-Match)").isNotNull();

        // PATCH quasi no-op : renvoyer les localités TELLES QUELLES (aucune coordonnée altérée). Le
        // statut de la réponse EST le verdict : 200 = l'observateur peut écrire (le push point→site de
        // la Phase 4 est faisable), 403/405 = interdit (on abandonne le push, le pull reste possible).
        int statut = api().header("If-Match", etag)
                .contentType("application/json")
                .body(Map.of("localites", localites))
                .when()
                .patch("/sites/{id}", idSite)
                .then()
                .extract()
                .statusCode();
        assertThat(statut)
                .as("VERDICT Phase 4 : 200 = push autorisé ; 403/405 = interdit (abandonner 4b) ;"
                        + " 422 = schéma du corps à ajuster (re-sonder)")
                .isEqualTo(200);
    }

    /// Petit ZIP d'essai en mémoire (une entrée texte) : suffisant pour sonder l'acceptation du format,
    /// sans téléverser un vrai WAV de plusieurs Mo.
    private static byte[] zipDEssai() {
        try (ByteArrayOutputStream octets = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(octets)) {
            zip.putNextEntry(new ZipEntry("probe.txt"));
            zip.write("probe vigiechiro-pr-companion".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return octets.toByteArray();
        } catch (java.io.IOException impossible) {
            throw new IllegalStateException("Construction du zip d'essai impossible", impossible);
        }
    }
}
