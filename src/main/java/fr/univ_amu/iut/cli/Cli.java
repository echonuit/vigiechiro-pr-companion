package fr.univ_amu.iut.cli;

import com.google.inject.Injector;
import fr.univ_amu.iut.cli.commande.CommandeRacine;
import fr.univ_amu.iut.cli.di.CliModule;
import fr.univ_amu.iut.commun.di.Amorcage;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.CleDeReglage;
import fr.univ_amu.iut.commun.model.ConfigurationJournalisation;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.RefusAvantEcriture;
import fr.univ_amu.iut.commun.persistence.VerrouWorkspace;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
        // Le mode couleur est POSÉ, pas déduit : l'heuristique de picocli colorise sous Windows et pas
        // sous Linux, et la même commande ne rendait donc pas le même texte selon la machine (#3738).
        ligne.setColorScheme(new CommandLine.Help.ColorScheme.Builder(CouleurCli.choisie()).build());
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
    /// limite à l'aide/usage n'a pas besoin de la base), **réserve le dossier de travail** si elle peut
    /// l'écrire, puis délègue à la stratégie standard de picocli.
    ///
    /// Le verrou se prend **par défaut** : une commande ne s'en dispense qu'en portant [LectureSeule],
    /// et l'interface dit pourquoi la déclaration va dans ce sens-là (#3498).
    private int migrerPuisExecuter(ParseResult parseResult) {
        if (!parseResult.hasSubcommand()) {
            return new CommandLine.RunLast().execute(parseResult);
        }
        // ⚠️ Le gestionnaire d'exceptions de picocli ne voit QUE ce que lève la commande. Un refus né
        // ici - migration ou verrou - lui échappe et retombait en code 1, « échec, état incertain »,
        // alors que rien n'a été touché. On le traduit donc sur place (#3498).
        try {
            injecteur.getInstance(MigrationSchema.class).migrer();
            if (litSeulement(parseResult)) {
                return new CommandLine.RunLast().execute(parseResult);
            }
            // Tenu pour toute la commande, et relâché quoi qu'il arrive : une commande qui échoue ne
            // doit pas laisser le dossier réservé derrière elle.
            try (VerrouWorkspace verrou =
                    VerrouWorkspace.pourOperationExclusive(Workspace.resolu(), nomDe(parseResult))) {
                return new CommandLine.RunLast().execute(parseResult);
            }
        } catch (RefusAvantEcriture refus) {
            LOG.fine(() -> "Refus avant écriture, hors exécution de la commande : " + refus.getMessage());
            parseResult.commandSpec().commandLine().getErr().println("Refus : " + refus.getMessage());
            return CODE_REFUS;
        }
    }

    /// La sous-commande la plus profonde ne fait-elle que lire ?
    ///
    /// On descend jusqu'à la feuille : c'est elle qui s'exécute, et un groupe intermédiaire ne dit rien
    /// de ce que fait la commande qu'il porte.
    private static boolean litSeulement(ParseResult parseResult) {
        return feuille(parseResult).commandSpec().userObject() instanceof LectureSeule;
    }

    /// Nom de la sous-commande invoquée, pour que le refus dise **ce qu'on n'a pas pu lancer**.
    private static String nomDe(ParseResult parseResult) {
        return "« " + feuille(parseResult).commandSpec().name() + " »";
    }

    private static ParseResult feuille(ParseResult parseResult) {
        ParseResult courant = parseResult;
        while (courant.hasSubcommand()) {
            courant = courant.subcommand();
        }
        return courant;
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

    /// Erreurs d'**exécution** d'une commande. Le classement vit dans [VerdictCli], parce que `main` en a
    /// besoin **avant** que picocli n'existe (#3570) ; ici on ne fait qu'imprimer et journaliser.
    ///
    /// On imprime le **message**, jamais la trace : elle parasiterait la sortie d'un script, et elle
    /// reste inspectable dans `<workspace>/logs/`. ⚠️ Ce n'était vrai qu'à moitié jusqu'à #3570 : la JVM
    /// installe un `ConsoleHandler` que personne ne retirait, si bien que le `SEVERE` reversait la pile
    /// entière sur la sortie d'erreur. La CLI le retire désormais au démarrage.
    private static int gererErreurExecution(Exception exception, CommandLine ligne, ParseResult parseResult) {
        VerdictCli verdict = VerdictCli.de(exception);
        journaliser(verdict, exception);
        ligne.getErr().println(verdict.phrase());
        return verdict.code();
    }

    /// Journalise selon la nature, à la parité de l'IHM : un refus discrètement, un incident avec sa
    /// trace - qui reste inspectable dans `<workspace>/logs/` (#1523) sans parasiter la sortie d'un
    /// script. Une simple erreur d'usage ne mérite ni l'un ni l'autre.
    private static void journaliser(VerdictCli verdict, Exception echec) {
        switch (verdict.nature()) {
            case USAGE -> {
                // Rien : une faute de frappe n'est pas un événement.
            }
            case REFUS -> LOG.fine(() -> "Refus métier d'une commande CLI : " + echec.getMessage());
            case INCIDENT -> LOG.log(Level.SEVERE, echec, () -> "Échec inattendu d'une commande CLI");
        }
    }

    /// Point d'entrée processus : extrait l'option globale `--workspace`, positionne
    /// `vigiechiro.workspace` **avant** de bâtir l'injecteur, exécute puis sort avec le code retourné.
    public static void main(String[] args) {
        List<String> sansWorkspace = new ArrayList<>();
        String workspace = extraireWorkspace(args, sansWorkspace);
        if (workspace != null) {
            System.setProperty("vigiechiro.workspace", workspace);
        }

        // Les réglages se posent AVANT l'injecteur, comme le workspace : les bornes sont lues à la
        // construction des services, et une propriété posée après ne servirait plus à rien (#4075).
        List<String> restants = new ArrayList<>();
        List<String> reglages = extraireReglages(sansWorkspace, restants);
        Optional<String> refus = poserLesReglages(reglages);
        if (refus.isPresent()) {
            System.err.println(refus.get());
            System.exit(CODE_ERREUR_ARGUMENTS);
            return;
        }

        // Journalisation après la résolution du workspace (pour écrire dans le bon dossier), avant tout
        // travail : la CLI aussi laisse une trace de ses incidents (#1523).
        System.exit(executerOuRendreCompte(restants));
    }

    /// Ce que `main` fait vraiment, séparé pour que son **échec** ait un gestionnaire.
    ///
    /// ⚠️ Les deux appels ci-dessous s'exécutent **avant** que picocli n'existe, donc hors de son
    /// gestionnaire d'erreurs. Sans ce `catch`, leurs exceptions sortaient par la JVM :
    /// `Exception in thread "main"`, la pile entière, et le code `1` - y compris pour un
    /// `RefusAvantEcriture`, que #3498 avait pourtant appris à traduire en `2`. `dev-docs/cli.md`
    /// promet « message seul, jamais la trace » : cette promesse était fausse sur ce chemin (#3570).
    private static int executerOuRendreCompte(List<String> restants) {
        try {
            // Journalisation après la résolution du workspace (pour écrire dans le bon dossier), avant
            // tout travail : la CLI aussi laisse une trace de ses incidents (#1523).
            ConfigurationJournalisation.configurerSansConsole(Workspace.resolu().dossierLogs());
            // Migrer AVANT de composer l'injecteur (ADR 1038, #2187) : `applicative()` compose
            // l'injecteur, ce qui lit les drapeaux de fonctionnalités en base ; une base à jour est donc
            // nécessaire ici. Seulement si la base existe déjà : une aide sur une installation neuve ne
            // doit créer aucun fichier. Le cas « base absente + vraie sous-commande » reste couvert par
            // la migration différée de `migrerPuisExecuter`, qui la crée.
            Amorcage.migrerSiPresente();
            return applicative().executer(restants.toArray(new String[0]), System.out, System.err);
        } catch (RuntimeException echec) {
            VerdictCli verdict = VerdictCli.de(echec);
            journaliser(verdict, echec);
            // `System.err` et non une `CommandLine` : picocli n'a pas encore été construit, et c'est
            // précisément la raison pour laquelle ce chemin n'avait pas de gestionnaire.
            System.err.println(verdict.phrase());
            return verdict.code();
        }
    }

    /// Retire les options globales `--reglage <cle>=<valeur>` (répétables, où qu'elles soient) et rend
    /// leurs paires, en accumulant les autres jetons dans `restants`.
    ///
    /// ⚠️ Elles sont retirées **avant** picocli, comme `--workspace` : elles ne visent aucune
    /// sous-commande en particulier, et les laisser passer ferait rougir l'analyse d'arguments sur une
    /// option que la commande ne déclare pas.
    static List<String> extraireReglages(List<String> args, List<String> restants) {
        List<String> reglages = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals("--reglage") && i + 1 < args.size()) {
                reglages.add(args.get(i + 1));
                i++; // saute la valeur
            } else {
                restants.add(args.get(i));
            }
        }
        return reglages;
    }

    /// Pose les réglages demandés, ou rend le **message de refus** qui nomme ce qui existe.
    ///
    /// ⚠️ La clé est cherchée au registre, jamais posée telle quelle : `--reglage` écrit une propriété
    /// système, et sans registre elle en écrirait **n'importe laquelle**, y compris celles de la
    /// plateforme. Le registre est aussi ce qui permet au refus de nommer les clés admises plutôt que
    /// de laisser chercher.
    ///
    /// Rend un message plutôt que de sortir : le refus se décide ici, mais c'est `main` qui sait
    /// comment sortir - ce qui rend ce chemin éprouvable sans lancer de processus.
    ///
    /// @param reglages les valeurs brutes de `--reglage`, sous la forme `cle=valeur`
    /// @return le message de refus, ou vide si tout a été posé
    static Optional<String> poserLesReglages(List<String> reglages) {
        for (String reglage : reglages) {
            int egal = reglage.indexOf('=');
            if (egal <= 0 || egal == reglage.length() - 1) {
                return Optional.of("Réglage mal écrit : « " + reglage
                        + " ». La forme attendue est --reglage <cle>=<valeur>, par exemple --reglage "
                        + CleDeReglage.IMPORT_ZIP_MAX_ENTREES.nom() + "=5000.");
            }
            String nom = reglage.substring(0, egal);
            String valeur = reglage.substring(egal + 1);
            Optional<CleDeReglage> cle = CleDeReglage.parNom(nom);
            if (cle.isEmpty()) {
                return Optional.of("Réglage inconnu : « " + nom + " ». Les réglages admis sont : "
                        + CleDeReglage.nomsAdmis() + ".");
            }
            System.setProperty(cle.get().propriete(), valeur);
        }
        return Optional.empty();
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
