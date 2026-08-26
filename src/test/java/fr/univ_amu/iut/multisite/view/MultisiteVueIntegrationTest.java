package fr.univ_amu.iut.multisite.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.ActionGroupee;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.commun.viewmodel.RevisionDonnees;
import fr.univ_amu.iut.multisite.model.CarreAgrege;
import fr.univ_amu.iut.multisite.model.EtatAnalyse;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.PointAgrege;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.model.TriMultisite;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import fr.univ_amu.iut.multisite.viewmodel.ReconstructionViewModel;
import fr.univ_amu.iut.sites.model.ServiceCommunes;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX **ciblant le câblage réel des contrôles** de l'écran M-Multisite via un
/// **lookup des `fx:id`** (et non une simple lecture des propriétés du ViewModel). Depuis #537 étape 6b,
/// les filtres passent par la **barre à puces** (recherche + « + Filtre » : Carré, Statut, Verdict, Année)
/// et les vues mémorisées par les **onglets** (`GestionnaireVues`) : ces tests forcent une vraie
/// **interaction** (ajout d'une puce, saisie, clic-carte, tri) et vérifient que **le tableau reflète le
/// filtre / le tri**. Complète [MultisiteViewTest] sans le dupliquer. Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class MultisiteVueIntegrationTest {

    private static final String RELEVE_LE = "2026-07-14T09:00:00Z";

    private ServiceMultisite service;
    private MultisiteController controleur;

    private static LignePassage ligne(long id, String carre, String point, int annee, int numero, String date) {
        return ligne(id, carre, point, annee, numero, date, Verdict.OK);
    }

    private static LignePassage ligne(
            long id, String carre, String point, int annee, int numero, String date, Verdict verdict) {
        return ligne(id, carre, point, annee, numero, date, verdict, EtatAnalyse.SANS_OBJET, null);
    }

    /// Variante portant l'**état d'analyse** (#1338) et la date de son relevé.
    private static LignePassage ligne(
            long id,
            String carre,
            String point,
            int annee,
            int numero,
            String date,
            Verdict verdict,
            EtatAnalyse analyse,
            String releveLe) {
        return new LignePassage(
                id,
                carre,
                point,
                annee,
                numero,
                date,
                StatutWorkflow.DEPOSE,
                verdict,
                analyse,
                releveLe,
                null,
                null,
                null);
    }

    @SuppressWarnings("unchecked")
    private static TableView<LignePassage> tableau(FxRobot robot) {
        return (TableView<LignePassage>)
                (TableView<?>) robot.lookup("#tableLignes").queryTableView();
    }

    /// Ajoute une puce de filtre via le menu « + Filtre » (l'item porte le libellé du critère).
    private static void ajouterPuce(FxRobot robot, String libelle) {
        MenuButton menuAjout = robot.lookup("#menuAjoutFiltre").queryAs(MenuButton.class);
        MenuItem item = menuAjout.getItems().stream()
                .filter(i -> libelle.equals(i.getText()))
                .findFirst()
                .orElseThrow();
        robot.interact(item::fire);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceMultisite.class);
        OuvrirPassage ouvrirPassage = mock(OuvrirPassage.class);
        DepotVues depotVues = mock(DepotVues.class);
        when(depotVues.findByFeature(anyString())).thenReturn(List.of());
        when(service.listerPassages(anyString()))
                .thenReturn(List.of(
                        // #1338 : deux états d'analyse distincts, pour éprouver la colonne et la puce.
                        ligne(
                                42L,
                                "640380",
                                "A1",
                                2026,
                                10,
                                "2026-06-21",
                                Verdict.OK,
                                EtatAnalyse.A_IMPORTER,
                                RELEVE_LE),
                        ligne(
                                7L,
                                "640381",
                                "B2",
                                2025,
                                3,
                                "2025-07-02",
                                Verdict.DOUTEUX,
                                EtatAnalyse.IMPORTEE,
                                RELEVE_LE)));
        // Carte (#152) : un carré avec un point géolocalisé → un marqueur attendu sur la carte.
        when(service.agregerPourCarte(anyString()))
                .thenReturn(List.of(new CarreAgrege(
                        "640380",
                        "Étang",
                        List.of(new PointAgrege("A1", 43.4010, -1.5740, 2, StatutWorkflow.VERIFIE)),
                        2)));
        // « Ouvrir sur Vigie-Chiro » de ligne (#1799) : portail mocké (aucun passage lié ici), pas de base.
        PortailVigieChiro portail = mock(PortailVigieChiro.class);
        when(portail.pageParticipation(any())).thenReturn(Optional.empty());
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            RevisionDonnees revision() {
                return new RevisionDonnees(Runnable::run);
            }

            @Override
            protected void configure() {
                bind(OuvrirPassage.class).toInstance(ouvrirPassage);
                // #2357 lot 3 : l'écran consomme l'action groupée sous son PORT. Un double suffit ici,
                // ces tests ne lancent pas de lot.
                // #2357 lot 3 : l'écran reçoit les actions en un porteur. Des doubles suffisent ici,
                // ces tests ne lancent pas de lot.
                bind(ActionsDeLot.class)
                        .toInstance(new ActionsDeLot(
                                Optional.of(mock(ActionGroupee.class)),
                                Optional.of(mock(ActionGroupee.class)),
                                Optional.of(mock(ActionGroupee.class)),
                                Optional.of(mock(ActionGroupee.class))));
                bind(OuvrirAudio.class).toInstance(source -> {});
                bind(DepotVues.class).toInstance(depotVues);
                // « Ouvrir sur Vigie-Chiro » de ligne (#1799) ouvre un lien : navigateur no-op ici.
                bind(OuvreurDeLien.class).toInstance(url -> {});
                bind(PortailVigieChiro.class).toInstance(portail);
                // NavigationMultisite ouvre la modale de reconstruction (#1396) et a besoin du Navigateur
                // du chrome ; aucun test d'ici ne l'ouvre, un double suffit.
                bind(Navigateur.class).toInstance(mock(Navigateur.class));
            }

            @Provides
            MultisiteViewModel viewModel() {
                return new MultisiteViewModel(
                        service, mock(ServiceSites.class), mock(ServiceCommunes.class), Optional.empty(), "u-1");
            }

            /// Hors connexion VigieChiro : la reconstruction est indisponible, son entrée de menu se retire.
            @Provides
            ReconstructionViewModel reconstruction() {
                return new ReconstructionViewModel(Optional.empty());
            }
        });
        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource("Multisite.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        FenetreAjustable.poser(stage, vue, 1100, 680);
        FenetreAjustable.afficher(stage);
    }

    @Test
    @DisplayName("La barre à puces (recherche, + Filtre : 5 critères) et le sélecteur de tri sont câblés")
    void la_barre_a_puces_et_le_tri_sont_cables(FxRobot robot) {
        assertThat(robot.lookup("#champRecherche").queryAs(TextField.class)).isNotNull();
        assertThat(robot.lookup("#pucesFiltres").queryAs(FlowPane.class)).isNotNull();

        MenuButton menuAjout = robot.lookup("#menuAjoutFiltre").queryAs(MenuButton.class);
        // « Lieu » (#2968) coexiste avec « Carré », et ce n'est pas un doublon oublié : retirer « Carré »
        // ferait se rejouer SANS FILTRE les vues mémorisées qui le portent, un critère inconnu étant
        // ignoré sans un mot à la restauration. Le retirer demande d'abord une migration des vues.
        assertThat(menuAjout.getItems())
                .extracting(MenuItem::getText)
                .containsExactlyInAnyOrder("Carré", "Lieu", "Statut", "Verdict", "Année", "Analyse", "Campagne");
        // Tri (souvent absent) : les 4 critères de TriMultisite.
        assertThat(robot.lookup("#choixTri").queryAs(ComboBox.class).getItems()).hasSize(TriMultisite.values().length);
    }

    @Test
    @DisplayName("#145 : trier par la colonne N° de passage (clic en-tête) réordonne, de façon NUMÉRIQUE")
    void tri_par_colonne_numero_est_numerique(FxRobot robot) {
        TableView<LignePassage> table = tableau(robot);
        TableColumn<LignePassage, ?> colNumero = table.getColumns().get(3); // colonne « N° passage »

        robot.interact(() -> {
            colNumero.setSortType(TableColumn.SortType.DESCENDING);
            table.getSortOrder().setAll(colNumero);
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Données n° 10 et 3 : un tri NUMÉRIQUE décroissant donne 10 puis 3 (un tri alphabétique sur les
        // chaînes « 10 »/« 3 » donnerait l'inverse). Prouve le comparateur numérique de la colonne.
        assertThat(table.getItems()).extracting(LignePassage::numeroPassage).containsExactly(10, 3);
    }

    @Test
    @DisplayName("#152 : la carte est présente et affiche un marqueur par point géolocalisé")
    void la_carte_affiche_les_points(FxRobot robot) {
        assertThat(robot.lookup("#zoneCarte").queryAll())
                .as("la zone carte est dans la vue")
                .isNotEmpty();
        Set<Node> marqueurs = robot.lookup(".carte-point-libelle").queryAll();
        assertThat(marqueurs)
                .as("un marqueur pour le point géolocalisé du carré 640380")
                .hasSize(1);
    }

    @Test
    @DisplayName("#152 : la légende est superposée à la carte (code couleur des statuts + densité)")
    void la_legende_est_superposee_a_la_carte(FxRobot robot) {
        Node legende = robot.lookup(".legende-carte").query();
        assertThat(legende)
                .as("le panneau de légende est présent dans la zone carte")
                .isNotNull();
        assertThat(robot.from(legende).lookup(StatutWorkflow.DEPOSE.libelle()).queryAll())
                .as("la légende elle-même affiche le libellé d'un statut workflow")
                .isNotEmpty();
    }

    @Test
    @DisplayName("#337 : la légende est repliée par défaut ; le chevron la déplie puis la replie")
    void legende_repliable(FxRobot robot) {
        Node corps = robot.lookup(".legende-corps").query();
        assertThat(corps.isManaged())
                .as("légende repliée au départ (#337) : corps non géré, carte dégagée")
                .isFalse();

        robot.clickOn(".bascule-legende");
        assertThat(corps.isVisible()).as("le chevron déplie la légende").isTrue();

        robot.clickOn(".bascule-legende");
        assertThat(corps.isManaged())
                .as("le chevron la replie (corps non géré/affiché)")
                .isFalse();
    }

    @Test
    @DisplayName("#152 : la poignée ◀ replie la carte (le tableau prend toute la largeur), puis la rouvre")
    void poignee_replie_et_rouvre_la_carte(FxRobot robot) {
        SplitPane split = robot.lookup("#splitCarteTableau").queryAs(SplitPane.class);
        Node zoneCarte = robot.lookup("#zoneCarte").query();
        Button replierTableau = robot.lookup("#boutonReplierTableau").queryAs(Button.class);
        assertThat(split.getItems()).as("carte + tableau visibles au départ").hasSize(2);

        robot.clickOn("#boutonReplierCarte");
        assertThat(split.getItems())
                .as("la carte est repliée : seul le tableau reste")
                .hasSize(1)
                .doesNotContain(zoneCarte);
        assertThat(replierTableau.isDisable())
                .as("on ne peut pas replier aussi le tableau (dernier panneau)")
                .isTrue();

        robot.clickOn("#boutonReplierCarte");
        assertThat(split.getItems())
                .as("la carte est rouverte, à sa place (index 0)")
                .hasSize(2)
                .containsExactly(zoneCarte, robot.lookup("#panneauTableau").query());
        assertThat(replierTableau.isDisable()).isFalse();
    }

    @Test
    @DisplayName("#152 : la poignée ▶ replie le tableau (la carte prend toute la largeur)")
    void poignee_replie_le_tableau(FxRobot robot) {
        SplitPane split = robot.lookup("#splitCarteTableau").queryAs(SplitPane.class);
        Node panneauTableau = robot.lookup("#panneauTableau").query();
        Button replierCarte = robot.lookup("#boutonReplierCarte").queryAs(Button.class);

        robot.clickOn("#boutonReplierTableau");
        assertThat(split.getItems())
                .as("le tableau est replié : seule la carte reste")
                .hasSize(1)
                .doesNotContain(panneauTableau);
        assertThat(replierCarte.isDisable())
                .as("on ne peut pas replier aussi la carte (dernier panneau)")
                .isTrue();
    }

    @Test
    @DisplayName("#338 : « Voir sur la carte » (focaliserSur) replie le tableau pour dégager la carte")
    void focaliser_replie_le_tableau(FxRobot robot) {
        SplitPane split = robot.lookup("#splitCarteTableau").queryAs(SplitPane.class);
        Node panneauTableau = robot.lookup("#panneauTableau").query();
        assertThat(split.getItems()).as("carte + tableau visibles au départ").contains(panneauTableau);

        robot.interact(() -> controleur.focaliserSur("640380"));

        assertThat(split.getItems())
                .as("après « Voir sur la carte », le tableau est replié : la carte occupe tout")
                .doesNotContain(panneauTableau);
    }

    @Test
    @DisplayName("« Placer sur la carte » (focaliserSurCarrePourPlacer) active le mode édition des positions")
    void placer_sur_la_carte_active_l_edition(FxRobot robot) {
        ToggleButton editer = robot.lookup("#boutonEditerPositions").queryAs(ToggleButton.class);
        assertThat(editer.isSelected()).as("édition inactive au départ").isFalse();

        robot.interact(() -> controleur.focaliserSurCarrePourPlacer("640380"));

        assertThat(editer.isSelected())
                .as("le mode édition est activé pour glisser le point sans GPS")
                .isTrue();
    }

    @Test
    @DisplayName("#152 : cliquer un carré sur la carte pose une puce « carré » qui filtre le tableau")
    void clic_carre_filtre_le_tableau(FxRobot robot) {
        Node rectangle = robot.lookup(".carte-carre").query();
        robot.interact(() -> rectangle.getOnMouseClicked().handle(null));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(tableau(robot).getItems())
                .as("le tableau ne montre plus que le carré cliqué (puce carré posée par la carte)")
                .extracting(LignePassage::numeroCarre)
                .containsOnly("640380");
    }

    @Test
    @DisplayName("#152 : sélectionner une ligne met le carré correspondant en surbrillance sur la carte")
    void selection_ligne_surbrillance_carre(FxRobot robot) {
        Rectangle rectangle = (Rectangle) robot.lookup(".carte-carre").query();
        assertThat(rectangle.getStrokeWidth()).isEqualTo(1.5);

        // 1re ligne = carré 640380 (le carré tracé sur la carte).
        robot.interact(() -> tableau(robot).getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(rectangle.getStrokeWidth())
                .as("le carré de la ligne sélectionnée est mis en évidence")
                .isEqualTo(3.0);
    }

    @Test
    @DisplayName("#339 : un bouton « recadrer » est superposé à la carte et déclenche le recadrage sans erreur")
    void bouton_recadrer_superpose_a_la_carte(FxRobot robot) {
        assertThat(robot.lookup(".bouton-recadrer").queryAll())
                .as("le bouton « recadrer » est superposé à la carte")
                .isNotEmpty();
        // Déclencher l'action ne doit pas lever (recadre sur les données affichées).
        robot.interact(robot.lookup(".bouton-recadrer").queryButton()::fire);
    }

    @Test
    @DisplayName("#1338 : la colonne « Analyse » affiche l'état en badge, avec la date du relevé en infobulle")
    void colonne_analyse_affiche_le_badge_et_date_le_releve(FxRobot robot) {
        assertThat(tableau(robot).getColumns())
                .as("la colonne « Analyse » (#1338) doit exister dans le tableau")
                .extracting(TableColumn::getText)
                .contains("Analyse");

        // La **cellule réellement rendue** (et non une cellule fabriquée à la main) : c'est le seul moyen de
        // vérifier que la fabrique de cellules est bien celle du badge, tooltip compris.
        TableCell<?, ?> cellule = robot.from(tableau(robot)).lookup(".table-cell").queryAllAs(TableCell.class).stream()
                .filter(c -> EtatAnalyse.A_IMPORTER.libelle().equals(c.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Aucune cellule « À importer » rendue dans le tableau."));

        assertThat(cellule.getStyleClass())
                .as("la couleur est dérivée de l'état, jamais stockée")
                .contains("badge", EtatAnalyse.A_IMPORTER.classeBadge());
        assertThat(cellule.getTooltip()).isNotNull();
        assertThat(cellule.getTooltip().getText())
                .as("le cache est un relevé daté, pas une vérité : la vue doit dire de quand il date")
                .contains("Dernier état connu le")
                .contains(RELEVE_LE);
    }

    @Test
    @DisplayName("#1338 : la puce « Analyse » filtre le tableau sur les nuits à importer")
    void filtre_analyse_via_la_barre_a_puces(FxRobot robot) {
        ajouterPuce(robot, "Analyse");
        FlowPane puces = robot.lookup("#pucesFiltres").queryAs(FlowPane.class);
        @SuppressWarnings("unchecked")
        ComboBox<EtatAnalyse> choix =
                (ComboBox<EtatAnalyse>) robot.from(puces).lookup(".combo-box").queryAs(ComboBox.class);
        robot.interact(() -> choix.setValue(EtatAnalyse.A_IMPORTER));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(tableau(robot).getItems())
                .as("la nuit dont les observations sont DÉJÀ importées ne doit pas rester dans la liste")
                .extracting(LignePassage::idPassage)
                .containsExactly(42L);
    }

    @Test
    @DisplayName("Ajouter une puce « Verdict » et choisir DOUTEUX filtre le tableau sur ce critère")
    void filtre_verdict_via_la_barre_a_puces(FxRobot robot) {
        ajouterPuce(robot, "Verdict");
        FlowPane puces = robot.lookup("#pucesFiltres").queryAs(FlowPane.class);
        @SuppressWarnings("unchecked")
        ComboBox<Verdict> choix =
                (ComboBox<Verdict>) robot.from(puces).lookup(".combo-box").queryAs(ComboBox.class);
        robot.interact(() -> choix.setValue(Verdict.DOUTEUX));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(tableau(robot).getItems())
                .as("seule la ligne au verdict DOUTEUX reste")
                .extracting(LignePassage::verdict)
                .containsOnly(Verdict.DOUTEUX);
    }

    @Test
    @DisplayName("Ajouter une puce « Carré » et saisir un n° filtre le tableau sur ce carré")
    void filtre_carre_via_la_barre_a_puces(FxRobot robot) {
        ajouterPuce(robot, "Carré");
        FlowPane puces = robot.lookup("#pucesFiltres").queryAs(FlowPane.class);
        TextField champCarre = robot.from(puces).lookup(".text-field").queryAs(TextField.class);
        robot.interact(() -> champCarre.setText("640380"));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(tableau(robot).getItems())
                .extracting(LignePassage::numeroCarre)
                .containsOnly("640380");
    }

    @Test
    @DisplayName("#3094 : une année illisible ne filtre rien, et la puce le dit au lieu de le taire")
    void annee_illisible_est_signalee(FxRobot robot) {
        ajouterPuce(robot, "Année");
        FlowPane puces = robot.lookup("#pucesFiltres").queryAs(FlowPane.class);
        TextField champAnnee = robot.from(puces).lookup(".text-field").queryAs(TextField.class);
        int lignesSansFiltre = tableau(robot).getItems().size();

        // Vide : la puce vient d'être posée, elle n'écarte rien et n'a aucune raison de rougir.
        assertThat(champAnnee.getStyleClass())
                .as("un champ encore vide ne rougit pas avant toute saisie")
                .doesNotContain("champ-invalide");

        // Illisible : le tableau n'est pas filtré, et c'est précisément ce qu'il faut annoncer. Sans
        // marque, la puce est posée, remplie, d'apparence active, sur une table qui ne l'est pas.
        robot.interact(() -> champAnnee.setText("202O"));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(tableau(robot).getItems())
                .as("le comportement de filtrage est inchangé : une année illisible n'écarte rien")
                .hasSize(lignesSansFiltre);
        assertThat(champAnnee.getStyleClass())
                .as("la saisie illisible doit se voir")
                .contains("champ-invalide");
        assertThat(champAnnee.getAccessibleText())
                .as("la bordure rouge est un signal de couleur : le sens doit exister sans elle")
                .contains("quatre chiffres");

        // Corrigée : la marque disparaît et le filtre s'applique pour de bon.
        robot.interact(() -> champAnnee.setText("2026"));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(champAnnee.getStyleClass()).doesNotContain("champ-invalide");
        assertThat(tableau(robot).getItems()).extracting(LignePassage::annee).containsOnly(2026);
    }

    @Test
    @DisplayName("#3095 : cocher un lieu ne retire pas les autres du menu (le domaine exclut son critère)")
    void la_puce_lieu_ne_s_auto_effondre_pas(FxRobot robot) {
        // Le seul cas qui distingue le cascadage d'un simple rafraîchissement. Si le domaine se calculait
        // sur la table déjà filtrée, cocher 640380 ne laisserait plus que 640380 au menu, et l'on ne
        // pourrait jamais en cocher un second.
        ajouterPuce(robot, "Lieu");
        FlowPane puces = robot.lookup("#pucesFiltres").queryAs(FlowPane.class);
        MenuButton lieu = (MenuButton) puces.lookup(".critere-multiple");
        assertThat(entreesCochables(lieu)).contains("640380", "640381");

        robot.interact(() -> cocher(lieu, "640380"));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(tableau(robot).getItems())
                .as("prérequis : la table est bien restreinte au carré coché")
                .extracting(LignePassage::numeroCarre)
                .containsOnly("640380");

        robot.interact(() -> lieu.getOnShowing().handle(new javafx.event.Event(javafx.event.Event.ANY)));

        assertThat(entreesCochables(lieu))
                .as("640381 doit rester proposé : sinon la puce ne sait plus désigner qu'un seul lieu")
                .contains("640380", "640381");
    }

    private static List<String> entreesCochables(MenuButton bouton) {
        return bouton.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(MenuItem::getText)
                .toList();
    }

    private static void cocher(MenuButton bouton, String valeur) {
        bouton.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .filter(item -> valeur.equals(item.getText()))
                .findFirst()
                .orElseThrow()
                .setSelected(true);
    }

    @Test
    @DisplayName("La recherche filtre le tableau (n° de carré, point, date)")
    void recherche_filtre_le_tableau(FxRobot robot) {
        robot.clickOn("#champRecherche").write("640381");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(tableau(robot).getItems())
                .extracting(LignePassage::numeroCarre)
                .containsOnly("640381");
    }

    @Test
    @DisplayName("Changer le sélecteur de tri ré-ordonne le tableau selon ce tri")
    void changer_le_tri_re_ordonne_le_tableau(FxRobot robot) {
        ComboBox<?> choixTri = robot.lookup("#choixTri").queryAs(ComboBox.class);
        // Items = TriMultisite.values() : [PAR_SITE, PAR_ANNEE, ...] → index 1 = PAR_ANNEE.
        robot.interact(() -> choixTri.getSelectionModel().select(1));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(tableau(robot).getItems())
                .as("tri par année croissante : 2025 (carré 640381) puis 2026 (carré 640380)")
                .extracting(LignePassage::annee)
                .containsExactly(2025, 2026);
    }

    @Test
    @DisplayName("#2968 : critère Lieu : commune, carré et point filtrent chacun ; cocher = appartenance")
    void critere_lieu_filtre_sur_chaque_dimension(FxRobot robot) {
        ObservableList<LignePassage> source = FXCollections.observableArrayList(
                ligneLieu(1L, "640380", "A1", "Aix-en-Provence"), ligneLieu(2L, "870150", "B2", "Venelles"));
        FilteredList<LignePassage> vues = new FilteredList<>(source);
        Filtres<LignePassage> filtresLocaux = new Filtres<>(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        GestionnaireFiltres<LignePassage> ignore = new GestionnaireFiltres<>(
                new TextField(),
                menuLocal,
                pucesLocales,
                filtresLocaux,
                List.of(CriteresMultisite.lieu(() -> source)),
                CriteresMultisite.rechercheTexte());
        assertThat(ignore).isNotNull();

        // Valeurs groupées par dimension (communes, carrés, points), triées au sein de chacune. Le SITE
        // n'y figure pas : LignePassage ne le porte pas, contrairement à la vue audio.
        robot.interact(() -> menuLocal.getItems().get(0).fire());
        MenuButton choixLieu = menuBoutonDeLieu(pucesLocales);
        assertThat(entetesLieu(choixLieu)).containsExactly("Communes", "Carrés", "Points");
        // Le point est qualifié par son carré : « A1 » seul confondrait les A1 de toute la saison.
        assertThat(cochablesLieu(choixLieu))
                .containsExactly("Aix-en-Provence", "Venelles", "640380", "870150", "640380 · A1", "870150 · B2");
        assertThat(vues).as("rien de coché n'écarte rien").hasSize(2);

        robot.interact(() -> cocheLieu(choixLieu, "Aix-en-Provence").setSelected(true));
        assertThat(vues).extracting(LignePassage::idPassage).containsExactly(1L);
        robot.interact(() -> cocheLieu(choixLieu, "870150").setSelected(true));
        assertThat(vues).as("une ligne passe par l'UNE de ses dimensions").hasSize(2);

        robot.interact(() -> {
            cocheLieu(choixLieu, "Aix-en-Provence").setSelected(false);
            cocheLieu(choixLieu, "870150").setSelected(false);
            cocheLieu(choixLieu, "870150 · B2").setSelected(true);
        });
        assertThat(vues).extracting(LignePassage::idPassage).containsExactly(2L);
    }

    @Test
    @DisplayName("#2992 : deux carrés ont chacun un point « A1 » : cocher l'un ne retient pas l'autre")
    void point_qualifie_distingue_deux_carres(FxRobot robot) {
        // Le cas que le schéma rend inévitable : UNIQUE(site_id, code), donc le même code vit dans
        // plusieurs carrés. C'est ce que la puce confondait avant la qualification.
        ObservableList<LignePassage> source = FXCollections.observableArrayList(
                ligneLieu(1L, "640380", "A1", "Ahetze"), ligneLieu(2L, "870150", "A1", "Venelles"));
        FilteredList<LignePassage> vues = new FilteredList<>(source);
        MenuButton menuLocal = new MenuButton();
        FlowPane puces = new FlowPane();
        GestionnaireFiltres<LignePassage> ignore = new GestionnaireFiltres<>(
                new TextField(),
                menuLocal,
                puces,
                new Filtres<>(vues, () -> {}),
                List.of(CriteresMultisite.lieu(() -> source)),
                CriteresMultisite.rechercheTexte());
        assertThat(ignore).isNotNull();

        robot.interact(() -> menuLocal.getItems().get(0).fire());
        MenuButton choixLieu = menuBoutonDeLieu(puces);
        // Deux entrées distinctes là où une seule « A1 » aurait paru.
        assertThat(cochablesLieu(choixLieu)).contains("640380 · A1", "870150 · A1");

        robot.interact(() -> cocheLieu(choixLieu, "640380 · A1").setSelected(true));
        assertThat(vues)
                .as("le A1 de l'autre carré n'est pas retenu")
                .extracting(LignePassage::idPassage)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("#2967 : le clic carte a besoin de « Carré » : « Lieu » ne sait pas recevoir un carré sans passage")
    void le_critere_carre_recoit_ce_que_la_liste_lieu_ne_contient_pas(FxRobot robot) {
        // Garde de la décision de garder « Carré » à côté de « Lieu ». La carte affiche l'agrégat NON
        // FILTRÉ des sites de l'utilisateur, passages ou non ; cliquer un carré sans passage doit
        // apprendre qu'il n'a rien. Router ce clic vers « Lieu » ne ferait RIEN, faute d'entrée à cocher.
        ObservableList<LignePassage> source =
                FXCollections.observableArrayList(ligneLieu(1L, "640380", "A1", "Ahetze"));
        FilteredList<LignePassage> vues = new FilteredList<>(source);
        MenuButton menuLocal = new MenuButton();
        FlowPane puces = new FlowPane();
        GestionnaireFiltres<LignePassage> gestionnaire = new GestionnaireFiltres<>(
                new TextField(),
                menuLocal,
                puces,
                new Filtres<>(vues, () -> {}),
                List.of(CriteresMultisite.lieu(() -> source), CriteresMultisite.carre()),
                CriteresMultisite.rechercheTexte());

        // Le carré cliqué n'a aucun passage : il n'est donc pas dans la liste de « Lieu ».
        robot.interact(() -> gestionnaire.poser("lieu", List.of("999999")));
        assertThat(vues)
                .as("« Lieu » ne coche que ce que sa liste contient : le filtre reste inerte")
                .hasSize(1);

        robot.interact(() -> gestionnaire.poser("carre", List.of("999999")));
        assertThat(vues)
                .as("« Carré » accepte une valeur venue d'ailleurs, et dit que ce carré n'a rien")
                .isEmpty();
    }

    /// Les intitulés des **en-têtes** de groupe : les items désactivés, qui nomment sans se cocher.
    private static List<String> entetesLieu(MenuButton bouton) {
        return bouton.getItems().stream()
                .filter(item -> !(item instanceof CheckMenuItem) && item.isDisable())
                .map(MenuItem::getText)
                .toList();
    }

    /// Les valeurs **cochables**, en-têtes et séparateurs exclus.
    private static List<String> cochablesLieu(MenuButton bouton) {
        return bouton.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(MenuItem::getText)
                .toList();
    }

    /// Ligne portant sa commune : l'aide générale du fichier la laisse nulle, faute d'en avoir eu besoin.
    private static LignePassage ligneLieu(long id, String carre, String point, String commune) {
        return new LignePassage(
                id,
                carre,
                point,
                2026,
                1,
                "2026-06-20",
                StatutWorkflow.DEPOSE,
                Verdict.OK,
                EtatAnalyse.SANS_OBJET,
                null,
                null,
                commune,
                null);
    }

    /// Le `MenuButton` de l'unique puce posée : la puce est une `HBox` dont le second enfant est l'éditeur.
    private static MenuButton menuBoutonDeLieu(FlowPane puces) {
        return (MenuButton) ((HBox) puces.getChildren().get(0)).getChildren().get(1);
    }

    /// La case à cocher d'un lieu, retrouvée par son libellé exact.
    private static CheckMenuItem cocheLieu(MenuButton bouton, String texte) {
        return bouton.getItems().stream()
                .filter(item -> item instanceof CheckMenuItem && texte.equals(item.getText()))
                .map(CheckMenuItem.class::cast)
                .findFirst()
                .orElseThrow();
    }
}
