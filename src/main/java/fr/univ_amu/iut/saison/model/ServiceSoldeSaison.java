package fr.univ_amu.iut.saison.model;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.passage.model.FenetreSaisonniere;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.PassageOpportunisteDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.sites.model.dao.SiteTiersDao;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// Service de la feature `saison` : calcule le **solde de saison** d'un observateur, c'est-à-dire,
/// pour une année donnée, ce qu'il lui reste à faire **point par point** (#2356).
///
/// **Il agrège, il ne réimplémente rien.** Les points suivis viennent de `sites` ([SiteDao],
/// [PointDao]), les nuits de `passage` ([PassageDao]), et les fenêtres calendaires R3 de
/// [FenetreSaisonniere] (définie dans `passage.model`). Aucune de ces règles n'est copiée ici :
/// c'est la condition pour que R3/R4 n'existent pas en deux exemplaires divergents.
///
/// **Périmètre : protocole PointFixeStandard.** Le solde repose sur la promesse « deux passages par
/// an et par point, dans des fenêtres » : c'est le protocole [Protocole#STANDARD]. Les sites en
/// [Protocole#RECHERCHE] suivent des dates libres, sans solde de saison au sens R3/R4 ; ils sont
/// donc écartés du décompte.
///
/// **Périmètre : vos propres carrés.** Un carré appartenant à un **tiers** (#2525, dérivé de
/// `site.observateur`) est écarté lui aussi : y participer est une occasion, pas une obligation de
/// protocole. Les nuits qu'on y réalise restent visibles ailleurs (M-Passage), simplement le solde
/// n'en fait pas un « reste à faire ».
///
/// Constructeur **simple** (sans annotation d'injection), à la manière de `ServiceMultisite` : DAO et
/// service restent de simples objets réutilisables, `SaisonModule` sait les assembler.
public class ServiceSoldeSaison {

    /// Format `jj/MM` des dates dans les phrases d'action (l'année est celle de la saison choisie).
    private static final DateTimeFormatter JOUR_MOIS = DateTimeFormatter.ofPattern("dd/MM");

    private static final Comparator<LigneSaison> PAR_CARRE_PUIS_POINT =
            Comparator.comparing(LigneSaison::numeroCarre).thenComparing(LigneSaison::codePoint);

    private final SiteDao siteDao;
    private final PointDao pointDao;
    private final PassageDao passageDao;
    private final PassageOpportunisteDao opportunistes;

    /// Carrés appartenant à un **tiers** (#2525), dérivés de l'API : le solde ne parle que de **vos**
    /// carrés — ceux d'un autre observateur n'engagent aucune obligation de protocole.
    private final SiteTiersDao carresDeTiers;

    private final Horloge horloge;

