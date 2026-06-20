package fr.univ_amu.iut.commun.view;

/// Ouvre une URL externe dans le navigateur du système.
///
/// Abstrait (interface) pour deux raisons : rester **testable** (un faux enregistre l'URL
/// demandée sans ouvrir de navigateur) et **découpler** les contrôleurs du `HostServices`
/// JavaFX (qui n'existe qu'au lancement de l'application graphique, pas en CLI/tests).
public interface OuvreurDeLien {

    /// Ouvre `url` dans le navigateur par défaut. Ne lève jamais d'exception : si aucun
    /// navigateur n'est disponible (mode CLI/headless), l'appel est silencieusement ignoré
    /// (journalisé).
    void ouvrir(String url);
}
