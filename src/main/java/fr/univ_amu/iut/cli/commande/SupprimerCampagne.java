package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `supprimer-campagne` (#2355) : supprime une campagne de suivi.
///
/// Geste **non destructeur pour les nuits** : les passages qui y étaient rattachés sont simplement
/// **détachés** (`campaign_id` repasse à `null`, cf. `ON DELETE SET NULL` de la migration V33), jamais
/// effacés. La sortie le rappelle, pour qu'on ne confonde pas supprimer un regroupement et supprimer
/// ce qu'il regroupait.
@Command(name = "supprimer-campagne", description = "Supprime une campagne ; ses passages sont détachés, pas effacés.")
public final class SupprimerCampagne implements Callable<Integer> {

    @Option(names = "--campagne", required = true, description = "Identifiant de la campagne à supprimer.")
    private Long campagne;

    @Spec
    private CommandSpec spec;

    private final ServiceCampagne service;

    @Inject
    public SupprimerCampagne(ServiceCampagne service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        service.supprimerCampagne(campagne);
        spec.commandLine()
                .getOut()
                .println("Campagne #" + campagne + " supprimée. Les passages rattachés ont été détachés, pas effacés.");
        return 0;
    }
}
