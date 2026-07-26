package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `rattacher-campagne` (#2355) : rattache un passage à une campagne, ou l'en **détache** si
/// `--campagne` est omis. Le rattachement est facultatif ; il ne touche rien d'autre du passage.
@Command(
        name = "rattacher-campagne",
        description = "Rattache un passage à une campagne (ou l'en détache si --campagne est omis).")
public final class RattacherCampagne implements Callable<Integer> {

    @Option(names = "--passage", required = true, description = "Identifiant du passage.")
    private Long passage;

    @Option(names = "--campagne", description = "Identifiant de la campagne ; omis, détache le passage.")
    private Long campagne;

    @Spec
    private CommandSpec spec;

    private final ServiceCampagne service;

    @Inject
    public RattacherCampagne(ServiceCampagne service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        Passage misAJour = service.rattacherPassage(passage, campagne);
        if (misAJour.idCampagne() == null) {
            spec.commandLine().getOut().println("Passage #" + misAJour.id() + " détaché de toute campagne.");
        } else {
            spec.commandLine()
                    .getOut()
                    .println("Passage #" + misAJour.id() + " rattaché à la campagne #" + misAJour.idCampagne() + ".");
        }
        return 0;
    }
}
