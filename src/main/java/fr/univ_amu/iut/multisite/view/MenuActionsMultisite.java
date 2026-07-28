package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.multisite.model.LignePassage;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerExpression;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableObjectValue;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import org.kordamp.ikonli.javafx.FontIcon;

/// État et gestes du menu **☰ actions** de la vue multi-sites, extraits de [MultisiteController] : le
/// controller y avait accumulé le câblage des quatre entrées et le sélecteur de fichier de l'export,
/// jusqu'à franchir le plafond de taille de classe. Rien de plus qu'un déplacement : la logique est
/// inchangée.
///
/// Le fil conducteur est le même pour les quatre : **une entrée qui ne peut rien faire doit dire
/// pourquoi**. Un `MenuItem` désactivé n'accueille pas de tooltip (il ne reçoit plus le survol), donc la
/// cause du grisage passe dans son **libellé** (#789) ; et l'entrée qui n'a aucun sens dans le contexte
/// (reconstruire hors connexion VigieChiro) est **retirée** plutôt que grisée.
final class MenuActionsMultisite {

    /// Les entrées du menu ☰, regroupées (#2483 : réduire l'arité des constructions).
    ///
    /// Sept paramètres du même type se distinguaient par leur seul **ordre** : les échanger deux à deux
    /// aurait compilé sans broncher, et grisé la mauvaise entrée. Le lot 3 devait encore en ajouter
    /// deux ; c'est le bon moment pour les nommer.
    record Entrees(
            MenuItem exporter,
            MenuItem ecouterLot,
            MenuItem ecouterPassage,
            MenuItem reconstruire,
            MenuItem reculerAnalyses,
            MenuItem preparerSelection,
            MenuItem televerserSelection) {}

    private MenuActionsMultisite() {}

    /// Câble l'état des entrées du menu ☰ sur l'état de l'écran.
    ///
    /// @param nonVide vrai quand le tableau filtré contient au moins une ligne
    /// @param selection ligne sélectionnée (`null` si aucune)
    /// @param reculerAnalyses item « Relever l'état des analyses » (#1338)
    /// @param peutReconstruire vrai quand la passerelle VigieChiro est présente (#1396)
    /// @param peutRelever vrai quand le relevé groupé est disponible (connecté à VigieChiro, #1338)
    static void installer(
            Entrees entrees,
            ObservableBooleanValue nonVide,
            ObservableObjectValue<LignePassage> selection,
            IntegerExpression nombreSelectionne,
            boolean peutReconstruire,
            boolean peutRelever) {
        entrees.exporter().disableProperty().bind(Bindings.not(nonVide));
        entrees.exporter().setGraphic(new FontIcon("fas-file-export"));
        entrees.exporter()
                .textProperty()
                .bind(Bindings.when(nonVide).then("Exporter…").otherwise("Exporter… (aucune ligne à exporter)"));
        // Écoute : la sélection filtrée suit la présence de lignes filtrées ; un passage exige une ligne sélectionnée.
        entrees.ecouterLot().disableProperty().bind(Bindings.not(nonVide));
        entrees.ecouterLot().setGraphic(new FontIcon("fas-headphones"));
        entrees.ecouterLot()
                .textProperty()
                .bind(Bindings.when(nonVide)
                        .then("Écouter la sélection filtrée")
                        .otherwise("Écouter la sélection filtrée (aucune ligne)"));
        entrees.ecouterPassage().disableProperty().bind(Bindings.isNull(selection));
        // Un item qui ne peut rien faire ne vaut pas mieux qu'un item absent : il vaut moins.
        entrees.reconstruire().setVisible(peutReconstruire);
        // #1338 : hors connexion, il n'y a rien à interroger — l'item se retire plutôt que de rester
        // grisé. Il n'est pas désactivé faute de nuit déposée : dans ce cas, le clic répond « rien à
        // relever » (le VM le dit), ce qui renseigne mieux qu'un item muet.
        entrees.reculerAnalyses().setVisible(peutRelever);

        // #2357 lot 3 : un item désactivé ne dit pas pourquoi (il n'accueille pas d'info-bulle), donc
        // c'est son LIBELLÉ qui porte la raison — même patron que « Exporter » ci-dessus.
        surLaSelection(
                entrees.preparerSelection(),
                nombreSelectionne,
                "Préparer le dépôt des ",
                "Préparer le dépôt de la ligne cochée…",
                "Préparer le dépôt de la sélection… (aucune ligne cochée)");
        surLaSelection(
                entrees.televerserSelection(),
                nombreSelectionne,
                "Téléverser les ",
                "Téléverser la ligne cochée…",
                "Téléverser la sélection… (aucune ligne cochée)");
    }

    /// Grisage et libellé d'une action de lot : le texte porte l'état de la sélection.
    ///
    /// Factorisé dès la deuxième action (#2357, PR 3/5) : les suivantes auront la même règle, et des
    /// copies auraient divergé au premier ajustement.
    ///
    /// Les **trois phrases** sont fournies entières plutôt que composées à partir d'un verbe : les
    /// actions ne se construisent pas toutes pareil en français. Une première version assemblait
    /// « verbe + la sélection », ce qui donnait « Préparer le dépôt **la** sélection ». Défaut vu en
    /// regardant la capture, pas en relisant le code.
    ///
    /// @param prefixePluriel début de phrase auquel le nombre puis « lignes cochées… » sont accolés
    private static void surLaSelection(
            MenuItem item, IntegerExpression nombre, String prefixePluriel, String une, String aucune) {
        item.disableProperty().bind(nombre.lessThan(1));
        item.textProperty()
                .bind(Bindings.when(nombre.greaterThan(1))
                        .then(Bindings.concat(prefixePluriel, nombre, " lignes cochées…"))
                        .otherwise(Bindings.when(nombre.isEqualTo(1)).then(une).otherwise(aucune)));
    }

    /// « Exporter » : demande où écrire et, si l'utilisateur ne renonce pas, remet le chemin choisi et
    /// **l'ordre réellement affiché** (tri par clic d'en-tête inclus, #291) au travail d'écriture.
    ///
    /// La désignation passe par le port [SelecteurFichier] (#1431), porté par l'écran. Le `FileChooser`
    /// qui vivait ici **figeait** tout test du geste : la Javadoc précédente le reconnaissait sans
    /// détour (« le dialogue vit dans la vue, non testé en TestFX »).
    static void exporter(SelecteurFichier selecteur, TableView<LignePassage> table, Consumer<Path> ecrire) {
        selecteur
                .enregistrerFichier("Exporter les passages en CSV", "vue-multisite.csv", FiltreFichier.csv())
                .ifPresent(ecrire);
    }

    /// Instantané des lignes **telles qu'affichées** (la table applique un `SortedList` par-dessus le
    /// ViewModel) : c'est cet ordre-là qu'on exporte, pas l'ordre interne du ViewModel.
    static List<LignePassage> lignesAffichees(TableView<LignePassage> table) {
        return List.copyOf(table.getItems());
    }
}
