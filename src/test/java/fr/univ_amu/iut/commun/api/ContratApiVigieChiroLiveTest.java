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
    @DisplayName("PROBE #984 (pilier B) : un ZIP déclaré + téléversé (application/zip) + finalisé est-il"
            + " accepté ? (échec = verdict WAV confirmé)")
    void probe_zip_vs_wav() {
        supposerEcritureAutorisee();
        ClientVigieChiro client = new ClientVigieChiro(baseUrl, () -> Optional.of(token));

        Optional<FichierSigne> signe = client.creerFichier("Car000000-2026-Pass1-Z0-probe.zip", "probe")
                .enOptionnel();
        assertThat(signe)
                .as("déclaration d'un .zip : refusée = verdict immédiat (les .zip ne sont pas des titres"
                        + " valides), le dépôt reste en WAV")
                .isPresent();

        boolean televerse = client.televerserVersS3(signe.get().urlSignee(), zipDEssai(), "application/zip");
        assertThat(televerse).as("PUT S3 du zip (mime signé application/zip)").isTrue();

        boolean finalise = client.finaliserFichier(signe.get().id()).echec().isEmpty();
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

    /// Une observation corrigeable du banc d'essai : les probes d'écriture exigent une participation
    /// **explicitement désignée** (`-Dvigiechiro.participationEssai=<id>`), jamais une participation
    /// réelle prise au hasard : une correction posée est définitive.
    private record CibleCorrection(String idDonnee, String idTaxon) {}

    private static CibleCorrection cibleCorrection() {
        String participation = System.getProperty("vigiechiro.participationEssai");
        assumeTrue(
                participation != null && !participation.isBlank(),
                "Probe corrections ignorée : fournir -Dvigiechiro.participationEssai=<participation banc"
                        + " d'essai>.");
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
