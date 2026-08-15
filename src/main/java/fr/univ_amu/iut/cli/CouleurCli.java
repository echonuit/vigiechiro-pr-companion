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
/// CI. Et l'utilisateur a le dernier mot **dans les deux sens** :
///
/// | Variable | Effet | Convention |
/// |---|---|---|
/// | [`NO_COLOR`](https://no-color.org) | éteint, toujours | spécifiée |
/// | `FORCE_COLOR` | allume, même sans console | usage répandu, non spécifié |
///
/// ⚠️ **`NO_COLOR` l'emporte** quand les deux sont posées : un refus explicite prime sur une demande
/// explicite. C'est le choix de la plupart des outils, et le seul défendable - se tromper dans ce sens
/// affiche du texte nu, se tromper dans l'autre crache des séquences d'échappement chez quelqu'un qui
/// a demandé qu'on ne le fasse pas.
///
/// ## Pourquoi `FORCE_COLOR` a été ajoutée (#3796)
///
/// La règle précédente disait « `NO_COLOR` donne le dernier mot ». Elle ne le donnait que dans **un**
/// sens : rien n'allumait. Trois situations ordinaires en souffraient, et elles ont en commun que la
/// sortie est **redirigée alors qu'un humain la lit** :
///
/// - `vigiechiro … | less -R`, où la couleur se rend très bien ;
/// - un journal de CI qui **interprète** l'ANSI - les Actions GitHub le font ;
/// - `script`, `unbuffer`, ou tout enrobage qui n'expose pas de console à la JVM.
///
/// ⚠️ `no-color.org` ne spécifie **que** l'extinction : il n'y a pas de norme à citer pour l'allumage,
/// donc ce choix se justifie au lieu de se déduire.
public final class CouleurCli {

    private CouleurCli() {}

    /// Le mode retenu pour cette exécution.
    public static Ansi choisie() {
        return choisie(System.console() != null, System.getenv("NO_COLOR"), System.getenv("FORCE_COLOR"));
    }

    /// La décision, sur des entrées **fournies** plutôt que lues du système.
    ///
    /// ⚠️ Sans cette couture, la règle ne serait éprouvable qu'en manipulant la console et
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
