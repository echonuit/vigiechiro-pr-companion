package fr.univ_amu.iut.cli.commande.api;

import fr.univ_amu.iut.cli.LectureSeule;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/// `vigiechiro api …` : l'**interrogation brute** de l'API VigieChiro, en lecture seule.
///
/// La CLI parle deux langages, et la structure le dit. **Sous `api`**, celui de l'API : des chemins,
/// des pages, des `_items` ; le JSON sort tel quel et rien n'écrit. **Au premier niveau**, celui du
/// produit : des sites, des carrés, des points, avec leurs projections. Ce n'est donc pas la façon
/// d'ajouter une capacité - une commande métier encode une fois pour toutes ce qu'il faut savoir de
/// l'API, pièges compris, quand ce groupe rend la main à l'utilisateur.
///
/// Ses sous-commandes sont **discrètes** - hors du catalogue de `dev-docs/cli.md`, détaillées dans
/// `dev-docs/api-vigiechiro.md`. Contrepartie : le groupe reste **strictement borné** (#2999).
@Command(
        name = "api",
        description = "Interrogation brute de l'API Vigie-Chiro (lecture seule, exploration).",
        subcommands = {ApiLire.class, ApiRessources.class})
public final class GroupeApi implements Callable<Integer>, LectureSeule {

    @Spec
    private CommandSpec spec;

    /// Sans sous-commande, le groupe affiche son aide et sort en succès - comme la racine.
    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return 0;
    }
}
