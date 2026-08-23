package fr.univ_amu.iut.recette;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
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
/// ⚠️ **« Pas de surcharge » ne veut pas dire « synchrone ».** `RacineInjecteur` lie l'exécuteur de
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

    /// Ce qu'un scénario écrit avant que l'écran ne s'ouvre.
    ///
    /// ⚠️ Il peut **lever** : un semis qui pose des fichiers de nuit fait des entrées/sorties, et un
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
    /// ⚠️ Le semis reçoit **l'injecteur en paramètre**, et doit s'en servir : le champ que
    /// [#montrer(Stage)] rendra n'est pas encore affecté quand le semis tourne. Une méthode de semis
    /// migrée telle quelle, qui lisait un champ `injector`, part en `NullPointerException` - c'est
    /// arrivé à la première classe migrée.
    public BancDeRecette semer(Semis semis) {
        this.semis = Objects.requireNonNull(semis, "semis");
        return this;
    }

    /// Enregistre une connexion, **comme la modale de connexion le ferait**.
    ///
    /// ⚠️ Posée AVANT le chargement du chrome : c'est à la construction du menu que
    /// `NavigationConnexion.libelleMenu()` est lu, et une connexion posée après laisserait l'entrée
    /// afficher « Se connecter à Vigie-Chiro… » pendant que la scène joue un utilisateur connecté.
    public BancDeRecette connecte(String id, String pseudo, String role) {
        this.profilConnecte = new ProfilVigieChiro(id, pseudo, role);
        return this;
    }

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
        surcharges.addAll(remplacements);
        Injector injecteur =
                Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(surcharges));

        new MigrationSchema(injecteur.getInstance(SourceDeDonnees.class)).migrer();
        semis.semer(injecteur);
        if (profilConnecte != null) {
            injecteur.getInstance(StockageConnexion.class).enregistrer("jeton-de-recette", profilConnecte);
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
