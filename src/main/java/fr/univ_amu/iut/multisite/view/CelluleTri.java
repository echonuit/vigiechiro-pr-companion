package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.multisite.model.TriMultisite;
import javafx.scene.control.ListCell;

/// Cellule-bouton de la liste de tri (#370) : préfixe « Tri : » devant l'ordre **sélectionné**, sans
/// toucher aux items du menu déroulant (qui restent bruts, via le converter). Remplace l'ancienne
/// étiquette « Tri : » posée avant la liste. Extraite de [MultisiteController] pour garder ce dernier
/// sous le seuil PMD `NcssCount` (la classe-cellule anonyme alourdissait le controller).
final class CelluleTri extends ListCell<TriMultisite> {

    @Override
    protected void updateItem(TriMultisite tri, boolean vide) {
        super.updateItem(tri, vide);
        setText(vide || tri == null ? null : "Tri : " + MultisiteController.libelleTri(tri));
    }
}
