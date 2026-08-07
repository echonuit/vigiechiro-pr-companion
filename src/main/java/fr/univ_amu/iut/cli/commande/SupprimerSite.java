package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.cli.Cli;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `supprimer-site` (#1383) : supprime un site déclaré et ses points d'écoute.
///
/// Parité avec le bouton « Supprimer » de l'écran Sites, **garde compris** : le service refuse tant
/// qu'un point porte au moins un passage, parce que le schéma cascade
/// `monitoring_site → listening_point → passage` détruirait sinon les nuits en silence.
///
/// Suit le patron des commandes destructrices du dépôt (`supprimer-passage`) : la perte se **chiffre
/// avant** toute écriture, et sans `--confirmer` la commande ne touche à rien et sort en **2** - un
/// code distinct du succès (0) et de l'échec (1), pour qu'un script s'arrête dessus sans avoir rien
/// détruit.
@Command(
        name = "supprimer-site",
        description = "Supprime un site et ses points d'écoute. Sans --confirmer, chiffre la perte "
                + "et ne touche à rien. Refusé si un point porte un passage.")
public final class SupprimerSite implements Callable<Integer> {

    @Option(names = "--site", required = true, paramLabel = "<id>", description = "Site à supprimer.")
    private Long idSite;

    @Option(
            names = "--confirmer",
            description = "Obligatoire pour agir : atteste que la perte chiffrée ci-dessus est voulue.")
    private boolean confirmer;

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma.
    private final Provider<ServiceSites> service;

    @Inject
    public SupprimerSite(Provider<ServiceSites> service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        ServiceSites sites = service.get();

        // Le blocage se constate AVANT de proposer --confirmer : faire confirmer une perte que le
        // service refusera ensuite reviendrait à demander un accord qui n'engage rien.
        List<String> bloquants = sites.pointsPortantUnPassage(idSite);
        if (!bloquants.isEmpty()) {
            spec.commandLine().getErr().println(refusEnClair(bloquants));
            // Refus MÉTIER, donc 2 et non 1 : rien n'a été touché, l'état est intact (convention
            // #2294). Le 1 est réservé à l'échec inattendu, où l'état devient incertain.
            return Cli.CODE_REFUS;
        }

        // La perte se chiffre AVANT toute écriture : sans --confirmer, rien d'autre ne se produira.
        List<PointDEcoute> points = sites.listerPoints(idSite);
        PrintWriter sortie = spec.commandLine().getOut();
        sortie.println(perteEnClair(points));

        if (!confirmer) {
            spec.commandLine().getErr().println("Rien n'a été supprimé. Ajoutez --confirmer pour assumer cette perte.");
            return Cli.CODE_REFUS;
        }
        // Le refus reste porté par le service : il est notre seule source de vérité pour la règle, et
        // il retranche la fenêtre entre le constat ci-dessus et l'écriture.
        sites.supprimerSite(idSite);
        sortie.println("Site " + idSite + " supprimé.");
        return 0;
    }

    /// Le refus, qui dit **ce qui bloque** et **quoi faire**, pas seulement « impossible » (ADR 2635).
    static String refusEnClair(List<String> bloquants) {
        String codes = String.join(", ", bloquants);
        String sujet = bloquants.size() == 1 ? "Le point « " + codes + " » porte" : "Les points " + codes + " portent";
        return "Suppression refusée : " + sujet + " au moins un passage." + System.lineSeparator()
                + "Supprimez d'abord ces passages (voir `supprimer-passage`), puis relancez.";
    }

    /// Ce que la suppression fait disparaître, dit avant de le faire.
    static String perteEnClair(List<PointDEcoute> points) {
        if (points.isEmpty()) {
            return "Ce site n'a aucun point d'écoute : sa suppression ne fait rien disparaître d'autre.";
        }
        String codes = points.stream().map(PointDEcoute::code).collect(Collectors.joining(", "));
        return "Suppression du site et de ses " + points.size() + " point(s) d'écoute : " + codes + ".";
    }
}
