package fr.univ_amu.iut;

/// Point d'entrée pour le **lancement empaqueté** (fat-jar et installeurs jpackage).
///
/// [App] étend `javafx.application.Application` : la lancer directement depuis un fat-jar (JavaFX sur
/// le *classpath*, pas sur le *module-path*) provoque l'erreur « JavaFX runtime components are
/// missing ». Cette classe, qui **n'étend pas** `Application`, sert de `main-class` à l'installeur :
/// elle délègue à [App#main(String[])], ce qui contourne le contrôle de JavaFX.
///
/// Côté développement, on continue de lancer l'application via `./mvnw javafx:run` ([App] reste le
/// point d'entrée). Ce `Launcher` n'est utilisé que pour les artefacts distribués.
public final class Launcher {

    private Launcher() {}

    public static void main(String[] args) {
        App.main(args);
    }
}
