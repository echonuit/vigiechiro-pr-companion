package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.Horodatage;
import fr.univ_amu.iut.commun.persistence.InventaireSauvegardes;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/// Contenu de la fenêtre de choix d'une sauvegarde (#3197) : la liste, ses colonnes, et **la ligne de
/// total**.
///
/// Séparé de la fenêtre pour la raison habituelle du socle : la fenêtre appelle `showAndWait()`, qui
/// fige un test headless ; le contenu, lui, se monte dans une scène de test et se regarde. C'est le
/// même partage que [SuiviProgression] / [DialogueProgression].
///
/// **Le total est le point de l'écran**, pas une décoration : l'application écrit un filet complet
/// avant chaque migration et n'en supprime aucun, délibérément (ADR 0048). Elle demandait donc un
/// ménage sans jamais dire ce qu'il y avait à ranger. C'est la seule ligne qui rende la question
/// actionnable, et elle compte **ce qui est montré**, sans arrondi trompeur sur le nombre d'entrées.
public final class ContenuChoixSauvegarde {

    public static final String ID_TABLE = "tableSauvegardes";
    public static final String ID_TOTAL = "libelleTotal";
    public static final String ID_RESTAURER = "boutonRestaurer";
    public static final String ID_PARCOURIR = "boutonParcourir";

    private static final String BOUTON_SECONDAIRE = "bouton-secondaire";

    private final VBox racine;
    private final TableView<InventaireSauvegardes.Entree> table;

    /// @param entrees ce que le dossier contient, déjà filtré selon ce que l'appelant sait restaurer
    /// @param surRestaurer reçoit la sauvegarde choisie : le contenu connaît sa sélection, la fenêtre n'a
    ///     pas à la lui redemander
    /// @param surParcourir navigation libre, pour une sauvegarde rangée ailleurs
    /// @param surAnnuler abandon
    public ContenuChoixSauvegarde(
            List<InventaireSauvegardes.Entree> entrees,
            Consumer<InventaireSauvegardes.Entree> surRestaurer,
            Runnable surParcourir,
            Runnable surAnnuler) {
        this.table = table(entrees);

        Label total = new Label(libelleTotal(entrees));
        total.setId(ID_TOTAL);
        // Une phrase, pas une etiquette : sans enroulement elle est tronquee des que le total est
        // long, et le garde de capture le refuse a juste titre (le chiffre serait illisible).
        total.setWrapText(true);
        total.setMaxWidth(Double.MAX_VALUE);

        Button restaurer = new Button("Restaurer");
        restaurer.getStyleClass().add("bouton-primaire");
        restaurer.setId(ID_RESTAURER);
        restaurer.setOnAction(
                evenement -> surRestaurer.accept(table.getSelectionModel().getSelectedItem()));
        // Rien de sélectionné, rien à restaurer : l'affordance dit ce que le clic exige (#790).
        restaurer
                .disableProperty()
                .bind(table.getSelectionModel().selectedItemProperty().isNull());

        Button parcourir = new Button("Parcourir…");
        parcourir.getStyleClass().add(BOUTON_SECONDAIRE);
        parcourir.setId(ID_PARCOURIR);
        parcourir.setOnAction(evenement -> surParcourir.run());

        Button annuler = new Button("Annuler");
        annuler.getStyleClass().add(BOUTON_SECONDAIRE);
        annuler.setCancelButton(true);
        annuler.setOnAction(evenement -> surAnnuler.run());

        Region espace = new Region();
        HBox.setHgrow(espace, javafx.scene.layout.Priority.ALWAYS);
        HBox boutons = new HBox(8, parcourir, espace, annuler, restaurer);
        boutons.setAlignment(Pos.CENTER_RIGHT);

        this.racine = new VBox(10, table, total, boutons);
        racine.setPadding(new Insets(14));
        racine.setPrefWidth(780);
    }

    public VBox racine() {
        return racine;
    }

    private static TableView<InventaireSauvegardes.Entree> table(List<InventaireSauvegardes.Entree> entrees) {
        TableView<InventaireSauvegardes.Entree> table = new TableView<>();
        table.setId(ID_TABLE);
        table.getItems().setAll(entrees);
        table.setPrefHeight(220);
        // Le dossier peut être vide (installation qui n'a jamais migré ni sauvegardé) : un état, pas
        // une anomalie. Le dire vaut mieux qu'une table vide sans explication.
        table.setPlaceholder(new Label("Aucune sauvegarde ici. « Parcourir… » pour en chercher ailleurs."));

        TableColumn<InventaireSauvegardes.Entree, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(ligne -> new ReadOnlyStringWrapper(
                Horodatage.dansUnTableau(ligne.getValue().date().atZone(ZoneId.systemDefault()))));
        date.setPrefWidth(130);

        TableColumn<InventaireSauvegardes.Entree, String> taille = new TableColumn<>("Taille");
        taille.setCellValueFactory(ligne -> new ReadOnlyStringWrapper(
                Formats.octetsLisibles(ligne.getValue().octets())));
        taille.setPrefWidth(90);

        TableColumn<InventaireSauvegardes.Entree, String> nature = new TableColumn<>("Nature");
        nature.setCellValueFactory(ligne ->
                new ReadOnlyStringWrapper(libelleNature(ligne.getValue().nature())));
        nature.setPrefWidth(160);

        TableColumn<InventaireSauvegardes.Entree, String> nom = new TableColumn<>("Fichier");
        nom.setCellValueFactory(
                ligne -> new ReadOnlyStringWrapper(ligne.getValue().nom()));
        nom.setPrefWidth(380);

        // La derniere colonne absorbe la largeur restante : le NOM du fichier est ce qui
        // identifie une sauvegarde, le tronquer reviendrait a ne pas la nommer. Vu sur la
        // capture, invisible a tout test.
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().setAll(List.of(date, taille, nature, nom));
        return table;
    }

    /// Ce que la nature veut dire pour qui doit choisir, et non le nom de la constante.
    private static String libelleNature(InventaireSauvegardes.Nature nature) {
        return switch (nature) {
            case BASE -> "Sauvegarde";
            case COMPLETE -> "Complète (avec audio)";
            case FILET_MIGRATION -> "Filet de migration";
        };
    }

    private static String libelleTotal(List<InventaireSauvegardes.Entree> entrees) {
        if (entrees.isEmpty()) {
            return "Rien ici pour l'instant.";
        }
        return entrees.size() + " sauvegarde(s), " + Formats.octetsLisibles(InventaireSauvegardes.total(entrees))
                + " au total. L'application n'en supprime aucune : c'est à vous de faire le ménage.";
    }
}
