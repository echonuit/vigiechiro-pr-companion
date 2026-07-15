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

/// `corriger-observations` (R16, #1311) : **retenir un autre taxon** que celui proposé par Tadarida, en lot.
///
/// Le pendant du sélecteur de taxon + bouton « ✎ Corriger » de l'écran. Le taxon doit **exister** au
/// référentiel : un code inconnu arrête tout, avant la moindre écriture - une correction en masse vers un
/// taxon qui n'existe pas serait un dégât silencieux.
///
/// La correction est **atomique** : tout le lot passe, ou rien. On ne corrige pas 40 lignes sur 60 en
/// laissant l'utilisateur deviner où ça s'est arrêté.
@Command(
        name = "corriger-observations",
        description = "Corrige en une fois : chaque observation retient le taxon donné à la place de Tadarida (R16).")
public final class CorrigerObservations implements Callable<Integer> {

    @Option(
            names = "--taxon",
            required = true,
            paramLabel = "<code>",
            description = "Code du taxon retenu (ex. Pippip). Doit exister au référentiel.")
    private String taxon;

    @ArgGroup(exclusive = true, multiplicity = "1")
    private CiblesRevue cibles;

    @Spec
    private CommandSpec spec;

    private final Provider<SelectionObservations> selection;
    private final Provider<RevueEnLot> revue;

    @Inject
    public CorrigerObservations(Provider<SelectionObservations> selection, Provider<RevueEnLot> revue) {
        this.selection = Objects.requireNonNull(selection, "selection");
        this.revue = Objects.requireNonNull(revue, "revue");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        List<Long> ids = cibles.resoudre(selection.get());

        int corriges = revue.get().corriger(ids, taxon);

        sortie.println("Corrigé en « " + taxon + " » : " + cibles.description(corriges) + ".");
        return 0;
    }
}
