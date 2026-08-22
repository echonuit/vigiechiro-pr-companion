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
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.view.InfobulleDeBlocage;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.viewmodel.SiteEditViewModel;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

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
        // Aucune correspondance Vigie-Chiro : le mock rend des collections vides, donc tout site y est
        // ABSENT et la modale n'annonce aucune portée. C'est le cas nominal de cette classe de tests ;
        // les trois états de la portée se vérifient sans IHM (SiteEditPorteeTest).
        LienVigieChiroDao liens = mock(LienVigieChiroDao.class);
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            SiteEditViewModel viewModel() {
                return new SiteEditViewModel(service, liens, ID_USER, Optional.empty(), Optional.empty());
            }
        });
        FXMLLoader loader = new FXMLLoader(ModaleSiteController.class.getResource("ModaleSite.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        // ⚠️ `Habillage`, et non `new Scene` : les trois cas de cette classe sont FILMÉS, et une scène
        // montée sans habillage porte la police de la MACHINE (#3773, #4149).
        stage.setScene(Habillage.scene(vue));
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
    @CasDeRecette(value = "S1-13", portee = Portee.A_L_ECRAN)
    @DisplayName("#790 : « Créer » reste fermé tant que le carré n'a pas ses six chiffres")
    void bouton_ferme_tant_que_le_carre_est_incomplet(FxRobot robot) {
        enCreation(robot);

        // On EMPÊCHE, au lieu d'avertir après coup : c'est tout l'objet de la validation en direct.
        assertThat(valider(robot).isDisabled())
                .as("formulaire vierge : rien à créer")
                .isTrue();
        Respiration.avantLeGeste(robot);

        // ⚠️ Les chiffres se TAPENT, un par un. Avec `setText`, le champ passait de vide à « 6403 »
        // puis à « 640380 » d'un coup : le clip montrait un bouton qui change d'état sans qu'on voie
        // ce qui le fait changer, et c'est justement la validation EN DIRECT que ce cas fait juger.
        TextField carre = robot.lookup("#champCarre").queryAs(TextField.class);
        robot.clickOn(carre).write("6403");
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(valider(robot).isDisabled())
                .as("quatre chiffres : toujours pas un carré")
                .isTrue();
        Respiration.leTempsDeLire(robot);

        robot.write("80");
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);

        assertThat(carre.getText())
                .as("les six chiffres sont bien dans le champ")
                .isEqualTo("640380");
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
    @CasDeRecette(value = "S1-23", portee = Portee.A_L_ECRAN)
    @DisplayName("#1431 : modifier un site : les champs sont pré-remplis, et l'enregistrement porte l'id")
    void edition_pre_remplit_et_enregistre(FxRobot robot) {
        enEdition(robot, site());
        when(service.modifierSite(anyLong(), anyString(), any(), any(), any())).thenReturn(site());

        assertThat(robot.lookup("#champCarre").queryAs(TextField.class).getText())
                .isEqualTo("640380");
        assertThat(robot.lookup("#champNom").queryAs(TextField.class).getText()).isEqualTo("Étang de la Tuilière");
        assertThat(valider(robot).getText()).isEqualTo("Enregistrer");

        Respiration.leTempsDeLire(robot);

        TextField nom = robot.lookup("#champNom").queryAs(TextField.class);
        robot.interact(nom::clear);
        robot.clickOn(nom).write("Nouveau nom");
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.avantLeGeste(robot);

        robot.clickOn(valider(robot));
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);

        verify(service).modifierSite(7L, "640380", "Nouveau nom", Protocole.STANDARD, "Aix");
        assertThat(rafraichissements).isEqualTo(1);
    }

    @Test
    @CasDeRecette(value = "S1-25", portee = Portee.A_L_ECRAN)
    @DisplayName("#1431 : « Annuler » ne crée rien")
    void annuler_ne_cree_rien(FxRobot robot) {
        enCreation(robot);

        // La saisie se voit, sinon « Annuler » n'annule rien de visible.
        robot.clickOn(robot.lookup("#champCarre").queryAs(TextField.class)).write("640380");
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.avantLeGeste(robot);

        robot.clickOn(robot.lookup(".bouton-secondaire").queryAs(Button.class));
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);

        verify(service, never()).creerSite(anyString(), any(), any(), any(), anyString());
        assertThat(rafraichissements).isZero();
    }

    @Test
    @DisplayName("#790 : le champ carré rougit seulement quand il est SAISI et incomplet")
    void le_champ_carre_rougit_quand_il_est_saisi_et_incomplet(FxRobot robot) {
        // Trou relevé par PIT à la clôture de #3458 : trois mutants du binding survivaient, faute d'un
        // test qui regarde la classe CSS ici. Le mécanisme est éprouvé au socle
        // (ValidationFormulaireTest) et sur la modale point ; c'est son câblage sur CE champ qui manquait.
        enCreation(robot);
        TextField carre = robot.lookup("#champCarre").queryAs(TextField.class);

        assertThat(carre.getStyleClass())
                .as("formulaire vierge : rien n'est encore fautif, donc rien ne rougit")
                .doesNotContain("champ-invalide");

        robot.interact(() -> carre.setText("6403"));
        assertThat(carre.getStyleClass())
                .as("saisi mais incomplet : c'est là qu'on le signale")
                .contains("champ-invalide");

        robot.interact(() -> carre.setText("640380"));
        assertThat(carre.getStyleClass()).as("six chiffres : le rouge s'efface").doesNotContain("champ-invalide");
    }

    @Test
    @DisplayName("#3458 : sans la vérification installée, la modale n'affiche pas de bouton mort")
    void sans_recherche_installee_pas_de_bouton_mort(FxRobot robot) {
        // Cette classe monte le ViewModel avec un `Optional.empty()` : c'est l'injecteur partiel (capture
        // d'écran, feature de connexion éteinte), où la plateforme n'est pas joignable par construction.
        enCreation(robot);
        StackPane enveloppe = robot.lookup("#enveloppeVerifierCarre").queryAs(StackPane.class);

        assertThat(enveloppe.isVisible())
                .as("un bouton qui ne peut rien faire vaut moins que pas de bouton du tout")
                .isFalse();
        assertThat(enveloppe.isManaged())
                .as("et il ne doit pas réserver sa place dans la ligne du carré")
                .isFalse();
    }

    @Test
    @DisplayName("#1970 : le grisage de « Créer » dit POURQUOI, et le motif suit la saisie")
    void le_grisage_dit_pourquoi(FxRobot robot) {
        // Avant #1970, le motif vivait dans un message que le ViewModel n'émettait que si l'on cliquait
        // - or le bouton est grisé sur EXACTEMENT la condition commentée : personne ne l'a jamais lu.
        enCreation(robot);
        StackPane enveloppe = robot.lookup("#enveloppeValider").queryAs(StackPane.class);

        assertThat(valider(robot).isDisabled()).isTrue();
        assertThat(InfobulleDeBlocage.texteDe(enveloppe))
                .as("un bouton gris sans explication est lui-même un défaut")
                .contains("6 chiffres");

        robot.interact(
                () -> robot.lookup("#champCarre").queryAs(TextField.class).setText("640380"));

        assertThat(valider(robot).isDisabled()).isFalse();
        assertThat(InfobulleDeBlocage.texteDe(enveloppe))
                .as("le motif disparaît avec le blocage : l'infobulle dit alors ce que fait l'action")
                .doesNotContain("6 chiffres");
    }
}
