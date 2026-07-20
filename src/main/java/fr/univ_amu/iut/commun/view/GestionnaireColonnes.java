package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.beans.InvalidationListener;
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
import org.kordamp.ikonli.javafx.FontIcon;

/// Gestionnaire **réutilisable** des colonnes d'une [TableView] : un panneau unique (façon table Notion)
/// où l'on **réordonne** les colonnes par glisser-déposer et où l'on **affiche/masque** chacune via une
/// case à cocher. Pensé pour être partagé par **toutes les vues à tables** (audio, analyse, multisite…),
/// il vit donc dans le socle (`commun.view`) et ne dépend d'aucune feature.
///
/// Deux points d'entrée sont câblés par [#installer] : un **menu contextuel** (clic droit sur la table) et
/// un item **« Colonnes… »** dans un [MenuButton] (☰) ; les deux ouvrent le **même** panneau, construit à
/// la demande sur l'ordre courant des colonnes (pas de double état à synchroniser).
///
/// Le menu contextuel est **composable** : une vue dont le clic droit porte déjà une action (ex. « Fiche
/// de l'espèce ») passe ses items à [#installer], qui les place **avant** « Colonnes… » plutôt que de les
/// écraser.
public final class GestionnaireColonnes {

    /// Icône de l'entrée « Colonnes… », partagée par le socle et le sélecteur de l'analyse : deux
    /// entrées de même libellé qui n'auraient pas la même icône rétabliraient l'écart qu'on corrige.
    public static final String ICONE_COLONNES = "fas-table";

    private GestionnaireColonnes() {}

    /// Une colonne proposée au panneau, avec son **libellé lisible** (les colonnes-icônes ont un en-tête
    /// peu parlant) et un drapeau `visibiliteVerrouillee` : une colonne d'**identité** reste toujours
    /// affichée (case cochée et désactivée) mais peut tout de même être déplacée.
    public record Colonne(TableColumn<?, ?> colonne, String libelle, boolean visibiliteVerrouillee) {}

    /// Descripteurs « par défaut » d'une table : chaque colonne prend son **en-tête** comme libellé, et la
    /// **première** colonne (au moment de l'appel) est traitée comme l'**identité** (visibilité verrouillée).
    /// Raccourci pour les tables dont l'en-tête est déjà le libellé voulu et dont l'identité est la colonne de
    /// tête ; à appeler **une fois** (typiquement à l'initialisation) pour figer l'identité avant tout
    /// réordonnancement. Les tables à colonnes-icônes, à libellé distinct de l'en-tête ou à identité ailleurs
    /// que la première colonne fournissent leur liste à la main.
    public static List<Colonne> colonnesParDefaut(TableView<?> table) {
        List<Colonne> colonnes = new ArrayList<>();
        List<? extends TableColumn<?, ?>> cols = table.getColumns();
        for (int i = 0; i < cols.size(); i++) {
            colonnes.add(new Colonne(cols.get(i), cols.get(i).getText(), i == 0));
        }
        return colonnes;
    }

    /// Câble le menu contextuel (clic droit) de `table` et ajoute un item « Colonnes… » au menu `menu`
    /// (☰) ; les deux ouvrent le panneau de gestion des `colonnes`.
    public static void installer(TableView<?> table, MenuButton menu, List<Colonne> colonnes) {
        installer(table, menu, colonnes, new MenuItem[0]);
    }

    /// Variante **composable** : `itemsClicDroit` (ex. « Fiche de l'espèce ») ouvrent le menu contextuel de
    /// la table, suivis d'un séparateur puis de « Colonnes… » — sans écraser l'action de clic droit propre à
    /// la vue. Le ☰ reçoit « Colonnes… », **précédé d'un séparateur seulement s'il porte déjà des actions**
    /// (pas de trait parasite sur un ☰ dédié aux colonnes, #995). Chaque item « Colonnes… » est une instance
    /// distincte (un [MenuItem] n'appartient qu'à un seul menu) ancrée sur son propre point.
    public static void installer(
            TableView<?> table, MenuButton menu, List<Colonne> colonnes, MenuItem... itemsClicDroit) {
        installerClicDroit(table, colonnes, itemsClicDroit);
        // Séparateur seulement si le ☰ porte déjà des actions (#995) : sur un menu par ailleurs vide (écrans
        // dont le ☰ ne sert qu'aux colonnes), un séparateur en tête apparaîtrait comme un trait parasite.
        if (!menu.getItems().isEmpty()) {
            menu.getItems().add(new SeparatorMenuItem());
        }
        menu.getItems().add(itemColonnes(table, colonnes, menu));
    }

