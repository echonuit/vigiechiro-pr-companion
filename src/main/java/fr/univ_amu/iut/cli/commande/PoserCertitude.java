package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.validation.model.SaisieCertitude;
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

/// `poser-certitude` (#1139, #1311) : déclarer la **certitude observateur** (`SUR` / `PROBABLE` /
/// `POSSIBLE`) sur les observations visées, ou l'**effacer**.
///
/// Le pendant des touches `1`/`2`/`3` de l'écran, et le miroir du champ « Confiance observateur » du site
/// VigieChiro.
///
/// **Elle ne se déduit de rien.** Ni de la probabilité de Tadarida, ni du fait qu'on ait validé : c'est un
/// **jugement**, déclaré à la main, et **vide par défaut**. Une certitude inventée par l'application serait
/// pire que pas de certitude du tout - c'est la valeur que la plateforme exigera avec le taxon au moment
/// de pousser une correction (#723), et un naturaliste la lira comme la parole de l'observateur.
///
/// C'est pourquoi il faut **choisir explicitement** : `--certitude <valeur>` **ou** `--effacer`, jamais un
/// défaut silencieux. Localement, effacer est possible (`certitude = null` = « non renseignée ») ; côté
/// plateforme, une certitude poussée ne se retire pas.
@Command(
        name = "poser-certitude",
        description = "Déclare (ou efface) la certitude observateur des observations visées.")
public final class PoserCertitude implements Callable<Integer> {

    @ArgGroup(exclusive = true, multiplicity = "1")
    private Valeur valeur;

    @ArgGroup(exclusive = true, multiplicity = "1")
    private CiblesRevue cibles;

    @Spec
    private CommandSpec spec;

    /// Poser une valeur, ou effacer : il faut **dire** laquelle. Pas de défaut - une certitude qu'on n'a
    /// pas voulue n'a rien à faire dans les données.
    public static final class Valeur {

        @Option(
                names = "--certitude",
                paramLabel = "<certitude>",
                description = "Certitude déclarée : ${COMPLETION-CANDIDATES}.")
        private Certitude certitude;

        @Option(names = "--effacer", description = "Efface la certitude (la ramène à « non renseignée »).")
        private boolean effacer;

        /// `null` = effacer, c'est la convention de [SaisieCertitude].
        Certitude posee() {
            return effacer ? null : certitude;
        }

        String enClair() {
            return effacer ? "effacée" : certitude.name();
        }
    }

    private final Provider<SelectionObservations> selection;
    private final Provider<SaisieCertitude> saisie;

    @Inject
    public PoserCertitude(Provider<SelectionObservations> selection, Provider<SaisieCertitude> saisie) {
        this.selection = Objects.requireNonNull(selection, "selection");
        this.saisie = Objects.requireNonNull(saisie, "saisie");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        List<Long> ids = cibles.resoudre(selection.get());

        int posees = saisie.get().poser(ids, valeur.posee());

        sortie.println("Certitude « " + valeur.enClair() + " » : " + cibles.description(posees) + ".");
        return 0;
    }
}
