package fr.univ_amu.iut.recette;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FournisseurToken;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.connexion.di.ConnexionModule;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.connexion.viewmodel.RefletDuJeton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;

/// Le banc d'un cas de recette, **déclaré** au lieu d'être recopié (#4133).
///
/// ## Pourquoi il existe
///
/// Monter un scénario filmé demande huit gestes toujours identiques - espace de travail jetable,
/// injecteur, migrations, semis, chrome, fenêtre, ouverture - et quatre décisions qui, elles, varient
/// vraiment : la **taille**, l'**exécuteur**, ce qu'on **remplace** dans l'injecteur, et l'écran
/// d'**ouverture**. À la clôture de #4133, ce préambule pesait **494 lignes pour cinquante cas**, et
/// les trois plus lourds coûtaient de quarante-sept à soixante-neuf lignes **pour un seul cas**.
///
/// Écrire un cas neuf revenait donc à recopier le préambule du voisin, et une copie hérite de la dette
/// de son modèle : trois classes sur onze posaient encore leur fenêtre avec un idiome antérieur à
/// [FenetreDuBanc], et deux tailles d'écran circulaient sans raison.
///
/// ## Ce qu'il ne fait pas
///
/// **« Pas de surcharge » ne veut pas dire « synchrone ».** `RacineInjecteur` lie l'exécuteur de
/// PRODUCTION, donc l'asynchrone : une classe qui ne surchargeait rien tournait en asynchrone. La
/// migration de trois classes a posé SYNCHRONE par erreur en lisant « exécuteur par défaut » dans un
/// inventaire, et trois cas ont rougi en « Not on FX application thread ». C'est une raison de plus
/// d'exiger le choix : il ne se devine pas depuis l'absence de surcharge.
///
/// Il ne choisit **pas** l'exécuteur à votre place. Synchrone ou asynchrone est une décision de fond,
/// documentée cas par cas : le synchrone rend les assertions déterministes, l'asynchrone est le seul
/// qui laisse voir un transitoire - une barre de progression, un voile - parce qu'en synchrone le fil
/// JavaFX est bloqué et qu'aucune image n'est rendue pendant ce temps. Le banc **exige** donc qu'on le
/// dise.
///
/// ## Usage
///
/// ```java
/// @Start
/// void start(Stage stage) throws IOException {
///     injecteur = BancDeRecette.surLeChrome()
///             .taille(1180, 900)
///             .executeur(Executeur.ASYNCHRONE)
///             .remplacer(binder -> binder.bind(ClientVigieChiro.class).toInstance(client))
///             .semer(this::seeder)
///             .connecte(ID_USER, "chiro", "observateur")
///             .ouvrir(inj -> inj.getInstance(NavigationSites.class).ouvrirAccueil())
///             .montrer(stage);
/// }
/// ```
public final class BancDeRecette {

    /// Comment les tâches de l'écran s'exécutent, et ce que ce choix décide de ce qu'on peut filmer.
    public enum Executeur {

        /// Le fil JavaFX porte le travail : les assertions sont déterministes, et **aucun transitoire
        /// n'est rendu** - rien ne s'affiche pendant que le fil est occupé.
        SYNCHRONE,

        /// Celui de la production. Le seul qui laisse voir une barre de progression ou un voile, donc
        /// le seul qui permette de filmer le **chemin** vers un résultat plutôt que le résultat seul.
        ASYNCHRONE
    }

    private final List<Module> remplacements = new ArrayList<>();
    private double largeur = 1180;
    private double hauteur = 900;
    private Executeur executeur;
    private Semis semis = injecteur -> {};
    private Consumer<Injector> ouverture = injecteur -> {};
    private ProfilVigieChiro profilConnecte;
    private boolean surLaPlateforme;

    /// Deposer le jeton avant l'ouverture, ou le laisser au scenario.
    ///
    /// SEPARE de [#surLaPlateforme] depuis le tir 32894626486, et c'est le defaut qu'il a
    /// trouve : les deux voies vers la plateforme reelle ne different que par le depot, mais un
    /// seul drapeau portait les deux sens. La voie qui ne depose rien ne levait donc pas le
    /// cablage, et son client repartait sur `http://localhost:1` - un vrai jeton, une vraie URL
    /// dans l'environnement, et un `ConnectException` sur le port 1.
    private boolean deposerLeJeton;

