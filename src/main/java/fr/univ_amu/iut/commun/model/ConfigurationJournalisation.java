package fr.univ_amu.iut.commun.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/// Amorçage de la **journalisation** de l'application (`java.util.logging`), à appeler **une fois** au
/// démarrage - IHM ([fr.univ_amu.iut.App]) comme CLI ([fr.univ_amu.iut.cli.Cli]). Sans elle, les
/// journaux n'existaient nulle part : le backend slf4j est en `nop` (test), et les quelques `Logger` du
/// code écrivaient à FINE, invisibles par défaut - rien à inspecter après un incident (#1523).
///
/// En plus de la console héritée (INFO), elle installe sur le logger racine :
///
/// - un **fichier tournant** dans `<workspace>/logs/` (5 fichiers de 2 Mo), pour qu'il reste une trace
///   **après** l'incident, même quand l'utilisateur a fermé l'application ;
/// - le niveau **FINE** sur `fr.univ_amu.iut`, pour que le fichier capte le détail de nos propres classes
///   sans encombrer la console (qui reste à INFO).
///
/// **Idempotent** : un seul jeu de handlers, même appelée plusieurs fois (Launcher -> App). En cas
/// d'échec d'ouverture du fichier, se rabat sur la console sans lever : mieux vaut une application sans
/// fichier de log qu'une application qui ne démarre pas. Non instanciable.
public final class ConfigurationJournalisation {

    private static final String PATRON_FICHIER = "vigiechiro-%g.log";
    private static final int TAILLE_MAX_OCTETS = 2_000_000;
    private static final int NOMBRE_FICHIERS = 5;

    /// Retient le logger de l'application pour empêcher sa **récupération par le GC** : un logger sans
    /// référence forte peut être collecté, et son niveau (FINE) serait alors réinitialisé.
    private static Logger loggerApplication;

    private static boolean configuree;

    private ConfigurationJournalisation() {}

    /// Installe le fichier de journal tournant dans `dossierLogs` (créé au besoin) sur le logger racine,
    /// et fixe `fr.univ_amu.iut` à FINE. **Idempotent** : les appels suivants ne font rien.
    public static synchronized void configurer(Path dossierLogs) {
        if (configuree) {
            return;
        }
        if (installer(Logger.getLogger(""), dossierLogs) == null) {
            return; // échec déjà signalé ; on réessaiera au prochain démarrage
        }
        loggerApplication = Logger.getLogger("fr.univ_amu.iut");
        loggerApplication.setLevel(Level.FINE);
        configuree = true;
        loggerApplication.info(() -> "Journalisation initialisée : " + dossierLogs);
    }

    /// Comme [#configurer(Path)], mais **sans la console** : réservé à la ligne de commande (#3570).
    ///
    /// La JVM installe d'office un `ConsoleHandler` sur le logger racine, que personne n'avait retiré.
    /// Un incident partait donc **deux fois** vers l'utilisateur : la phrase que la CLI écrit, et le
    /// `SEVERE` avec sa pile entière, déversé sur la sortie d'erreur par le journal. `dev-docs/cli.md`
    /// promet « message seul, jamais la trace » ; la promesse était fausse pour **tout** incident de la
    /// CLI, pas seulement pour ceux du démarrage.
    ///
    /// ⚠️ Pour la CLI **seulement**, et c'est un arbitrage : l'IHM garde sa console, parce que personne
    /// ne script sa sortie et qu'un développeur qui lance `javafx:run` y lit ses journaux. Le trajet des
    /// journaux est un choix de **surface**, pas un réglage global.
    ///
    /// La trace, elle, n'est pas perdue : elle reste dans `<workspace>/logs/`, qui est l'endroit prévu
    /// pour la lire.
    public static synchronized void configurerSansConsole(Path dossierLogs) {
        // AVANT de configurer, et non après : `configurer` journalise elle-même son installation, et
        // cette ligne partait sur la sortie d'erreur de tout script. Retirer la console ensuite laissait
        // passer exactement une ligne parasite - le genre de reste qui fait dire « c'est corrigé ».
        retirerLaConsole(Logger.getLogger(""));
        configurer(dossierLogs);
    }

    /// Retire les `ConsoleHandler` de `racine`, et **eux seuls** : le fichier de journal reste.
    ///
    /// Séparée pour être éprouvée sans toucher au logger racine réel, comme [#installer].
    static void retirerLaConsole(Logger racine) {
        for (Handler installe : racine.getHandlers()) {
            if (installe instanceof ConsoleHandler) {
                racine.removeHandler(installe);
            }
        }
    }

    /// Crée `dossierLogs` et installe un fichier de journal tournant sur le logger `racine`. Renvoie le
    /// handler (pour le fermer en test) ou `null` si le fichier n'a pas pu être ouvert. Extrait de
    /// [#configurer] pour être testable **sans** toucher au logger racine réel ni au drapeau d'idempotence.
    static FileHandler installer(Logger racine, Path dossierLogs) {
        try {
            Files.createDirectories(dossierLogs);
            FileHandler fichier = new FileHandler(
                    dossierLogs.resolve(PATRON_FICHIER).toString(), TAILLE_MAX_OCTETS, NOMBRE_FICHIERS, true);
            fichier.setFormatter(new SimpleFormatter());
            fichier.setLevel(Level.ALL);
            racine.addHandler(fichier);
            return fichier;
        } catch (IOException echec) {
            // On installe justement le journal : impossible de le tracer autrement qu'en dernier recours.
            System.err.println("Journalisation : fichier indisponible dans " + dossierLogs + " (" + echec + ")");
            return null;
        }
    }
}
