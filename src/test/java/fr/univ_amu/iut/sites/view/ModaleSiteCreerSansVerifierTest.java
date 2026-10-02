package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.viewmodel.EtatConnexion;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.connexion.viewmodel.RefletDuJeton;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.sites.model.PresenceDuCarre;
import fr.univ_amu.iut.sites.model.RapatriementCarre;
import fr.univ_amu.iut.sites.model.RechercheCarreExistant;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.viewmodel.SiteEditViewModel;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// **« Créer » vérifie ce qui ne l'a pas été** (#5607).
///
/// La vérification n'avait lieu que sur un clic de « Vérifier sur Vigie-Chiro ». Qui saisissait un carré
/// et cliquait directement « Créer » n'apprenait rien avant le dépôt, et pouvait même créer le doublon
/// local d'un carré déjà en Point Fixe, que le dépôt ne sait pas rattacher. Chaque cas compte les appels
/// au portail, parce que c'est ce nombre qui dit si la vérification a eu lieu.
@ExtendWith(ApplicationExtension.class)
class ModaleSiteCreerSansVerifierTest {

    private static final String CARRE = "202013";
    private static final long ATTENTE_MS = 10_000L;

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);
    private final ServiceSites service = mock(ServiceSites.class);
    private final RapatriementCarre rapatriement = mock(RapatriementCarre.class);
    private final AtomicReference<RetourOperation> annonce = new AtomicReference<>();
    private final AtomicBoolean cree = new AtomicBoolean(false);

    private ModaleSiteController controleur;

    @Start
    void start(Stage stage) throws Exception {
        LienVigieChiroDao liens = mock(LienVigieChiroDao.class);
        RefletDuJeton reflet = new RefletDuJeton(() -> Optional.of("jeton-de-test"), Runnable::run);
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                OptionalBinder.newOptionalBinder(binder(), EtatConnexion.class)
                        .setBinding()
                        .toInstance(reflet);
            }

            @Provides
            SiteEditViewModel viewModel() {
                return new SiteEditViewModel(
                        service,
                        liens,
                        "u-1",
                        Optional.of(new RechercheCarreExistant(client)),
                        Optional.of(rapatriement));
            }
        });
        FXMLLoader loader = new FXMLLoader(ModaleSiteController.class.getResource("ModaleSite.fxml"));
        loader.setControllerFactory(DiagnosticGuice.pour(injector));
        Parent vue = loader.load();
        controleur = loader.getController();
        stage.setScene(Habillage.scene(vue));
        stage.show();
    }

    @Test
    @DisplayName(
            "#5607 : « Créer » sans vérification, carré absent : le portail est interrogé, le site créé, et le verdict annoncé")
    void creer_un_carre_absent_le_verifie_puis_le_cree(FxRobot robot) throws TimeoutException {
        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.succes(List.of()));
        enCreation(robot);
        saisirCarre(robot, CARRE);

        creer(robot);
        Attente.queSurLeFil(cree::get, "la modale a conclu", ATTENTE_MS);

        verify(client, times(1)).chercherCarre(CARRE);
        verify(service).creerSite(anyString(), any(), any(), any(), anyString());
        assertThat(annonce.get())
                .as("le verdict part au bandeau de « Mes sites »")
                .isNotNull();
        assertThat(annonce.get().texte())
                .as("lue dans « Mes sites », l'annonce nomme le carré et ne propose plus de le déclarer")
                .startsWith("Carré " + CARRE + " déclaré")
                .contains(PresenceDuCarre.GESTE_DU_PORTAIL)
                .doesNotContain("déclarer ici");
    }

    @Test
    @DisplayName(
            "#5607 : « Créer » sans vérification, carré déjà en Point Fixe : rien n'est créé, et « Récupérer » est proposé")
    void creer_un_carre_deja_en_point_fixe_ne_cree_rien(FxRobot robot) throws TimeoutException {
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(
                        List.of(new SiteVigieChiro("pf", "Vigiechiro - Point Fixe-" + CARRE, false))));
        enCreation(robot);
        saisirCarre(robot, CARRE);

        creer(robot);
        Attente.queSurLeFil(() -> ligneRecuperer(robot).isVisible(), "la modale a conclu", ATTENTE_MS);

        verify(client, times(1)).chercherCarre(CARRE);
        verify(service, never()).creerSite(anyString(), any(), any(), any(), anyString());
        assertThat(cree)
                .as("la modale reste ouverte : le geste juste est à côté")
                .isFalse();
    }

    @Test
    @DisplayName("#5607 : « Créer » après une vérification ne réinterroge pas le portail")
    void creer_apres_verification_n_interroge_pas_de_nouveau(FxRobot robot) throws TimeoutException {
        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.succes(List.of()));
        enCreation(robot);
        saisirCarre(robot, CARRE);
        robot.interact(
                () -> robot.lookup("#btnVerifierCarre").queryAs(Button.class).fire());
        Attente.queSurLeFil(
                () -> !controleur
                        .viewModel()
                        .carre()
                        .retourProperty()
                        .get()
                        .texte()
                        .isEmpty(),
                "la modale a conclu",
                ATTENTE_MS);

        creer(robot);
        Attente.queSurLeFil(cree::get, "la modale a conclu", ATTENTE_MS);

        verify(client, times(1)).chercherCarre(CARRE);
        verify(service).creerSite(anyString(), any(), any(), any(), anyString());
    }

    @Test
    @DisplayName(
            "#5607 : « Créer » sans vérification, portail injoignable : le site est créé, et l'annonce dit qu'il n'a pas été vérifié")
    void creer_hors_d_atteinte_cree_en_le_disant(FxRobot robot) throws TimeoutException {
        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.injoignable("délai dépassé"));
        enCreation(robot);
        saisirCarre(robot, CARRE);

        creer(robot);
        Attente.queSurLeFil(cree::get, "la modale a conclu", ATTENTE_MS);

        verify(service).creerSite(anyString(), any(), any(), any(), anyString());
        assertThat(annonce.get()).isNotNull();
        assertThat(annonce.get().texte())
                .startsWith("Carré " + CARRE + " déclaré")
                .contains("PAS été vérifié");
    }

    private void enCreation(FxRobot robot) {
        robot.interact(() -> controleur.demarrerCreation(() -> cree.set(true), rapatrie -> {}, annonce::set));
    }

    private void saisirCarre(FxRobot robot, String carre) {
        robot.interact(
                () -> robot.lookup("#champCarre").queryAs(TextField.class).setText(carre));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void creer(FxRobot robot) {
        robot.interact(
                () -> robot.lookup("#boutonValider").queryAs(Button.class).fire());
    }

    /// La **ligne** qui porte « Récupérer ce carré » : c'est elle que la modale montre ou cache. Le bouton
    /// seul se dit visible même sous un parent caché, et une attente sur lui serait vraie d'avance.
    private javafx.scene.Node ligneRecuperer(FxRobot robot) {
        return robot.lookup("#ligneRecupererCarre").query();
    }
}
