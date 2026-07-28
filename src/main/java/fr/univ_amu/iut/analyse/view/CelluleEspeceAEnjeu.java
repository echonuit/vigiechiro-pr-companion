package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.commun.view.RepereEspeceAEnjeu;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.MarqueurEspecesAEnjeu;
import javafx.scene.control.TableCell;
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

    private CelluleEspeceAEnjeu() {}

    static TableCell<EspeceAgregee, String> cellule(MarqueurEspecesAEnjeu marqueur) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String libelle, boolean vide) {
                super.updateItem(libelle, vide);
                EspeceAgregee espece =
                        getTableRow() == null ? null : getTableRow().getItem();
                setText(vide ? null : libelle);
                if (vide || espece == null) {
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                // Le bouclier occupe une GOUTTIÈRE de largeur fixe, présente même quand l'espèce n'est pas
                // prioritaire : sinon les noms non marqués commenceraient plus à gauche que les autres, et
                // la colonne d'identité — celle qu'on parcourt du regard — perdrait son bord aligné.
                boolean aEnjeu = marqueur.aEnjeu(espece.code());
                FontIcon icone = RepereEspeceAEnjeu.icone();
                icone.setVisible(aEnjeu);
                setGraphic(icone);
                setTooltip(aEnjeu ? RepereEspeceAEnjeu.infobulle() : null);
            }
        };
    }
}
