package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.recette.Attente;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.text.Text;

/// Juge les pastilles qu'une colonne de table **dessine**, par le procédé de `TableNuitsTest` (#5111)
/// étendu aux autres colonnes qui passent par [ColonneBadge] (#6101).
///
/// Une cellule qui manque de place ne déborde pas : elle remplace la fin de son texte par une
/// ellipse, et `getText()` continue de rendre le libellé entier. Ce juge lit donc le nœud de texte
/// que l'habillage de la cellule a posé, et le compare au libellé que la cellule a reçu.
///
/// Il mesure avec les polices de la machine qui l'exécute. Une colonne qui tient ici peut couper
/// ailleurs si la police diffère : c'est pourquoi il tourne aussi en CI.
public final class PastillesEntieres {

    private static final long DELAI_MS = 5_000L;

    private PastillesEntieres() {}

    /// Une pastille telle qu'elle est à l'écran : le libellé reçu, le texte dessiné, et la place.
    ///
    /// @param recu ce que la cellule a reçu de sa colonne
    /// @param dessine ce que le nœud de texte montre, ellipse comprise
    /// @param largeurDuTexte la largeur du texte dessiné, en pixels
    /// @param largeurDisponible la largeur de la cellule, marges intérieures retirées
    public record Pastille(String recu, String dessine, double largeurDuTexte, double largeurDisponible) {

        /// Vrai quand le texte dessiné est le libellé reçu et qu'il tient entre les marges.
        public boolean entiere() {
            return recu.equals(dessine) && largeurDuTexte <= largeurDisponible;
        }
    }

    /// Lit les pastilles que `colonne` dessine dans `table`, après une passe de mise en page.
    ///
    /// Seules les lignes que la table a bâties sont lues : une table trop basse pour ses lignes
    /// en rend moins qu'on n'en a semé, et c'est à l'appelant de s'en apercevoir en comparant.
    public static List<Pastille> lire(TableView<?> table, TableColumn<?, ?> colonne) {
        return Attente.surLeFil(
                () -> {
                    table.applyCss();
                    table.layout();
                    return table.lookupAll(".table-cell").stream()
                            .filter(TableCell.class::isInstance)
                            .map(noeud -> (TableCell<?, ?>) noeud)
                            .filter(cellule -> cellule.getTableColumn() == colonne)
                            .filter(cellule -> cellule.getStyleClass().contains("badge"))
                            .filter(cellule -> cellule.getText() != null
                                    && !cellule.getText().isBlank())
                            .map(PastillesEntieres::mesurer)
                            .toList();
                },
                "lire les pastilles de la colonne « " + colonne.getText() + " »",
                DELAI_MS);
    }

    /// Lit les pastilles de `colonne`, refuse celles qui sont coupées, et rend les libellés reçus.
    ///
    /// L'appelant compare ce retour aux libellés qu'il a semés : sans cela, une table restée vide
    /// passerait ce juge sans avoir rien montré.
    ///
    /// @param ouElargir le fichier qui déclare la largeur, repris dans le message de refus
    public static List<String> libellesEntiers(TableView<?> table, TableColumn<?, ?> colonne, String ouElargir) {
        List<Pastille> pastilles = lire(table, colonne);
        assertThat(pastilles)
                .as(
                        "la colonne « %s » (%.0f px) coupe un libellé : l'élargir dans %s",
                        colonne.getText(), colonne.getWidth(), ouElargir)
                .allMatch(Pastille::entiere);
        return pastilles.stream().map(Pastille::recu).toList();
    }

    /// Lit ce que dessinent les libellés visibles de `conteneur`, hors de toute table (#4834).
    ///
    /// La lecture est celle des pastilles : un libellé d'action à l'étroit finit lui aussi par une
    /// ellipse pendant que `getText()` rend le texte entier. Un libellé enroulable s'y lit de même, son
    /// nœud de texte gardant le texte entier tant que la hauteur ne lui manque pas.
    ///
    /// Les libellés sans texte sont écartés : un bouton qui ne porte qu'un graphique n'a rien à couper.
    public static List<Pastille> lireLesLibelles(Node conteneur) {
        return Attente.surLeFil(
                () -> {
                    conteneur.applyCss();
                    conteneur.getScene().getRoot().layout();
                    List<Pastille> lues = new ArrayList<>();
                    collecter(conteneur, lues);
                    return lues;
                },
                "lire les libellés que le conteneur dessine",
                DELAI_MS);
    }

    /// Descend dans `noeud` sans entrer dans ce qui est masqué : un libellé retiré de l'écran n'est pas lu.
    private static void collecter(Node noeud, List<Pastille> lues) {
        if (!noeud.isVisible()) {
            return;
        }
        if (noeud instanceof Labeled libelle
                && libelle.getText() != null
                && !libelle.getText().isBlank()) {
            lues.add(mesurer(libelle));
        }
        if (noeud instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(enfant -> collecter(enfant, lues));
        }
    }

    private static Pastille mesurer(Labeled libelle) {
        Text rendu = (Text) libelle.lookup(".text");
        double disponible = libelle.getWidth()
                - libelle.getInsets().getLeft()
                - libelle.getInsets().getRight();
        return new Pastille(
                libelle.getText(), rendu.getText(), rendu.getLayoutBounds().getWidth(), disponible);
    }
}
