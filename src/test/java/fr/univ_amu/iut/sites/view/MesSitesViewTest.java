package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.commun.view.InfobulleDeBlocage;
import fr.univ_amu.iut.commun.viewmodel.RevisionDonnees;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.connexion.viewmodel.RefletDuJeton;
import fr.univ_amu.iut.recette.CadreVisible;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.FenetreDuBanc;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.fxml.FXMLLoader;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX de l'écran **M-Sites** : chargement du chrome + de la vue d'accueil
/// via Guice sur une base SQLite jetable, affichage des cartes seedées et navigation vers le
/// détail au déclenchement d'une carte.
///
/// Les interactions sont déclenchées **sur le thread JavaFX** (`robot.interact` + handlers /
/// `fire()`) plutôt que via le robot souris/clavier de l'OS : ce dernier dépend d'un serveur
/// d'affichage capable de synthétiser des entrées (il se bloque sous Wayland et ne route pas
/// fiablement les clics sous un xvfb sans gestionnaire de fenêtres). On teste ainsi le câblage
/// réel (handler de carte → navigation) de façon déterministe dans tous les environnements.
@ExtendWith(ApplicationExtension.class)
class MesSitesViewTest {

    private static final String ID_USER = "u-test";

    /// Le libelle que `NavigationConnexion.libelleMenu()` rend tant qu'aucun profil n'est enregistre.
    /// Une fois connecte, cette meme entree porte le pseudo : le menu DIT l'etat de connexion.
    private static final String LIBELLE_SE_CONNECTER = "Se connecter à Vigie-Chiro…";
    private Injector injector;
    private final ClientVigieChiro client = mock(ClientVigieChiro.class);

