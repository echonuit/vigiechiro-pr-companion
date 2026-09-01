package fr.univ_amu.iut.commun.view;

import java.nio.file.Path;

/// Ouvre ce que l'application montre au-dehors : une **URL** dans le navigateur, un **dossier** dans
/// le gestionnaire de fichiers.
///
/// Abstrait (interface) pour deux raisons : rester **testable** (un faux enregistre ce qu'on lui a
/// demandé sans rien ouvrir) et **découpler** les contrôleurs du `HostServices` JavaFX (qui n'existe
/// qu'au lancement de l'application graphique, pas en CLI/tests).
///
/// **Deux méthodes, parce que ce sont deux gestes.** Le port n'en avait qu'une, et deux appelants lui
/// passaient un `file://` en croyant ouvrir un dossier. `HostServices.showDocument` confie ce `file://`
/// au navigateur, qui affiche un listing de répertoire : un observateur y a cherché comment déposer
/// ses archives, et n'a rien trouvé puisqu'il n'y a rien à y faire (#4982).
public interface OuvreurDeLien {

    /// Ouvre `url` dans le navigateur par défaut. Ne lève jamais d'exception : si aucun
    /// navigateur n'est disponible (mode CLI/headless), l'appel est silencieusement ignoré
    /// (journalisé).
    void ouvrir(String url);

    /// Ouvre `dossier` dans le gestionnaire de fichiers du système.
    ///
    /// Rend `false` quand elle n'y est pas parvenue - ce qui n'est pas une hypothèse d'école :
    /// `Desktop` n'existe pas en headless, donc ni dans les bancs ni en ligne de commande.
    /// L'appelant doit alors **dire le chemin**, et non se rabattre sur le navigateur.
    ///
    /// @param dossier le dossier à montrer
    /// @return `true` si le système l'a pris en charge
    default boolean ouvrirDossier(Path dossier) {
        // Défaut « ne sait pas » : l'interface reste fonctionnelle, donc les doubles de test qui
        // n'observent que les liens restent des lambdas. Et il ne ment pas - un ouvreur sans ce
        // geste ne sait effectivement pas ouvrir un dossier, et l'appelant dira le chemin.
        return false;
    }
}
