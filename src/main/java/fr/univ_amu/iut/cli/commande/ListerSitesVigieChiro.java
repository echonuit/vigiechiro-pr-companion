package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.cli.LectureSeule;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.LotPagine;
import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.Besoin;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `lister-sites-vigiechiro` (#3003) : interroge le **catalogue des sites de la plateforme**, ou les
/// vôtres, et sait les **recenser** plutôt que de rendre du JSON à recompter ailleurs.
///
/// ## Ce qu'elle répond
///
/// « Combien de carrés portent un point nommé `Z1` ? » se répondait jusqu'ici avec `curl` et un script
/// jetable. Avec `--recenser`, la commande le dit : `Z1 288 96,0 %`.
///
/// ## Ce qu'elle ne cache pas
///
/// Le catalogue fait plus de deux cents pages. Lire un **échantillon** est légitime, l'annoncer comme
/// un tout ne l'est pas : chaque sortie porte donc son dénominateur (« 300 site(s) lus sur 20572
/// annoncés »), et la sortie `--json` est une **enveloppe** plutôt qu'un tableau nu - un script qui lit
/// la sortie standard ne verrait jamais un avertissement posé ailleurs.
///
/// À distinguer de `lister-sites`, qui liste vos sites **en base locale** : celle-ci parle au serveur.
@Command(
        name = "lister-sites-vigiechiro",
        description = "Interroge le catalogue des sites de Vigie-Chiro (les vôtres, ou toute la plateforme).")
public final class ListerSitesVigieChiro implements Callable<Integer>, LectureSeule {

    /// Portée de la lecture : vos sites (dérivés de vos participations) ou le catalogue entier.
    public enum Portee {
        MES,
        PLATEFORME
    }

    /// Combien de pages du catalogue lire. Les deux s'excluent ; sans l'un ni l'autre, **une** page.
    private static final class Etendue {

        @Option(
                names = "--pages",
                paramLabel = "<n>",
                description = "Nombre de pages à lire (100 sites par page). Défaut : 1.")
        private Integer pages;

        @Option(names = "--tout", description = "Lit la collection entière (plus de 200 pages, 1 à 2 minutes).")
        private boolean tout;
    }

    @Option(
            names = "--portee",
            paramLabel = "<portee>",
            defaultValue = "MES",
            description = "Sites à lire : ${COMPLETION-CANDIDATES}. Défaut : ${DEFAULT-VALUE}.")
    private Portee portee;

    @ArgGroup(multiplicity = "0..1")
    private Etendue etendue;

    @Option(names = "--point", paramLabel = "<code>", description = "Ne garde que les sites portant ce code de point.")
    private String point;

    @Option(names = "--carre", paramLabel = "<numero>", description = "Ne garde que les sites de ce carré.")
    private String carre;

    @Option(names = "--recenser", description = "Compte les sites par code de point, au lieu de les lister un par un.")
    private boolean recenser;

    @Option(names = "--json", description = "Émet une enveloppe JSON (contenu + ce qui a été lu) plutôt qu'un texte.")
    private boolean json;

    @Option(names = "--token", paramLabel = "<jeton>", description = "Jeton VigieChiro, s'il n'est pas enregistré.")
    private String token;

    @Spec
    private CommandSpec spec;

    private final ClientVigieChiro client;

    /// Le client HTTP n'ouvre pas la base : il s'injecte directement, sans `Provider` (celui-ci est
    /// réservé aux dépendances qui la touchent, picocli instanciant les commandes avant la migration).
    @Inject
    public ListerSitesVigieChiro(ClientVigieChiro client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public Integer call() {
        refuserEtendueAvecCarre();
        if (token != null && !token.isBlank()) {
            System.setProperty("vigiechiro.token", token);
        }
        LotPagine<SiteVigieChiro> lot = lire();
        List<SiteVigieChiro> retenus = filtrer(lot.elements());
        PrintWriter sortie = spec.commandLine().getOut();

        if (json) {
            sortie.println(FormatJson.objet(enveloppe(lot, retenus)));
            return 0;
        }
        if (recenser) {
            ecrireRecensement(sortie, retenus);
        } else {
            ecrireSites(sortie, retenus);
        }
        sortie.println();
        sortie.println(bilan(lot, retenus));
        return 0;
    }

    /// Refuse `--pages` / `--tout` quand `--carre` est présent (#3769).
    ///
    /// La recherche par carré interroge **toute** la collection en une requête : borner son étendue n'a
    /// plus de sens. Accepter l'option en l'ignorant serait précisément ce que ce backend fait de
    /// `where=` - un paramètre honoré en apparence, sans effet en fait - et ce que le dépôt lui reproche.
    private void refuserEtendueAvecCarre() {
        if (carre != null && etendue != null) {
            throw new RegleMetierException("« --carre » cherche sur toute la collection, en une requête :"
                    + " « --pages » et « --tout » n'ont donc rien à borner. Relancez sans eux.");
        }
    }

    /// Lit la portée demandée. Les sites de l'utilisateur arrivent toujours complets (le client parcourt
    /// toutes les pages) ; le catalogue, lui, s'arrête où on le lui a dit.
    private LotPagine<SiteVigieChiro> lire() {
        if (portee == Portee.MES) {
            List<SiteVigieChiro> miens = exiger(client.mesSites(), "la liste de vos sites");
            return new LotPagine<>(miens, miens.size(), 1, true);
        }
        if (carre != null) {
            return chercherLeCarre();
        }
        int pages = etendue == null ? 1 : (etendue.tout ? Integer.MAX_VALUE : Math.max(1, etendue.pages));
        return exiger(client.sitesPlateforme(pages, this::annoncerPage), "le catalogue des sites");
    }

    /// Un carré se demande **au serveur** (#3769), en une requête : `q` filtre réellement, mesuré le
    /// 2026-08-14 (20 767 → 1). Paginer deux cents pages pour filtrer ensuite chez nous coûtait une à
    /// deux minutes, et surtout **manquait le carré** dès qu'il ne tombait pas dans la page lue.
    ///
    /// Le lot rendu est **complet par construction** : la recherche porte sur la collection entière,
    /// donc le dénominateur du bilan est celui de la recherche, pas celui du catalogue.
    private LotPagine<SiteVigieChiro> chercherLeCarre() {
        List<SiteVigieChiro> trouves = exiger(client.chercherCarre(carre), "la recherche du carré " + carre);
        return new LotPagine<>(trouves, trouves.size(), 1, true);
    }

    /// La progression va sur la **sortie d'erreur** : la sortie standard reste le compte rendu, qu'un
    /// script doit pouvoir lire d'un bloc.
    private void annoncerPage(int page, int totalPages) {
        spec.commandLine().getErr().println("page " + page + (totalPages > 0 ? "/" + totalPages : ""));
    }

    /// Filtres appliqués **chez nous**, sur ce qui a été lu : ce backend accepte puis ignore `where=`,
    /// et un filtre serveur qui ne filtre pas serait pire qu'absent.
    private List<SiteVigieChiro> filtrer(List<SiteVigieChiro> sites) {
        return sites.stream()
                .filter(site -> carre == null || carre.equals(site.numeroCarre()))
                .filter(site -> point == null || porteLePoint(site))
                .toList();
    }

    private boolean porteLePoint(SiteVigieChiro site) {
        return site.points().stream().map(PointVigieChiro::code).anyMatch(code -> point.equalsIgnoreCase(code));
    }

    private void ecrireSites(PrintWriter sortie, List<SiteVigieChiro> sites) {
        sortie.printf("%-8s %-26s %4s  %s%n", "CARRE", "SITE", "PTS", "TITRE");
        for (SiteVigieChiro site : sites) {
            sortie.printf(
                    "%-8s %-26s %4d  %s%n",
                    site.numeroCarre() == null ? "-" : site.numeroCarre(),
                    site.id(),
                    site.points().size(),
                    site.titre() == null ? "-" : site.titre());
        }
    }

    private void ecrireRecensement(PrintWriter sortie, List<SiteVigieChiro> sites) {
        List<RecensementPoints.Ligne> lignes = RecensementPoints.de(sites);
        int recenses = RecensementPoints.sitesRecenses(sites);
        sortie.printf("%-12s %6s %8s%n", "CODE", "SITES", "PART");
        for (RecensementPoints.Ligne ligne : lignes) {
            sortie.printf("%-12s %6d %7s%n", ligne.code(), ligne.sites(), part(ligne.sites(), Math.max(recenses, 1)));
        }
    }

    /// La part, **des sites recensés** : c'est l'en-tête et le bilan qui disent sur quoi elle porte.
    private static String part(int nombre, int total) {
        return String.format(Locale.FRENCH, "%.1f %%", 100.0 * nombre / total);
    }

    /// La ligne qui distingue un **échantillon** d'un recensement. Sans elle, trois pages lues sur
    /// deux cent six passeraient pour la plateforme entière. Elle dit aussi combien de sites n'ont
    /// **aucun point** exploitable, faute de quoi un recensement les passerait sous silence.
    private String bilan(LotPagine<SiteVigieChiro> lot, List<SiteVigieChiro> retenus) {
        if (carre != null && portee == Portee.PLATEFORME) {
            return bilanRecherche(retenus);
        }
        String filtres = retenus.size() == lot.elements().size() ? "" : retenus.size() + " site(s) retenu(s) sur ";
        String etendue = lot.complet()
                ? lot.elements().size() + " site(s) lu(s) : collection complète."
                : lot.elements().size() + " site(s) lu(s) sur " + lot.totalAnnonce() + " annoncés ("
                        + lot.pagesLues() + " page(s) sur " + lot.pagesAnnoncees()
                        + "). Échantillon : ces chiffres ne valent que pour lui.";
        return filtres + etendue + sansPoint(retenus);
    }

    /// Le bilan d'une **recherche** (#3769), qui n'est pas celui d'une lecture partielle.
    ///
    /// « 1 site lu : collection complète » serait exact et pourtant trompeur : on n'a pas parcouru la
    /// collection, on a posé une question au serveur, qui a répondu sur **toute** la collection. Dire
    /// laquelle, et sur quoi la réponse porte, est ce que la garde de #1277 demande - un échantillon ne
    /// doit jamais passer pour un recensement, ni l'inverse.
    private String bilanRecherche(List<SiteVigieChiro> retenus) {
        return "Recherche du carré " + carre + " sur toute la collection : " + retenus.size() + " site(s) trouvé(s)."
                + sansPoint(retenus);
    }

    /// Les sites **sans point d'écoute** exploitable, quand il y en a : sur le protocole routier, les
    /// localités sont des **tronçons de transect** (géométrie linéaire) et non des points fixes ; elles
    /// n'entrent donc pas dans un recensement de codes de points. Le dire vaut mieux que sous-compter
    /// en silence - sur les premières pages du catalogue, ces sites sont la majorité.
    ///
    /// Le compte est le **complément exact** du dénominateur des parts : ce qui est annoncé « hors du
    /// recensement » doit être précisément ce que le recensement ne divise pas, sinon les deux
    /// nombres affichés côte à côte ne s'additionnent plus.
    private static String sansPoint(List<SiteVigieChiro> sites) {
        int recenses = RecensementPoints.sitesRecenses(sites);
        int muets = sites.size() - recenses;
        return muets == 0
                ? ""
                : " Dont " + muets + " site(s) sans point d'écoute ponctuel (transects routiers), "
                        + "hors du recensement : les parts portent sur les " + recenses + " autre(s).";
    }

    /// Enveloppe JSON : le contenu **et** ce qui a été lu. Un tableau nu obligerait le script à croire
    /// que ce qu'il reçoit est tout ce qui existe.
    private Map<String, Object> enveloppe(LotPagine<SiteVigieChiro> lot, List<SiteVigieChiro> retenus) {
        Map<String, Object> champs = new LinkedHashMap<>();
        champs.put("portee", portee.name().toLowerCase(Locale.ROOT));
        champs.put("sitesLus", lot.elements().size());
        champs.put("sitesRetenus", retenus.size());
        champs.put("totalAnnonce", lot.totalAnnonce());
        champs.put("pagesLues", lot.pagesLues());
        champs.put("pagesAnnoncees", lot.pagesAnnoncees());
        champs.put("complet", lot.complet());
        champs.put(
                "sitesSansPoint",
                retenus.stream().filter(site -> site.points().isEmpty()).count());
        if (recenser) {
            champs.put("points", lignesRecensement(retenus));
        } else {
            champs.put("sites", lignesSites(retenus));
        }
        return champs;
    }

    private static List<Map<String, Object>> lignesSites(List<SiteVigieChiro> sites) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (SiteVigieChiro site : sites) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("site", site.id());
            ligne.put("carre", site.numeroCarre());
            ligne.put("titre", site.titre());
            ligne.put("verrouille", site.verrouille());
            ligne.put("observateur", site.observateur());
            ligne.put(
                    "points", site.points().stream().map(PointVigieChiro::code).toList());
            lignes.add(ligne);
        }
        return lignes;
    }

    private static List<Map<String, Object>> lignesRecensement(List<SiteVigieChiro> sites) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        int total = Math.max(RecensementPoints.sitesRecenses(sites), 1);
        for (RecensementPoints.Ligne ligne : RecensementPoints.de(sites)) {
            Map<String, Object> champs = new LinkedHashMap<>();
            champs.put("code", ligne.code());
            champs.put("sites", ligne.sites());
            champs.put("part", Math.round(1000.0 * ligne.sites() / total) / 1000.0);
            lignes.add(champs);
        }
        return lignes;
    }

    /// Traduit une issue d'API en valeur, ou en **refus motivé** (code 2). Même patron que
    /// [fr.univ_amu.iut.passage.model.PlateformeReconstruction] : chaque variante dit ce qui a manqué,
    /// et l'absence de jeton porte le [Besoin] dont la CLI tirera le geste à taper.
    private static <T> T exiger(ReponseApi<T> reponse, String quoi) {
        return switch (reponse) {
            case ReponseApi.Succes<T>(T valeur) -> valeur;
            case ReponseApi.NonConnecte<T> ignore ->
                throw new RegleMetierException(
                        "Non connecté à Vigie-Chiro : " + quoi + " n'a pas pu être lu.", new Besoin.Connexion());
            case ReponseApi.Injoignable<T>(String cause) ->
                throw new RegleMetierException(
                        "Vigie-Chiro est injoignable (" + cause + ") : " + quoi + " n'a pas pu être lu.");
            case ReponseApi.Refuse<T>(int statut, String corps) ->
                throw new RegleMetierException(
                        "Vigie-Chiro a refusé de rendre " + quoi + " (HTTP " + statut + " : " + corps + ").");
        };
    }
}
