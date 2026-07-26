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
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private ServiceActivite service;
    private ActiviteController controleur;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceActivite.class);
        when(service.plageNuit(anyLong())).thenReturn(Optional.empty());
        OuvrirSite ouvrirSite = mock(OuvrirSite.class);
        OuvrirPassage ouvrirPassage = mock(OuvrirPassage.class);
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
