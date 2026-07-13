package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.lot.model.ServiceLot;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `reinitialiser-depot` (#984) : efface le **plan de dépôt local** d'un passage et le ramène à
/// « Prêt à déposer » pour forcer un **nouveau téléversement** (ex. dépôt orphelin d'avant le
/// rattachement `lien_participation`, ou reprise à zéro). Équivalent CLI du bouton « Réinitialiser le
/// dépôt » de M-Lot.
///
/// Les **archives ZIP sur disque** (`depot/`) et le **lien de participation** sont conservés : le
/// re-dépôt réutilise la même participation. Opération **locale** (aucun appel serveur). Code retour `0`.
@Command(
        name = "reinitialiser-depot",
        description = "Efface le suivi de dépôt d'un passage (retour « Prêt à déposer ») pour re-téléverser.")
public final class ReinitialiserDepot implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Passage dont le dépôt local doit être réinitialisé.")
    private Long idPassage;

    @Spec
    private CommandSpec spec;

    private final ServiceLot service;

    @Inject
    public ReinitialiserDepot(ServiceLot service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        service.reinitialiserDepot(idPassage);
        spec.commandLine()
                .getOut()
                .println("Dépôt réinitialisé pour le passage " + idPassage + " : vous pouvez le re-téléverser.");
        return 0;
    }
}
