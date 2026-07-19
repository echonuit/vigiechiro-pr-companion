package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.commun.view.VueCompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.importation.viewmodel.NuitVM;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.ObservableList;
import javafx.scene.layout.VBox;

/// Remplit la zone **multi-nuits** de M-Import : la table des nuits, puis ce qui **bloque** la
/// numérotation (#801) - aucune nuit incluse, ou des numéros déjà pris.
///
/// Ce blocage était une phrase unique, qui disait « un ou plusieurs numéros » sans dire lesquels (#2050).
/// C'est désormais un compte rendu : chaque numéro pris nomme sa nuit, et l'utilisateur sait quelle ligne
/// de la table corriger. Extrait du [ImportationController] pour le garder sous le plafond de taille.
final class ZoneNuits {

    private ZoneNuits() {}

    /// Tous les détails sont montrés : ils sont aussi nombreux que les nuits en conflit, donc bornés par
    /// la table juste au-dessus. Un « … et N autre(s) » renverrait à une liste que l'on voit déjà.
    private static final int DETAILS_MONTRES = VueCompteRendu.SANS_PLAFOND;

    static void remplir(VBox zone, ObservableList<NuitVM> nuits, ReadOnlyObjectProperty<CompteRendu> blocage) {
        zone.getChildren().add(TableNuits.creer(nuits));
        VBox rendu = new VBox();
        zone.getChildren().add(rendu);
        blocage.addListener((observable, avant, apres) -> afficher(rendu, apres));
        afficher(rendu, blocage.get());
    }

    private static void afficher(VBox zone, CompteRendu rendu) {
        zone.getChildren().setAll(VueCompteRendu.rendre(rendu, DETAILS_MONTRES).getChildren());
        zone.getStyleClass().setAll(VueCompteRendu.CLASSE_RACINE);
        zone.setVisible(!rendu.estVide());
        zone.setManaged(!rendu.estVide());
    }
}
