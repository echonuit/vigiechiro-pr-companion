package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.cli.model.RegistrePassages;
import fr.univ_amu.iut.cli.model.RegistrePassages.LignePassage;
import fr.univ_amu.iut.commun.model.LieuQualifie;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.multisite.model.EtatAnalyse;
import fr.univ_amu.iut.multisite.model.FiltresMultisite;
import fr.univ_amu.iut.validation.model.FiltresLieu;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `lister-passages` (P5) : liste les passages enregistrés avec leur contexte (carré, point, année, statut,
/// verdict). Lecture pure via [RegistrePassages]. Option `--json` pour une sortie exploitable en script.
@Command(
        name = "lister-passages",
        description = "Liste les passages enregistrés (carré, point, année, statut, verdict).")
public final class ListerPassages implements Callable<Integer> {

    @Option(names = "--carre", description = "Ne garde que les passages de ce carré (n° exact).")
    private String carre;

    @Option(names = "--annee", description = "Ne garde que les passages de cette année.")
    private Integer annee;

    @Option(names = "--statut", description = "Ne garde que ce statut de workflow : ${COMPLETION-CANDIDATES}.")
    private StatutWorkflow statut;

    @Option(names = "--verdict", description = "Ne garde que ce verdict de vérification : ${COMPLETION-CANDIDATES}.")
    private Verdict verdict;

    @Option(
            names = "--lieu",
            description = "Ne garde que les passages d'un lieu : commune, n° de carré, nom du carré ou"
                    + " « carré · point ». Répétable ; un passage est retenu dès qu'un des lieux correspond.")
    private List<String> lieux;

    @Option(
            names = "--campagne",
            description = "Ne garde que les passages d'une campagne (fragment du nom, insensible à la casse).")
    private String campagne;

    @Option(names = "--analyse", description = "Ne garde que cet état d'analyse : ${COMPLETION-CANDIDATES}.")
    private EtatAnalyse analyse;

    @Option(
            names = "--json",
            description = "Émet la liste au format JSON (pour l'enchaînement de scripts) plutôt qu'en texte.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    private final RegistrePassages registre;

    @Inject
    public ListerPassages(RegistrePassages registre) {
        this.registre = Objects.requireNonNull(registre, "registre");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        List<LignePassage> toutes = registre.lister();
        // Constater la base vide AVANT de filtrer : `--lieu` refuse un lieu absent des lignes (ADR 3082),
        // et sur une base sans aucun passage il refuserait donc TOUT lieu - en disant « ce lieu n'existe
        // pas » là où la vérité est « il n'y a aucun passage ».
        if (toutes.isEmpty()) {
            sortie.println(json ? "[]" : "Aucun passage enregistré.");
            return 0;
        }
        List<LignePassage> passages = restreindre(toutes);

        if (json) {
            sortie.println(FormatJson.tableau(
                    passages.stream().map(ListerPassages::enObjet).toList()));
            return 0;
        }
        if (passages.isEmpty()) {
            // Un filtre qui ne retient rien se DIT. Sans cette distinction, « Aucun passage enregistré »
            // s'afficherait sur une base qui en porte, et le filtre ferait passer une base peuplée pour
            // une base vide.
            sortie.println("Aucun passage ne correspond aux filtres (" + toutes.size() + " passage(s) au total).");
            return 0;
        }
        sortie.println(passages.size() + " passage(s) :");
        for (LignePassage ligne : passages) {
            sortie.println("  #" + ligne.idPassage()
                    + "  carré " + ligne.carre()
                    + "  point " + ligne.codePoint()
                    + "  " + ligne.annee() + " passage " + ligne.numeroPassage()
                    + "  [" + ligne.statut().libelle() + "]"
                    + "  verdict : "
                    + (ligne.verdict() == null ? "-" : ligne.verdict().libelle()));
        }
        return 0;
    }

    /// Projection JSON d'un passage (clés stables pour les scripts).
    /// Applique les filtres au moyen du **même prédicat que l'écran** « Carte & passages » :
    /// `FiltresMultisite` est un record portant tous les critères, dont `accepte` ignore ceux qui sont
    /// `null`. Aucune règle n'est donc réécrite ici (#3269).
    ///
    /// Les **sept** critères de l'écran sont désormais offerts : `RegistrePassages` a gagné les trois
    /// dimensions qui lui manquaient (commune, campagne, état d'analyse), plutôt que la commande ne
    /// change de source de données.
    private List<LignePassage> restreindre(List<LignePassage> passages) {
        FiltresMultisite filtres = new FiltresMultisite(carre, statut, verdict, annee, analyse, campagne);
        List<LignePassage> retenus =
                passages.stream().filter(ligne -> accepte(filtres, ligne)).toList();
        // Le lieu se filtre à part : c'est le seul critère à porter PLUSIEURS dimensions (commune, carré,
        // nom du carré, point), et la règle en vit dans `FiltresLieu`, lue aussi par lister-observations
        // et exporter-activite. Une seconde écriture ici finirait par en diverger.
        return FiltresLieu.parLieu(retenus, lieux, ListerPassages::dimensionsDuLieu);
    }

    /// Les noms sous lesquels un passage se laisse désigner par `--lieu`, dans l'ordre où l'écran les
    /// propose : la commune, le carré (numéro puis nom), et le point **qualifié par son carré** - un code
    /// de point seul désigne autant de lieux qu'il y a de carrés (#2992).
    private static List<String> dimensionsDuLieu(LignePassage ligne) {
        List<String> noms = new ArrayList<>();
        if (ligne.commune() != null) {
            noms.add(ligne.commune());
        }
        noms.add(ligne.carre());
        if (ligne.nomSite() != null) {
            noms.add(ligne.nomSite());
        }
        noms.add(LieuQualifie.qualifier(ligne.carre(), ligne.codePoint()));
        return noms;
    }

    /// Traduit la ligne de la CLI vers celle que `FiltresMultisite` sait juger. Les deux décrivent le
    /// même passage, sous deux projections : celle de la commande ne porte pas la carte.
    private static boolean accepte(FiltresMultisite filtres, LignePassage ligne) {
        return filtres.accepte(new fr.univ_amu.iut.multisite.model.LignePassage(
                ligne.idPassage(),
                ligne.carre(),
                ligne.codePoint(),
                ligne.annee(),
                ligne.numeroPassage(),
                null,
                ligne.statut(),
                ligne.verdict(),
                ligne.etatAnalyse(),
                null,
                ligne.campagne(),
                ligne.commune(),
                ligne.nomSite()));
    }

    private static Map<String, Object> enObjet(LignePassage ligne) {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("passage", ligne.idPassage());
        objet.put("carre", ligne.carre());
        objet.put("point", ligne.codePoint());
        objet.put("annee", ligne.annee());
        objet.put("numeroPassage", ligne.numeroPassage());
        objet.put("statut", ligne.statut().libelle());
        objet.put("verdict", ligne.verdict() == null ? null : ligne.verdict().libelle());
        return objet;
    }
}
