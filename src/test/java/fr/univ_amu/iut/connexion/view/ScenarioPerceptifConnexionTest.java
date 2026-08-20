package fr.univ_amu.iut.connexion.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.connexion.viewmodel.ConnexionViewModel;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.Jugement;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.Seance;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Les scénarios qui **jouent** `S1-26` et `S1-27`, pour qu'un humain les tranche en regardant
/// (#3791, EPIC #3667).
///
/// ## Ce que ces tests prouvent, et ce qu'ils ne prouvent pas
///
/// Ils prouvent que le geste **a eu lieu** : la modale est à l'écran, la récupération est allée à son
/// terme. Ils ne prouvent **pas** que rien n'a sauté ni débordé - aucune assertion ne voit un contenu
/// qui se replace, elle voit un contenu correct une fois posé. C'est pourquoi ils portent
/// `jugement = HUMAIN` : le verdict revient à qui regarde le clip.
///
/// ## ⚠️ Pourquoi l'exécuteur asynchrone, alors que les tests emploient le synchrone
///
/// `S1-27` porte sur un **transitoire** : la zone de progression paraît seule, et le bandeau n'arrive
/// qu'à la fin. Avec l'exécuteur synchrone que `@ImplementedBy` donne par défaut aux tests, la
/// récupération se ferait sur le fil JavaFX : aucune image ne serait rendue pendant ce temps, et il
/// n'y aurait **rien à filmer**. Ces scénarios câblent donc l'exécuteur de la production.
///
/// C'est l'exact contraire du besoin des captures, qui exigent le synchrone pour ne pas photographier
/// un « Chargement… ». Ici, ce chargement **est** le sujet.
///
/// ⚠️ L'assertion de jeu n'est pas décorative. Un scénario qui n'assert rien du tout **échouerait en
/// silence** : robot mort, clip noir, et le contrôle de couverture du montage n'y verrait qu'une
/// fenêtre de moins - ce qui est parfaitement légitime pour un test sans interface. `HUMAIN` dit qui
/// rend le verdict, pas qu'on ne vérifie rien.
///
/// ## ⚠️ Pourquoi la modale n'est PAS ouverte dans le `@Start`
///
/// [ConnexionModaleViewTest] la montre dans son `@Start`, ce qui est juste pour asserter un état.
/// Ici ce serait fatal : les repères de la séance filmée sont posés **autour du test**, si bien que
/// l'ouverture tomberait avant le premier et n'apparaîtrait sur aucune image. Le clip raterait
/// exactement ce qu'il faut juger.
///
/// L'ouverture reproduit donc `NavigationConnexion.ouvrir()`, du chargement FXML jusqu'à
/// [Habillage#scene(Parent)] - qui n'est pas un détail : c'est ce qui embarque la typographie, et
/// donc ce qui fait que le clip montre l'application telle qu'elle est livrée.
@ExtendWith(ApplicationExtension.class)
class ScenarioPerceptifConnexionTest {

    /// ⚠️ Une LATENCE simulée, et non un temps d'arrêt. La récupération doit prendre du temps, sans
    /// quoi la zone de progression paraîtrait et disparaîtrait entre deux images : il n'y aurait
    /// rien à voir. Elle vit ici, et non dans [Respiration], parce qu'elle décrit ce que
    /// l'application FAIT et non ce que le film montre.
    /// La fenêtre de l'application, plus PETITE que l'écran du banc (1280x900).
    ///
    /// ⚠️ Elle valait d'abord 1280x860, soit la largeur exacte de l'écran. Avec ses décorations, la
    /// fenêtre débordait alors, et la modale ouverte par-dessus atterrissait en (0,0), barre de
    /// titre hors champ - sur le clip PUBLIÉ, alors qu'elle se centrait en local.
    ///
    /// Ce qui a tranché : dans la MÊME publication, la modale de `S1-37` était parfaitement centrée.
    /// Même machine, même gestionnaire de fenêtres, deux résultats - donc la cause n'était ni l'un
    /// ni l'autre. Son scénario donne 1100x720 à son hôte, et laisse de la marge autour.
    private static final int LARGEUR = 1100;

    private static final int HAUTEUR = 720;

    private static final long LATENCE_RECUPERATION_MS = 1_500;

    private Injector injector;

    private Stage fenetre;

