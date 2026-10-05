package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;

/// Le **repli manuel** de l'écran de lot (#5867) : quand Vigie-Chiro refuse des séquences sans recours,
/// la carte des archives revient, sous l'étape qui vient d'être refusée et sous un titre qui dit ce
/// qu'elle est devenue.
///
/// C'est la même carte que l'étape « Générer les archives de dépôt », et non une seconde : son bouton,
/// sa barre et sa table font déjà ce que le repli demande. Seuls changent son titre, sa consigne et sa
/// place. Le fil d'étapes, lui, ne bouge pas : le repli découle du téléversement, il ne le précède pas.
final class RepliManuelUI {

    static final String TITRE_DU_REPLI = "Repli : déposer à la main";

    static final String DERNIER_GESTE_FERME =
            "À faire une fois les archives générées, puis déposées à la main sur le portail.";

    static final String DERNIER_GESTE_OUVERT = "Marquer le passage comme déposé sur Vigie-Chiro.";

    private RepliManuelUI() {}

    /// La consigne du repli : ce qui a été refusé, puis le geste.
    static String consigne(int sequences) {
        return "Vigie-Chiro a refusé " + sequences + " séquence(s). Générez les archives ZIP de la nuit,"
                + " déposez-les à la main sur le portail, puis marquez le passage déposé ci-dessous.";
    }

    /// La carte des archives, son titre, sa consigne, la carte du téléversement qui la situe, et le
    /// bouton du dernier geste avec l'enveloppe qui dit pourquoi il est grisé.
    record Vue(Node carte, Label titre, Label consigne, Node televersement, Node enveloppeMarquer, Button marquer) {

        Vue {
            Objects.requireNonNull(carte, "carte");
            Objects.requireNonNull(titre, "titre");
            Objects.requireNonNull(consigne, "consigne");
            Objects.requireNonNull(televersement, "televersement");
            Objects.requireNonNull(enveloppeMarquer, "enveloppeMarquer");
            Objects.requireNonNull(marquer, "marquer");
        }
    }

    static void cabler(Vue vue, LotViewModel lot, DepotViewModel depot) {
        BooleanBinding enRepli = EtapeDesArchives.enRepli(lot, depot);
        ReadOnlyIntegerProperty refusees = depot.sequencesRefuseesSansRecoursProperty();
        // Ce que le FXML pose vaut pour l'étape : on le garde tel quel hors du repli.
        String titreDeLEtape = vue.titre().getText();
        String consigneDeLEtape = vue.consigne().getText();
        vue.titre()
                .textProperty()
                .bind(Bindings.when(enRepli).then(TITRE_DU_REPLI).otherwise(titreDeLEtape));
        vue.consigne()
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> enRepli.get() ? consigne(refusees.get()) : consigneDeLEtape, enRepli, refusees));
        // La liaison du titre tient `enRepli` : l'écoute vit donc aussi longtemps que la carte.
        enRepli.addListener((observable, avant, apres) -> placer(vue, apres));
        placer(vue, enRepli.get());
        cablerLeDernierGeste(vue, enRepli, lot, depot);
    }

    /// « Marquer le passage déposé » : offert en repli seulement, actif une fois les archives générées.
    ///
    /// Dès qu'une participation est liée, la dernière étape de l'écran lance la participation et ne
    /// marque plus rien : un dépôt fini à la main restait « Dépôt en cours ». Le moteur autorise ce
    /// passage vers « Déposé », il lui manquait une porte.
    private static void cablerLeDernierGeste(Vue vue, BooleanBinding enRepli, LotViewModel lot, DepotViewModel depot) {
        vue.enveloppeMarquer().visibleProperty().bind(enRepli);
        vue.enveloppeMarquer().managedProperty().bind(enRepli);
        BooleanBinding ferme = lot.peutDeposerProperty()
                .not()
                .or(Bindings.isEmpty(lot.suiviLignes().lignes()))
                .or(lot.generationEnCoursProperty())
                .or(depot.enCoursProperty());
        vue.marquer().disableProperty().bind(ferme);
        IndicateurBlocage.expliquer(
                vue.enveloppeMarquer(),
                Bindings.when(ferme).then(DERNIER_GESTE_FERME).otherwise(DERNIER_GESTE_OUVERT));
    }

    /// Pose la carte juste avant celle du téléversement, ou juste après en repli.
    private static void placer(Vue vue, boolean enRepli) {
        ObservableList<Node> cartes = ((Pane) vue.carte().getParent()).getChildren();
        int actuel = cartes.indexOf(vue.carte());
        boolean dessous = actuel > cartes.indexOf(vue.televersement());
        if (dessous == enRepli) {
            return;
        }
        cartes.remove(vue.carte());
        cartes.add(cartes.indexOf(vue.televersement()) + (enRepli ? 1 : 0), vue.carte());
    }
}
