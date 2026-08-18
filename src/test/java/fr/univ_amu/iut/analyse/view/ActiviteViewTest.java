package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.analyse.model.ContactHoraire;
import fr.univ_amu.iut.analyse.model.ServiceActivite;
import fr.univ_amu.iut.analyse.viewmodel.ActiviteViewModel;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX de l'écran **Activité de la nuit** (#2352) : chargement du FXML via Guice
/// (avec un [ServiceActivite] mocké), courbe par espèce (cinq séries par défaut), sélecteur d'espèces et
/// état vide. Pas de base de données ; chaque test décrit son propre passage.
@ExtendWith(ApplicationExtension.class)
class ActiviteViewTest {

    private static final long PASSAGE = 1L;

    @TempDir
    Path dossier;

    private ServiceActivite service;
    private ActiviteController controleur;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceActivite.class);
        when(service.plageNuit(anyLong())).thenReturn(Optional.empty());
        OuvrirSite ouvrirSite = mock(OuvrirSite.class);
        OuvrirPassage ouvrirPassage = mock(OuvrirPassage.class);
        // Dépôt de vues vide : seuls les onglets par défaut (catégories du référentiel) sont rendus.
        DepotVues depotVues = mock(DepotVues.class);
        when(depotVues.findByFeature("activite")).thenReturn(List.of());
        Injector injector = Guice.createInjector(new AbstractModule() {
            // Référentiel des espèces à enjeu (#2353) : cet injecteur n'installe pas les modules de
            // feature, il déclare donc son propre monde. La justesse du référentiel réel est gardée
            // par EspecesPrioritairesReferentielTest.
            @Provides
            EspecesPrioritaires especesPrioritaires() {
                return () -> Set.of("PIPKUH");
            }

            @Provides
            ActiviteViewModel viewModel() {
                return new ActiviteViewModel(service);
            }

            @Provides
            OuvrirSite ouvrirSite() {
                return ouvrirSite;
            }

            @Provides
            OuvrirPassage ouvrirPassage() {
                return ouvrirPassage;
            }

            @Provides
            DepotVues depotVues() {
                return depotVues;
            }
        });
        FXMLLoader loader = new FXMLLoader(ActiviteController.class.getResource("Activite.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        stage.setScene(Habillage.scene(vue, 960, 640));
        stage.show();
    }

    @Test
    void charge_affiche_les_cinq_especes_les_plus_contactees(FxRobot robot) {
        List<ContactHoraire> contacts = new ArrayList<>();
        String[] noms = {"Kuhl", "Barbastelle", "Murin", "Sérotine", "Noctule", "Oreillard"};
        for (int rang = 0; rang < noms.length; rang++) {
            contacts.addAll(nContacts("ESP" + rang, noms[rang], noms.length - rang));
        }
        charger(robot, contacts);

        LineChart<?, ?> graphe = robot.lookup("#grapheActivite").queryAs(LineChart.class);
        FlowPane selecteur = robot.lookup("#selecteurEspeces").queryAs(FlowPane.class);
        assertThat(graphe.getData())
                .as("cinq courbes tracées par défaut, les plus contactées")
                .hasSize(5);
        assertThat(selecteur.getChildren()).as("une case par espèce de la nuit").hasSize(6);
    }

    @Test
    void nuit_sans_espece_montre_l_etat_vide(FxRobot robot) {
        charger(robot, List.of());

        Label vide = robot.lookup("#lblEtatVide").queryAs(Label.class);
        LineChart<?, ?> graphe = robot.lookup("#grapheActivite").queryAs(LineChart.class);
        assertThat(vide.isVisible()).as("l'absence d'espèce est dite").isTrue();
        assertThat(graphe.isVisible()).as("pas de graphe vide muet").isFalse();
    }

    @Test
    void decocher_une_espece_retire_sa_courbe(FxRobot robot) {
        List<ContactHoraire> contacts = new ArrayList<>();
        contacts.addAll(nContacts("PIPKUH", "Pipistrelle de Kuhl", 5));
        contacts.addAll(nContacts("BARBAR", "Barbastelle d'Europe", 2));
        charger(robot, contacts);
        LineChart<?, ?> graphe = robot.lookup("#grapheActivite").queryAs(LineChart.class);
        assertThat(graphe.getData()).hasSize(2);

        robot.clickOn(robot.lookup(".check-box").nth(0).queryAs(CheckBox.class));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(graphe.getData()).as("décocher une espèce retire sa courbe").hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("#3964 : au retour, l'activité est RELUE par la porte qui a servi")
    void l_activite_est_relue_au_retour_par_la_bonne_porte(FxRobot robot) {
        // Cet écran a DEUX portes : un passage, ou tous les passages d'un utilisateur. Relire par la
        // mauvaise afficherait la nuit d'un autre périmètre, ce qui est pire que ne pas relire.
        when(service.contactsDeLUtilisateur("u-1")).thenReturn(nContacts("PIPKUH", "Pipistrelle de Kuhl", 5));
        robot.interact(() -> controleur.ouvrirTout("u-1"));
        assertThat(robot.lookup("#grapheActivite").queryAs(LineChart.class).getData())
                .as("l'état de départ")
                .hasSize(1);

        // Le geste qui a lieu ailleurs : une observation corrigée, un taxon de plus sur la courbe.
        when(service.contactsDeLUtilisateur("u-1"))
                .thenReturn(java.util.stream.Stream.concat(
                                nContacts("PIPKUH", "Pipistrelle de Kuhl", 5).stream(),
                                nContacts("BARBAR", "Barbastelle", 2).stream())
                        .toList());

        robot.interact(() -> controleur.rafraichirAuRetour());

        assertThat(robot.lookup("#grapheActivite").queryAs(LineChart.class).getData())
                .as("l'écran a gardé la courbe d'avant la correction : il ne relit pas sa source")
                .hasSize(2);
    }

    @Test
    void ouvrir_tout_charge_les_passages_de_l_utilisateur_en_racine(FxRobot robot) {
        when(service.contactsDeLUtilisateur("u-1")).thenReturn(nContacts("PIPKUH", "Pipistrelle de Kuhl", 5));

        robot.interact(() -> controleur.ouvrirTout("u-1"));

        LineChart<?, ?> graphe = robot.lookup("#grapheActivite").queryAs(LineChart.class);
        assertThat(graphe.getData())
                .as("la courbe couvre tous les passages de l'utilisateur")
                .hasSize(1);
        assertThat(controleur.emplacement())
                .as("en transverse (racine), le fil d'Ariane se réduit au segment courant")
                .singleElement()
                .extracting(Lieu::libelle)
                .isEqualTo("Activité de la nuit");
    }

    @Test
    void les_onglets_partitionnent_les_taxons_par_categorie(FxRobot robot) {
        FlowPane onglets = robot.lookup("#barreOnglets").queryAs(FlowPane.class);

        assertThat(onglets.getChildren())
                .as("un onglet par catégorie du référentiel, plus « Tout » et « + Vue »")
                .isNotEmpty();
        assertThat(robot.lookup("Chiroptères").tryQuery())
                .as("Tadarida détecte aussi des orthoptères : la catégorie doit pouvoir s'isoler")
                .isPresent();
        assertThat(robot.lookup("Autres").tryQuery())
                .as("« Autres » cumule les catégories non-chiroptères : il tient enfin sa promesse (#2615)")
                .isPresent();
    }

    @Test
    void l_ecran_s_ouvre_sur_l_onglet_chiropteres(FxRobot robot) {
        // Tadarida ne détecte pas que des chauves-souris : sur une vraie saison, la présélection des cinq
        // taxons les plus contactés peut retenir une sauterelle, tracée comme une espèce de chiroptère.
        // L'écran doit donc s'ouvrir sur la seule catégorie que le protocole vise (#2616).
        when(service.contactsDeLUtilisateur("u-1"))
                .thenReturn(concat(
                        nContactsDuGroupe("PIPKUH", "Chiroptères", 5),
                        nContactsDuGroupe("TETVIR", "Orthoptères et cigales", 9)));
        robot.interact(() -> controleur.ouvrirTout("u-1"));
        WaitForAsyncUtils.waitForFxEvents();

        FlowPane onglets = robot.lookup("#barreOnglets").queryAs(FlowPane.class);
        Node actif = onglets.getChildren().stream()
                .filter(o -> o.getStyleClass().contains("onglet-vue-actif"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucun onglet actif à l'ouverture"));
        assertThat(robot.from(actif)
                        .lookup(".onglet-vue-nom")
                        .queryAs(Label.class)
                        .getText())
                .as("l'écran s'ouvre sur la catégorie que le protocole vise")
                .isEqualTo("Chiroptères");

        LineChart<?, ?> graphe = robot.lookup("#grapheActivite").queryAs(LineChart.class);
        assertThat(graphe.getData())
                .extracting(XYChart.Series::getName)
                .as("l'orthoptère le plus contacté ne doit pas être tracé comme une chauve-souris")
                .containsExactly("PIPKUH");
    }

    @Test
    void l_onglet_especes_prioritaires_ne_garde_que_les_especes_a_enjeu(FxRobot robot) {
        // Deux espèces de chiroptères, une seule prioritaire au plan national : l'onglet doit trancher.
        when(service.contactsDeLUtilisateur("u-1"))
                .thenReturn(concat(
                        nContactsDuGroupe("PIPKUH", "Chiroptères", 5), nContactsDuGroupe("BARBAR", "Chiroptères", 3)));
        robot.interact(() -> controleur.ouvrirTout("u-1"));
        WaitForAsyncUtils.waitForFxEvents();

        LineChart<?, ?> graphe = robot.lookup("#grapheActivite").queryAs(LineChart.class);
        assertThat(graphe.getData())
                .as("les deux espèces sont tracées à l'ouverture")
                .hasSize(2);

        robot.clickOn("Espèces prioritaires");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(graphe.getData())
                .extracting(XYChart.Series::getName)
                .as("seule l'espèce prioritaire au plan national reste tracée")
                .containsExactly("PIPKUH");
    }

    @Test
    void le_menu_filtre_offre_le_lieu_et_le_taxon_parent(FxRobot robot) {
        MenuButton menu = robot.lookup("#menuAjoutFiltre").queryAs(MenuButton.class);

        assertThat(menu.getItems())
                .extracting(MenuItem::getText)
                .as("le lieu (#2967), la nuit, le taxon parent, et la nature de la nuit (#2614)")
                .contains("Lieu", "Nuit", "Taxon parent", "Nature de la nuit", "Espèces à enjeu");
        assertThat(menu.getItems())
                .extracting(MenuItem::getText)
                .as("« Lieu » remplace les deux puces à choix unique, il ne s'y ajoute pas")
                .doesNotContain("Carré", "Point");
    }

    @Test
    void le_filtre_nature_de_la_nuit_ecarte_les_nuits_opportunistes(FxRobot robot) {
        // Deux espèces, deux nuits : l'une menée dans le cadre du protocole, l'autre sur le carré d'un
        // tiers (#2525). Sans cette dimension, elles se mêlaient sans que rien ne le signale.
        when(service.contactsDeLUtilisateur("u-1"))
                .thenReturn(concat(nContactsDuPassage("PIPKUH", 1L, 5), nContactsDuPassage("BARBAR", 2L, 3)));
        when(service.nuitsOpportunistes()).thenReturn(Set.of(2L));
        robot.interact(() -> controleur.ouvrirTout("u-1"));
        LineChart<?, ?> graphe = robot.lookup("#grapheActivite").queryAs(LineChart.class);
        assertThat(graphe.getData()).as("les deux nuits sont tracées au départ").hasSize(2);

        MenuButton menuAjout = robot.lookup("#menuAjoutFiltre").queryAs(MenuButton.class);
        MenuItem itemNature = menuAjout.getItems().stream()
                .filter(item -> "Nature de la nuit".equals(item.getText()))
                .findFirst()
                .orElseThrow();
        robot.interact(itemNature::fire);
        WaitForAsyncUtils.waitForFxEvents();

        FlowPane puces = robot.lookup("#pucesFiltres").queryAs(FlowPane.class);
        @SuppressWarnings("unchecked")
        ComboBox<String> choixNature =
                (ComboBox<String>) robot.from(puces).lookup(".combo-box").queryAs(ComboBox.class);
        robot.interact(() -> choixNature.setValue("Opportuniste"));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(graphe.getData())
                .as("seule la nuit réalisée sur le carré d'un tiers reste tracée")
                .hasSize(1);
        assertThat(graphe.getData().get(0).getName()).isEqualTo("BARBAR");

        robot.interact(() -> choixNature.setValue("Protocole"));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(graphe.getData())
                .as("le complément : la nuit du protocole, et elle seule")
                .hasSize(1);
        assertThat(graphe.getData().get(0).getName()).isEqualTo("PIPKUH");
    }

    @Test
    void la_recherche_texte_restreint_la_courbe(FxRobot robot) {
        when(service.contactsDeLUtilisateur("u-1"))
                .thenReturn(
                        concat(nContacts("PIPKUH", "Pipistrelle de Kuhl", 5), nContacts("BARBAR", "Barbastelle", 2)));
        robot.interact(() -> controleur.ouvrirTout("u-1"));
        LineChart<?, ?> graphe = robot.lookup("#grapheActivite").queryAs(LineChart.class);
        assertThat(graphe.getData()).hasSize(2);

        robot.clickOn(robot.lookup("#champRecherche").queryAs(TextField.class)).write("PIPKUH");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(graphe.getData())
                .as("la recherche « PIPKUH » écarte la Barbastelle du sous-ensemble, ré-agrégé")
                .hasSize(1);
    }

    @Test
    void la_legende_nomme_chaque_courbe_affichee(FxRobot robot) {
        charger(robot, concat(nContacts("PIPKUH", "Pipistrelle de Kuhl", 5), nContacts("BARBAR", "Barbastelle", 2)));
        LineChart<?, ?> graphe = robot.lookup("#grapheActivite").queryAs(LineChart.class);

        assertThat(graphe.isLegendVisible())
                .as("la légende porte l'identification des courbes, le survol en donne le détail")
                .isTrue();
        assertThat(graphe.getData())
                .extracting(XYChart.Series::getName)
                .containsExactlyInAnyOrder("Pipistrelle de Kuhl", "Barbastelle");
    }

    @Test
    void l_aplat_marque_la_fenetre_nocturne_du_passage(FxRobot robot) {
        when(service.plageNuit(PASSAGE)).thenReturn(Optional.of(new PlageNuit(21, 6)));
        charger(robot, nContacts("PIPKUH", "Pipistrelle de Kuhl", 5));
        WaitForAsyncUtils.waitForFxEvents();

        Rectangle aplat = robot.lookup(".aplat-nuit").queryAs(Rectangle.class);
        assertThat(aplat.isVisible())
                .as("la fenêtre coucher → lever du passage est matérialisée")
                .isTrue();
        assertThat(aplat.getWidth())
                .as("l'aplat couvre une largeur non nulle sur l'axe nocturne")
                .isGreaterThan(0.0);
    }

    @Test
    void la_vue_transverse_multi_nuits_n_affiche_pas_d_aplat(FxRobot robot) {
        when(service.contactsDeLUtilisateur("u-1")).thenReturn(nContacts("PIPKUH", "Pipistrelle de Kuhl", 5));
        robot.interact(() -> controleur.ouvrirTout("u-1"));
        WaitForAsyncUtils.waitForFxEvents();

        Rectangle aplat = robot.lookup(".aplat-nuit").queryAs(Rectangle.class);
        assertThat(aplat.isVisible())
                .as("sans nuit unique, pas d'aplat qui donnerait une fenêtre trompeuse")
                .isFalse();
    }

    @Test
    void l_export_image_redessine_un_graphe_reellement_dessine(FxRobot robot) throws Exception {
        when(service.plageNuit(PASSAGE)).thenReturn(Optional.of(new PlageNuit(21, 6)));
        charger(robot, concat(nContacts("PIPKUH", "Pipistrelle de Kuhl", 5), nContacts("BARBAR", "Barbastelle", 2)));
        Path image = dossier.resolve("activite.png");
        controleur.selecteur().definir(new SelecteurFige(image));

        robot.clickOn("#boutonExporterImage");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(Files.exists(image)).as("l'export écrit bien le PNG demandé").isTrue();
        BufferedImage rendu = ImageIO.read(image.toFile());
        assertThat(rendu).as("le PNG est lisible").isNotNull();
        assertThat(couleursDistinctes(rendu))
                .as("le graphe est REDESSINÉ : une capture d'un nœud masqué rendrait une image unie (noire)")
                .isGreaterThan(5);
    }

    @Test
    void un_export_reussi_le_dit(FxRobot robot) {
        charger(robot, nContacts("PIPKUH", "Pipistrelle de Kuhl", 5));
        controleur.selecteur().definir(new SelecteurFige(dossier.resolve("activite.png")));

        robot.clickOn("#boutonExporterImage");
        WaitForAsyncUtils.waitForFxEvents();

        Label retour = robot.lookup("#lblRetour").queryAs(Label.class);
        assertThat(retour.getText())
                .as("un export qui a marché sans rien dire est indiscernable d'un clic sans effet")
                .contains("activite.png");
    }

    @Test
    void un_export_impossible_le_dit_au_lieu_de_ne_rien_faire(FxRobot robot) throws Exception {
        charger(robot, nContacts("PIPKUH", "Pipistrelle de Kuhl", 5));
        // Destination impossible : le « dossier » parent est en réalité un fichier, l'écriture échoue.
        Path obstacle = dossier.resolve("obstacle");
        Files.writeString(obstacle, "je ne suis pas un dossier");
        controleur.selecteur().definir(new SelecteurFige(obstacle.resolve("activite.png")));

        robot.clickOn("#boutonExporterImage");
        WaitForAsyncUtils.waitForFxEvents();

        Label retour = robot.lookup("#lblRetour").queryAs(Label.class);
        assertThat(retour.getText())
                .as("l'échec est dit : sans cela l'exception est avalée par le fil JavaFX, et le bouton"
                        + " « ne fait rien »")
                .contains("échoué");
    }

    @Test
    void l_ecran_exporte_aussi_les_donnees_avec_leur_lieu(FxRobot robot) throws Exception {
        charger(robot, nContacts("PIPKUH", "Pipistrelle de Kuhl", 5));
        Path csv = dossier.resolve("activite.csv");
        controleur.selecteur().definir(new SelecteurFige(csv));

        robot.clickOn("#boutonExporterDonnees");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(Files.exists(csv))
                .as("la ligne de commande savait exporter les données, l'écran le sait désormais aussi")
                .isTrue();
        assertThat(Files.readString(csv))
                .as("chaque ligne porte son lieu, sinon l'export ne se recoupe pas")
                .contains("Carré;Point;Nuit");
        Label retour = robot.lookup("#lblRetour").queryAs(Label.class);
        assertThat(retour.getText()).contains("exportée");
    }

    @Test
    void sans_courbe_tracee_l_export_est_grise(FxRobot robot) {
        charger(robot, List.of());

        Button exporter = robot.lookup("#boutonExporterImage").queryAs(Button.class);
        assertThat(exporter.isDisabled())
                .as("rien à exporter : le bouton le dit en se grisant plutôt que d'accepter un clic sans effet")
                .isTrue();
    }

    /// Nombre de couleurs distinctes d'un rendu, plafonné : au-delà de quelques teintes, l'image porte du
    /// dessin (courbes, axes, texte) et n'est donc pas l'aplat uni d'une capture ratée.
    private static int couleursDistinctes(BufferedImage image) {
        Set<Integer> couleurs = new HashSet<>();
        for (int x = 0; x < image.getWidth() && couleurs.size() <= 32; x += 4) {
            for (int y = 0; y < image.getHeight() && couleurs.size() <= 32; y += 4) {
                couleurs.add(image.getRGB(x, y));
            }
        }
        return couleurs.size();
    }

    /// Sélecteur de fichier **figé** : répond toujours le même chemin, sans ouvrir de `FileChooser` natif
    /// (qui figerait le test headless).
    private record SelecteurFige(Path fichier) implements fr.univ_amu.iut.commun.view.SelecteurFichier {

        @Override
        public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
            return Optional.of(fichier);
        }

        @Override
        public Optional<Path> choisirFichier(
                String titre, Optional<Path> dossierInitial, fr.univ_amu.iut.commun.view.FiltreFichier filtre) {
            return Optional.of(fichier);
        }

        @Override
        public Optional<Path> enregistrerFichier(
                String titre, String nomPropose, fr.univ_amu.iut.commun.view.FiltreFichier filtre) {
            return Optional.of(fichier);
        }
    }

    @Test
    void l_etat_vide_nomme_la_dimension_responsable(FxRobot robot) {
        charger(robot, List.of());

        Label vide = robot.lookup("#lblEtatVide").queryAs(Label.class);
        assertThat(vide.isVisible()).isTrue();
        assertThat(vide.getText())
                .as("nuit sans donnée : la cause est nommée, pas un « aucune donnée » muet")
                .contains("Aucune espèce détectée");
    }

    private static List<ContactHoraire> concat(List<ContactHoraire> a, List<ContactHoraire> b) {
        List<ContactHoraire> tous = new ArrayList<>(a);
        tous.addAll(b);
        return tous;
    }

    private void charger(FxRobot robot, List<ContactHoraire> contacts) {
        when(service.contactsDuPassage(PASSAGE)).thenReturn(contacts);
        ContextePassage contexte = new ContextePassage(PASSAGE, 3, new ContexteSite("640380", "A1", "Étang"));
        robot.interact(() -> controleur.ouvrirSur(contexte));
    }

    /// Contacts d'un **groupe taxonomique donné**, pour éprouver la partition par catégorie.
    private static List<ContactHoraire> nContactsDuGroupe(String taxon, String groupe, int nombre) {
        List<ContactHoraire> contacts = new ArrayList<>();
        for (int i = 0; i < nombre; i++) {
            contacts.add(new ContactHoraire(taxon, taxon, groupe, LocalDateTime.of(2026, 6, 20, 22, i)));
        }
        return contacts;
    }

    @Test
    @DisplayName("#2967 : la puce « Lieu » groupe les dimensions, qualifie le point, et retient deux carrés")
    void critere_lieu_filtre_sur_chaque_dimension(FxRobot robot) {
        // Barre autonome plutôt que le semis de l'écran : celui-ci n'a qu'un carré et qu'une commune, et
        // l'étendre ferait porter à tous les autres cas un jeu de données qu'ils n'utilisent pas.
        //
        // Le semis porte à dessein DEUX carrés ayant chacun un point « Z1 » : c'est le cas que le schéma
        // autorise (UNIQUE(site_id, code)) et que la liste doit rendre non ambigu.
        ObservableList<ContactHoraire> source = FXCollections.observableArrayList(
                contactLieu("PIPKUH", "Ahetze", "640380", "Z1"), contactLieu("BARBAR", "Biarritz", "870150", "Z1"));
        FilteredList<ContactHoraire> vues = new FilteredList<>(source);
        Filtres<ContactHoraire> filtresLocaux = new Filtres<>(vues, () -> {});
        MenuButton menuLocal = new MenuButton();
        FlowPane pucesLocales = new FlowPane();
        GestionnaireFiltres<ContactHoraire> ignore = new GestionnaireFiltres<>(
                new TextField(),
                menuLocal,
                pucesLocales,
                filtresLocaux,
                List.of(CriteresActivite.lieu(() -> source)),
                CriteresActivite.rechercheTexte());
        assertThat(ignore).isNotNull();

        robot.interact(() -> menuLocal.getItems().get(0).fire());
        MenuButton choixLieu = menuBoutonDeLieu(pucesLocales);
        assertThat(entetesLieu(choixLieu)).containsExactly("Communes", "Carrés", "Points");
        assertThat(cochablesLieu(choixLieu))
                .as("le point paraît QUALIFIÉ : un « Z1 » nu désignerait les deux carrés à la fois (#2992)")
                .containsExactly("Ahetze", "Biarritz", "640380", "870150", "640380 · Z1", "870150 · Z1");
        assertThat(vues).as("rien de coché n'écarte rien").hasSize(2);

        // La COMMUNE seule : la dimension que l'écran ne savait pas filtrer avant #2967.
        robot.interact(() -> cocheLieu(choixLieu, "Ahetze").setSelected(true));
        assertThat(vues).extracting(ContactHoraire::taxon).containsExactly("PIPKUH");

        // Le point qualifié désigne UN point d'UN carré, là où deux puces en conjonction le disaient.
        robot.interact(() -> {
            cocheLieu(choixLieu, "Ahetze").setSelected(false);
            cocheLieu(choixLieu, "870150 · Z1").setSelected(true);
        });
        assertThat(vues)
                .as("« 870150 · Z1 » ne retient que le Z1 de CE carré")
                .extracting(ContactHoraire::taxon)
                .containsExactly("BARBAR");

        // DEUX carrés à la fois : ce que la puce « Carré » à choix unique interdisait.
        robot.interact(() -> {
            cocheLieu(choixLieu, "870150 · Z1").setSelected(false);
            cocheLieu(choixLieu, "640380").setSelected(true);
            cocheLieu(choixLieu, "870150").setSelected(true);
        });
        assertThat(vues)
                .as("appartenance : deux carrés cochés retiennent les deux, impossible avant #2967")
                .hasSize(2);
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

    private static ContactHoraire contactLieu(String taxon, String commune, String carre, String point) {
        return new ContactHoraire(
                taxon, taxon, "Chiroptères", LocalDateTime.of(2026, 6, 20, 22, 0), commune, carre, point, 1L, null);
    }

    /// Contacts d'un **passage donné**, pour distinguer les nuits du protocole des nuits opportunistes.
    private static List<ContactHoraire> nContactsDuPassage(String taxon, long idPassage, int nombre) {
        List<ContactHoraire> contacts = new ArrayList<>();
        for (int i = 0; i < nombre; i++) {
            contacts.add(new ContactHoraire(
                    taxon,
                    taxon,
                    "Chiroptères",
                    LocalDateTime.of(2026, 6, 20, 22, i),
                    "Ahetze",
                    "640380",
                    "A1",
                    idPassage,
                    null));
        }
        return contacts;
    }

    private static List<ContactHoraire> nContacts(String taxon, String nom, int nombre) {
        List<ContactHoraire> contacts = new ArrayList<>();
        for (int i = 0; i < nombre; i++) {
            contacts.add(new ContactHoraire(taxon, nom, "Chiroptères", LocalDateTime.of(2026, 6, 20, 22, i)));
        }
        return contacts;
    }
}
