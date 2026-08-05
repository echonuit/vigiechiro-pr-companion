package fr.univ_amu.iut.cli.model;

import fr.univ_amu.iut.commun.model.ReleveTraitement;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.ReleveTraitementDao;
import fr.univ_amu.iut.multisite.model.EtatAnalyse;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.CommuneDuPoint;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Lecture transverse pour la commande `lister-passages` (parcours P5, navigation
/// multi-sites).
///
/// Service de lecture pure (aucune écriture, aucun JavaFX). Il **orchestre des DAO de plusieurs
/// features** (`passage` et `sites`) pour reconstituer, pour chaque passage, le
/// contexte « carré / point » sans charger de vue : exactement le type de dépendance
/// inter-feature `cli → <feature>.model` autorisé par la règle ArchUnit assouplie (jamais vers un
/// `view`/`viewmodel`). Le graphe reste acyclique : `cli` est un puits, aucune feature ne
/// dépend de lui.
///
/// Pour éviter un effet N+1, les points et les sites sont chargés une seule fois et indexés par
/// identifiant ; les passages orphelins (point/site introuvable) restent listés avec un libellé
/// `"?"` plutôt que d'être masqués.
public final class RegistrePassages {

    private final PassageDao passageDao;
    private final PointDao pointDao;
    private final SiteDao siteDao;

    /// Les trois dimensions que la ligne ne portait pas, et qui manquaient donc aux filtres (#3269).
    ///
    /// Elles sont lues **une seule fois** par appel, jamais une par ligne : ces tables ne portent qu'une
    /// entrée par nuit déposée ou par point, elles tiennent en mémoire, et les interroger passage par
    /// passage ferait autant de requêtes que de lignes.
    private final PointCommuneDao communesDao;

    private final ReleveTraitementDao relevesDao;
    private final ResultatsIdentificationDao resultatsDao;

    /// Le service de campagnes vit derrière un drapeau de fonctionnalité (ADR 0003) : absent, aucune
    /// ligne ne porte de campagne, et `--campagne` ne retient donc rien - ce qui est exact, puisqu'il
    /// n'y a pas de campagne à retenir.
    private final Optional<ServiceCampagne> campagnes;

    public RegistrePassages(
            PassageDao passageDao,
            PointDao pointDao,
            SiteDao siteDao,
            PointCommuneDao communesDao,
            ReleveTraitementDao relevesDao,
            ResultatsIdentificationDao resultatsDao,
            Optional<ServiceCampagne> campagnes) {
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.communesDao = Objects.requireNonNull(communesDao, "communesDao");
        this.relevesDao = Objects.requireNonNull(relevesDao, "relevesDao");
        this.resultatsDao = Objects.requireNonNull(resultatsDao, "resultatsDao");
        this.campagnes = Objects.requireNonNull(campagnes, "campagnes");
    }

    /// Tous les passages enregistrés, enrichis du contexte site/point, triés pour un affichage
    /// stable.
    public List<LignePassage> lister() {
        Map<Long, PointDEcoute> points =
                pointDao.findAll().stream().collect(Collectors.toMap(PointDEcoute::id, Function.identity()));
        Map<Long, Site> sites = siteDao.findAll().stream().collect(Collectors.toMap(Site::id, Function.identity()));

        Map<Long, String> communes = new HashMap<>();
        for (CommuneDuPoint resolue : communesDao.findAll()) {
            communes.put(resolue.idPoint(), resolue.commune().nom());
        }
        Map<Long, ReleveTraitement> releves = relevesDao.parPassage();
        Set<Long> importes = resultatsDao.passagesAvecResultats();
        Map<Long, String> nomsCampagnes = campagnes
                .map(service ->
                        service.listerCampagnes().stream().collect(Collectors.toMap(Campagne::id, Campagne::nom)))
                .orElseGet(Map::of);

        return passageDao.findAll().stream()
                .map(passage -> versLigne(passage, points, sites, communes, releves, importes, nomsCampagnes))
                .sorted(Comparator.comparing(LignePassage::carre)
                        .thenComparing(LignePassage::codePoint)
                        .thenComparingInt(LignePassage::annee)
                        .thenComparingInt(LignePassage::numeroPassage))
                .toList();
    }

    private static LignePassage versLigne(
            Passage passage,
            Map<Long, PointDEcoute> points,
            Map<Long, Site> sites,
            Map<Long, String> communes,
            Map<Long, ReleveTraitement> releves,
            Set<Long> importes,
            Map<Long, String> nomsCampagnes) {
        PointDEcoute point = points.get(passage.idPoint());
        Site site = point == null ? null : sites.get(point.idSite());
        // L'état d'analyse se DÉDUIT, il ne se lit pas : la règle vit dans `EtatAnalyse.deduire` et
        // n'est pas réécrite ici. C'est la même que celle de l'écran « Carte & passages ».
        Optional<ReleveTraitement> releve = Optional.ofNullable(releves.get(passage.id()));
        return new LignePassage(
                passage.id(),
                site == null ? "?" : site.numeroCarre(),
                point == null ? "?" : point.code(),
                passage.annee(),
                passage.numeroPassage(),
                passage.statutWorkflow(),
                passage.verdictVerification(),
                EtatAnalyse.deduire(passage.statutWorkflow(), releve, importes.contains(passage.id())),
                passage.idCampagne() == null ? null : nomsCampagnes.get(passage.idCampagne()),
                point == null ? null : communes.get(point.id()),
                site == null ? null : site.nomConvivial());
    }

    /// Ligne d'affichage d'un passage (objet de présentation, pas une entité persistée).
    ///
    /// @param idPassage identifiant technique du passage
    /// @param carre numéro de carré du site (ou `"?"` si introuvable)
    /// @param codePoint code du point d'écoute (ou `"?"` si introuvable)
    /// @param annee année du passage
    /// @param numeroPassage numéro de passage dans l'année
    /// @param statut statut du workflow
    /// @param verdict verdict de vérification (`null` tant que non vérifié)
    public record LignePassage(
            Long idPassage,
            String carre,
            String codePoint,
            int annee,
            int numeroPassage,
            StatutWorkflow statut,
            Verdict verdict,
            EtatAnalyse etatAnalyse,
            String campagne,
            String commune,
            String nomSite) {}
}
