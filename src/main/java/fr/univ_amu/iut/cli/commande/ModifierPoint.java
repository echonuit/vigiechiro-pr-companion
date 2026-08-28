package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.sites.model.ControleCarreLocal;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `modifier-point` (#1383) : corrige un point d'écoute d'un site.
///
/// Parité avec la modale « Modifier le point » de l'écran Sites. Les trois refus de
/// [ServiceSites#modifierPoint] sortent en **refus métier** (code 2, état intact) : code mal formé (R2), point
/// n'appartenant pas au site, et code déjà pris par un **autre** point du site.
///
/// `--site` est requis bien que l'identifiant du point suffise techniquement : le service s'en sert
/// pour **vérifier l'appartenance**. Le rendre optionnel supprimerait ce garde-fou, et une faute de
/// frappe sur `--point` corrigerait alors un point d'un autre site sans rien dire.
@Command(
        name = "modifier-point",
        description = "Corrige le code, les coordonnées ou la description d'un point d'écoute.")
public final class ModifierPoint implements Callable<Integer> {

    @Option(names = "--point", required = true, paramLabel = "<id>", description = "Point à modifier.")
    private Long idPoint;

    @Option(
            names = "--site",
            required = true,
            paramLabel = "<id>",
            description = "Site auquel le point doit appartenir (vérifié).")
    private Long idSite;

    @Option(names = "--code", required = true, paramLabel = "<c>", description = "Code du point (unique dans le site).")
    private String code;

    @Option(names = "--lat", paramLabel = "<lat>", description = "Latitude (optionnelle).")
    private Double latitude;

    @Option(names = "--lon", paramLabel = "<lon>", description = "Longitude (optionnelle).")
    private Double longitude;

    @Option(names = "--description", paramLabel = "<texte>", description = "Description libre (optionnelle).")
    private String description;

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma.
    private final Provider<ServiceSites> service;

    private final Provider<ControleCarreLocal> controle;

    @Inject
    public ModifierPoint(Provider<ServiceSites> service, Provider<ControleCarreLocal> controle) {
        this.service = Objects.requireNonNull(service, "service");
        this.controle = Objects.requireNonNull(controle, "controle");
    }

    @Override
    public Integer call() {
        PointDEcoute point = service.get().modifierPoint(idPoint, idSite, code, latitude, longitude, description);
        spec.commandLine().getOut().println("Point " + point.id() + " modifié : " + point.code());
        AvertissementCarre.direSiDivergence(
                spec, controle.get(), service.get().site(point.idSite()).numeroCarre(), latitude, longitude);
        return 0;
    }
}
