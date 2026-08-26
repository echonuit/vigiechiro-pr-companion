package fr.univ_amu.iut.connexion.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.connexion.viewmodel.ConnexionViewModel;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Jugement;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.recette.Seance;
import fr.univ_amu.iut.recette.film.EnregistreurDeFilm;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
/// ## Pourquoi l'exécuteur asynchrone, alors que les tests emploient le synchrone
///
/// `S1-27` porte sur un **transitoire** : la zone de progression paraît seule, et le bandeau n'arrive
/// qu'à la fin. Avec l'exécuteur synchrone que `@ImplementedBy` donne par défaut aux tests, la
/// récupération se ferait sur le fil JavaFX : aucune image ne serait rendue pendant ce temps, et il
/// n'y aurait **rien à filmer**. Ces scénarios câblent donc l'exécuteur de la production.
///
/// C'est l'exact contraire du besoin des captures, qui exigent le synchrone pour ne pas photographier
/// un « Chargement… ». Ici, ce chargement **est** le sujet.
///
/// L'assertion de jeu n'est pas décorative. Un scénario qui n'assert rien du tout **échouerait en
/// silence** : robot mort, clip noir, et le contrôle de couverture du montage n'y verrait qu'une
/// fenêtre de moins - ce qui est parfaitement légitime pour un test sans interface. `HUMAIN` dit qui
/// rend le verdict, pas qu'on ne vérifie rien.
///
/// ## Pourquoi la modale n'est PAS ouverte dans le `@Start`
///
/// [ConnexionModaleViewTest] la montre dans son `@Start`, ce qui est juste pour asserter un état.
/// Ici ce serait fatal : les repères de la séance filmée sont posés **autour du test**, si bien que
/// l'ouverture tomberait avant le premier et n'apparaîtrait sur aucune image. Le clip raterait
/// exactement ce qu'il faut juger.
///
/// L'ouverture reproduit donc `NavigationConnexion.ouvrir()`, du chargement FXML jusqu'à
/// [Habillage#scene(Parent)] - qui n'est pas un détail : c'est ce qui embarque la typographie, et
/// donc ce qui fait que le clip montre l'application telle qu'elle est livrée.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioPerceptifConnexionTest {

    /// Une LATENCE simulée, et non un temps d'arrêt. La récupération doit prendre du temps, sans
    /// quoi la zone de progression paraîtrait et disparaîtrait entre deux images : il n'y aurait
    /// rien à voir. Elle vit ici, et non dans [Respiration], parce qu'elle décrit ce que
    /// l'application FAIT et non ce que le film montre.
    /// La fenêtre de l'application, plus PETITE que l'écran du banc (1280x900).
    ///
    /// Elle valait d'abord 1280x860, soit la largeur exacte de l'écran. Avec ses décorations, la
    /// fenêtre débordait alors, et la modale ouverte par-dessus atterrissait en (0,0), barre de
    /// titre hors champ - sur le clip PUBLIÉ, alors qu'elle se centrait en local.
    ///
    /// Ce qui a tranché : dans la MÊME publication, la modale de `S1-37` était parfaitement centrée.
    /// Même machine, même gestionnaire de fenêtres, deux résultats - donc la cause n'était ni l'un
    /// ni l'autre. Son scénario donne 1100x720 à son hôte, et laisse de la marge autour.
    private static final int LARGEUR = 1100;

    private static final int HAUTEUR = 720;

    /// L'entrée du menu ☰ qui ouvre la modale, telle que le produit la nomme quand aucun profil
    /// n'est enregistré.
    private static final String LIBELLE_ENTREE_MENU = "Se connecter à Vigie-Chiro…";

    private static final long LATENCE_RECUPERATION_MS = 1_500;

    private Injector injector;

    /// Le client bouchonné : la seule frontière que ce scénario remplace.
    private final ClientVigieChiro client = mock(ClientVigieChiro.class);

    private Stage fenetre;

    @Start
    void start(Stage stage) throws IOException {
        // La récupération PREND DU TEMPS, sans quoi il n'y aurait rien à voir : la zone de progression
        // paraîtrait et disparaîtrait entre deux images. Hors séance filmée, aucune attente n'est payée.
        when(client.moi()).thenAnswer(appel -> {
            if (Seance.filmee()) {
                Thread.sleep(LATENCE_RECUPERATION_MS);
            }
            return ReponseApi.succes(new ProfilVigieChiro("u-scenario", "chiro", "observateur"));
        });

        injector = BancDeRecette.surLeChrome()
                .taille(LARGEUR, HAUTEUR)
                // ASYNCHRONE, celui de la production. Le transitoire de S1-27 n'existe que hors du fil
                // JavaFX : en synchrone, la récupération bloque le fil, aucune image n'est rendue pendant
                // ce temps, et il n'y aurait rien à filmer. C'est l'exact contraire du besoin des
                // captures (#3242), qui exigent le synchrone pour ne pas photographier un « Chargement… ».
                // Ici, ce chargement EST le sujet.
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .remplacer(new AbstractModule() {
                    @Provides
                    ConnexionViewModel viewModel(StockageConnexion stockage) {
                        return new ConnexionViewModel(stockage, client, Set.of(), Optional.empty());
                    }

                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        // Le scénario n'ouvre aucun navigateur : rien à voir sur le film, et rien à
                        // lancer sur la machine qui filme.
                        return lien -> {};
                    }
                })
                .montrer(stage);
        fenetre = stage;
    }

    @Test
    @CasDeRecette(value = "S1-26", jugement = Jugement.HUMAIN, portee = Portee.A_L_ECRAN)
    @DisplayName("S1-26 · la modale de connexion s'ouvre : à regarder, rien ne doit se replacer après coup")
    void la_modale_de_connexion_s_ouvre(FxRobot robot) throws TimeoutException {
        Respiration.avantLeGeste(robot);

        ouvrirLaModaleParLeMenu(robot);

        // Le moment que ce cas existe pour montrer : la modale est posée, et c'est là qu'on juge
        // si quelque chose s'est replacé après coup.
        Respiration.surLeMomentCle(robot);

        assertThat(robot.lookup("#champToken").tryQuery())
                .as("le geste a-t-il seulement eu lieu ? Sans cette question, un robot mort rendrait"
                        + " un clip noir que personne ne signalerait.")
                .isPresent();
    }

    @Test
    @CasDeRecette(value = "S1-27", jugement = Jugement.HUMAIN, portee = Portee.A_L_ECRAN)
    @DisplayName("S1-27 · pendant la récupération : à regarder, rien ne doit sortir du cadre avant le bandeau")
    void la_recuperation_ne_pousse_rien_hors_du_cadre(FxRobot robot) throws TimeoutException {
        ouvrirLaModaleParLeMenu(robot);
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

    /// Ouvre la modale comme un utilisateur : le menu ☰, puis l'entrée qui la nomme.
    ///
    /// La version précédente appelait `ActionConnexion.executer(fenetre)` directement. C'était le
    /// bon chemin de code, et un mauvais film : la modale paraissait sans qu'aucun geste ne
    /// l'explique. Retour de la revue : « on ne comprend pas comment on arrive sur la modale ».
    ///
    /// Un cas perceptif se joue comme on le jouerait à la main. Le clic sur `#menuOutils` ouvre le
    /// menu et le laisse voir ; le clic sur l'entrée l'ouvre. Ce sont deux gestes, et le film les
    /// montre tous les deux.
    ///
    /// On clique l'entrée par son LIBELLÉ, tel que `NavigationConnexion.libelleMenu()` le rend
    /// pour un profil absent. Le viser par sa position dans le menu se casserait au premier ajout
    /// d'entrée, sans que le film le dise - il montrerait un autre écran s'ouvrir.
    private void ouvrirLaModaleParLeMenu(FxRobot robot) throws TimeoutException {
        GesteVisible.choisir(robot, "#menuOutils", LIBELLE_ENTREE_MENU);
    }
}
