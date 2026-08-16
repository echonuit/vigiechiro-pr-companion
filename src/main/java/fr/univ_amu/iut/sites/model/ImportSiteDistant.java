package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.sites.model.dao.SiteTiersDao;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/// Poser **un** site de la plateforme dans la base locale : le créer s'il manque, le compléter s'il
/// existe, et rendre le lien qui le rattache à son homologue distant.
///
/// ## Pourquoi cette classe existe
///
/// Ce geste avait **un seul déclencheur** - la synchronisation périodique, qui part des participations
/// (`RapprochementSites`, #718). #3806 lui en donne un second, à la demande et par numéro de carré, pour
/// les carrés où l'on n'a **pas encore** déposé : la synchronisation ne les voit pas, et c'est
/// précisément là qu'il faut le lien, puisque le dépôt l'exige.
///
/// Les deux chemins doivent poser **exactement** le même état - même création, mêmes points rapatriés,
/// même marquage de propriété - sans quoi un carré arrivé par l'un se comporterait autrement qu'arrivé
/// par l'autre. D'où l'extraction, plutôt qu'une seconde écriture.
public class ImportSiteDistant {

    private static final Logger LOG = Logger.getLogger(ImportSiteDistant.class.getName());

    private final SiteDao siteDao;
    private final ServiceSites serviceSites;
    private final LienVigieChiroDao liens;

    /// Marquage « carré d'un tiers » (#2525), dérivé de `site.observateur`.
    private final SiteTiersDao siteTiers;

    private final String idUtilisateur;

    /// Communes des points (#2791), rattrapées après l'import : le réseau est à portée et l'on est déjà
    /// hors du fil JavaFX.
    private final ServiceCommunes communes;

