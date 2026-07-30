package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `modifier-campagne` (#2355) : corrige le nom, l'année ou le commentaire d'une campagne existante.
///
/// Créer une campagne sans pouvoir la corriger obligeait à la supprimer pour la refaire : en
/// détachant au passage tous ses passages. Cette commande comble la moitié manquante du geste.
@Command(name = "modifier-campagne", description = "Modifie le nom, l'année ou le commentaire d'une campagne.")
public final class ModifierCampagne implements Callable<Integer> {

    @Option(names = "--campagne", required = true, description = "Identifiant de la campagne à modifier.")
    private Long campagne;

    @Option(names = "--nom", required = true, description = "Nouveau nom de la campagne.")
    private String nom;

    @Option(names = "--annee", required = true, description = "Nouvelle année de la campagne.")
    private Integer annee;

    @Option(names = "--commentaire", description = "Nouveau commentaire (omis, le commentaire est effacé).")
    private String commentaire;

    @Spec
    private CommandSpec spec;

    private final ServiceCampagne service;

    @Inject
    public ModifierCampagne(ServiceCampagne service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        Campagne modifiee = service.modifierCampagne(campagne, nom, annee, commentaire);
        spec.commandLine()
                .getOut()
                .println(
                        "Campagne modifiée : #" + modifiee.id() + " " + modifiee.nom() + " (" + modifiee.annee() + ")");
        return 0;
    }
}
