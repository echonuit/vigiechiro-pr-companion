/// Module JavaFX de VigieChiro PR Companion.
///
/// `open module` (et non `module` + `opens fr.univ_amu.iut to javafx.fxml`) : choix volontaire
/// d'ouvrir tout le module à la réflexion. Plus permissif, mais plus simple : FXML et Guice
/// instancient par réflexion sans avoir à déclarer des `opens ... to` ciblés.
open module vigiechiro {
    // JavaFX dependencies (alignées sur les 4 deps + javafx-media du pom).
    requires transitive javafx.base;
    requires transitive javafx.controls;
    requires transitive javafx.graphics;
    requires transitive javafx.fxml;
    requires transitive javafx.media;

    // javafx.swing (SwingFXUtils) + java.desktop (ImageIO / BufferedImage) : capture d'écran
    // hors-écran de l'outil `commun.outils.ApercuFx`.
    requires javafx.swing;
    requires java.desktop;

    // Persistance : API JDBC + driver SQLite (module automatique).
    requires java.sql;
    requires org.xerial.sqlitejdbc;

    // Injection de dépendances (Guice 7). `open module` ouvre déjà tous les paquets
    // à la réflexion, donc Guice peut instancier les classes sans `opens ... to`.
    requires com.google.guice;

    // Composant audio fourni pour la SAÉ 2.01 (sonogramme / spectrogramme).
    requires fr.nedjar.vigiechiro.audio;

    // Icônes vectorielles FontAwesome 5 via Ikonli (FontIcon), utilisées par l'écran d'accueil du
    // socle. Le pack `fontawesome5` est requis pour que son IkonProvider soit découvert par le
    // ServiceLoader d'Ikonli dans le graphe de modules (JPMS).
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;

    // Paquet de base exporté. `open module` ouvre déjà tous les paquets à la réflexion (FXML, Guice),
    // donc les paquets `view` des features n'ont pas besoin d'export explicite.
    exports fr.univ_amu.iut;
}
