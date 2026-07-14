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

/// `marquer-reference` (#1311) : verser (ou retirer) des observations à la **bibliothèque de sons de
/// référence**, en lot.
///
/// Le pendant du bouton « ⭐ Marquer référence » et de la touche `R`. La bibliothèque n'est pas un écran
/// séparé : c'est une **source** de « Sons & validation », et ce drapeau est ce qui l'alimente. C'est aussi
/// lui que consomme l'export de la bibliothèque (P10).
@Command(
        name = "marquer-reference",
        description = "Verse (ou retire, avec --retirer) les observations visées dans la bibliothèque de "
                + "sons de référence.")
public final class MarquerReference implements Callable<Integer> {

    @Option(names = "--retirer", description = "Retire de la bibliothèque au lieu d'y verser.")
    private boolean retirer;

    @ArgGroup(exclusive = true, multiplicity = "1")
    private CiblesRevue cibles;

    @Spec
    private CommandSpec spec;

    private final Provider<SelectionObservations> selection;
    private final Provider<RevueEnLot> revue;

    @Inject
    public MarquerReference(Provider<SelectionObservations> selection, Provider<RevueEnLot> revue) {
        this.selection = Objects.requireNonNull(selection, "selection");
        this.revue = Objects.requireNonNull(revue, "revue");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        List<Long> ids = cibles.resoudre(selection.get());

        int traitees = revue.get().marquerReference(ids, !retirer);

        sortie.println((retirer ? "Retiré de la bibliothèque : " : "Versé en référence : ")
                + cibles.description(traitees) + ".");
        return 0;
    }
}
