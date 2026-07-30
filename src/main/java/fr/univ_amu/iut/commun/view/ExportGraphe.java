package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.outils.ApercuFx;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.Scene;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/// Export d'un **graphe temporel** en image : il **redessine** ce qu'il exporte, il ne le capture pas
/// (ADR 2348).
///
/// La distinction n'est pas cosmétique. Photographier le nœud affiché rend une image **vide ou noire**
/// dès que le graphe est masqué ou accéléré matériellement : un export qui échoue en **silence**, tout
/// en produisant un fichier d'apparence normale. On reconstruit donc un graphe neuf dans une scène
/// transitoire, hors écran.
///
/// Deux écrans exportent ainsi : la courbe d'activité par espèce (#2352) et la courbe climatique du
/// diagnostic (#2618). L'appelant fournit ce qui lui est propre (ses séries, la graduation de son axe,
/// sa bande de nuit, ses lignes de contexte), ce socle porte le geste commun.
///
/// Les séries arrivent par un **fournisseur** et non toutes faites : une `XYChart.Series` n'appartient
/// qu'à un graphe à la fois, et emprunter celles de l'écran les lui **retirerait** sous les yeux de
/// l'utilisateur.
public final class ExportGraphe {

    /// Dimensions de la scène d'export : assez large pour que les étiquettes, la légende et les lignes de
    /// contexte tiennent sans être comprimées : `ApercuFx` refuse une image aux libellés tronqués.
    private static final int LARGEUR = 1100;

    private static final int HAUTEUR = 640;

    private ExportGraphe() {}

    /// Redessine le graphe hors écran et l'écrit en PNG dans `fichier`. À appeler sur le fil JavaFX.
    ///
    /// @param series fournit des séries **neuves** à chaque appel
    /// @param configurerAxeX gradue l'axe du temps, comme à l'écran
    /// @param titreAxeY intitulé de l'axe des ordonnées
    /// @param fenetreNuit bande coucher → lever en unités de l'axe, ou `null` quand elle est inconnue
    /// @param lignesLegende contexte à estampiller sous le graphe, dans l'ordre
    /// @param fichier le PNG à écrire
    public static void ecrire(
            Supplier<List<XYChart.Series<Number, Number>>> series,
            Consumer<NumberAxis> configurerAxeX,
            String titreAxeY,
            double[] fenetreNuit,
            List<String> lignesLegende,
            Path fichier) {
        NumberAxis axeTemps = new NumberAxis();
        axeTemps.setLabel("Heure");
        configurerAxeX.accept(axeTemps);
        NumberAxis axeValeurs = new NumberAxis();
        axeValeurs.setLabel(titreAxeY);

        GrapheNocturne graphe = new GrapheNocturne(axeTemps, axeValeurs);
        graphe.setAnimated(false);
        graphe.setLegendVisible(true);
        graphe.getData().setAll(series.get());
        if (fenetreNuit != null) {
            graphe.definirFenetreNuit(fenetreNuit[0], fenetreNuit[1]);
        }
        VBox.setVgrow(graphe, Priority.ALWAYS);

        VBox racine = new VBox(6.0, graphe);
        racine.getChildren()
                .addAll(lignesLegende.stream().map(ExportGraphe::ligne).toList());
        racine.getStyleClass().add("export-graphe");

        Scene scene = new Scene(racine, LARGEUR, HAUTEUR);
        scene.getStylesheets()
                .addAll(
                        ExportGraphe.class
                                .getResource("/fr/univ_amu/iut/commun/view/palette.css")
                                .toExternalForm(),
                        ExportGraphe.class
                                .getResource("/fr/univ_amu/iut/commun/view/design.css")
                                .toExternalForm());
        ApercuFx.enregistrerPng(scene, fichier);
    }

    /// Une ligne de contexte. Non enroulable : la scène est assez large, et un libellé comprimé ferait
    /// **refuser** la capture : ce qui est le bon comportement, mais qu'on n'a pas à provoquer.
    private static Label ligne(String texte) {
        Label libelle = new Label(texte);
        libelle.getStyleClass().add("legende-export");
        return libelle;
    }
}
