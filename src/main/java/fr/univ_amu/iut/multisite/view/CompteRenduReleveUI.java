package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.commun.view.PanneauCompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import java.util.Objects;
import javafx.beans.value.ObservableValue;
import javafx.scene.layout.VBox;

/// Câble la bande de compte rendu du **relevé groupé** (#2757) sur sa zone de l'écran multisite.
///
/// La zone est repliée (`visible` et `managed` à faux) tant qu'il n'y a rien à rendre : un `VBox` vide mais
/// géré laisserait une gouttière que rien n'explique.
///
/// ## Pourquoi aucune action en pied
///
/// La suite naturelle d'un relevé partiel serait « relever à nouveau ». Elle n'y figure pas, pour la raison
/// que [fr.univ_amu.iut.lot.view.CompteRenduDepotUI] donne déjà : un compte rendu **ne double pas un
/// bouton** que l'utilisateur a par ailleurs. Le geste vit dans le menu ☰ de l'écran, à un clic, et
/// l'avertissement du compte rendu dit explicitement qu'un nouveau relevé reprendra les nuits manquantes.
final class CompteRenduReleveUI {

    private CompteRenduReleveUI() {}

    static void cabler(VBox zone, ObservableValue<CompteRenduChiffre> compteRendu) {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(compteRendu, "compteRendu");
        PanneauCompteRendu bande = new PanneauCompteRendu();
        // Identifiant posé ici, faute de FXML : c'est par lui que les tests de vue l'atteignent, comme
        // `#compteRenduChiffre` sur la modale de réactivation.
        bande.setId("compteRenduReleve");
        zone.getChildren().setAll(bande);
        compteRendu.addListener((observable, avant, apres) -> afficher(zone, bande, apres));
        afficher(zone, bande, compteRendu.getValue());
    }

    private static void afficher(VBox zone, PanneauCompteRendu bande, CompteRenduChiffre compteRendu) {
        if (compteRendu != null) {
            bande.afficher(compteRendu);
        }
        zone.setVisible(compteRendu != null);
        zone.setManaged(compteRendu != null);
    }
}
