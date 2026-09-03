package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CarteDeRecette;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.ExecuteurTacheRalenti;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.recette.film.EnregistreurDeFilm;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.view.NavigationSites;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le quatrième bandeau de l'inspection : **le support est monté en lecture seule** (#4991).
///
/// ## Ce que ces cas ont de rare, et pourquoi ils dormaient
///
/// La session les annote « carte réellement protégée en écriture, ou volume monté en lecture seule
/// pour l'occasion ». Aucun banc ne sait monter un volume, et c'est pourquoi ces trois cas n'avaient
/// pas de clip. #5091 a posé la couture qu'il fallait - `definirSondeDuSupport` - parce que « ni un
/// banc ni un outil de capture ne sait monter un volume en lecture seule ».
///
/// ## Ce que le clip démontre, et qui tient à la PAIRE
///
/// `S2-71` montre le bandeau ; `S2-72` montre que **l'import aboutit derrière**. Séparés, chacun ment
/// à moitié : un bandeau seul laisse croire à un refus, ce qu'un observateur craint précisément en
/// voyant un avertissement sur sa carte. Le clip les joue donc d'un trait.
///
/// L'ordre du bandeau porte son sens : d'abord que l'import fonctionne, ensuite le geste à faire,
/// enfin que c'est la **prochaine** nuit qui est en jeu. Le banc l'affirme dans cet ordre, et non par
/// la seule présence des trois phrases.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioBandeauLectureSeuleTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    /// La carte est saine : c'est le SUPPORT qui est en lecture seule, pas son contenu. Les deux sont
    /// indépendants, et la couture le dit en ne regardant que le volume.
    private static final String FIXTURE = "sd-nominale";

    private static final String LECTURE_SEULE = "lecture seule";

    private static final String L_IMPORT_FONCTIONNE = "l'import de cette nuit fonctionne";

    private static final String LE_GESTE = "vérifiez le verrou";

    private static final String LA_PROCHAINE_NUIT = "prochaine nuit";

    private static final int APPARITION_SECONDES = 30;

    private static final int FIN_SECONDES = 180;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

    private Path carteSd;

    private Injector injecteur;

    @Start
    void start(Stage stage) throws IOException {
        carteSd = CarteDeRecette.materialiser(FIXTURE);
        injecteur = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .remplacer(new AbstractModule() {
                    @Provides
                    @Singleton
                    ExecuteurTache executeurFreine() {
                        return new ExecuteurTacheRalenti(new ExecuteurTacheAsynchrone(), PAUSE_PAR_FICHIER_MS);
                    }
                })
                .semer(this::poserLeCarreEtSonPoint)
                .ouvrir(inj -> inj.getInstance(NavigationSites.class).ouvrirDetail(CARRE))
                .montrer(stage);
    }

    private void poserLeCarreEtSonPoint(Injector inj) {
        SourceDeDonnees source = inj.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Observateur"));
        ServiceSites service = inj.getInstance(ServiceSites.class);
        Site carre = service.creerSite(CARRE, "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(carre.id(), "A1", 43.42, 5.11, "Près du grand chêne");
    }

    @Test
    @CasDeRecette(
            value = {"S2-71", "S2-72"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S2-71 et S2-72 · le support en lecture seule s'annonce, et l'import aboutit quand même")
    void le_support_en_lecture_seule_s_annonce_et_l_import_aboutit(FxRobot robot) throws TimeoutException {
        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, "#boutonImporterNuit");
        WaitForAsyncUtils.waitForFxEvents();

        // La sonde se pose sur l'inspection DE CET ÉCRAN-CI : le ViewModel n'est lié nulle part, donc
        // une instance prise à l'injecteur n'atteindrait pas ce qu'on filme (#5138).
        controleur().inspection().definirSondeDuSupport(chemin -> true);
        controleur().selecteur().definir(repondant(carteSd));

        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonParcourir");
        WaitForAsyncUtils.waitForFxEvents();

        Attente.que(
                () -> bandeaux(robot).contains(LECTURE_SEULE),
                "le quatrième bandeau n'a jamais paru : l'inspection balaie le dossier hors du fil"
                        + " JavaFX, et rien n'a été annoncé dans le temps imparti",
                APPARITION_SECONDES * 1000L);

        String dit = bandeaux(robot);

        // ─── S2-71 · les trois phrases, DANS LEUR ORDRE ──────────────────────────────────────────
        assertThat(dit)
                .as("le bandeau dit d'abord que l'import fonctionne, ensuite le geste à faire, enfin"
                        + " que c'est la PROCHAINE nuit qui est en jeu. Cet ordre est son sens :"
                        + " commencer par le geste ferait craindre un refus")
                .containsSubsequence(L_IMPORT_FONCTIONNE, LE_GESTE, LA_PROCHAINE_NUIT);

        // ─── S2-72 · et l'import ABOUTIT ─────────────────────────────────────────────────────────
        ComboBox<?> points = robot.lookup("#comboPoints").queryAs(ComboBox.class);
        robot.interact(() -> points.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        GesteVisible.amenerDansLeCadre(robot, "#boutonImporter");
        GesteVisible.cliquer(robot, "#boutonImporter");

        Attente.que(
                () -> robot.lookup("#compteRenduChiffre").tryQuery().isPresent(),
                "l'import n'a pas abouti alors que le bandeau ne fait qu'informer. C'est LE point du"
                        + " geste : Companion lit la source et n'y écrit jamais, y compris pour poser"
                        + " cette question - elle est posée au volume, sans aucune écriture (R9)",
                FIN_SECONDES * 1000L);
    }

    @Test
    @CasDeRecette(value = "S2-73", portee = Portee.A_L_ECRAN)
    @DisplayName("S2-73 · sur un support inscriptible, ce bandeau n'apparaît pas")
    void une_carte_inscriptible_n_annonce_rien(FxRobot robot) throws TimeoutException {
        // Contrôle négatif, et il n'est pas décoratif : sans lui, un bandeau qui paraîtrait TOUJOURS
        // passerait le cas précédent. La sonde reste celle du produit, qui interroge le vrai volume.
        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, "#boutonImporterNuit");
        WaitForAsyncUtils.waitForFxEvents();

        controleur().selecteur().definir(repondant(carteSd));

        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonParcourir");
        WaitForAsyncUtils.waitForFxEvents();

        Attente.que(
                () -> !texte(robot, "#labelOriginaux").isBlank(),
                "l'inspection n'a jamais rendu son compte d'originaux : sans elle, l'absence de bandeau"
                        + " ne prouverait rien, la carte n'ayant pas été lue",
                APPARITION_SECONDES * 1000L);

        assertThat(bandeaux(robot))
                .as("un message qui paraîtrait sur une carte saine ferait douter d'un support qui va"
                        + " bien, et l'observateur cesserait de le croire quand il compte")
                .doesNotContain(LECTURE_SEULE);
    }

    /// Tout ce que la zone des avertissements dit, mis bout à bout dans l'ordre de l'écran.
    private static String bandeaux(FxRobot robot) {
        Node zone = robot.lookup("#zoneAvertissements").tryQuery().orElse(null);
        if (!(zone instanceof Parent parent)) {
            return "";
        }
        StringBuilder dit = new StringBuilder();
        collecter(parent, dit);
        return dit.toString();
    }

    private static void collecter(Node noeud, StringBuilder dit) {
        if (noeud instanceof Labeled libelle && libelle.getText() != null) {
            dit.append(libelle.getText()).append('\n');
        }
        if (noeud instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(enfant -> collecter(enfant, dit));
        }
    }

    private ImportationController controleur() {
        Navigateur navigateur = injecteur.getInstance(Navigateur.class);
        Object courant = navigateur.historique().getLast().controleur();
        assertThat(courant)
                .as("l'écran affiché doit être l'assistant d'import")
                .isInstanceOf(ImportationController.class);
        return (ImportationController) courant;
    }

    private static SelecteurFichier repondant(Path carte) {
        return new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                return Optional.of(carte);
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
                return Optional.of(carte);
            }

            @Override
            public Optional<Path> enregistrerFichier(String titre, String nomPropose, FiltreFichier filtre) {
                throw new AssertionError("l'import LIT une source : ce geste n'écrit aucun fichier");
            }
        };
    }

    private static String texte(FxRobot robot, String id) {
        Node noeud = robot.lookup(id).tryQuery().orElse(null);
        if (noeud instanceof Labeled libelle) {
            return libelle.getText() == null ? "" : libelle.getText();
        }
        if (noeud instanceof TextInputControl champ) {
            return champ.getText() == null ? "" : champ.getText();
        }
        return "";
    }
}
