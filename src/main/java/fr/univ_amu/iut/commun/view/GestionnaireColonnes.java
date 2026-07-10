package fr.univ_amu.iut.commun.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.beans.property.BooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

/// Gestionnaire **réutilisable** des colonnes d'une [TableView] : un panneau unique (façon table Notion)
/// où l'on **réordonne** les colonnes par glisser-déposer et où l'on **affiche/masque** chacune via une
/// case à cocher. Pensé pour être partagé par **toutes les vues à tables** (audio, analyse, multisite…),
/// il vit donc dans le socle (`commun.view`) et ne dépend d'aucune feature.
///
/// Deux points d'entrée sont câblés par [#installer] : un **menu contextuel** (clic droit sur la table) et
/// un item **« Colonnes… »** dans un [MenuButton] (☰) ; les deux ouvrent le **même** panneau, construit à
/// la demande sur l'ordre courant des colonnes (pas de double état à synchroniser).
public final class GestionnaireColonnes {

    private GestionnaireColonnes() {}

    /// Une colonne proposée au panneau, avec son **libellé lisible** (les colonnes-icônes ont un en-tête
    /// peu parlant) et un drapeau `visibiliteVerrouillee` : une colonne d'**identité** reste toujours
    /// affichée (case cochée et désactivée) mais peut tout de même être déplacée.
    public record Colonne(TableColumn<?, ?> colonne, String libelle, boolean visibiliteVerrouillee) {}

    /// Câble le menu contextuel (clic droit) de `table` et ajoute un item « Colonnes… » au menu `menu`
    /// (☰) ; les deux ouvrent le panneau de gestion des `colonnes`.
    public static void installer(TableView<?> table, MenuButton menu, List<Colonne> colonnes) {
        MenuItem itemContextuel = new MenuItem("Colonnes…");
        itemContextuel.setOnAction(e -> ouvrir(table, colonnes, table));
        table.setContextMenu(new ContextMenu(itemContextuel));

        MenuItem itemMenu = new MenuItem("Colonnes…");
        itemMenu.setOnAction(e -> ouvrir(table, colonnes, menu));
        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(itemMenu);
    }

