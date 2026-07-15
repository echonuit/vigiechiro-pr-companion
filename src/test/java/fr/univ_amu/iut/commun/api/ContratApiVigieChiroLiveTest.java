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
import java.util.ArrayList;
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
/// Ce fichier couvre d'abord le **mode lecture** (idempotent, sûr). Les contrats d'**écriture** (POST/PATCH :
/// probes ZIP, site et corrections d'observations) sont opt-in `-Dvigiechiro.write=true` ; les probes de
/// corrections (#1203) exigent en plus une participation **banc d'essai** explicitement désignée
/// (`-Dvigiechiro.participationEssai=<id>`), car une correction posée ne se retire pas.
///
/// ## La seule route qu'on ne peut pas défaire (#1456)
///
/// `PUT /donnees/{id}/observations/{index}/messages` est **à part**. Toutes les autres écritures se
/// rattrapent : un `PATCH` de correction **remplace**, un `POST /participations` se re-modifie, un dépôt se
/// réinitialise. Celle-ci, **non** : le serveur **ajoute** par `$push`, et **aucune route ne permet de
/// supprimer ni de modifier un message**. Ce qu'elle écrit **reste**, sur des données que lit un validateur
/// du MNHN. Il n'y a pas de « nettoyage après test ».
///
/// Elle exige donc **trois** verrous, et non deux : `-Dvigiechiro.write=true`,
/// `-Dvigiechiro.participationEssai=<id>` **et** `-Dvigiechiro.message=true`. Le troisième existe pour une
/// raison précise : sans lui, qui lance les probes d'écriture pour éprouver les **corrections** pousserait,
/// sans le vouloir, une trace **définitive**.
///
/// Le contrat live **hebdomadaire** (`api-live.yml`) est en **lecture seule** et doit le rester : il ne
/// passe aucun de ces trois drapeaux.
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
    @DisplayName("Transport #1284 : un refus serveur revient en Refuse avec son statut (422 pour"
            + " max_results>100), plus jamais en vide silencieux — le verrou qui aurait rendu #1277 bruyante")
    void refus_serveur_est_un_refuse_explicite() {
        TransportVigieChiro transport = new TransportVigieChiro(baseUrl, () -> Optional.of(token));

        ReponseApi<String> reponse = transport.lire("/moi/participations?max_results=1000&page=1");

        assertThat(reponse)
                .as("Eve rejette max_results>100 : le transport doit conserver ce refus, pas le taire")
                .isInstanceOf(ReponseApi.Refuse.class);
        assertThat(((ReponseApi.Refuse<String>) reponse).statut()).isEqualTo(422);
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

        List<ParticipationVigieChiro> participations =
                client.mesParticipations().enOptionnel().orElseThrow();

        assertThat(participations)
                .as("le parseur ParticipationsVigieChiro lit la réponse réelle")
                .isNotEmpty();
        assertThat(participations.getFirst().point())
                .as("point (code localité) non nul")
                .isNotNull();
    }

    @Test
    @DisplayName("GET /grille_stoc/cercle (#733) : le carré rendu pour les coordonnées d'un point EST le carré"
            + " de son site — la sonde qui tranche la convention lat/lng")
    void grille_stoc_rend_le_carre_du_point() {
        ClientVigieChiro client = new ClientVigieChiro(baseUrl, () -> Optional.of(token));

        // Un site RÉEL de l'observateur, avec un point géolocalisé : on connaît donc déjà la bonne réponse.
        // La sonde est ainsi auto-vérifiante, sans coordonnées codées en dur qui périmeraient.
        Optional<SiteVigieChiro> siteGeolocalise = client.mesSites().enOptionnel().orElseThrow().stream()
                .filter(site -> site.numeroCarre() != null && !site.points().isEmpty())
                .findFirst();
        assumeTrue(siteGeolocalise.isPresent(), "Aucun site géolocalisé sur ce compte : sonde sans objet.");
        SiteVigieChiro site = siteGeolocalise.get();
        PointVigieChiro point = site.points().getFirst();

        Optional<String> carre = client.carreStoc(point.latitude(), point.longitude())
                .enOptionnel()
                .orElseThrow();

        // Le vrai risque de cet endpoint n'est pas qu'il échoue : c'est qu'il réponde À CÔTÉ. La plateforme
        // mélange les conventions (les localités d'un site sont stockées [lat, lon], à rebours du GeoJSON),
        // et une inversion lat/lng rendrait un carré parfaitement plausible... au milieu de l'Asie. Seule
        // une confrontation au réel le dit — leçon de #1277, que seul le contrat live avait vue.
        assertThat(carre)
                .as("les coordonnées du point %s tombent dans le carré de son propre site", point.code())
                .contains(site.numeroCarre());
    }

    @Test
    @DisplayName("Dérive client : ClientVigieChiro.participation(id) lit le détail réel (_etag présent)")
    void client_lit_le_detail_participation() {
        ClientVigieChiro client = new ClientVigieChiro(baseUrl, () -> Optional.of(token));
        String id = client.mesParticipations()
                .enOptionnel()
                .orElseThrow()
                .getFirst()
                .id();

        ParticipationDetail detail = client.participation(id).enOptionnel().orElseThrow();

        assertThat(detail.etag())
                .as("_etag présent (requis en If-Match pour un futur PATCH)")
                .isNotNull();
        assertThat(detail.point()).as("point (code localité) non nul").isNotNull();
    }

    @Test
    @DisplayName("Dérive client : le bloc traitement réel est lisible (état connu, dates cohérentes) — #1260")
    void client_lit_le_traitement_reel() {
        ClientVigieChiro client = new ClientVigieChiro(baseUrl, () -> Optional.of(token));
        String id = client.mesParticipations()
                .enOptionnel()
                .orElseThrow()
                .getFirst()
                .id();

        Traitement traitement =
                client.participation(id).enOptionnel().orElseThrow().traitement();

        // Une participation jamais calculée n'a pas de bloc traitement : c'est un cas légitime, pas un
        // échec. En revanche, s'il y en a un, le parseur doit en reconnaître l'état — un état inconnu
        // signalerait que le backend a introduit une valeur que cette version ignore (le point même de
        // cette sonde : détecter la dérive).
        if (traitement.estInconnu()) {
            assertThat(traitement)
                    .as("aucun traitement : le bloc doit être l'absence franche, pas un état non reconnu")
                    .isEqualTo(Traitement.absent());
            return;
        }

        assertThat(traitement.etat())
                .as("état reconnu parmi les 5 valeurs du backend (participations.py:73)")
                .isIn((Object[]) EtatTraitement.values());

        // ⚠️ Ne PAS exiger `date_planification` : le serveur **remplace** tout le sous-document
        // `traitement` à chaque étape (`p_resource.update(id, {'traitement': …})`), il ne le complète pas.
        // Dès qu'un worker démarre, le bloc devient {etat, date_debut} et la date de planification
        // **disparaît**. Constaté en réel sur la participation canonique (FINI, sans date_planification) :
        // c'est ce tir qui a corrigé l'hypothèse.
        if (traitement.resultatsDisponibles()) {
            assertThat(traitement.dateDebut())
                    .as("FINI : le traitement a forcément démarré")
                    .isNotNull();
            assertThat(traitement.dateFin())
                    .as("FINI : la date de fin est posée par le serveur")
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("PROBE #1565 : le CSV d'observations est disponible sur S3 et se télécharge d'un coup."
            + " Découverte via pieces_jointes?processing_extra=true (PAS /fichiers, non listable), puis"
            + " /fichiers/{id}/acces -> URL signée. Entête Tadarida BRUT, sans _id : un seul téléchargement"
            + " remplace les ~48 pages de donnees (reconstruction quasi instantanée)")
    void csv_observations_est_telechargeable_via_pieces_jointes() {
        String participation = participationTraitee();

        // La BONNE route de découverte : pieces_jointes filtré sur processing_extra. Le CSV d'observations
        // est généré avec force_upload=True (task_observations_csv.py:52, backend Scille/vigiechiro-api),
        // donc TOUJOURS monté sur S3 (comme les logs, contrairement aux WAV extraits d'un ZIP, #1244). La
        // collection /fichiers, elle, n'est pas listable (403) : c'est pourquoi le _id du CSV vient d'ici.
        var extra = api().when()
                .get("/participations/{id}/pieces_jointes?processing_extra=true", participation)
                .then()
                .statusCode(200)
                .extract();
        String idCsv = extra.path("_items[0]._id");
        assumeTrue(idCsv != null, "Pas de CSV d'observations (processing_extra) sur cette participation.");
        assertThat(extra.<String>path("_items[0].titre"))
                .as("le fichier processing_extra EST le CSV d'observations")
                .endsWith("-observations.csv");
        assertThat(extra.<Boolean>path("_items[0].disponible"))
                .as("le CSV est monté sur S3 (force_upload=True) : disponible, contrairement aux WAV en ZIP")
                .isTrue();

        // /acces rend une URL S3 signée (même mécanisme que le journal, JournalVigieChiro.urlSignee).
        String urlSignee = api().when()
                .get("/fichiers/{id}/acces", idCsv)
                .then()
                .statusCode(200)
                .extract()
                .path("s3_signed_url");
        assertThat(urlSignee).as("URL S3 signée du CSV").isNotNull();

        // Téléchargement direct, SANS Authorization (la signature de l'URL fait foi) et SANS ré-encoder
        // l'URL (sinon la signature casse). Un seul appel ramène toutes les observations.
        String csv = given().urlEncodingEnabled(false)
                .when()
                .get(urlSignee)
                .then()
                .statusCode(200)
                .extract()
                .asString();
        String entete = csv.lines().findFirst().orElse("");
        assertThat(entete)
                .as("entête Tadarida BRUT (séparateur ';', champs quotés)")
                .contains("nom du fichier")
                .contains("temps_debut")
                .contains("frequence_mediane")
                .contains("tadarida_taxon");
        assertThat(entete)
                .as("le CSV ne porte AUCUN _id d'observation : l'ancrage plateforme doit venir d'ailleurs"
                        + " (à la réactivation, #1565)")
                .doesNotContain("_id");
    }

    @Test
    @DisplayName("PROBE #1565 : donnees?where={titre} est SILENCIEUSEMENT IGNORÉ (renvoie le jeu complet,"
            + " _meta.total inchangé). Même classe de faux-négatif que max_results>100 (#1277) : l'ancrage"
            + " à la réactivation ne peut donc pas se résoudre par fichier, il faut une passe donnees complète")
    void donnees_where_titre_est_silencieusement_ignore() {
        String participation = participationTraitee();

        int total = api().when()
                .get("/participations/{id}/donnees?max_results=1", participation)
                .then()
                .statusCode(200)
                .extract()
                .path("_meta.total");
        assumeTrue(total > 1, "Il faut au moins deux donnees pour prouver que le filtre ne filtre pas.");
        String titre = api().when()
                .get("/participations/{id}/donnees?max_results=1", participation)
                .then()
                .statusCode(200)
                .extract()
                .path("_items[0].titre");

        // where={"titre":"<un seul fichier>"} : si le filtre s'appliquait, _meta.total vaudrait le nombre
        // de donnees de CE fichier (une poignée), pas le total de la participation. RestAssured encode le
        // queryParam comme Eve l'attend.
        int totalFiltre = api().queryParam("where", "{\"titre\":\"" + titre + "\"}")
                .when()
                .get("/participations/{id}/donnees", participation)
                .then()
                .statusCode(200)
                .extract()
                .path("_meta.total");

        assertThat(totalFiltre)
                .as("where={titre} est ignoré : le serveur renvoie TOUT le jeu, pas le sous-ensemble du"
                        + " fichier demandé. L'ancrage à la réactivation exige donc une passe donnees complète")
                .isEqualTo(total);
    }

    @Test
    @DisplayName("Dérive client (#1565) : ClientVigieChiro.piecesJointes + csvObservations télécharge le CSV"
            + " réel d'un coup (processing_extra -> accesFichier), entête Tadarida BRUT")
    void client_telecharge_le_csv_d_observations() {
        ClientVigieChiro client = new ClientVigieChiro(baseUrl, () -> Optional.of(token));
        String participation = participationTraitee();

        List<PieceJointe> extra = client.piecesJointes(participation, TypePieceJointe.PROCESSING_EXTRA)
                .enOptionnel()
                .orElseThrow();
        assumeTrue(
                extra.stream().anyMatch(p -> p.titre() != null && p.titre().endsWith("-observations.csv")),
                "Pas de CSV d'observations sur cette participation.");

        Optional<String> csv =
                client.csvObservations(participation).enOptionnel().orElseThrow();
        assertThat(csv)
                .as("le CSV est présent et téléchargé d'un coup (une requête)")
                .isPresent();
        assertThat(csv.orElseThrow().lines().findFirst().orElse(""))
                .as("entête Tadarida BRUT lue par le client")
                .contains("nom du fichier")
                .contains("tadarida_taxon");
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

    /// **Troisième** verrou, pour la seule route **irréversible** du client (#1456).
    ///
    /// Toutes les autres écritures se rattrapent : un `PATCH` de correction **remplace** (#1203), un
    /// `POST /participations` se re-modifie, un dépôt se réinitialise. `PUT …/messages`, **non** : le
    /// serveur **ajoute** par `$push`, et **aucune route ne permet de supprimer ni de modifier un
    /// message**. Il n'y a pas de « nettoyage après test ».
    ///
    /// `-Dvigiechiro.write=true` ne suffit donc **pas** : quelqu'un qui lance les probes d'écriture pour
    /// éprouver les **corrections** pousserait, sans le vouloir, une trace **définitive** sur des données
    /// que lit un validateur du MNHN. Ce verrou-ci ne s'ouvre qu'en le **nommant**.
    private static void supposerEcritureIrreversibleAutorisee() {
        assumeTrue(
                Boolean.getBoolean("vigiechiro.message"),
                "Probe de message ignorée : opt-in -Dvigiechiro.message=true. ATTENTION, cette écriture est"
                        + " DÉFINITIVE (le serveur $push, aucune route ne retire ni ne modifie un message).");
    }

    @Test
    @DisplayName("PROBE #1261 : un compute est accepté, et le SECOND (400 « Already ») est reconnu comme"
            + " « déjà lancé » — non comme un échec")
    void probe_compute_et_refus_deja_lance() {
        supposerEcritureAutorisee();
        // Participation de rebut fournie par l'observateur (vide, non supprimable côté plateforme) : on
        // peut y lancer un calcul sans conséquence. Sans elle, la probe est ignorée — on ne lance JAMAIS un
        // compute sur une participation réelle : il détruirait ses observations pour les recalculer (#1244).
        String participationId = System.getProperty("vigiechiro.participationRebut");
        assumeTrue(
                participationId != null && !participationId.isBlank(),
                "Probe ignorée : fournir -Dvigiechiro.participationRebut=<id d'une participation jetable>.");

        TraitementVigieChiro traitement =
                new TraitementVigieChiro(new ClientVigieChiro(baseUrl, () -> Optional.of(token)));

        ResultatLancement premier = traitement.lancer(participationId);
        assertThat(premier.issue())
                .as("premier compute sur une participation jamais calculée : le serveur accepte")
                .isEqualTo(IssueLancement.ACCEPTE);

        assertThat(traitement.etat(participationId).enOptionnel().orElseThrow().etat())
                .as("le compute a posé un traitement (PLANIFIE, ou déjà EN_COURS si un worker a été prompt)")
                .isIn(EtatTraitement.PLANIFIE, EtatTraitement.EN_COURS);

        // LE cas qui motive #1261 : le serveur refuse (400 « Already ») une demande concurrente dans sa
        // fenêtre de 24 h. Avant, ce refus était rendu comme un échec, avec un point d'interrogation.
        ResultatLancement second = traitement.lancer(participationId);
        assertThat(second.issue())
                .as("refus « Already » qualifié par la relecture de l'état, pas par le message d'erreur")
                .isEqualTo(IssueLancement.DEJA_LANCE);
        assertThat(second.traitementEnRoute())
                .as("le traitement est bel et bien en route : la CLI doit rendre 0, l'IHM ne doit pas crier")
                .isTrue();
    }

    @Test
    @DisplayName("PROBE #984/#1231 : la plateforme ACCEPTE un ZIP (déclaration + PUT S3 application/zip +"
            + " finalisation). C'est le mode de dépôt PAR DÉFAUT : un échec ici veut dire que le dépôt"
            + " par défaut est cassé, pas qu'il faut revenir au WAV")
    void probe_zip_vs_wav() {
        supposerEcritureAutorisee();
        // #1287 : la probe passait « probe » en second argument. Depuis #1239 c'est le lien_participation,
        // et « probe » n'est pas un ObjectId — la déclaration était refusée, la probe échouait, et son
        // libellé (« échec = verdict WAV confirmé ») faisait conclure l'INVERSE de la vérité. Elle vise
        // désormais la participation de rebut, comme les autres probes d'écriture.
        String participation = participationDeRebut();
        ClientVigieChiro client = new ClientVigieChiro(baseUrl, () -> Optional.of(token));

        Optional<FichierSigne> signe = client.creerFichier("Car000000-2026-Pass1-Z0-probe.zip", participation)
                .enOptionnel();
        assertThat(signe)
                .as("déclaration d'un .zip : la plateforme l'accepte comme fichier de participation (un"
                        + " refus ici casserait le dépôt en archives, mode par défaut depuis #984)")
                .isPresent();

        boolean televerse = client.televerserVersS3(signe.get().urlSignee(), zipDEssai(), "application/zip");
        assertThat(televerse).as("PUT S3 du zip (mime signé application/zip)").isTrue();

        boolean finalise = client.finaliserFichier(signe.get().id()).echec().isEmpty();
        assertThat(finalise)
                .as("finalisation du zip : la plateforme accepte un zip comme fichier de participation,"
                        + " l'ingère et le dézippe (vérifié en production depuis #1231 — la nuit canonique"
                        + " a été déposée ainsi, 4806 observations à la clé)")
                .isTrue();
    }

    /// La participation **de rebut** (`-Dvigiechiro.participationEssai=<id>`), jamais une participation
    /// réelle : les probes d'écriture y laissent des fichiers déclarés, et un fichier déclaré ne se retire
    /// pas d'un simple revers de main.
    private static String participationDeRebut() {
        String participation = System.getProperty("vigiechiro.participationEssai");
        assumeTrue(
                participation != null && !participation.isBlank(),
                "Probe ignorée : fournir -Dvigiechiro.participationEssai=<participation banc d'essai>.");
        return participation;
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

    // ---------------------------------------------------------------------------------------------
    // CONTRAT D'ÉCRITURE DES CORRECTIONS (#1203, prérequis de #723) : sondes en lecture (sans risque)
    // puis probes en écriture (opt-in). Verdicts établis par lecture statique du backend
    // (donnees.py, 2026-07-13), que ces sondes confirment en réel : route positionnelle
    // PATCH /donnees/{id}/observations/{index}, probabilité en énumération SUR|PROBABLE|POSSIBLE,
    // taxon en objectid, propriétaire seul, pas d'If-Match exigé.
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("GET /donnees/{id} : la donnée est adressable directement par son _id (ancrage des"
            + " corrections, #1203)")
    void donnee_adressable_par_son_id() {
        String idDonnee = idPremiereDonnee();

        api().when()
                .get("/donnees/{id}", idDonnee)
                .then()
                .statusCode(200)
                .body("_id", org.hamcrest.Matchers.equalTo(idDonnee));
    }

    @Test
    @DisplayName("Dérive client : DonneesVigieChiro lit le _id des donnees réelles (sans lui, aucune"
            + " correction n'est adressable)")
    void parseur_lit_l_id_des_donnees_reelles() {
        String corps = api().when()
                .get("/participations/{id}/donnees?max_results=1", participationTraitee())
                .then()
                .statusCode(200)
                .extract()
                .asString();

        List<DonneeVigieChiro> donnees = DonneesVigieChiro.donnees(corps);

        assertThat(donnees).as("au moins une donnée lue sur la page").isNotEmpty();
        assertThat(donnees.getFirst().id()).as("_id de la donnée parsé").isNotNull();
    }

    @Test
    @DisplayName("OPTIONS /donnees/{id}/observations/0 : la route positionnelle existe, l'index du tableau"
            + " EST l'identifiant de l'observation (Allow contient PATCH ; rappel : Allow reflète le"
            + " schéma, pas le rôle)")
    void route_positionnelle_annoncee() {
        String allow = api().when()
                .options("/donnees/{id}/observations/0", idPremiereDonnee())
                .then()
                .statusCode(200)
                .extract()
                .header("Allow");

        assertThat(allow).as("Allow de la route positionnelle").contains("PATCH");
    }

    @Test
    @DisplayName("PROBE #1203 : PATCH /donnees/{id}/observations/{index} en auto-validation inoffensive"
            + " (observateur_taxon = taxon Tadarida, probabilite SUR) puis relecture. ATTENTION : une"
            + " correction posée ne se retire pas (la route ne fait que du $set) : banc d'essai seulement")
    void probe_patch_correction_observation() {
        supposerEcritureAutorisee();
        CibleCorrection cible = cibleCorrection();

        // Sans en-tête If-Match, délibérément : le handler backend n'en lit pas (contrairement à la
        // convention Eve des participations). Un 428/412 ici serait un verdict à consigner.
        api().contentType("application/json")
                .body(Map.of("observateur_taxon", cible.idTaxon(), "observateur_probabilite", "SUR"))
                .when()
                .patch("/donnees/{id}/observations/0", cible.idDonnee())
                .then()
                .statusCode(200);

        var relue = api().when()
                .get("/donnees/{id}", cible.idDonnee())
                .then()
                .statusCode(200)
                .extract();
        assertThat(relue.<String>path("observations[0].observateur_probabilite"))
                .as("probabilité observateur relue : énumération SUR|PROBABLE|POSSIBLE, pas un flottant")
                .isEqualTo("SUR");
        assertThat(relue.<Object>path("observations[0].observateur_taxon"))
                .as("taxon observateur relu (consigner sa forme exacte : objectid brut ou objet embarqué)")
                .isNotNull();
    }

    @Test
    @DisplayName("PROBE #1203 (verdicts négatifs) : taxon sans probabilité = 422 (champ obligatoire) ;"
            + " PATCH du tableau observations complet = 403 (réservé admin)")
    void probe_verdicts_negatifs_corrections() {
        supposerEcritureAutorisee();
        CibleCorrection cible = cibleCorrection();

        int sansProbabilite = api().contentType("application/json")
                .body(Map.of("observateur_taxon", cible.idTaxon()))
                .when()
                .patch("/donnees/{id}/observations/0", cible.idDonnee())
                .then()
                .extract()
                .statusCode();
        assertThat(sansProbabilite)
                .as("observateur_probabilite est obligatoire dès que observateur_taxon est envoyé")
                .isEqualTo(422);

        // Quasi no-op : renvoyer le tableau observations TEL QUE LU. Attendu : 403 avant toute écriture
        // (le tableau complet est réservé à l'admin, « in fact script »). Même si un backend différent
        // l'acceptait, le corps étant celui que le serveur vient de renvoyer, le contenu resterait le sien.
        List<Map<String, Object>> observations = api().when()
                .get("/donnees/{id}", cible.idDonnee())
                .then()
                .statusCode(200)
                .extract()
                .path("observations");
        int tableauComplet = api().contentType("application/json")
                .body(Map.of("observations", observations))
                .when()
                .patch("/donnees/{id}", cible.idDonnee())
                .then()
                .extract()
                .statusCode();
        assertThat(tableauComplet)
                .as("PATCH /donnees/{id} avec observations : refusé à l'observateur (le contrat de #723"
                        + " passe par la route positionnelle, pas par le tableau complet)")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("PROBE #1418/#1456 : PUT /donnees/{id}/observations/{index}/messages — le message part (200)"
            + " et se relit dans le fil. ATTENTION : cette écriture est DÉFINITIVE, le serveur $push et"
            + " aucune route ne retire ni ne modifie un message. Triple opt-in, banc d'essai seulement")
    void probe_put_message_observation() {
        supposerEcritureAutorisee();
        supposerEcritureIrreversibleAutorisee();
        CibleCorrection cible = cibleCorrection();

        // Le texte se DÉSIGNE lui-même comme une sonde : ce qu'on écrit ici reste, et un validateur du
        // MNHN peut le lire un jour. Autant qu'il sache tout de suite ce que c'est.
        String texte = "Sonde de contrat automatisée (#1456) : vérification du contrat d'écriture des"
                + " messages. Ce message n'appelle pas de réponse.";

        api().contentType("application/json")
                .body(Map.of("message", texte))
                .when()
                .put("/donnees/{id}/observations/0/messages", cible.idDonnee())
                .then()
                .statusCode(200);

        // Le contrat ne vaut que relu : le serveur a-t-il vraiment gardé ce qu'on lui a donné ?
        List<Map<String, Object>> fil = api().when()
                .get("/donnees/{id}", cible.idDonnee())
                .then()
                .statusCode(200)
                .extract()
                .path("observations[0].messages");

        assertThat(fil)
                .as("fil de discussion relu après le PUT (le serveur ajoute par $push : le fil ne rétrécit"
                        + " jamais)")
                .isNotNull()
                .isNotEmpty();
        assertThat(fil.stream().map(message -> String.valueOf(message.get("message"))))
                .as("le texte envoyé se relit tel quel dans le fil")
                .contains(texte);
        // Ni auteur ni date ne sont inventés côté client : c'est le serveur qui les pose. Consigner leur
        // présence, c'est consigner que notre modèle a raison de les attendre de lui.
        assertThat(fil.getLast())
                .as("le serveur horodate et signe lui-même le message (le client n'envoie que le texte)")
                .containsKeys("auteur", "date");
    }

    @Test
    @DisplayName("PROBE #1418/#1456 (verdict négatif) : un corps de message non-chaîne est refusé (422) —"
            + " le serveur n'écrit rien, la sonde ne laisse donc aucune trace")
    void probe_message_corps_invalide() {
        supposerEcritureAutorisee();
        supposerEcritureIrreversibleAutorisee();
        CibleCorrection cible = cibleCorrection();

        int refus = api().contentType("application/json")
                .body(Map.of("message", Map.of("texte", "un objet, pas une chaîne")))
                .when()
                .put("/donnees/{id}/observations/0/messages", cible.idDonnee())
                .then()
                .extract()
                .statusCode();

        assertThat(refus)
                .as("le serveur n'accepte qu'une chaîne : c'est ce refus qui garantit qu'on ne peut pas"
                        + " glisser une structure dans un fil de discussion")
                .isEqualTo(422);
    }

    /// Une observation corrigeable du banc d'essai : les probes d'écriture exigent une participation
    /// **explicitement désignée** (`-Dvigiechiro.participationEssai=<id>`), jamais une participation
    /// réelle prise au hasard : une correction posée est définitive.
    private record CibleCorrection(String idDonnee, String idTaxon) {}

    private static CibleCorrection cibleCorrection() {
        String participation = participationDeRebut();
        CibleCorrection existante = chercherCible(participation);
        return existante != null ? existante : creerCible(participation);
    }

    /// Première donnée avec observation du banc d'essai, ou `null` s'il n'en a pas (encore).
    private static CibleCorrection chercherCible(String participation) {
        var reponse = api().when()
                .get("/participations/{id}/donnees?max_results=100", participation)
                .then()
                .statusCode(200)
                .extract();
        String filtre = "_items.find { it.observations }";
        String idDonnee = reponse.path(filtre + "._id");
        return idDonnee == null
                ? null
                : new CibleCorrection(idDonnee, reponse.path(filtre + ".observations[0].tadarida_taxon._id"));
    }

    /// Banc d'essai vide : la cible est **créée** avec une observation Tadarida factice sur un taxon réel
    /// du référentiel. Vérifie au passage un contrat de plus : `POST /participations/{id}/donnees` est
    /// ouvert au **propriétaire** (`create_donnee`), pas seulement au pipeline serveur.
    private static CibleCorrection creerCible(String participation) {
        String idTaxon = api().when()
                .get("/taxons?max_results=1")
                .then()
                .statusCode(200)
                .extract()
                .path("_items[0]._id");
        assumeTrue(idTaxon != null, "Référentiel taxons illisible : impossible de fabriquer la cible.");

        String idDonnee = api().contentType("application/json")
                .body(Map.of(
                        "commentaire",
                        "banc d'essai du contrat d'écriture des corrections (#1203)",
                        "observations",
                        List.of(Map.of(
                                "temps_debut", 0.0,
                                "temps_fin", 5.0,
                                "frequence_mediane", 44.0,
                                "tadarida_taxon", idTaxon,
                                "tadarida_probabilite", 0.5))))
                .when()
                .post("/participations/{id}/donnees", participation)
                .then()
                .statusCode(201)
                .extract()
                .path("_id");
        return new CibleCorrection(idDonnee, idTaxon);
    }

    /// Participation de l'observateur ayant déjà des résultats Tadarida : le banc d'essai d'abord s'il
    /// est fourni (et non vide), sinon la première trouvée en balayant la première page. Les `donnees`
    /// n'existent qu'après traitement serveur : skip (assume) si rien n'a encore été calculé.
    private static String participationTraitee() {
        String essai = System.getProperty("vigiechiro.participationEssai");
        List<String> candidates = new ArrayList<>();
        if (essai != null && !essai.isBlank()) {
            candidates.add(essai);
        }
        candidates.addAll(api().when()
                .get("/moi/participations")
                .then()
                .statusCode(200)
                .extract()
                .path("_items._id"));
        for (String participation : candidates) {
            String idDonnee = api().when()
                    .get("/participations/{id}/donnees?max_results=1", participation)
                    .then()
                    .statusCode(200)
                    .extract()
                    .path("_items[0]._id");
            if (idDonnee != null) {
                return participation;
            }
        }
        assumeTrue(false, "Aucune participation traitée (aucune donnée) : relancer après un premier compute.");
        throw new IllegalStateException("inatteignable : assumeTrue vient d'interrompre le test");
    }

    /// `_id` de la première donnée de [#participationTraitee].
    private static String idPremiereDonnee() {
        return api().when()
                .get("/participations/{id}/donnees?max_results=1", participationTraitee())
                .then()
                .statusCode(200)
                .extract()
                .path("_items[0]._id");
    }
}