    /// Câble **uniquement** le menu contextuel (clic droit) de `table` : `itemsClicDroit` (actions propres à
    /// la vue, ex. « Fiche de l'espèce »), un séparateur, puis « Colonnes… ». À utiliser quand une vue a
    /// **plusieurs** tables mais un seul ☰ (ex. Analyse : inventaire espèces/carrés + observations) : chaque
    /// table reçoit son clic droit, et le ☰ pilote la table voulue en appelant directement [#ouvrir].
    public static void installerClicDroit(TableView<?> table, List<Colonne> colonnes, MenuItem... itemsClicDroit) {
        List<MenuItem> itemsContexte = new ArrayList<>(Arrays.asList(itemsClicDroit));
        if (!itemsContexte.isEmpty()) {
            itemsContexte.add(new SeparatorMenuItem());
        }
        itemsContexte.add(itemColonnes(table, colonnes, table));
        table.setContextMenu(new ContextMenu(itemsContexte.toArray(new MenuItem[0])));
    }

    /// Un item « Colonnes… » qui ouvre le panneau ancré sous `ancre` (la table pour le clic droit, le ☰
    /// pour le menu).
    private static MenuItem itemColonnes(TableView<?> table, List<Colonne> colonnes, Node ancre) {
        MenuItem item = new MenuItem("Colonnes…", new FontIcon(ICONE_COLONNES));
        item.setOnAction(e -> ouvrir(table, colonnes, ancre));
        return item;
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
        // Le Popup a sa propre scène et n'hérite pas des feuilles de l'écran : on y joint palette + design
        // pour que les classes d'affordance du panneau (poignee-colonne, ligne-colonne) prennent effet (#801).
        panneau.getStylesheets()
                .addAll(
                        GestionnaireColonnes.class.getResource("palette.css").toExternalForm(),
                        GestionnaireColonnes.class.getResource("design.css").toExternalForm());
        Bounds ecran = ancre.localToScreen(ancre.getBoundsInLocal());
        popup.show(ancre, ecran.getMinX(), ecran.getMaxY());
    }

    /// **Décrit** la disposition courante des `colonnes` gérées de `table` (#994) : leurs libellés dans
    /// l'**ordre d'affichage** actuel, avec leur **visibilité**. Miroir de [GestionnaireFiltres#decrire] pour
    /// les colonnes ; le [DescripteurColonnes] obtenu est sérialisable (cf. [DescripteurColonnesJson]) et
    /// rejouable par [#restaurer].
    public static DescripteurColonnes decrire(TableView<?> table, List<Colonne> colonnes) {
        List<Colonne> ordonnees = new ArrayList<>(colonnes);
        ordonnees.sort(Comparator.comparingInt(c -> table.getColumns().indexOf(c.colonne())));
        List<DescripteurColonnes.EtatColonne> etats = new ArrayList<>();
        for (Colonne c : ordonnees) {
            etats.add(
                    new DescripteurColonnes.EtatColonne(c.libelle(), c.colonne().isVisible()));
        }
        return new DescripteurColonnes(etats);
    }

    /// **Rejoue** une disposition [DescripteurColonnes] sur les `colonnes` gérées de `table` (#994) : applique
    /// la **visibilité** de chaque colonne retrouvée par son libellé (une colonne **verrouillée** reste
    /// toujours affichée, quoi que dise le descripteur) puis rétablit l'**ordre** décrit. Tolérant aux
    /// évolutions du modèle : une colonne du descripteur **disparue** (renommée/supprimée) est ignorée ; une
    /// colonne gérée **absente** du descripteur (nouvelle) garde sa visibilité courante et se range après
    /// les colonnes décrites. Les colonnes **non gérées** de la table ne bougent pas (via [#appliquerOrdre]).
    public static void restaurer(TableView<?> table, List<Colonne> colonnes, DescripteurColonnes descripteur) {
        Map<String, Colonne> parLibelle = new HashMap<>();
        for (Colonne c : colonnes) {
            parLibelle.put(c.libelle(), c);
        }
        List<TableColumn<?, ?>> ordre = new ArrayList<>();
        Set<String> placees = new HashSet<>();
        for (DescripteurColonnes.EtatColonne etat : descripteur.colonnes()) {
            Colonne c = parLibelle.get(etat.libelle());
            if (c == null || !placees.add(etat.libelle())) {
                continue;
            }
            c.colonne().setVisible(c.visibiliteVerrouillee() || etat.visible());
            ordre.add(c.colonne());
        }
        for (Colonne c : colonnes) {
            if (!placees.contains(c.libelle())) {
                ordre.add(c.colonne());
            }
        }
        appliquerOrdre(table, ordre);
    }

