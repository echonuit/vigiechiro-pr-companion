package fr.univ_amu.iut.commun.view;

import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;

/// Le **repère visuel des espèces à enjeu** (#2353), dit une seule fois pour les trois écrans qui le
/// portent : la revue (colonne-indicateur), l'inventaire (cellule du nom) et l'activité (case à cocher).
///
/// Chacun le **place** différemment (ce que représente une ligne n'y est pas le même), mais tous
/// désignent le **même fait**. Trois littéraux de glyphe et trois libellés d'infobulle laissaient trois
/// occasions de diverger : il aurait suffi qu'un écran change d'icône pour que le même fait cesse de se
/// reconnaître d'un écran à l'autre.
///
/// **Jamais la couleur seule** : la forme du bouclier et l'infobulle portent l'information
/// indépendamment de la teinte, qui ne survit ni au daltonisme, ni à l'impression, ni au thème sombre.
/// Le jeton `-couleur-enjeu` vit dans `palette.css`, donc il suivra ce dernier le jour où il sera câblé
/// (#1037).
public final class RepereEspeceAEnjeu {

    /// Bouclier FontAwesome : une protection, pas une alerte. L'enjeu porte sur l'espèce, pas sur la
    /// qualité de la donnée : ce qui écarte le triangle d'avertissement et le point d'interrogation,
    /// déjà pris par d'autres indicateurs.
    public static final String ICONE = "fas-shield-alt";

    /// Classe CSS du bouclier, définie dans `design.css` (partagée, deux écrans la portent).
    public static final String STYLE = "icone-enjeu";

    /// Ce que dit l'infobulle. Nomme **le plan**, pas le fait brut : « à enjeu » sans source laisserait
    /// croire à un jugement du produit.
    public static final String INFOBULLE = "Espèce prioritaire du Plan National d'Actions Chiroptères";

    private RepereEspeceAEnjeu() {}

    /// Une icône neuve, prête à être posée. **Neuve à chaque appel** : un nœud JavaFX n'a qu'un seul
    /// parent, une instance partagée disparaîtrait de la ligne précédente en apparaissant sur la suivante.
    public static FontIcon icone() {
        FontIcon icone = new FontIcon(ICONE);
        icone.getStyleClass().add(STYLE);
        return icone;
    }

    /// L'infobulle qui accompagne le repère.
    public static Tooltip infobulle() {
        return new Tooltip(INFOBULLE);
    }
}
