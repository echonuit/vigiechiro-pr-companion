package fr.univ_amu.iut.commun.view;

import javafx.beans.value.ObservableValue;
import javafx.scene.Node;

/// Lie la **visibilité** d'un nœud à une condition en gardant `managed` synchronisé avec `visible` :
/// un nœud masqué ne doit pas occuper d'espace dans la mise en page (sinon un « trou » subsisterait).
/// Motif partagé par les écrans à **sections conditionnelles** (import, diagnostic…).
public final class VisibiliteGeree {

    private VisibiliteGeree() {}

    /// Lie à la fois `visible` et `managed` du nœud à `condition`.
    public static void lier(Node noeud, ObservableValue<? extends Boolean> condition) {
        noeud.visibleProperty().bind(condition);
        noeud.managedProperty().bind(condition);
    }
}
