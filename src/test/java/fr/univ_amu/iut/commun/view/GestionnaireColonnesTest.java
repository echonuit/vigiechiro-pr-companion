package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.model.DispositionColonnesEnMemoire;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Tests du composant réutilisable [GestionnaireColonnes] : réordonnancement des colonnes (cœur du
/// glisser-déposer), tolérance aux colonnes non gérées, et panneau (liste + cases de visibilité liées).
@ExtendWith(ApplicationExtension.class)
class GestionnaireColonnesTest {

    private StackPane racine;

    @Start
    void start(Stage stage) {
        racine = new StackPane();
        stage.setScene(new Scene(racine, 320, 260));
        stage.show();
    }

    private static TableView<String> tableAvec(String... entetes) {
        TableView<String> table = new TableView<>();
        for (String entete : entetes) {
            table.getColumns().add(new TableColumn<>(entete));
        }
        return table;
    }

    @Test
    @DisplayName("appliquerOrdre réordonne les colonnes gérées selon la séquence fournie")
    void applique_ordre(FxRobot robot) {
        AtomicReference<TableView<String>> ref = new AtomicReference<>();
        robot.interact(() -> {
            TableView<String> table = tableAvec("A", "B", "C");
            GestionnaireColonnes.appliquerOrdre(
                    table,
                    List.of(
                            table.getColumns().get(2),
                            table.getColumns().get(0),
                            table.getColumns().get(1)));
            ref.set(table);
        });
        assertThat(ref.get().getColumns()).extracting(TableColumn::getText).containsExactly("C", "A", "B");
    }

    @Test
    @DisplayName("appliquerOrdre laisse les colonnes non gérées à leur place")
    void applique_ordre_sous_ensemble(FxRobot robot) {
        AtomicReference<TableView<String>> ref = new AtomicReference<>();
        robot.interact(() -> {
            TableView<String> table = tableAvec("A", "B", "C", "D");
            // Gérées : A (index 0) et C (index 2), réordonnées en [C, A] → C reste en 0, A en 2, B et D fixes.
            GestionnaireColonnes.appliquerOrdre(
                    table, List.of(table.getColumns().get(2), table.getColumns().get(0)));
            ref.set(table);
        });
        assertThat(ref.get().getColumns()).extracting(TableColumn::getText).containsExactly("C", "B", "A", "D");
    }

    @Test
    @DisplayName("deplacer glisse un item et répercute l'ordre sur la table (sans effet hors bornes)")
    void deplacer_reordonne(FxRobot robot) {
        AtomicReference<TableView<String>> ref = new AtomicReference<>();
        robot.interact(() -> {
            TableView<String> table = tableAvec("A", "B", "C");
            ListView<GestionnaireColonnes.Colonne> liste = new ListView<>();
            for (TableColumn<String, ?> col : table.getColumns()) {
                liste.getItems().add(new GestionnaireColonnes.Colonne(col, col.getText(), false));
            }
            GestionnaireColonnes.deplacer(table, liste, 5, 0); // hors bornes : sans effet
            GestionnaireColonnes.deplacer(table, liste, 0, 2); // A passe en fin
            ref.set(table);
        });
        assertThat(ref.get().getColumns()).extracting(TableColumn::getText).containsExactly("B", "C", "A");
    }

    @Test
    @DisplayName("Le panneau liste les colonnes ; une case décochée masque sa colonne, la verrouillée est figée")
    void panneau_cases_visibilite(FxRobot robot) {
        AtomicReference<TableColumn<String, ?>> colB = new AtomicReference<>();
        robot.interact(() -> {
            TableView<String> table = tableAvec("A", "B", "C");
            colB.set(table.getColumns().get(1));
            VBox panneau = GestionnaireColonnes.construirePanneau(
                    table,
                    List.of(
                            new GestionnaireColonnes.Colonne(table.getColumns().get(0), "A", true),
                            new GestionnaireColonnes.Colonne(table.getColumns().get(1), "B", false),
                            new GestionnaireColonnes.Colonne(table.getColumns().get(2), "C", false)));
            racine.getChildren().setAll(panneau);
        });

        CheckBox caseA = robot.lookup("A").queryAs(CheckBox.class);
        CheckBox caseB = robot.lookup("B").queryAs(CheckBox.class);
        assertThat(caseA.isDisabled())
                .as("colonne d'identité : visibilité verrouillée")
                .isTrue();
        assertThat(colB.get().isVisible()).isTrue();

        robot.interact(() -> caseB.setSelected(false));
        assertThat(colB.get().isVisible())
                .as("décocher masque la colonne (liaison bidirectionnelle)")
                .isFalse();
    }

    @Test
    @DisplayName("installer : clic droit = « Colonnes… » ; le ☰ reçoit un séparateur puis « Colonnes… »")
    void installer_pose_les_deux_entrees(FxRobot robot) {
        AtomicReference<TableView<String>> refTable = new AtomicReference<>();
        MenuButton menu = new MenuButton("☰");
        robot.interact(() -> {
            TableView<String> table = tableAvec("A", "B");
            GestionnaireColonnes.installer(
                    table,
                    menu,
                    List.of(
                            new GestionnaireColonnes.Colonne(table.getColumns().get(0), "A", true),
                            new GestionnaireColonnes.Colonne(table.getColumns().get(1), "B", false)));
            refTable.set(table);
        });

        assertThat(refTable.get().getContextMenu().getItems())
                .extracting(MenuItem::getText)
                .containsExactly("Colonnes…");
        assertThat(menu.getItems()).hasSize(2);
        assertThat(menu.getItems().get(0)).isInstanceOf(SeparatorMenuItem.class);
        assertThat(menu.getItems().get(1).getText()).isEqualTo("Colonnes…");
    }

    @Test
    @DisplayName("installer composable : les items de clic droit précèdent un séparateur puis « Colonnes… »")
    void installer_compose_le_clic_droit(FxRobot robot) {
        AtomicReference<TableView<String>> refTable = new AtomicReference<>();
        MenuItem fiche = new MenuItem("Fiche de l'espèce");
        robot.interact(() -> {
            TableView<String> table = tableAvec("A", "B");
            GestionnaireColonnes.installer(
                    table,
                    new MenuButton("☰"),
                    List.of(new GestionnaireColonnes.Colonne(table.getColumns().get(0), "A", false)),
                    fiche);
            refTable.set(table);
        });

        var items = refTable.get().getContextMenu().getItems();
        assertThat(items).hasSize(3);
        assertThat(items.get(0)).as("l'action de la vue vient en tête").isSameAs(fiche);
        assertThat(items.get(1)).isInstanceOf(SeparatorMenuItem.class);
        assertThat(items.get(2).getText()).isEqualTo("Colonnes…");
    }

    @Test
    @DisplayName("installerClicDroit : câble le seul clic droit (action + « Colonnes… »), sans toucher de ☰")
    void installerClicDroit_cable_le_clic_droit_seul(FxRobot robot) {
        AtomicReference<TableView<String>> refTable = new AtomicReference<>();
        MenuItem action = new MenuItem("Action");
        robot.interact(() -> {
            TableView<String> table = tableAvec("A", "B");
            GestionnaireColonnes.installerClicDroit(
                    table,
                    List.of(new GestionnaireColonnes.Colonne(table.getColumns().get(0), "A", false)),
                    action);
            refTable.set(table);
        });

        var items = refTable.get().getContextMenu().getItems();
        assertThat(items).hasSize(3);
        assertThat(items.get(0)).as("l'action de la vue vient en tête").isSameAs(action);
        assertThat(items.get(1)).isInstanceOf(SeparatorMenuItem.class);
        assertThat(items.get(2).getText()).isEqualTo("Colonnes…");
    }

    @Test
    @DisplayName("colonnesParDefaut : en-tête = libellé, la colonne de tête est l'identité verrouillée")
    void colonnesParDefaut_premiere_colonne_est_identite(FxRobot robot) {
        AtomicReference<List<GestionnaireColonnes.Colonne>> ref = new AtomicReference<>();
        robot.interact(() -> ref.set(GestionnaireColonnes.colonnesParDefaut(tableAvec("A", "B", "C"))));

        assertThat(ref.get()).extracting(GestionnaireColonnes.Colonne::libelle).containsExactly("A", "B", "C");
        assertThat(ref.get())
                .filteredOn(GestionnaireColonnes.Colonne::visibiliteVerrouillee)
                .extracting(c -> c.colonne().getText())
                .as("seule la colonne de tête est verrouillée")
                .containsExactly("A");
    }

    @Test
    @DisplayName("decrire : reflète l'ordre d'affichage courant et la visibilité de chaque colonne")
    void decrire_reflete_ordre_et_visibilite(FxRobot robot) {
        AtomicReference<DescripteurColonnes> ref = new AtomicReference<>();
        robot.interact(() -> {
            TableView<String> table = tableAvec("A", "B", "C");
            List<GestionnaireColonnes.Colonne> colonnes = List.of(
                    new GestionnaireColonnes.Colonne(table.getColumns().get(0), "A", true),
                    new GestionnaireColonnes.Colonne(table.getColumns().get(1), "B", false),
                    new GestionnaireColonnes.Colonne(table.getColumns().get(2), "C", false));
            table.getColumns().get(1).setVisible(false); // B masquée avant le réordonnancement
            GestionnaireColonnes.appliquerOrdre(
                    table,
                    List.of(
                            table.getColumns().get(2),
                            table.getColumns().get(0),
                            table.getColumns().get(1))); // ordre voulu : C, A, B
            ref.set(GestionnaireColonnes.decrire(table, colonnes));
        });

        assertThat(ref.get().colonnes())
                .extracting(DescripteurColonnes.EtatColonne::libelle)
                .containsExactly("C", "A", "B");
        assertThat(ref.get().colonnes())
                .filteredOn(e -> !e.visible())
                .extracting(DescripteurColonnes.EtatColonne::libelle)
                .containsExactly("B");
    }

    @Test
    @DisplayName("restaurer : rétablit l'ordre et la visibilité ; une colonne verrouillée reste affichée")
    void restaurer_reordonne_et_respecte_le_verrou(FxRobot robot) {
        AtomicReference<TableView<String>> ref = new AtomicReference<>();
        robot.interact(() -> {
            TableView<String> table = tableAvec("A", "B", "C");
            List<GestionnaireColonnes.Colonne> colonnes = List.of(
                    new GestionnaireColonnes.Colonne(table.getColumns().get(0), "A", true), // identité
                    new GestionnaireColonnes.Colonne(table.getColumns().get(1), "B", false),
                    new GestionnaireColonnes.Colonne(table.getColumns().get(2), "C", false));
            DescripteurColonnes desc = new DescripteurColonnes(List.of(
                    new DescripteurColonnes.EtatColonne("C", true),
                    new DescripteurColonnes.EtatColonne("A", false), // masquer l'identité : doit être ignoré
                    new DescripteurColonnes.EtatColonne("B", false)));
            GestionnaireColonnes.restaurer(table, colonnes, desc);
            ref.set(table);
        });

        assertThat(ref.get().getColumns()).extracting(TableColumn::getText).containsExactly("C", "A", "B");
        assertThat(ref.get().getColumns().get(0).isVisible()).as("C visible").isTrue();
        assertThat(ref.get().getColumns().get(1).isVisible())
                .as("identité verrouillée : reste visible malgré le descripteur")
                .isTrue();
        assertThat(ref.get().getColumns().get(2).isVisible()).as("B masquée").isFalse();
    }

    @Test
    @DisplayName("restaurer : ignore une colonne disparue et range une colonne nouvelle à la fin")
    void restaurer_tolere_evolution_du_modele(FxRobot robot) {
        AtomicReference<TableView<String>> ref = new AtomicReference<>();
        robot.interact(() -> {
            TableView<String> table = tableAvec("A", "B", "C");
            List<GestionnaireColonnes.Colonne> colonnes = List.of(
                    new GestionnaireColonnes.Colonne(table.getColumns().get(0), "A", false),
                    new GestionnaireColonnes.Colonne(table.getColumns().get(1), "B", false),
                    new GestionnaireColonnes.Colonne(table.getColumns().get(2), "C", false));
            DescripteurColonnes desc = new DescripteurColonnes(List.of(
                    new DescripteurColonnes.EtatColonne("C", true),
                    new DescripteurColonnes.EtatColonne("DISPARUE", true), // inconnue : ignorée
                    new DescripteurColonnes.EtatColonne("A", true))); // B absente du descripteur → à la fin
            GestionnaireColonnes.restaurer(table, colonnes, desc);
            ref.set(table);
        });

        assertThat(ref.get().getColumns()).extracting(TableColumn::getText).containsExactly("C", "A", "B");
    }

    @Test
    @DisplayName("persister : restaure la disposition mémorisée, puis ré-enregistre à chaque changement")
    void persister_restaure_puis_sauvegarde(FxRobot robot) {
        DepotDispositionColonnes depot = new DispositionColonnesEnMemoire();
        depot.enregistrer(
                "ecran",
                "principale",
                DescripteurColonnesJson.serialiser(new DescripteurColonnes(List.of(
                        new DescripteurColonnes.EtatColonne("C", true),
                        new DescripteurColonnes.EtatColonne("A", true),
                        new DescripteurColonnes.EtatColonne("B", false)))));
        AtomicReference<TableView<String>> ref = new AtomicReference<>();
        robot.interact(() -> {
            TableView<String> table = tableAvec("A", "B", "C");
            List<GestionnaireColonnes.Colonne> colonnes = List.of(
                    new GestionnaireColonnes.Colonne(table.getColumns().get(0), "A", true),
                    new GestionnaireColonnes.Colonne(table.getColumns().get(1), "B", false),
                    new GestionnaireColonnes.Colonne(table.getColumns().get(2), "C", false));
            GestionnaireColonnes.persister(table, colonnes, depot, "ecran", "principale");
            ref.set(table);
        });

        // Restaurée au branchement : ordre C, A, B et « B » masquée.
        assertThat(ref.get().getColumns()).extracting(TableColumn::getText).containsExactly("C", "A", "B");
        assertThat(ref.get().getColumns().get(2).isVisible()).isFalse();

        // Un changement (masquer « C ») est ré-enregistré : le dépôt reflète l'état courant.
        robot.interact(() -> ref.get().getColumns().get(0).setVisible(false));
        DescripteurColonnes memorise = DescripteurColonnesJson.interpreter(
                depot.charger("ecran", "principale").orElseThrow());
        assertThat(memorise.colonnes())
                .filteredOn(e -> e.libelle().equals("C"))
                .singleElement()
                .satisfies(e -> assertThat(e.visible()).isFalse());
    }

    @Test
    @DisplayName("DescripteurColonnesJson : aller-retour JSON fidèle")
    void json_colonnes_round_trip() {
        DescripteurColonnes desc = new DescripteurColonnes(List.of(
                new DescripteurColonnes.EtatColonne("Espèce", true),
                new DescripteurColonnes.EtatColonne("Période", false)));

        String json = DescripteurColonnesJson.serialiser(desc);

        assertThat(json).contains("colonnes", "libelle", "visible");
        assertThat(DescripteurColonnesJson.interpreter(json)).isEqualTo(desc);
    }
}
