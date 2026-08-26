package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.EtapeNavigation;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de la **façade** [NavigationAudio] (#3521).
///
/// Elle n'était citée dans aucun fichier de test, là où ses jumelles ont chacune la leur. Sur le vrai
/// injecteur ([RacineInjecteur]) plus le chrome, on appelle `ouvrir(source)` et on vérifie que la
/// chaîne tient : ressource FXML trouvée, `controllerFactory` Guice, contexte donné au contrôleur,
/// publication dans le [Navigateur].
///
/// Le passage est **absent de la base** : cela suffit à exercer la chaîne sans seeding.
@ExtendWith(ApplicationExtension.class)
class NavigationAudioViewTest {

    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-audio-nav");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1100, 760));
        injector.getInstance(NavigationAudio.class)
                .ouvrir(new SourceObservations.ParPassage(
                        new ContextePassage(999L, 3, new ContexteSite("640380", "A1", "Étang de la Tuilière"))));
        stage.show();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#3521 : ouvrir(source) charge Sons & validation et le publie dans le navigateur")
    void ouvrir_publie_l_ecran_dans_le_navigateur() {
        assertThat(injector.getInstance(NavigationViewModel.class)
                        .vueCouranteProperty()
                        .get())
                .isEqualTo("audio");
    }

    @Test
    @DisplayName("#3521 : le fil d'Ariane complet prouve que la source a été donnée au contrôleur")
    void le_fil_prouve_que_la_source_a_ete_transmise() {
        // C'est là que ça se voit, et nulle part ailleurs : sans `ouvrirSur(source, cible)`, le
        // contrôleur n'a pas de source, son emplacement retombe sur un fil minimal, et l'écran s'ouvre
        // vide sans que rien d'autre ne bronche.
        assertThat(injector.getInstance(Navigateur.class).filActuel())
                .extracting(Lieu::libelle)
                .containsExactly(
                        "Accueil", "Mes sites", "Carré 640380", "Détails du passage N° 3", "Sons & validation");
    }

    @Test
    @DisplayName("#3521 : le corpus de références s'ouvre en RACINE, une nuit s'empile")
    void le_corpus_de_references_repart_de_la_racine(FxRobot robot) {
        // Deux façons d'arriver au même écran, et elles ne laissent pas la même pile derrière elles.
        // Depuis la carte d'accueil (source « références »), on part d'en haut : la pile se réinitialise,
        // sinon « ← Retour » ramènerait sur la nuit qu'on regardait avant, sans rapport avec le corpus.
        // Depuis une nuit, au contraire, l'écran s'empile pour qu'on puisse y revenir.
        Navigateur navigateur = injector.getInstance(Navigateur.class);
        // Deux précautions, trouvées en voyant le mutant survivre deux fois. D'abord repartir de
        // l'accueil : « audio » est déjà dans la pile depuis le démarrage, et l'anti-ré-entrance ferait
        // dépiler `empiler` jusqu'à lui - donnant exactement le même résultat que `ouvrirRacine`.
        // Ensuite intercaler un écran : depuis la racine nue, les deux branches sont indiscernables.
        robot.interact(navigateur::afficherAccueil);
        robot.interact(() -> navigateur.empiler(new javafx.scene.Group(), "sites", "Mes sites", null));

        robot.interact(() -> injector.getInstance(NavigationAudio.class)
                .ouvrir(new SourceObservations.References("demo-enseignant")));

        assertThat(navigateur.historique())
                .as("la pile repart de la racine : l'écran intercalé a disparu")
                .extracting(EtapeNavigation::id)
                .containsExactly("accueil", "audio");

        assertThat(injector.getInstance(Navigateur.class).filActuel())
                .extracting(Lieu::libelle)
                // Le fil nomme le CORPUS (« Sons de référence ») là où l'entrée de navigation nomme
                // l'écran : deux mots pour deux questions, « où suis-je » et « quel écran ».
                .containsExactly("Accueil", "Sons de référence");
    }
}