    public ServiceSoldeSaison(
            SiteDao siteDao,
            PointDao pointDao,
            PassageDao passageDao,
            PassageOpportunisteDao opportunistes,
            SiteTiersDao carresDeTiers,
            Horloge horloge) {
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.opportunistes = Objects.requireNonNull(opportunistes, "opportunistes");
        this.carresDeTiers = Objects.requireNonNull(carresDeTiers, "carresDeTiers");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    /// Solde de la **saison courante** (année de l'horloge) pour l'observateur `idUtilisateur`.
    public SoldeSaison soldeCourant(String idUtilisateur) {
        return soldePour(idUtilisateur, horloge.aujourdhui().getYear());
    }

    /// Solde de la saison `annee` pour l'observateur `idUtilisateur` : une ligne par point suivi de
    /// ses sites PointFixeStandard, triée par carré puis par code de point (ordre déterministe).
    public SoldeSaison soldePour(String idUtilisateur, int annee) {
        Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        LocalDate aujourdhui = horloge.aujourdhui();
        List<LigneSaison> lignes = new ArrayList<>();
        // Lecture groupée : un seul accès pour écarter tous les carrés de tiers (#2525).
        Set<Long> tiers = carresDeTiers.tousLesIds();
        for (Site site : siteDao.findByUtilisateur(idUtilisateur)) {
            if (site.protocole() != Protocole.STANDARD || tiers.contains(site.id())) {
                continue;
            }
            for (PointDEcoute point : pointDao.findBySite(site.id())) {
                lignes.add(ligneDuPoint(site, point, annee, aujourdhui));
            }
        }
        lignes.sort(PAR_CARRE_PUIS_POINT);
        return new SoldeSaison(annee, aujourdhui, lignes);
    }

    private LigneSaison ligneDuPoint(Site site, PointDEcoute point, int annee, LocalDate aujourdhui) {
        CasePassage passage1 = casePour(point.id(), annee, 1);
        CasePassage passage2 = casePour(point.id(), annee, 2);
        String reste = resteAFaire(passage1, passage2, annee, aujourdhui);
        return new LigneSaison(site.numeroCarre(), point.code(), point.id(), passage1, passage2, reste);
    }

    private CasePassage casePour(Long idPoint, int annee, int numero) {
        return passageDao
                .trouverParPointAnneePassage(idPoint, annee, numero)
                .map(passage -> CasePassage.de(passage, opportunistes.estOpportuniste(passage.id())))
                .orElseGet(CasePassage::absente);
    }

    /// La phrase « reste à faire » d'un point : l'action du **premier passage non terminé** (passage
    /// 1 puis passage 2). Vide quand les deux passages sont terminés.
    private String resteAFaire(CasePassage passage1, CasePassage passage2, int annee, LocalDate aujourdhui) {
        String action1 = actionPour(passage1, 1, annee, aujourdhui);
        if (!action1.isEmpty()) {
            return action1;
        }
        return actionPour(passage2, 2, annee, aujourdhui);
    }

    private String actionPour(CasePassage cas, int numero, int annee, LocalDate aujourdhui) {
        if (cas.opportuniste()) {
            return ""; // hors protocole (carré d'un tiers) : rien à faire, pas de « poser l'enregistreur »
        }
        if (cas.terminee()) {
            return "";
        }
        if (cas.inexploitable()) {
            return "Refaire le " + ordinal(numero) + " passage";
        }
        if (cas.presente()) {
            return actionWorkflow(cas);
        }
        return actionPoser(numero, annee, aujourdhui);
    }

    /// Action pour une nuit **présente mais pas encore déposée** : l'étape suivante de son workflow.
    private String actionWorkflow(CasePassage cas) {
        String nuit =
                cas.date() == null ? "la nuit" : "la nuit du " + cas.date().format(JOUR_MOIS);
        return switch (cas.statut()) {
            case IMPORTE -> "Transformer " + nuit;
            case TRANSFORME -> "Vérifier " + nuit;
            case VERIFIE -> "Préparer le dépôt de " + nuit;
            case PRET_A_DEPOSER -> "Téléverser " + nuit;
            case DEPOT_EN_COURS -> "Reprendre le dépôt de " + nuit;
            case DEPOSE -> "";
        };
    }

    /// Action pour un passage **absent** : poser l'enregistreur avant la fermeture de sa fenêtre R3
    /// (ou signaler la fenêtre dépassée).
    private String actionPoser(int numero, int annee, LocalDate aujourdhui) {
        Optional<FenetreSaisonniere> fenetre = FenetreSaisonniere.pour(numero, annee);
        if (fenetre.isEmpty()) {
            return "Poser l'enregistreur";
        }
        LocalDate fin = fenetre.get().fin();
        if (aujourdhui.isAfter(fin)) {
            return "Fenêtre du " + ordinal(numero) + " passage dépassée (" + fin.format(JOUR_MOIS) + ")";
        }
        return "Poser l'enregistreur avant le " + fin.format(JOUR_MOIS);
    }

    private static String ordinal(int numero) {
        return numero == 1 ? "1er" : numero + "e";
    }
}
