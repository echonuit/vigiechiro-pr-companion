package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.commun.view.carte.CarteSites;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import java.util.LinkedHashSet;
import java.util.Set;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;

/// **Carte de répartition** de l'écran « Espèces & observations » : encapsule le composant socle
/// [CarteSites] et ses overlays (légende de richesse, bouton « recadrer »), et tient son contenu à jour à
/// partir de l'inventaire par carré et de la répartition de l'espèce sélectionnée. Extrait du
/// `AnalyseController` (qui reste un pur câblage léger), sur le modèle des helpers carte de `multisite`.
///
/// La carte se (re)construit quand elle **devient visible**, quand l'**inventaire par carré** change
/// (filtre statut), ou quand la **sélection d'espèce** change (surbrillance). Le recadrage n'a lieu qu'à
/// l'ouverture (sinon la vue sauterait à chaque changement de sélection).
final class CarteRepartition {

    private final CarteSites carte = new CarteSites();
    private final ObservableList<CarreEspeces> carres;
    private final ObservableList<String> carresSurbrilles;
    private final ReadOnlyBooleanProperty affichee;

    CarteRepartition(
            ObservableList<CarreEspeces> carres,
            ObservableList<String> carresSurbrilles,
            ReadOnlyBooleanProperty affichee) {
        this.carres = carres;
        this.carresSurbrilles = carresSurbrilles;
        this.affichee = affichee;
    }

    /// Installe la carte et ses overlays dans `zone`, et branche les rafraîchissements.
    void installerDans(StackPane zone) {
        zone.getChildren().add(carte);

        Node legende = construireLegende();
        StackPane.setAlignment(legende, Pos.BOTTOM_LEFT);
        StackPane.setMargin(legende, new Insets(8));
        zone.getChildren().add(legende);

        Button recadrer = new Button("⤢");
        recadrer.getStyleClass().add("carte-recadrer");
        recadrer.setAccessibleText("Recadrer la carte");
        recadrer.setOnAction(evenement -> carte.recadrer());
        StackPane.setAlignment(recadrer, Pos.TOP_RIGHT);
        StackPane.setMargin(recadrer, new Insets(8));
        zone.getChildren().add(recadrer);

        carte.setOnCarreClic(carreGeo -> carte.surbrillanceCarre(carreGeo.numeroCarre()));

        affichee.addListener((obs, ancien, visible) -> {
            if (Boolean.TRUE.equals(visible)) {
                rendreEtRecadrer();
            }
        });
        carres.addListener((ListChangeListener<CarreEspeces>) changement -> rafraichir(false));
        carresSurbrilles.addListener((ListChangeListener<String>) changement -> rafraichir(false));

        // Installation **paresseuse** : la carte n'est créée/installée qu'au premier passage en mode Carte.
        // Le listener ci-dessus ayant été ajouté après cette transition, on déclenche ici le rendu initial.
        if (affichee.get()) {
            rendreEtRecadrer();
        }
    }

    /// Reconstruit la carte puis, **après le layout** (la zone vient de passer `managed`), redemande un
    /// recadrage : le moteur de tuiles de la MapView ne démarre qu'au pulse suivant, sans quoi le fond de
    /// carte resterait vide jusqu'à la première interaction.
    private void rendreEtRecadrer() {
        rafraichir(true);
        Platform.runLater(carte::recadrer);
    }

    /// (Re)construit les données de la carte ; sans effet si la carte n'est pas affichée. `recadrer` ne
    /// vaut `true` qu'à l'ouverture.
    private void rafraichir(boolean recadrer) {
        if (!affichee.get()) {
            return;
        }
        Set<String> surbrilles = new LinkedHashSet<>(carresSurbrilles);
        carte.setDonnees(ConstructeurDonneesCarteEspeces.depuis(carres, surbrilles), recadrer);
    }

    /// Légende minimale : un dégradé « peu → beaucoup » d'espèces par carré, superposé à la carte.
    /// L'intitulé reprend exactement le nom de la colonne du tableau (« Espèces du carré ») pour que carte
    /// et tableau parlent le même langage.
    private Node construireLegende() {
        Label titre = new Label("Espèces du carré");
        titre.getStyleClass().add("carte-legende-titre");
        Rectangle degrade = new Rectangle(90, 10);
        degrade.setArcWidth(4);
        degrade.setArcHeight(4);
        degrade.setFill(new LinearGradient(
                0,
                0,
                1,
                0,
                true,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#1e8449", 0.15)),
                new Stop(1, Color.web("#1e8449", 0.60))));
        Label peu = new Label("peu");
        Label beaucoup = new Label("beaucoup");
        peu.getStyleClass().add("carte-legende-borne");
        beaucoup.getStyleClass().add("carte-legende-borne");
        HBox echelle = new HBox(6, peu, degrade, beaucoup);
        echelle.setAlignment(Pos.CENTER_LEFT);
        VBox boite = new VBox(4, titre, echelle);
        boite.getStyleClass().add("carte-legende");
        boite.setMaxWidth(Region.USE_PREF_SIZE);
        boite.setMaxHeight(Region.USE_PREF_SIZE);
        return boite;
    }
}
