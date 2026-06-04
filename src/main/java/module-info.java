/// Module JavaFX pour le SAÉ 2.01 - VigieChiro PR Companion.
///
/// Ce module exporte les paquetages des features. Ajoutez les exports des paquetages `view` des
/// features que vous construisez au fur et à mesure.
///
/// `open module` (et pas `module` + `opens fr.univ_amu.iut to javafx.fxml`) : choix volontaire
/// par cohérence avec l'ancien R202/template-tp-javafx (qui ouvrait tout pour simplifier la vie
/// de l'étudiant·e qui n'a pas à comprendre les nuances JPMS la première fois). Plus permissif
/// mais pédagogiquement plus simple.
open module vigiechiro {
    // JavaFX dependencies (alignées sur les 4 deps + javafx-media du pom).
    requires transitive javafx.base;
    requires transitive javafx.controls;
    requires transitive javafx.graphics;
    requires transitive javafx.fxml;
    requires transitive javafx.media;

    // javafx.swing (SwingFXUtils) + java.desktop (ImageIO / BufferedImage) : capture d'écran
    // hors-écran de l'outil enseignant `commun.outils.ApercuFx`. À retirer en passe A2 si l'outil
    // de capture est supprimé de la version étudiante.
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

    // Export base package
    exports fr.univ_amu.iut;

// ========== FEATURES - Ajouter les exports ici au fil de l'eau ==========
// exports fr.univ_amu.iut.sites.view;
// exports fr.univ_amu.iut.passage.view;
// ...
}
