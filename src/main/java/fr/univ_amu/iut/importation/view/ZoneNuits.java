package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.importation.viewmodel.NuitVM;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/// Remplit la zone **multi-nuits** de M-Import : la table des nuits, puis un **avertissement de blocage**
/// de la numérotation (#801) — « aucune nuit incluse » ou « n° déjà pris » — jusqu'ici muet (l'import se
/// désactivait en silence). Extrait du [ImportationController] pour le garder sous le plafond de taille
/// (PMD `NcssCount`).
final class ZoneNuits {

    private ZoneNuits() {}

    static void remplir(VBox zone, ObservableList<NuitVM> nuits, ReadOnlyStringProperty avertissement) {
        zone.getChildren().add(TableNuits.creer(nuits));
        Label avert = new Label();
        avert.setWrapText(true);
        avert.getStyleClass().add("insp-incoherence");
        avert.textProperty().bind(avertissement);
        avert.visibleProperty().bind(avertissement.isNotEmpty());
        avert.managedProperty().bind(avertissement.isNotEmpty());
        zone.getChildren().add(avert);
    }
}