    @Start
    void start(Stage stage) throws IOException {
        Path workspace = Files.createTempDirectory("vc-scenario-connexion");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        StockageConnexion stockage = new StockageConnexion(new Workspace(workspace), Horloge.systeme());
        ClientVigieChiro client = mock(ClientVigieChiro.class);
        // La récupération PREND DU TEMPS, sans quoi il n'y aurait rien à voir : la zone de
        // progression paraîtrait et disparaîtrait entre deux images. Hors séance filmée, aucune
        // attente n'est payée.
        when(client.moi()).thenAnswer(appel -> {
            if (Seance.filmee()) {
                Thread.sleep(LATENCE_RECUPERATION_MS);
            }
            return ReponseApi.succes(new ProfilVigieChiro("u-scenario", "chiro", "observateur"));
        });

        // ⚠️ L'application RÉELLE, et non une scène hôte vide. La version précédente montait un
        // `StackPane` sans contenu ni dimensions, au motif que « ce qu'il faut voir est
        // l'ouverture ». Le clip publié montrait donc un écran noir, un rectangle blanc de la
        // taille d'une vignette, puis une modale posée dans un coin : rien qui permette de juger
        // « la saisie est en place, rien ne se replace ». Un scénario perceptif se juge à l'oeil,
        // et un oeil a besoin d'un contexte.
        //
        // `Modules.override` est le chemin que `RacineInjecteur` documente lui-même, et que les
        // outils de capture empruntent déjà : on garde le câblage du produit, on ne remplace que ce
        // que ce scénario doit tenir - le client, l'ouvreur de lien, et l'exécuteur.
        injector =
                Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        // ⚠️ L'exécuteur ASYNCHRONE, celui de la production, et non le synchrone que
                        // `@ImplementedBy` donne par défaut aux tests. Le transitoire de S1-27
                        // n'existe que hors du fil JavaFX : en synchrone, la récupération bloque le
                        // fil, aucune image n'est rendue pendant ce temps, et il n'y aurait rien à
                        // filmer.
                        //
                        // C'est l'exact contraire du besoin des captures (#3242), qui exigent le
                        // synchrone pour ne pas photographier un « Chargement… ». Ici, ce chargement
                        // EST le sujet.
                        bind(ExecuteurTache.class)
                                .to(ExecuteurTacheAsynchrone.class)
                                .in(Singleton.class);
                    }

                    @Provides
                    ConnexionViewModel viewModel() {
                        return new ConnexionViewModel(stockage, client, Set.of(), Optional.empty());
                    }

                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        return lien -> {
                            // Le scénario n'ouvre aucun navigateur : rien à voir sur le film, et
                            // rien à lancer sur la machine qui filme.
                        };
                    }
                }));
        new MigrationSchema(injector.getInstance(SourceDeDonnees.class)).migrer();

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        // ⚠️ La taille du produit, et `Habillage.scene` : c'est lui qui embarque la typographie, donc
        // ce qui fait que le clip montre l'application telle qu'elle est livrée.
        stage.setScene(Habillage.scene(racine, LARGEUR, HAUTEUR));
        stage.show();
        fenetre = stage;
    }

    @Test
    @CasDeRecette(value = "S1-26", jugement = Jugement.HUMAIN)
    @DisplayName("S1-26 · la modale de connexion s'ouvre : à regarder, rien ne doit se replacer après coup")
    void la_modale_de_connexion_s_ouvre(FxRobot robot) {
        Respiration.avantLeGeste(robot);

        robot.interact(this::ouvrirLaModaleCommeLApplication);
        WaitForAsyncUtils.waitForFxEvents();

        // Le moment que ce cas existe pour montrer : la modale est posée, et c'est là qu'on juge
        // si quelque chose s'est replacé après coup.
        Respiration.surLeMomentCle(robot);

        assertThat(robot.lookup("#champToken").tryQuery())
                .as("le geste a-t-il seulement eu lieu ? Sans cette question, un robot mort rendrait"
                        + " un clip noir que personne ne signalerait.")
                .isPresent();
    }

    @Test
    @CasDeRecette(value = "S1-27", jugement = Jugement.HUMAIN)
    @DisplayName("S1-27 · pendant la récupération : à regarder, rien ne doit sortir du cadre avant le bandeau")
    void la_recuperation_ne_pousse_rien_hors_du_cadre(FxRobot robot) throws TimeoutException {
        robot.interact(this::ouvrirLaModaleCommeLApplication);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.avantLeGeste(robot);

        robot.clickOn("#champToken").write("jeton-de-scenario");
        robot.clickOn("Se connecter");

        // C'est ici que se joue le cas : la zone de progression paraît d'abord, seule, et le bandeau
        // n'arrive qu'à la fin. Attendre le BANDEAU, et non la zone, garantit que le film contient
        // les deux moments - donc le passage de l'un à l'autre, qui est ce qu'on juge.
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> robot.lookup("#bandeauStatut").queryAs(Label.class).isVisible());
        // Le moment que ce cas existe pour montrer : le bandeau vient de remplacer la zone de
        // progression, et c'est ce passage qu'on juge.
        Respiration.surLeMomentCle(robot);

        assertThat(robot.lookup("#bandeauStatut").queryAs(Label.class).getText())
                .as("la récupération est-elle seulement allée à son terme ? Sans cette question, un"
                        + " scénario qui n'aurait rien déclenché rendrait un clip immobile.")
                .isNotBlank();
    }

    // ----------------------------------------------------------------------------------------

    /// Ouvre la modale par le CHEMIN RÉEL du produit : l'entrée de menu, avec la fenêtre de
    /// l'application pour propriétaire.
    ///
    /// ⚠️ La version précédente recopiait le corps de `NavigationConnexion.ouvrir()` dans ce fichier.
    /// Une copie ne suit pas l'original : quand `ouvrir()` a reçu son propriétaire (#4073), la copie
    /// ne l'a pas reçu, et le clip a continué de montrer une modale posée n'importe où. Un scénario
    /// qui rejoue le geste au lieu de l'appeler ne joue pas ce geste-là.
    private void ouvrirLaModaleCommeLApplication() {
        injector.getInstance(ActionConnexion.class).executer(fenetre);
    }
}
