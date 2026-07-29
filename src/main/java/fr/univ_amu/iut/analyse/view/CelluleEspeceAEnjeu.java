package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.commun.view.RepereEspeceAEnjeu;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.MarqueurEspecesAEnjeu;
import java.util.function.Function;
import javafx.scene.control.TableCell;
import org.kordamp.ikonli.javafx.FontIcon;

/// Cellule du nom d'espèce, ornée d'un **bouclier** quand l'espèce est **prioritaire** au sens du Plan
/// National d'Actions Chiroptères (#2353).
///
/// Le repère se pose **dans la cellule du nom**, et non dans une colonne dédiée comme sur l'écran de
/// revue : sur les écrans qui la partagent, une ligne **est** une espèce, l'information lui appartient
/// déjà. Dépenser une colonne pour la porter à côté ferait un détour. La revue, elle, aligne des
/// **observations** : il lui faut une colonne, parce que le fait ne porte pas sur la ligne mais sur le
/// taxon qu'elle retient.
///
/// Deux écrans la partagent : l'inventaire (#2353) et la synthèse d'une nuit (#2348, clôture du
/// chantier). D'où le paramètre `codeDe` : les deux alignent des espèces, mais ne les représentent pas
/// avec le même type.
///
/// **Jamais la couleur seule** : la forme du glyphe et l'infobulle portent l'information indépendamment de
/// la teinte, qui ne survit ni au daltonisme ni à l'impression.
final class CelluleEspeceAEnjeu {

    private CelluleEspeceAEnjeu() {}

    /// @param marqueur sait dire si un code de taxon est prioritaire
    /// @param codeDe extrait le **code du taxon** de la ligne, quel que soit son type
    static <T> TableCell<T, String> cellule(MarqueurEspecesAEnjeu marqueur, Function<T, String> codeDe) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String libelle, boolean vide) {
                super.updateItem(libelle, vide);
                T ligne = getTableRow() == null ? null : getTableRow().getItem();
                setText(vide ? null : libelle);
                if (vide || ligne == null) {
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                // Le bouclier occupe une GOUTTIÈRE de largeur fixe, présente même quand l'espèce n'est pas
                // prioritaire : sinon les noms non marqués commenceraient plus à gauche que les autres, et
                // la colonne d'identité, celle qu'on parcourt du regard, perdrait son bord aligné.
                boolean aEnjeu = marqueur.aEnjeu(codeDe.apply(ligne));
                FontIcon icone = RepereEspeceAEnjeu.icone();
                icone.setVisible(aEnjeu);
                setGraphic(icone);
                setTooltip(aEnjeu ? RepereEspeceAEnjeu.infobulle() : null);
            }
        };
    }

    /// La cellule de l'inventaire, dont les lignes sont des [EspeceAgregee].
    static TableCell<EspeceAgregee, String> pourInventaire(MarqueurEspecesAEnjeu marqueur) {
        return cellule(marqueur, EspeceAgregee::code);
    }
}
