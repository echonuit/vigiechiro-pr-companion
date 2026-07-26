package fr.univ_amu.iut.analyse.view;

import javafx.beans.NamedArg;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.shape.Rectangle;

/// `LineChart` de l'activité qui matérialise la **fenêtre nocturne** (coucher → lever) par un **aplat
/// pâle** derrière les courbes (#2352). On voit d'un coup d'œil si de l'activité déborde en période
/// diurne, ce qui est un signal de dispositif autant qu'écologique.
///
/// Sous-classe (plutôt qu'un calque voisin) pour dessiner dans le **repère du plot** via
/// `getPlotChildren` : la bande s'aligne exactement sur l'axe sans translation de coordonnées, et se
/// repositionne à chaque mise en page (redimensionnement) en surchargeant [#layoutPlotChildren].
public class GrapheNocturne extends LineChart<Number, Number> {

    private final Rectangle aplat = new Rectangle();
    private Double debutMinutes;
    private Double finMinutes;

    public GrapheNocturne(@NamedArg("xAxis") Axis<Number> xAxis, @NamedArg("yAxis") Axis<Number> yAxis) {
        super(xAxis, yAxis);
        aplat.getStyleClass().add("aplat-nuit");
        aplat.setMouseTransparent(true);
        aplat.setManaged(false);
        aplat.setVisible(false);
        getPlotChildren().add(aplat);
    }

    /// Définit la fenêtre nocturne à matérialiser, en minutes depuis l'origine de l'axe (18 h), ou
    /// l'**efface** (`null`) quand elle n'est pas calculable (sans GPS) ou pas unique (vue multi-nuits).
    public void definirFenetreNuit(Double debutMinutes, Double finMinutes) {
        this.debutMinutes = debutMinutes;
        this.finMinutes = finMinutes;
        requestLayout();
    }

    @Override
    protected void layoutPlotChildren() {
        super.layoutPlotChildren();
        if (debutMinutes == null || finMinutes == null) {
            aplat.setVisible(false);
            return;
        }
        double x1 = getXAxis().getDisplayPosition(debutMinutes);
        double x2 = getXAxis().getDisplayPosition(finMinutes);
        aplat.setX(Math.min(x1, x2));
        aplat.setWidth(Math.abs(x2 - x1));
        aplat.setY(0);
        aplat.setHeight(getYAxis().getHeight());
        aplat.setVisible(true);
        aplat.toBack(); // derrière les courbes, sur le fond du plot
    }
}
