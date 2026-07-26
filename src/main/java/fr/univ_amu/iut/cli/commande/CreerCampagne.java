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

/// `creer-campagne` (#2355) : crée une campagne de suivi (regroupement facultatif de passages). Lecture
/// pure de [ServiceCampagne]. L'année vaut celle du jour si `--annee` est omis.
@Command(name = "creer-campagne", description = "Crée une campagne de suivi (regroupement de passages).")
public final class CreerCampagne implements Callable<Integer> {

    @Option(names = "--nom", required = true, description = "Nom de la campagne.")
    private String nom;

    @Option(names = "--annee", description = "Année de la campagne (par défaut : l'année courante).")
    private Integer annee;

    @Option(names = "--commentaire", description = "Commentaire libre (optionnel).")
    private String commentaire;

    @Spec
    private CommandSpec spec;

    private final ServiceCampagne service;

    @Inject
    public CreerCampagne(ServiceCampagne service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        Campagne campagne = service.creerCampagne(nom, annee, commentaire);
        spec.commandLine()
                .getOut()
                .println("Campagne créée : #" + campagne.id() + " " + campagne.nom() + " (" + campagne.annee() + ")");
        return 0;
    }
}
