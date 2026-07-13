package fr.univ_amu.iut.commun.api;

import fr.univ_amu.iut.commun.model.CertitudeObservateur;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleConsumer;

/// Client HTTP de l'**API REST VigieChiro** (backend Eve, base `…/api/v1`, cf. #142). Socle réseau
/// destiné à être réutilisé par les features (identité, sites, taxons, participations, fichiers).
///
/// Le client **nomme les points d'accès** de l'API et délègue à deux collaborateurs : la mécanique
/// HTTP et le tri des issues à [TransportVigieChiro] (#1284), la lecture des réponses JSON à
/// [ReponsesVigieChiro] et consorts (fonctions pures, testables sans réseau).
///
/// **Dégradation propre** (historique, en cours de résorption par #1284) : les lectures convertissent
/// encore toute issue non-succès en [Optional#empty()] / liste vide via [ReponseApi#enOptionnel()] ;
/// aucune exception ne remonte à l'IHM. Les endpoints basculent un à un vers [ReponseApi] pour que
/// leurs appelants distinguent enfin non connecté / injoignable / refusé.
public final class ClientVigieChiro {

    private static final String URL_DEFAUT = "https://vigiechiro.herokuapp.com/api/v1";

    /// Préfixe de chemin de l'API des participations (`GET .../donnees`, `GET .../#id`, `PATCH .../#id`).
    private static final String CHEMIN_PARTICIPATIONS = "/participations/";
    /// Chemin des participations de l'observateur courant (source des sites et des participations).
    private static final String CHEMIN_MOI_PARTICIPATIONS = "/moi/participations";
    /// Garde-fou de pagination (`GET …/donnees`) : une participation a des milliers de fichiers, jamais
    /// des centaines de milliers ; on plafonne le nombre de pages pour éviter toute boucle. À
    /// [PaginationEve#TAILLE_PAGE] éléments par page, 500 pages couvrent 50 000 éléments (une nuit en
    /// compte ~5 000).
    private static final int PAGES_MAX = 500;

    private final TransportVigieChiro transport;

    public ClientVigieChiro(FournisseurToken fournisseurToken) {
        this(URL_DEFAUT, fournisseurToken);
    }

