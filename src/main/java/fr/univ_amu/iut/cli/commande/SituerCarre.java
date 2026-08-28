package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.LectureSeule;
import fr.univ_amu.iut.sites.model.PropositionCarre;
import fr.univ_amu.iut.sites.model.VerdictProposition;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `situer-carre` (#4660) : la **facette CLI** du geste « Situer » de la modale de déclaration.
///
/// L'écran sait déduire un carré d'une position depuis #4577 ; la ligne de commande ne le savait pas,
/// ce que l'article A19 n'autorise pas. L'écart a été constaté à la passe 2 de la clôture du chantier
/// #4573, et comblé là plutôt que reporté.
///
/// **Aucun réseau, aucun jeton** : le carroyage national est embarqué. C'est ce qui rend cette commande
/// utile à un script - traiter un lot de nuits dont on a les positions GPS, sans ouvrir l'application.
///
/// Trois issues, et deux ne rendent aucun numéro : une frontière rend les candidats et refuse de
/// choisir, une position hors métropole ne rend rien. Le code de sortie les distingue, pour qu'un
/// script puisse trancher sans lire la prose.
@Command(
        name = "situer-carre",
        description = "Déduit le carré d'une position (hors ligne). Écrit le numéro, ou les candidats sur une"
                + " frontière.")
public final class SituerCarre implements Callable<Integer>, LectureSeule {

    @Option(
            names = "--position",
            required = true,
            paramLabel = "<lat, lon>",
            description = "Position, latitude puis longitude : « 44.44674980384396, 6.298116860416506 »."
                    + " Le degré-minute-seconde est accepté.")
    private String position;

    @Spec
    private CommandSpec spec;

    private final PropositionCarre proposition;

    @Inject
    public SituerCarre(PropositionCarre proposition) {
        this.proposition = proposition;
    }

    @Override
    public Integer call() {
        VerdictProposition verdict = proposition.pour(position);
        return switch (verdict) {
            case VerdictProposition.Propose propose -> {
                spec.commandLine().getOut().println(propose.numero());
                yield ExitCode.OK;
            }
            // Les candidats sur la sortie standard, le motif sur l'erreur : un script qui lit `stdout`
            // récupère les deux numéros sans avoir à écarter une phrase.
            case VerdictProposition.Frontiere frontiere -> {
                frontiere.numeros().forEach(spec.commandLine().getOut()::println);
                spec.commandLine().getErr().println(frontiere.message());
                yield ExitCode.SOFTWARE;
            }
            case VerdictProposition.HorsGrille horsGrille -> {
                spec.commandLine().getErr().println(horsGrille.message());
                yield ExitCode.SOFTWARE;
            }
            case VerdictProposition.PositionIllisible illisible -> {
                spec.commandLine().getErr().println(illisible.message());
                yield ExitCode.USAGE;
            }
        };
    }
}
