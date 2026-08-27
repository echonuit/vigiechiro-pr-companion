package fr.univ_amu.iut.commun.api;

import com.google.gson.JsonArray;
import fr.univ_amu.iut.commun.model.Certitude;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    /// Préfixe de chemin d'un site (`GET .../#id`, `PUT .../#id/localites`).
    private static final String CHEMIN_SITES = "/sites/";
    /// Garde-fou de pagination (`GET …/donnees`) : une participation a des milliers de fichiers, jamais
    /// des centaines de milliers ; on plafonne le nombre de pages pour éviter toute boucle. À
    /// [PaginationEve#TAILLE_PAGE] éléments par page, 500 pages couvrent 50 000 éléments (une nuit en
    /// compte ~5 000).
    private static final int PAGES_MAX = 500;

    /// Combien de sites au plus une recherche de carré rapporte (#3458).
    ///
    /// Un même carré porte un site **par protocole** : quelques-uns au grand maximum, jamais une page.
    /// Ce backend refuse `max_results` au delà de 100 (422), et rend alors des listes vides **en
    /// silence** : la borne est basse à dessein, et ne doit pas être élargie sans mesure.
    private static final int RESULTATS_RECHERCHE_CARRE = 20;

    /// Rayon de recherche d'un carré STOC (#733), en **mètres**. Un carré fait 2 km de côté : 10 km laissent
    /// de la marge pour répondre « le carré voisin » plutôt que « aucun » quand la position est en limite.
    private static final int RAYON_CARRE_STOC_METRES = 10_000;

    /// Racine des ressources « fichier » de l'API (déclaration, finalisation, multipart, abandon).
    private static final String CHEMIN_FICHIERS = "/fichiers";

    /// Méthode HTTP des écritures qui **remplacent** : localités d'un site, message, partie
    /// multipart. Nommée ici plutôt que répétée : un « PUT » qui traîne en trois exemplaires se
    /// transforme un jour en « PATCH » à deux endroits sur trois.
    private static final String PUT = "PUT";

    private final FournisseurToken fournisseurToken;

    private final TransportVigieChiro transport;

    public ClientVigieChiro(FournisseurToken fournisseurToken) {
        this(URL_DEFAUT, fournisseurToken);
    }

    /// Constructeur d'**injection de l'URL de base** : les tests hors-ligne pointent une URL injoignable
    /// (les réponses deviennent `empty`), et l'exécution peut viser un serveur de recette ou le **stub**
    /// des E2E CLI via la surcharge `vigiechiro.url` / `VIGIECHIRO_URL` (cf. `ConnexionModule#urlDeBase`).
    public ClientVigieChiro(String baseUrl, FournisseurToken fournisseurToken) {
        this.fournisseurToken = Objects.requireNonNull(fournisseurToken, "fournisseurToken");
        this.transport = new TransportVigieChiro(baseUrl, fournisseurToken);
    }

    /// Constructeur d'**injection du transport** (#2354), réservé aux tests : un transport à `HttpClient`
    /// mocké simule le dialogue multipart (URL de partie, `PUT` S3, finalisation) sans réseau réel.
    ClientVigieChiro(TransportVigieChiro transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
        // Ce client-ci n'a pas de source de jeton : le transport en porte une, mais elle ne remonte pas
        // jusqu'ici. `estConnecte()` répond donc « non », ce qui est la réponse prudente pour une
        // affordance - on ferme un geste plutôt que de l'offrir sans savoir.
        this.fournisseurToken = Optional::empty;
    }

    /// Un jeton est-il disponible ? **Sans appel réseau** : la question se pose pour décider ce qu'un
    /// écran offre, et une affordance ne se paie pas d'un aller-retour.
    ///
    /// Un jeton présent ne dit pas qu'il est **valide** - seul [#moi()] le sait. C'est suffisant pour
    /// ce à quoi cela sert : empêcher un geste qui ne peut RIEN faire faute de jeton (#4194).
    public boolean estConnecte() {
        return fournisseurToken.token().isPresent();
    }

    /// Profil de l'utilisateur connecté (`GET /moi`), **trié** (#1284) : un jeton refusé (`401`)
    /// revient en `Refuse`, une panne en `Injoignable` : la modale de connexion peut enfin cesser
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
    /// **obligatoires** `lng`, `lat`, `r` (flottants), une requête MongoDB `$near`, donc des résultats
    /// **triés par distance croissante** : plafonnés à 80 éléments. Le rôle `Observateur` suffit.
    ///
    /// Le rayon est en **mètres** (`$maxDistance` d'un `$near` GeoJSON). [#RAYON_CARRE_STOC_METRES] vaut
    /// 10 km : les carrés STOC font 2 km de côté, un point tombe donc toujours à moins de ~1,5 km du
    /// centre du sien ; le rayon large sert à répondre quand même (le carré voisin) plutôt que rien.
    ///
    /// Issue **triée** (#1284) : hors connexion, panne et refus restent distincts d'une position sans
    /// carré : c'est ce qui permet à l'appelant de se taire au lieu d'accuser à tort.
    public ReponseApi<Optional<String>> carreStoc(double latitude, double longitude) {
        return carreStoc(latitude, longitude, RAYON_CARRE_STOC_METRES);
    }

    /// Le même carré, au **rayon qu'on choisit** (#4576). [#RAYON_CARRE_STOC_METRES] a été taillé pour un
    /// **contrôle**, où un humain a déjà tapé la vérité à côté et où le voisin ne fait que nuancer. Une
    /// **proposition** se valide sans se relire : le voisin y devient un numéro faux et plausible.
    ///
    /// Le serrage se demande au **serveur** : `r` est le `$maxDistance` de son `$near`. Filtrer soi-même
    /// obligerait à lire `centre`, ce dont [ReponsesVigieChiro#numeroCarreStoc] se dispense.
    public ReponseApi<Optional<String>> carreStoc(double latitude, double longitude, int rayonMetres) {
        String requete = "/grille_stoc/cercle?lng=" + longitude + "&lat=" + latitude + "&r=" + rayonMetres;
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

    /// Le **catalogue des sites de toute la plateforme** (`GET /sites`, collection Eve paginée) :
    /// ~20 500 sites, tous observateurs confondus, là où [#mesSites] ne rend que ceux où vous avez une
    /// nuit.
    ///
    /// `pages` est le nombre de pages à lire, **borné à `[1, PAGES_MAX]`** : la collection entière fait
    /// plus de deux cents pages, et il est légitime de n'en vouloir qu'un échantillon. Le [LotPagine]
    /// rendu dit **combien** il a lu et **si c'est tout** - sans quoi un échantillon serait indiscernable
    /// de la collection, ce qui est le défaut que #1277 a coûté cher à corriger.
    ///
    /// Aucun filtre serveur n'est demandé **ici** : ce backend **accepte puis ignore** `where=` (le total
    /// annoncé ne bouge même pas), si bien qu'un filtre encodé ainsi ne filtrerait rien tout en le
    /// laissant croire. Le tri se fait chez l'appelant, sur ce qu'il a réellement lu.
    ///
    /// **Cela ne vaut que pour `where=`.** Le paramètre `q`, lui, filtre réellement - voir
    /// [#chercherCarre] : qui cherche **un** carré n'a pas à payer deux cents pages.
    public ReponseApi<LotPagine<SiteVigieChiro>> sitesPlateforme(int pages, SuiviPagination suivi) {
        int bornees = Math.clamp(pages, 1, PAGES_MAX);
        return PaginationEve.parcourirBorne(bornees, this::pageSites, SitesVigieChiro::sites, suivi);
    }

    /// Les sites de la plateforme portant ce **numéro de carré** (#3458), en **une** requête.
    ///
    /// ## Ce que la mesure a établi (2026-08-14, six `GET` en lecture seule)
    ///
    /// `_sites_generic_list` construit `lookup['$text'] = {'$search': q}` quand `q` est présent, et
    /// l'index texte **existe** : le total annoncé passe de 20 767 à 1 pour `q=130711`, et à 0 pour un
    /// numéro qui n'existe pas. Ce n'est donc **pas** un paramètre accepté puis ignoré comme `where=`.
    ///
    /// **`$text` cherche des mots entiers, jamais des préfixes** : `13071` ne ramène pas `130711`.
    /// C'est exactement ce qu'il faut pour un numéro de carré - aucun faux positif par troncature - mais
    /// cela **interdit** de bâtir une recherche partielle là-dessus. Le titre d'un site
    /// (`Vigiechiro - Point Fixe-130711`) indexe le numéro comme un mot à lui seul, le tiret servant de
    /// séparateur.
    ///
    /// Liste **vide** = ce carré n'existe pas sur la plateforme. Plusieurs entrées sont possibles : un
    /// même carré peut porter un site par protocole, et le titre le dit.
    public ReponseApi<List<SiteVigieChiro>> chercherCarre(String numeroCarre) {
        String requete = "/sites?max_results=" + RESULTATS_RECHERCHE_CARRE + "&q="
                + URLEncoder.encode(numeroCarre, StandardCharsets.UTF_8);
        return transport.lire(requete).transformer(SitesVigieChiro::sites);
    }

    /// Les localités d'un site **telles quelles**, avec l'`_etag` du site (#3458).
    ///
    /// Lecture séparée de [#mesSites] parce qu'elle sert à **réécrire** : elle rend le JSON non
    /// interprété, seul moyen de renvoyer intacts les champs qu'on ne sait pas lire.
    public ReponseApi<LocalitesDuSite> localitesDuSite(String idSite) {
        return transport.lire(CHEMIN_SITES + idSite).lireAvec(ReponsesVigieChiro::localitesDuSite);
    }

    /// Remplace **toute** la liste des localités d'un site (`PUT /sites/{id}/localites`, #3458).
    ///
    /// **Aucun `If-Match` n'est envoyé, et ce n'est pas un oubli** : `set_localite` appelle
    /// `sites.update(...)` **sans `if_match`** côté backend, si bien que l'en-tête n'y changerait rien -
    /// le serveur retente jusqu'à passer. La protection contre l'écrasement concurrent est donc
    /// **entièrement côté client**, dans [fr.univ_amu.iut.sites.model.PublicationPoint], qui relit
    /// l'`_etag` juste avant d'écrire. Croire que cet en-tête protège serait le pire des deux mondes.
    ///
    /// @param localites la liste **complète** : ce qui n'y figure pas est effacé
    public ReponseApi<String> remplacerLocalites(String idSite, JsonArray localites) {
        return transport.ecrire(
                PUT,
                CHEMIN_SITES + idSite + "/localites",
                RequetesVigieChiro.localites(localites),
                null,
                TransportVigieChiro.Rejeu.INTERDIT);
    }

    /// **Lecture brute** d'un chemin de l'API, réservée à l'exploration (`vigiechiro api lire`).
    ///
    /// Cette méthode est la seule qui **ne nomme pas** ce qu'elle lit : elle rend le corps tel quel,
    /// sans projection ni interprétation. C'est délibérément une **échappatoire**, pas une porte
    /// d'entrée. Tout le reste de ce client existe parce qu'un point d'accès nommé encode, une fois
    /// pour toutes, ce qu'il faut savoir avant d'appeler - le plafond de pagination, l'ordre inversé
    /// des coordonnées, le filtre que le serveur ignore. Une capacité nouvelle passe par une méthode
    /// nommée, jamais par ici.
    ///
    /// Une règle d'architecture (`ArchitectureTest#lecture_brute_reservee_au_groupe_api`) empêche que
    /// cette promesse ne repose sur la seule bonne volonté : hors du groupe `api`, personne ne l'appelle.
    public ReponseApi<String> lectureBrute(String chemin) {
        return transport.lire(chemin);
    }

    /// Corps JSON d'une page du catalogue.
    private ReponseApi<String> pageSites(int page) {
        return transport.lire("/sites" + PaginationEve.requete(page));
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

    /// Participation **détaillée** (`GET /participations/#id`, axe 4) : `_etag` (que la garde de
    /// concurrence relit et compare avant d'écrire, #4552), dates, météo, configuration matérielle et
    /// état du traitement Tadarida. Issue
    /// **triée** (#1284) : une participation inconnue revient en `Refuse(404)`, une panne en
    /// `Injoignable`, l'absence de jeton en `NonConnecte` : plus jamais un même « vide ».
    public ReponseApi<ParticipationDetail> participation(String id) {
        return transport.lire(CHEMIN_PARTICIPATIONS + id).lireAvec(ParticipationsVigieChiro::detail);
    }

    /// Résultats Tadarida d'une participation (`GET /participations/#id/donnees`, #719, axe 4.2) : les
    /// fichiers et leurs observations, **toutes pages confondues** (la réponse Eve est paginée ; on
    /// parcourt jusqu'à une page vide). Issue **triée** (#1284) et pagination **tout-ou-rien** : une
    /// panne en cours de parcours rend l'issue, jamais un préfixe silencieux. Un `Succes` à liste vide
    /// signifie réellement « le serveur n'a pas (encore) de résultats » (#1264).
    public ReponseApi<List<DonneeVigieChiro>> donnees(String participationId) {
        return donnees(participationId, (page, totalPages) -> {});
    }

    /// Variante **suivie page par page** (#1522, #1534) : [SuiviPagination#surPage] après chaque page lue
    /// (avec le nombre total de pages, `_meta.total`), pour une progression **déterminée** et **honorer une
    /// annulation** pendant un long téléchargement (une nuit fait des milliers de fichiers, donc des
    /// dizaines de pages). Même contrat tout-ou-rien / trié.
    public ReponseApi<List<DonneeVigieChiro>> donnees(String participationId, SuiviPagination suivi) {
        return PaginationEve.parcourir(
                PAGES_MAX,
                // Le renoncement de l'appelant descend jusqu'au réessai (#2686) : sans lui, « Annuler »
                // disparaîtrait dans une temporisation de reprise, alors que ce parcours le relaie
                // précisément page par page depuis #1522.
                page -> transport.lire(
                        CHEMIN_PARTICIPATIONS + participationId + "/donnees" + PaginationEve.requete(page),
                        suivi::renonce),
                DonneesVigieChiro::donnees,
                suivi);
    }

    /// **Journal de traitement** d'une participation (#1132) : le serveur y trace l'ingestion :
    /// archives extraites avec inventaire (`Archive contained: {'audio/wav': N}`), chaque fichier
    /// passé à Tadarida. Chaîne : `GET /participations/#id` → document `logs` → `GET
    /// /fichiers/#id/acces` → URL S3 signée, téléchargée **sans** en-tête d'authentification.
    ///
    /// Issue **triée** (#1284) : `Succes(Optional.empty())` signifie « le serveur répond, et le
    /// journal n'existe pas (encore) » (traitement jamais lancé) : à ne plus confondre avec une panne
    /// ou un refus, qui gardent leur cause (la vérification d'un dépôt hors ligne n'est plus un faux
    /// « tout manquant »).
    public ReponseApi<Optional<String>> journalTraitement(String participationId) {
        return transport
                .lire(CHEMIN_PARTICIPATIONS + participationId)
                .puis(corps -> JournalVigieChiro.idJournal(corps)
                        .map(idFichier -> accesFichier(idFichier).transformer(Optional::of))
                        .orElseGet(() -> ReponseApi.succes(Optional.empty())));
    }

    /// **Télécharge le contenu d'un fichier** rattaché par son `_id` : `GET /fichiers/#id/acces` → URL S3
    /// signée → [TransportVigieChiro#telecharger] (GET de l'URL signée, **sans** en-tête
    /// d'authentification, la signature faisant foi). Généralise la chaîne autrefois codée en dur pour le
    /// seul journal (#1132) : elle sert aussi le CSV d'observations (#1565) et le repli audio (#1244).
    /// Issue **triée** (#1284) : un fichier indisponible (`disponible:false`) revient en `Refuse(410)`,
    /// une panne en `Injoignable`.
    public ReponseApi<String> accesFichier(String fichierId) {
        return transport
                .lire(CHEMIN_FICHIERS + "/" + fichierId + "/acces")
                .lireAvec(ReponsesVigieChiro::urlSignee)
                .puis(transport::telecharger);
    }

    /// **Fichiers rattachés** à une participation, filtrés par type (`GET
    /// /participations/#id/pieces_jointes?<filtre>=true`, #1565) : chaque [PieceJointe] porte
    /// `{_id, titre, disponible}`. C'est la **seule** voie pour obtenir le `_id` d'un fichier (la
    /// collection `/fichiers` n'est pas listable pour un observateur, `403`). Issue **triée** (#1284) ;
    /// une liste vide signifie « le serveur répond, aucun fichier de ce type (encore) ».
    public ReponseApi<List<PieceJointe>> piecesJointes(String participationId, TypePieceJointe filtre) {
        return transport
                .lire(CHEMIN_PARTICIPATIONS + participationId + "/pieces_jointes?" + filtre.parametre() + "=true")
                .transformer(PiecesJointesVigieChiro::pieces);
    }

    /// **CSV d'observations** d'une participation (#1565), au format Tadarida BRUT : un **seul**
    /// téléchargement ([#piecesJointes] `processing_extra` pour trouver son `_id`, puis [#accesFichier])
    /// qui remplace les dizaines de pages de [#donnees]. `Succes(Optional.empty())` signifie « le serveur
    /// répond mais le CSV n'existe pas (encore) ou n'est pas monté sur S3 ». Contrepartie du format : il
    /// ne porte **pas** l'`_id` des donnees, donc l'ancrage plateforme doit être acquis ailleurs (à la
    /// réactivation, #1571).
    public ReponseApi<Optional<String>> csvObservations(String participationId) {
        return piecesJointes(participationId, TypePieceJointe.PROCESSING_EXTRA)
                .puis(pieces -> pieces.stream()
                        .filter(PieceJointe::disponible)
                        .filter(piece -> piece.titre() != null && piece.titre().endsWith("-observations.csv"))
                        .findFirst()
                        .map(piece -> accesFichier(piece.id()).transformer(Optional::of))
                        .orElseGet(() -> ReponseApi.succes(Optional.empty())));
    }

    // ---------------------------------------------------------------------------------------------
    // Écritures (dépôt d'une nuit, #142) : création de participation + upload de fichiers vers S3
    // ---------------------------------------------------------------------------------------------

    /// Crée une **participation** sur un site (`POST /sites/#id/participations`, #142) : renvoie l'`_id` créé,
    /// ou un [ResultatEcriture] portant le **détail de l'échec** (statut HTTP + corps de la réponse
    /// VigieChiro) pour un message exploitable : le dépôt étant une écriture, un refus doit être **expliqué**,
    /// pas silencieusement vide. Prérequis métier : site **verrouillé** côté VigieChiro.
    public ResultatEcriture creerParticipation(String siteId, ParticipationADeposer participation) {
        ReponseApi<String> reponse =
                poster("/sites/" + siteId + "/participations", RequetesVigieChiro.participation(participation));
        String echec = echecDe(reponse);
        if (echec != null) {
            return ResultatEcriture.echouee(echec);
        }
        String corps = reponse.enOptionnel().orElseThrow();
        return ReponsesVigieChiro.idCree(corps)
                .map(ResultatEcriture::reussie)
                .orElseGet(() -> ResultatEcriture.echouee("réponse acceptée mais sans identifiant : " + corps));
    }

    /// **Met à jour** une participation existante (`PATCH /participations/#id`, axe 4) : n'émet que les
    /// métadonnées synchronisables (dates, météo, configuration ; cf. [RequetesVigieChiro#miseAJourParticipation]).
    /// Renvoie l'`_id` en cas de succès, ou le **détail de l'échec** (statut + corps) : un refus doit être
    /// expliqué.
    ///
    /// `etag` n'est **plus envoyé** : mesuré le 2026-08-26, cette route rend `200` sans en-tête comme
    /// avec un étiquetage faux (#4523). Il reste au contrat parce qu'une concurrence optimiste côté
    /// client, sur le patron de `PublicationPoint`, en aurait besoin.
    public ResultatEcriture modifierParticipation(String id, String etag, ParticipationADeposer miseAJour) {
        // Pas de rejeu (#2677) : la plateforme ignore l'`If-Match` sur cette route (#4523), donc un
        // rejeu reposerait des valeurs absolues par-dessus ce qu'un autre poste a écrit entre-temps.
        // La concurrence est tenue en amont, par la relecture de `SynchronisationParticipation` (#4552).
        String echec = echecDe(transport.ecrire(
                "PATCH",
                CHEMIN_PARTICIPATIONS + id,
                RequetesVigieChiro.miseAJourParticipation(miseAJour),
                null,
                TransportVigieChiro.Rejeu.INTERDIT));
        return echec == null ? ResultatEcriture.reussie() : ResultatEcriture.echouee(echec);
    }

    /// **Publie une correction d'observation** (`PATCH /donnees/#id/observations/#indice`, #723,
    /// contrat #1203) : pose le taxon observateur (**objectid**) et la certitude sur le sous-document
    /// **positionnel** `indice` de la donnée (une observation serveur n'a pas d'`_id` propre). Pas
    /// d'`If-Match` : le handler serveur n'en lit pas. `bilan` à `false` ajoute `?no_bilan=true` (le
    /// serveur ne régénère pas le bilan de la participation : levier de rafale, ne mettre `true` que
    /// sur le **dernier** envoi d'un lot). Un `HTTP 404` signale un **ancrage périmé** (la donnée a
    /// été régénérée par un re-compute) ; tout refus revient détaillé (statut + corps).
    public ResultatEcriture corrigerObservation(
            String donneeId, int indice, String objectidTaxon, Certitude certitude, boolean bilan) {
        String chemin = "/donnees/" + donneeId + "/observations/" + indice + (bilan ? "" : "?no_bilan=true");
        String echec = echecDe(
                // Rejeu autorisé (#2677) : la correction pose une **valeur absolue** (taxon, certitude)
                // sur un sous-document repéré par son indice. L'appliquer deux fois donne le même état, et
                // la seconde réponse dit la même chose que la première.
                transport.ecrire(
                        "PATCH",
                        chemin,
                        RequetesVigieChiro.correction(objectidTaxon, certitude),
                        null,
                        TransportVigieChiro.Rejeu.AUTORISE));
        return echec == null ? ResultatEcriture.reussie() : ResultatEcriture.echouee(echec);
    }

    /// Poste un **message** dans le fil de discussion d'une observation (#1418, axe 4.4) :
    /// `PUT /donnees/{donneeId}/observations/{indice}/messages`, corps `{"message": "…"}`.
    ///
    /// Même **ancrage positionnel** que la correction (#1203 / #1139) : la donnée par son `_id`, puis
    /// l'indice **brut** de l'observation dans son tableau. Le serveur laisse passer le propriétaire de la
    /// donnée, donc un jeton d'`Observateur` suffit : contrairement à l'avis de validateur, qu'il refuse
    /// (403) et que l'application ne peut donc que lire (#1417).
    ///
    /// **Écriture définitive.** Le serveur ajoute par `$push`, et **aucune route ne permet de supprimer
    /// ni de modifier un message**. Ce qui part ne se retire pas, et part sur des données partagées avec
    /// un validateur du MNHN. L'appelant **doit** l'avoir fait confirmer explicitement.
    ///
    /// Ni `If-Match`, ni `_etag` : le serveur n'offre aucun contrôle de concurrence sur cette route. Deux
    /// messages postés en même temps s'empilent, ils ne s'écrasent pas : c'est le seul point rassurant de
    /// cette absence.
    ///
    /// @return l'issue **triée** (#1284) : `Succes` (le fil contient le message), `NonConnecte`,
    ///     `Injoignable` ou `Refuse` (statut + corps). Un `404` signale un **ancrage périmé** : la donnée a
    ///     été régénérée par un re-compute côté serveur, et il faut réimporter avant de réessayer
    public ReponseApi<String> posterMessage(String donneeId, int indice, String texte) {
        String chemin = "/donnees/" + donneeId + "/observations/" + indice + "/messages";
        // Rejeu INTERDIT (#2677), et c'est le cas qui montre qu'une règle par verbe HTTP serait fausse :
        // ce `PUT` **empile** par `$push` côté serveur. Le rejouer poste le message deux fois, sur des
        // données partagées avec un validateur du MNHN, et aucune route ne permet de le retirer.
        return transport.ecrire(
                PUT, chemin, RequetesVigieChiro.message(texte), null, TransportVigieChiro.Rejeu.INTERDIT);
    }

    /// Déclare un **fichier** à téléverser (`POST /fichiers`, étape 1/3) : renvoie son `_id` et l'URL S3
    /// pré-signée ([FichierSigne]), issue **triée** (#1284) pour un message de dépôt exploitable. Le
    /// mime n'est pas transmis (déduit de l'extension du titre) ; le titre doit respecter la
    /// convention de nommage VigieChiro (`Car…-Pass…`).
    public ReponseApi<FichierSigne> creerFichier(String titre, String participationId) {
        return poster(CHEMIN_FICHIERS, RequetesVigieChiro.fichier(titre, participationId))
                .lireAvec(ReponsesVigieChiro::fichierSigne);
    }

    /// **PUT** des octets vers l'**URL S3 pré-signée** (étape 2/3) : hors API VigieChiro (aucun en-tête
    /// d'auth, l'URL est déjà signée). Le `Content-Type` doit être le **mime attendu par la signature**
    /// (sinon S3 répond `SignatureDoesNotMatch`).
    ///
    /// Rend l'**issue triée** et non un booléen (#3688) : le transport la construit de toute façon, et
    /// c'est elle qui dit si le refus se **retente**. Le booléen la jetait au dernier moment, si bien
    /// qu'un refus définitif redevenait rejouable une ligne plus loin.
    public ReponseApi<String> televerserVersS3(String urlSignee, byte[] octets, String mime) {
        return transport.deposerVersS3(urlSignee, () -> HttpRequest.BodyPublishers.ofByteArray(octets), mime);
    }

    /// Variante **en flux** de [#televerserVersS3(String, byte[], String)] (#982) : le corps du `PUT` est
    /// **streamé depuis le disque** (`BodyPublishers.ofFile`) au lieu d'être chargé en mémoire : une
    /// archive ZIP de dépôt peut peser ~700 Mo. Même issue triée, fichier illisible compris.
    public ReponseApi<String> televerserVersS3(String urlSignee, Path fichier, String mime) {
        return televerserVersS3(urlSignee, fichier, mime, fraction -> {});
    }

    /// Comme [#televerserVersS3(String, Path, String)], en **remontant l'avancement** octet par octet
    /// (#984) à `progression` (fraction 0 à 1) pour alimenter une barre de progression par archive.
    public ReponseApi<String> televerserVersS3(
            String urlSignee, Path fichier, String mime, DoubleConsumer progression) {
        return televerserVersS3(urlSignee, fichier, mime, progression, SuiviReprise.SILENCIEUX);
    }

    /// Comme ci-dessus, en **remontant les reprises** (#2354) à `reprise` : une coupure momentanée sur ce
    /// gros `PUT` est réessayée (le `PUT` S3 est idempotent), et chaque nouvelle tentative est signalée
    /// pour une mention discrète côté IHM.
    public ReponseApi<String> televerserVersS3(
            String urlSignee, Path fichier, String mime, DoubleConsumer progression, SuiviReprise reprise) {
        return transport.deposerVersS3(
                urlSignee, () -> CorpsFichierAvecProgression.depuis(fichier, progression), mime, reprise);
    }

    /// Finalise un fichier téléversé (`POST /fichiers/#id`, étape 3/3), issue **triée** (#1284) : un
    /// refus revient avec son statut et son corps, une panne avec sa cause.
    public ReponseApi<String> finaliserFichier(String fichierId) {
        return poster(CHEMIN_FICHIERS + "/" + fichierId, RequetesVigieChiro.finalisation());
    }

    /// Seuil de bascule en **multipart** (#2354) : au-delà, le `PUT` S3 est découpé en parties
    /// réessayables séparément. 5 Mo est le minimum d'une partie S3 (sauf la dernière) et la taille de
    /// chunk retenue - valeur reprise de l'implémentation de référence ChiroTool.
    public static final long SEUIL_MULTIPART_OCTETS = 5L * 1024 * 1024;

    /// Déclare un fichier **multipart** (#2354, `POST /fichiers {multipart:true}`) : la réponse ne porte
    /// qu'un `_id` (aucune URL unique, les URL viennent partie par partie), rendu en cas de succès.
    public ReponseApi<String> creerFichierMultipart(String titre, String participationId) {
        return poster(CHEMIN_FICHIERS, RequetesVigieChiro.fichier(titre, participationId, true))
                .lireAvec(ReponsesVigieChiro::idCree);
    }

    /// Téléverse `fichier` en **parties** (#2354) sous le fichier déjà déclaré `fichierId` : pour chaque
    /// chunk de [#SEUIL_MULTIPART_OCTETS], demande son URL signée (`PUT /fichiers/{id}/multipart`), la
    /// dépose (réessayée, `ETag` collecté), puis finalise (`POST /fichiers/{id} {parts}`). Rend le succès
    /// de la finalisation, ou la **première** issue en échec (URL, partie ou finalisation) - à charge de
    /// l'appelant d'[#abandonnerFichier] pour ne pas laisser de parties orphelines côté serveur.
    public ReponseApi<String> deposerEnParts(
            String fichierId, Path fichier, String mime, DoubleConsumer progression, SuiviReprise reprise) {
        return deposerEnParts(fichierId, fichier, mime, SEUIL_MULTIPART_OCTETS, progression, reprise);
    }

    /// Variante à **taille de chunk** explicite (#2354) : la production découpe en [#SEUIL_MULTIPART_OCTETS],
    /// les tests en petits chunks pour exercer la boucle sur un fichier minuscule.
    ReponseApi<String> deposerEnParts(
            String fichierId,
            Path fichier,
            String mime,
            long tailleChunk,
            DoubleConsumer progression,
            SuiviReprise reprise) {
        long taille;
        try {
            taille = Files.size(fichier);
        } catch (IOException illisible) {
            return ReponseApi.injoignable("fichier illisible : " + illisible.getMessage());
        }
        List<PartieDeposee> parties = new ArrayList<>();
        long envoye = 0;
        try (InputStream flux = Files.newInputStream(fichier)) {
            byte[] tampon = new byte[(int) tailleChunk];
            for (int numero = 1; ; numero++) {
                int lus = flux.readNBytes(tampon, 0, tampon.length);
                if (lus == 0) {
                    break;
                }
                byte[] chunk = lus == tampon.length ? tampon.clone() : Arrays.copyOf(tampon, lus);
                // Rejeu autorisé (#2677) : demander deux fois l'URL de la partie `numero` ne crée rien -
                // c'est une signature, et le numéro de partie est explicite. C'est le `PUT` S3 qui suit
                // qui dépose des octets, et il a son propre réessai depuis #2354.
                ReponseApi<String> url = transport
                        .ecrire(
                                PUT,
                                CHEMIN_FICHIERS + "/" + fichierId + "/multipart",
                                RequetesVigieChiro.demandePartie(numero),
                                null,
                                TransportVigieChiro.Rejeu.AUTORISE)
                        .lireAvec(ReponsesVigieChiro::urlDePartie);
                if (!(url instanceof ReponseApi.Succes<String>(String urlSignee))) {
                    return url;
                }
                ReponseApi<String> partie = transport.deposerPartie(
                        urlSignee, () -> HttpRequest.BodyPublishers.ofByteArray(chunk), mime, reprise);
                if (!(partie instanceof ReponseApi.Succes<String>(String etag))) {
                    return partie;
                }
                parties.add(new PartieDeposee(numero, etag));
                envoye += lus;
                progression.accept(taille == 0 ? 1.0 : (double) envoye / taille);
            }
        } catch (IOException interrompu) {
            return ReponseApi.injoignable("lecture du fichier interrompue : " + interrompu.getMessage());
        }
        return poster(CHEMIN_FICHIERS + "/" + fichierId, RequetesVigieChiro.finalisationMultipart(parties));
    }

    /// Abandonne un upload multipart (#2354, `DELETE /fichiers/{id}`) : à appeler quand une partie a
    /// échoué définitivement, pour que le serveur ne conserve pas de parties orphelines. Best-effort.
    public ReponseApi<String> abandonnerFichier(String fichierId) {
        // Rejeu autorisé (#2677) : supprimer deux fois laisse le même état. Un `404` au second passage
        // n'est pas un problème ici, l'abandon étant de toute façon au mieux-effort.
        return transport.ecrire(
                "DELETE", CHEMIN_FICHIERS + "/" + fichierId, "", null, TransportVigieChiro.Rejeu.AUTORISE);
    }

    // Le traitement serveur (lancer le compute, lire son etat) vit dans TraitementVigieChiro :
    // le client transporte, il ne decide pas de ce qu'un refus veut dire (#1261).
    /// **POST authentifié** d'un corps JSON sur `chemin`, issue **triée** ([ReponseApi]) : statut et
    /// corps d'un refus conservés, pour un message d'erreur exploitable (création de participation,
    /// lancement d'un traitement #1261).
    /// Jamais rejoué (#2677) : ces `POST` **créent** (participation, déclaration de fichier,
    /// finalisation, lancement d'un traitement) et l'API n'offre aucune clé d'idempotence. Si la requête
    /// arrive, que le serveur crée, et que la réponse se perd, un rejeu créerait une seconde ressource -
    /// une participation en double sur la plateforme, qu'il faudrait aller supprimer à la main.
    ReponseApi<String> poster(String chemin, String corpsJson) {
        return transport.ecrire("POST", chemin, corpsJson, null, TransportVigieChiro.Rejeu.INTERDIT);
    }

    /// Triage **commun des écritures** : la cause d'échec exploitable ([ReponseApi#echec()], le
    /// vocabulaire unique des messages), ou `null` si la réponse est un succès 2xx. Une écriture
    /// refusée doit être expliquée à l'utilisateur, jamais réduite à un booléen opaque.
    private static String echecDe(ReponseApi<String> reponse) {
        return reponse.echec().orElse(null);
    }
}
