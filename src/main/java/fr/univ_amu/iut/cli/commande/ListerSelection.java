package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.qualification.model.SequenceEnSelection;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
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

/// `lister-selection` (#1512) : affiche la **sélection d'écoute** d'un passage — chaque séquence
/// échantillonnée avec son fichier, son état d'écoute et son **verdict par fichier** (`Bon` / `Mauvais`
/// / `Inexploitable` / `Non jugé`) — plus le **verdict final proposé** du passage, dérivé de ces
/// verdicts par fichier ([ServiceQualification#verdictDerivePassage]).
///
/// Parité CLI de l'écran M-Qualification (verdict par fichier + final, chantier #1524) : lecture pure,
/// `--json` pour l'enchaînement de scripts. Aucune sélection n'est constituée ici (lecture seule) : la
/// liste est vide tant que la vérification n'a pas ouvert de sélection pour le passage.
@Command(
        name = "lister-selection",
        description = "Affiche la sélection d'écoute d'un passage : verdict par fichier + verdict final proposé.")
public final class ListerSelection implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Passage dont on liste la sélection d'écoute.")
    private Long idPassage;

    @Option(
            names = "--json",
            description = "Émet la sélection au format JSON (pour l'enchaînement de scripts) plutôt qu'en texte.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    private final ServiceQualification service;

    @Inject
    public ListerSelection(ServiceQualification service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        List<SequenceEnSelection> sequences = service.detaillerSelectionParPassage(idPassage);
        Verdict propose = service.verdictDerivePassage(idPassage);

        if (json) {
            Map<String, Object> objet = new LinkedHashMap<>();
            objet.put("passage", idPassage);
            objet.put("verdictFinalPropose", propose.libelle());
            objet.put(
                    "sequences",
                    sequences.stream().map(ListerSelection::enObjet).toList());
            sortie.println(FormatJson.objet(objet));
            return 0;
        }
        if (sequences.isEmpty()) {
            sortie.println("Aucune sélection d'écoute pour le passage #" + idPassage + ".");
            return 0;
        }
        sortie.println("Sélection d'écoute du passage #" + idPassage + " — " + sequences.size()
                + " séquence(s) · verdict final proposé : " + propose.libelle());
        for (SequenceEnSelection sequence : sequences) {
            sortie.println("  N° " + (sequence.position() + 1)
                    + "  " + sequence.sequence().nomFichier()
                    + "  écouté : " + (sequence.ecoutee() ? "oui" : "non")
                    + "  verdict : " + sequence.verdict().libelle());
        }
        return 0;
    }

    /// Projection JSON d'une séquence de la sélection (clés stables pour les scripts).
    private static Map<String, Object> enObjet(SequenceEnSelection sequence) {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("position", sequence.position() + 1);
        objet.put("fichier", sequence.sequence().nomFichier());
        objet.put("ecoutee", sequence.ecoutee());
        objet.put("verdict", sequence.verdict().libelle());
        return objet;
    }
}
