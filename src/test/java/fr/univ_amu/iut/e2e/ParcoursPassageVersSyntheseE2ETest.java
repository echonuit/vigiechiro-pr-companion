package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.analyse.model.LigneSynthese;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
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
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// **Test E2E de parcours (P-Synthèse)** : depuis **M-Passage**, l'ouverture de **M-Synthese** par le
/// contrat socle `OuvrirSynthese`, puis la lecture du tableau **sur des observations réellement en base**.
///
/// Il traverse la couture que les tests d'écran ne voient pas : base → `ProjectionsAudioDao` →
/// `ServiceSynthese` → `ReferentielActivite` embarqué → ViewModel → tableau. `SyntheseViewTest` mocke le
/// service et lui dicte ses lignes ; il ne prouve donc **rien** du référentiel réel, ni du fait que la
/// jointure entre un code de taxon en base et une déclinaison du référentiel aboutisse.
///
/// La base, elle, se protège : une contrainte de clé étrangère refuse un code de taxon inconnu, si bien
/// qu'un code mal orthographié n'y entre jamais. Ce parcours ne couvre donc **pas** ce défaut-là : il vit
/// dans les fixtures qui construisent leurs objets sans passer par la base, et c'est
/// `CodesTaxonFixturesCaptureTest` qui l'y traque (#2715). Ce que ce parcours couvre, c'est la jointure
/// **entre deux référentiels distincts** : les taxons en base et les déclinaisons du référentiel
/// d'activité, qui n'ont ni la même source ni la même couverture.
@ExtendWith(ApplicationExtension.class)
class ParcoursPassageVersSyntheseE2ETest {

    /// Une nuit d'été : la saison se déduit de la date, et l'été a ses propres quantiles.
    private static final LocalDateTime NUIT = LocalDateTime.of(2026, 6, 21, 22, 15);

    private Injector injector;
    private long idPassage;
    private ContexteSite contexte;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-synthese");
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
        // Trois espèces, quatre contacts. La Pipistrelle commune en porte deux, ce qui distingue un
        // comptage réel d'un simple « une ligne par observation ». L'Épervier, lui, existe au référentiel
        // des TAXONS (363 entrées) mais pas à celui de l'ACTIVITÉ (98) : c'est lui qui prouve que la
        // couverture se calcule au lieu d'être toujours vraie.
        jeu.ajouterObservationA(NUIT, "Pippip");
        jeu.ajouterObservationA(NUIT.plusMinutes(20), "Pippip");
        jeu.ajouterObservationA(NUIT.plusMinutes(40), "Pipkuh");
        jeu.ajouterObservationA(NUIT.plusMinutes(60), "Accnis");
        idPassage = jeu.idPassage();
        contexte = new ContexteSite("640380", "A1", null);

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(DiagnosticGuice.pour(injector));
        Parent racine = loader.load();
        FenetreAjustable.poser(stage, racine, 1280, 860);
        FenetreAjustable.afficher(stage);
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private void ouvrirSynthese(FxRobot robot) {
        robot.interact(() -> injector.getInstance(OuvrirPassage.class).ouvrir(idPassage, contexte));
        robot.interact(robot.lookup("#boutonSynthese").queryAs(Button.class)::fire);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("M-Passage : la carte « Synthèse de la nuit » ouvre M-Synthese et compte les observations en base")
    void passage_ouvre_synthese_et_compte_les_observations(FxRobot robot) {
        NavigationViewModel navigation = injector.getInstance(NavigationViewModel.class);

        ouvrirSynthese(robot);

        assertThat(navigation.getVueCourante()).isEqualTo("synthese");
        TableView<?> table = robot.lookup("#tableSynthese").queryAs(TableView.class);
        assertThat(table.getItems())
                .as("une ligne par espèce, pas par observation : la chaîne base → projection → agrégation tient")
                .hasSize(3);

        LigneSynthese commune = (LigneSynthese) table.getItems().stream()
                .map(LigneSynthese.class::cast)
                .filter(ligne -> "Pippip".equals(ligne.codeTaxon()))
                .findFirst()
                .orElseThrow();
        assertThat(commune.contacts())
                .as("les deux contacts de la Pipistrelle commune sont sommés")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("La classe d'activité vient du référentiel RÉEL, pas d'un service mocké")
    void la_classe_vient_du_referentiel_embarque(FxRobot robot) {
        // Le cœur de ce parcours. `SyntheseViewTest` dicte ses seuils au service : il ne prouve pas que
        // le code d'un taxon en base joigne une déclinaison du référentiel embarqué. Une saison mal
        // déduite, une région introuvable, une déclinaison absente, et la jointure rend vide, sans que
        // rien ne rougisse.
        ouvrirSynthese(robot);

        TableView<?> table = robot.lookup("#tableSynthese").queryAs(TableView.class);

        assertThat(ligne(table, "Pippip").couvertParLeReferentiel())
                .as("la Pipistrelle commune est au référentiel : si cette jointure casse, l'écran se tait")
                .isTrue();
        assertThat(ligne(table, "Pippip").seuils())
                .as("les quantiles retenus accompagnent la classe, sinon elle est un verdict")
                .isPresent();

        // Le pendant, sans lequel la vérification précédente ne distinguerait pas une jointure réussie
        // d'un drapeau toujours vrai.
        assertThat(ligne(table, "Accnis").couvertParLeReferentiel())
                .as("l'Épervier est un taxon connu de la base, mais le référentiel d'activité ne le "
                        + "couvre pas : la couverture se calcule, elle n'est pas acquise")
                .isFalse();
        assertThat(ligne(table, "Accnis").libelleClasse())
                .as("et la cellule DIT pourquoi, plutôt que de rester vide")
                .isEqualTo("Non couvert par le référentiel");
    }

    /// La ligne d'un taxon dans le tableau affiché, ou un échec explicite si elle manque.
    private static LigneSynthese ligne(TableView<?> table, String codeTaxon) {
        return table.getItems().stream()
                .map(LigneSynthese.class::cast)
                .filter(l -> codeTaxon.equals(l.codeTaxon()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Aucune ligne pour le taxon " + codeTaxon));
    }

    @Test
    @DisplayName("Le référentiel employé est nommé à l'écran, avec la saison déduite de la nuit")
    void le_referentiel_employe_est_nomme(FxRobot robot) {
        ouvrirSynthese(robot);

        assertThat(robot.lookup("#lblReferentiel").queryAs(Label.class).getText())
                .as("une classe dont on ignore la référence est un oracle")
                .contains("Comparé au référentiel")
                .contains("Été");
    }

    @Test
    @DisplayName("« Validées seulement » recalcule : sans validation, le tableau se vide")
    void la_bascule_recalcule_sur_de_vraies_observations(FxRobot robot) {
        // La bascule ne masque pas des lignes, elle recalcule. Sur des observations semées et non
        // validées, elle doit donc tout retirer : un test d'écran sur service mocké ne le montre pas.
        ouvrirSynthese(robot);
        TableView<?> table = robot.lookup("#tableSynthese").queryAs(TableView.class);
        assertThat(table.getItems()).isNotEmpty();

        robot.clickOn(robot.lookup("#chkValideesSeulement").queryAs(CheckBox.class));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(table.getItems())
                .as("aucune observation validée : la lecture « validées seulement » est vide, et le dit")
                .isEmpty();
    }
}
