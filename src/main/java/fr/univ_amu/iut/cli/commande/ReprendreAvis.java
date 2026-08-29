package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.qualification.model.ServiceEmport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `reprendre-avis` (#4729) : l'expéditeur range l'avis revenu **à côté du sien**, jamais dessus
/// (ADR 4517), parité en ligne de commande du geste « Reprendre un avis reçu… » (article A19).
///
/// **Un second avis ne remplace le premier qu'avec `--remplacer`.** Le refus nomme le relecteur
/// présent et ce qui serait perdu : en ligne de commande, un écrasement silencieux ne laisse aucune
/// modale pour se rattraper.
@Command(
        name = "reprendre-avis",
        description = "Range un avis revenu à côté du nôtre. --remplacer pour écraser un avis présent.")
public final class ReprendreAvis implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = "--fichier", required = true, paramLabel = "<v>", description = "Avis revenu à reprendre.")
    private Path paquet;

    @Option(names = "--remplacer", description = "Remplace un avis déjà présent.")
    private boolean remplacer;

    private final ServiceEmport service;

    @Inject
    public ReprendreAvis(ServiceEmport service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() throws IOException {
        // Aucun garde ici : `appliquerImport` refuse déjà un plan qui refuse, et un second avis non
        // confirmé, en nommant ce qui serait remplacé. Le redoubler donnerait deux messages pour un
        // seul refus, dont un moins précis.
        ServiceEmport.BilanImportAvis bilan = service.importerAvis(paquet, remplacer);
        spec.commandLine()
                .getOut()
                .println(bilan.verdicts() + " verdict(s) de « " + bilan.pseudoRelecteur()
                        + " » rangés à côté des vôtres.");
        return 0;
    }
}