    /// Affiche le panneau de gestion des colonnes dans une fenêtre flottante ancrée sous `ancre`. Un bouton
    /// **Fermer** (en plus de l'auto-masquage au clic extérieur, peu fiable selon l'environnement) garantit
    /// qu'on peut toujours refermer le panneau. Les changements (ordre, visibilité) sont appliqués **en
    /// direct**, il n'y a donc rien à « valider ».
    public static void ouvrir(TableView<?> table, List<Colonne> colonnes, Node ancre) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        VBox panneau = construirePanneau(table, colonnes);
        Button fermer = new Button("Fermer");
        fermer.setMaxWidth(Double.MAX_VALUE);
        fermer.setOnAction(e -> popup.hide());
        panneau.getChildren().add(fermer);
        popup.getContent().add(panneau);
        Bounds ecran = ancre.localToScreen(ancre.getBoundsInLocal());
        popup.show(ancre, ecran.getMinX(), ecran.getMaxY());
    }

    /// Construit le contenu du panneau (titre + liste réordonnable), sur l'**ordre courant** des colonnes
    /// dans la table. Extrait pour être testable sans fenêtre flottante.
    static VBox construirePanneau(TableView<?> table, List<Colonne> colonnes) {
        List<Colonne> ordonnees = new ArrayList<>(colonnes);
        ordonnees.sort(Comparator.comparingInt(c -> table.getColumns().indexOf(c.colonne())));

        ListView<Colonne> liste = new ListView<>(FXCollections.observableArrayList(ordonnees));
        liste.setCellFactory(vue -> new CelluleColonne(table));
        liste.setPrefHeight(Math.min(360, 34.0 * ordonnees.size() + 8));
        liste.setPrefWidth(240);

        Label titre = new Label("Colonnes  ·  glisser pour réordonner");
        titre.getStyleClass().add("titre-panneau-colonnes");
        VBox panneau = new VBox(6, titre, liste);
        panneau.getStyleClass().add("panneau-colonnes");
        panneau.setPadding(new Insets(8));
        panneau.setStyle("-fx-background-color: -fx-background; -fx-border-color: -fx-box-border;"
                + " -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 12, 0, 0, 4);");
        return panneau;
    }

    /// Réordonne les colonnes **gérées** de `table` selon `ordre`, en laissant les éventuelles colonnes
    /// non gérées à leur place (leurs positions ne bougent pas ; seules les positions occupées par des
    /// colonnes gérées reçoivent la nouvelle séquence).
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void appliquerOrdre(TableView<?> table, List<TableColumn<?, ?>> ordre) {
        Set<TableColumn<?, ?>> gerees = new HashSet<>(ordre);
        List<TableColumn<?, ?>> resultat = new ArrayList<>();
        int prochaine = 0;
        for (TableColumn<?, ?> colonne : table.getColumns()) {
            if (gerees.contains(colonne)) {
                resultat.add(ordre.get(prochaine++));
            } else {
                resultat.add(colonne);
            }
        }
        // Capture de wildcard sur ObservableList<TableColumn<S,?>> : conversion non typée ciblée (les
        // éléments sont bien des TableColumn de la même table, seule la variable S est effacée).
        ((ObservableList) table.getColumns()).setAll(resultat);
    }

    /// Déplace l'item `depuis` vers l'index `vers` dans `liste`, puis répercute l'ordre obtenu sur les
    /// colonnes de `table`. Cœur du glisser-déposer, extrait pour être testable sans simuler l'événement.
    /// Sans effet si les indices sont hors bornes ou identiques.
    static void deplacer(TableView<?> table, ListView<Colonne> liste, int depuis, int vers) {
        List<Colonne> items = liste.getItems();
        if (depuis == vers || depuis < 0 || depuis >= items.size()) {
            return;
        }
        Colonne deplacee = items.remove(depuis);
        items.add(Math.min(vers, items.size()), deplacee);
        List<TableColumn<?, ?>> ordre = new ArrayList<>();
        for (Colonne c : items) {
            ordre.add(c.colonne());
        }
        appliquerOrdre(table, ordre);
        liste.getSelectionModel().select(deplacee);
    }

    /// Cellule d'une colonne : poignée de déplacement, case de visibilité (liée à `visibleProperty`) et
    /// libellé. Porte le glisser-déposer de **réordonnancement** (déplace l'item puis applique l'ordre à la
    /// table). La liaison bidirectionnelle est détachée/rattachée à chaque recyclage de cellule.
    private static final class CelluleColonne extends ListCell<Colonne> {

        private final TableView<?> table;
        private final CheckBox visible = new CheckBox();
        /// Enveloppe du CheckBox : un CheckBox désactivé n'affiche pas de tooltip, on l'installe donc sur ce
        /// StackPane (qui reçoit le survol) pour expliquer le grisage d'une colonne verrouillée (#789).
        private final StackPane enveloppeVisible = new StackPane(visible);
        private final Tooltip infoVerrou =
                new Tooltip("Colonne toujours affichée : sa visibilité est verrouillée (colonne d'identité).");
        private final Label poignee = new Label("⋮⋮");
        private final HBox contenu = new HBox(8, poignee, enveloppeVisible);
        private BooleanProperty lieeA;

        CelluleColonne(TableView<?> table) {
            this.table = table;
            poignee.getStyleClass().add("poignee-colonne");
            contenu.getStyleClass().add("ligne-colonne");
            activerReordonnancement();
        }

        @Override
        protected void updateItem(Colonne colonne, boolean vide) {
            super.updateItem(colonne, vide);
            detacher();
            if (vide || colonne == null) {
                setGraphic(null);
                return;
            }
            visible.setText(colonne.libelle());
            visible.setDisable(colonne.visibiliteVerrouillee());
            // Explique le grisage d'une colonne verrouillée (#789). Cellule recyclée : on repart d'un état
            // propre (uninstall sans effet si absent) puis on (ré)installe le tooltip seulement si verrouillée.
            Tooltip.uninstall(enveloppeVisible, infoVerrou);
            if (colonne.visibiliteVerrouillee()) {
                Tooltip.install(enveloppeVisible, infoVerrou);
            }
            lieeA = colonne.colonne().visibleProperty();
            visible.selectedProperty().bindBidirectional(lieeA);
            setGraphic(contenu);
        }

        private void detacher() {
            if (lieeA != null) {
                visible.selectedProperty().unbindBidirectional(lieeA);
                lieeA = null;
            }
        }

        private void activerReordonnancement() {
            setOnDragDetected(evenement -> {
                if (isEmpty() || getItem() == null) {
                    return;
                }
                Dragboard planche = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent contenuGlisse = new ClipboardContent();
                contenuGlisse.putString(Integer.toString(getIndex()));
                planche.setContent(contenuGlisse);
                evenement.consume();
            });
            setOnDragOver(evenement -> {
                if (evenement.getGestureSource() != this
                        && evenement.getDragboard().hasString()) {
                    evenement.acceptTransferModes(TransferMode.MOVE);
                }
                evenement.consume();
            });
            setOnDragDropped(evenement -> {
                boolean depose = false;
                Dragboard planche = evenement.getDragboard();
                if (planche.hasString()) {
                    deplacer(table, getListView(), Integer.parseInt(planche.getString()), cibleDepot());
                    depose = true;
                }
                evenement.setDropCompleted(depose);
                evenement.consume();
            });
        }

        /// Index de dépôt : la ligne survolée, ou la fin de liste si l'on lâche sous la dernière.
        private int cibleDepot() {
            return isEmpty() || getItem() == null ? getListView().getItems().size() - 1 : getIndex();
        }
    }
}
