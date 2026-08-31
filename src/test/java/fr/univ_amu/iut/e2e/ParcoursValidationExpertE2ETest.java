package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.MessageVigieChiro;
import fr.univ_amu.iut.commun.api.ObservationVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MessageObservation;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
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

/// **Test E2E de la validation d'expert** (#1417, EPIC #1154) : de l'import VigieChiro jusqu'à l'écran.
///
/// La chaîne complète, sur le **vrai câblage** de l'application (`RacineInjecteur`), seule la plateforme
/// étant bouchonnée : un import rapatrie une observation sur laquelle **un expert du MNHN a contredit
/// l'observateur** et lui a écrit, puis « Sons & validation » s'ouvre, et **le montre**.
///
/// Ce que ce parcours protège tient en une phrase : ces champs arrivaient **déjà** du serveur, à chaque
/// import, et l'application les jetait. Elle présentait donc la correction de l'observateur comme le
/// dernier mot, alors qu'un expert avait pu la réviser **sans qu'on le voie jamais**. Les tests unitaires
/// vérifient chaque maillon ; celui-ci vérifie que la chaîne **tient d'un bout à l'autre**.
@ExtendWith(ApplicationExtension.class)
class ParcoursValidationExpertE2ETest {

    private static final String ID_USER = "u-e2e-expert";
    private static final String SERIE = "1925492";
    private static final String PARTICIPATION = "6a53f5faae21902a597394d3";
    private static final String SEQUENCE = "Car130711-2026-Pass1-Z41-PaRec_20260703_220529_000";

    private Injector injector;
    private long idPassage;

    /// Le contrôleur **que le FXML a créé**, pas celui que l'injecteur rendrait : la `controllerFactory`
    /// en fabrique un neuf par chargement, et seul celui-là a ses colonnes câblées.
    private fr.univ_amu.iut.audio.view.SonsValidationController controleur;

    /// JUnit crée ce répertoire et le **supprime** en fin de test (#4914).
    @TempDir
    private Path dossierTemporaire;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = dossierTemporaire;
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = Guice.createInjector(Modules.override(RacineInjecteur.modules())
                .with(liaison -> liaison.bind(ClientVigieChiro.class).toInstance(plateformeBouchonnee())));

        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        semerLaNuit(source);

