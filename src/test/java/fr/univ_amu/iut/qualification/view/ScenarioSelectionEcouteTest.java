package fr.univ_amu.iut.qualification.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.importation.view.PreambuleImport;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Personnaliser la sélection d'écoute, la modale qui décide de ce qu'on écoutera et dont
/// « Régénérer » **efface la progression** de l'observateur (#4734).
///
/// Le banc part d'un vrai import, par [PreambuleImport], et rejoint la vérification par la carte du
/// passage - mesurée ouverte sur un passage fraîchement importé.
///
/// Ce geste **était injouable** : `personnaliser()` se terminait par un `showAndWait`, qui fige
/// TestFX, alors même qu'il efface la progression. #1431 en a fait une vraie modale, et c'est ce qui
/// le rend filmable.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioSelectionEcouteTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    private static final String FIXTURE = "sd-nominale";

    private static final int APPARITION_SECONDES = 30;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

    /// Ce que l'avertissement de la modale annonce, et que la confirmation directe reprend.
    private static final String PERTE = "efface la progression";

    private Path carteSd;

    private Injector injecteur;

    @Start
    void start(Stage stage) throws IOException {
        carteSd = CarteDeRecette.materialiser(FIXTURE);

        injecteur = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .remplacer(liaison -> liaison.bind(ExecuteurTache.class)
                        .toInstance(new ExecuteurTacheRalenti(new ExecuteurTacheAsynchrone(), PAUSE_PAR_FICHIER_MS)))
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
            value = {"S3-12", "S3-13", "S3-14", "S3-15", "S3-16", "S3-17"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S3-12 à S3-17 · personnaliser la sélection d'écoute, et ce que « Régénérer » efface")
    void personnaliser_la_selection_d_ecoute(FxRobot robot) throws TimeoutException {
        PreambuleImport.importerUneNuitEtOuvrirSonPassage(robot, injecteur.getInstance(Navigateur.class), carteSd);

        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonVerifier");
        WaitForAsyncUtils.waitForFxEvents();

        attendre(
                APPARITION_SECONDES,
                () -> robot.lookup("#tableSequences").tryQuery().isPresent(),
                "l'écran de vérification ne s'est pas ouvert depuis le passage : c'est par sa carte que"
                        + " l'observateur y arrive, et sans lui aucun des six cas n'a de quoi se lire");

        // Le confirmateur est SUBSTITUÉ et ses questions capturées : `Alert.showAndWait()` fige TestFX.
        // Il refuse toujours, ce qui en fait un relevé SANS effet de bord - on peut donc l'interroger
        // plusieurs fois sans rien détruire.
        List<String> demandes = new ArrayList<>();
        QualificationController controleur = controleurDeLEcran();
        controleur.confirmateur().definir(message -> {
            demandes.add(message);
            return false;
        });

        // Le compte rendu de régénération (#1404/#1509) est SUBSTITUÉ pour la même raison que le
        // confirmateur : il ouvre un vrai dialogue, qui fige TestFX. Mesuré ici - c'est lui qui a fait
        // rougir le premier tir, sa fenêtre passant pour la modale de sélection.
        List<String> comptesRendus = new ArrayList<>();
        controleur.notificateur().definir((niveau, entete, message) -> comptesRendus.add(entete + " · " + message));

        regenererDirectNePrevientQueSIlYAAPerdre(robot, demandes);
        laModaleOffreSesDeuxMethodes(robot);
        lAvertissementDEffacementEstALEcran(robot);
        annulerNeToucheRien(robot, demandes);
        echapFermeLaModale(robot);
        regenererAppliqueEtRemetLaProgressionAZero(robot, demandes, comptesRendus);
    }

    private static void regenererDirectNePrevientQueSIlYAAPerdre(FxRobot robot, List<String> demandes)
            throws TimeoutException {
        // ─── S3-12 · « Régénérer » direct ne prévient QUE s'il y a quelque chose à perdre ────────
        // Premier relevé, progression vierge : le produit ne doit rien demander. Sans ce relevé-là,
        // « il demande » ne dirait pas s'il demande À BON ESCIENT ou à chaque fois.
        GesteVisible.cliquer(robot, "#boutonRegenerer");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(demandes)
                .as("aucune séquence n'a été écoutée : régénérer ne coûte rien, et le produit ne pose"
                        + " donc aucune question. Un avertissement systématique s'apprend à ignorer,"
                        + " et ne protégerait plus le jour où il y a vraiment à perdre")
                .isEmpty();

        ecouterLaPremiereSequence(robot);

        // Second relevé, une séquence écoutée : cette fois il y a de quoi perdre.
        GesteVisible.cliquer(robot, "#boutonRegenerer");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(demandes)
                .as("une séquence a été écoutée : le produit prévient AVANT d'effacer cette progression."
                        + " C'est le seul avertissement que l'observateur recevra")
                .hasSize(1);

        assertThat(demandes.getFirst())
                .as("et la question NOMME ce qui va être perdu, au lieu d'un « confirmer ? » qui ne"
                        + " permettrait pas de décider")
                .contains(PERTE);

        Respiration.leTempsDeLire(robot);
    }

    private static void laModaleOffreSesDeuxMethodes(FxRobot robot) throws TimeoutException {
        // ─── S3-13 · « Personnaliser… » ouvre la modale, et ses DEUX méthodes ────────────────────
        ouvrirLaModale(robot);

        assertThat(robot.lookup("#choixReparti").tryQuery())
                .as("la méthode « réparties sur la nuit » est proposée : c'est celle du protocole")
                .isPresent();
        assertThat(robot.lookup("#choixAleatoire").tryQuery())
                .as("et la méthode aléatoire aussi - DEUX méthodes, comme la case le demande. Une"
                        + " seule proposée ne serait plus un choix")
                .isPresent();
        assertThat(robot.lookup("#curseurTaille").tryQuery())
                .as("la taille de la sélection se règle, et son libellé la dit")
                .isPresent();
        assertThat(texte(robot, "#lblTaille"))
                .as("le libellé de taille n'est pas vide : un curseur sans valeur lisible se règle à" + " l'aveugle")
                .isNotBlank();
    }

    private static void lAvertissementDEffacementEstALEcran(FxRobot robot) throws TimeoutException {
        // ─── S3-14 · l'avertissement est VISIBLE, pas seulement présent ──────────────────────────
        Node avertissement = robot.lookup(n -> n instanceof javafx.scene.control.Labeled libelle
                        && libelle.getText() != null
                        && libelle.getText().contains(PERTE))
                .query();

        assertThat(avertissement.isVisible())
                .as(
                        "l'avertissement « %s » est à l'écran dans la modale. C'est le seul endroit où"
                                + " l'observateur apprend ce que « Régénérer » lui coûte",
                        PERTE)
                .isTrue();

        Respiration.leTempsDeLire(robot);
    }

    private static void annulerNeToucheRien(FxRobot robot, List<String> demandes) throws TimeoutException {
        // ─── S3-16 · « Annuler » ne touche RIEN - le test clé de #1462 ───────────────────────────
        // Trois grandeurs relevées, modifiées, puis relues après « Annuler ». Un instantané ne
        // distinguerait pas « Annuler » d'un bouton mort.
        boolean aleatoireAvant = radio(robot, "#choixAleatoire").isSelected();
        double tailleAvant = curseur(robot).getValue();

        GesteVisible.cliquer(robot, aleatoireAvant ? "#choixReparti" : "#choixAleatoire");
        robot.interact(() ->
                curseur(robot).setValue(tailleAvant == curseur(robot).getMin() ? tailleAvant + 5 : tailleAvant - 5));
        WaitForAsyncUtils.waitForFxEvents();

        GesteVisible.cliquer(robot, "Annuler");
        WaitForAsyncUtils.waitForFxEvents();

        attendre(
                APPARITION_SECONDES,
                () -> robot.lookup("#curseurTaille").tryQuery().isEmpty(),
                "« Annuler » n'a pas fermé la modale");

        ouvrirLaModale(robot);

        assertThat(radio(robot, "#choixAleatoire").isSelected())
                .as("la méthode est celle d'avant : le brouillon a été jeté, le réglage n'a jamais été"
                        + " touché. C'est ce que #1462 garde")
                .isEqualTo(aleatoireAvant);
        assertThat(curseur(robot).getValue())
                .as("la taille aussi : elle valait %.0f", tailleAvant)
                .isEqualTo(tailleAvant);

        GesteVisible.cliquer(robot, "Annuler");
        WaitForAsyncUtils.waitForFxEvents();

        demandes.clear();
        GesteVisible.cliquer(robot, "#boutonRegenerer");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(demandes)
                .as("et la PROGRESSION non plus n'a pas bougé : le produit prévient encore, donc il y a"
                        + " toujours une écoute à perdre. Sans ce troisième constat, « Annuler » pourrait"
                        + " avoir effacé l'écoute en laissant les réglages intacts")
                .hasSize(1);
    }

    private static void echapFermeLaModale(FxRobot robot) throws TimeoutException {
        // ─── S3-17 · `Échap` ferme la modale ─────────────────────────────────────────────────────
        ouvrirLaModale(robot);
        robot.push(KeyCode.ESCAPE);
        WaitForAsyncUtils.waitForFxEvents();

        attendre(
                APPARITION_SECONDES,
                () -> robot.lookup("#curseurTaille").tryQuery().isEmpty(),
                "`Échap` n'a pas fermé la modale : une modale dont on ne sort qu'à la souris piège"
                        + " l'observateur au clavier (#1505)");

        Respiration.leTempsDeLire(robot);
    }

    private static void regenererAppliqueEtRemetLaProgressionAZero(
            FxRobot robot, List<String> demandes, List<String> comptesRendus) throws TimeoutException {
        // ─── S3-15 · « Régénérer » applique, reconstruit et ferme ; la progression repart à 0 ────
        ouvrirLaModale(robot);
        boolean aleatoireVoulu = !radio(robot, "#choixAleatoire").isSelected();
        GesteVisible.cliquer(robot, aleatoireVoulu ? "#choixAleatoire" : "#choixReparti");
        WaitForAsyncUtils.waitForFxEvents();

        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, modale(robot, "#boutonRegenerer"));
        WaitForAsyncUtils.waitForFxEvents();

        attendre(
                APPARITION_SECONDES,
                () -> robot.lookup("#curseurTaille").tryQuery().isEmpty(),
                "« Régénérer » n'a pas fermé la modale : elle a appliqué sans rendre la main");

        ouvrirLaModale(robot);
        assertThat(radio(robot, "#choixAleatoire").isSelected())
                .as("la méthode choisie a été APPLIQUÉE, et la modale la rouvre sur ce choix-là. Une"
                        + " modale qui se rouvrirait sur l'ancien réglage aurait régénéré autre chose"
                        + " que ce qui était demandé")
                .isEqualTo(aleatoireVoulu);
        GesteVisible.cliquer(robot, "Annuler");
        WaitForAsyncUtils.waitForFxEvents();

        demandes.clear();
        GesteVisible.cliquer(robot, "#boutonRegenerer");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(demandes)
                .as("et la progression est repartie à ZÉRO : le produit ne prévient plus, donc il n'y a"
                        + " plus rien à perdre. C'est le même oracle qu'au premier relevé, et c'est lui"
                        + " qui prouve que « Régénérer » a bien effacé ce qu'il annonçait effacer")
                .isEmpty();

        assertThat(comptesRendus)
                .as("et chaque régénération a RENDU COMPTE : reconstruire la sélection sans rien dire"
                        + " laisserait l'observateur devant une liste changée sans savoir pourquoi")
                .isNotEmpty();

        Respiration.leTempsDeLire(robot);
    }

    // --------------------------------------------------------------------------------------------

    /// Écoute la première séquence, comme l'observateur : la ligne se choisit, puis « Lecture ».
    ///
    /// **Le banc supplée le périphérique audio**, qu'il n'a pas. Mesuré : le clic sur `#playButton`
    /// laisse `playing` à faux, le composant retombant deux fois faute de carte son. La propriété est
    /// donc posée à vrai ensuite, et c'est le câblage RÉEL du produit qui prend le relais -
    /// `playingProperty` déclenche `marquerCouranteEcoutee`, qui marque la progression.
    ///
    /// Ce que ceci ne démontre pas, et que la page des clips dit : que « Lecture » émette du son.
    private static void ecouterLaPremiereSequence(FxRobot robot) throws TimeoutException {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);
        robot.interact(() -> table.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        AudioView vue = (AudioView) robot.lookup("#audioView").query();
        attendre(
                APPARITION_SECONDES,
                () -> vue.getAudioFile() != null,
                "aucune séquence n'a été chargée dans la vue audio : sans fichier, « Lecture » n'a rien"
                        + " à jouer et la progression ne peut pas être marquée");

        GesteVisible.cliquer(robot, "#playButton");
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> vue.setPlaying(true));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// Ouvre la modale et attend qu'elle soit là.
    private static void ouvrirLaModale(FxRobot robot) throws TimeoutException {
        GesteVisible.cliquer(robot, "#boutonPersonnaliser");
        WaitForAsyncUtils.waitForFxEvents();
        attendre(
                APPARITION_SECONDES,
                () -> robot.lookup("#curseurTaille").tryQuery().isPresent(),
                "« Personnaliser… » n'a pas ouvert la modale de sélection d'écoute");
    }

    /// Le nœud `id` de la MODALE, et non son homonyme du panneau.
    ///
    /// `#boutonRegenerer` existe en deux exemplaires dès que la modale est ouverte - mesuré : le
    /// panneau de sélection porte le même identifiant que la modale. Un `lookup` par identifiant seul
    /// en trouve deux et n'a aucune raison de rendre le bon.
    private static Node modale(FxRobot robot, String id) {
        Stage fenetre = robot.listTargetWindows().stream()
                .filter(Stage.class::isInstance)
                .map(Stage.class::cast)
                .filter(f -> f.getOwner() != null)
                .filter(f -> !robot.from(f.getScene().getRoot())
                        .lookup(id)
                        .queryAll()
                        .isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune fenêtre ouverte par-dessus la principale ne porte « " + id + " »."));
        return robot.from(fenetre.getScene().getRoot()).lookup(id).query();
    }

    private static RadioButton radio(FxRobot robot, String id) {
        return robot.lookup(id).queryAs(RadioButton.class);
    }

    private static Slider curseur(FxRobot robot) {
        return robot.lookup("#curseurTaille").queryAs(Slider.class);
    }

    /// Le contrôleur de l'écran affiché, pris chez le navigateur qui le détient.
    ///
    /// `Injector#getInstance` en rendrait un AUTRE : il n'est pas singleton, et celui de la scène a
    /// été créé par le `FXMLLoader` de la navigation. Substituer le confirmateur d'un contrôleur
    /// absent de l'écran laisserait le vrai dialogue s'ouvrir, et il fige TestFX.
    private QualificationController controleurDeLEcran() {
        Object courant =
                injecteur.getInstance(Navigateur.class).historique().getLast().controleur();
        if (!(courant instanceof QualificationController qualification)) {
            throw new IllegalStateException("L'écran affiché n'est pas la vérification mais "
                    + (courant == null ? "rien" : courant.getClass().getSimpleName())
                    + " : le clic sur « Vérifier » n'a pas mené où la session le dit.");
        }
        return qualification;
    }

    private static String texte(FxRobot robot, String id) {
        Node noeud = robot.lookup(id).tryQuery().orElse(null);
        return noeud instanceof javafx.scene.control.Labeled libelle && libelle.getText() != null
                ? libelle.getText()
                : "";
    }

    private static void attendre(int secondes, java.util.concurrent.Callable<Boolean> condition, String siJamais)
            throws TimeoutException {
        try {
            WaitForAsyncUtils.waitFor(secondes, TimeUnit.SECONDS, condition);
        } catch (TimeoutException jamais) {
            throw new TimeoutException(siJamais);
        }
    }
}
