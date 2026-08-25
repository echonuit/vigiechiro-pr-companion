package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.Horodatage;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;

/// Une colonne de **date** dans une table : la valeur est la date, l'affichage est français (#4019).
///
/// Ces colonnes portaient la chaîne **ISO** telle que la base la stocke, et pour une raison qui se
/// tient : une colonne de chaînes **trie lexicalement**, et l'ISO est le seul format où ce tri reste
/// chronologique. Franciser l'affichage seul, comme #3997 et #3971 l'ont fait ailleurs, aurait rendu
/// la colonne triable dans le désordre, `01/07/2026` avant `22/06/2026`, sans rien faire rougir.
///
/// Ce composant sépare donc ce qui se **trie** de ce qui s'**affiche** : la valeur est une
/// [LocalDate], donc l'ordre est chronologique par construction, et le libellé vient d'une cellule.
/// Un comparateur posé sur une colonne de chaînes aurait marché **et** se serait perdu à la première
/// colonne recopiée : il vit à côté de la valeur, pas dedans.
///
/// Une date absente ou illisible est `null`, donc rangée en fin de tri ; son affichage suit la
/// convention de sa table, vide en général et le cadratin dans la table audio (#3236). La CLI, elle,
/// rend la chaîne telle quelle : c'est un flux qu'on relit, pas un tableau qu'on trie.
public final class ColonneDate {

    private ColonneDate() {}
    /// Câble `colonne` : valeur = la date lue depuis `isoDeLaLigne`, affichage = `22/06/2026`, cellule
    /// **vide** quand la date manque.
    public static <L> void configurer(TableColumn<L, LocalDate> colonne, Callback<L, String> isoDeLaLigne) {
        configurer(colonne, isoDeLaLigne, "");
    }

    /// Variante qui **nomme la marque d'absence**.
    ///
    /// ⚠️ Elle existe parce que la table audio a sa propre règle, et qu'elle est écrite : « carré, point,
    /// commune, date et fichier : cinq colonnes, une seule règle » (#3236, cadratin et non tiret tapé à la
    /// main). Une colonne de date qui se viderait là où ses quatre voisines marquent l'absence romprait une
    /// convention **déjà décidée** - et c'est son test qui l'a rappelé, en rougissant.
    ///
    /// @param colonne la colonne à câbler
    /// @param isoDeLaLigne d'une ligne, sa date au format ISO (`null` accepté)
    /// @param marqueAbsence ce que rend une cellule sans date - vide ailleurs, le cadratin côté audio
    public static <L> void configurer(
            TableColumn<L, LocalDate> colonne, Callback<L, String> isoDeLaLigne, String marqueAbsence) {
        colonne.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(analyser(isoDeLaLigne.call(c.getValue()))));
        colonne.setCellFactory(col -> cellule(marqueAbsence));
    }

    /// La date d'une chaîne ISO, ou `null` si elle est absente ou illisible.
    public static LocalDate analyser(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(iso);
        } catch (DateTimeParseException illisible) {
            return null;
        }
    }

    /// Le libellé français d'une chaîne ISO - `22/06/2026` - ou la chaîne telle quelle si elle est
    /// illisible.
    ///
    /// ⚠️ Exposé pour les **filtres** : la recherche texte du multisite cherche dans la date de la
    /// ligne, et elle cherchait dans l'ISO. Franciser l'affichage sans elle aurait fait taper
    /// `2026-06-22` à qui lit `22/06/2026`.
    public static String libelle(String iso) {
        return Horodatage.dateSeule(iso);
    }

    private static <L> TableCell<L, LocalDate> cellule(String marqueAbsence) {
        return new TableCell<>() {
            @Override
            protected void updateItem(LocalDate date, boolean vide) {
                super.updateItem(date, vide);
                if (vide) {
                    setText(null);
                } else {
                    setText(date == null ? marqueAbsence : Horodatage.dateSeule(date.toString()));
                }
            }
        };
    }
}
