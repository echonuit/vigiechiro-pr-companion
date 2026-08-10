package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.commun.model.PointsDuCarre;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// Le **point d'écoute** d'un passage, tel que la modale « Modifier le passage » permet de le corriger
/// (#1495).
///
/// ## Ce que cet objet garde ensemble
///
/// Le point **actuel** - référence de l'« avant » du renommage -, le point **choisi**, et les points du
/// même carré entre lesquels choisir. Les trois ne se lisent qu'ensemble : comparer l'un sans l'autre ne
/// veut rien dire, et c'est cette cohésion qui justifie de les sortir du ViewModel plutôt que d'y ajouter
/// trois champs de plus (plafond God Class).
///
/// ## Le carré, et pas au-delà
///
/// La liste est bornée aux points du carré courant. Déplacer une nuit d'un carré à l'autre n'est pas une
/// correction de saisie : c'est un autre geste, qui n'a pas sa place dans cette modale.
public final class ChoixDuPoint {

    private final PointsDuCarre pointsDuCarre;
    private final StringProperty choisi = new SimpleStringProperty(this, "choisi", "");
    private final ObservableList<String> disponibles = FXCollections.observableArrayList();
    private String actuel = "";

    ChoixDuPoint(PointsDuCarre pointsDuCarre) {
        this.pointsDuCarre = Objects.requireNonNull(pointsDuCarre, "pointsDuCarre");
    }

    /// Charge le point courant et ses frères pour le carré `carre`.
    ///
    /// Sans implémentation branchée - injecteurs partiels des outils et de la ligne de commande - la
    /// liste rendue est vide : on garde alors **le point courant** comme seul choix, faute de quoi
    /// l'écran offrirait une liste vide sur une valeur qui, elle, existe.
    void charger(String carre, String codePoint) {
        actuel = Objects.requireNonNull(codePoint, "codePoint");
        choisi.set(codePoint);
        List<String> freres = pointsDuCarre.codes(carre);
        disponibles.setAll(freres.isEmpty() ? List.of(codePoint) : freres);
    }

    /// Le point tel qu'il était à l'ouverture : l'« avant » du renommage.
    String actuel() {
        return actuel;
    }

    /// Le point choisi, modifiable depuis l'écran.
    public StringProperty choisiProperty() {
        return choisi;
    }

    /// Les points du même carré, offerts au choix.
    public ObservableList<String> disponibles() {
        return disponibles;
    }
}
