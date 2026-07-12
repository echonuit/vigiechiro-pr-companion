package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.EtatEtape;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;

/// Stepper réutilisable : reconstruit un conteneur de **puces d'étapes** (une par étape, colorée selon
/// son [EtatEtape] : franchie / courante / à venir). Mutualise le rendu identique des steppers de
/// M-Passage (workflow) et de M-Lot (dépôt) : même boucle, même dérivation de classe CSS
/// (`etape-<etat>`), mêmes classes `.etape*` (dans `design.css`). Les features fournissent seulement
/// leur type d'étape et la façon d'en extraire libellé et état.
public final class Stepper {

    private Stepper() {}

    /// Vide `conteneur` puis y ajoute une puce [Label] par étape, stylée `etape` + `etape-<etat>`.
    ///
    /// @param conteneur le conteneur à reconstruire (ex. le `HBox` de classe `stepper`)
    /// @param etapes    les étapes à afficher, dans l'ordre
    /// @param libelle   le libellé court affiché dans la puce
    /// @param etat      l'état d'avancement de l'étape (dérive la classe CSS, donc la couleur)
    public static <T> void reconstruire(
            Pane conteneur, List<T> etapes, Function<T, String> libelle, Function<T, EtatEtape> etat) {
        conteneur.getChildren().clear();
        for (T etape : etapes) {
            Label puce = new Label(libelle.apply(etape));
            puce.getStyleClass()
                    .addAll("etape", "etape-" + etat.apply(etape).name().toLowerCase(Locale.ROOT));
            conteneur.getChildren().add(puce);
        }
    }
}
