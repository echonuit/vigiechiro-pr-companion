package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CarteDeRecette;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.recette.film.EnregistreurDeFilm;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.view.NavigationSites;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// L'import nominal de `S2`, dans l'ordre où la session le joue (#4521).
///
/// La carte SD n'est pas versionnée : elle est **matérialisée depuis sa spec** par
/// [fr.univ_amu.iut.recette.CarteDeRecette], qui la reconstruit à l'octet près - aucune date tirée de l'horloge, aucun
/// octet aléatoire. Le banc n'a donc rien à fabriquer, et le clip montre la vraie inspection d'un
/// vrai arbre de fichiers.
///
/// Le geste part du **détail du carré 640380**, comme la session le dit, et non de l'écran d'import
/// monté directement : c'est de là que l'utilisateur y arrive, et un clip qui commencerait sur
/// l'assistant ne montrerait pas ce qui l'a ouvert.
///
/// Le carré 640380 est local et non relié, et c'est le garde-fou de la séance : l'import crée la
/// participation Vigie-Chiro au plus tôt dès que l'observateur est connecté et le site relié
/// (`ServiceImport.creerParticipationSiPossible`). Ici, aucune écriture serveur.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioImportNominalTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    /// La spec de la carte nominale : 6 wav, série 1925492, nuit du 22/04.
    private static final String FIXTURE = "sd-nominale";

    /// Ce que le journal de cette carte-là déclare, et que l'inspection doit nommer.
    private static final String SERIE = "1925492";

    private static final int ORIGINAUX = 6;

    private static final int APPARITION_SECONDES = 30;

    private Path carteSd;

    private Injector injecteur;

    @Start
    void start(Stage stage) throws IOException {
        carteSd = CarteDeRecette.materialiser(FIXTURE);

        injecteur = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                // ASYNCHRONE : l'inspection balaie un dossier hors du fil JavaFX, et c'est ce
                // balayage qu'on filme. En synchrone le fil est bloqué, donc aucune image ne sort
                // pendant l'opération.
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .semer(this::poserLeCarreEtSonPoint)
                .ouvrir(inj -> inj.getInstance(NavigationSites.class).ouvrirDetail(CARRE))
                .montrer(stage);
    }

    /// La base de départ : un observateur, un carré local, un point d'écoute. C'est celle que S1
    /// laisse derrière elle, et la session le dit en toutes lettres.
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
            value = {"S2-01", "S2-02", "S2-03", "S2-04", "S2-05", "S2-06", "S2-07"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S2-01 à S2-07 · désigner la carte SD, et lire ce que l'inspection en dit")
    void designer_la_source_et_l_inspecter(FxRobot robot) throws TimeoutException {
        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, "#boutonImporterNuit");
        WaitForAsyncUtils.waitForFxEvents();

        // ─── S2-01 · le champ est en LECTURE SEULE ───────────────────────────────────────────────
        // Asserté AVANT le clic sur « Parcourir » : après, le champ porte un chemin, et un champ
        // rempli paraît figé même s'il ne l'est pas.
        assertThat(robot.lookup("#champDossier").queryAs(TextField.class).isEditable())
                .as("« Dossier source » se DÉSIGNE, il ne se saisit pas : un chemin tapé à la main"
                        + " désignerait un dossier que personne n'a parcouru, et l'inspection porterait"
                        + " sur autre chose que ce que l'écran montre")
                .isFalse();

        // Le sélecteur natif figerait TestFX (#1431), et c'est pour cela qu'il est un porteur
        // remplaçable. Le clic sur « Parcourir », lui, est RÉEL : le clip montre le geste, et le
        // dialogue système est ce que le banc ne peut pas filmer, pas ce qu'il contourne.
        controleur().selecteur().definir(repondant(carteSd));

        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonParcourir");
        WaitForAsyncUtils.waitForFxEvents();

        attendre(
                APPARITION_SECONDES,
                () -> !texte(robot, "#labelOriginaux").isBlank(),
                "l'inspection n'a jamais rendu son compte d'originaux : elle balaie le dossier hors du"
                        + " fil JavaFX, et rien n'a paru dans le temps imparti");

        // ─── S2-03 · le journal détecté, NOMMÉ ───────────────────────────────────────────────────
        assertThat(texte(robot, "#labelJournal"))
                .as(
                        "l'inspection doit NOMMER le journal qu'elle a trouvé (`LogPR%s`). Un libellé qui"
                                + " dirait seulement « journal détecté » ne permettrait pas de voir qu'elle a lu"
                                + " CETTE carte-là",
                        SERIE)
                .contains(SERIE);

        // ─── S2-04 · le relevé climatique ────────────────────────────────────────────────────────
        assertThat(texte(robot, "#labelReleve"))
                .as(
                        "le relevé climatique de cette carte est `PaRecPR%s_THLog.csv` : l'inspection"
                                + " l'annonce, sans quoi l'observateur ne saurait pas que les températures"
                                + " suivront la nuit",
                        SERIE)
                .isNotBlank();

        // ─── S2-05 · les six originaux, COMPTÉS ──────────────────────────────────────────────────
        assertThat(texte(robot, "#labelOriginaux"))
                .as(
                        "la carte nominale porte %d wav, et l'inspection les compte. C'est ce compte qui"
                                + " dit à l'observateur que rien n'a été oublié sur la carte",
                        ORIGINAUX)
                .contains(String.valueOf(ORIGINAUX));

        // ─── S2-06 · aucun bandeau, parce que rien ne cloche ─────────────────────────────────────
        // Deux faits, et non un. « Invisible » se confondrait avec « absent » : mes aides rendent
        // faux dans les deux cas, et un cas qui ne distinguerait pas les deux resterait vert le jour
        // où la zone disparaîtrait du FXML. Or elle y est toujours - `SectionInspectionController`
        // ne fait que basculer sa visibilité sur `rendu.estVide()`.
        assertThat(robot.lookup("#zoneAvertissements").tryQuery())
                .as("la zone d'avertissements doit EXISTER dans l'écran : si le nœud disparaissait, le"
                        + " constat « aucun bandeau » ci-dessous serait vrai pour la mauvaise raison, et"
                        + " ce cas ne garderait plus rien")
                .isPresent();

        assertThat(visible(robot, "#zoneAvertissements"))
                .as("cas NOMINAL : la zone est là et reste MASQUÉE. C'est le contrôle négatif des cas"
                        + " dégradés - si un bandeau paraît ici, ceux qui en attendent un plus loin ne"
                        + " prouvent plus rien")
                .isFalse();

        // ─── S2-07 · le renommage ANNONCÉ, et rien de plus ───────────────────────────────────────
        assertThat(texte(robot, "#labelNommage"))
                .as("l'inspection annonce le renommage À VENIR : elle est en lecture seule, et les"
                        + " originaux sont intacts sur la carte tant que l'import n'a pas eu lieu")
                .isNotBlank();

        assertThat(Files.isDirectory(carteSd.resolve("bruts")))
                .as("les originaux sont INTACTS : l'inspection lit la carte, elle n'y touche pas. Un"
                        + " cas qui ne le constaterait pas laisserait passer une inspection qui renomme"
                        + " avant que l'observateur ait dit oui")
                .isTrue();

        Respiration.leTempsDeLire(robot);
    }

    // --------------------------------------------------------------------------------------------

    /// Le contrôleur de l'écran affiché, pris chez le navigateur qui le détient.
    ///
    /// `Injector#getInstance` en rendrait un AUTRE : le contrôleur n'est pas un singleton, et celui de
    /// la scène a été créé par le `FXMLLoader` de la navigation. Poser le double sur un contrôleur qui
    /// n'est pas à l'écran laisserait « Parcourir » ouvrir le dialogue natif, qui fige le banc.
    private ImportationController controleur() {
        Navigateur navigateur = injecteur.getInstance(Navigateur.class);
        Object courant = navigateur.historique().getLast().controleur();
        assertThat(courant)
                .as("l'écran affiché doit être l'assistant d'import : le clic sur « Importer une nuit »"
                        + " n'a pas mené où la session le dit")
                .isInstanceOf(ImportationController.class);
        return (ImportationController) courant;
    }

    /// Un sélecteur qui répond `carte` à la demande de dossier, et refuse le reste.
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

    private static void attendre(int secondes, java.util.concurrent.Callable<Boolean> condition, String siJamais)
            throws TimeoutException {
        try {
            WaitForAsyncUtils.waitFor(secondes, java.util.concurrent.TimeUnit.SECONDS, condition);
        } catch (TimeoutException jamais) {
            throw new TimeoutException(siJamais);
        }
    }

    private static boolean visible(FxRobot robot, String id) {
        Node noeud = robot.lookup(id).tryQuery().orElse(null);
        return noeud != null && noeud.isVisible() && noeud.getParent() != null;
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
