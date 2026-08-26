package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CadreVisible;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX du chrome (`MainView`) : la barre de navigation (← Retour + fil d'Ariane,
/// portés par le chrome donc présents sur tous les écrans) reflète l'historique et l'emplacement,
/// permet de revenir à l'écran précédent réel et de sauter à un ancêtre du fil, et respecte le verrou
/// (#54). Couvre #22 et #140.
@ExtendWith({ApplicationExtension.class, SansExceptionAvalee.class})
class MainViewTest {

    private Injector injector;
    private SourceDeDonnees source;
    private Navigateur navigateur;
    private NavigationViewModel navigation;

    @Start
    void start(Stage stage) throws IOException {
        injector = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                // `ActionRestaurer` en singleton, POUR LE BANC SEULEMENT. Sans cela l'injecteur en
                // rend une instance neuve à chaque demande - mesuré - et les dialogues bouchonnés par le
                // test s'appliquent à un jetable pendant que le menu en garde un autre : le clic
                // ouvrirait alors le sélecteur natif, qui fige le banc. La portée est le seul écart avec
                // la production, et elle ne change rien à ce que le cas éprouve.
                .remplacer(liaison -> liaison.bind(ActionRestaurer.class).in(Singleton.class))
                .montrer(stage);
        source = injector.getInstance(SourceDeDonnees.class);
        navigateur = injector.getInstance(Navigateur.class);
        navigation = injector.getInstance(NavigationViewModel.class);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Le ← Retour est masqué sur l'accueil et apparaît dès qu'on entre dans une feature")
    void retour_masque_sur_accueil(FxRobot robot) {
        Button retour = robot.lookup("#boutonRetour").queryAs(Button.class);
        assertThat(retour.isVisible()).isFalse();

        robot.interact(() -> navigateur.afficher(new Group(), "sites", "Mes sites"));

        assertThat(retour.isVisible()).isTrue();
    }

    @Test
    @DisplayName("← Retour revient à l'écran précédent réel, puis à l'accueil (sans détour)")
    void retour_revient_a_l_ecran_precedent(FxRobot robot) {
        robot.interact(() -> {
            navigateur.afficher(new Group(), "sites", "Mes sites");
            navigateur.afficher(new Group(), "site-detail", "Carré 640380");
        });
        Button retour = robot.lookup("#boutonRetour").queryAs(Button.class);

        robot.interact(retour::fire);
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("sites");

        robot.interact(retour::fire);
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("accueil");
        assertThat(retour.isVisible()).isFalse();
        assertThat(robot.lookup("#cartesActivites").tryQuery()).isPresent();
    }

    @Test
    @CasDeRecette(value = "S1-29", portee = Portee.A_L_ECRAN)
    @DisplayName("#927 : le menu ☰ → « Réglages » ouvre l'écran de réglages dans la zone centrale")
    void menu_reglages_ouvre_l_ecran(FxRobot robot) throws TimeoutException {
        MenuButton menu = robot.lookup("#menuOutils").queryAs(MenuButton.class);
        MenuItem reglages = menu.getItems().stream()
                .filter(item -> item.getText() != null && item.getText().contains("Réglages"))
                .findFirst()
                .orElseThrow();

        // Le menu s'OUVRE avant qu'on choisisse. `fire()` sur l'entrée saute cette moitié : l'écran
        // changeait sans que rien ne l'explique, et le clip ne montrait pas d'où venait le geste (#4149).
        Respiration.avantLeGeste(robot);

        // Le pointeur VA sur l'entrée et s'y arrête avant de cliquer (#4177). Remplacer `fire()` par
        // un vrai clic (#4158) était nécessaire et pas suffisant : `clickOn(libellé)` téléporte le
        // pointeur et clique dans la foulée, le menu se referme, et l'instant du choix n'existe sur
        // aucune trame. Mesuré en extrayant les images autour du clic.
        GesteVisible.choisir(robot, "#menuOutils", reglages.getText());
        Respiration.surLeMomentCle(robot);

        // La zone centrale affiche désormais l'écran Réglages (son TabPane d'onglets).
        assertThat(navigateur.getVueCentrale().lookup(".onglets-reglages"))
                .as("l'écran Réglages est affiché")
                .isNotNull();
    }

    @Test
    @CasDeRecette(value = "S1-01", portee = Portee.A_L_ECRAN)
    @DisplayName("L'accueil regroupe les cartes en deux sections de prismes (Collecte / Biodiversité)")
    void accueil_regroupe_en_deux_prismes(FxRobot robot) {
        FlowPane sections = robot.lookup("#cartesActivites").queryAs(FlowPane.class);
        assertThat(sections.getChildren()).as("une section par prisme").hasSize(2);

        List<Label> titres = robot.lookup(".section-prisme-titre").queryAllAs(Label.class).stream()
                .toList();
        assertThat(titres)
                .extracting(Label::getText)
                .containsExactlyInAnyOrder("Collecte & passages", "Espèces & biodiversité");

        // Ce cas fait juger un REGROUPEMENT : il ne se lit qu'en voyant les deux titres, chacun à
        // l'image et assez longtemps pour être lu. Le clip précédent traversait cet écran en une
        // fraction de seconde - « ne s'arrête pas assez longtemps pour qu'on voie ce qu'il montre »
        // (#4149).
        for (Label titre : titres) {
            CadreVisible.amener(titre, robot);
            assertThat(CadreVisible.contient(titre))
                    .as(
                            "« %s » reste hors du cadre : le clip annoncerait deux sections dont une invisible",
                            titre.getText())
                    .isTrue();
            Respiration.leTempsDeLire(robot);
        }
    }

    @Test
    @CasDeRecette(value = "S1-03", portee = Portee.A_L_ECRAN)
    @DisplayName("Le fil d'Ariane reflète le parcours ; cliquer un ancêtre y ramène")
    void fil_ariane_reflete_le_parcours(FxRobot robot) throws TimeoutException {
        // De VRAIS écrans, atteints par des CLICS. Ce test naviguait sur deux `Group` vides : il
        // prouvait le câblage du fil d'Ariane - c'est légitime - mais son clip montrait un fil au-dessus
        // d'un écran BLANC. Le produit qu'il filmait n'existait pas, d'où « incompréhensible » (#4149),
        // et aucun temps d'arrêt n'y aurait rien changé.
        semerUnSite();
        Respiration.leTempsDeLire(robot);

        robot.clickOn(carteDAccueil(robot, "Mes sites"));
        // L'écran « Mes sites » se peuple par l'exécuteur ASYNCHRONE du vrai injecteur : au retour du
        // clic il ne porte encore que son voile d'occupation. `waitForFxEvents` vide la file du fil FX,
        // il n'attend pas le thread qui la remplira - on attend donc la carte elle-même.
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> !robot.lookup(".carte-site").queryAll().isEmpty());
        Respiration.entreDeuxGestes(robot);

        robot.clickOn((Node) robot.lookup(".carte-site").query());
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.leTempsDeLire(robot);

        // Fil = Accueil › Mes sites › Carré 640380 (segments dans l'ordre, dernier non cliquable).
        HBox fil = robot.lookup("#filAriane").queryAs(HBox.class);
        var libelles = fil.getChildren().stream()
                .filter(n -> n.getStyleClass().contains("fil-ariane-segment")
                        || n.getStyleClass().contains("fil-ariane-courant"))
                .map(n -> ((Labeled) n).getText())
                .toList();
        assertThat(libelles).containsExactly("Accueil", "Mes sites", "Carré 640380");

        Hyperlink mesSites = fil.getChildren().stream()
                .filter(n -> n instanceof Hyperlink h && "Mes sites".equals(h.getText()))
                .map(Hyperlink.class::cast)
                .findFirst()
                .orElseThrow();
        // Le segment se CLIQUE : le clip montre le retour en arrière au lieu de le subir.
        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, mesSites);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.apresLeGeste(robot);

        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("sites");
    }

    @Test
    @DisplayName("Raccourcis clavier : Alt+← (retour) et Alt+Début (accueil) sont actifs sur le chrome")
    void raccourcis_clavier_navigation(FxRobot robot) {
        var altGauche = new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN);
        var altDebut = new KeyCodeCombination(KeyCode.HOME, KeyCombination.ALT_DOWN);
        Scene scene = robot.lookup("#boutonRetour").queryAs(Button.class).getScene();

        // Les deux raccourcis de navigation sont enregistrés sur la scène du chrome.
        assertThat(scene.getAccelerators()).containsKeys(altGauche, altDebut);

        // Navigation profonde : Accueil › Mes sites › Carré 640380. Le focus sort du champ de
        // recherche, qui le porte par défaut : sous macOS, Option+← y est la navigation par mot, et le
        // champ CONSOMME la frappe. Mesuré (run 32943631805) : le filtre de la scène voit le
        // KEY_PRESSED de LEFT et `match` y vaut vrai, mais un seul des deux événements ressort en
        // remontée, et l'accélérateur ne s'exécute jamais. Alt+Début agit depuis le champ, d'où un
        // seul des deux raccourcis rouge. Poser le focus sur la racine est le geste réel : on ne
        // revient pas en arrière au milieu d'une saisie (#4522).
        robot.interact(() -> {
            navigateur.afficher(new Group(), "sites", "Mes sites");
            navigateur.afficher(new Group(), "site-detail", "Carré 640380");
            scene.getRoot().requestFocus();
        });

        // On PRESSE les touches, on ne lance pas le `Runnable` de l'accélérateur. Lancer le
        // `Runnable` prouve que le `Runnable` fait son travail ; presser prouve que le raccourci est
        // CÂBLÉ, ce qui est la question. Et sans frappe réelle, aucun `KeyEvent` n'atteint la scène,
        // donc le calque des gestes n'a rien à dessiner et le clip ne montre pas le raccourci (#4242).

        // Alt+← est bien câblé au RETOUR (écran précédent réel = sites), pas au saut à l'accueil.
        robot.push(KeyCode.ALT, KeyCode.LEFT);
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("sites");

        // Alt+Début saute directement à l'accueil depuis n'importe quel écran.
        robot.push(KeyCode.ALT, KeyCode.HOME);
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(navigation.vueCouranteProperty().get()).isEqualTo("accueil");
        assertThat(robot.lookup("#boutonRetour").queryAs(Button.class).isVisible())
                .isFalse();
    }

    @Test
    @DisplayName(
            "#906 : le ← Retour reste actif pendant une opération critique (on avertit à la sortie, pas de blocage dur)")
    void retour_actif_pendant_operation_critique(FxRobot robot) {
        robot.interact(() -> navigateur.afficher(new Group(), "import", "Importer une nuit"));
        Button retour = robot.lookup("#boutonRetour").queryAs(Button.class);
        assertThat(retour.isDisabled()).isFalse();

        // Une opération critique ne grise plus le bouton (#906) : il reste cliquable et l'avertissement
        // survient au clic (cf. Navigateur#peutQuitter), au lieu d'un blocage muet.
        robot.interact(() -> navigation.setOperationCritique("l'import"));
        assertThat(retour.isDisabled()).isFalse();
    }

    @Test
    @CasDeRecette(value = "S1-02", portee = Portee.A_L_ECRAN)
    @DisplayName("Tableau de bord : le bandeau de compteurs est masqué quand la base est vide (#141)")
    void bandeau_masque_si_base_vide(FxRobot robot) {
        FlowPane bandeau = robot.lookup("#bandeauIndicateurs").queryAs(FlowPane.class);
        assertThat(bandeau.isVisible())
                .as("au premier lancement, la base est vide : le bandeau ne paraît pas")
                .isFalse();
        Respiration.leTempsDeLire(robot);

        // Une absence NE SE VOIT PAS. Un arrêt sur un accueil sans bandeau montre un accueil, et rien
        // ne dit ce qu'on est censé ne pas y voir (#4149). Elle se lit par CONTRASTE : on donne à la
        // base de quoi remplir le bandeau, puis on le lui retire.
        //
        // Le sens compte, et il distingue ce clip de celui de `S1-09` : là-bas le bandeau PARAÎT sans
        // qu'on ait navigué, ici il SE RETIRE quand la donnée s'en va - qui est la phrase du cas.
        semerUnSite();
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(bandeau.isVisible())
                .as("avec une donnée, le bandeau est là : c'est le point de comparaison")
                .isTrue();
        Respiration.leTempsDeLire(robot);

        viderLesSites();
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);

        assertThat(bandeau.isVisible())
                .as("la base redevient vide : le bandeau se retire, il ne reste pas à zéro")
                .isFalse();
        assertThat(bandeau.getChildren()).isEmpty();
    }

    @Test
    @DisplayName("Tableau de bord : le bandeau affiche les compteurs après un retour sur l'accueil (#141)")
    void bandeau_affiche_compteurs_apres_donnees(FxRobot robot) {
        robot.interact(() -> {
            new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
            injector.getInstance(ServiceSites.class)
                    .creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, "u-1");
        });
        // On quitte l'accueil puis on y revient : le retour déclenche le recalcul des compteurs.
        robot.interact(() -> navigateur.afficher(new Group(), "sites", "Mes sites"));
        robot.interact(navigateur::afficherAccueil);

        FlowPane bandeau = robot.lookup("#bandeauIndicateurs").queryAs(FlowPane.class);
        assertThat(bandeau.isVisible()).isTrue();
        assertThat(robot.lookup(".indicateur-libelle").queryAllAs(Label.class))
                .extracting(Label::getText)
                .contains("Sites");
    }

    @Test
    @CasDeRecette(value = "S1-10", portee = Portee.A_L_ECRAN)
    @DisplayName("#1405 : les compteurs suivent une RESTAURATION, sans qu'on ait quitté l'accueil")
    void bandeau_suit_une_restauration_sans_navigation(FxRobot robot, @TempDir Path sauvegardes)
            throws TimeoutException {
        // Ce cas n'est PAS celui de S1-09, et la nuance décide de son existence. Là-bas, la
        // mutation est un `creerSite`, qui annonce lui-même sa révision. Ici, la base ENTIÈRE est
        // remplacée par le contenu d'un fichier : c'est un tout autre chemin, et rien ne garantit a
        // priori qu'il emprunte le même canal d'annonce. S'il ne l'empruntait pas, les compteurs
        // resteraient sur l'ancienne base tant qu'on n'a pas quitté l'accueil - ce que le test de
        // S1-09 ne peut pas voir.
        FlowPane bandeau = robot.lookup("#bandeauIndicateurs").queryAs(FlowPane.class);
        ServiceSites sites = injector.getInstance(ServiceSites.class);
        ServiceSauvegarde sauvegarde = injector.getInstance(ServiceSauvegarde.class);

        // Une sauvegarde qui CONTIENT un site, puis une base qu'on vide : l'écran retombe à zéro.
        robot.interact(() -> {
            new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
            sites.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, "u-1");
        });
        WaitForAsyncUtils.waitForFxEvents();
        Path fichier = sauvegarde.sauvegarder(sauvegardes);
        robot.interact(() -> sites.listerSites("u-1").forEach(site -> sites.supprimerSite(site.id())));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(bandeau.isVisible())
                .as("point de départ du cas : la base est vide, le bandeau est masqué")
                .isFalse();
        // L'état de repos, avant le geste : sans lui on ne peut pas dire que quelque chose a changé.
        Respiration.avantLeGeste(robot);

        // Le geste RÉEL, par le menu. Le test appelait `sauvegarde.restaurer(fichier)` : le clip
        // montrait un bandeau qui se vide puis se remplit sans aucune cause visible, ce qu'un
        // spectateur attentif prend pour un défaut du produit (#4158).
        //
        // Une seule frontière est bouchonnée : la DÉSIGNATION du fichier. Le socle a été bâti pour
        // cela - un sélecteur natif fige un test headless, d'où `ChoixSauvegarde` et `Confirmateur`
        // injectables (#3197, #1405). Tout le reste est le chemin de production.
        ActionRestaurer entree = actionDuMenu(ActionRestaurer.class);
        ActionsSauvegarde dialogues = entree.porteur().actions();
        dialogues.choix().definir((titre, dossier, entrees, repli) -> Optional.of(fichier));
        dialogues.confirmateur().definir(message -> true);
        dialogues.notificateur().definir((niveau, entete, message) -> {});

        // Le pointeur s'arrête SUR l'entrée avant de cliquer (#4177) : sans quoi le clip montre le
        // menu, puis les compteurs qui changent, et jamais ce qui a été choisi entre les deux.
        GesteVisible.choisir(robot, "#menuOutils", entree.libelle());
        Respiration.surLeMomentCle(robot);

        assertThat(bandeau.isVisible())
                .as("les compteurs reflètent la base restaurée sans qu'on ait quitté l'accueil")
                .isTrue();
        assertThat(robot.lookup(".indicateur-libelle").queryAllAs(Label.class))
                .extracting(Label::getText)
                .contains("Sites");
    }

    @Test
    @CasDeRecette(value = "S1-09", portee = Portee.A_L_ECRAN)
    @DisplayName("#1376 : les compteurs suivent une mutation survenue SANS changement de vue")
    void bandeau_suit_une_mutation_sans_navigation(FxRobot robot) {
        FlowPane bandeau = robot.lookup("#bandeauIndicateurs").queryAs(FlowPane.class);
        assertThat(bandeau.isVisible())
                .as("base vide au départ : le bandeau est masqué")
                .isFalse();
        Respiration.avantLeGeste(robot);

        // Le geste réel de #1376 : la connexion s'ouvre PAR-DESSUS l'accueil et sa synchronisation
        // importe des sites. On ne quitte pas l'accueil, et on n'y revient pas : c'est précisément
        // l'aller-retour de `bandeau_affiche_compteurs_apres_donnees` qui masquait le défaut.
        // On ne poste PAS le signal à la main ici. `creerSite` l'émet lui-même, et c'est ce maillon
        // que ce test doit tenir : avec une annonce explicite en plus, il resterait vert même si le
        // service cessait d'annoncer, c'est-à-dire précisément quand le défaut #1376 reviendrait.
        robot.interact(() -> {
            new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
            injector.getInstance(ServiceSites.class)
                    .creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, "u-1");
        });
        WaitForAsyncUtils.waitForFxEvents();
        // Ce que le cas existe pour montrer : le bandeau qui paraît alors qu'on n'a pas quitté l'accueil.
        Respiration.surLeMomentCle(robot);

        assertThat(bandeau.isVisible())
                .as("le bandeau paraît sans qu'on ait navigué")
                .isTrue();
        assertThat(robot.lookup(".indicateur-libelle").queryAllAs(Label.class))
                .extracting(Label::getText)
                .contains("Sites");
    }

    @Test
    @DisplayName("L'accueil affiche le hero nocturne et une carte (chip + chevron) par activité")
    void accueil_affiche_hero_et_cartes(FxRobot robot) {
        assertThat(robot.lookup(".hero-nocturne").tryQuery()).isPresent();

        int cartes = robot.lookup(".carte-activite").queryAll().size();
        assertThat(cartes).isPositive();
        // Chaque carte porte exactement une pastille d'icône et un chevron d'invite.
        assertThat(robot.lookup(".carte-chip").queryAll()).hasSize(cartes);
        assertThat(robot.lookup(".carte-chevron").queryAll()).hasSize(cartes);
    }

    @Test
    @CasDeRecette(value = "S1-03", portee = Portee.A_L_ECRAN)
    @DisplayName("#144 : Ctrl+F est actif sur le chrome et donne le focus au champ de recherche")
    void ctrl_f_active_la_recherche(FxRobot robot) {
        var ctrlF = new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN);
        Scene scene = robot.lookup("#champRecherche").queryAs(TextField.class).getScene();
        assertThat(scene.getAccelerators()).containsKey(ctrlF);

        TextField champ = robot.lookup("#champRecherche").queryAs(TextField.class);

        // Le focus est REMIS AILLEURS d'abord, et sans cela ce test ne prouvait rien : mesuré,
        // `#champRecherche` porte déjà le focus au démarrage, si bien que l'assertion « Ctrl+F donne le
        // focus » était vraie AVANT que le raccourci ne soit lancé. Son mutant survivait (#4149).
        robot.interact(
                () -> robot.lookup("#menuOutils").queryAs(MenuButton.class).requestFocus());
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(champ.isFocused())
                .as("point de départ : le focus est ailleurs, sinon le raccourci n'a rien à faire")
                .isFalse();
        Respiration.avantLeGeste(robot);

        // On PRESSE Ctrl+F. Lancer le `Runnable` de l'accélérateur donnait bien le focus, mais
        // aucun `KeyEvent` n'atteignait la scène : le clip de ce cas ne montrait AUCUN raccourci, et sa
        // bande de badge restait immobile à la troisième décimale sur les 49 images utiles (#4242).
        robot.push(KeyCode.CONTROL, KeyCode.F);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(champ.isFocused())
                .as("Ctrl+F ramène le focus sur le champ de recherche")
                .isTrue();

        // Un focus NE SE VOIT PAS : le clip montrait un écran immobile, et le cas restait
        // incompréhensible (#4149). On tape derrière le raccourci - c'est ce que fait quelqu'un qui
        // vient de le presser, et c'est la seule façon de montrer que le champ est vivant.
        //
        // L'assertion y gagne : un focus qui ne reçoit pas les frappes est un focus qui ment.
        robot.write("640380");
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);

        assertThat(champ.getText())
                .as("la frappe atteint le champ sans qu'on ait eu à le cliquer")
                .isEqualTo("640380");
    }

    @Test
    @DisplayName("#144 : saisir un n° de carré liste le site puis Entrée navigue (liste fermée, champ vidé)")
    void recherche_globale_liste_et_navigue(FxRobot robot) {
        // L'utilisateur courant (idUtilisateurCourant) est auto-créé au démarrage ; on seede le site
        // sous SON identité, car la recherche filtre par utilisateur courant.
        String utilisateur = injector.getInstance(Key.get(String.class, Names.named("idUtilisateurCourant")));
        robot.interact(() -> injector.getInstance(ServiceSites.class)
                .creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, utilisateur));

        TextField champ = robot.lookup("#champRecherche").queryAs(TextField.class);
        VBox panneau = robot.lookup("#panneauResultats").queryAs(VBox.class);
        @SuppressWarnings("unchecked")
        ListView<Object> liste =
                (ListView<Object>) robot.lookup("#listeResultats").queryAs(ListView.class);

        robot.interact(() -> champ.setText("640380"));
        attendreRecherche(); // laisse passer l'anti-rebond (#314 P3)
        assertThat(liste.getItems())
                .as("le site correspondant apparaît dans la liste")
                .isNotEmpty();
        assertThat(panneau.isVisible()).as("la liste déroulante est affichée").isTrue();

        robot.interact(() -> {
            liste.requestFocus();
            liste.getSelectionModel().select(0);
        });
        robot.type(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(navigation.vueCouranteProperty().get())
                .as("la sélection a navigué hors de l'accueil")
                .isNotEqualTo("accueil");
        assertThat(panneau.isVisible()).as("la liste se ferme après navigation").isFalse();
        assertThat(champ.getText()).as("le champ est vidé après navigation").isEmpty();
    }

    @Test
    @DisplayName("#795 : une recherche sans correspondance affiche « Aucun résultat » (liste plus cachée)")
    void recherche_sans_resultat_affiche_placeholder(FxRobot robot) {
        TextField champ = robot.lookup("#champRecherche").queryAs(TextField.class);
        VBox panneau = robot.lookup("#panneauResultats").queryAs(VBox.class);
        ListView<?> liste = robot.lookup("#listeResultats").queryAs(ListView.class);

        robot.interact(() -> champ.setText("zzz-introuvable"));
        attendreRecherche();

        // Auparavant le panneau disparaissait (état vide muet) : il reste désormais visible avec le
        // placeholder « Aucun résultat ». Une recherche vidée, elle, referme bien le panneau.
        assertThat(panneau.isVisible())
                .as("le panneau reste visible pour signaler l'absence de résultat (#795)")
                .isTrue();
        assertThat(liste.getItems()).isEmpty();
        assertThat(liste.getPlaceholder()).isInstanceOf(Label.class);
        assertThat(((Label) liste.getPlaceholder()).getText()).contains("Aucun résultat");

        robot.interact(() -> champ.setText(""));
        attendreRecherche();
        assertThat(panneau.isVisible())
                .as("champ vidé → plus de recherche active → panneau refermé")
                .isFalse();
    }

    @Test
    @DisplayName("#314 P2 : après Échap, Entrée/↓ dans le champ n'agissent plus sur des résultats cachés")
    void echap_invalide_la_navigation_clavier(FxRobot robot) {
        String utilisateur = injector.getInstance(Key.get(String.class, Names.named("idUtilisateurCourant")));
        robot.interact(() -> injector.getInstance(ServiceSites.class)
                .creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, utilisateur));
        TextField champ = robot.lookup("#champRecherche").queryAs(TextField.class);
        VBox panneau = robot.lookup("#panneauResultats").queryAs(VBox.class);
        ListView<?> liste = robot.lookup("#listeResultats").queryAs(ListView.class);

        robot.interact(champ::requestFocus);
        robot.interact(() -> champ.setText("640380"));
        attendreRecherche();
        assertThat(panneau.isVisible()).isTrue();

        // Échap ferme la liste...
        robot.type(KeyCode.ESCAPE);
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(panneau.isVisible()).isFalse();

        // ...et invalide la navigation clavier : Entrée ne navigue pas, ↓ ne déplace pas le focus.
        robot.type(KeyCode.ENTER);
        robot.type(KeyCode.DOWN);
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(navigation.vueCouranteProperty().get())
                .as("Entrée sur une liste fermée ne doit pas naviguer")
                .isEqualTo("accueil");
        assertThat(liste.isFocused())
                .as("↓ sur une liste fermée ne doit pas y déplacer le focus")
                .isFalse();
    }

    /// Laisse passer l'anti-rebond de la recherche (#314 P3) avant d'observer les résultats.
    private static void attendreRecherche() {
        WaitForAsyncUtils.sleep(350, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("Tableau de bord : un compteur à zéro est atténué (classe indicateur-vide) (#141)")
    void compteur_a_zero_est_attenue(FxRobot robot) {
        robot.interact(() -> {
            new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
            injector.getInstance(ServiceSites.class)
                    .creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, "u-1");
        });
        robot.interact(() -> navigateur.afficher(new Group(), "sites", "Mes sites"));
        robot.interact(navigateur::afficherAccueil);

        // Sites = 1, mais Points / Passages / Observations restent à 0 : ces pastilles sont atténuées.
        assertThat(robot.lookup(".indicateur-vide").queryAll()).isNotEmpty();
    }

    /// L'entrée du menu ☰ de ce type, telle que l'injecteur la fournit au chrome.
    ///
    /// C'est **la même instance** que celle que le menu porte : bouchonner ses dialogues sur une
    /// autre ne changerait rien, et le clic ouvrirait un sélecteur natif qui fige le banc.
    private <T extends ActionMenu> T actionDuMenu(Class<T> type) {
        return injector.getInstance(Key.get(new TypeLiteral<Set<ActionMenu>>() {})).stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune entrée de menu de type " + type.getSimpleName()));
    }

    /// L'utilisateur **courant**, auto-créé au démarrage.
    ///
    /// Les écrans filtrent par lui. Semer sous un identifiant à soi remplit bien la base et les
    /// compteurs, mais « Mes sites » reste vide : le site existe et l'écran ne le liste pas.
    private String utilisateurCourant() {
        return injector.getInstance(Key.get(String.class, Names.named("idUtilisateurCourant")));
    }

    /// Sème un carré sous l'utilisateur courant : de quoi remplir le bandeau et peupler « Mes sites ».
    private void semerUnSite() {
        injector.getInstance(ServiceSites.class)
                .creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, utilisateurCourant());
    }

    /// Retire tous les sites : la base redevient vide, et le bandeau doit se retirer avec elle.
    private void viderLesSites() {
        ServiceSites sites = injector.getInstance(ServiceSites.class);
        sites.listerSites(utilisateurCourant()).forEach(site -> sites.supprimerSite(site.id()));
    }

    /// La carte d'accueil portant `intitule`, ou une erreur qui la nomme.
    ///
    /// Le titre d'une carte est un `Text`, non un `Label` : `CartesAccueil` l'a changé en #2046 pour
    /// que l'enroulement soit fiable.
    private static Node carteDAccueil(FxRobot robot, String intitule) {
        return robot.lookup(".carte-activite").queryAll().stream()
                .filter(carte -> carte.lookupAll(".carte-activite-titre").stream()
                        .anyMatch(noeud -> noeud instanceof Text texte && intitule.equals(texte.getText())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune carte d'accueil intitulée « " + intitule + " »"));
    }
}
