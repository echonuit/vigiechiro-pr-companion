package fr.univ_amu.iut.cli;

import picocli.CommandLine.Help.Ansi;

/// Le mode couleur de la ligne de commande, **choisi par le produit** et non déduit par la plateforme.
///
/// ## Pourquoi ce choix est explicite
///
/// picocli applique par défaut son heuristique `AUTO`, et elle ne décide pas la même chose partout : la
/// première exécution de la suite sous Windows (#3526) a montré une aide **colorisée** là où Linux la
/// rend nue. `Usage: ESC[1mvigiechiro recuperer-vigiechiro…` - un utilisateur dont la console ne rend
/// pas l'ANSI y lit `←[1mvigiechiro`, et un script qui filtre la sortie se comporte différemment selon
/// le poste.
///
/// ⚠️ Ce n'est pas la couleur qui posait problème, c'est que **personne ne l'avait choisie**. Le dépôt
/// refuse déjà cela ailleurs : les captures épinglent locale et fuseau pour que l'aperçu montre le
/// produit et non la machine qui l'a rendu (#3389).
///
/// ⚠️ Et l'heuristique n'était pas reproductible : forcer `-Dpicocli.ansi=true` sous Linux laisse la
/// sortie nue. On ne peut pas s'en remettre à une règle qu'on ne sait pas rejouer.
///
/// ## La règle
///
/// De la couleur pour un humain devant un terminal ; jamais dans un tuyau, un fichier ou un journal de
/// CI. Et [`NO_COLOR`](https://no-color.org) donne le dernier mot à l'utilisateur.
public final class CouleurCli {

    private CouleurCli() {}

    /// Le mode retenu pour cette exécution.
    public static Ansi choisie() {
        return choisie(System.console() != null, System.getenv("NO_COLOR"));
    }

    /// La décision, sur des entrées **fournies** plutôt que lues du système.
    ///
    /// ⚠️ Sans cette couture, la règle ne serait éprouvable qu'en manipulant la console et
    /// l'environnement du processus de test - c'est-à-dire pas du tout. Même raison que `TailleFichier`
    /// (#3627) et `GestesFichiers` (#3525) : ce qui ne se fabrique pas de façon portable s'injecte.
    ///
    /// @param terminalInteractif vrai quand la sortie va vers une console, faux quand elle est redirigée
    /// @param noColor la valeur de `NO_COLOR`, ou `null` si la variable n'est pas posée
    static Ansi choisie(boolean terminalInteractif, String noColor) {
        // La convention veut que la variable compte dès qu'elle est **présente et non vide**, quelle que
        // soit sa valeur : `NO_COLOR=0` désactive donc la couleur, aussi surprenant que cela paraisse.
        // La respecter à moitié serait pire que l'ignorer.
        if (noColor != null && !noColor.isEmpty()) {
            return Ansi.OFF;
        }
        return terminalInteractif ? Ansi.ON : Ansi.OFF;
    }
}
