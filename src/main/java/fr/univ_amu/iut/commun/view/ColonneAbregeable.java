package fr.univ_amu.iut.commun.view;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;

/// Colonne de texte qui **assume** de couper ce qu'elle montre, parce que le survol le rend en entier
/// (#5113).
///
/// Un nom de fichier de soixante caractères ou le détail d'un constat ne tiennent dans aucune largeur
/// raisonnable : la cellule finit par une ellipse. Ce n'est acceptable que si le texte se relit
/// ailleurs, et l'infobulle est cet ailleurs. Les deux gestes se posent donc **ensemble**, ici, et
/// dans cet ordre : l'infobulle d'abord, la marque ensuite. Une marque posée seule ferait taire le
/// garde des captures sur un texte que personne ne peut plus lire ; il la refuse.
public final class ColonneAbregeable {

    /// Classe CSS **marqueur**, sans règle de style, par laquelle une vue assume qu'un texte se
    /// raccourcisse quand la place manque. Le garde des captures la lit.
    public static final String MARQUE = "abregeable";

    /// Au-delà, l'infobulle passe à la ligne au lieu de s'étirer sur la largeur de l'écran.
    private static final double LARGEUR_MAX_INFOBULLE = 480;

    private ColonneAbregeable() {}

    /// Donne à chaque cellule de `colonne` une infobulle qui porte son texte entier, puis marque la
    /// colonne. Remplace la fabrique de cellules : à réserver à une colonne de texte simple.
    public static <S> void assumer(TableColumn<S, String> colonne) {
        colonne.setCellFactory(c -> cellule());
        if (!colonne.getStyleClass().contains(MARQUE)) {
            colonne.getStyleClass().add(MARQUE);
        }
    }

    /// Cellule de texte dont l'infobulle répète le texte. Une cellule vide n'en porte pas : il n'y
    /// aurait qu'une bulle vide au survol.
    private static <S> TableCell<S, String> cellule() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String valeur, boolean vide) {
                super.updateItem(valeur, vide);
                if (vide || valeur == null || valeur.isBlank()) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(valeur);
                    setTooltip(infobulle(valeur));
                }
            }
        };
    }

    private static Tooltip infobulle(String texte) {
        Tooltip infobulle = new Tooltip(texte);
        infobulle.setWrapText(true);
        infobulle.setMaxWidth(LARGEUR_MAX_INFOBULLE);
        return infobulle;
    }
}
