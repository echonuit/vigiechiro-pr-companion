package fr.univ_amu.iut.qualification.view;

import fr.univ_amu.iut.qualification.model.SequenceEnSelection;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/// Barre empilée **multi-couleur** de la répartition des verdicts PAR FICHIER d'une sélection d'écoute
/// (#1524, lot 6a) : trois segments Bon / Mauvais / Inexploitable, de largeur proportionnelle à leur
/// nombre, suivis d'une queue « non jugé » grise qui recule à mesure qu'on juge. Elle remplace la barre
/// de progression d'écoute mono-couleur : d'un coup d'œil, un segment rouge (inexploitable) qui domine
/// signale un enregistrement à problème.
///
/// **Contrôle autonome** (patron custom-control) : instancié par FXML via son constructeur sans argument,
/// il bâtit ses segments et se met à jour en observant la liste passée à [#suivre]. Le [#resumeProperty]
/// chiffré double la couleur en texte (règle #801 : ne pas encoder l'information par la seule teinte) et
/// alimente aussi l'infobulle.
public final class BarreVerdicts extends HBox {

    private final Region segmentBon = segment("segment-bon");
    private final Region segmentMauvais = segment("segment-mauvais");
    private final Region segmentInexploitable = segment("segment-inexploitable");
    private final Region segmentNonJuge = segment("segment-non-juge");

    /// Fractions de largeur des trois verdicts (le « non jugé » absorbe le reste via `hgrow`).
    private final DoubleProperty fractionBon = new SimpleDoubleProperty(this, "fractionBon", 0);
    private final DoubleProperty fractionMauvais = new SimpleDoubleProperty(this, "fractionMauvais", 0);
    private final DoubleProperty fractionInexploitable = new SimpleDoubleProperty(this, "fractionInexploitable", 0);

    private final ReadOnlyStringWrapper resume = new ReadOnlyStringWrapper(this, "resume", "");
    private final Tooltip infobulle = new Tooltip();

    public BarreVerdicts() {
        getStyleClass().add("barre-verdicts");
        // Largeur de chaque verdict = largeur de la barre × sa fraction ; « non jugé » prend le reste
        // (hgrow), ce qui évite les trous d'arrondi et fait de la queue grise le complément exact.
        segmentBon.prefWidthProperty().bind(widthProperty().multiply(fractionBon));
        segmentMauvais.prefWidthProperty().bind(widthProperty().multiply(fractionMauvais));
        segmentInexploitable.prefWidthProperty().bind(widthProperty().multiply(fractionInexploitable));
        HBox.setHgrow(segmentNonJuge, Priority.ALWAYS);
        getChildren().addAll(segmentBon, segmentMauvais, segmentInexploitable, segmentNonJuge);
        Tooltip.install(this, infobulle);
        appliquer(0, 0, 0, 0);
    }

    private static Region segment(String classe) {
        Region region = new Region();
        region.getStyleClass().addAll("segment", classe);
        region.setMinWidth(0);
        return region;
    }

    /// Observe la sélection : recalcule la répartition à chaque changement de la liste (ajout, retrait,
    /// ou remplacement d'une ligne quand un verdict par fichier est rendu).
    public void suivre(ObservableList<SequenceEnSelection> lignes) {
        lignes.addListener((ListChangeListener<SequenceEnSelection>) changement -> recalculer(lignes));
        recalculer(lignes);
    }

    private void recalculer(ObservableList<SequenceEnSelection> lignes) {
        int bon = 0;
        int mauvais = 0;
        int inexploitable = 0;
        int nonJuge = 0;
        for (SequenceEnSelection ligne : lignes) {
            switch (ligne.verdict()) {
                case BON -> bon++;
                case MAUVAIS -> mauvais++;
                case INEXPLOITABLE -> inexploitable++;
                case NON_JUGE -> nonJuge++;
            }
        }
        appliquer(bon, mauvais, inexploitable, nonJuge);
    }

    private void appliquer(int bon, int mauvais, int inexploitable, int nonJuge) {
        int total = Math.max(1, bon + mauvais + inexploitable + nonJuge);
        fractionBon.set((double) bon / total);
        fractionMauvais.set((double) mauvais / total);
        fractionInexploitable.set((double) inexploitable / total);
        String texte = resumer(bon, mauvais, inexploitable, nonJuge);
        resume.set(texte);
        infobulle.setText(texte);
    }

    private static String resumer(int bon, int mauvais, int inexploitable, int nonJuge) {
        StringBuilder resume = new StringBuilder()
                .append(bon)
                .append(" Bon · ")
                .append(mauvais)
                .append(" Mauvais · ")
                .append(inexploitable)
                .append(" Inexploitable");
        if (nonJuge > 0) {
            resume.append(" · ").append(nonJuge).append(" non jugé").append(nonJuge > 1 ? "s" : "");
        }
        return resume.toString();
    }

    /// Résumé chiffré de la répartition (« 7 Bon · 3 Mauvais · 2 Inexploitable · 18 non jugés »), pour
    /// doubler la couleur en texte (accessibilité, #801) et servir de libellé sous la barre.
    public ReadOnlyStringProperty resumeProperty() {
        return resume.getReadOnlyProperty();
    }
}