        // Le passage est rattaché à sa participation : c'est ce qui rend l'import possible.
        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), PARTICIPATION));

        // L'import réel (pas une maquette du parcours) : c'est lui qui doit ramener le troisième avis.
        injector.getInstance(ImportVigieChiro.class).importer(idPassage, false);

        FXMLLoader loader = new FXMLLoader(
                AudioViewModel.class.getResource("/fr/univ_amu/iut/audio/view/" + "SonsValidation.fxml"));
        loader.setControllerFactory(DiagnosticGuice.pour(injector));
        Parent vue = loader.load();
        controleur = loader.getController();
        controleur.ouvrirSur(new SourceObservations.ParPassage(
                new ContextePassage(idPassage, 1, new ContexteSite("130711", "Z41", "Étang"))));
        FenetreAjustable.poser(stage, vue, 1400, 800);
        FenetreAjustable.afficher(stage);
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#1417 : l'expert a contredit l'observateur, l'écran le MONTRE, et donne sa discussion à lire")
    void le_troisieme_avis_arrive_jusqu_a_l_ecran(FxRobot robot) throws TimeoutException {
        TableView<LigneObservationAudio> table =
                robot.lookup("#tableObservations").queryAs(TableView.class);
        // L'ouverture (controleur.ouvrirSur, dans @Start) charge la table hors du fil JavaFX
        // (occupation.occuper) : `waitForFxEvents` ne fait que vider la file du fil FX, il n'attend pas
        // ce thread en tache de fond. Sans cette attente, la table est encore vide quand l'assertion
        // tombe, un échec qui ne se produit que sur une machine lente, donc en CI.
        Attente.que(() -> !table.getItems().isEmpty(), "la table se remplit", 5 * 1000L);
        assertThat(table.getItems())
                .as("l'import a bien ramené l'observation de la plateforme")
                .isNotEmpty();

        LigneObservationAudio ligne = table.getItems().getFirst();
        assertThat(ligne.taxonObservateur())
                .as("l'observateur avait corrigé Tadarida…")
                .isEqualTo("Nyclei");
        assertThat(ligne.taxonValidateur())
                .as("…et l'expert du MNHN l'a révisé. C'est CE mot-là que l'application jetait")
                .isEqualTo("Pipnat");
        assertThat(ligne.validateurEnDesaccord())
                .as("un désaccord d'expert : c'est ce qu'il faut voir en premier")
                .isTrue();
        assertThat(ligne.nbMessages())
                .as("et une discussion est ouverte sur cette détection")
                .isEqualTo(2);

        // Le fil, arrivé du serveur, est en base et rattaché à la BONNE observation (ancrage positionnel).
        List<MessageObservation> fil =
                injector.getInstance(AudioViewModel.class).discussion().fil(ligne.idObservation());
        assertThat(fil)
                .extracting(MessageObservation::texte)
                .containsExactly("La médiane est basse pour un Nyclei. Je penche pour Pipnat.", "Je réécoute.");

        // Et l'écran l'affiche : le panneau s'ouvre à droite du lecteur dès qu'on sélectionne la ligne.
        robot.interact(() -> table.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        VBox panneau = robot.lookup("#panneauDiscussion").queryAs(VBox.class);
        assertThat(panneau.isVisible())
                .as("un fil se lit EN écoutant : le panneau vit à côté du spectrogramme")
                .isTrue();
        assertThat(robot.lookup(".bulle-message").queryAllAs(VBox.class))
                .as("les deux messages sont là, dans l'ordre du serveur")
                .hasSize(2);
        assertThat(robot.lookup("#filDiscussion").queryAs(VBox.class).getChildren())
                .as("et chaque message dit qui parle")
                .anySatisfy(bulle -> assertThat(
                                ((Label) ((VBox) bulle).getChildren().getFirst()).getText())
                        .startsWith("Le validateur"));
    }

    // --- Harnais -----------------------------------------------------------------------------------

    /// La nuit, telle qu'elle est en base après un import de la carte SD : un site, un point, un passage,
    /// une session et **la** séquence que la participation serveur va nommer.
    private void semerLaNuit(SourceDeDonnees source) {
        // Session, original et fréquence sont ceux que la fixture pose par défaut.
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre("130711")
                .nomSite("Étang")
                .point("Z41")
                .enregistreur(SERIE)
                .nuit(1, 2026, "2026-07-03")
                .statut(StatutWorkflow.DEPOSE)
                .semer();
        idPassage = jeu.idPassage();
        Long idSession = jeu.idSession();
        Long idOriginal = jeu.idOriginal();
        new SequenceDao(source)
                .insert(new SequenceDEcoute(
                        null,
                        SEQUENCE + ".wav",
                        idOriginal,
                        0,
                        0.0,
                        5.0,
                        "/ws/transformes/" + SEQUENCE + ".wav",
                        false,
                        idSession));
    }

    /// La plateforme telle qu'elle répond quand un expert du MNHN est passé : il **contredit** la
    /// correction de l'observateur, et lui a écrit.
    ///
    /// Depuis #1284, un retour `ReponseApi` non bouchonné vaut **`null`** : chaque appel du parcours doit
    /// être stubé.
    private static ClientVigieChiro plateformeBouchonnee() {
        ClientVigieChiro client = mock(ClientVigieChiro.class);
        when(client.donnees(eq(PARTICIPATION), any()))
                .thenReturn(new ReponseApi.Succes<>(List.of(new DonneeVigieChiro(
                        "d-1",
                        SEQUENCE,
                        List.of(new ObservationVigieChiro(
                                0,
                                "Pippip", // Tadarida propose
                                0.74,
                                45.0,
                                0.5,
                                3.8,
                                null,
                                "Nyclei", // l'observateur corrige
                                Certitude.PROBABLE,
                                "Pipnat", // le validateur tranche, et le contredit
                                Certitude.SUR,
                                List.of(
                                        new MessageVigieChiro(
                                                "u-validateur",
                                                "La médiane est basse pour un Nyclei. Je penche pour Pipnat.",
                                                Instant.parse("2026-07-11T21:04:00Z")),
                                        new MessageVigieChiro("u-moi", "Je réécoute.", null))))))));
        return client;
    }
}
