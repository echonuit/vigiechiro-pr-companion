package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.analyse.model.LigneSynthese;
import fr.univ_amu.iut.analyse.model.ServiceSynthese;
import fr.univ_amu.iut.analyse.viewmodel.SyntheseViewModel;
import fr.univ_amu.iut.commun.model.ClasseActivite;
import fr.univ_amu.iut.commun.model.ConfianceReferentiel;
import fr.univ_amu.iut.commun.model.ContexteActivite;
import fr.univ_amu.iut.commun.model.SaisonActivite;
import fr.univ_amu.iut.commun.model.SeuilsActivite;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableColumn;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Vérifie qu'**aucune cellule du tableau ne tronque** son texte, à la taille où l'aperçu est rendu.
///
/// ## Pourquoi ce garde n'existait pas, et aurait dû
///
/// La capture publiée montrait « Q98 = … » et « Orthoptères et ciga... » (#3074). Personne ne l'avait vu
/// pendant des mois, pour une raison qui rend la revue humaine impuissante ici : **le même code ne
/// produit pas la même image en local et en CI**. Les deux environnements n'ont pas les mêmes polices,
/// et le tableau ne disposait que de **4 pixels** de marge (1060 px de colonnes pour 1064 px utiles).
/// Ce qui tenait sur un poste débordait sur l'autre.
///
/// Une revue visuelle ne peut pas rattraper cela : elle porte sur une image, produite quelque part. Ce
/// test, lui, mesure le texte avec **les polices de la machine qui l'exécute** - donc celles de la CI
/// quand il tourne en CI. C'est le seul dispositif qui voit le défaut là où il se produit.
///
/// `ApercuFx` refuse déjà une capture dont un **bloc** déborde (ADR 0042), mais une cellule de tableau
/// élide silencieusement : elle ne déborde pas, elle se coupe. D'où ce garde séparé.
@ExtendWith(ApplicationExtension.class)
class SyntheseLargeurColonnesTest {

    /// Largeur de la scène des deux aperçus (`CaptureSynthese`). Le garde ne vaut que s'il mesure à la
    /// taille où l'image est **réellement** produite.
    private static final double LARGEUR_APERCU = 1180;

    /// Marge sous laquelle on considère qu'une cellule est en danger. Une colonne qui tient au pixel
    /// près sur cette machine tronquera sur la prochaine : c'est exactement ce qui est arrivé.
    private static final double MARGE = 12;

    private ServiceSynthese service;
    private SyntheseController controleur;

    /// Les lignes les plus **larges** que l'écran sait produire, pas les plus représentatives : un garde
    /// de troncature se règle sur le pire cas.
    private static List<LigneSynthese> lignesLesPlusLarges() {
        SeuilsActivite seuils =
                new SeuilsActivite(23, 261, 1804, 307, ConfianceReferentiel.BONNE, "region:Corse", "printemps");
        return List.of(
                new LigneSynthese(
                        "Pipkuh",
                        "Pipistrelle de Kuhl",
                        "Chiroptères",
                        718,
                        359,
                        Optional.of(ClasseActivite.TRES_FORTE),
                        Optional.of(seuils),
                        true),
                new LigneSynthese(
                        "Antcho",
                        "Antaxie catalane",
                        "Orthoptères et cigales",
                        40,
                        40,
                        Optional.empty(),
                        Optional.empty(),
                        false),
                new LigneSynthese(
                        "Myodas",
                        "Murin des marais",
                        "Chiroptères",
                        5,
                        5,
                        Optional.of(ClasseActivite.FORTE),
                        Optional.of(
                                new SeuilsActivite(1, 2, 11, 16, ConfianceReferentiel.FAIBLE, "national", "printemps")),
                        true));
    }

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceSynthese.class);
        when(service.referentielDisponible()).thenReturn(true);
        when(service.milieuxDisponibles()).thenReturn(List.of("Foret"));
        when(service.contexte(anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ContexteActivite(
                        Optional.of(SaisonActivite.PRINTEMPS),
                        Optional.of("Provence-Alpes-Cote dAzur"),
                        Optional.empty()));
        when(service.pour(
                        anyLong(),
                        anyBoolean(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(lignesLesPlusLarges());

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            SyntheseViewModel viewModel() {
                return new SyntheseViewModel(service);
            }

            @Provides
            OuvrirSite ouvrirSite() {
                return mock(OuvrirSite.class);
            }

            @Provides
            OuvrirPassage ouvrirPassage() {
                return mock(OuvrirPassage.class);
            }

            @Provides
            fr.univ_amu.iut.validation.model.EspecesPrioritaires especesPrioritaires() {
                return () -> Set.of("Pipkuh");
            }
        });
        FXMLLoader loader = new FXMLLoader(SyntheseController.class.getResource("Synthese.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        stage.setScene(new Scene(vue, LARGEUR_APERCU, 700));
        stage.show();
    }

    @Test
    @DisplayName("#3074 : aucune cellule ne tronque son texte à la taille de l'aperçu")
    void aucune_cellule_ne_tronque(FxRobot robot) {
        robot.interact(() ->
                controleur.ouvrirSur(new ContextePassage(1L, 3, new ContexteSite("130246", "A1", "Étang de Berre"))));
        WaitForAsyncUtils.waitForFxEvents();

        Set<Labeled> cellules = robot.lookup(".table-cell").queryAllAs(Labeled.class);

        assertThat(cellules)
                .as("sans cellule rendue, ce test ne vérifie rien : le tableau n'a pas été peuplé")
                .isNotEmpty();

        for (Labeled cellule : cellules) {
            String contenu = cellule.getText();
            if (contenu == null || contenu.isBlank()) {
                continue; // les lignes vides du tableau, sans texte à couper
            }
            Text mesure = new Text(contenu);
            mesure.setFont(cellule.getFont());
            double largeurTexte = mesure.getLayoutBounds().getWidth();

            assertThat(cellule.getWidth())
                    .as(
                            "« %s » demande %.0f px, sa cellule en offre %.0f. Une ellipse est un aveu : "
                                    + "élargir la colonne dans Synthese.fxml, ou raccourcir le texte.",
                            contenu, largeurTexte + MARGE, cellule.getWidth())
                    .isGreaterThanOrEqualTo(largeurTexte + MARGE);
        }
    }

    @Test
    @DisplayName("#3074 : les colonnes tiennent dans la largeur de l'aperçu, sans barre de défilement")
    void les_colonnes_tiennent_dans_la_scene(FxRobot robot) {
        javafx.scene.control.TableView<?> table =
                robot.lookup("#tableSynthese").queryAs(javafx.scene.control.TableView.class);
        double total = 0;
        for (TableColumn<?, ?> colonne : table.getColumns()) {
            total += colonne.getWidth();
        }

        // 36 px de padding du conteneur, plus la bordure du tableau. Le compte était de 4 px avant
        // #3074 : c'est cette absence de marge qui a laissé la troncature apparaître en CI seulement.
        assertThat(total)
                .as(
                        "les colonnes totalisent %.0f px pour une scène de %.0f : le tableau défilerait",
                        total, LARGEUR_APERCU)
                .isLessThanOrEqualTo(LARGEUR_APERCU - 36 - 8);
    }
}
