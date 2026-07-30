package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.sites.model.dao.SiteTiersDao;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/// **Importe et relie** les sites de l'observateur VigieChiro à la connexion (#728/#718).
///
/// Les sites d'un observateur viennent de `GET /moi/participations` (cf. [ClientVigieChiro#mesSites()]) :
/// un observateur *participe* à des sites régionaux dont il n'est pas propriétaire. Chaque site distant
/// porte son **numéro de carré** (extrait du titre) et ses **points** (localités). Pour chacun :
/// - si un site local **de même carré** existe → on le **relie** à son `objectid` ;
/// - sinon on le **crée** (site + points, via [ServiceSites]) puis on le relie.
///
/// Un site atteint par une participation est **verrouillé** (dépôt possible) → le lien porte
/// `verrouille=true` (badge « Verrouillé » sur M-Sites). Idempotent : la déduplication par carré évite
/// tout doublon aux connexions suivantes. **Best-effort** : l'échec d'un site (ou d'un point) est logué
/// et ignoré, sans compromettre les autres ni la connexion.
///
/// Contribué au `Multibinder<RapprochementVigieChiro>` par `SitesModule` ; le client est reçu **en
/// argument** (aucune dépendance vers la feature `connexion`).
public class RapprochementSites implements RapprochementVigieChiro {

    private static final Logger LOG = Logger.getLogger(RapprochementSites.class.getName());

    /// Libellé du compte-rendu (pluriel, cf. RapportSynchro#libelle).
    private static final String LIBELLE_SITES = "sites";

    private final SiteDao siteDao;
    private final ServiceSites serviceSites;
    private final LienVigieChiroDao liens;

    /// Marquage « carré d'un tiers » (#2525), dérivé de `site.observateur` : entretenu **à chaque**
    /// synchronisation, la propriété d'un carré pouvant changer côté plateforme.
    private final SiteTiersDao siteTiers;

    private final String idUtilisateur;

    /// Communes des points (#2791) : rattrapées en fin d'import, la synchro tournant déjà hors du fil
    /// JavaFX avec le réseau à portée - le moment idéal pour combler les points en attente.
    private final ServiceCommunes communes;

    public RapprochementSites(
            SiteDao siteDao,
            ServiceSites serviceSites,
            LienVigieChiroDao liens,
            SiteTiersDao siteTiers,
            String idUtilisateur,
            ServiceCommunes communes) {
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.serviceSites = Objects.requireNonNull(serviceSites, "serviceSites");
        this.liens = Objects.requireNonNull(liens, "liens");
        this.siteTiers = Objects.requireNonNull(siteTiers, "siteTiers");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        this.communes = Objects.requireNonNull(communes, "communes");
    }

    @Override
    public Optional<RapportSynchro> synchroniser(ClientVigieChiro client) {
        try {
            // Toute issue non-succès = ne toucher à rien (ni création, ni purge : garde anti-purge).
            // Depuis #1284 la cause remonte, au lieu d'être omise en silence : sauf « non connecté ».
            return switch (client.mesSites()) {
                case ReponseApi.Succes<List<SiteVigieChiro>>(List<SiteVigieChiro> distants) ->
                    importer(distants, idProfilConnecte(client));
                case ReponseApi.NonConnecte<List<SiteVigieChiro>> nonConnecte -> Optional.empty();
                case ReponseApi.Injoignable<List<SiteVigieChiro>>(String cause) ->
                    Optional.of(RapportSynchro.empechee(LIBELLE_SITES, "Vigie-Chiro injoignable : " + cause));
                case ReponseApi.Refuse<List<SiteVigieChiro>>(int statut, String corps) ->
                    Optional.of(RapportSynchro.empechee(LIBELLE_SITES, "refus HTTP " + statut));
            };
        } catch (RuntimeException echec) {
            LOG.log(Level.FINE, echec, () -> "Import des sites Vigie-Chiro ignoré (best-effort)");
            return Optional.empty();
        }
    }

