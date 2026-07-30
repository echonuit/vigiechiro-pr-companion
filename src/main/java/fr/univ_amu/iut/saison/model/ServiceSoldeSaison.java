package fr.univ_amu.iut.saison.model;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.FenetreSaisonniere;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
/// **Les nuits opportunistes ont leur propre colonne.** Une nuit hors protocole ne prend pas la place
/// du passage protocolaire correspondant : les colonnes « passage 1 » et « passage 2 » ne montrent que
/// ce qui compte, et [LigneSaison#horsProtocole] porte le reste. Sinon une case remplie et un « reste à
/// faire » qui réclame ce même passage se contrediraient sur la même ligne.
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
    /// carrés : ceux d'un autre observateur n'engagent aucune obligation de protocole.
    private final SiteTiersDao carresDeTiers;

    /// Campagnes (#2355), **optionnelles** : la feature est désactivable. Absente, aucun point ne relève
    /// d'une campagne : le solde complet reste entier, seul le filtre par campagne ne retient rien.
    private final Optional<ServiceCampagne> campagnes;

    private final Horloge horloge;

    public ServiceSoldeSaison(
            SiteDao siteDao,
            PointDao pointDao,
            PassageDao passageDao,
            PassageOpportunisteDao opportunistes,
            SiteTiersDao carresDeTiers,
            Optional<ServiceCampagne> campagnes,
            Horloge horloge) {
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.opportunistes = Objects.requireNonNull(opportunistes, "opportunistes");
        this.carresDeTiers = Objects.requireNonNull(carresDeTiers, "carresDeTiers");
        this.campagnes = Objects.requireNonNull(campagnes, "campagnes");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    /// Solde de la **saison courante** (année de l'horloge) pour l'observateur `idUtilisateur`.
    public SoldeSaison soldeCourant(String idUtilisateur) {
        return soldeCourant(idUtilisateur, null);
    }

    /// Solde de la **saison courante**, restreint à une campagne (#2355) ; `campagne` nul = tout le solde.
    /// Évite à l'appelant (CLI, IHM) d'avoir à connaître l'année du jour pour combiner les deux.
    public SoldeSaison soldeCourant(String idUtilisateur, String campagne) {
        return soldePour(idUtilisateur, horloge.aujourdhui().getYear(), campagne);
    }

    /// Solde de la saison `annee` pour l'observateur `idUtilisateur` : une ligne par point suivi de
    /// ses sites PointFixeStandard, triée par carré puis par code de point (ordre déterministe).
    public SoldeSaison soldePour(String idUtilisateur, int annee) {
        return soldePour(idUtilisateur, annee, null);
    }

    /// Solde de la saison `annee`, **restreint à une campagne** (#2355) : ne sont retenus que les points
    /// dont au moins un des deux passages relève de `campagne` (correspondance partielle, insensible à la
    /// casse, comme dans « Carte & passages »). Répond à « où en est ma campagne ? ».
    ///
    /// Le point retenu est montré **en entier** (ses deux passages et son « reste à faire »), même si
    /// l'un d'eux n'appartient pas à la campagne : c'est l'état du point qui dit ce qu'il reste à y faire.
    ///
    /// @param campagne fragment du nom de campagne, ou `null` pour ne pas restreindre (toutes les nuits,
    ///     rattachées ou non)
    public SoldeSaison soldePour(String idUtilisateur, int annee, String campagne) {
        Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        LocalDate aujourdhui = horloge.aujourdhui();
        List<LigneSaison> lignes = new ArrayList<>();
        // Lecture groupée : un seul accès pour écarter tous les carrés de tiers (#2525), un autre pour
        // résoudre les noms de campagne (#2355) : pas une requête par point.
        Set<Long> tiers = carresDeTiers.tousLesIds();
        Map<Long, String> nomsCampagnes = nomsDesCampagnes();
        for (Site site : siteDao.findByUtilisateur(idUtilisateur)) {
            if (site.protocole() != Protocole.STANDARD || tiers.contains(site.id())) {
                continue;
            }
            for (PointDEcoute point : pointDao.findBySite(site.id())) {
                LigneSaison ligne = ligneDuPoint(site, point, annee, aujourdhui, nomsCampagnes);
                if (releveDeLaCampagne(ligne, campagne)) {
                    lignes.add(ligne);
                }
            }
        }
        // Tri **défensif** : les deux DAO trient déjà en SQL (`ORDER BY square_number`, `ORDER BY code`),
        // donc la boucle imbriquée produit naturellement cet ordre-là. Le retirer ne serait aujourd'hui
        // observable par aucun test (mutant équivalent par construction, constaté au PIT de clôture
        // #2349). Il reste, pour que l'ordre annoncé par cet écran soit garanti ICI plutôt que dépendre
        // de deux clauses SQL vivant dans une autre feature.
        lignes.sort(PAR_CARRE_PUIS_POINT);
        return new SoldeSaison(annee, aujourdhui, lignes);
    }

    /// Campagnes proposables au filtre de l'écran (#2610), vide si la fonctionnalité est coupée.
    ///
    /// Exposée par **ce** service, et non par un second injecté dans le ViewModel : l'écran ne connaît
    /// qu'un interlocuteur, et la liste qu'il propose vient de la même source que le filtre qui
    /// l'applique. Une liste et un filtre nourris par deux chemins finiraient par diverger.
    public List<Campagne> campagnesProposables() {
        return campagnes.map(ServiceCampagne::listerCampagnes).orElseGet(List::of);
    }

    /// Noms des campagnes par identifiant, lus **une seule fois** par solde. Vide si la feature `campagne`
    /// est coupée : aucun point ne relève alors d'une campagne, et un filtre renseigné ne retient rien.
    private Map<Long, String> nomsDesCampagnes() {
        return campagnes
                .map(service ->
                        service.listerCampagnes().stream().collect(Collectors.toMap(Campagne::id, Campagne::nom)))
                .orElseGet(Map::of);
    }

    /// Le point est-il retenu par le filtre `campagne` ? Sans filtre, tous le sont. Avec, il faut qu'**au
    /// moins un** des deux passages porte une campagne dont le nom contient le fragment demandé.
    private boolean releveDeLaCampagne(LigneSaison ligne, String campagne) {
        if (campagne == null || campagne.isBlank()) {
            return true;
        }
        String fragment = campagne.toLowerCase(Locale.ROOT);
        // Toutes les nuits du point comptent, y compris hors protocole : une campagne peut parfaitement
        // regrouper des participations opportunistes.
        return ligne.toutesLesCases().anyMatch(cas -> correspond(cas.campagne(), fragment));
    }

    private static boolean correspond(String nomCampagne, String fragmentEnMinuscules) {
        return nomCampagne != null && nomCampagne.toLowerCase(Locale.ROOT).contains(fragmentEnMinuscules);
    }

    private LigneSaison ligneDuPoint(
            Site site, PointDEcoute point, int annee, LocalDate aujourdhui, Map<Long, String> nomsCampagnes) {
        CasePassage nuit1 = casePour(point.id(), annee, 1, nomsCampagnes);
        CasePassage nuit2 = casePour(point.id(), annee, 2, nomsCampagnes);
        // Les nuits opportunistes quittent les colonnes protocolaires (#2525) : y laisser une pastille
        // ferait lire « ce passage est fait » là où le passage protocolaire manque, pendant que le
        // « reste à faire » dit d'aller poser l'enregistreur. Elles ont leur propre colonne.
        List<CasePassage> horsProtocole =
                Stream.of(nuit1, nuit2).filter(CasePassage::opportuniste).toList();
        CasePassage passage1 = nuit1.opportuniste() ? CasePassage.absente() : nuit1;
        CasePassage passage2 = nuit2.opportuniste() ? CasePassage.absente() : nuit2;
        String reste = resteAFaire(passage1, passage2, annee, aujourdhui);
        return new LigneSaison(site.numeroCarre(), point.code(), point.id(), passage1, passage2, horsProtocole, reste);
    }

    private CasePassage casePour(Long idPoint, int annee, int numero, Map<Long, String> nomsCampagnes) {
        return passageDao
                .trouverParPointAnneePassage(idPoint, annee, numero)
                .map(passage -> CasePassage.de(
                        passage,
                        opportunistes.estOpportuniste(passage.id()),
                        passage.idCampagne() == null ? null : nomsCampagnes.get(passage.idCampagne())))
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
        // Précédence délibérée sur `inexploitable()` ci-dessous : un passage déposé ne se redemande pas,
        // même si son verdict le disqualifiait. Le cas est aujourd'hui **inatteignable** - deux gardes
        // vivant ailleurs l'interdisent (`ServiceLot.exigerDeposable` refuse de déposer un inexploitable,
        // R14 ; `ServicePassage.poserVerdict` fige le verdict une fois déposé). Le contrôle reste ici pour
        // que le solde énonce sa règle plutôt que de l'emprunter à deux autres features (constaté au PIT
        // de clôture #2349 : mutant survivant, équivalent tant que ces gardes tiennent).
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
            // Une nuit récupérée de Vigie-Chiro (#2581) n'a rien à faire avancer dans ce workflow :
            // elle est déjà déposée là-bas. Ce qui lui manque, c'est son audio - et le geste qui le lui
            // rend est la réactivation, depuis sa fiche.
            case RECUPERE -> "Réactiver " + nuit;
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
