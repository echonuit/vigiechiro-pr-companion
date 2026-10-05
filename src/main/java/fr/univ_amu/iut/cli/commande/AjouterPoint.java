package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.sites.model.ControleCarreLocal;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.PointVoisin;
import java.util.List;
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
///
/// **Le carré est contrôlé quand une position est donnée** (#4671), comme dans l'écran : le carroyage
/// embarqué dit dans quelle maille tombe le point, et une divergence part sur la sortie d'ERREUR. Le
/// code de sortie ne change pas - le contrôle est un confort, jamais une condition, et l'identifiant du
/// point reste seul sur la sortie standard pour que `POINT=$(...)` continue de marcher.
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
            description = "Code du point : une lettre suivie d'un ou plusieurs chiffres (ex. A1, Z41).")
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

    private final ControleCarreLocal controle;

    @Inject
    public AjouterPoint(ServiceSites service, ControleCarreLocal controle) {
        this.service = Objects.requireNonNull(service, "service");
        this.controle = Objects.requireNonNull(controle, "controle");
    }

    @Override
    public Integer call() {
        // Les voisins se cherchent AVANT la création : après, le point serait son propre voisin.
        List<PointDEcoute> dejaLa = service.listerPoints(idSite);
        PointDEcoute point = service.ajouterPoint(idSite, code, latitude, longitude, description);
        spec.commandLine().getOut().println(point.id());
        direUnVoisin(dejaLa);
        AvertissementCarre.direSiDivergence(spec, controle, service.site(idSite).numeroCarre(), latitude, longitude);
        return 0;
    }

    /// Un point du site à 40 m ou moins de la position donnée se **dit**, comme l'écran le dit depuis
    /// #5688 (#5837, ADR 0014). Sur la sortie d'erreur et sans toucher au code de retour, pour la raison
    /// d'[AvertissementCarre] : la sortie standard ne porte que l'identifiant du point.
    private void direUnVoisin(List<PointDEcoute> dejaLa) {
        if (latitude == null || longitude == null) {
            return;
        }
        PointVoisin.lePlusProche(latitude, longitude, dejaLa)
                .ifPresent(voisin -> spec.commandLine().getErr().println(voisin.avertissement()));
    }
}
