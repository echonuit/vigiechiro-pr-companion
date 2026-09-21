package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.LectureSeule;
import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// Lien de la participation associée à un passage local (#1874), sans accès réseau.
@Command(name = "lien-participation", description = "Afficher l'URL de la participation liée à un passage local.")
public final class LienParticipation implements Callable<Integer>, LectureSeule {
    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<identifiant local>",
            description = "Identifiant du passage dans le workspace.")
    private Long passage;

    @Spec
    private CommandSpec spec;

    private final PortailVigieChiro portail;

    @Inject
    public LienParticipation(PortailVigieChiro portail) {
        this.portail = Objects.requireNonNull(portail, "portail");
    }

    @Override
    public Integer call() {
        String url = portail.pageParticipation(passage)
                .orElseThrow(() -> new ErreurUsage("Aucune participation liée au passage " + passage + "."));
        spec.commandLine().getOut().println(url);
        return 0;
    }
}
