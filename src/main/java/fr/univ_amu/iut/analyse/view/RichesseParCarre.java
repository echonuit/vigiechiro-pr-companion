package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.validation.model.CarreEspeces;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.InvalidationListener;
import javafx.collections.ObservableList;
import javafx.scene.control.TableView;

/// Correspondance **carré → richesse** (nombre d'espèces distinctes), dérivée de l'inventaire par carré
/// de l'analyse. Extraite d'[AnalyseController] (plafond NcssCount) : la colonne « Espèces du carré »
/// du détail lit cette richesse, tenue à jour à chaque changement de l'inventaire.
final class RichesseParCarre {

    private final Map<String, Integer> parNumero = new HashMap<>();

    /// Se branche sur l'`inventaireParCarre` : à chaque changement, recalcule la table et rafraîchit
    /// `tableADrafraichir` (dont une colonne lit [#libelle]).
    void brancher(ObservableList<CarreEspeces> inventaireParCarre, TableView<?> tableADrafraichir) {
        inventaireParCarre.addListener((InvalidationListener) observable -> {
            majDepuis(inventaireParCarre);
            tableADrafraichir.refresh();
        });
    }

    /// Richesse du carré `numeroCarre`, ou `—` si inconnue de l'inventaire.
    String libelle(String numeroCarre) {
        Integer richesse = parNumero.get(numeroCarre);
        return richesse == null ? "—" : richesse.toString();
    }

    private void majDepuis(List<CarreEspeces> carres) {
        parNumero.clear();
        for (CarreEspeces carre : carres) {
            parNumero.put(carre.numeroCarre(), carre.richesse());
        }
    }
}
