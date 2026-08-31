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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le clavier de l'écran de vérification (#4787).
///
/// ## Trois de ces six cas gardent une ABSENCE
///
/// `Espace` ne doit **pas** déclencher le bouton qui a le focus (#1504), `Entrée` ne doit **rien**
/// faire sans verdict, et `O`/`D`/`J` doivent se **taire** pendant la saisie. Sur des raccourcis
/// simplement débranchés, les trois seraient verts.
///
/// Chacun relève donc d'abord **l'autre condition** : la touche agit là où elle doit agir, puis se
/// tait là où elle doit se taire. C'est le seul moyen qu'un non-effet prouve quelque chose.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioRaccourcisVerificationTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    private static final String FIXTURE = "sd-nominale";

    private static final int APPARITION_SECONDES = 30;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

    /// La classe que `QualificationController` pose sur le bouton du verdict retenu.
    private static final String CHOISI = "verdict-choisi";

    private static final List<String> BOUTONS_DE_VERDICT =
            List.of("#boutonOk", "#boutonDouteux", "#boutonInexploitable");

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
            value = {"S3-39", "S3-40", "S3-41", "S3-42", "S3-43", "S3-44"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S3-39 à S3-44 · les raccourcis clavier : ce qu'ils font, et ce qu'ils s'interdisent")
    void les_raccourcis_clavier_de_la_verification(FxRobot robot) throws TimeoutException {
        PreambuleImport.importerUneNuitEtOuvrirSonPassage(robot, injecteur.getInstance(Navigateur.class), carteSd);

        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonVerifier");
        WaitForAsyncUtils.waitForFxEvents();
        Attente.que(
                () -> robot.lookup("#tableSequences").tryQuery().isPresent(),
                "l'écran de vérification ne s'est pas ouvert : sans lui, aucun raccourci n'a de clavier",
                APPARITION_SECONDES * 1000L);

        lesFlechesChangentDeSequence(robot);
        espaceVaAuLecteurEtPasAuBoutonFocalise(robot);
        entreeNeFaitRienSansVerdict(robot);
        lesLettresPosentLeVerdictGlobal(robot);
        lesRaccourcisSeTaisentPendantLaSaisie(robot);
        entreeEnregistreUneFoisLeVerdictChoisi(robot);
        laLegendeNommeLesLibellesDeLEcran(robot);
    }

    // ─── S3-39 ──────────────────────────────────────────────────────────────────────────────────

    private static void lesFlechesChangentDeSequence(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);
        robot.interact(() -> {
            table.requestFocus();
            table.getSelectionModel().select(0);
        });
        WaitForAsyncUtils.waitForFxEvents();

        Respiration.surLeMomentCle(robot);
        robot.push(KeyCode.DOWN);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(table.getSelectionModel().getSelectedIndex())
                .as("la flèche BAS passe à la séquence suivante. C'est le geste de l'écoute au long"
                        + " cours : une main sur le clavier, l'autre nulle part")
                .isEqualTo(1);
    }

    // ─── S3-40 ──────────────────────────────────────────────────────────────────────────────────

    /// `Espace` va au lecteur, **et pas** au bouton qui a le focus (#1504).
    ///
    /// Les deux moitiés se relèvent : la seconde seule serait verte sur un raccourci débranché. Ce que
    /// le banc ne démontre pas, et que la page des clips dit : que le son sorte - la lecture ne démarre
    /// pas faute de périphérique audio, mesuré pendant #4734.
    private static void espaceVaAuLecteurEtPasAuBoutonFocalise(FxRobot robot) {
        AudioView lecteur = (AudioView) robot.lookup("#audioView").query();
        List<Boolean> tentatives = new ArrayList<>();
        AtomicInteger declenchements = new AtomicInteger();
        Button focalise = robot.lookup("#boutonOk").queryAs(Button.class);

        robot.interact(() -> {
            lecteur.playingProperty().addListener((observable, avant, apres) -> tentatives.add(apres));
            focalise.addEventHandler(ActionEvent.ACTION, evenement -> declenchements.incrementAndGet());
            focalise.requestFocus();
        });
        WaitForAsyncUtils.waitForFxEvents();

        Respiration.surLeMomentCle(robot);
        robot.push(KeyCode.SPACE);
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);

        assertThat(declenchements.get())
                .as("le bouton qui a le focus n'est PAS déclenché. C'est le défaut de #1504 : la barre"
                        + " d'espace active nativement un bouton focalisé, et l'observateur aurait posé"
                        + " un verdict en croyant écouter")
                .isZero();

        assertThat(tentatives)
                .as("et la touche est bien allée au LECTEUR : ses tentatives en témoignent. Sans ce"
                        + " second constat, un raccourci débranché rendrait le premier vert")
                .isNotEmpty();
    }

    /// La moitié « absence » de `S3-42`, et elle vient AVANT le verdict : mesuré, retirer la condition
    /// `peutEnregistrer` laissait tout vert quand un verdict était déjà retenu. Un cas qui garde un
    /// non-effet doit l'éprouver dans l'état où ce non-effet est attendu.
    ///
    /// **Ce que la mutation n'a PAS pu montrer.** Trois couches le tiennent en série - la condition du
    /// contrôleur, la garde muette du ViewModel (#1970), un refus plus bas encore - et les retirer une
    /// à une, puis deux ensemble, laisse ce cas vert. Il constate un fait vrai sans prouver laquelle le
    /// tient.
    private static void entreeNeFaitRienSansVerdict(FxRobot robot) {
        robot.interact(() -> robot.lookup("#tableSequences").query().requestFocus());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(verdictsChoisis(robot))
                .as("aucun verdict n'est retenu : c'est l'état dans lequel « Entrée » ne doit rien faire")
                .isEmpty();

        Respiration.surLeMomentCle(robot);
        robot.push(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);

        assertThat(robot.lookup("#lblSucces").query().isVisible())
                .as("« Entrée » n'enregistre RIEN tant qu'aucun verdict n'est retenu. Enregistrer un"
                        + " verdict vide poserait sur la nuit un jugement que personne n'a rendu")
                .isFalse();
    }

    // ─── S3-41 ──────────────────────────────────────────────────────────────────────────────────

    private static void lesLettresPosentLeVerdictGlobal(FxRobot robot) {
        robot.interact(() -> robot.lookup("#tableSequences").query().requestFocus());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(verdictsChoisis(robot))
                .as("aucun verdict n'est encore retenu : c'est le relevé qui donne son sens au suivant")
                .isEmpty();

        robot.push(KeyCode.O);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(verdictsChoisis(robot))
                .as("« O » retient le verdict OK, et LUI SEUL. Deux boutons marqués ne diraient plus"
                        + " lequel part à l'enregistrement")
                .containsExactly("#boutonOk");

        Respiration.leTempsDeLire(robot);
    }

    // ─── S3-43 ──────────────────────────────────────────────────────────────────────────────────

    /// Les raccourcis se taisent pendant la saisie, et la lettre **s'écrit**.
    ///
    /// `J` est frappé plutôt que `O` : le verdict retenu est déjà OK, et une touche qui ne changerait
    /// rien laisserait le cas vert sans rien garder.
    private static void lesRaccourcisSeTaisentPendantLaSaisie(FxRobot robot) {
        TextArea commentaire = robot.lookup("#champCommentaire").queryAs(TextArea.class);
        robot.interact(() -> {
            commentaire.clear();
            commentaire.requestFocus();
        });
        WaitForAsyncUtils.waitForFxEvents();

        Respiration.surLeMomentCle(robot);
        robot.push(KeyCode.J);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(commentaire.getText())
                .as("la lettre s'écrit dans le commentaire : un raccourci qui mangerait la frappe"
                        + " rendrait le champ inutilisable")
                .isEqualToIgnoringCase("j");

        assertThat(verdictsChoisis(robot))
                .as("et le verdict n'a PAS bougé - « J » vaut Inexploitable hors saisie. Un observateur"
                        + " qui commente ne requalifie pas sa nuit en tapant")
                .containsExactly("#boutonOk");

        robot.interact(commentaire::clear);
        WaitForAsyncUtils.waitForFxEvents();
    }

    // ─── S3-42 ──────────────────────────────────────────────────────────────────────────────────

    private static void entreeEnregistreUneFoisLeVerdictChoisi(FxRobot robot) throws TimeoutException {
        robot.interact(() -> robot.lookup("#tableSequences").query().requestFocus());
        WaitForAsyncUtils.waitForFxEvents();

        Respiration.surLeMomentCle(robot);
        robot.push(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();

        Attente.que(
                () -> robot.lookup("#lblSucces").query().isVisible(),
                "« Entrée » n'a pas enregistré le verdict, alors qu'un verdict était retenu",
                APPARITION_SECONDES * 1000L);

        Respiration.leTempsDeLire(robot);
    }

    // ─── S3-44 ──────────────────────────────────────────────────────────────────────────────────

    /// La légende nomme les libellés que les boutons **portent**, et non ceux qu'ils portaient.
    ///
    /// Contrôle croisé et non lecture : #1513 a eu à corriger une légende qui avait cessé de suivre
    /// l'écran. Une aide qui nomme autre chose que ce qu'on voit est pire que pas d'aide.
    private static void laLegendeNommeLesLibellesDeLEcran(FxRobot robot) {
        String legende = texte(
                robot,
                robot.lookup(noeud -> noeud instanceof Labeled libelle
                                && libelle.getText() != null
                                && libelle.getText().startsWith("Raccourcis clavier"))
                        .query());

        assertThat(legende)
                .as("la légende est à l'écran : les raccourcis sont câblés mais indécouvrables sans" + " elle (#796)")
                .isNotBlank();

        for (String bouton : BOUTONS_DE_VERDICT) {
            String libelle = texte(robot, robot.lookup(bouton).query());
            assertThat(legende)
                    .as("la légende nomme « %s », le libellé que %s porte aujourd'hui", libelle, bouton)
                    .contains(libelle);
        }

        Respiration.leTempsDeLire(robot);
    }

    // --------------------------------------------------------------------------------------------

    /// Les boutons de verdict qui portent la marque du choix retenu.
    ///
    /// Une classe de style et non une couleur : c'est la décision du contrôleur qu'on lit, comme le
    /// liseré « recommandée » des cartes du passage.
    private static List<String> verdictsChoisis(FxRobot robot) {
        return BOUTONS_DE_VERDICT.stream()
                .filter(id -> robot.lookup(id).query().getStyleClass().contains(CHOISI))
                .toList();
    }

    private static String texte(FxRobot robot, Node noeud) {
        return noeud instanceof Labeled libelle && libelle.getText() != null ? libelle.getText() : "";
    }
}