    /// Raccourci [#installer] **puis** [#persister] pour une table mono-sélecteur (#994) : câble clic droit +
    /// ☰ et rend la disposition persistante par écran, en une seule ligne côté contrôleur.
    public static void installerEtPersister(
            TableView<?> table,
            MenuButton menu,
            List<Colonne> colonnes,
            DepotDispositionColonnes depot,
            String feature,
            String cle,
            MenuItem... itemsClicDroit) {
        installer(table, menu, colonnes, itemsClicDroit);
        persister(table, colonnes, depot, feature, cle);
    }

    /// Rend la disposition des `colonnes` de `table` **persistante par écran** (#994, couche « défaut par
    /// écran ») : restaure au branchement la dernière disposition mémorisée pour `(feature, cle)`, puis la
    /// **ré-enregistre** à chaque changement d'**ordre** ou de **visibilité**. À appeler **une fois**, après
    /// [#installer] : les écouteurs sont posés **après** la restauration initiale, qui ne déclenche donc pas
    /// d'enregistrement superflu. Indépendant des vues nommées (#623), qui portent leur propre disposition.
    public static void persister(
            TableView<?> table, List<Colonne> colonnes, DepotDispositionColonnes depot, String feature, String cle) {
        depot.charger(feature, cle)
                .ifPresent(json -> restaurer(table, colonnes, DescripteurColonnesJson.interpreter(json)));
        InvalidationListener sauvegarde = observable ->
                depot.enregistrer(feature, cle, DescripteurColonnesJson.serialiser(decrire(table, colonnes)));
        table.getColumns().addListener(sauvegarde);
        for (Colonne colonne : colonnes) {
            colonne.colonne().visibleProperty().addListener(sauvegarde);
        }
    }

    /// Fabrique un [AdaptateurColonnes] **mono-table** (#994) : la vue mémorisée décrit/rejoue les colonnes de
    /// `table` sous la clé `cle`. `colonnes` est un **fournisseur** (rebâti à la demande) pour que la liste
    /// reste cohérente avec le câblage `installer`, sans dépendre de l'ordre d'initialisation. Les vues à
    /// plusieurs tables (ex. analyse) composent plusieurs entrées à la main plutôt que d'utiliser ce raccourci.
    public static AdaptateurColonnes adaptateurMonoTable(String cle, TableView<?> table, List<Colonne> colonnes) {
        return adaptateurMonoTable(cle, table, () -> colonnes);
    }

    /// Variante à **fournisseur** de la liste (rebâtie à la demande) : cf. [#adaptateurMonoTable(String,
    /// TableView, List)].
    public static AdaptateurColonnes adaptateurMonoTable(
            String cle, TableView<?> table, java.util.function.Supplier<List<Colonne>> colonnes) {
        return new AdaptateurColonnes() {
            @Override
            public Map<String, DescripteurColonnes> decrire() {
                return Map.of(cle, GestionnaireColonnes.decrire(table, colonnes.get()));
            }

            @Override
            public void restaurer(Map<String, DescripteurColonnes> dispositions) {
                DescripteurColonnes disposition = dispositions.get(cle);
                if (disposition != null) {
                    GestionnaireColonnes.restaurer(table, colonnes.get(), disposition);
                }
            }
        };
    }

    /// Construit le contenu du panneau (titre + liste réordonnable), sur l'**ordre courant** des colonnes
    /// dans la table. Public (et non plus seulement `ouvert` par [#ouvrir]) pour être **rendu sans fenêtre
    /// flottante** : par les tests et par les outils de capture d'écran (le `Popup` n'est pas capturé par le
    /// `snapshot` de scène ; on rend donc directement ce `VBox`).
    public static VBox construirePanneau(TableView<?> table, List<Colonne> colonnes) {
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
        /// Poignée de glisser-déposer : une icône, pas un caractère (#1564). Le « ⋮⋮ » qu'elle
        /// portait se rend, étant un opérateur mathématique BMP - mais un signe typographique seul
        /// sur un nœud est une icône qui s'ignore, et il ne suivait ni la couleur ni l'état.
        private final Label poignee = new Label("", new FontIcon("fas-grip-vertical"));
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
