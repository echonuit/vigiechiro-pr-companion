package fr.univ_amu.iut.recherche.model;

import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.RechercheGlobale;
import fr.univ_amu.iut.commun.model.ResultatRecherche;
import fr.univ_amu.iut.commun.model.TypeResultat;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Implémentation de la [RechercheGlobale] (#144) : agrège les lectures des features `sites`
/// (sites + points) et `multisite` (passages aplatis en [LignePassage]) pour l'utilisateur courant,
/// et filtre par correspondance **insensible casse/accents** ([NormalisationTexte]).
///
/// Service **model** pur (aucune dépendance JavaFX/navigation) : il renvoie des [ResultatRecherche]
/// porteurs des seules clés d'identité ; c'est la couche `view` qui décidera de l'écran à ouvrir.
/// Réutilise des **services** existants (et non les DAO directement) pour ne pas redupliquer
/// l'agrégation des passages, sur le modèle de [ServiceMultisite] vis-à-vis de `sites`/`passage`.
public class ServiceRechercheGlobale implements RechercheGlobale {

    /// Plafond de résultats **par type** : la liste déroulante reste lisible et la recherche rapide.
    /// Au-delà, l'utilisateur affine sa saisie (les résultats supplémentaires sont silencieusement omis).
    static final int MAX_PAR_TYPE = 8;

    private final ServiceSites services;
    private final ServiceMultisite multisite;
    private final String idUtilisateur;

    public ServiceRechercheGlobale(ServiceSites services, ServiceMultisite multisite, String idUtilisateur) {
        this.services = Objects.requireNonNull(services, "services");
        this.multisite = Objects.requireNonNull(multisite, "multisite");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
    }

    @Override
    public List<ResultatRecherche> rechercher(String requete) {
        String aiguille = NormalisationTexte.normaliser(requete);
        if (aiguille.isEmpty()) {
            return List.of();
        }
        List<ResultatRecherche> resultats = new ArrayList<>();
        resultats.addAll(chercherSitesEtPoints(aiguille));
        resultats.addAll(chercherPassages(aiguille));
        return resultats;
    }

    /// Sites correspondants (par n° de carré ou nom) puis, pour chaque site, ses points correspondants
    /// (par code ou description). Chaque catégorie est plafonnée à [#MAX_PAR_TYPE].
    private List<ResultatRecherche> chercherSitesEtPoints(String aiguille) {
        List<ResultatRecherche> sites = new ArrayList<>();
        List<ResultatRecherche> points = new ArrayList<>();
        for (Site site : services.listerSites(idUtilisateur)) {
            // Les deux plafonds atteints : inutile de continuer (et surtout d'interroger d'autres points).
            if (sites.size() >= MAX_PAR_TYPE && points.size() >= MAX_PAR_TYPE) {
                break;
            }
            if (sites.size() < MAX_PAR_TYPE && correspond(aiguille, site.numeroCarre(), site.nomConvivial())) {
                sites.add(resultatSite(site));
            }
            // N'interroge les points d'un site (requête par site) que s'il reste de la place côté points :
            // une fois le plafond de points atteint, on évite un listerPoints inutile pour chaque site.
            if (site.id() == null || points.size() >= MAX_PAR_TYPE) {
                continue;
            }
            for (PointDEcoute point : services.listerPoints(site.id())) {
                if (points.size() >= MAX_PAR_TYPE) {
                    break;
                }
                if (correspond(aiguille, point.code(), point.description())) {
                    points.add(resultatPoint(site, point));
                }
            }
        }
        List<ResultatRecherche> fusion = new ArrayList<>(sites);
        fusion.addAll(points);
        return fusion;
    }

    /// Passages correspondants (par n° de carré, code point, n° de passage, année ou date), plafonnés.
    ///
    /// Limite connue : `listerPassages` **matérialise** tous les passages de l'utilisateur avant le
    /// filtrage/plafond. Suffisant aux volumes actuels ; si le nombre de passages grandit nettement, une
    /// **projection dédiée** (requête filtrante en base) remplacera ce balayage en mémoire.
    private List<ResultatRecherche> chercherPassages(String aiguille) {
        List<ResultatRecherche> passages = new ArrayList<>();
        for (LignePassage ligne : multisite.listerPassages(idUtilisateur)) {
            if (passages.size() >= MAX_PAR_TYPE) {
                break;
            }
            boolean trouve = correspond(
                    aiguille,
                    ligne.numeroCarre(),
                    ligne.codePoint(),
                    Integer.toString(ligne.numeroPassage()),
                    Integer.toString(ligne.annee()),
                    ligne.dateEnregistrement());
            if (trouve) {
                passages.add(resultatPassage(ligne));
            }
        }
        return passages;
    }

    private static ResultatRecherche resultatSite(Site site) {
        boolean aNom = site.nomConvivial() != null && !site.nomConvivial().isBlank();
        String libelle = aNom ? site.nomConvivial() : "Carré " + site.numeroCarre();
        String details = aNom ? "Carré " + site.numeroCarre() : "Site";
        return new ResultatRecherche(
                TypeResultat.SITE, libelle, details, site.numeroCarre(), null, site.nomConvivial(), null);
    }

    private static ResultatRecherche resultatPoint(Site site, PointDEcoute point) {
        String libelle = site.numeroCarre() + " / " + point.code();
        String details =
                point.description() != null && !point.description().isBlank() ? point.description() : "Point d'écoute";
        return new ResultatRecherche(
                TypeResultat.POINT, libelle, details, site.numeroCarre(), point.code(), site.nomConvivial(), null);
    }

    private static ResultatRecherche resultatPassage(LignePassage ligne) {
        String libelle = ligne.numeroCarre() + " / " + ligne.codePoint() + " · n°" + ligne.numeroPassage();
        String details = ligne.dateEnregistrement() != null
                ? "Passage " + ligne.annee() + " · " + ligne.dateEnregistrement()
                : "Passage " + ligne.annee();
        return new ResultatRecherche(
                TypeResultat.PASSAGE,
                libelle,
                details,
                ligne.numeroCarre(),
                ligne.codePoint(),
                null,
                ligne.idPassage());
    }

    /// Vrai si au moins un des `champs` (non nuls) contient l'`aiguille` déjà normalisée.
    private static boolean correspond(String aiguille, String... champs) {
        for (String champ : champs) {
            if (champ != null && NormalisationTexte.normaliser(champ).contains(aiguille)) {
                return true;
            }
        }
        return false;
    }
}
