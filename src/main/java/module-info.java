/// Module JavaFX pour le SAÉ 2.01 - VigieChiro PR Companion.
///
/// Ce module exporte les paquetages nécessaires pour les exercices. Ajoutez les exports des
/// nouveaux paquetages d'exercices au fur et à mesure.
///
/// `open module` (et pas `module` + `opens fr.univ_amu.iut to javafx.fxml`) : choix volontaire
/// par cohérence avec l'ancien R202/template-tp-javafx (qui ouvrait tout pour simplifier la vie
/// de l'étudiant·e qui n'a pas à comprendre les nuances JPMS la première fois). Plus permissif
/// mais pédagogiquement plus simple.
open module tp1.javafx {
    // JavaFX dependencies (alignées sur les 4 deps + javafx-media du pom).
    requires transitive javafx.base;
    requires transitive javafx.controls;
    requires transitive javafx.graphics;
    requires transitive javafx.fxml;
    requires transitive javafx.media;

    // Export base package
    exports fr.univ_amu.iut;

    // ========== EXERCICES - Ajouter les exports ici ==========
    // exports fr.univ_amu.iut.exercice1;
    // exports fr.univ_amu.iut.exercice2;
    // ...
}
