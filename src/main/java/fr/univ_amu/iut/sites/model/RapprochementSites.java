package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
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

    private final SiteDao siteDao;
    private final ServiceSites serviceSites;
    private final LienVigieChiroDao liens;
    private final String idUtilisateur;

    public RapprochementSites(
            SiteDao siteDao, ServiceSites serviceSites, LienVigieChiroDao liens, String idUtilisateur) {
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.serviceSites = Objects.requireNonNull(serviceSites, "serviceSites");
        this.liens = Objects.requireNonNull(liens, "liens");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
    }

    @Override
    public Optional<RapportSynchro> synchroniser(ClientVigieChiro client) {
        try {
            List<SiteVigieChiro> distants = client.mesSites();
            // Liste vide = non connecté / API indisponible : on ne touche à rien (ni création, ni purge).
            if (distants.isEmpty()) {
                return Optional.empty();
            }
            Map<String, Site> localesParCarre = new HashMap<>();
            for (Site local : siteDao.findByUtilisateur(idUtilisateur)) {
                localesParCarre.put(local.numeroCarre(), local);
            }
            List<LienVigieChiro> correspondances = new ArrayList<>();
            for (SiteVigieChiro distant : distants) {
                importerOuLier(distant, localesParCarre).ifPresent(correspondances::add);
            }
            if (correspondances.isEmpty()) {
                return Optional.empty();
            }
            liens.remplacer(LienVigieChiro.ENTITE_SITE, correspondances);
            return Optional.of(new RapportSynchro("sites", correspondances.size()));
        } catch (RuntimeException echec) {
            LOG.log(Level.FINE, echec, () -> "Import des sites VigieChiro ignoré (best-effort)");
            return Optional.empty();
        }
    }

    /// Relie le site distant à son pendant local (créé si absent), et renvoie le lien. Un site distant
    /// sans carré exploitable, ou dont la création échoue, est ignoré (best-effort par site).
    private Optional<LienVigieChiro> importerOuLier(SiteVigieChiro distant, Map<String, Site> localesParCarre) {
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
            return Optional.of(new LienVigieChiro(
                    LienVigieChiro.ENTITE_SITE, String.valueOf(local.id()), distant.id(), distant.verrouille()));
        } catch (RuntimeException echecSite) {
            LOG.log(Level.FINE, echecSite, () -> "Import du site VigieChiro (carré " + carre + ") ignoré");
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
                serviceSites.ajouterPoint(site.id(), point.code(), point.latitude(), point.longitude(), null);
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
