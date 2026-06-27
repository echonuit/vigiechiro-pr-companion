package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/// Légende de la carte multisite (#152) : explique le **code couleur des marqueurs** (statut workflow du
/// dernier passage d'un point) et le **remplissage des carrés** (densité de passages). Construite depuis la
/// palette de [ConstructeurDonneesCarte] pour rester cohérente avec ce qui est réellement dessiné.
///
/// Accessibilité (#163) : chaque entrée associe une couleur **et un libellé** (jamais la seule couleur).
final class LegendeCarte {

    private LegendeCarte() {}

    /// Construit le panneau de légende (à superposer en coin de carte). Repliable : un bouton chevron
    /// réduit la légende à son seul en-tête pour dégager la carte (#152), et la rouvre.
    static Node creer() {
        VBox corps = new VBox(4);
        corps.getStyleClass().add("legende-corps");
        corps.getChildren().add(titre("Statut du dernier passage"));
        for (StatutWorkflow statut : StatutWorkflow.values()) {
            corps.getChildren().add(ligneStatut(statut));
        }
        corps.getChildren().add(titre("Densité de passages"));
        corps.getChildren().add(ligneDensite());

        Button bascule = new Button("▾");
        bascule.getStyleClass().add("bascule-legende");
        bascule.setAccessibleText("Replier la légende");
        bascule.setOnAction(evenement -> {
            boolean ouverte = !corps.isVisible();
            corps.setVisible(ouverte);
            corps.setManaged(ouverte);
            bascule.setText(ouverte ? "▾" : "▸");
            bascule.setAccessibleText(ouverte ? "Replier la légende" : "Déplier la légende");
        });

        Label entete = titre("Légende");
        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);
        HBox barre = new HBox(6, entete, espace, bascule);
        barre.setAlignment(Pos.CENTER_LEFT);

        VBox boite = new VBox(4, barre, corps);
        boite.getStyleClass().add("legende-carte");
        boite.setPadding(new Insets(8));
        boite.setMaxWidth(Region.USE_PREF_SIZE);
        boite.setMaxHeight(Region.USE_PREF_SIZE);
        boite.setStyle("-fx-background-color: rgba(255,255,255,0.9); -fx-background-radius: 6;"
                + " -fx-border-color: #d0d7de; -fx-border-radius: 6;");
        boite.setAccessibleText("Légende de la carte : couleur des points par statut, densité des carrés");
        return boite;
    }

    private static Label titre(String texte) {
        Label titre = new Label(texte);
        titre.setStyle("-fx-font-weight: bold;");
        return titre;
    }

    private static Node ligneStatut(StatutWorkflow statut) {
        Circle pastille = new Circle(6, ConstructeurDonneesCarte.couleurStatut(statut));
        pastille.setStroke(Color.WHITE);
        pastille.setStrokeWidth(1);
        HBox ligne = new HBox(6, pastille, new Label(statut.libelle()));
        ligne.setAlignment(Pos.CENTER_LEFT);
        return ligne;
    }

    private static Node ligneDensite() {
        Rectangle faible = echantillon(ConstructeurDonneesCarte.couleurDensite(1, 10));
        Rectangle forte = echantillon(ConstructeurDonneesCarte.couleurDensite(10, 10));
        HBox ligne = new HBox(6, faible, forte, new Label("peu → beaucoup"));
        ligne.setAlignment(Pos.CENTER_LEFT);
        return ligne;
    }

    private static Rectangle echantillon(Color couleur) {
        Rectangle rectangle = new Rectangle(14, 14, couleur);
        rectangle.setStroke(Color.web("#2c3e50"));
        rectangle.setStrokeWidth(1);
        return rectangle;
    }
}
