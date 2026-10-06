package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.qualification.model.ServiceEmport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `ouvrir-paquet-recu` (#4729) : le relecteur ouvre un paquet qu'on lui a confié, parité en ligne de
/// commande du geste homonyme de M-Qualification (article A19).
///
/// L'identité s'appose **à l'ouverture** et non au jugement (#4626) : sans connexion valide, la
/// commande refuse plutôt que de recueillir des verdicts que personne ne pourrait attribuer.
///
/// Refus métier (nuit inconnue du poste, séquence absente, identité manquante, sélection déjà
/// présente sans `--remplacer`) : code 1.
@Command(
        name = "ouvrir-paquet-recu",
        description = "Ouvre un paquet reçu et crée la sélection figée de l'expéditeur."
                + " --remplacer pour écraser une sélection présente.")
public final class OuvrirPaquetRecu implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = "--fichier", required = true, paramLabel = "<v>", description = "Paquet reçu à ouvrir.")
    private Path paquet;

    @Option(names = "--remplacer", description = "Remplace la sélection d'écoute déjà présente sur cette nuit.")
    private boolean remplacer;

    private final ServiceEmport service;

    private final StockageConnexion connexion;

    @Inject
    public OuvrirPaquetRecu(ServiceEmport service, StockageConnexion connexion) {
        this.service = Objects.requireNonNull(service, "service");
        this.connexion = Objects.requireNonNull(connexion, "connexion");
    }

    @Override
    public Integer call() throws IOException {
        // Aucun garde ici : `reprendre` refuse déjà de remplacer une sélection sans confirmation, en
        // nommant ce qui serait perdu. La commande ne peut pas le savoir avant lui.
        ServiceEmport.BilanReprise bilan = service.reprendre(paquet, connexion.profil(), remplacer);
        spec.commandLine()
                .getOut()
                .println(bilan.sequences() + " séquence(s) à relire, signées « " + bilan.pseudoRelecteur()
                        + " ». Sélection reçue #" + bilan.idSelection() + ".");
        return 0;
    }
}
