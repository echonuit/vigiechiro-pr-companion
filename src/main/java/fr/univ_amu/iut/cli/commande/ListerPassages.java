package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.cli.model.RegistrePassages;
import fr.univ_amu.iut.cli.model.RegistrePassages.LignePassage;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.multisite.model.FiltresMultisite;
import java.io.PrintWriter;
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
        List<LignePassage> passages = restreindre(toutes);

        if (json) {
            sortie.println(FormatJson.tableau(
                    passages.stream().map(ListerPassages::enObjet).toList()));
            return 0;
        }
        if (toutes.isEmpty()) {
            sortie.println("Aucun passage enregistré.");
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
    /// ⚠️ Trois des sept critères de l'écran manquent encore : **Lieu**, **Analyse** et **Campagne**.
    /// Ce n'est pas un oubli d'ergonomie mais une limite des **données** : la ligne que
    /// [fr.univ_amu.iut.cli.model.RegistrePassages] compose ne porte ni la commune du point, ni l'état
    /// d'analyse, ni la campagne du passage. Les ajouter demande d'étendre cette lecture, pas d'ajouter
    /// une option.
    private List<LignePassage> restreindre(List<LignePassage> passages) {
        FiltresMultisite filtres = new FiltresMultisite(carre, statut, verdict, annee, null, null);
        return passages.stream().filter(ligne -> accepte(filtres, ligne)).toList();
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
                null,
                null,
                null,
                null,
                null));
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
