package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.viewmodel.SiteEditViewModel;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// La **modale de site** (#1431) : déclarer un site, le modifier, et la validation en direct.
///
/// Ces deux gestes étaient portés par des `Dialog<T>` bâtis à la main dans `MesSitesController` et
/// `SiteDetailController`. Ils se terminaient par un `showAndWait`, donc **aucun** n'était jouable dans
/// un test - y compris **déclarer un site**, qui est l'entrée du produit : sans site, on n'importe
/// aucune nuit.
///
/// Alignés sur le patron **modale** déjà en service ailleurs (`ModalePoint`, `RattachementModale`,
/// `ReconstructionModale`), ils deviennent une vraie vue : le geste se joue, et la validation - qui
/// vivait dans la vue - devient un binding observable, vérifiable sans IHM.
@ExtendWith(ApplicationExtension.class)
class ModaleSiteViewTest {

    private static final String ID_USER = "u-1";

    private ServiceSites service;
    private ModaleSiteController controleur;

    /// Rafraîchissements demandés par la modale après un enregistrement réussi.
    private int rafraichissements;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceSites.class);
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            SiteEditViewModel viewModel() {
                return new SiteEditViewModel(service, ID_USER);
            }
        });
        FXMLLoader loader = new FXMLLoader(ModaleSiteController.class.getResource("ModaleSite.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        stage.setScene(new Scene(vue));
        stage.show();
    }

    private void enCreation(FxRobot robot) {
        robot.interact(() -> controleur.demarrerCreation(() -> rafraichissements++));
    }

    private void enEdition(FxRobot robot, Site site) {
        robot.interact(() -> controleur.demarrerEdition(site, () -> rafraichissements++));
    }

    private static Site site() {
        return new Site(7L, "640380", "Étang de la Tuilière", Protocole.STANDARD, "Aix", "2026-01-01", ID_USER);
    }

    private Button valider(FxRobot robot) {
        return robot.lookup("#boutonValider").queryAs(Button.class);
    }

    @Test
    @DisplayName("#1431 : déclarer un site : le carré saisi part en base, et l'appelant est rafraîchi")
    void declaration_cree_le_site(FxRobot robot) {
        enCreation(robot);
        when(service.creerSite(anyString(), any(), any(), any(), anyString())).thenReturn(site());

        robot.interact(() -> {
            robot.lookup("#champCarre").queryAs(TextField.class).setText("640380");
            robot.lookup("#champNom").queryAs(TextField.class).setText("Étang de la Tuilière");
        });
        robot.interact(() -> valider(robot).fire());

        verify(service).creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        assertThat(rafraichissements)
                .as("la liste des sites doit montrer le nouveau site sans qu'on la recharge à la main")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("#790 : « Créer » reste fermé tant que le carré n'a pas ses six chiffres")
    void bouton_ferme_tant_que_le_carre_est_incomplet(FxRobot robot) {
        enCreation(robot);

        // On EMPÊCHE, au lieu d'avertir après coup : c'est tout l'objet de la validation en direct.
        assertThat(valider(robot).isDisabled())
                .as("formulaire vierge : rien à créer")
                .isTrue();

        robot.interact(
                () -> robot.lookup("#champCarre").queryAs(TextField.class).setText("6403"));
        assertThat(valider(robot).isDisabled())
                .as("quatre chiffres : toujours pas un carré")
                .isTrue();

        robot.interact(
                () -> robot.lookup("#champCarre").queryAs(TextField.class).setText("640380"));
        assertThat(valider(robot).isDisabled())
                .as("six chiffres : le geste s'ouvre")
                .isFalse();
    }

    @Test
    @DisplayName("#1431 : carré déjà déclaré : le refus s'affiche DANS la modale, et la saisie est conservée")
    void refus_metier_reste_dans_la_modale(FxRobot robot) {
        enCreation(robot);
        when(service.creerSite(anyString(), any(), any(), any(), anyString()))
                .thenThrow(new RegleMetierException("Le carré 640380 est déjà déclaré pour cet utilisateur."));

        robot.interact(
                () -> robot.lookup("#champCarre").queryAs(TextField.class).setText("640380"));
        robot.interact(() -> valider(robot).fire());

        // L'alerte d'après coup obligeait à tout ressaisir. Ici le motif s'affiche à côté du champ fautif,
        // la modale reste ouverte, et la saisie est intacte.
        Label erreur = robot.lookup("#lblRetour").queryAs(Label.class);
        assertThat(erreur.getText()).contains("déjà déclaré");
        assertThat(erreur.isVisible()).isTrue();
        assertThat(robot.lookup("#champCarre").queryAs(TextField.class).getText())
                .as("la saisie de l'utilisateur ne doit pas être perdue par un refus")
                .isEqualTo("640380");
        assertThat(rafraichissements).isZero();
    }

    @Test
    @DisplayName("#1431 : modifier un site : les champs sont pré-remplis, et l'enregistrement porte l'id")
    void edition_pre_remplit_et_enregistre(FxRobot robot) {
        enEdition(robot, site());
        when(service.modifierSite(anyLong(), anyString(), any(), any(), any())).thenReturn(site());

        assertThat(robot.lookup("#champCarre").queryAs(TextField.class).getText())
                .isEqualTo("640380");
        assertThat(robot.lookup("#champNom").queryAs(TextField.class).getText()).isEqualTo("Étang de la Tuilière");
        assertThat(valider(robot).getText()).isEqualTo("Enregistrer");

        robot.interact(() -> robot.lookup("#champNom").queryAs(TextField.class).setText("Nouveau nom"));
        robot.interact(() -> valider(robot).fire());

        verify(service).modifierSite(7L, "640380", "Nouveau nom", Protocole.STANDARD, "Aix");
        assertThat(rafraichissements).isEqualTo(1);
    }

    @Test
    @DisplayName("#1431 : « Annuler » ne crée rien")
    void annuler_ne_cree_rien(FxRobot robot) {
        enCreation(robot);

        robot.interact(
                () -> robot.lookup("#champCarre").queryAs(TextField.class).setText("640380"));
        robot.interact(
                () -> robot.lookup(".bouton-secondaire").queryAs(Button.class).fire());

        verify(service, never()).creerSite(anyString(), any(), any(), any(), anyString());
        assertThat(rafraichissements).isZero();
    }
}
