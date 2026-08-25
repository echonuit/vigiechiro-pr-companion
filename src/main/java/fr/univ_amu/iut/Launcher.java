package fr.univ_amu.iut;

import fr.univ_amu.iut.cli.Cli;
import java.util.Arrays;
import java.util.function.Consumer;

/// Point d'entrée **unique** du lancement empaqueté (fat-jar et emballages jpackage) : il ouvre la
/// fenêtre ou répond en texte, selon le **mot** qu'on lui a donné.
///
/// [App] étend `javafx.application.Application` : la lancer directement depuis un fat-jar (JavaFX sur
/// le *classpath*, pas sur le *module-path*) provoque l'erreur « JavaFX runtime components are
/// missing ». Cette classe, qui **n'étend pas** `Application`, sert de `main-class` aux emballages et
/// délègue à [App#main(String[])], ce qui contourne le contrôle. En développement on lance toujours
/// par `./mvnw javafx:run` ou par [Cli] ; ce `Launcher` ne sert qu'aux artefacts distribués.
///
/// [#MOT_FENETRE] déclare l'ouverture de l'interface graphique ; **tout le reste** part à [Cli], qui
/// rend son code de sortie au shell. Le mot n'est pas à la charge de qui double-clique : chaque
/// emballage l'écrit pour lui - `jpackage --arguments ihm` le dépose dans le `.cfg` du lanceur, le
/// `.desktop` et le script Flatpak le portent dans leur `Exec`.
///
/// Une invocation **sans aucun argument** n'ouvre donc pas la fenêtre : elle rend l'usage de la ligne
/// de commande, parce que personne n'a demandé de fenêtre. C'est un changement pour `java -jar
/// vigiechiro-*-shaded.jar`, qui ouvrait l'interface et demande désormais `ihm` (#4071). C'est la
/// différence entre **déclarer** et **déduire** (ADR 3828) : « zéro argument, donc fenêtre » serait une
/// condition ambiante tenant lieu de déclaration.
public final class Launcher {

    /// Le mot qui déclare l'ouverture de la fenêtre. C'est une surface **publique** : il est écrit
    /// dans le `.cfg` des lanceurs jpackage, dans les fichiers `.desktop` et dans le script de
    /// lancement du Flatpak. Le changer casse le double-clic de tous les emballages déjà installés.
    static final String MOT_FENETRE = "ihm";

    private Launcher() {}

    public static void main(String[] args) {
        aiguiller(args, App::main, Cli::main);
    }

    /// L'aiguillage seul, sans JavaFX ni injecteur : ses deux destinations sont **fournies**, ce qui
    /// permet de l'éprouver sans ouvrir de fenêtre ni toucher à une base.
    ///
    /// @param args arguments reçus du lanceur
    /// @param fenetre destination du mot [#MOT_FENETRE] ; elle le reçoit **retiré** de ses arguments,
    ///     car JavaFX les expose tels quels dans `Parameters` et le mot n'y regarde personne
    /// @param texte destination de tout le reste, arguments intacts
    static void aiguiller(String[] args, Consumer<String[]> fenetre, Consumer<String[]> texte) {
        if (args.length > 0 && MOT_FENETRE.equals(args[0])) {
            fenetre.accept(Arrays.copyOfRange(args, 1, args.length));
        } else {
            texte.accept(args);
        }
    }
}