    /// Ce qu'un scénario écrit avant que l'écran ne s'ouvre.
    ///
    /// Il peut **lever** : un semis qui pose des fichiers de nuit fait des entrées/sorties, et un
    /// `Consumer` l'aurait obligé à emballer son exception dans une non vérifiée - c'est-à-dire à cacher
    /// ce que le banc doit laisser remonter. Constaté à la migration de `ScenarioPerceptifRefusDepotTest`.
    @FunctionalInterface
    public interface Semis {
        void semer(Injector injecteur) throws IOException;
    }

    private BancDeRecette() {}

    /// Un banc qui monte le **chrome réel** (`MainView.fxml`), seul cadre où un cas montre aussi le
    /// chemin qui mène à l'écran.
    public static BancDeRecette surLeChrome() {
        return new BancDeRecette();
    }

    /// Taille de la fenêtre. Par défaut 1180 × 900, la taille des scénarios de la session 1.
    public BancDeRecette taille(double largeur, double hauteur) {
        this.largeur = largeur;
        this.hauteur = hauteur;
        return this;
    }

    public BancDeRecette executeur(Executeur executeur) {
        this.executeur = Objects.requireNonNull(executeur, "executeur");
        return this;
    }

    /// Ce que ce scénario remplace dans l'injecteur du produit - en pratique la **frontière réseau**,
    /// et elle seule. Tout le reste du câblage reste celui de l'application.
    public BancDeRecette remplacer(Module... modules) {
        remplacements.addAll(List.of(modules));
        return this;
    }

    /// Les données que ce scénario suppose, écrites après les migrations.
    ///
    /// Le semis reçoit **l'injecteur en paramètre**, et doit s'en servir : le champ que
    /// [#montrer(Stage)] rendra n'est pas encore affecté quand le semis tourne. Une méthode de semis
    /// migrée telle quelle, qui lisait un champ `injector`, part en `NullPointerException` - c'est
    /// arrivé à la première classe migrée.
    public BancDeRecette semer(Semis semis) {
        this.semis = Objects.requireNonNull(semis, "semis");
        return this;
    }

    /// Enregistre une connexion, **comme la modale de connexion le ferait**.
    ///
    /// Posée AVANT le chargement du chrome : c'est à la construction du menu que
    /// `NavigationConnexion.libelleMenu()` est lu, et une connexion posée après laisserait l'entrée
    /// afficher « Se connecter à Vigie-Chiro… » pendant que la scène joue un utilisateur connecté.
    /// **Factice veut dire factice**, y compris pendant un tournage connecté : le banc lie sa propre
    /// source de jeton et ignore celui du processus (cf. [#montrer(Stage)]).
    public BancDeRecette connecte(String id, String pseudo, String role) {
        if (surLaPlateforme) {
            throw new IllegalStateException(exclusion());
        }
        this.profilConnecte = new ProfilVigieChiro(id, pseudo, role);
        return this;
    }

    /// Enregistre le jeton **réel** du tournage connecté, sans profil, et **sans le faire paraître**.
    ///
    /// ## Pourquoi sans profil
    ///
    /// `ConnexionViewModel.jetonAVerifier()` rend le jeton enregistré tant que le profil est vide
    /// (#1369), et la modale le **revérifie à son ouverture, sans geste**, progression comprise. C'est
    /// ce qui permet de filmer la connexion à la plateforme réelle sans jamais coller un caractère dans
    /// le champ - lequel est un `TextField` et non un `PasswordField`, donc lisible sur le clip.
    ///
    /// ## Pourquoi il refuse plutôt que de se dégrader
    ///
    /// Sans jeton, ce banc filmerait un écran hors ligne parfaitement convaincant, et le cas serait
    /// **muet sur son propre objet** (ADR 4142). Un scénario qui déclare vouloir la plateforme et ne la
    /// trouve pas n'a rien à montrer : il s'arrête, et il dit quoi poser.
    public BancDeRecette connecteALaPlateforme() {
        if (profilConnecte != null) {
            throw new IllegalStateException(exclusion());
        }
        this.surLaPlateforme = true;
        this.deposerLeJeton = true;
        return this;
    }

