package fr.univ_amu.iut.cli;

import picocli.CommandLine.Help.Ansi;

/// Le mode couleur de la ligne de commande, **choisi par le produit** et non déduit par la plateforme.
///
/// L'heuristique `AUTO` de picocli ne décide pas la même chose partout : sous Windows l'aide sortait
/// colorisée là où Linux la rend nue (#3526), et elle n'était pas rejouable. Ce n'est pas la couleur
/// qui posait problème, c'est que personne ne l'avait choisie - comme les captures épinglent locale
/// et fuseau pour montrer le produit et non la machine (#3389).
///
/// De la couleur pour un humain devant un terminal, jamais dans un tuyau ni un journal ;
/// [`NO_COLOR`](https://no-color.org) éteint, `FORCE_COLOR` allume même sans console (#3796), et
/// **`NO_COLOR` l'emporte**, un refus explicite primant sur une demande explicite.
public final class CouleurCli {

    private CouleurCli() {}

    /// Le mode retenu pour cette exécution.
    public static Ansi choisie() {
        return choisie(System.console() != null, System.getenv("NO_COLOR"), System.getenv("FORCE_COLOR"));
    }

    /// La décision, sur des entrées **fournies** plutôt que lues du système.
    ///
    /// Sans cette couture, la règle ne serait éprouvable qu'en manipulant la console et
    /// l'environnement du processus de test - c'est-à-dire pas du tout. Même raison que `TailleFichier`
    /// (#3627) et `GestesFichiers` (#3525) : ce qui ne se fabrique pas de façon portable s'injecte.
    ///
    /// @param terminalInteractif vrai quand la sortie va vers une console, faux quand elle est redirigée
    /// @param noColor la valeur de `NO_COLOR`, ou `null` si la variable n'est pas posée
    /// @param forceColor la valeur de `FORCE_COLOR`, ou `null` si la variable n'est pas posée
    static Ansi choisie(boolean terminalInteractif, String noColor, String forceColor) {
        // La convention veut qu'une variable compte dès qu'elle est **présente et non vide**, quelle que
        // soit sa valeur : `NO_COLOR=0` désactive donc la couleur, aussi surprenant que cela paraisse.
        // La respecter à moitié serait pire que l'ignorer. `FORCE_COLOR` suit la même lecture, pour que
        // l'utilisateur n'ait pas deux règles à retenir.
        if (posee(noColor)) {
            return Ansi.OFF;
        }
        if (posee(forceColor)) {
            return Ansi.ON;
        }
        return terminalInteractif ? Ansi.ON : Ansi.OFF;
    }

    /// Une variable d'environnement **présente et non vide**.
    private static boolean posee(String valeur) {
        return valeur != null && !valeur.isEmpty();
    }
}
