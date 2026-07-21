package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX du **câblage Vue ↔ ViewModel** de la feature `sites`, vérifié par un
/// **vrai lookup des `fx:id`** (et non en relisant les propriétés du ViewModel).
///
/// Les tests de vue de base (`MesSitesViewTest`, `ModalePointViewTest`) confirment le rendu et
/// quelques parcours, mais ils n'interrogent pas systématiquement les contrôles par leur `fx:id` :
/// un écran non câblé (FXML sans `fx:id`, controller sans `@FXML`) peut alors passer
/// inaperçu. Ce test ferme ce trou : pour chacun des trois écrans (accueil M-Sites, détail
/// M-Site-detail, modale d'ajout de point), il récupère les contrôles par `#fx:id`, vérifie que
/// leur état reflète le ViewModel, **et** qu'une interaction (déclenchement de carte, lien de
/// retour, saisie de code) produit bien l'effet câblé.
///
/// Les interactions sont déclenchées sur le thread JavaFX (`robot.interact` + handlers / `fire()`)
/// plutôt qu'avec le robot souris/clavier de l'OS, pour rester déterministe sous xvfb comme sous
/// Wayland (même raison que `MesSitesViewTest`).
@ExtendWith(ApplicationExtension.class)
class SitesVueIntegrationTest {

