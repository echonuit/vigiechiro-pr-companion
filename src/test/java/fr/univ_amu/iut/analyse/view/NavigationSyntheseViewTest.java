package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de la **façade** [NavigationSynthese] (#3521).
///
/// Elle n'était citée dans aucun fichier de test, là où ses jumelles - diagnostic, qualification, lot,
/// validation - ont chacune la leur. Sur le vrai injecteur ([RacineInjecteur]) plus le chrome, on
/// appelle `ouvrir(passage)` et on vérifie que la chaîne entière tient : ressource FXML trouvée,
/// `controllerFactory` Guice, publication dans le [Navigateur] du socle.
///
/// Le passage est **absent de la base** : cela suffit à exercer toute la chaîne sans seeding, comme
/// `NavigationDiagnosticViewTest`.
@ExtendWith(ApplicationExtension.class)
class NavigationSyntheseViewTest {

    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-synthese");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        FenetreAjustable.poser(stage, racine, 1100, 760);
        injector.getInstance(NavigationSynthese.class)
                .ouvrir(new ContextePassage(999L, 3, new ContexteSite("640380", "A1", "Étang de la Tuilière")));
        FenetreAjustable.afficher(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#3521 : ouvrir(passage) charge l'écran Synthèse via Guice et le publie dans le navigateur")
    void ouvrir_publie_l_ecran_dans_le_navigateur() {
        // La table de l'écran existe : le FXML a été trouvé, chargé, et son contrôleur injecté.
        assertThat(injector.getInstance(NavigationViewModel.class)
                        .vueCouranteProperty()
                        .get())
                .isEqualTo("synthese");

        // Et le fil d'Ariane est COMPLET, ce qui prouve que la façade a bien donné son contexte à
        // l'écran. C'est là que ça se voit et nulle part ailleurs : sans `ouvrirSur(passage)`, le
        // contrôleur n'a pas de contexte, son `emplacement()` retombe sur un segment unique, et l'écran
        // s'ouvre vide sans que rien d'autre ne bronche.
        assertThat(injector.getInstance(Navigateur.class).filActuel())
                .extracting(Lieu::libelle)
                .containsExactly(
                        "Accueil", "Mes sites", "Carré 640380", "Détails du passage N° 3", "Synthèse de la nuit");
    }

    @Test
    @DisplayName("#3521 : l'écran chargé porte bien sa table de synthèse")
    void l_ecran_charge_porte_sa_table(FxRobot robot) {
        assertThat(robot.lookup("#tableSynthese").tryQuery()).isPresent();
        assertThat(robot.lookup("#tableSynthese").queryAs(TableView.class)).isNotNull();
    }
}
