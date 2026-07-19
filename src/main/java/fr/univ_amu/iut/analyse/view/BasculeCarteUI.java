package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.commun.view.IconeSelonEtat;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/// Câblage de la bascule **inventaire / carte** de l'écran d'analyse.
///
/// Le bouton **dit ce vers quoi il mène** : « Carte » tant qu'on voit le tableau, « Tableau » quand la
/// carte est affichée - libellé et icône **liés à l'état**, et non basculés au clic. C'est ce qui le rend
/// juste même quand la bascule vient d'**ailleurs** (« Voir sur la carte » depuis l'audio, #476).
///
/// L'installation de la carte reste à la charge de l'appelant, appelée au **premier** passage : le
/// composant Gluon Maps est lourd, et l'écran d'inventaire n'a aucune raison de le payer tant qu'on reste
/// en tableau.
final class BasculeCarteUI {

    private BasculeCarteUI() {}

    /// @param bouton le bouton de bascule
    /// @param icone son icône, qui suit son libellé
    /// @param carteAffichee l'état observé, piloté par ce bouton comme par les autres écrans
    /// @param zone le conteneur de la carte ; **vide** tant qu'elle n'a pas été installée, ce qui tient
    ///     lieu de mémo : la fabrique n'est appelée qu'au premier passage
    /// @param fabrique construit la carte, appelée une seule fois
    static void cabler(
            Button bouton,
            FontIcon icone,
            BooleanProperty carteAffichee,
            StackPane zone,
            Supplier<CarteRepartition> fabrique) {
        Objects.requireNonNull(bouton, "bouton");
        Objects.requireNonNull(carteAffichee, "carteAffichee");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(fabrique, "fabrique");
        IconeSelonEtat.lier(icone, carteAffichee, FontAwesomeSolid.TABLE, FontAwesomeSolid.MAP);
        bouton.textProperty().bind(Bindings.when(carteAffichee).then("Tableau").otherwise("Carte"));
        carteAffichee.addListener((observable, avant, affichee) -> {
            if (Boolean.TRUE.equals(affichee) && zone.getChildren().isEmpty()) {
                fabrique.get().installerDans(zone);
            }
        });
    }
}
