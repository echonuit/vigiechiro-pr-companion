package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.ResultatRecherche;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.TypeResultat;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test E2E de la **recherche globale** du chrome (#144, #1821) : de la saisie jusqu'à l'**écran
/// ouvert**.
///
/// Pourquoi ce test existe : la recherche n'était couverte qu'au **niveau service**
/// (`ServiceRechercheGlobaleTest`). Le chrome (`RechercheChrome`) et surtout la **navigation du
/// résultat vers son écran** - la couture qui porte la valeur - n'avaient aucun test, à aucun niveau.
///
/// Parcours réel sur de vrais services + base SQLite : on saisit dans `#champRecherche`, on attend que
/// l'**anti-rebond** (180 ms) livre les résultats, on choisit une entrée au clavier (↓ puis Entrée) et
/// on vérifie l'écran atteint. Les chemins non nominaux (aucune correspondance, requête blanche) sont
/// exercés aussi : ce sont eux qui manquent d'habitude.
@ExtendWith(ApplicationExtension.class)
class ParcoursRechercheGlobaleE2ETest {

    private static final String CARRE = "640380";
    private static final String POINT = "A1";
    private static final String NOM_SITE = "Étang de la Tuilière";

    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-recherche-e2e");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        seederSitePointPassage(source);

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1100, 760));
        stage.show(); // démarre sur l'accueil du chrome
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Chercher un site puis l'ouvrir au clavier : la recherche mène à la fiche du site")
    void recherche_un_site_puis_l_ouvre(FxRobot robot) throws TimeoutException {
        NavigationViewModel navigation = injector.getInstance(NavigationViewModel.class);
        assertThat(navigation.getVueCourante()).isEqualTo("accueil");

        ListView<?> liste = saisirEtAttendreDesResultats(robot, "Tuilière");
        assertThat(liste.getItems())
                .as("le nom convivial du site doit être trouvé")
                .isNotEmpty();
        assertThat(resultat(liste, 0).type()).isEqualTo(TypeResultat.SITE);

        ouvrirLeResultat(robot, liste, 0);

        assertThat(navigation.getVueCourante())
                .as("ouvrir un résultat de type site mène à sa fiche")
                .isEqualTo("site-detail");
    }

    @Test
    @DisplayName("Chercher un passage puis l'ouvrir au clavier : la recherche mène à M-Passage")
    void recherche_un_passage_puis_l_ouvre(FxRobot robot) throws TimeoutException {
        NavigationViewModel navigation = injector.getInstance(NavigationViewModel.class);

        // Le n° de carré est porté par les trois natures de résultat : on récupère donc site, point ET
        // passage, et on choisit explicitement le passage - ce qui vérifie au passage le groupage.
        ListView<?> liste = saisirEtAttendreDesResultats(robot, CARRE);
        int rang = indexDuPremier(liste, TypeResultat.PASSAGE);
        assertThat(rang)
                .as("la nuit seedée doit ressortir comme résultat de type passage")
                .isNotNegative();

        ouvrirLeResultat(robot, liste, rang);

        assertThat(navigation.getVueCourante())
                .as("ouvrir un résultat de type passage mène à M-Passage")
                .isEqualTo("passage");
    }

    @Test
    @DisplayName("Requête sans correspondance : le panneau s'ouvre quand même, sur « Aucun résultat »")
    void requete_sans_correspondance_montre_aucun_resultat(FxRobot robot) throws TimeoutException {
        TextField champ = robot.lookup("#champRecherche").queryAs(TextField.class);
        VBox panneau = robot.lookup("#panneauResultats").queryAs(VBox.class);
        ListView<?> liste = robot.lookup("#listeResultats").queryAs(ListView.class);

        robot.interact(() -> champ.setText("zzzz-introuvable"));
        // Le panneau doit s'ouvrir malgré l'absence de résultat (#795) : rester invisible ferait croire
        // que la recherche n'a pas tourné.
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, panneau::isVisible);

        assertThat(liste.getItems()).isEmpty();
        assertThat(liste.getPlaceholder())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(Label.class))
                .extracting(Label::getText)
                .isEqualTo("Aucun résultat");
    }

    @Test
    @DisplayName("Requête blanche : le panneau se referme (rien à proposer)")
    void requete_blanche_referme_le_panneau(FxRobot robot) throws TimeoutException {
        TextField champ = robot.lookup("#champRecherche").queryAs(TextField.class);
        VBox panneau = robot.lookup("#panneauResultats").queryAs(VBox.class);

        saisirEtAttendreDesResultats(robot, "Tuilière"); // le panneau est ouvert...
        robot.interact(() -> champ.setText("   "));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !panneau.isVisible()); // ...puis se referme
    }

    /// Saisit `requete` dans le champ du chrome et attend que l'**anti-rebond** (180 ms) ait livré des
    /// résultats. Sans cette attente, la liste est encore vide au moment de l'assertion.
    private ListView<?> saisirEtAttendreDesResultats(FxRobot robot, String requete) throws TimeoutException {
        TextField champ = robot.lookup("#champRecherche").queryAs(TextField.class);
        ListView<?> liste = robot.lookup("#listeResultats").queryAs(ListView.class);
        robot.interact(() -> champ.setText(requete));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !liste.getItems().isEmpty());
        return liste;
    }

    /// Le résultat de rang `rang`. La liste du chrome est typée `ResultatRecherche` côté FXML ; le
    /// `lookup` la rend sans paramètre de type, d'où ce transtypage **d'élément** (vérifié) plutôt qu'un
    /// transtypage de la liste (qui, lui, serait non vérifié).
    private static ResultatRecherche resultat(ListView<?> liste, int rang) {
        return (ResultatRecherche) liste.getItems().get(rang);
    }

    /// Ouvre le résultat de rang `rang` **comme le ferait l'utilisateur** : on entre dans la liste, on
    /// s'y positionne, puis Entrée.
    private void ouvrirLeResultat(FxRobot robot, ListView<?> liste, int rang) {
        robot.interact(() -> {
            liste.requestFocus();
            liste.getSelectionModel().select(rang);
        });
        robot.type(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static int indexDuPremier(ListView<?> liste, TypeResultat type) {
        for (int i = 0; i < liste.getItems().size(); i++) {
            if (resultat(liste, i).type() == type) {
                return i;
            }
        }
        return -1;
    }

    /// Sème la topologie d'une nuit (utilisateur, site **nommé**, point, passage, session) via la fixture
    /// partagée : de quoi faire ressortir les trois natures de résultat sur une recherche par n° de carré.
    private void seederSitePointPassage(SourceDeDonnees source) {
        JeuDeDonneesPassage.dans(source)
                .carre(CARRE)
                .nomSite(NOM_SITE)
                .point(POINT)
                .statut(StatutWorkflow.TRANSFORME)
                .semer();
    }
}