    private static final String ID_USER = "u-test";
    private static final String CARTE_ETANG = "Carré 640380";
    private static final String BOUTON_AJOUTER_POINT = "+ Ajouter un point";

    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-sites-integration");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        // Composition réelle, mais exécuteur SYNCHRONE (#1212) : même raison que MesSitesViewTest.
        injector =
                Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ExecuteurTache.class)
                                .to(ExecuteurTacheSynchrone.class)
                                .in(Singleton.class);
                    }
                }));
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        seeder(source);
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1100, 720));
        injector.getInstance(NavigationSites.class).ouvrirAccueil();
        stage.show();
    }

    private void seeder(SourceDeDonnees source) {
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        ServiceSites service = injector.getInstance(ServiceSites.class);
        Site etang = service.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(etang.id(), "A1", 43.5, 5.4, "Chêne");
        Site zac = service.creerSite("752204", "ZAC Nord", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(zac.id(), "A1", null, null, null);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    // ----- Écran d'accueil M-Sites -------------------------------------------------------------

    @Test
    @DisplayName("Le résumé de l'accueil est déporté en zone centre de la barre de statut (#693)")
    void resume_accueil_en_barre_de_statut(FxRobot robot) {
        // Le titre/sous-titre d'en-tête ont été retirés (#693) ; le résumé occupe la barre de statut.
        Label piedCentre = robot.lookup("#piedCentre").queryAs(Label.class);

        // Deux sites seedés, zéro passage : le ViewModel compose « 2 sites déclarés · … ».
        assertThat(piedCentre.getText()).contains("2 sites déclarés");
    }

    @Test
    @DisplayName("Avec des sites, #etatVide est masqué et #listeCartes est peuplée (état = ViewModel)")
    void liste_affichee_et_etat_vide_masque(FxRobot robot) {
        ScrollPane zoneListe = robot.lookup("#zoneListe").queryAs(ScrollPane.class);
        VBox listeCartes = robot.lookup("#listeCartes").queryAs(VBox.class);
        VBox etatVide = robot.lookup("#etatVide").queryAs(VBox.class);

        assertThat(zoneListe.isVisible()).isTrue();
        assertThat(listeCartes.getChildren()).hasSize(2);
        assertThat(etatVide.isVisible()).isFalse();
        assertThat(etatVide.isManaged()).isFalse();
    }

    @Test
    @DisplayName("Le chevron d'invite « › » est VISIBLE sur chaque carte (non éteint par une collision de nom)")
    void chevron_de_carte_est_visible(FxRobot robot) {
        // Garde-fou du correctif #1974 : la classe `carte-chevron` de la feature sites entrait en
        // collision avec celle de base.css (`-fx-opacity: 0`, révélée au survol des cartes d'accueil).
        // La carte de site n'étant jamais une `.carte-activite`, le chevron restait invisible. Renommé
        // `.chevron-site`, il retrouve son opacité par défaut. Rien ne testait cette visibilité - c'est
        // pour cela que le défaut a vécu depuis la création de la feature.
        var chevrons = robot.lookup(".chevron-site").queryAll();

        assertThat(chevrons).as("un chevron d'invite par carte de site").hasSize(2);
        assertThat(chevrons)
                .allSatisfy(chevron -> assertThat(chevron.getOpacity())
                        .as("le chevron ne doit pas être éteint (opacity 0 = invisible)")
                        .isEqualTo(1.0));
    }

    // ----- Écran de détail M-Site-detail -------------------------------------------------------

    @Test
    @DisplayName("Déclencher une carte affiche le bandeau d'identité du détail (labels par fx:id)")
    void carte_ouvre_le_bandeau_du_detail(FxRobot robot) {
        ouvrirDetail(robot, CARTE_ETANG);

        Label piedGauche = robot.lookup("#piedGauche").queryAs(Label.class);
        Label valNumeroCarre = robot.lookup("#valNumeroCarre").queryAs(Label.class);
        Label valDepartement = robot.lookup("#valDepartement").queryAs(Label.class);
        Label valProtocole = robot.lookup("#valProtocole").queryAs(Label.class);
        FlowPane cartesPoints = robot.lookup("#cartesPoints").queryAs(FlowPane.class);

        // Le titre (nom du site) est déporté en zone gauche de la barre de statut (#693).
        assertThat(piedGauche.getText()).isEqualTo("Carré 640380 — Étang de la Tuilière");
        assertThat(valNumeroCarre.getText()).isEqualTo("640380");
        assertThat(valDepartement.getText()).isEqualTo("64");
        assertThat(valProtocole.getText()).isEqualTo(Protocole.STANDARD.libelle());
        // Un seul point (A1) seedé sur ce site : une seule carte de point bâtie en code.
        assertThat(cartesPoints.getChildren()).hasSize(1);
    }

    @Test
    @DisplayName("Les boutons #boutonModifier/#boutonSupprimer reflètent l'état du ViewModel")
    void boutons_du_detail_refletent_le_viewmodel(FxRobot robot) {
        ouvrirDetail(robot, CARTE_ETANG);

        Button boutonModifier = robot.lookup("#boutonModifier").queryAs(Button.class);
        Button boutonSupprimer = robot.lookup("#boutonSupprimer").queryAs(Button.class);

        // « Modifier » est actif : il ouvre la boîte d'édition de la fiche site.
        assertThat(boutonModifier.isDisabled()).isFalse();
        // Aucun passage rattaché : la suppression est possible, donc le bouton est actif.
        assertThat(boutonSupprimer.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Le ← Retour du chrome ramène du détail à la liste des sites")
    void retour_du_detail_ramene_a_la_liste(FxRobot robot) {
        ouvrirDetail(robot, CARTE_ETANG);
        Button retour = robot.lookup("#boutonRetour").queryAs(Button.class);

        robot.interact(retour::fire);

        // #listeCartes est la liste M-Sites : sa présence (avec ses 2 cartes) prouve le retour à la liste.
        VBox listeCartes = robot.lookup("#listeCartes").queryAs(VBox.class);
        assertThat(listeCartes.getChildren()).hasSize(2);
    }

    // ----- Modale d'ajout de point -------------------------------------------------------------

    @Test
    @DisplayName("Un code invalide (R2) surligne #champCode et désactive #boutonValider")
    void code_invalide_surligne_et_desactive(FxRobot robot) {
        ouvrirDetail(robot, CARTE_ETANG);
        ouvrirModaleAjout(robot);

        Label titreModale = robot.lookup("#titreModale").queryAs(Label.class);
        assertThat(titreModale.getText()).contains("Nouveau point");

        TextField champCode = robot.lookup("#champCode").queryAs(TextField.class);
        robot.interact(() -> champCode.setText("ZZ"));

        Button boutonValider = robot.lookup("#boutonValider").queryAs(Button.class);
        assertThat(champCode.getStyleClass()).contains("champ-invalide");
        assertThat(boutonValider.isDisabled()).isTrue();
    }

    @Test
    @DisplayName("Un code valide retire le surlignage de #champCode et active #boutonValider")
    void code_valide_retire_surlignage_et_active(FxRobot robot) {
        ouvrirDetail(robot, CARTE_ETANG);
        ouvrirModaleAjout(robot);

        TextField champCode = robot.lookup("#champCode").queryAs(TextField.class);
        robot.interact(() -> champCode.setText("B2"));

        Button boutonValider = robot.lookup("#boutonValider").queryAs(Button.class);
        assertThat(champCode.getStyleClass()).doesNotContain("champ-invalide");
        assertThat(boutonValider.isDisabled()).isFalse();
    }

    // ----- Aides locales (volontairement dupliquées : chaque test de vue reste autonome) --------

    private void ouvrirDetail(FxRobot robot, String titreCarte) {
        HBox carte = trouverCarte(robot, titreCarte);
        robot.interact(() -> carte.getOnMouseClicked().handle(clicGauche()));
    }

    private void ouvrirModaleAjout(FxRobot robot) {
        Button ajouter = robot.lookup(BOUTON_AJOUTER_POINT).queryButton();
        robot.interact(ajouter::fire);
    }

    private static HBox trouverCarte(FxRobot robot, String titre) {
        return robot.lookup(".carte-site").queryAllAs(HBox.class).stream()
                .filter(carte -> contientTitre(carte, titre))
                .findFirst()
                .orElseThrow();
    }

    private static boolean contientTitre(HBox carte, String titre) {
        return carte.lookupAll(".carte-titre").stream()
                .anyMatch(noeud -> noeud instanceof Label label && titre.equals(label.getText()));
    }

    private static MouseEvent clicGauche() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                null);
    }
}
