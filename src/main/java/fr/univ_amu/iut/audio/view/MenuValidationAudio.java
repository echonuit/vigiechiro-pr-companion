package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.Taxon;
import java.util.function.Supplier;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableView;

/// Sous-menu « Validation ▸ » du clic droit de Sons & validation (#1797) : porte au menu contextuel les
/// gestes de revue déjà offerts par les boutons et le clavier (valider, corriger, certitude, référence,
/// douteux), en réutilisant [ActionsSelectionAudio] (aiguillage unitaire vs lot selon la sélection).
///
/// Extrait du controller, qui est à son plafond `NcssCount` : le controller se contente de passer le menu
/// construit ici au menu contextuel de la table.
final class MenuValidationAudio {

    private MenuValidationAudio() {}

    /// Construit le menu « Validation », désactivé sans sélection. `taxonCorrection` fournit le taxon de
    /// correction courant (le ComboBox de l'écran).
    static Menu creer(
            TableView<LigneObservationAudio> table, ActionsSelectionAudio actions, Supplier<Taxon> taxonCorrection) {
        Menu menu = new Menu("Validation");
        menu.disableProperty()
                .bind(table.getSelectionModel().selectedItemProperty().isNull());

        MenuItem valider = new MenuItem("Valider");
        valider.setOnAction(evenement -> actions.valider());
        MenuItem corriger = new MenuItem("Corriger");
        corriger.setOnAction(evenement -> actions.corriger(taxonCorrection.get()));
        MenuItem reference = new MenuItem("Marquer référence");
        reference.setOnAction(evenement -> actions.basculerReference());
        MenuItem douteux = new MenuItem("Marquer douteux");
        douteux.setOnAction(evenement -> actions.basculerDouteux());

        menu.getItems().addAll(valider, corriger, certitude(actions), new SeparatorMenuItem(), reference, douteux);
        return menu;
    }

    private static Menu certitude(ActionsSelectionAudio actions) {
        Menu certitude = new Menu("Certitude");
        for (Certitude valeur : Certitude.values()) {
            MenuItem item = new MenuItem(valeur.libelle());
            item.setOnAction(evenement -> actions.poserCertitude(valeur));
            certitude.getItems().add(item);
        }
        return certitude;
    }
}
