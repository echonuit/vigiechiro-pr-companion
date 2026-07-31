package fr.univ_amu.iut.cli.commande.api;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/// `vigiechiro api …` : l'**interrogation brute** de l'API VigieChiro, en lecture seule.
///
/// ## La frontière que ce groupe matérialise
///
/// La CLI parle deux langages, et la structure le dit :
///
/// - **sous `api`**, on parle celui de l'**API** : des chemins, des pages, des `_items`. Le JSON sort
///   tel quel, et rien ici n'écrit ;
/// - **au premier niveau** (`lister-sites-vigiechiro`, `lister-participations-vigiechiro`…), on parle
///   celui du **produit** : des sites, des carrés, des points, avec des projections, un recensement, un
///   dénominateur.
///
/// ## Ce que ce groupe n'est pas
///
/// Ce n'est pas la façon d'ajouter une capacité. Une commande métier encode, une fois pour toutes, ce
/// qu'il faut savoir de l'API - les pièges compris ; le groupe `api` rend la main à l'utilisateur. S'en
/// servir comme raccourci ferait cesser de grandir la couche qui protège tout le monde.
///
/// Ses sous-commandes sont **volontairement discrètes** : elles ne figurent pas au catalogue des
/// commandes (`dev-docs/cli.md` n'a qu'une ligne pour le groupe) et leur détail vit dans
/// `dev-docs/api-vigiechiro.md`. Contrepartie : le groupe reste **strictement borné**, et ne grossit
/// pas sans décision (cf. l'ADR du chantier #2999).
@Command(
        name = "api",
        description = "Interrogation brute de l'API Vigie-Chiro (lecture seule, exploration).",
        subcommands = {ApiLire.class, ApiRessources.class})
public final class GroupeApi implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    /// Sans sous-commande, le groupe affiche son aide et sort en succès - comme la racine.
    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return 0;
    }
}