    /// Identifiant plateforme de l'observateur connecté (`GET /moi`), ou `null` si le profil n'a pas pu
    /// être lu. On le demande **au client déjà fourni** plutôt qu'à la feature `connexion` (dont cette
    /// classe ne dépend pas, cf. note d'en-tête). Sans profil, aucun carré n'est présumé « d'un tiers » :
    /// le rapprochement se comporte comme avant #2525.
    private static String idProfilConnecte(ClientVigieChiro client) {
        return client.moi() instanceof ReponseApi.Succes<ProfilVigieChiro>(ProfilVigieChiro profil)
                ? profil.id()
                : null;
    }

    /// Import/liaison de sites effectivement reçus. Une liste vide (observateur sans participation)
    /// reste un no-op prudent.
    private Optional<RapportSynchro> importer(List<SiteVigieChiro> distants, String idProfilConnecte) {
        if (distants.isEmpty()) {
            return Optional.empty();
        }
        {
            Map<String, Site> localesParCarre = new HashMap<>();
            for (Site local : siteDao.findByUtilisateur(idUtilisateur)) {
                localesParCarre.put(local.numeroCarre(), local);
            }
            List<LienVigieChiro> correspondances = new ArrayList<>();
            for (SiteVigieChiro distant : distants) {
                importerOuLier(distant, localesParCarre, idProfilConnecte).ifPresent(correspondances::add);
            }
            if (correspondances.isEmpty()) {
                return Optional.empty();
            }
            liens.remplacer(LienVigieChiro.ENTITE_SITE, correspondances);
            rattraperCommunes();
            return Optional.of(new RapportSynchro(LIBELLE_SITES, correspondances.size()));
        }
    }

    /// Comble les communes des points en attente (#2791), dont ceux que cette synchro vient de créer.
    /// Best-effort intégral : la commune est un confort dérivé, un raté ici ne doit ni faire échouer la
    /// synchro ni altérer son rapport.
    private void rattraperCommunes() {
        try {
            communes.rattraper();
        } catch (RuntimeException echec) {
            LOG.log(Level.FINE, echec, () -> "Rattrapage des communes ignoré (best-effort)");
        }
    }

    /// Relie le site distant à son pendant local (créé si absent), et renvoie le lien. Un site distant
    /// sans carré exploitable, ou dont la création échoue, est ignoré (best-effort par site).
    private Optional<LienVigieChiro> importerOuLier(
            SiteVigieChiro distant, Map<String, Site> localesParCarre, String idProfilConnecte) {
        String carre = distant.numeroCarre();
        if (carre == null) {
            return Optional.empty();
        }
        try {
            Site local = localesParCarre.get(carre);
            if (local == null) {
                local = creerDepuis(distant);
                localesParCarre.put(carre, local);
            }
            // Propriété du carré (#2525) : réévaluée à chaque synchro, dans les deux sens (un carré peut
            // changer de main côté plateforme). Sans profil lisible, `appartientAUnTiers` répond faux.
            siteTiers.definir(local.id(), distant.appartientAUnTiers(idProfilConnecte));
            return Optional.of(new LienVigieChiro(
                    LienVigieChiro.ENTITE_SITE, String.valueOf(local.id()), distant.id(), distant.verrouille()));
        } catch (RuntimeException echecSite) {
            LOG.log(Level.FINE, echecSite, () -> "Import du site Vigie-Chiro (carré " + carre + ") ignoré");
            return Optional.empty();
        }
    }

    /// Crée le site local (carré + titre en nom) et ses points d'écoute depuis les localités du site
    /// distant. Un point au code/GPS invalide est ignoré, sans faire échouer le site.
    private Site creerDepuis(SiteVigieChiro distant) {
        Site site =
                serviceSites.creerSite(distant.numeroCarre(), distant.titre(), Protocole.STANDARD, null, idUtilisateur);
        for (PointVigieChiro point : distant.points()) {
            try {
                // Marqué synchronisé (#1738) : rapatrié en masse, il pourra être masqué de la fiche site
                // tant qu'aucune nuit ne s'y rattache, contrairement à un point ajouté à la main.
                serviceSites.ajouterPointSynchronise(
                        site.id(), point.code(), point.latitude(), point.longitude(), null);
            } catch (RuntimeException pointInvalide) {
                LOG.log(
                        Level.FINE,
                        pointInvalide,
                        () -> "Point " + point.code() + " ignoré (carré " + distant.numeroCarre() + ")");
            }
        }
        return site;
    }
}
