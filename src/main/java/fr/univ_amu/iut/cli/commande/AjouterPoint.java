package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `ajouter-point` (#615) : ajoute un point d'écoute à un site et **écrit son identifiant** sur la sortie
/// standard (par exemple `POINT=$(vigiechiro ajouter-point --site 3 --code A1)`). Réutilise
/// [ServiceSites#ajouterPoint] sans logique nouvelle ; les refus métier (site introuvable, code mal formé
/// R2, code déjà pris) sortent en échec d'exécution (code 1). Les coordonnées sont en degrés décimaux.
@Command(
        name = "ajouter-point",
        description = "Ajoute un point d'écoute à un site et écrit son identifiant (exploitable en script).")
public final class AjouterPoint implements Callable<Integer> {

    @Option(names = "--site", required = true, paramLabel = "<id>", description = "Identifiant du site parent.")
    private Long idSite;

    @Option(
            names = "--code",
            required = true,
            paramLabel = "<code>",
            description = "Code du point : une lettre suivie d'un chiffre (ex. A1).")
    private String code;

    @Option(names = "--lat", paramLabel = "<degrés>", description = "Latitude en degrés décimaux (optionnelle).")
    private Double latitude;

    @Option(names = "--lon", paramLabel = "<degrés>", description = "Longitude en degrés décimaux (optionnelle).")
    private Double longitude;

    @Option(names = "--description", paramLabel = "<texte>", description = "Descriptif du point (optionnel).")
    private String description;

    @Spec
    private CommandSpec spec;

    private final ServiceSites service;

    @Inject
    public AjouterPoint(ServiceSites service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        PointDEcoute point = service.ajouterPoint(idSite, code, latitude, longitude, description);
        spec.commandLine().getOut().println(point.id());
        return 0;
    }
}
