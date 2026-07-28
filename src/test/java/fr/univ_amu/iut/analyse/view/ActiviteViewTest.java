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
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
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
        stage.setScene(new Scene(vue, 960, 640));
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
        assertThat(robot.lookup("Orthoptères et cigales").tryQuery())
                .as("l'onglet porte le nom exact de sa catégorie, il ne promet pas plus qu'il ne filtre")
                .isPresent();
    }

    @Test
    void le_menu_filtre_offre_la_cascade_geo_et_le_taxon_parent(FxRobot robot) {
        MenuButton menu = robot.lookup("#menuAjoutFiltre").queryAs(MenuButton.class);

        assertThat(menu.getItems())
                .extracting(MenuItem::getText)
                .as("cascade carré → point → nuit, plus le taxon parent")
                .contains("Carré", "Point", "Nuit", "Taxon parent");
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

    private static List<ContactHoraire> nContacts(String taxon, String nom, int nombre) {
        List<ContactHoraire> contacts = new ArrayList<>();
        for (int i = 0; i < nombre; i++) {
            contacts.add(new ContactHoraire(taxon, nom, "Chiroptères", LocalDateTime.of(2026, 6, 20, 22, i)));
        }
        return contacts;
    }
}
