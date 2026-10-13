package fr.univ_amu.iut.commun.view;

import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;

/// L'écoute d'un objet qui vit plus longtemps que celui qui écoute (#6184).
///
/// Un écran qui pose un écouteur sur un singleton, les réglages réactifs ou l'état de connexion, s'y
/// fait retenir : la liste des écouteurs du singleton tient l'écouteur, qui tient ce qu'il capture, un
/// contrôle et par lui tout l'écran, jusqu'à la fermeture de l'application. La fiche d'un carré puis
/// l'écran des réglages l'ont fait (#6170, #6184).
///
/// Ici l'écoute s'inscrit **faible** auprès de la source, et c'est le porteur qui tient l'écouteur : il
/// vit autant que lui, pas davantage. Les deux moitiés comptent. Sans la première, l'écran reste ; sans
/// la seconde, l'écouteur tombe au premier ramasse-miettes et l'écran cesse de suivre sans rien dire.
///
/// Une liaison (`bind`, `bindBidirectional`) n'a pas besoin de ce détour : elle ne retient rien.
public final class EcouteFaible {

    private EcouteFaible() {}

    /// Fait suivre `source` par `ecouteur` tant que `porteur` vit. `porteur` est le nœud que l'écouteur
    /// met à jour : il garde l'écouteur dans ses propriétés, et la source ne garde rien.
    public static <T> void poser(Node porteur, ObservableValue<T> source, ChangeListener<? super T> ecouteur) {
        Objects.requireNonNull(porteur, "porteur");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(ecouteur, "ecouteur");
        porteur.getProperties().put(ecouteur, ecouteur);
        source.addListener(new WeakChangeListener<>(ecouteur));
    }
}
