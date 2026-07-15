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
import picocli.CommandLine.Spec;

/// `valider-observations` (R15, #1311) : **accepter la proposition de Tadarida**, en lot.
///
/// Le pendant du bouton « ✔ Valider » de l'écran, et de la touche `Entrée`. L'observation retient le taxon
/// que Tadarida a proposé, en mode `manuel` - c'est ce `manuel` qui distingue « un humain a regardé et a
/// dit oui » de « personne n'a encore rien dit ».
///
/// **Mode Activité uniquement.** L'écran offre aussi un mode *Inventaire*, où valider une espèce **propage**
/// la décision à d'autres lignes. La CLI ne le propose **pas**, et c'est délibéré : propager, c'est toucher
/// des lignes que l'utilisateur n'a **pas** désignées. À l'écran il les voit se cocher ; dans un script,
/// il ne verrait rien. Le lot en ligne de commande traite **exactement** les lignes visées.
@Command(
        name = "valider-observations",
        description = "Valide en une fois : chaque observation retient la proposition de Tadarida (R15).")
public final class ValiderObservations implements Callable<Integer> {

    @ArgGroup(exclusive = true, multiplicity = "1")
    private CiblesRevue cibles;

    @Spec
    private CommandSpec spec;

    private final Provider<SelectionObservations> selection;
    private final Provider<RevueEnLot> revue;

    @Inject
    public ValiderObservations(Provider<SelectionObservations> selection, Provider<RevueEnLot> revue) {
        this.selection = Objects.requireNonNull(selection, "selection");
        this.revue = Objects.requireNonNull(revue, "revue");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        List<Long> ids = cibles.resoudre(selection.get());

        int valides = revue.get().valider(ids);

        sortie.println("Validé : " + cibles.description(valides) + ".");
        return 0;
    }
}
