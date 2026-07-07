package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.cli.model.RegistrePassages;
import fr.univ_amu.iut.cli.model.RegistrePassages.LignePassage;
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
        List<LignePassage> passages = registre.lister();

        if (json) {
            sortie.println(FormatJson.tableau(
                    passages.stream().map(ListerPassages::enObjet).toList()));
            return 0;
        }
        if (passages.isEmpty()) {
            sortie.println("Aucun passage enregistré.");
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
