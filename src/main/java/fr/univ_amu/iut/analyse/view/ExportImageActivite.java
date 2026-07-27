package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.analyse.model.CourbeEspece;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.Scene;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/// Export **image** de la courbe d'activité (#2352) : **redessine** le graphe hors écran, il ne le
/// **capture pas**.
///
/// La distinction n'est pas cosmétique. Photographier le nœud affiché produit une image noire dès que le
/// graphe est masqué (onglet en arrière-plan, fenêtre réduite) ou accéléré matériellement — un export qui
/// échoue **silencieusement**, en rendant un fichier d'apparence normale. On reconstruit donc un graphe
/// neuf, dans une scène transitoire dimensionnée pour l'export.
///
/// Les séries sont **reconstruites** depuis les courbes du ViewModel, jamais empruntées à l'écran : une
/// `XYChart.Series` n'appartient qu'à un graphe à la fois, et la réutiliser la **retirerait** de la vue
/// affichée. Le même [ActiviteController#versSeries] les produit, donc l'image montre exactement ce que
/// l'écran montre (étiquette au pic comprise).
///
/// L'image **porte son contexte** ([LegendeExportActivite]) : carré, point, passage, tranche, filtres
/// actifs, version et date. Une courbe sans ces mentions devient inexploitable dès qu'elle quitte
/// l'application.
public final class ExportImageActivite {

    /// Dimensions de la scène d'export : assez large pour que les étiquettes d'heures, la légende des
    /// espèces et les lignes de contexte tiennent sans être comprimées (`ApercuFx` refuse une image aux
    /// libellés tronqués).
    private static final int LARGEUR = 1100;

    private static final int HAUTEUR = 640;

    private ExportImageActivite() {}

    /// Redessine `courbes` sur un axe nocturne neuf, estampille les `lignesLegende` sous le graphe et écrit
    /// le PNG dans `fichier`. À appeler sur le fil JavaFX.
    ///
    /// @param courbes les courbes affichées à l'écran, redessinées à l'identique
    /// @param configurerAxe pose l'axe nocturne 18 h → 8 h, la même configuration qu'à l'écran
    /// @param fenetreNuit fenêtre coucher → lever en minutes sur l'axe, ou `null` (pas d'aplat)
    /// @param lignesLegende lignes de contexte à estampiller, dans l'ordre
    /// @param fichier le PNG à écrire
    public static void ecrire(
            List<CourbeEspece> courbes,
            Consumer<NumberAxis> configurerAxe,
            double[] fenetreNuit,
            List<String> lignesLegende,
            Path fichier) {
        NumberAxis axeTemps = new NumberAxis();
        axeTemps.setLabel("Heure");
        configurerAxe.accept(axeTemps);
        NumberAxis axeContacts = new NumberAxis();
        axeContacts.setLabel("Contacts");

        GrapheNocturne graphe = new GrapheNocturne(axeTemps, axeContacts);
        graphe.setAnimated(false);
        graphe.setLegendVisible(true);
        graphe.getData().setAll(ActiviteController.versSeries(courbes));
        if (fenetreNuit != null) {
            graphe.definirFenetreNuit(fenetreNuit[0], fenetreNuit[1]);
        }
        VBox.setVgrow(graphe, Priority.ALWAYS);

        VBox racine = new VBox(6.0, graphe);
        racine.getChildren()
                .addAll(lignesLegende.stream().map(ExportImageActivite::ligne).toList());
        racine.getStyleClass().add("export-activite");

        Scene scene = new Scene(racine, LARGEUR, HAUTEUR);
        scene.getStylesheets()
                .addAll(
                        ActiviteController.class
                                .getResource("/fr/univ_amu/iut/commun/view/palette.css")
                                .toExternalForm(),
                        ActiviteController.class.getResource("activite.css").toExternalForm());
        ApercuFx.enregistrerPng(scene, fichier);
    }

    /// Une ligne de légende. Non enroulable : la scène d'export est assez large pour ces lignes, et un
    /// libellé enroulable comprimé ferait **refuser** la capture (garde `ApercuFx`).
    private static Label ligne(String texte) {
        Label libelle = new Label(texte);
        libelle.getStyleClass().add("legende-export");
        return libelle;
    }
}
