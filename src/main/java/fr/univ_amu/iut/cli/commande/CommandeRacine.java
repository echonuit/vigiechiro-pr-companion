package fr.univ_amu.iut.cli.commande;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/// Commande **racine** du CLI VigieChiro (#614) : porte le nom du programme, l'aide générale et la liste
/// des sous-commandes. Sans logique propre — chaque sous-commande réutilise un service métier existant.
///
/// Lancée **sans sous-commande**, elle affiche l'aide (au lieu de ne rien faire), pour guider l'utilisateur.
/// L'aide, l'usage et la liste des commandes sont **générés** par picocli à partir de ces annotations : plus
/// de texte d'aide maintenu à la main.
@Command(
        name = "vigiechiro",
        version = "VigieChiro — compagnon PR (CLI)",
        mixinStandardHelpOptions = true,
        sortOptions = false,
        synopsisSubcommandLabel = "<commande>",
        header = "Compagnon VigieChiro — interface en ligne de commande.",
        description = "Automatise les parcours du compagnon (import, dépôt, export) sans interface graphique, "
                + "pour scripter des traitements. Chaque commande s'appuie sur les mêmes services que "
                + "l'application.",
        footerHeading = "%nOption globale :%n",
        footer = {
            "  --workspace <dir>   Dossier de travail (base vigiechiro.db). Défaut : <Documents>/VigieChiro-Companion.",
            "",
            "Codes de sortie : 0 succès · 1 échec d'exécution (règle métier, E/S) · 2 mauvaise invocation."
        },
        subcommands = {ListerPassages.class, Importer.class, ExporterLot.class, ExporterVu.class})
public final class CommandeRacine implements Runnable {

    @Spec
    private CommandSpec spec;

    /// Aucune sous-commande fournie : on affiche l'aide sur la sortie standard (comportement plus utile
    /// qu'un silence), et le CLI sort en succès.
    @Override
    public void run() {
        CommandLine ligne = spec.commandLine();
        ligne.usage(ligne.getOut());
    }
}
