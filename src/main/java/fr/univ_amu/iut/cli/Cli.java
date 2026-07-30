package fr.univ_amu.iut.cli;

import com.google.inject.Injector;
import fr.univ_amu.iut.cli.commande.CommandeRacine;
import fr.univ_amu.iut.cli.di.CliModule;
import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.di.Amorcage;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.ConfigurationJournalisation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.MissingParameterException;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.UnmatchedArgumentException;

/// Point d'entrée **en ligne de commande** (sans JavaFX) du compagnon VigieChiro (parcours A10 :
/// scriptabilité). Le socle (#614) repose sur **picocli** : la commande racine [CommandeRacine] et ses
/// sous-commandes (`cli.commande`) déclarent nom, options et aide ; picocli parse, génère l'aide et
/// convertit les types. Les commandes sont **construites par Guice** ([FabriqueGuice]) pour recevoir les
/// services métier ; la CLI n'a **aucune logique propre**.
///
/// **Workspace surchargeable.** L'option globale `--workspace <dir>` est consommée par [#main(String[])]
/// **avant** de bâtir l'injecteur (elle positionne `vigiechiro.workspace`, lu par `CommunModule`) ; les
/// tests positionnent la propriété directement sur un `@TempDir`.
///
/// **Codes de sortie** (convention #2294) : `0` succès · `1` échec d'exécution, **état incertain** (accès
/// aux données, E/S, incident inattendu) · `2` **refus métier** (état intact, rien n'a été fait) **ou**
/// mauvaise invocation (commande inconnue, option requise manquante ou mal formée). `executer` **ne fait
/// pas** `System.exit` (il renvoie le code, pour rester testable) ; seul `main` traduit le code.
public final class Cli {

    /// Succès.
    public static final int CODE_SUCCES = 0;

    /// Échec d'exécution : accès aux données, E/S, incident inattendu (état incertain).
    public static final int CODE_ERREUR_EXECUTION = 1;

    /// Refus métier : état intact, rien n'a été fait (convention #2294).
    public static final int CODE_REFUS = 2;

    /// Mauvaise invocation : commande inconnue, argument requis manquant ou mal formé.
    public static final int CODE_ERREUR_ARGUMENTS = 2;

    private static final Logger LOG = Logger.getLogger(Cli.class.getName());

    private final Injector injecteur;

    /// @param injecteur injecteur Guice résolvant le socle, les features et le [CliModule]
    ///     (typiquement [#injecteurApplicatif()])
    public Cli(Injector injecteur) {
        this.injecteur = Objects.requireNonNull(injecteur, "injecteur");
    }

    /// Injecteur applicatif complet ([RacineInjecteur#creer()]) augmenté du [CliModule] en injecteur enfant.
    /// À appeler après avoir éventuellement positionné `vigiechiro.workspace`.
    public static Injector injecteurApplicatif() {
        return RacineInjecteur.creer().createChildInjector(new CliModule());
    }

    /// CLI prête à l'emploi, branchée sur l'injecteur applicatif complet.
    public static Cli applicative() {
        return new Cli(injecteurApplicatif());
    }

    /// Exécute une invocation et renvoie son code de sortie (sans `System.exit`). La base est **migrée**
    /// (idempotent) avant toute sous-commande, mais pas pour l'aide seule.
    ///
    /// @param args arguments (`--workspace` déjà consommé par [#main(String[])])
    /// @param sortie flux du compte rendu (typiquement `System.out`)
    /// @param erreur flux des messages d'erreur (typiquement `System.err`)
    public int executer(String[] args, PrintStream sortie, PrintStream erreur) {
        CommandLine ligne = new CommandLine(CommandeRacine.class, new FabriqueGuice(injecteur));
        // `-h`/`--help` sur CHAQUE sous-commande (pas seulement la racine), en un seul point plutôt que
        // `mixinStandardHelpOptions` répété sur 35 `@Command` (#1592) : `reactiver --help` doit décrire la
        // commande, pas échouer « Unknown option ». Le drapeau court-circuite la validation des arguments.
        ligne.getSubcommands().values().forEach(sous -> sous.getCommandSpec().mixinStandardHelpOptions(true));
        ligne.setCaseInsensitiveEnumValuesAllowed(true); // --protocole standard == STANDARD (confort de saisie)
        ligne.setOut(new PrintWriter(sortie, true, StandardCharsets.UTF_8));
        ligne.setErr(new PrintWriter(erreur, true, StandardCharsets.UTF_8));
        ligne.setExecutionStrategy(this::migrerPuisExecuter);
        ligne.setParameterExceptionHandler(Cli::gererErreurUsage);
        ligne.setExecutionExceptionHandler(Cli::gererErreurExecution);
        int code = ligne.execute(args);
        // Un `PrintWriter` en auto-flush ne se vide QUE sur `println`/`printf`, jamais sur `print`. Une
        // commande dont le dernier caractère écrit ne vient pas d'un `println` (un CSV, qui porte déjà
        // sa propre fin de ligne) verrait sa sortie disparaître en silence, code 0 à l'appui. On vide
        // donc explicitement, une fois pour toutes les commandes (#2351).
        ligne.getOut().flush();
        ligne.getErr().flush();
        return code;
    }

    /// Stratégie d'exécution : migre la base **si une sous-commande est invoquée** (une invocation qui se
    /// limite à l'aide/usage n'a pas besoin de la base), puis délègue à la stratégie standard de picocli.
    private int migrerPuisExecuter(ParseResult parseResult) {
        if (parseResult.hasSubcommand()) {
            injecteur.getInstance(MigrationSchema.class).migrer();
        }
        return new CommandLine.RunLast().execute(parseResult);
    }