    /// Garde le cablage de production **sans rien deposer** : le scenario collera le jeton lui-meme,
    /// avec [#jetonDeLaPlateforme()].
    ///
    /// Prendre le jeton ne suffit pas a dire au banc vers QUI parler : depuis #4332 le banc lie un client
    /// hors ligne a tout scenario n'ayant declare aucun serveur, et cette voie-la ne levait rien. Elle
    /// filmait donc l'ecran hors ligne que [#connecteALaPlateforme()] refuse de produire.
    ///
    /// `BancDeRecetteSansDepotTest` porte la mesure, et `DeclarationDeLaPlateformeTest` exige la
    /// declaration de tout scenario connecte.
    public BancDeRecette parleALaPlateforme() {
        if (profilConnecte != null) {
            throw new IllegalStateException(exclusion());
        }
        this.surLaPlateforme = true;
        return this;
    }

    private static String exclusion() {
        return "Un banc est connecté en factice ou à la plateforme réelle, jamais les deux : le premier"
                + " montre un écran, le second éprouve une frontière. Choisir lequel des deux ce"
                + " scénario est.";
    }

    /// Le jeton du tournage connecté, **rendu au scénario** au lieu d'être déposé pour lui.
    ///
    /// ## Pourquoi les deux voies existent
    ///
    /// [#connecteALaPlateforme()] dépose le jeton et laisse la modale le revérifier seule : c'est ce
    /// qu'il faut à un scénario qui doit **partir connecté**, sans refaire la connexion à chaque cas.
    ///
    /// Celle-ci ne dépose rien. Elle sert au scénario qui filme la connexion **comme un utilisateur la
    /// fait** : coller le jeton dans le champ, puis cliquer. C'est ce que font déjà les scénarios
    /// bouchonnés, et s'en écarter rendait leur pendant connecté illisible - une modale qui se connecte
    /// toute seule ne montre pas ce qui l'a connectée.
    ///
    /// Le jeton **paraît donc à l'écran**, et le clip est publié. C'est assumé : il est révoqué en
    /// fin de run (#4305), et un jeton mort n'est pas un secret. La publication n'a d'ailleurs lieu que
    /// si la révocation a **confirmé** son retrait, sans quoi l'hypothèse « il est mort » ne serait
    /// qu'un espoir.
    ///
    /// Refuse, comme l'autre voie, plutôt que de rendre vide : un scénario qui déclare vouloir la
    /// plateforme et ne la trouve pas n'a rien à montrer.
    public static String jetonDeLaPlateforme() {
        return ConnexionModule.jetonPonctuel().orElseThrow(() -> new IllegalStateException(SANS_JETON));
    }

    /// L URL d un banc qui n a declare aucun serveur : le port 1 ne repond jamais, donc toute reponse
    /// devient `Injoignable`. C est l idiome hors-ligne deja employe par `CapturePassage` et
    /// `CaptureValidationTadarida`.
    private static final String URL_HORS_LIGNE = "http://localhost:1";

    private static final String SANS_JETON =
            "Ce scénario a déclaré vouloir la plateforme réelle et aucun jeton n'est là. Poser"
                    + " VIGIECHIRO_TOKEN dans l'env du PAS qui filme, jamais dans celui du job. Sans"
                    + " jeton, ce banc filmerait un écran hors ligne convaincant et muet sur son propre"
                    + " objet.";

    /// L'écran par lequel le scénario commence.
    public BancDeRecette ouvrir(Consumer<Injector> ouverture) {
        this.ouverture = Objects.requireNonNull(ouverture, "ouverture");
        return this;
    }