    /// Ce que l'écran montrait pendant la synchronisation : une fenêtre de suivi, une barre, un « Annuler ».
    private final List<String> vuPendantLaSynchro = new ArrayList<>();

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-mes-sites");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        // Composition réelle, mais exécuteur SYNCHRONE (#1212) : le chargement des cartes passe par
        // l'occupation ; en asynchrone les assertions courraient contre le fil de chargement.
        injector =
                Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ExecuteurTache.class)
                                .to(ExecuteurTacheSynchrone.class)
                                .in(Singleton.class);
                        // Client bouchonné : la synchronisation n'appelle rien, mais elle passe par lui -
                        // c'est le seul endroit d'où observer l'écran PENDANT l'opération (#2606).
                        bind(ClientVigieChiro.class).toInstance(client);
                    }
                }));
        // ⚠️ Le mock répond comme le VRAI client : `estConnecte()` demande « un jeton est-il
        // enregistré ? » et rien d'autre (`ClientVigieChiro:98`). Un clip peut donc MONTRER la
        // connexion rouvrir le geste, au lieu de basculer un booléen que le produit n'aurait pas vu
        // bouger. Le stockage est un fichier relu à chaque appel : la réponse suit le geste.
        StockageConnexion stockage = injector.getInstance(StockageConnexion.class);
        when(client.estConnecte()).thenAnswer(appel -> stockage.estConnecte());
        when(client.moi()).thenReturn(ReponseApi.succes(new ProfilVigieChiro(ID_USER, "chiro", "observateur")));
        // ⚠️ Avec leur NUMÉRO DE CARRÉ, et le constructeur complet. La version courte laissait
        // `numeroCarre` à `null` : le rapprochement ne reconnaissait aucun site local, rendait un
        // rapport vide, et l'écran repliait sur « Aucun site distant récupéré (non connecté, ou aucun
        // site sur Vigie-Chiro) ». Le clip montrait alors une connexion réussie surmontée d'un bandeau
        // disant « non connecté ». Un bouchon trop pauvre fabrique un produit qui a l'air cassé.
        when(client.mesSites())
                .thenReturn(ReponseApi.succes(List.of(
                        new SiteVigieChiro("vc-1", "Étang de la Tuilière", false, "640380", ID_USER, List.of()),
                        new SiteVigieChiro("vc-2", "ZAC Nord", false, "752204", ID_USER, List.of()))));
        when(client.mesParticipations()).thenReturn(ReponseApi.succes(List.of()));
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        seeder(source);
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        // ⚠️ `Habillage` via `FenetreDuBanc`, et non `new Scene` : les six cas de cette classe sont
        // FILMÉS, et une scène montée sans habillage porte la police de la MACHINE (#3773, #4149).
        FenetreDuBanc.poser(stage, racine, 1180, 900);
        injector.getInstance(NavigationSites.class).ouvrirAccueil();
        FenetreDuBanc.afficher(stage);
    }

    private void seeder(SourceDeDonnees source) {
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        ServiceSites service = injector.getInstance(ServiceSites.class);
        Site etang = service.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(etang.id(), "A1", 43.5, 5.4, "Chêne");
        Site zac = service.creerSite("752204", "ZAC Nord", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(zac.id(), "A1", null, null, null);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#2606 : la synchronisation s'annonce sous une fenêtre de suivi, avec « Annuler »")
    void synchro_montre_son_avancement_et_laisse_renoncer(FxRobot robot) {
        // Depuis #4194 le geste est FERMÉ sans jeton : il faut donc en poser un pour l'exercer.
        seConnecter(robot);
        when(client.mesParticipations()).thenAnswer(appel -> {
            // Instantané pris pendant le travail : après coup, la fenêtre est déjà refermée.
            Window suivi = Window.getWindows().stream()
                    .filter(f -> f.getScene() != null && f.getScene().lookup(".progress-bar") != null)
                    .findFirst()
                    .orElse(null);
            vuPendantLaSynchro.add("fenetre_de_suivi=" + (suivi != null));
            vuPendantLaSynchro.add(
                    "annuler=" + (suivi != null && suivi.getScene().lookup(".button") != null));
            return ReponseApi.injoignable("bouchon");
        });

        robot.clickOn("#btnSyncVigieChiro");

        // Ce que le voile opaque ne donnait pas : un avancement visible, et un moyen de renoncer.
        assertThat(vuPendantLaSynchro).containsExactly("fenetre_de_suivi=true", "annuler=true");
        assertThat(Window.getWindows())
                .as("la fenêtre de suivi se referme, elle ne reste pas là")
                .noneSatisfy(fenetre ->
                        assertThat(fenetre.getScene().lookup(".progress-bar")).isNotNull());
    }

    @Test
    @CasDeRecette(value = "S1-16", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-16 · hors connexion, « Récupérer depuis Vigie-Chiro » est fermé et dit ce qui manque")
    void hors_connexion_la_recuperation_est_fermee(FxRobot robot) throws TimeoutException {
        // ⚠️ Le clip commence par ÉTABLIR la situation, au lieu de la supposer. Le menu principal
        // porte « Se connecter à Vigie-Chiro… » tant qu'aucun profil n'est enregistré, et le pseudo
        // ensuite (`NavigationConnexion.libelleMenu`) : c'est le produit qui dit qu'on n'est pas
        // connecté, le clip n'a rien à affirmer par-dessus. Demande de la revue (#4171) : « il
        // faudrait montrer que si on n'est pas connecté, en affichant le menu action par exemple ».
        robot.clickOn("#menuOutils");
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(robot.lookup(LIBELLE_SE_CONNECTER).tryQuery())
                .as("le menu annonce qu'il reste à se connecter : c'est la situation que ce clip pose")
                .isPresent();
        Respiration.leTempsDeLire(robot);

        robot.type(KeyCode.ESCAPE);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.entreDeuxGestes(robot);

        Button bouton = robot.lookup("#btnSyncVigieChiro").queryAs(Button.class);
        CadreVisible.amener(bouton, robot);
        assertThat(bouton.isVisible())
                .as("app complète : la passerelle est liée, le bouton est offert (#1045)")
                .isTrue();
        assertThat(bouton.isDisabled())
                .as("sans jeton, rien ne peut être récupéré : le geste est fermé (#4194)")
                .isTrue();
        // ⚠️ Le motif se FAIT PARAÎTRE, il ne se lit pas par programme. Les images du tournage
        // précédent montraient un bouton gris impeccable et aucune explication : la moitié du cas -
        // « il dit ce qui manque » - restait hors de l'image.
        assertThat(InfobulleDeBlocage.montrerEtLire(
                        robot.lookup("#enveloppeSync").query(), robot))
                .as("et il dit ce qui manque, avec le geste qui répare")
                .contains("pas connecté")
                .contains("Se connecter à Vigie-Chiro");
        Respiration.surLeMomentCle(robot);
        Respiration.leTempsDeLire(robot);
    }

    @Test
    @CasDeRecette(value = "S1-16", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-16 · une fois connecté, « Récupérer depuis Vigie-Chiro » s'ouvre et rend compte")
    void une_fois_connecte_la_recuperation_s_ouvre_et_rend_compte(FxRobot robot) throws TimeoutException {
        Button bouton = robot.lookup("#btnSyncVigieChiro").queryAs(Button.class);
        CadreVisible.amener(bouton, robot);
        assertThat(bouton.isDisabled())
                .as("écran de départ : le geste est encore fermé, faute de jeton")
                .isTrue();
        Respiration.avantLeGeste(robot);

        // ⚠️ La connexion se JOUE, elle ne se pose pas dans le stockage à la main. La version
        // précédente basculait un booléen du mock et sautait de « fermé » à « ouvert » sans rien
        // montrer : la revue n'y voyait pas ce qui avait changé l'état (#4171). Ici le jeton arrive
        // par le seul chemin qu'un utilisateur emprunte, et c'est LUI qui rouvre le bouton.
        robot.clickOn("#menuOutils");
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.entreDeuxGestes(robot);
        GesteVisible.cliquer(robot, LIBELLE_SE_CONNECTER);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.avantLeGeste(robot);

        robot.clickOn("#champToken").write("jeton-de-recette");
        robot.clickOn("#boutonConnecter");
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> robot.lookup("#bandeauStatut").queryAs(Label.class).isVisible());
        Respiration.leTempsDeLire(robot);

        // ⚠️ ADR 4188 : une modale se filme avec son écran de départ ET son écran d'arrivée. On la
        // referme donc, et « Mes sites » revient : c'est là que le changement se lit.
        robot.clickOn("#boutonFermer");
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.apresLeGeste(robot);

        CadreVisible.amener(bouton, robot);
        assertThat(bouton.isDisabled())
                .as("le jeton posé, le geste se rouvre : c'est ce que la connexion vient de changer")
                .isFalse();
        Respiration.avantLeGeste(robot);

        robot.clickOn(bouton);
        WaitForAsyncUtils.waitForFxEvents();

        Label message = robot.lookup("#lblSynchro").queryAs(Label.class);
        CadreVisible.amener(message, robot);
        assertThat(message.isVisible())
                .as("la synchronisation rend compte : un bouton qui ne dit rien ne se distingue pas"
                        + " d'un bouton qui n'a rien fait")
                .isTrue();
        // ⚠️ L'assertion cherchait le mot « site », et « Aucun site distant récupéré (non connecté, ou
        // aucun site sur Vigie-Chiro) » le contient. Le clip publié montrait donc une connexion réussie
        // surmontée d'un bandeau disant « non connecté » : la scène et son compte rendu se
        // contredisaient, et rien ne rougissait. Une assertion assez lâche pour accepter le contraire de
        // ce qu'elle vérifie n'est pas une assertion.
        assertThat(message.getText())
                .as("le bandeau nomme ce qui a été récupéré, et ne dit surtout pas le contraire de la scène")
                .contains("synchronisé")
                .doesNotContain("Aucun")
                .doesNotContain("non connecté");

        // ⚠️ La respiration FINALE, et elle est longue exprès. Le bandeau est le résultat du cas :
        // un clip qui coupe à l'instant où il paraît ne le donne pas à lire (#4171).
        Respiration.surLeMomentCle(robot);
        Respiration.leTempsDeLire(robot);
    }

    @Test
    @CasDeRecette(value = "S1-17", portee = Portee.A_L_ECRAN)
    @DisplayName("#1212 : l'overlay d'occupation est en place, masqué une fois le chargement terminé")
    void overlay_occupation_masque_apres_chargement(FxRobot robot) {
        Node voile = robot.lookup(".occupation-voile").query();

        assertThat(voile).as("overlay d'occupation superposé à l'écran").isNotNull();
        assertThat(voile.isVisible())
                .as("chargement terminé (exécuteur synchrone) : overlay masqué, cartes affichées")
                .isFalse();

        // Ce que le clip montre : l'écran chargé et RIEN qui le voile - les cartes sont là, lisibles,
        // atteignables. C'est le défaut que ce cas garde : un voile resté en place bloquerait tout.
        assertThat(robot.lookup(".carte-site").queryAll())
                .as("sans carte, un écran non voilé ne prouverait rien : il serait vide, pas prêt")
                .isNotEmpty();
        Respiration.leTempsDeLire(robot);

        // ⚠️ Ce clip-ci ne montre pas le voile PENDANT qu'il paraît, et c'est voulu : il garde l'autre
        // moitié du cas - l'écran chargé, ses cartes lisibles, et RIEN qui le voile. Un voile resté en
        // place bloquerait tout.
        //
        // ⚠️ J'ai écrit ici qu'AUCUN clip ne pouvait montrer le voile, et c'était faux. L'affirmation
        // était mesurée sur cette fixture-ci, deux sites, où `charger()` est instantané. Sur cent
        // cinquante carrés - ce que suit un coordinateur départemental - il prend un demi-seconde, de
        // quoi rendre cinq images. `ScenarioVoileChargementTest` le filme (#4172).
        //
        // Le défaut de raisonnement mérite d'être noté : j'avais conclu du produit ce qui n'était vrai
        // que de la base de démonstration.
    }

    @Test
    @CasDeRecette(value = "S1-14", portee = Portee.A_L_ECRAN)
    @DisplayName("Les cartes des sites seedés sont affichées")
    void affiche_les_cartes(FxRobot robot) {
        List<Label> cartes =
                robot.lookup(".carte-titre").queryAllAs(Label.class).stream().toList();

        assertThat(cartes).extracting(Label::getText).contains("Carré 640380", "Carré 752204");

        // Chaque carte est amenée à l'image et tenue : ce cas fait juger une LISTE, et une liste dont
        // la moitié est sous le pli n'est pas montrée.
        for (Label carte : cartes) {
            CadreVisible.amener(carte, robot);
            assertThat(CadreVisible.contient(carte))
                    .as("« %s » reste hors du cadre", carte.getText())
                    .isTrue();
            Respiration.leTempsDeLire(robot);
        }
    }

    @Test
    @DisplayName("Déclencher une carte ouvre le détail du site correspondant")
    void clic_carte_ouvre_le_detail(FxRobot robot) {
        HBox carte = trouverCarte(robot, "Carré 640380");

        robot.interact(() -> carte.getOnMouseClicked().handle(clicGauche()));

        // Le titre du détail est déporté en barre de statut (#693) : on confirme l'ouverture du bon
        // site par le bandeau d'identité (numéro de carré) plutôt que par un titre d'en-tête.
        Label numeroCarre = robot.lookup("#valNumeroCarre").queryAs(Label.class);
        assertThat(numeroCarre.getText()).isEqualTo("640380");
    }

    @Test
    @CasDeRecette(value = "S1-15", portee = Portee.A_L_ECRAN)
    @DisplayName("#799 : une carte est atteignable au Tab, et annoncée comme un bouton")
    void une_carte_est_atteignable_au_clavier(FxRobot robot) {
        // Le Tab lui-même n'est pas simulé : ce qui le rend possible est cette propriété, et c'est
        // elle que le défaut ferait disparaître. Une HBox n'est PAS focalisable par défaut.
        HBox carte = trouverCarte(robot, "Carré 640380");

        assertThat(carte.isFocusTraversable())
                .as("sans cela, aucun Tab n'atteint la carte et les deux tests suivants n'ont plus de sujet")
                .isTrue();
        assertThat(carte.getAccessibleRole()).isEqualTo(AccessibleRole.BUTTON);

        // Le clip montrait un écran immobile : une propriété ne se voit pas. Le focus, lui, se voit -
        // c'est ce que cette propriété rend possible, et c'est ce qu'un utilisateur au clavier obtient.
        CadreVisible.amener(carte, robot);
        Respiration.avantLeGeste(robot);
        robot.interact(carte::requestFocus);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);

        assertThat(carte.isFocused())
                .as("la carte prend réellement le focus : « focalisable » n'est pas « focalisée »")
                .isTrue();
    }

    @Test
    @CasDeRecette(value = "S1-15", portee = Portee.A_L_ECRAN)
    @DisplayName("#799 : Entrée sur une carte ouvre le détail, comme un clic")
    void entree_ouvre_le_detail(FxRobot robot) {
        HBox carte = trouverCarte(robot, "Carré 640380");

        // ⚠️ Une VRAIE frappe, sur une carte qui a le focus. Appeler `getOnKeyPressed().handle(...)`
        // prouve le gestionnaire et saute tout le reste : le clip montrait l'écran changer sans qu'aucun
        // geste ne l'explique (#4149). Et l'assertion y gagne - un gestionnaire câblé sur un noeud que
        // le clavier n'atteint jamais ne sert personne.
        CadreVisible.amener(carte, robot);
        robot.interact(carte::requestFocus);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.avantLeGeste(robot);

        robot.push(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);

        // Le détail absent est la façon dont ce test rougit quand la touche cesse d'être
        // traitée : sans cette question posée d'abord, il ne rendrait qu'un « nœud
        // introuvable » là où la cause est « Entrée n'a rien déclenché ».
        assertThat(robot.lookup("#valNumeroCarre").tryQuery())
                .as("Entrée sur la carte n'a pas ouvert le détail")
                .isPresent();
        assertThat(robot.lookup("#valNumeroCarre").queryAs(Label.class).getText())
                .isEqualTo("640380");
    }

    @Test
    @CasDeRecette(value = "S1-15", portee = Portee.A_L_ECRAN)
    @DisplayName("#799 : Espace sur une carte ouvre le détail, comme un clic")
    void espace_ouvre_le_detail(FxRobot robot) {
        // Les deux touches sont éprouvées séparément : le contrôleur les traite dans une seule
        // condition, et retirer une des deux branches ne ferait rougir aucun test qui n'essaie que
        // l'autre.
        HBox carte = trouverCarte(robot, "Carré 752204");

        CadreVisible.amener(carte, robot);
        robot.interact(carte::requestFocus);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.avantLeGeste(robot);

        robot.push(KeyCode.SPACE);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);

        // Le détail absent est la façon dont ce test rougit quand la touche cesse d'être
        // traitée : sans cette question posée d'abord, il ne rendrait qu'un « nœud
        // introuvable » là où la cause est « Espace n'a rien déclenché ».
        assertThat(robot.lookup("#valNumeroCarre").tryQuery())
                .as("Espace sur la carte n'a pas ouvert le détail")
                .isPresent();
        assertThat(robot.lookup("#valNumeroCarre").queryAs(Label.class).getText())
                .isEqualTo("752204");
    }

    /// Pose un jeton et provoque la relecture, comme le ferait une connexion depuis le menu ☰.
    ///
    /// ⚠️ La connexion elle-même n'est pas jouée ici - elle a sa propre modale et ses propres cas
    /// (`S1-04` à `S1-08`). Ce qui est montré est son EFFET sur cet écran : le geste qui se rouvre.
    /// Se connecte **comme le produit le fait** : un jeton écrit dans le stockage, et le reflet publié.
    ///
    /// ⚠️ Cette aide basculait un booléen du mock (`when(client.estConnecte()).thenReturn(true)`).
    /// Depuis que l'écran lit `EtatConnexion` - le même lecteur que la fiche et la modale de carré -
    /// c'est le STOCKAGE qui fait foi, et un drapeau posé sur le client ne rouvrait plus rien. Un test
    /// qui simule autrement que le produit finit par mesurer sa simulation.
    private void seConnecter(FxRobot robot) {
        injector.getInstance(StockageConnexion.class)
                .enregistrer("jeton-de-recette", new ProfilVigieChiro(ID_USER, "chiro", "observateur"));
        injector.getInstance(RefletDuJeton.class).relire();
        robot.interact(() -> injector.getInstance(RevisionDonnees.class).mutationStructurelleValidee());
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);
    }

    private static KeyEvent touche(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    private static HBox trouverCarte(FxRobot robot, String titre) {
        return robot.lookup(".carte-site").queryAllAs(HBox.class).stream()
                .filter(carte -> contientTitre(carte, titre))
                .findFirst()
                .orElseThrow();
    }

    private static boolean contientTitre(HBox carte, String titre) {
        return carte.lookupAll(".carte-titre").stream()
                .anyMatch(noeud -> noeud instanceof Label label && titre.equals(label.getText()));
    }

    private static MouseEvent clicGauche() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                null);
    }
}
