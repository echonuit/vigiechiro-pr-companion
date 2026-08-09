package fr.univ_amu.iut.cli.commande.api;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.LectureSeule;
import fr.univ_amu.iut.commun.api.CatalogueApi;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `vigiechiro api ressources` : **quoi taper**.
///
/// Le manque qui a ouvert ce chantier n'était pas le pouvoir d'interroger - `api lire` couvre tous les
/// chemins de lecture - mais de **savoir lesquels existent**. La carte ([CatalogueApi]) le dit, avec ce
/// qu'il faut savoir avant d'essayer : les ressources sans route de collection (leur refus n'est pas
/// une affaire de rôle), celles dont le nom trompe, et les pièges communs.
///
/// Avec `--sonder`, la commande **confronte la carte au serveur** : la colonne « répond » porte le
/// statut observé. Sans, elle affiche ce qu'on **sait** ; c'est une carte, pas un état.
@Command(name = "ressources", description = "Liste les ressources lisibles de l'API et leurs chemins.")
public final class ApiRessources implements Callable<Integer>, LectureSeule {

    @Option(
            names = "--sonder",
            description = "Interroge chaque ressource pour confronter la carte à ce que le serveur répond.")
    private boolean sonder;

    @Option(names = "--token", paramLabel = "<jeton>", description = "Jeton VigieChiro, s'il n'est pas enregistré.")
    private String token;

    @Spec
    private CommandSpec spec;

    private final ClientVigieChiro client;

    @Inject
    public ApiRessources(ClientVigieChiro client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public Integer call() {
        if (token != null && !token.isBlank()) {
            System.setProperty("vigiechiro.token", token);
        }
        PrintWriter sortie = spec.commandLine().getOut();
        for (CatalogueApi.RessourceApi ressource : CatalogueApi.ressources()) {
            sortie.println(ressource.nom() + (sonder ? "  [" + sonderLaPremiere(ressource) + "]" : ""));
            for (CatalogueApi.RouteApi route : ressource.lectures()) {
                sortie.println("    " + route.chemin());
            }
            sortie.println("    · " + ressource.note());
        }
        sortie.println();
        sortie.println("À savoir sur toutes les lectures :");
        for (String piege : CatalogueApi.pieges()) {
            sortie.println("  · " + piege);
        }
        return 0;
    }

    /// Interroge le **premier chemin sans paramètre** de la ressource et rend ce que le serveur en dit.
    /// Une ressource dont tous les chemins réclament un identifiant n'est pas sondable ainsi : on le
    /// dit plutôt que de fabriquer un identifiant qui n'existe pas.
    private String sonderLaPremiere(CatalogueApi.RessourceApi ressource) {
        return ressource.lectures().stream()
                .map(CatalogueApi.RouteApi::chemin)
                .filter(chemin -> !chemin.contains("{id}"))
                .findFirst()
                .map(this::statut)
                .orElse("non sondable sans identifiant");
    }

    private String statut(String chemin) {
        return switch (client.lectureBrute(chemin)) {
            case ReponseApi.Succes<String> ignore -> "répond";
            case ReponseApi.NonConnecte<String> ignore -> "non connecté";
            case ReponseApi.Injoignable<String>(String cause) -> "injoignable : " + cause;
            case ReponseApi.Refuse<String>(int statut, String corps) -> "refus HTTP " + statut;
        };
    }
}
