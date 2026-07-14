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
/// Depuis #1284, **tous les endpoints parlent [ReponseApi]** : non connecté / injoignable /
/// refusé(statut, corps) / succès, sans jamais lever vers l'IHM. La « dégradation propre »
/// historique (tout échec devenait un même vide) a disparu ; là où le silence reste voulu, c'est
/// l'appelant qui le choisit explicitement via [ReponseApi#enOptionnel()].
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

    /// Rayon de recherche d'un carré STOC (#733), en **mètres**. Un carré fait 2 km de côté : 10 km laissent
    /// de la marge pour répondre « le carré voisin » plutôt que « aucun » quand la position est en limite.
    private static final int RAYON_CARRE_STOC_METRES = 10_000;

    private final TransportVigieChiro transport;

    public ClientVigieChiro(FournisseurToken fournisseurToken) {
        this(URL_DEFAUT, fournisseurToken);
    }

    /// Constructeur d'injection de l'URL de base (tests hors-ligne : une URL injoignable donne `empty`).
    ClientVigieChiro(String baseUrl, FournisseurToken fournisseurToken) {
        this.transport = new TransportVigieChiro(baseUrl, fournisseurToken);
    }

    /// Profil de l'utilisateur connecté (`GET /moi`), **trié** (#1284) : un jeton refusé (`401`)
    /// revient en `Refuse`, une panne en `Injoignable` — la modale de connexion peut enfin cesser
    /// d'annoncer « jeton invalide » quand c'est le réseau qui est coupé.
    public ReponseApi<ProfilVigieChiro> moi() {
        return transport.lire("/moi").lireAvec(ReponsesVigieChiro::profil);
    }

    /// Référentiel officiel des taxons (`GET /taxons/liste`, résumé non paginé : `_id` + libellés),
    /// **trié** (#1284) : les rapprocheurs peuvent enfin dire pourquoi rien n'a été synchronisé.
    public ReponseApi<List<TaxonVigieChiro>> taxons() {
        return transport.lire("/taxons/liste").transformer(ReponsesVigieChiro::taxons);
    }

    /// **Carré STOC officiel** d'une position (`GET /grille_stoc/cercle`, #733) : le carré de la grille
    /// nationale dont le centre est le plus proche du point donné, ou vide s'il n'y en a aucun dans le
    /// rayon (en mer, hors de France).
    ///
    /// Contrat relevé dans le code du backend (`vigiechiro/resources/grille_stoc.py`) : trois paramètres
    /// **obligatoires** `lng`, `lat`, `r` (flottants), une requête MongoDB `$near` — donc des résultats
    /// **triés par distance croissante** — plafonnés à 80 éléments. Le rôle `Observateur` suffit.
    ///
    /// Le rayon est en **mètres** (`$maxDistance` d'un `$near` GeoJSON). [#RAYON_CARRE_STOC_METRES] vaut
    /// 10 km : les carrés STOC font 2 km de côté, un point tombe donc toujours à moins de ~1,5 km du
    /// centre du sien ; le rayon large sert à répondre quand même (le carré voisin) plutôt que rien.
    ///
    /// Issue **triée** (#1284) : hors connexion, panne et refus restent distincts d'une position sans
    /// carré — c'est ce qui permet à l'appelant de se taire au lieu d'accuser à tort.
    public ReponseApi<Optional<String>> carreStoc(double latitude, double longitude) {
        String requete = "/grille_stoc/cercle?lng=" + longitude + "&lat=" + latitude + "&r=" + RAYON_CARRE_STOC_METRES;
        return transport.lire(requete).transformer(ReponsesVigieChiro::numeroCarreStoc);
    }

    /// Sites rattachés à l'observateur, **dérivés de ses participations** (`GET /moi/participations`),
    /// **triés** (#1284) et **tout-ou-rien** (jamais un préfixe de pages).
    ///
    /// On ne passe **pas** par `/moi/sites` : celui-ci filtre sur le *propriétaire* du site et renvoie
    /// vide pour un simple participant à un site régional (cf. #718). Chaque participation embarque son
    /// `site` ; on les déduplique par `_id`, **toutes pages confondues** (#1150) : la réponse Eve est
    /// paginée, et un observateur peut dépasser une page de participations.
    public ReponseApi<List<SiteVigieChiro>> mesSites() {
        return PaginationEve.parcourir(PAGES_MAX, this::pageParticipations, ParticipationsVigieChiro::sites)
                .transformer(ClientVigieChiro::dedupliquerParId);
    }

    /// Déduplication inter-pages des sites (un même site revient sur chaque participation qui le
    /// porte) : premier vu, premier gardé.
    private static List<SiteVigieChiro> dedupliquerParId(List<SiteVigieChiro> sites) {
        Map<String, SiteVigieChiro> parId = new LinkedHashMap<>();
        for (SiteVigieChiro site : sites) {
            parId.putIfAbsent(site.id(), site);
        }
        return List.copyOf(parId.values());
    }

    /// **Participations** de l'observateur (`GET /moi/participations`, axe 4.2) : id + localité + date +
    /// site, pour rattacher à la main un passage à une participation existante (import de résultats sans
    /// dépôt-app préalable). **Toutes pages** (#1150), issue **triée** (#1284).
    public ReponseApi<List<ParticipationVigieChiro>> mesParticipations() {
        return PaginationEve.parcourir(PAGES_MAX, this::pageParticipations, ParticipationsVigieChiro::participations);
    }

    /// Corps JSON **trié** de la page `page` de `GET /moi/participations` : source commune des sites et
    /// des participations, parcourue **toutes pages confondues** via [PaginationEve] (#1150).
    private ReponseApi<String> pageParticipations(int page) {
        return transport.lire(CHEMIN_MOI_PARTICIPATIONS + PaginationEve.requete(page));
    }

    /// Participation **détaillée** (`GET /participations/#id`, axe 4) : `_etag` (pour un `PATCH` `If-Match`
    /// concurrent-sûr), dates, météo, configuration matérielle et état du traitement Tadarida. Issue
    /// **triée** (#1284) : une participation inconnue revient en `Refuse(404)`, une panne en
    /// `Injoignable`, l'absence de jeton en `NonConnecte` — plus jamais un même « vide ».
    public ReponseApi<ParticipationDetail> participation(String id) {
        return transport.lire(CHEMIN_PARTICIPATIONS + id).lireAvec(ParticipationsVigieChiro::detail);
    }

    /// Résultats Tadarida d'une participation (`GET /participations/#id/donnees`, #719, axe 4.2) : les
    /// fichiers et leurs observations, **toutes pages confondues** (la réponse Eve est paginée ; on
    /// parcourt jusqu'à une page vide). Issue **triée** (#1284) et pagination **tout-ou-rien** : une
    /// panne en cours de parcours rend l'issue, jamais un préfixe silencieux. Un `Succes` à liste vide
    /// signifie réellement « le serveur n'a pas (encore) de résultats » (#1264).
    public ReponseApi<List<DonneeVigieChiro>> donnees(String participationId) {
        return PaginationEve.parcourir(
                PAGES_MAX,
                page -> transport.lire(
                        CHEMIN_PARTICIPATIONS + participationId + "/donnees" + PaginationEve.requete(page)),
                DonneesVigieChiro::donnees);
    }

    /// **Journal de traitement** d'une participation (#1132) : le serveur y trace l'ingestion —
    /// archives extraites avec inventaire (`Archive contained: {'audio/wav': N}`), chaque fichier
    /// passé à Tadarida. Chaîne : `GET /participations/#id` → document `logs` → `GET
    /// /fichiers/#id/acces` → URL S3 signée, téléchargée **sans** en-tête d'authentification.
    ///
    /// Issue **triée** (#1284) : `Succes(Optional.empty())` signifie « le serveur répond, et le
    /// journal n'existe pas (encore) » (traitement jamais lancé) — à ne plus confondre avec une panne
    /// ou un refus, qui gardent leur cause (la vérification d'un dépôt hors ligne n'est plus un faux
    /// « tout manquant »).
    public ReponseApi<Optional<String>> journalTraitement(String participationId) {
        return transport
                .lire(CHEMIN_PARTICIPATIONS + participationId)
                .puis(corps -> JournalVigieChiro.idJournal(corps)
                        .map(idFichier -> transport
                                .lire("/fichiers/" + idFichier + "/acces")
                                .lireAvec(JournalVigieChiro::urlSignee)
                                .puis(transport::telecharger)
                                .transformer(Optional::of))
                        .orElseGet(() -> ReponseApi.succes(Optional.empty())));
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

    /// Poste un **message** dans le fil de discussion d'une observation (#1418, axe 4.4) :
    /// `PUT /donnees/{donneeId}/observations/{indice}/messages`, corps `{"message": "…"}`.
    ///
    /// Même **ancrage positionnel** que la correction (#1203 / #1139) : la donnée par son `_id`, puis
    /// l'indice **brut** de l'observation dans son tableau. Le serveur laisse passer le propriétaire de la
    /// donnée, donc un jeton d'`Observateur` suffit — contrairement à l'avis de validateur, qu'il refuse
    /// (403) et que l'application ne peut donc que lire (#1417).
    ///
    /// ⚠️ **Écriture définitive.** Le serveur ajoute par `$push`, et **aucune route ne permet de supprimer
    /// ni de modifier un message**. Ce qui part ne se retire pas — et part sur des données partagées avec
    /// un validateur du MNHN. L'appelant **doit** l'avoir fait confirmer explicitement.
    ///
    /// Ni `If-Match`, ni `_etag` : le serveur n'offre aucun contrôle de concurrence sur cette route. Deux
    /// messages postés en même temps s'empilent, ils ne s'écrasent pas — c'est le seul point rassurant de
    /// cette absence.
    ///
    /// @return l'issue **triée** (#1284) : `Succes` (le fil contient le message), `NonConnecte`,
    ///     `Injoignable` ou `Refuse` (statut + corps). Un `404` signale un **ancrage périmé** : la donnée a
    ///     été régénérée par un re-compute côté serveur, et il faut réimporter avant de réessayer
    public ReponseApi<String> posterMessage(String donneeId, int indice, String texte) {
        String chemin = "/donnees/" + donneeId + "/observations/" + indice + "/messages";
        return transport.ecrire("PUT", chemin, RequetesVigieChiro.message(texte), null);
    }

    /// Déclare un **fichier** à téléverser (`POST /fichiers`, étape 1/3) : renvoie son `_id` et l'URL S3
    /// pré-signée ([FichierSigne]), issue **triée** (#1284) pour un message de dépôt exploitable. Le
    /// mime n'est pas transmis (déduit de l'extension du titre) ; le titre doit respecter la
    /// convention de nommage VigieChiro (`Car…-Pass…`).
    public ReponseApi<FichierSigne> creerFichier(String titre, String participationId) {
        return poster("/fichiers", RequetesVigieChiro.fichier(titre, participationId))
                .lireAvec(ReponsesVigieChiro::fichierSigne);
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

    /// Finalise un fichier téléversé (`POST /fichiers/#id`, étape 3/3), issue **triée** (#1284) : un
    /// refus revient avec son statut et son corps, une panne avec sa cause.
    public ReponseApi<String> finaliserFichier(String fichierId) {
        return poster("/fichiers/" + fichierId, RequetesVigieChiro.finalisation());
    }

    // Le traitement serveur (lancer le compute, lire son etat) vit dans TraitementVigieChiro :
    // le client transporte, il ne decide pas de ce qu'un refus veut dire (#1261).
    /// **POST authentifié** d'un corps JSON sur `chemin`, issue **triée** ([ReponseApi]) : statut et
    /// corps d'un refus conservés, pour un message d'erreur exploitable (création de participation,
    /// lancement d'un traitement #1261).
    ReponseApi<String> poster(String chemin, String corpsJson) {
        return transport.ecrire("POST", chemin, corpsJson, null);
    }

    /// Triage **commun des écritures** : la cause d'échec exploitable ([ReponseApi#echec()], le
    /// vocabulaire unique des messages), ou `null` si la réponse est un succès 2xx. Une écriture
    /// refusée doit être expliquée à l'utilisateur, jamais réduite à un booléen opaque.
    private static String echecDe(ReponseApi<String> reponse) {
        return reponse.echec().orElse(null);
    }
}
