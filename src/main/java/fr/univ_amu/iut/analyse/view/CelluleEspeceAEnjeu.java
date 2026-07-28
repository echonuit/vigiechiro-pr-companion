package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.MarqueurEspecesAEnjeu;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;

/// Cellule du nom d'espèce de l'inventaire, ornée d'un **bouclier** quand l'espèce est **prioritaire** au
/// sens du Plan National d'Actions Chiroptères (#2353).
///
/// Le repère se pose **dans la cellule du nom**, et non dans une colonne dédiée comme sur l'écran de
/// revue : ici une ligne **est** une espèce, l'information lui appartient déjà. Dépenser une colonne pour
/// la porter à côté ferait un détour.
///
/// **Jamais la couleur seule** : la forme du glyphe et l'infobulle portent l'information indépendamment de
/// la teinte, qui ne survit ni au daltonisme ni à l'impression.
final class CelluleEspeceAEnjeu {

    /// Glyphe et classe CSS partagés avec l'écran de revue, pour que le même fait se reconnaisse d'un
    /// écran à l'autre.
    private static final String ICONE = "fas-shield-alt";

    private static final String STYLE = "icone-enjeu";

    private CelluleEspeceAEnjeu() {}

    static TableCell<EspeceAgregee, String> cellule(MarqueurEspecesAEnjeu marqueur) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String libelle, boolean vide) {
                super.updateItem(libelle, vide);
                EspeceAgregee espece =
                        getTableRow() == null ? null : getTableRow().getItem();
                setText(vide ? null : libelle);
                if (vide || espece == null || !marqueur.aEnjeu(espece.code())) {
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    FontIcon icone = new FontIcon(ICONE);
                    icone.getStyleClass().add(STYLE);
                    setGraphic(icone);
                    setTooltip(new Tooltip("Espèce prioritaire du Plan National d'Actions Chiroptères"));
                }
            }
        };
    }
}