    /// Erreurs de **parsing** (commande inconnue, option requise manquante ou mal typée) → message français
    /// + rappel de l'aide, code [#CODE_ERREUR_ARGUMENTS].
    private static int gererErreurUsage(ParameterException exception, String[] args) {
        CommandLine ligne = exception.getCommandLine();
        PrintWriter erreur = ligne.getErr();
        erreur.println(messageUsage(exception));
        erreur.println("Lancez « vigiechiro --help » pour la liste des commandes.");
        return CODE_ERREUR_ARGUMENTS;
    }

    /// Reformule en français les erreurs de parsing les plus courantes (commande inconnue, argument requis
    /// manquant) en conservant les noms d'options ; repli sur le message picocli sinon.
    private static String messageUsage(ParameterException exception) {
        if (exception instanceof UnmatchedArgumentException) {
            return "Commande inconnue : " + exception.getMessage();
        }
        if (exception instanceof MissingParameterException manquant) {
            String noms = manquant.getMissing().stream().map(Cli::nomArgument).collect(Collectors.joining(", "));
            return "Argument requis manquant : " + noms + ".";
        }
        return "Erreur d'usage : " + exception.getMessage();
    }

    /// Nom lisible d'un argument manquant : le nom long de l'option (`--sortie`) ou son libellé de paramètre.
    private static String nomArgument(ArgSpec argument) {
        return argument instanceof OptionSpec option ? option.longestName() : argument.paramLabel();
    }

    /// Erreurs d'**exécution** d'une commande : une [ErreurUsage] (invocation invalide détectée dans la
    /// logique, ex. point introuvable) sort en [#CODE_ERREUR_ARGUMENTS] ; un **refus métier**
    /// (`RegleMetierException` **ou** l'`IllegalArgumentException` des validateurs R1/R2) sort en
    /// [#CODE_REFUS] (état intact, rien n'a été fait, convention #2294) ; toute autre exception en
    /// [#CODE_ERREUR_EXECUTION] (échec inattendu, état incertain). On imprime le **message** à l'utilisateur
    /// (jamais la trace, qui parasiterait la sortie d'un script) et on **journalise** l'échec (#1523), à la
    /// parité de l'IHM : un refus métier discrètement (FINE, sans trace), un échec **inattendu** avec sa
    /// trace (SEVERE, reste inspectable dans `<workspace>/logs/`). Une simple erreur d'usage ne mérite ni
    /// l'un ni l'autre.
    private static int gererErreurExecution(Exception exception, CommandLine ligne, ParseResult parseResult) {
        if (exception instanceof ErreurUsage) {
            ligne.getErr().println("Erreur d'usage : " + exception.getMessage());
            return CODE_ERREUR_ARGUMENTS;
        }
        if (exception instanceof RegleMetierException || exception instanceof IllegalArgumentException) {
            // Un `IllegalArgumentException` qui remonte jusqu'ici vient des validateurs (R1/R2 :
            // `ValidateurCarre`, `ValidateurCodePoint`) : c'est un refus métier, pas un incident. L'IHM le
            // traite déjà ainsi (`catch (RegleMetierException | IllegalArgumentException)`) ; la CLI s'aligne,
            // pour ne pas noyer les logs sous une trace SEVERE à chaque saisie invalide.
            LOG.fine(() -> "Refus métier d'une commande CLI : " + exception.getMessage());
            // Le message du modèle dit ce qui MANQUE ; c'est ici qu'on ajoute quoi taper (#2635). Avant,
            // il arrivait avec « menu ☰ > Se connecter » écrit dedans, servi à qui n'a pas de menu.
            ligne.getErr().println("Refus : " + GesteAttenduCli.message(exception));
            return CODE_REFUS;
        }
        LOG.log(Level.SEVERE, exception, () -> "Échec inattendu d'une commande CLI");
        ligne.getErr().println("Échec : " + exception.getMessage());
        return CODE_ERREUR_EXECUTION;
    }

    /// Point d'entrée processus : extrait l'option globale `--workspace`, positionne
    /// `vigiechiro.workspace` **avant** de bâtir l'injecteur, exécute puis sort avec le code retourné.
    public static void main(String[] args) {
        List<String> restants = new ArrayList<>();
        String workspace = extraireWorkspace(args, restants);
        if (workspace != null) {
            System.setProperty("vigiechiro.workspace", workspace);
        }
        // Journalisation après la résolution du workspace (pour écrire dans le bon dossier), avant tout
        // travail : la CLI aussi laisse une trace de ses incidents (#1523).
        ConfigurationJournalisation.configurer(Workspace.resolu().dossierLogs());
        // Migrer AVANT de composer l'injecteur (ADR 1038, #2187) : `applicative()` compose l'injecteur,
        // ce qui lit les drapeaux de fonctionnalités en base ; une base à jour est donc nécessaire ici.
        // Seulement si la base existe déjà : une aide sur une installation neuve ne doit créer aucun
        // fichier (picocli n'a pas besoin de base pour imprimer un usage). Le cas « base absente + vraie
        // sous-commande » reste couvert par la migration différée de `migrerPuisExecuter`, qui la crée.
        Amorcage.migrerSiPresente();
        int code = applicative().executer(restants.toArray(new String[0]), System.out, System.err);
        System.exit(code);
    }

    /// Retire l'option globale `--workspace <dir>` du tableau d'arguments (où qu'elle soit) et renvoie sa
    /// valeur, en accumulant les autres jetons dans `restants`.
    static String extraireWorkspace(String[] args, List<String> restants) {
        String workspace = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--workspace") && i + 1 < args.length) {
                workspace = args[i + 1];
                i++; // saute la valeur
            } else {
                restants.add(args[i]);
            }
        }
        return workspace;
    }
}