    /// Constructeur d'injection de l'URL de base (tests hors-ligne : une URL injoignable donne `empty`).
    ClientVigieChiro(String baseUrl, FournisseurToken fournisseurToken) {
        this.transport = new TransportVigieChiro(baseUrl, fournisseurToken);
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
    /// `site` ; on les déduplique par `_id`, **toutes pages confondues** (#1150) : la réponse Eve est
    /// paginée, et un observateur peut dépasser une page de participations.
    public List<SiteVigieChiro> mesSites() {
        Map<String, SiteVigieChiro> parId = new LinkedHashMap<>();
        for (SiteVigieChiro site :
                PaginationEve.parcourir(PAGES_MAX, this::pageParticipations, ParticipationsVigieChiro::sites)) {
            parId.putIfAbsent(site.id(), site);
        }
        return List.copyOf(parId.values());
    }

    /// **Participations** de l'observateur (`GET /moi/participations`, axe 4.2) : id + localité + date +
    /// site, pour rattacher à la main un passage à une participation existante (import de résultats sans
    /// dépôt-app préalable). Liste vide si non connecté / indisponible. **Toutes pages** (#1150).
    public List<ParticipationVigieChiro> mesParticipations() {
        return PaginationEve.parcourir(PAGES_MAX, this::pageParticipations, ParticipationsVigieChiro::participations);
    }

    /// Corps JSON de la page `page` de `GET /moi/participations` : source commune des sites et des
    /// participations, parcourue **toutes pages confondues** via [PaginationEve] (#1150).
    private Optional<String> pageParticipations(int page) {
        return get(CHEMIN_MOI_PARTICIPATIONS + PaginationEve.requete(page));
    }

    /// Participation **détaillée** (`GET /participations/#id`, axe 4) : `_etag` (pour un `PATCH` `If-Match`
    /// concurrent-sûr), dates, météo, configuration matérielle et état du traitement Tadarida. Vide si non
    /// connecté, indisponible, ou participation inconnue.
    public Optional<ParticipationDetail> participation(String id) {
        return get(CHEMIN_PARTICIPATIONS + id).flatMap(ParticipationsVigieChiro::detail);
    }

    /// Résultats Tadarida d'une participation (`GET /participations/#id/donnees`, #719, axe 4.2) : les
    /// fichiers et leurs observations, **toutes pages confondues**. Liste vide si non connecté /
    /// indisponible. La réponse Eve est paginée ; on parcourt les pages (grande taille demandée) jusqu'à
    /// une page vide.
    public List<DonneeVigieChiro> donnees(String participationId) {
        return PaginationEve.parcourir(
                PAGES_MAX,
                page -> get(CHEMIN_PARTICIPATIONS + participationId + "/donnees" + PaginationEve.requete(page)),
                DonneesVigieChiro::donnees);
    }

    /// **Journal de traitement** d'une participation (#1132) : le serveur y trace l'ingestion —
    /// archives extraites avec inventaire (`Archive contained: {'audio/wav': N}`), chaque fichier
    /// passé à Tadarida. Chaîne : `GET /participations/#id` → document `logs` → `GET
    /// /fichiers/#id/acces` → URL S3 signée, téléchargée **sans** en-tête d'authentification. Vide si
    /// non connecté, participation inconnue, ou journal pas encore disponible (traitement pas lancé).
    public Optional<String> journalTraitement(String participationId) {
        return get(CHEMIN_PARTICIPATIONS + participationId)
                .flatMap(JournalVigieChiro::idJournal)
                .flatMap(idFichier -> get("/fichiers/" + idFichier + "/acces"))
                .flatMap(JournalVigieChiro::urlSignee)
                .flatMap(url -> transport.telecharger(url).enOptionnel());
    }

    // ---------------------------------------------------------------------------------------------
    // Écritures (dépôt d'une nuit, #142) : création de participation + upload de fichiers vers S3
    // ---------------------------------------------------------------------------------------------

    /// Crée une **participation** sur un site (`POST /sites/#id/participations`, #142) : renvoie l'`_id` créé,
    /// ou un [ResultatParticipation] portant le **détail de l'échec** (statut HTTP + corps de la réponse
    /// VigieChiro) pour un message exploitable — le dépôt étant une écriture, un refus doit être **expliqué**,
    /// pas silencieusement vide. Prérequis métier : site **verrouillé** côté VigieChiro.
    public ResultatParticipation creerParticipation(String siteId, ParticipationADeposer participation) {
        ReponseApi<String> reponse =
                poster("/sites/" + siteId + "/participations", RequetesVigieChiro.participation(participation));
        String echec = echecDe(reponse);
        if (echec != null) {
            return ResultatParticipation.echouee(echec);
        }
        String corps = reponse.enOptionnel().orElseThrow();
        return ReponsesVigieChiro.idCree(corps)
                .map(ResultatParticipation::reussie)
                .orElseGet(() -> ResultatParticipation.echouee("réponse acceptée mais sans identifiant : " + corps));
    }

    /// **Met à jour** une participation existante (`PATCH /participations/#id`, axe 4) : n'émet que les
    /// métadonnées synchronisables (dates, météo, configuration ; cf. [RequetesVigieChiro#miseAJourParticipation]),
    /// avec l'en-tête `If-Match: <etag>` exigé par Eve (concurrence optimiste). L'`etag` frais se lit via
    /// [#participation]. Renvoie l'`_id` en cas de succès, ou le **détail de l'échec** (statut + corps) — un
    /// refus doit être expliqué. Prérequis : `etag` courant (sinon `412 Precondition Failed`).
    public ResultatParticipation modifierParticipation(String id, String etag, ParticipationADeposer miseAJour) {
        String echec = echecDe(transport.ecrire(
                "PATCH", CHEMIN_PARTICIPATIONS + id, RequetesVigieChiro.miseAJourParticipation(miseAJour), etag));
        return echec == null ? ResultatParticipation.reussie(id) : ResultatParticipation.echouee(echec);
    }

    /// **Publie une correction d'observation** (`PATCH /donnees/#id/observations/#indice`, #723,
    /// contrat #1203) : pose le taxon observateur (**objectid**) et la certitude sur le sous-document
    /// **positionnel** `indice` de la donnée (une observation serveur n'a pas d'`_id` propre). Pas
    /// d'`If-Match` : le handler serveur n'en lit pas. `bilan` à `false` ajoute `?no_bilan=true` (le
    /// serveur ne régénère pas le bilan de la participation : levier de rafale, ne mettre `true` que
    /// sur le **dernier** envoi d'un lot). Un `HTTP 404` signale un **ancrage périmé** (la donnée a
    /// été régénérée par un re-compute) ; tout refus revient détaillé (statut + corps).
    public ResultatCorrection corrigerObservation(
            String donneeId, int indice, String objectidTaxon, CertitudeObservateur certitude, boolean bilan) {
        String chemin = "/donnees/" + donneeId + "/observations/" + indice + (bilan ? "" : "?no_bilan=true");
        String echec = echecDe(
                transport.ecrire("PATCH", chemin, RequetesVigieChiro.correction(objectidTaxon, certitude), null));
        return echec == null ? ResultatCorrection.reussie() : ResultatCorrection.echouee(echec);
    }

    /// Déclare un **fichier** à téléverser (`POST /fichiers`, étape 1/3) : renvoie son `_id` et l'URL S3
    /// pré-signée ([FichierSigne]), ou vide. Le mime n'est pas transmis (déduit de l'extension du titre) ;
    /// le titre doit respecter la convention de nommage VigieChiro (`Car…-Pass…`).
    public Optional<FichierSigne> creerFichier(String titre, String participationId) {
        return post("/fichiers", RequetesVigieChiro.fichier(titre, participationId))
                .flatMap(ReponsesVigieChiro::fichierSigne);
    }

    /// **PUT** des octets vers l'**URL S3 pré-signée** (étape 2/3) : hors API VigieChiro (aucun en-tête
    /// d'auth, l'URL est déjà signée). Le `Content-Type` doit être le **mime attendu par la signature**
    /// (sinon S3 répond `SignatureDoesNotMatch`). `true` si 2xx, `false` sinon (dégradation propre).
    public boolean televerserVersS3(String urlSignee, byte[] octets, String mime) {
        return transport.deposerVersS3(urlSignee, () -> HttpRequest.BodyPublishers.ofByteArray(octets), mime);
    }

    /// Variante **en flux** de [#televerserVersS3(String, byte[], String)] (#982) : le corps du `PUT` est
    /// **streamé depuis le disque** (`BodyPublishers.ofFile`) au lieu d'être chargé en mémoire — une
    /// archive ZIP de dépôt peut peser ~700 Mo. Mêmes garanties : `true` si 2xx, `false` sinon (fichier
    /// illisible compris).
    public boolean televerserVersS3(String urlSignee, Path fichier, String mime) {
        return televerserVersS3(urlSignee, fichier, mime, fraction -> {});
    }

    /// Comme [#televerserVersS3(String, Path, String)], en **remontant l'avancement** octet par octet
    /// (#984) à `progression` (fraction 0 à 1) pour alimenter une barre de progression par archive.
    public boolean televerserVersS3(String urlSignee, Path fichier, String mime, DoubleConsumer progression) {
        return transport.deposerVersS3(urlSignee, () -> CorpsFichierAvecProgression.depuis(fichier, progression), mime);
    }

    /// Finalise un fichier téléversé (`POST /fichiers/#id`, étape 3/3) : `true` si accepté, `false` sinon.
    public boolean finaliserFichier(String fichierId) {
        return post("/fichiers/" + fichierId, RequetesVigieChiro.finalisation()).isPresent();
    }

    // Le traitement serveur (lancer le compute, lire son etat) vit dans TraitementVigieChiro :
    // le client transporte, il ne decide pas de ce qu'un refus veut dire (#1261).
    /// **POST authentifié** d'un corps JSON sur `chemin` : renvoie le corps de la réponse si 2xx, vide
    /// sinon (pas de token, refus, autre statut, réseau indisponible). Pendant en écriture de [#get].
    Optional<String> post(String chemin, String corpsJson) {
        return poster(chemin, corpsJson).enOptionnel();
    }

    /// Variante **triée** de [#post] : renvoie l'issue complète ([ReponseApi]), y compris le statut et
    /// le corps d'un refus, pour construire un message d'erreur exploitable (ex. la création de
    /// participation, le lancement d'un traitement #1261).
    ReponseApi<String> poster(String chemin, String corpsJson) {
        return transport.ecrire("POST", chemin, corpsJson, null);
    }

    /// Triage **commun des écritures** : la cause d'échec exploitable, ou `null` si la réponse est un
    /// succès 2xx. Une écriture refusée doit être expliquée à l'utilisateur, jamais réduite à un booléen
    /// opaque — et depuis #1284, « non connecté » ne se déguise plus en panne réseau.
    private static String echecDe(ReponseApi<String> reponse) {
        return switch (reponse) {
            case ReponseApi.Succes<String> succes -> null;
            case ReponseApi.NonConnecte<String> nonConnecte -> "non connecté à VigieChiro (aucun jeton).";
            case ReponseApi.Injoignable<String>(String cause) -> "VigieChiro injoignable : " + cause + ".";
            case ReponseApi.Refuse<String>(int statut, String corps) -> "HTTP " + statut + " : " + corps;
        };
    }

    /// **GET authentifié** sur `chemin` (relatif à la base) : renvoie le corps de la réponse si 2xx,
    /// vide dans tous les autres cas (pas de token, refus, réseau indisponible). Adaptateur historique
    /// de [TransportVigieChiro#lire] : les endpoints qui l'utilisent encore basculent un à un vers
    /// [ReponseApi] (#1284).
    Optional<String> get(String chemin) {
        return transport.lire(chemin).enOptionnel();
    }
}
