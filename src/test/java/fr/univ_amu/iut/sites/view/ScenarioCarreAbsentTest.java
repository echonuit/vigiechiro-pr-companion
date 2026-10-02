package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CadreVisible;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.recette.Seance;
import fr.univ_amu.iut.sites.model.PresenceDuCarre;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// **Un carré que le dépôt n'acceptera pas encore se dit à la déclaration** (#5607), sur l'application
/// montée de bout en bout : « Mes sites », la modale, le portail bouchonné, la base.
///
/// Les cas de `S1` sur la vérification d'un carré vivent dans [ScenarioModaleCarreTest] ; ceux-ci en
/// sont sortis parce que la classe atteignait le plafond de taille du portail qualité.
@ExtendWith({ApplicationExtension.class, SansExceptionAvalee.class})
class ScenarioCarreAbsentTest {

    private static final String ID_USER = "u-carre-absent";
    private static final String CARRE_ABSENT = "202013";
    private static final String CARRE_ROUTIER = "640380";
    private static final long VERIFICATION_MS = 700;
    private static final long DELAI_MS = 10_000L;

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);

    @Start
    void start(Stage stage) throws IOException {
        Injector injector = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .remplacer(liaison -> liaison.bind(ClientVigieChiro.class).toInstance(client))
                .semer(inj -> new UtilisateurDao(inj.getInstance(SourceDeDonnees.class))
                        .insert(new Utilisateur(ID_USER, "Observateur")))
                .connecte(ID_USER, "chiro", "observateur")
                .ouvrir(inj -> inj.getInstance(NavigationSites.class).ouvrirAccueil())
                .montrer(stage);
        StockageConnexion stockage = injector.getInstance(StockageConnexion.class);
        when(client.estConnecte()).thenAnswer(appel -> stockage.estConnecte());
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    /// **« Créer » vérifie ce qui ne l'a pas été**, et le verdict survit à la fermeture de la modale :
    /// c'est le bandeau de « Mes sites » qui le porte, après le rechargement que la création déclenche.
    /// Sans cela, Samuel ne l'apprenait qu'au dépôt, sa nuit déjà transformée.
    @Test
    @CasDeRecette(value = "S1-41", portee = Portee.A_L_ECRAN)
    @DisplayName(
            "S1-41 · « Créer » sans vérifier un carré absent : le bandeau de « Mes sites » dit le geste du portail")
    void creer_sans_verifier_un_carre_absent_le_dit(FxRobot robot) throws TimeoutException {
        when(client.chercherCarre(CARRE_ABSENT)).thenAnswer(appel -> {
            attendreCommeLeReseau();
            return ReponseApi.succes(List.of());
        });
        ouvrirLaDeclaration(robot);
        saisir(robot, CARRE_ABSENT);

        GesteVisible.cliquer(robot, "#boutonValider");
        Attente.queSurLeFil(
                () -> robot.lookup("#lblRetour")
                        .tryQueryAs(Label.class)
                        .filter(libelle -> libelle.getText().contains("Point Fixe sur le portail"))
                        .isPresent(),
                "le bandeau de « Mes sites » porte le verdict du carré créé",
                DELAI_MS);
        Respiration.surLeMomentCle(robot);

        assertThat(robot.lookup("#lblRetour").queryAs(Label.class).getText())
                .startsWith("Carré " + CARRE_ABSENT + " déclaré.")
                .contains(PresenceDuCarre.GESTE_DU_PORTAIL);
    }

    /// Un carré que la plateforme porte en Routier seulement n'est pas récupérable : le rapatriement ne
    /// rattache que le Point Fixe. La vérification l'annonçait pourtant « existe déjà, récupérez-le », et
    /// le clic finissait sur « rien n'a été récupéré ».
    @Test
    @CasDeRecette(value = "S1-42", portee = Portee.A_L_ECRAN)
    @DisplayName(
            "S1-42 · un carré présent seulement en Routier : l'encart dit le geste du portail, sans proposer de récupérer")
    void le_carre_en_routier_dit_le_geste_du_portail(FxRobot robot) throws TimeoutException {
        when(client.chercherCarre(CARRE_ROUTIER)).thenAnswer(appel -> {
            attendreCommeLeReseau();
            return ReponseApi.succes(List.of(new SiteVigieChiro("rt", "Vigiechiro - Routier-" + CARRE_ROUTIER, false)));
        });
        ouvrirLaDeclaration(robot);
        saisir(robot, CARRE_ROUTIER);

        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, "#btnVerifierCarre");
        Attente.queSurLeFil(() -> encart(robot).isVisible(), "l'encart de vérification du carré paraît", DELAI_MS);
        CadreVisible.amener(encart(robot), robot);
        Respiration.surLeMomentCle(robot);

        assertThat(encart(robot).getText())
                .contains("pas en Point Fixe")
                .contains("Vigiechiro - Routier-" + CARRE_ROUTIER)
                .contains(PresenceDuCarre.GESTE_DU_PORTAIL);
        assertThat(encart(robot).getStyleClass()).contains("encart-avertissement");
        // La visibilité se pose sur la LIGNE qui porte le bouton : le bouton seul se dit visible même
        // sous un parent caché.
        assertThat(robot.lookup("#ligneRecupererCarre").query().isVisible())
                .as("rien à récupérer en Point Fixe : le bouton ne s'offre pas")
                .isFalse();
    }

    /// Ouvre la déclaration **par le bouton de l'écran**, et attend que la modale soit là.
    private void ouvrirLaDeclaration(FxRobot robot) throws TimeoutException {
        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, "+ Nouveau site");
        Attente.queSurLeFil(
                () -> robot.lookup("#champCarre").tryQuery().isPresent(),
                "la modale de déclaration s'ouvre, reconnue à son champ de carré",
                DELAI_MS);
        Respiration.apresLeGeste(robot);
    }

    /// Tape le numéro, chiffre à chiffre, dans le champ qu'on vient de cliquer.
    private void saisir(FxRobot robot, String carre) {
        GesteVisible.cliquer(robot, "#champCarre");
        robot.write(carre);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static Label encart(FxRobot robot) {
        return robot.lookup("#messageCarreExistant").queryAs(Label.class);
    }

    /// Le temps qu'un appel réseau prend, **seulement en séance filmée** : un cadencement pour le film,
    /// qui n'observe rien, et n'est donc pas une attente sur condition.
    private static void attendreCommeLeReseau() throws InterruptedException {
        if (Seance.filmee()) {
            Thread.sleep(VERIFICATION_MS);
        }
    }
}
