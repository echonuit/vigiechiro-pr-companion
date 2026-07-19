package fr.univ_amu.iut.commun.view;

import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/// Lie l'**icône** d'un contrôle à l'état qui pilote déjà son **libellé**.
///
/// Certains boutons changent de sens selon l'état : « Téléverser sur Vigie-Chiro » devient « Reprendre le
/// dépôt », « Marquer déposé » devient « Lancer la participation », « Carte » devient « Tableau ». Leur
/// libellé suit par un `Bindings.when(…)` ; leur icône, elle, restait figée sur le premier des deux sens et
/// disait donc le contraire du mot une fois sur deux - un nuage sur « Reprendre », une carte sur
/// « Tableau ».
///
/// Le geste tient en une ligne, mais il se recopiait en quatre à chaque fois, dans des contrôleurs déjà au
/// plafond de taille. Il vit donc ici, à côté des autres patrons de contrôle du socle.
///
/// ```java
/// IconeSelonEtat.lier(iconeCarte, carteAffichee, FontAwesomeSolid.TABLE, FontAwesomeSolid.MAP);
/// ```
public final class IconeSelonEtat {

    private IconeSelonEtat() {}

    /// Fait porter à `icone` le glyphe `siVrai` tant que `condition` est vraie, `siFaux` sinon.
    ///
    /// @param icone l'icône du contrôle, nommée dans son FXML pour être atteignable
    /// @param condition l'état qui pilote déjà le libellé du contrôle
    /// @param siVrai le glyphe du sens que le contrôle prend quand la condition tient
    /// @param siFaux le glyphe de son sens par défaut
    public static void lier(FontIcon icone, ObservableBooleanValue condition, Ikon siVrai, Ikon siFaux) {
        Objects.requireNonNull(icone, "icone");
        Objects.requireNonNull(condition, "condition");
        icone.iconCodeProperty()
                .bind(Bindings.when(condition)
                        .then(Objects.requireNonNull(siVrai, "siVrai"))
                        .otherwise(Objects.requireNonNull(siFaux, "siFaux")));
    }
}