    public ImportSiteDistant(
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

    /// Ce qu'un import a réellement produit : le lien à enregistrer, et **combien de points ont été
    /// posés** - pas combien la plateforme en a envoyé. Un point au code ou au GPS invalide est ignoré en
    /// best-effort ; l'annoncer quand même ferait chercher longtemps un point qui n'est pas là.
    ///
    /// @param lien la correspondance entre le site local et son homologue distant
    /// @param pointsPoses le nombre de localités effectivement créées par cet import
    public record ResultatImport(LienVigieChiro lien, int pointsPoses) {}

    /// Les sites locaux de l'utilisateur, indexés par numéro de carré : l'appelant qui en importe
    /// plusieurs le construit **une** fois.
    public Map<String, Site> sitesLocauxParCarre() {
        Map<String, Site> localesParCarre = new HashMap<>();
        for (Site local : siteDao.findByUtilisateur(idUtilisateur)) {
            localesParCarre.put(local.numeroCarre(), local);
        }
        return localesParCarre;
    }

    /// Relie le site distant à son pendant local (créé si absent), et renvoie le lien. Un site distant
    /// sans carré exploitable, ou dont la création échoue, est ignoré (best-effort par site).
    public Optional<ResultatImport> importerOuLier(
            SiteVigieChiro distant, Map<String, Site> localesParCarre, String idProfilConnecte) {
        return importerOuLier(
                distant,
                localesParCarre,
                idProfilConnecte,
                new SouhaitDeclaration(distant.numeroCarre(), Protocole.STANDARD, null, null));
    }

    /// Variante qui **respecte le protocole choisi** par l'utilisateur (#3806). La synchronisation
    /// périodique, elle, n'a personne à qui demander et garde `STANDARD` : c'est la surcharge ci-dessus.
    public Optional<ResultatImport> importerOuLier(
            SiteVigieChiro distant,
            Map<String, Site> localesParCarre,
            String idProfilConnecte,
            SouhaitDeclaration souhait) {
        String carre = distant.numeroCarre();
        if (carre == null) {
            return Optional.empty();
        }
        try {
            Site local = localesParCarre.get(carre);
            int poses;
            if (local == null) {
                local = creerDepuis(distant, souhait);
                poses = compterPoints(local);
                localesParCarre.put(carre, local);
            } else {
                poses = completerLesPoints(local, distant);
            }
            // Propriété du carré (#2525) : réévaluée à chaque import, dans les deux sens (un carré peut
            // changer de main côté plateforme). Sans profil lisible, `appartientAUnTiers` répond faux.
            siteTiers.definir(local.id(), distant.appartientAUnTiers(idProfilConnecte));
            return Optional.of(new ResultatImport(
                    new LienVigieChiro(
                            LienVigieChiro.ENTITE_SITE, String.valueOf(local.id()), distant.id(), distant.verrouille()),
                    poses));
        } catch (RuntimeException echecSite) {
            LOG.log(Level.FINE, echecSite, () -> "Import du site Vigie-Chiro (carré " + carre + ") ignoré");
            return Optional.empty();
        }
    }

    /// Le site local rattaché au carré, après import : ce que l'appelant à la demande veut montrer.
    public Optional<Site> siteLocalDuCarre(String numeroCarre) {
        return siteDao.findByUtilisateur(idUtilisateur).stream()
                .filter(site -> numeroCarre.equals(site.numeroCarre()))
                .findFirst();
    }

    /// Enregistre les correspondances **telles quelles**, sans purger celles qu'elles ne citent pas :
    /// l'import à la demande ne connaît qu'un carré, et n'a donc aucune autorité sur les autres.
    public void enregistrer(LienVigieChiro lien) {
        liens.upsert(lien);
    }

    /// Comble les communes des points en attente (#2791), dont ceux que l'import vient de créer.
    /// Best-effort intégral : la commune est un confort dérivé, un raté ici ne doit ni faire échouer
    /// l'import ni altérer son compte rendu.
    public void rattraperCommunes() {
        try {
            communes.rattraper();
        } catch (RuntimeException echec) {
            LOG.log(Level.FINE, echec, () -> "Rattrapage des communes ignoré (best-effort)");
        }
    }

    /// Complète un site **déjà relié** avec les points distants qui lui manquent (#3458).
    ///
    /// ⚠️ **On n'écrit que ce qui manque.** Un point de même code déjà local est laissé **intact** :
    /// c'est de la donnée saisie par l'utilisateur, et un import n'a pas à déplacer son point ni à le
    /// requalifier en « rapatrié ». Le rapprochement entre un point local et son homologue distant de
    /// mêmes coordonnées sous un autre nom est une **fusion**, qui demande un choix explicite et vient à
    /// part (#3750).
    ///
    /// ⚠️ **Le filtre par code n'est pas ce qui protège cette saisie**, et la mutation l'a montré : le
    /// retirer laisse le test passer, parce que `ajouterPoint` lève déjà sur l'unicité et que le
    /// best-effort avale. Il est là pour que la protection soit **explicite** plutôt qu'accidentelle, et
    /// pour ne pas produire quarante et une exceptions avalées à chaque import - un journal qui crie
    /// « Point Z1 ignoré » sur le cas nominal apprend à ne plus être lu.
    private int completerLesPoints(Site local, SiteVigieChiro distant) {
        Set<String> codesLocaux = serviceSites.listerPoints(local.id()).stream()
                .map(PointDEcoute::code)
                .collect(Collectors.toSet());
        int poses = 0;
        for (PointVigieChiro point : distant.points()) {
            if (!codesLocaux.add(point.code())) {
                continue;
            }
            if (ajouterPointRapatrie(local, distant, point)) {
                poses++;
            }
        }
        return poses;
    }

    /// Combien de points porte le site local : le compte **après** création, seul chiffre qui dise ce qui
    /// est réellement en base.
    private int compterPoints(Site local) {
        return serviceSites.listerPoints(local.id()).size();
    }

    /// Crée le site local (carré + titre en nom) et ses points d'écoute depuis les localités du site
    /// distant. Un point au code/GPS invalide est ignoré, sans faire échouer le site.
    private Site creerDepuis(SiteVigieChiro distant, SouhaitDeclaration souhait) {
        // La saisie de l'utilisateur l'emporte sur le titre de la plateforme : il venait de l'écrire, et
        // « Vigiechiro - Point Fixe-130711 » est un libellé technique. À défaut, le titre sert de nom.
        Site site = serviceSites.creerSite(
                distant.numeroCarre(),
                souhait.nomOuTitre(distant.titre()),
                souhait.protocole(),
                souhait.commentaireOuNull(),
                idUtilisateur);
        for (PointVigieChiro point : distant.points()) {
            ajouterPointRapatrie(site, distant, point);
        }
        return site;
    }

    /// Pose un point rapatrié, **best-effort** : un point au code ou au GPS invalide est ignoré, sans
    /// emporter les quarante autres ni le site lui-même.
    ///
    /// Marqué synchronisé (#1738) : rapatrié en masse, il pourra être masqué de la fiche site tant
    /// qu'aucune nuit ne s'y rattache, contrairement à un point ajouté à la main.
    private boolean ajouterPointRapatrie(Site site, SiteVigieChiro distant, PointVigieChiro point) {
        try {
            serviceSites.ajouterPointSynchronise(site.id(), point.code(), point.latitude(), point.longitude(), null);
            return true;
        } catch (RuntimeException pointInvalide) {
            LOG.log(
                    Level.FINE,
                    pointInvalide,
                    () -> "Point " + point.code() + " ignoré (carré " + distant.numeroCarre() + ")");
            return false;
        }
    }
}
