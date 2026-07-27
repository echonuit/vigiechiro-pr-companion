package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// **Test E2E de parcours (P-Activité)** : depuis **M-Passage**, l'ouverture de **M-Activite** par le
/// contrat socle `OuvrirActivite`, puis le tracé de la courbe **sur des observations réellement en base**.
///
/// Il traverse la couture que les tests d'écran ne voient pas : base → `ProjectionsAudioDao` →
/// `ServiceActivite` → ViewModel → graphe. Les tests de vue mockent le service, et le mock a longtemps
/// menti par omission : il ne rendait qu'**une** nuit, si bien que le repliement des nuits sur l'axe
/// nocturne n'était exercé par rien — le défaut (courbe en dents de scie) n'a été vu que sur une vraie
/// saison. Ce parcours sème donc **deux nuits**.
@ExtendWith(ApplicationExtension.class)
class ParcoursPassageVersActiviteE2ETest {

    /// Deux nuits distinctes, même heure : c'est le cas qui a échappé aux tests d'écran.
    private static final LocalDateTime PREMIERE_NUIT = LocalDateTime.of(2026, 6, 21, 22, 15);

    private static final LocalDateTime SECONDE_NUIT = LocalDateTime.of(2026, 6, 22, 22, 15);

    private Injector injector;
    private long idPassage;
    private ContexteSite contexte;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-activite");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .carre("640380")
                .point("A1")
                .position(43.4010, -1.5740)
                .semer();
        jeu.ajouterResultats();
        // Deux contacts d'une même espèce à la même heure de nuit, mais sur deux nuits : une fois repliés
        // sur l'axe nocturne, ils doivent former UN point de valeur 2, pas deux points superposés.
        jeu.ajouterObservationA(PREMIERE_NUIT, "Pippip");
        jeu.ajouterObservationA(SECONDE_NUIT, "Pippip");
        idPassage = jeu.idPassage();
        contexte = new ContexteSite("640380", "A1", null);

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1280, 860));
        stage.show();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("M-Passage : la carte « Activité de la nuit » ouvre M-Activite et trace les observations en base")
    void passage_ouvre_activite_et_trace_les_observations(FxRobot robot) {
        NavigationViewModel navigation = injector.getInstance(NavigationViewModel.class);

        robot.interact(() -> injector.getInstance(OuvrirPassage.class).ouvrir(idPassage, contexte));
        Button activite = robot.lookup("#boutonActivite").queryAs(Button.class);

        robot.interact(activite::fire);

        assertThat(navigation.getVueCourante()).isEqualTo("activite");
        LineChart<?, ?> graphe = robot.lookup("#grapheActivite").queryAs(LineChart.class);
        assertThat(graphe.getData())
                .as("l'espèce semée en base est tracée : la chaîne base → projection → service → courbe tient")
                .hasSize(1);
    }

    @Test
    @DisplayName("#2352 : deux nuits à la même heure se replient en un point sommé, pas en deux points")
    void deux_nuits_se_replient_sur_l_axe_nocturne(FxRobot robot) {
        robot.interact(() -> injector.getInstance(OuvrirPassage.class).ouvrir(idPassage, contexte));
        robot.interact(robot.lookup("#boutonActivite").queryAs(Button.class)::fire);

        LineChart<?, ?> graphe = robot.lookup("#grapheActivite").queryAs(LineChart.class);
        XYChart.Series<?, ?> serie = graphe.getData().get(0);
        long abscissesDistinctes = serie.getData().stream()
                .map(donnee -> donnee.getXValue().toString())
                .distinct()
                .count();

        assertThat(serie.getData().size())
                .as("une abscisse ne porte qu'un point : sinon la ligne repart en arrière à chaque nuit")
                .isEqualTo((int) abscissesDistinctes);
        // La valeur, et pas seulement la forme : les deux nuits doivent être SOMMÉES. Sans cette
        // vérification le test reste vert alors que le repliement est neutralisé — le comblement des
        // zéros dédoublonne les abscisses, mais en écrasant, ce qui perdrait un contact sur deux.
        int contactsTraces = serie.getData().stream()
                .mapToInt(donnee -> ((Number) donnee.getYValue()).intValue())
                .sum();
        assertThat(contactsTraces)
                .as("les deux nuits sont SOMMÉES : la courbe porte deux contacts, pas un seul écrasant l'autre")
                .isEqualTo(2);
    }
}
