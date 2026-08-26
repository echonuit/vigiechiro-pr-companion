package fr.univ_amu.iut.connexion.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.SuiviProgression;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.recette.film.EnregistreurDeFilm;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// La connexion à la **vraie** plateforme, filmée comme un utilisateur la fait (#4324, chantier #4291).
///
/// ## Un seul clip, parce que c'est un seul geste
///
/// `S8-01`, `S8-05` et `S8-06` sont trois constats d'un **même parcours** : coller le jeton, voir
/// l'avancement, lire l'identité. Trois clips en referaient trois fois le préambule et couperaient
/// l'histoire en trois ; un seul la raconte.
///
/// ## Quatre temps, dont trois que l'ADR 4188 exige
///
/// Une modale se filme avec l'écran d'où elle part et celui où elle rend
/// ([ADR 4188](../../../../../../dev-docs/decisions/4188-une-modale-se-filme-avec-son-ecran.md)). Ici
/// c'est **le même écran**, l'accueil - mais il n'y revient pas identique.
///
/// | Temps | Ce que le clip montre |
/// |---|---|
/// | 1 · l'écran de départ | l'accueil **sans** bandeau de compteurs, et le geste qui ouvre la modale |
/// | 2 · la modale | le jeton collé, l'avancement, « Fermer » grisé, puis l'identité et le résumé |
/// | 3 · l'écran d'arrivée | l'accueil retrouvé, où le **bandeau de compteurs a paru** (#1376) |
/// | 4 · le menu rouvert | l'entrée qui nomme désormais qui est connecté |
///
/// Le quatrième n'est pas exigé par l'ADR : c'est une **confirmation de plus** que la connexion a eu
/// lieu, et ce qui permet de voir ses conséquences de bout en bout, sur les **deux** surfaces qu'elle
/// change.
///
/// ## Le jeton PARAÎT à l'écran, et c'est délibéré
///
/// La première version évitait la saisie pour qu'aucun jeton ne soit filmé : elle déposait le jeton en
/// coulisse et laissait la modale se vérifier seule. Le clip montrait alors une modale **qui se
/// connectait toute seule**, sans qu'on voie ce qui l'avait connectée.
///
/// C'était de la surqualité, et elle coûtait le clip. Le jeton est **révoqué en fin de run** (#4305,
/// vérifié deux fois en production : `POST /logout` rend `200`), donc ce que le clip montre ne vaut
/// plus rien avant même d'être regardé. **Un jeton mort n'est pas un secret ; un clip incompréhensible
/// est un problème.**
///
/// Et ce n'est pas une hypothèse laissée en l'air : le versement sur `clips-connectes` n'a lieu que si
/// la révocation a **confirmé** le retrait. Sans confirmation, le clip n'est pas publié.
///
/// C'est aussi l'idiome des scénarios bouchonnés voisins - `ScenarioPerceptifConnexionTest` et
/// `ScenarioPerceptifIssuesConnexionTest` collent tous deux leur jeton à l'écran. S'en écarter faisait
/// diverger le pendant connecté de ses jumeaux pour rien.
///
/// ## Ce que le premier tir a appris, et qui tient toujours
///
/// **Une attente satisfaite avant que l'opération commence.** Attendre que la progression *disparaisse*
/// est vrai à `t=0`. On attend son **apparition puis** sa disparition, dans cet ordre.
///
/// **Une assertion que rien ne pouvait faire rougir.** `ConnexionViewModel` pose « Jeton enregistré,
/// non vérifié » dès qu'un jeton est déposé sans profil : un `isNotBlank()` passait réseau débranché.
/// On asserte ce que le produit **réserve** au succès - la classe `badge-succes`, et le bandeau
/// « Connexion réussie ».
///
/// **Une caméra qui s'arrêtait sur le geste.** Sur 35 images, 34 sans modale. Le scénario **tient donc
/// l'écran** après chaque assertion, par [Respiration], qui ne coûte rien hors séance filmée.
///
/// ## Exclu du build par défaut
///
/// `@Tag("recette-connectee")`, exclu par `surefire.excludedGroups`. Sans jeton le banc **refuse** de
/// monter, comme il doit. Le tournage connecté inverse les **deux** propriétés : poser `groups` seul ne
/// lève pas l'exclusion, et le premier tir l'a appris en rendant « Tests run: 0 » sur un build vert.
@Tag("recette-connectee")
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioConnecteConnexionTest {

    /// Même cadrage que les scénarios de connexion bouchonnés : la fenêtre reste plus petite que
    /// l'écran du banc, sans quoi la modale atterrit en (0,0) et sa barre de titre sort du champ.
    private static final int LARGEUR = 1100;

    private static final int HAUTEUR = 720;

    private static final String LIBELLE_ENTREE_MENU = "Se connecter à Vigie-Chiro…";

    /// Ce que devient cette même entrée une fois connecté : `NavigationConnexion#libelleMenu` rend
    /// « Vigie-Chiro : &lt;pseudo&gt; (&lt;rôle&gt;) ».
    ///
    /// Le **préfixe seul** : le pseudo dépend du compte, et un test qui l'épinglerait rougirait au
    /// prochain compte de tournage pour une raison qui n'est pas le produit.
    private static final String PREFIXE_ENTREE_CONNECTEE = "Vigie-Chiro : ";

    /// Ce que le produit met sur le badge d'identité **quand la plateforme a répondu**, et lui seul.
    /// L'état initial porte `badge-neutre` : c'est ce qui distingue un succès d'un jeton simplement
    /// enregistré.
    private static final String BADGE_CONNECTE = "badge-succes";

    /// La progression **paraît** vite : c'est le premier aller-retour réseau. Une attente courte suffit,
    /// et une attente longue masquerait un écran qui ne s'ouvre pas.
    private static final int APPARITION_SECONDES = 60;

    /// La **fin**, elle, est bien plus lente que ce qu'un bouchon laissait croire. Mesuré au premier
    /// tir : à **23 secondes**, les rapprocheurs tournaient encore - se connecter rejoue le
    /// rapatriement des nuits du compte (#2557), donc la durée suit la taille du compte.
    ///
    /// Si ce butoir est atteint, la conclusion n'est **pas** « le produit est cassé » mais « le compte
    /// de tournage est plus gros que ce banc ne le prévoit ». Le message le dit.
    private static final int FIN_SECONDES = 240;

    @Start
    void start(Stage stage) throws IOException {
        BancDeRecette.surLeChrome()
                .taille(LARGEUR, HAUTEUR)
                // ASYNCHRONE : la progression est le sujet, et en synchrone le fil JavaFX est bloqué,
                // donc aucune image n'est rendue pendant l'opération.
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                // Ni `connecte(...)` ni `connecteALaPlateforme()` : ce scénario part DÉCONNECTÉ,
                // parce que c'est la connexion elle-même qu'il filme. Le banc lie quand même sa propre
                // source de jeton, donc rien de l'environnement ne s'invite dans la réserve (ADR 4134).
                //
                // Mais il DÉCLARE parler à la plateforme, sinon son client repart sur
                // `http://localhost:1` : prendre le jeton ne dit pas au banc vers qui parler. Trois
                // tournages ont filmé un écran hors ligne avant que le relevé ne le dise.
                .parleALaPlateforme()
                .remplacer(new AbstractModule() {
                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        // Rien à ouvrir sur la machine qui filme, et rien à voir sur le clip.
                        return lien -> {};
                    }
                })
                .montrer(stage);
    }

    @Test
    @CasDeRecette(
            value = {"S8-01", "S8-05", "S8-06"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S8-01, S8-05, S8-06 · coller le jeton, voir l'avancement, lire l'identité rendue")
    void coller_le_jeton_puis_lire_l_identite(FxRobot robot) throws TimeoutException {
        // Lu AVANT d'ouvrir quoi que ce soit : sans jeton, ce scénario n'a rien à montrer, et il
        // vaut mieux qu'il le dise avant d'avoir filmé huit secondes d'écran inutile.
        String jeton = BancDeRecette.jetonDeLaPlateforme();

        Respiration.avantLeGeste(robot);

        // ─── Temps 1 · l'écran de départ (ADR 4188) ───────────────────────────────────────────────
        // Constaté AVANT, pour que l'après veuille dire quelque chose. Le banc part d'un espace de
        // travail vierge : `AccueilViewModel.aDesDonnees()` est faux, et le bandeau de compteurs est
        // donc masqué. C'est ce contraste que le clip doit rendre lisible.
        assertThat(visible(robot, "#bandeauIndicateurs"))
                .as("l'accueil part SANS compteurs : sur un espace de travail vierge il n'y a rien à"
                        + " compter. Si le bandeau était déjà là, l'apparition qui suit ne prouverait rien")
                .isFalse();

        // `GesteVisible.choisir` plutôt qu'un `clickOn` nu : le banc rend le graphe de scène, où le
        // pointeur n'existe pas. C'est lui qui dessine le halo de l'appui, sans quoi la modale
        // s'ouvrirait sans qu'on voie ce qui l'a ouverte (ADR 4248).
        GesteVisible.choisir(robot, "#menuOutils", LIBELLE_ENTREE_MENU);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.leTempsDeLire(robot);

        // ─── S8-01 · coller le jeton ──────────────────────────────────────────────────────────────
        assertThat(texte(robot, "#champToken"))
                .as("le parcours part d'un champ VIDE : c'est l'état que la case décrit, et celui qui"
                        + " rend la suite lisible")
                .isEmpty();

        robot.clickOn("#champToken").write(jeton);
        WaitForAsyncUtils.waitForFxEvents();
        // Le jeton doit rester à l'écran assez longtemps pour qu'on voie D'OÙ vient la connexion.
        Respiration.leTempsDeLire(robot);

        assertThat(texte(robot, "#champToken"))
                .as("le jeton collé doit être dans le champ : c'est ce que la case demande de constater,"
                        + " et sans lui le clic suivant n'aurait aucune cause visible")
                .isEqualTo(jeton);

        GesteVisible.cliquer(robot, "#boutonConnecter");
        WaitForAsyncUtils.waitForFxEvents();

        // ─── S8-05 · l'avancement paraît DANS la modale ───────────────────────────────────────────
        attendre(
                APPARITION_SECONDES,
                () -> visible(robot, "#zoneProgression"),
                "la progression n'a jamais paru dans la modale");

        // On asserte AVANT de tenir l'écran. L'état est celui de l'instant où la progression paraît,
        // donc déterministe ; le maintien qui suit sert la caméra, pas l'assertion.
        assertThat(fenetresOuvertes())
                .as("l'avancement doit paraître DANS la modale : une seconde fenêtre par-dessus"
                        + " montrerait deux fenêtres pour un seul geste, et aucun clip n'en rendrait compte")
                .isLessThanOrEqualTo(2);
        assertThat(grise(robot, "#boutonFermer"))
                .as("« Fermer » reste grisé tant que l'opération tourne : la fermer en cours laisserait"
                        + " un jeton à moitié vérifié et une modale qu'on croit close")
                .isTrue();

        Respiration.surLeMomentCle(robot);
        Respiration.leTempsDeLire(robot);

        // ─── S8-06 · l'identité et le résumé, à la fin ────────────────────────────────────────────
        attendre(
                FIN_SECONDES,
                () -> !visible(robot, "#zoneProgression"),
                "l'opération n'a pas fini dans le temps imparti. À lire comme « le compte de tournage est"
                        + " plus gros que ce banc ne le prévoit », pas comme un défaut du produit :"
                        + " se connecter rejoue le rapatriement des nuits du compte");
        WaitForAsyncUtils.waitForFxEvents();

        // Ce que la synchro a fait, imprime plutot que suppose. Le resume ne porte que des
        // COMPTES, donc rien d'identifiant : publiable dans un journal de run, et c'est la seule
        // trace qui explique pourquoi une connexion a ete longue ou breve.
        System.out.printf("  synchro de la connexion : %s%n", texte(robot, "#bandeauStatut"));

        // On asserte le SUCCÈS, pas la non-vacuité : `identiteProperty` porte « Jeton enregistré, non
        // vérifié » dès le premier instant d'un jeton sans profil.
        assertThat(classes(robot, "#labelIdentite"))
                .as(
                        "le badge d'identité passe à « %s » quand la plateforme a répondu, et reste"
                                + " « badge-neutre » sinon. C'est le seul signal que l'état initial ne porte pas",
                        BADGE_CONNECTE)
                .contains(BADGE_CONNECTE);
        assertThat(texte(robot, "#labelIdentite"))
                .as("le badge doit nommer QUI est connecté, pas rappeler qu'un jeton attend d'être vérifié")
                .isNotBlank()
                .doesNotContain("non vérifié");
        assertThat(texte(robot, "#bandeauStatut"))
                .as("la case demande l'identité ET le résumé. Le bandeau les annonce ensemble :"
                        + " « Connexion réussie · référentiel à jour : … ». Le CONTENU du résumé dépend du"
                        + " compte, donc seule son annonce est assertée ici")
                .startsWith("Connexion réussie");
        assertThat(texte(robot, "#champToken"))
                .as("le champ se vide à la connexion réussie : le jeton n'a pas à rester affiché une fois"
                        + " qu'il a servi")
                .isEmpty();

        Respiration.leTempsDeLire(robot);

        // ─── Temps 3 · l'écran d'arrivée, et ce qui y a changé (ADR 4188) ────────────────────────
        // Une modale se filme avec l'écran d'où elle part ET celui où elle rend. Ici c'est le même,
        // l'accueil - mais il n'y revient pas identique : `MainController` pose que « le bandeau suit
        // désormais la DONNÉE (#1376) », et qu'« une synchronisation déroulée par-dessus l'accueil se
        // voit sans qu'on ait navigué ». Les rapprocheurs de la connexion en sont une.
        GesteVisible.cliquer(robot, "#boutonFermer");
        WaitForAsyncUtils.waitForFxEvents();

        attendre(
                APPARITION_SECONDES,
                () -> visible(robot, "#bandeauIndicateurs"),
                "le bandeau de compteurs n'est jamais apparu sur l'accueil. La connexion a pourtant"
                        + " synchronisé sites et taxons : si rien ne paraît, c'est le bandeau qui ne suit"
                        + " pas la donnée, et non ce cas qui se trompe");
        Respiration.leTempsDeLire(robot);

        assertThat(pastilles(robot))
                .as("le bandeau doit porter au moins un compteur : il paraît parce qu'il y a désormais"
                        + " quelque chose à compter. Un bandeau visible et VIDE serait un cadre sans"
                        + " contenu, ce qui est pire qu'un bandeau masqué")
                .isNotEmpty();

        // ─── Temps 4 · ce que le menu dit maintenant ──────────────────────────────────────────────
        // Un QUATRIÈME temps, et non une extension du troisième : l'ADR 4188 est déjà satisfaite par
        // l'accueil retrouvé et tenu. Celui-ci est une confirmation DE PLUS que la connexion a bien eu
        // lieu, et il complète le clip - on y voit alors les conséquences d'une connexion de bout en
        // bout, sur les deux surfaces qu'elle change : le bandeau de l'accueil et l'entrée de menu.
        // Les libellés du menu sont réévalués à chaque ouverture (`ConstructeurMenuOutils.setOnShowing`).
        GesteVisible.cliquer(robot, "#menuOutils");
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.leTempsDeLire(robot);

        assertThat(libelleAffiche(robot, PREFIXE_ENTREE_CONNECTEE))
                .as(
                        "l'entrée de menu disait « %s » au départ ; connecté, elle doit nommer l'identité"
                                + " (« %s… »). C'est le second effet visible de la connexion, et il ne se"
                                + " constate qu'en rouvrant le menu",
                        LIBELLE_ENTREE_MENU, PREFIXE_ENTREE_CONNECTEE)
                .isPresent();

        robot.press(KeyCode.ESCAPE).release(KeyCode.ESCAPE);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.apresLeGeste(robot);
    }

    // --------------------------------------------------------------------------------------------

    /// Une attente qui **dit ce qu'elle attendait** quand elle échoue. `WaitForAsyncUtils` rend sinon un
    /// `TimeoutException` nu, et le lecteur d'un tournage raté n'a que la ligne pour comprendre.

    @Test
    @CasDeRecette(
            value = {"S8-02", "S8-03"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S8-02, S8-03 · la barre avance nuit par nuit, et annonce le temps restant")
    void la_barre_avance_nuit_par_nuit(FxRobot robot) throws TimeoutException {
        String jeton = BancDeRecette.jetonDeLaPlateforme();

        Respiration.avantLeGeste(robot);
        GesteVisible.choisir(robot, "#menuOutils", LIBELLE_ENTREE_MENU);
        WaitForAsyncUtils.waitForFxEvents();

        robot.clickOn("#champToken").write(jeton);
        WaitForAsyncUtils.waitForFxEvents();
        GesteVisible.cliquer(robot, "#boutonConnecter");
        WaitForAsyncUtils.waitForFxEvents();

        attendre(
                APPARITION_SECONDES,
                () -> visible(robot, "#zoneProgression"),
                "la progression n'a jamais paru dans la modale");

        // Le premier relevé, pris au plus tôt : il sert de point de comparaison.
        double fractionInitiale = fraction(robot);
        // Releve AU PREMIER INSTANT, et garde. Le prendre plus tard ne dit rien : sur une
        // connexion breve la zone a deja disparu, et le releve rend une chaine vide.
        String avancementInitial = texte(robot, "#" + SuiviProgression.ID_MESSAGE);

        Respiration.surLeMomentCle(robot);
        Respiration.leTempsDeLire(robot);

        // La PRÉCONDITION de ce geste, déclarée plutôt que supposée. Se connecter rejoue le
        // rapatriement des nuits du compte (#2557), donc la durée suit ce qui RESTE à récupérer -
        // une participation sans passage local, ou un passage réduit à un squelette
        // (`ServiceReconstructionPassages#aReconstruire`). Sur un compte à jour, il n'y a ni barre qui
        // avance ni temps à estimer : le geste n'a pas lieu, et on le DIT au lieu de rougir.
        //
        // L'enregistreur n'indexera aucun cas : un clip qui ne montre pas son geste ne doit pas passer
        // pour une couverture.

        // Imprime AVANT la precondition : c'est justement quand le geste est ABANDONNE qu'on veut
        // savoir pourquoi. L'avancement au moment du releve dit combien de nuits restaient.
        System.out.printf("  avancement au premier instant : %s%n", avancementInitial);

        Assumptions.assumeTrue(
                visible(robot, "#zoneProgression"),
                "Le compte de tournage n'a plus de nuit à rapatrier : la connexion est brève, et le geste"
                        + " « rapatrier les nuits du compte » n'a pas eu lieu. Ce n'est PAS un défaut du"
                        + " produit. Pour le rejouer, il faut une participation sans passage local, ou un"
                        + " passage réduit à un squelette.");

        // ─── S8-02 · la barre AVANCE, et son libellé nomme la nuit ───────────────────────────────
        // « Il ne reste pas figé » ne se constate pas sur UN instantané : une barre arrêtée et une
        // barre qui progresse s'y ressemblent. On compare donc DEUX relevés.
        attendre(
                APPARITION_SECONDES,
                () -> fraction(robot) > fractionInitiale,
                "la barre n'a pas bougé entre deux relevés : elle est restée figée à sa valeur"
                        + " d'ouverture, ce que la case S8-02 interdit explicitement");

        assertThat(texte(robot, "#" + SuiviProgression.ID_MESSAGE))
                .as("le libellé doit NOMMER la nuit en cours, sous la forme « Nuits k / N » que"
                        + " SuiviTraitement compose. Un libellé simplement non vide ne dirait pas où en"
                        + " est l'opération, et c'est ce que la case demande de constater")
                .containsPattern("Nuits\\s+\\d+\\s*/\\s*\\d+");

        // ─── S8-03 · l'estimation du temps restant ───────────────────────────────────────────────
        // « une fois l'avancement mesurable » : ProgressionOperation extrapole le restant depuis le
        // temps écoulé, donc elle ne peut rien annoncer au premier instant.
        attendre(
                APPARITION_SECONDES,
                () -> texte(robot, "#" + SuiviProgression.ID_MESSAGE).contains("restant"),
                "aucune estimation du temps restant n'a paru dans le libellé d'avancement. Elle"
                        + " s'extrapole du temps écoulé : si elle manque, c'est que l'opération n'a jamais"
                        + " été mesurable, et la case S8-03 n'a rien à montrer");

        assertThat(texte(robot, "#" + SuiviProgression.ID_MESSAGE))
                .as("l'estimation s'ajoute au libellé sous la forme « … · ~X s restant » :"
                        + " ProgressionOperation la compose ainsi, et c'est ce que le spectateur lit")
                .contains("restant")
                .containsPattern("~\\s*\\d+");

        Respiration.leTempsDeLire(robot);

        // Le geste se termine où l'opération se termine : on laisse la barre finir plutôt que de
        // couper le clip au milieu.
        attendre(
                FIN_SECONDES,
                () -> !visible(robot, "#zoneProgression"),
                "l'opération n'a pas fini dans le temps imparti. À lire comme « le compte de tournage est"
                        + " plus gros que ce banc ne le prévoit », pas comme un défaut du produit");
        WaitForAsyncUtils.waitForFxEvents();

        Respiration.leTempsDeLire(robot);
    }

    private static void attendre(int secondes, Callable<Boolean> condition, String quoi) throws TimeoutException {
        try {
            WaitForAsyncUtils.waitFor(secondes, TimeUnit.SECONDS, condition);
        } catch (TimeoutException _) {
            throw new TimeoutException(quoi + " (au bout de " + secondes + " s)");
        }
    }

    /// L'avancement de la barre, ou -1 si elle n'est pas là.
    ///
    /// -1 plutôt que 0 : une barre absente et une barre à zéro ne sont pas le même fait, et les
    /// confondre ferait passer « la progression a disparu » pour « elle n'a pas encore commencé ».
    private static double fraction(FxRobot robot) {
        Node noeud = robot.lookup("#" + SuiviProgression.ID_BARRE).tryQuery().orElse(null);
        return noeud instanceof ProgressBar barre ? barre.getProgress() : -1;
    }

    private static boolean visible(FxRobot robot, String selecteur) {
        Node noeud = robot.lookup(selecteur).tryQuery().orElse(null);
        return noeud != null && noeud.isVisible();
    }

    private static boolean grise(FxRobot robot, String selecteur) {
        Node noeud = robot.lookup(selecteur).tryQuery().orElse(null);
        return noeud instanceof Button bouton && bouton.isDisabled();
    }

    /// Le texte d'un nœud, qu'il soit un libellé ou une saisie : le champ du jeton est un
    /// `TextInputControl`, pas un `Labeled`, et le confondre rendrait « vide » un champ rempli.
    private static String texte(FxRobot robot, String selecteur) {
        Node noeud = robot.lookup(selecteur).tryQuery().orElse(null);
        if (noeud instanceof TextInputControl saisie) {
            return saisie.getText() == null ? "" : saisie.getText();
        }
        return noeud instanceof Labeled libelle && libelle.getText() != null ? libelle.getText() : "";
    }

    /// Le premier libellé **rendu** qui commence par `prefixe`, s'il y en a un.
    ///
    /// Une entrée de menu n'est pas un `Node` : c'est le menu ouvert qui en rend un. On cherche
    /// donc dans la scène ce qui s'AFFICHE, et non dans le modèle ce qui devrait s'afficher - c'est
    /// la différence entre « le produit le sait » et « on le voit », et l'ADR 4188 porte sur la
    /// seconde.
    private static java.util.Optional<String> libelleAffiche(FxRobot robot, String prefixe) {
        return robot.lookup((Node noeud) -> noeud instanceof Labeled libelle
                        && libelle.getText() != null
                        && libelle.getText().startsWith(prefixe))
                .tryQuery()
                .map(noeud -> ((Labeled) noeud).getText());
    }

    /// Les compteurs **rendus** dans le bandeau de l'accueil. `MainController#peuplerIndicateurs`
    /// y pose une pastille par compteur, et vide le conteneur quand il n'y a rien à compter.
    private static List<Node> pastilles(FxRobot robot) {
        Node bandeau = robot.lookup("#bandeauIndicateurs").tryQuery().orElse(null);
        return bandeau instanceof Parent conteneur ? List.copyOf(conteneur.getChildrenUnmodifiable()) : List.of();
    }

    private static List<String> classes(FxRobot robot, String selecteur) {
        Node noeud = robot.lookup(selecteur).tryQuery().orElse(null);
        return noeud == null ? List.of() : List.copyOf(noeud.getStyleClass());
    }

    private static long fenetresOuvertes() {
        return Window.getWindows().stream().filter(Window::isShowing).count();
    }
}
