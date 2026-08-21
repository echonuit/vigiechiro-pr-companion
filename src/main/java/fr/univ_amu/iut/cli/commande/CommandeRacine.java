package fr.univ_amu.iut.cli.commande;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/// Commande **racine** du CLI VigieChiro (#614) : porte le nom du programme, l'aide générale et la liste
/// des sous-commandes. Sans logique propre : chaque sous-commande réutilise un service métier existant.
///
/// Lancée **sans sous-commande**, elle affiche l'aide (au lieu de ne rien faire), pour guider l'utilisateur.
/// L'aide, l'usage et la liste des commandes sont **générés** par picocli à partir de ces annotations : plus
/// de texte d'aide maintenu à la main.
@Command(
        name = "vigiechiro",
        // Fournisseur plutôt que chaîne figée : la version vient du manifeste, donc n'est connue
        // qu'à l'exécution. L'attribut `version` portait jusqu'ici un libellé sans numéro, qui
        // répondait à `--version` sans rien apprendre (#2108).
        versionProvider = FournisseurVersion.class,
        mixinStandardHelpOptions = true,
        sortOptions = false,
        synopsisSubcommandLabel = "<commande>",
        header = "Compagnon VigieChiro : interface en ligne de commande.",
        description = "Automatise les parcours du compagnon (import, dépôt, export) sans interface graphique, "
                + "pour scripter des traitements. Chaque commande s'appuie sur les mêmes services que "
                + "l'application.",
        footerHeading = "%nOption globale :%n",
        // ⚠️ Le mot `ihm` est écrit ICI EN DUR, et non interpolé depuis `Launcher.MOT_FENETRE` : une
        // constante de compilation est INLINÉE par javac dans l'annotation, donc la changer laisserait
        // ce texte inchangé sans que rien ne rougisse - la figure que l'ADR 3947 a payée. Le lien entre
        // les deux est tenu par un test qui les compare (`LauncherTest`), pas par le compilateur.
        //
        // Il est nommé ici parce que, sur un produit installé, `vigiechiro` est souvent le SEUL nom
        // dans le PATH : le `.deb` ne pose pas de lien pour le lanceur graphique. Sans cette ligne,
        // celui qui découvre la commande n'a aucun moyen d'apprendre comment ouvrir la fenêtre (#4071).
        footer = {
            "  --workspace <dir>   Dossier de travail (base vigiechiro.db). Défaut : <Documents>/VigieChiro-Companion.",
            // ⚠️ Les clés ne sont PAS listées ici : elles vivent au registre `CleDeReglage`, et une
            // annotation ne peut citer qu'une constante de compilation - la liste y serait donc une
            // COPIE, qui se démoderait au premier réglage ajouté. Une clé inconnue nomme celles qui
            // existent, ce qui met la liste là où on en a besoin (#4075).
            "  --reglage <cle>=<valeur>   Relève une borne (répétable). Une clé inconnue liste les clés admises.",
            "",
            // ⚠️ Le « refus métier » était rangé sous le code 1 dans ce pied, alors que la convention
            // #2294 le rend en 2 - et c'est bien 2 que le produit renvoie, comme l'attestent les E2E
            // « refus métier explicite, exit 2 ». L'aide disait donc à un script de traiter un refus
            // (état intact, rien n'a été fait) comme une panne d'exécution (état incertain), c'est-à-dire
            // exactement l'inverse de ce que la distinction sert à décider.
            "Codes de sortie : 0 succès · 1 échec d'exécution, état incertain (E/S, incident)",
            "                 · 2 refus métier, rien n'a été fait, ou mauvaise invocation.",
            "",
            // ⚠️ Une seule ligne, et courte : picocli replie à la largeur du terminal. Une première
            // version tenait en deux lignes de source et se rendait en TROIS, dont une orpheline
            // (« fenêtre, » seul). Ce qui se juge ici est le rendu, pas le source.
            "Ouvrir la fenêtre : « vigiechiro ihm », comme le double-clic."
        },
        subcommands = {
            CreerSite.class,
            RecupererCarre.class,
            ModifierSite.class,
            SupprimerSite.class,
            AjouterPoint.class,
            ModifierPoint.class,
            ListerSites.class,
            ListerSitesVigieChiro.class,
            ListerParticipationsVigieChiro.class,
            fr.univ_amu.iut.cli.commande.api.GroupeApi.class,
            ListerPassages.class,
            AfficherSoldeSaison.class,
            CreerCampagne.class,
            ListerCampagnes.class,
            ModifierCampagne.class,
            RattacherCampagne.class,
            SupprimerCampagne.class,
            StatutPassage.class,
            Diagnostiquer.class,
            VerifierMiseAJour.class,
            Auditer.class,
            RetroEmpreintes.class,
            RattraperCommunes.class,
            Reactiver.class,
            ReconstruirePassage.class,
            MetadonneesPassage.class,
            Importer.class,
            ImporterTransformes.class,
            ImporterTadarida.class,
            ImporterVigieChiro.class,
            PublierCorrectionsVigieChiro.class,
            ListerObservations.class,
            ListerEspeces.class,
            ListerCarres.class,
            ValiderObservations.class,
            CorrigerObservations.class,
            MarquerDouteux.class,
            MarquerReference.class,
            PoserCertitude.class,
            Discussion.class,
            Qualifier.class,
            QualifierFichier.class,
            ListerSelection.class,
            PreCheck.class,
            ConstituerSelection.class,
            ExporterLot.class,
            Deposer.class,
            RecupererVigieChiro.class,
            DeposerVigieChiro.class,
            LancerTraitementVigieChiro.class,
            TraiterPassages.class,
            EtatTraitementVigieChiro.class,
            ReinitialiserDepot.class,
            SupprimerPassage.class,
            VerifierDepotVigieChiro.class,
            ExporterVu.class,
            ExporterObservations.class,
            ExporterActivite.class,
            ExporterSons.class,
            SynthetiserPassage.class,
            Sauvegarder.class,
            Restaurer.class,
            ListerSauvegardes.class,
            SupprimerSauvegarde.class,
            ResetGuide.class,
            Emplacements.class
        })
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
