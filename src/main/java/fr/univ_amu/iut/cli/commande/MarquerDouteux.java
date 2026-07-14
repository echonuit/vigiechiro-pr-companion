package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.SelectionObservations;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `marquer-douteux` (#160, #1311) : lever (ou baisser) le drapeau **« observation douteuse »**, en lot.
///
/// Le pendant de la touche `D` de l'écran. Ce drapeau ne dit **rien** du taxon : il dit *« je ne sais
/// pas »*, ce qui n'est ni valider ni corriger. C'est une **troisième** réponse, et c'est précisément
/// pourquoi elle a sa propre commande plutôt qu'une valeur de taxon bidon.
///
/// Le geste est **réversible** (`--retirer`), contrairement à un message posté : rien ici ne part sur la
/// plateforme.
@Command(
        name = "marquer-douteux",
        description = "Marque (ou retire, avec --retirer) le drapeau « douteuse » sur les observations visées.")
public final class MarquerDouteux implements Callable<Integer> {

    @Option(names = "--retirer", description = "Retire le drapeau au lieu de le poser (le geste est réversible).")
    private boolean retirer;

    @ArgGroup(exclusive = true, multiplicity = "1")
    private CiblesRevue cibles;

    @Spec
    private CommandSpec spec;

    private final Provider<SelectionObservations> selection;
    private final Provider<RevueEnLot> revue;

    @Inject
    public MarquerDouteux(Provider<SelectionObservations> selection, Provider<RevueEnLot> revue) {
        this.selection = Objects.requireNonNull(selection, "selection");
        this.revue = Objects.requireNonNull(revue, "revue");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        List<Long> ids = cibles.resoudre(selection.get());

        int traitees = revue.get().marquerDouteux(ids, !retirer);

        sortie.println((retirer ? "Drapeau « douteuse » retiré : " : "Marqué « douteuse » : ")
                + cibles.description(traitees) + ".");
        return 0;
    }
}
