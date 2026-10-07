package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.CarteDeRecette;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le champ de la source désignée ne s'écrit que sur le fil JavaFX (#6138).
///
/// Monté sur le câblage de production, donc sur l'exécuteur asynchrone : un exécuteur synchrone
/// jouerait la tâche sur le fil du geste et ne pourrait pas voir le défaut. Le fil se relève dans
/// une liste et s'affirme après : JavaFX avale l'exception qu'un écouteur lèverait.
@ExtendWith(ApplicationExtension.class)
class SourceDesigneeSurLeFilTest {

    private static final long DELAI_MS = 30_000L;

    private final List<String> filsEtrangers = new CopyOnWriteArrayList<>();
    private final AtomicInteger ecritures = new AtomicInteger();

    private Injector injecteur;

    @TempDir
    private Path espace;

    @Start
    void start(Stage stage) throws IOException {
        System.setProperty("vigiechiro.workspace", espace.toString());
        injecteur = RacineInjecteur.creer();
        new MigrationSchema(injecteur.getInstance(SourceDeDonnees.class)).migrer();
        FXMLLoader chargeur = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        chargeur.setControllerFactory(DiagnosticGuice.pour(injecteur));
        Parent racine = chargeur.load();
        FenetreAjustable.poserHabillee(stage, racine, 1100, 760);
        injecteur.getInstance(NavigationImportation.class).ouvrir();
        FenetreAjustable.afficher(stage);
    }

    @AfterEach
    void rendreLEspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("désigner une source écrit le champ du dossier sur le fil JavaFX, et nulle part ailleurs")
    void designer_une_source_ecrit_le_champ_sur_le_fil(FxRobot robot) throws IOException {
        Path carte = CarteDeRecette.materialiser("sd-sans-journal");
        Attente.surLeFil(
                () -> {
                    champ(robot).textProperty().addListener((obs, avant, apres) -> releverLeFil());
                    controleur().selecteur().definir(repondant(carte));
                    robot.lookup("#boutonParcourir").queryAs(Button.class).fire();
                },
                "désigner la carte par « Parcourir »",
                DELAI_MS);

        InspectionConclue.attendre(
                robot, "l'inspection n'a pas conclu : la désignation n'est pas allée à son terme", DELAI_MS);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(ecritures)
                .as("le champ doit avoir été écrit, sans quoi ce cas ne constate rien")
                .hasPositiveValue();
        assertThat(filsEtrangers)
                .as("le champ du dossier appartient à la scène : l'écrire depuis la tâche de fond"
                        + " réécrit le texte et son curseur pendant que le fil JavaFX les met en page")
                .isEmpty();
        assertThat(Attente.surLeFil(() -> champ(robot).getText(), "relire le champ du dossier", DELAI_MS))
                .as("la source désignée s'affiche comme avant")
                .isEqualTo(carte.toString());
    }

    private void releverLeFil() {
        ecritures.incrementAndGet();
        if (!Platform.isFxApplicationThread()) {
            filsEtrangers.add(Thread.currentThread().toString());
        }
    }

    private static TextField champ(FxRobot robot) {
        return robot.lookup("#champDossier").queryAs(TextField.class);
    }

    private ImportationController controleur() {
        Object courant =
                injecteur.getInstance(Navigateur.class).historique().getLast().controleur();
        assertThat(courant).isInstanceOf(ImportationController.class);
        return (ImportationController) courant;
    }

    private static SelecteurFichier repondant(Path carte) {
        return new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                return Optional.of(carte);
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
                return Optional.of(carte);
            }

            @Override
            public Optional<Path> enregistrerFichier(String titre, String nomPropose, FiltreFichier filtre) {
                throw new AssertionError("l'import lit une source : ce geste n'écrit aucun fichier");
            }
        };
    }
}