    /// Monte tout et affiche la fenêtre. Rend l'injecteur, dont le scénario aura besoin.
    public Injector montrer(Stage stage) throws IOException {
        if (executeur == null) {
            throw new IllegalStateException("Le banc exige un exécuteur : SYNCHRONE rend les assertions"
                    + " déterministes, ASYNCHRONE est le seul qui laisse voir un transitoire. Ce choix"
                    + " décide de ce que le clip peut montrer, et ne se prend pas par défaut.");
        }
        Path espace = Files.createTempDirectory("vc-banc");
        System.setProperty("vigiechiro.workspace", espace.toString());

        List<Module> surcharges = new ArrayList<>();
        surcharges.add(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ExecuteurTache.class)
                        .to(
                                executeur == Executeur.SYNCHRONE
                                        ? ExecuteurTacheSynchrone.class
                                        : ExecuteurTacheAsynchrone.class)
                        .in(Singleton.class);
            }
        });
        // Le banc LIE SA PROPRE SOURCE DE JETON, et c'est la figure de l'ADR 4134 d'un cran plus
        // haut : là c'était la fenêtre primaire de TestFX, ici c'est l'environnement du processus.
        //
        // `ConnexionModule` lie `() -> jetonPonctuel().or(stockage::token)`, où le jeton ponctuel -
        // propriété `vigiechiro.token`, sinon variable `VIGIECHIRO_TOKEN` - L'EMPORTE. C'est juste pour
        // la CLI, à qui `--token` sert précisément à passer outre. Ce ne l'est pas ici : un scénario qui
        // a demandé une connexion FACTICE parlerait à la plateforme réelle dès que le pas qui filme
        // porte le secret, avec un écran gardant l'apparence du profil semé et rien pour le signaler.
        //
        // Le banc lit donc SA réserve, et elle seule. Ce qu'il y met est la seule différence entre les
        // deux modes ci-dessous.
        surcharges.add(new AbstractModule() {
            @Provides
            @Singleton
            FournisseurToken sourceDuBanc(StockageConnexion reserve) {
                return reserve::token;
            }
        });

        // Et il lie AUSSI son client, pour la même raison un champ plus loin (#4332).
        //
        // `ConnexionModule#fournirClient` prend son URL dans `vigiechiro.url`, sinon `VIGIECHIRO_URL`.
        // Un scénario qui ne remplace pas son client - huit des treize, mesuré - parlait donc à ce que
        // l'environnement désignait : le bouchon qu'un `cli-reseau.bats` a laissé exporté, ou la
        // production. Le banc pose sa propre valeur, et `http://localhost:1` est l'idiome hors-ligne
        // déjà employé par les outils de capture : les réponses deviennent `Injoignable`, ce qui est le
        // comportement juste d'un scénario qui n'a déclaré aucun serveur.
        //
        // SAUF pour un scénario connecté, qui garde le câblage de production - c'est-à-dire l'URL
        // ambiante, qu'il a précisément demandée. Ne rien lier est ici plus juste que lier la valeur par
        // défaut : le tournage connecté déclare `VIGIECHIRO_URL`, et un jour un serveur de recette.
        if (!surLaPlateforme) {
            surcharges.add(new AbstractModule() {
                @Provides
                @Singleton
                ClientVigieChiro clientDuBanc(FournisseurToken jetons) {
                    return new ClientVigieChiro(URL_HORS_LIGNE, jetons);
                }
            });
        }

        // DEUX surcharges emboîtées, et non une liste à plat : le banc surcharge la production, puis
        // le scénario surcharge le banc. À plat, les cinq scénarios qui lient déjà `ClientVigieChiro`
        // entreraient en collision avec la liaison ci-dessus, et Guice refuserait le doublon.
        Module socleDuBanc = Modules.override(RacineInjecteur.modules()).with(surcharges);
        Injector injecteur = Guice.createInjector(Modules.override(socleDuBanc).with(remplacements));

        new MigrationSchema(injecteur.getInstance(SourceDeDonnees.class)).migrer();
        semis.semer(injecteur);
        if (profilConnecte != null) {
            injecteur.getInstance(StockageConnexion.class).enregistrer("jeton-de-recette", profilConnecte);
            injecteur.getInstance(RefletDuJeton.class).relire();
        } else if (deposerLeJeton) {
            // Sans profil : la modale le revérifiera d'elle-même à l'ouverture (#1369), ce qui filme la
            // connexion réelle sans qu'un caractère du jeton passe par le champ.
            String jeton = jetonDeLaPlateforme();
            injecteur.getInstance(StockageConnexion.class).enregistrer(jeton, null);
            injecteur.getInstance(RefletDuJeton.class).relire();
        }

        FXMLLoader chargeur = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        chargeur.setControllerFactory(injecteur::getInstance);
        Parent racine = chargeur.load();
        FenetreDuBanc.poser(stage, racine, largeur, hauteur);
        ouverture.accept(injecteur);
        FenetreDuBanc.afficher(stage);
        return injecteur;
    }
}
