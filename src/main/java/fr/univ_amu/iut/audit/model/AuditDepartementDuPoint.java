package fr.univ_amu.iut.audit.model;

import fr.univ_amu.iut.commun.model.Commune;
import fr.univ_amu.iut.commun.model.RegionDuCarre;
import fr.univ_amu.iut.commun.model.RegionsFrancaises;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.sites.model.CommuneDuPoint;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// Confronte les **deux lectures** du département d'un point d'écoute (#2848), hors ligne.
///
/// Depuis que chaque point porte sa commune (ADR 2791), son département se lit de deux façons qui
/// peuvent se contredire : par son **carré**, les deux premiers chiffres du numéro
/// ([RegionDuCarre#departement], ADR 2351), lecture qu'affiche la fiche site ; ou par sa **commune**, le
/// préfixe du code INSEE ([Commune#departement]).
///
/// Le constat **montre** l'écart, il ne le juge pas. Un carré fait 2 km de côté (R26) et peut chevaucher
/// deux départements dès qu'il touche une limite : la divergence est alors normale, et c'est le cas le
/// plus fréquent. Elle peut aussi trahir un GPS mal pointé ou un numéro mal recopié, et rien ici ne
/// départage. D'où [Severite#INFO], le seul niveau qui ne fasse pas rendre 1 à `audit-coherence`.
///
/// Les silences sont des décisions : un point **sans commune résolue** n'a pas de seconde lecture à
/// confronter, un **numéro illisible** pas de première, et une différence de pure écriture (Corse `20`
/// contre `2A`, outre-mer `97` contre `971`) n'est pas une divergence,
/// [RegionsFrancaises#memeDepartement] s'abstenant plutôt que de trancher.
public final class AuditDepartementDuPoint {

    private final SiteDao siteDao;
    private final PointDao pointDao;
    private final PointCommuneDao communeDao;

    public AuditDepartementDuPoint(SiteDao siteDao, PointDao pointDao, PointCommuneDao communeDao) {
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.communeDao = Objects.requireNonNull(communeDao, "communeDao");
    }

    /// Les constats de tous les points de tous les sites, dans l'ordre des sites puis des points.
    List<ConstatAudit> auditer() {
        // Points et communes lus **par lot** (#4281) : la boucle en lançait une requête par site, puis
        // une par point pour sa commune. Même défaut que #4251, #4271 et #4278, de moindre ampleur -
        // mesuré à chaud, 130 ms à cent cinquante carrés, et qui croît avec l'inventaire.
        List<Site> sites = siteDao.findAll();
        Map<Long, List<PointDEcoute>> pointsParSite =
                pointDao.findParSites(sites.stream().map(Site::id).toList());
        Map<Long, Commune> communes = new HashMap<>();
        for (CommuneDuPoint resolue : communeDao.findAll()) {
            communes.put(resolue.idPoint(), resolue.commune());
        }

        List<ConstatAudit> constats = new ArrayList<>();
        for (Site site : sites) {
            Optional<String> duCarre = RegionDuCarre.departement(site.numeroCarre());
            if (duCarre.isEmpty()) {
                continue; // numéro illisible : pas de première lecture, rien à confronter
            }
            for (PointDEcoute point : pointsParSite.getOrDefault(site.id(), List.of())) {
                confronter(site, point, duCarre.get(), communes).ifPresent(constats::add);
            }
        }
        return List.copyOf(constats);
    }

    private Optional<ConstatAudit> confronter(
            Site site, PointDEcoute point, String duCarre, Map<Long, Commune> communes) {
        Optional<Commune> commune = Optional.ofNullable(communes.get(point.id()));
        if (commune.isEmpty()) {
            return Optional.empty(); // commune non résolue : pas de seconde lecture
        }
        String delaCommune = commune.get().departement();
        if (RegionsFrancaises.memeDepartement(duCarre, delaCommune)) {
            return Optional.empty();
        }
        return Optional.of(new ConstatAudit(
                Severite.INFO,
                CategorieConstat.DEPARTEMENT_DIVERGENT,
                null,
                site.numeroCarre() + " / " + point.code(),
                detail(site, point, duCarre, commune.get(), delaCommune)));
    }

    /// Le détail nomme les **deux** lectures et **leurs sources**, et il les nomme **en tête**.
    ///
    /// Dire « départements 13 et 84 » sans dire lequel vient d'où obligerait à rouvrir deux écrans pour
    /// savoir quoi vérifier. Mais la place compte autant que le contenu : la colonne « Détail » de la
    /// table **tronque**, et la revue visuelle de la clôture a montré la phrase coupée à
    /// « …(départeme… » - c'est-à-dire juste avant la moitié de la comparaison. Ce constat **est** une
    /// comparaison ; en montrer une seule moitié ne dit rien. Les deux nombres et leur source passent
    /// donc avant la prose, pour tenir dans ce que la cellule laisse voir.
    private static String detail(Site site, PointDEcoute point, String duCarre, Commune commune, String delaCommune) {
        return "Départements " + delaCommune + " (commune) et " + duCarre + " (carré) : le point "
                + point.code() + " est en " + commune.nom() + ", son carré " + site.numeroCarre()
                + " dit autre chose."
                + " Un carré posé sur une limite de département peut en chevaucher deux : l'écart est peut-être normal."
                + " Sinon, vérifiez les coordonnées du point ou le numéro du carré.";
    }
}
