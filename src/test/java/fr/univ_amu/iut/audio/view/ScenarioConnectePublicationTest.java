package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.plateforme.PlateformeDeTest;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.recette.film.EnregistreurDeFilm;
import fr.univ_amu.iut.validation.model.RapprochementTaxons;
import fr.univ_amu.iut.validation.model.Taxon;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// `S4-90` et `S4-92` : publier une correction vers Vigie-Chiro, puis la republier.
///
/// ## Pourquoi ce scénario n'existe que sur la plateforme de test
///
/// Les deux cas demandent des corrections **réellement publiées**. Contre un double, on lirait ce
/// qu'on lui a fait dire. Contre la plateforme nationale, on réécrirait les observations d'un compte
/// réel, et une correction publiée ne se retire pas. La plateforme de test de l'ADR 5641 joue le vrai
/// code serveur sur un état qu'on jette.
///
/// Il la vise donc **explicitement**, et porte `plateforme-de-test-seule`, que le tournage national
/// exclut (#5795).
///
/// ## Ce qu'il sème, et ce qu'il laisse au produit
///
/// Un passage local relié à la participation `nuit-traitee` de l'état de départ, avec deux séquences
/// nommées comme ses `donnees`. Le rapprochement des taxons n'est pas fabriqué : le semis appelle
/// [RapprochementTaxons], que le produit joue à la connexion, et qui relie « Barbar » au second taxon
/// de l'état de départ (#5794).
///
/// Tout le reste se fait **à l'écran**, ouvert directement : aucun import d'office ne le précède.
@Tag("recette-connectee")
@Tag("plateforme-de-test")
@Tag("plateforme-de-test-seule")
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioConnectePublicationTest {

    private static final String ID_USER = "u-recette";
    private static final String CARRE = "130711";
    private static final String POINT = "Z1";
    private static final String NOM_SITE = "Carré 130711";
    private static final int NUMERO_PASSAGE = 1;
    private static final int ANNEE = 2026;
    private static final Prefixe PREFIXE = new Prefixe(CARRE, ANNEE, NUMERO_PASSAGE, POINT);

    /// Les titres des `donnees` de `nuit-traitee` dans l'état de départ : c'est par eux que l'import
    /// rapproche une ligne du CSV d'une séquence locale.
    private static final List<String> SEQUENCES = List.of(
            "Car130711-2026-Pass1-Z1-PaRec_20260615_220000_000", "Car130711-2026-Pass1-Z1-PaRec_20260615_221500_000");

    /// Le second taxon de l'état de départ : la correction va de « Pippip », que Tadarida a rendu, à lui.
    private static final String TAXON_RETENU = "Barbar";

    private static final String MENU_ACTIONS = "#menuActions";
    private static final String IMPORTER = "Importer depuis Vigie-Chiro…";
    private static final String PUBLIER = "Publier les corrections vers Vigie-Chiro…";

    /// Ce que le récapitulatif dit quand la publication doit d'abord rapatrier des identifiants.
    private static final String ANCRAGE_A_VENIR = "à ancrer d'abord";

    private static final long REPONSE_DU_SERVEUR_MS = 60_000L;

    private Injector injecteur;
    private ContextePassage contexte;

    /// Les récapitulatifs que l'écran a soumis à confirmation, dans l'ordre. Écrits par le fil JavaFX,
    /// lus par le fil du test : une liste sûre, un `ArrayList` nu ne publiant pas ses écritures.
    private final List<String> recapitulatifs = new CopyOnWriteArrayList<>();

    @Start
    void start(Stage stage) throws IOException {
        injecteur = BancDeRecette.surLeChrome()
                .taille(1400, 900)
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .surLaPlateformeDeTest("observatrice")
                .semer(this::semerLaNuitTraiteeEtReliee)
                .ouvrir(inj ->
                        inj.getInstance(NavigationAudio.class).ouvrir(new SourceObservations.ParPassage(contexte)))
                .montrer(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    private void semerLaNuitTraiteeEtReliee(Injector inj) {
        SourceDeDonnees source = inj.getInstance(SourceDeDonnees.class);
        Path workspace = inj.getInstance(Workspace.class).racine();
        try {
            Long idPassage = JeuDeDonneesPassage.dans(source)
                    .utilisateur(ID_USER)
                    .carre(CARRE)
                    .nomSite(NOM_SITE)
                    .point(POINT)
                    .nuit(NUMERO_PASSAGE, ANNEE, "2026-06-15")
                    .statut(StatutWorkflow.DEPOSE)
                    .verdict(Verdict.OK)
                    .semerPassage()
                    .idPassage();

            Path racine = workspace.resolve(PREFIXE.nomDossierSession());
            Files.createDirectories(racine.resolve("transformes"));
            Long idSession = new SessionDao(source)
                    .insert(new SessionDEnregistrement(null, racine.toString(), null, 4096L, idPassage))
                    .id();
            Long idOriginal = new EnregistrementOriginalDao(source)
                    .insert(new EnregistrementOriginal(
                            null,
                            "PaRec_20260615_220000.wav",
                            "bruts/PaRec_20260615_220000.wav",
                            12.0,
                            384000,
                            null,
                            idSession))
                    .id();
            SequenceDao sequences = new SequenceDao(source);
            for (int rang = 0; rang < SEQUENCES.size(); rang++) {
                String nom = SEQUENCES.get(rang) + ".wav";
                Files.writeString(racine.resolve("transformes").resolve(nom), "sequence");
                sequences.insert(new SequenceDEcoute(
                        null, nom, idOriginal, rang, rang * 5.0, 5.0, "transformes/" + nom, true, idSession));
            }

            inj.getInstance(LienVigieChiroDao.class)
                    .upsert(new LienVigieChiro(
                            LienVigieChiro.ENTITE_PASSAGE,
                            String.valueOf(idPassage),
                            PlateformeDeTest.acces().id("participations:nuit-traitee"),
                            false));
            // Ce que le produit fait à la connexion : relier ses codes de taxon aux identifiants de la
            // plateforme. Sans cela, la correction serait écartée « hors référentiel ».
            inj.getInstance(RapprochementTaxons.class).synchroniser(inj.getInstance(ClientVigieChiro.class));

            contexte = new ContextePassage(idPassage, NUMERO_PASSAGE, new ContexteSite(CARRE, POINT, NOM_SITE));
        } catch (IOException e) {
            throw new IllegalStateException("le semis de la nuit traitée a échoué", e);
        }
    }

    @Test
    @CasDeRecette(
            value = {"S4-90", "S4-92"},
            portee = Portee.HORS_APPLICATION,
            reserve =
                    "Le récapitulatif de confirmation, sur lequel S4-92 se juge, est lu par le banc et ne paraît pas à l'image"
                            + " : le clip montre les deux publications et leur compte rendu, pas la phrase qui dit que la seconde ne rapatrie plus rien.")
    @DisplayName("S4-90 et S4-92 · une correction publiée arrive sans écart, et republier ne rapatrie plus rien")
    void publier_une_correction_puis_la_republier(FxRobot robot) {
        Attente.surLeFil(
                () -> controleur().confirmateur().definir(message -> {
                    recapitulatifs.add(message);
                    return true;
                }),
                "poser le confirmateur de l'écran",
                5_000L);

        // ─── le préalable : les observations de la nuit, telles que la plateforme les rend ───────
        Respiration.avantLeGeste(robot);
        GesteVisible.choisir(robot, MENU_ACTIONS, IMPORTER);
        TableView<?> table = robot.lookup("#tableObservations").queryAs(TableView.class);
        Attente.queSurLeFil(
                () -> table.getItems().size() == SEQUENCES.size(),
                "l'import depuis la plateforme de test n'a pas rendu les deux observations",
                REPONSE_DU_SERVEUR_MS);

        // ─── la correction, et la certitude sans laquelle rien ne part ───────────────────────────
        robot.interact(() -> table.getSelectionModel().select(0));
        ComboBox<Taxon> choix = robot.lookup("#choixTaxon").queryAs(ComboBox.class);
        Taxon retenu = Attente.surLeFil(
                () -> choix.getItems().stream()
                        .filter(taxon -> TAXON_RETENU.equals(taxon.code()))
                        .findFirst()
                        .orElse(null),
                "chercher le second taxon dans la liste",
                5_000L);
        assertThat(retenu)
                .as("le référentiel local doit porter « %s », le second taxon de l'état de départ", TAXON_RETENU)
                .isNotNull();
        robot.interact(() -> choix.getSelectionModel().select(retenu));
        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#btnCorriger");
        GesteVisible.choisir(robot, "#menuCertitude", "Sûr");

        // ─── S4-90 · la publication, menée à son terme ───────────────────────────────────────────
        Respiration.avantLeGeste(robot);
        GesteVisible.choisir(robot, MENU_ACTIONS, PUBLIER);
        attendreLaFinDeLaPublication(robot, 1, "la première publication");
        String premier = compteRendu(robot);
        System.out.printf("  première publication : %s%n", premier.replace("\n", " / "));

        assertThat(recapitulatifs)
                .as("la première publication a demandé une confirmation, et une seule")
                .hasSize(1);
        assertThat(recapitulatifs.getFirst())
                .as("le CSV ne porte aucun identifiant : la première publication annonce qu'elle va les rapatrier")
                .contains(ANCRAGE_A_VENIR);
        assertThat(premier).as("la correction est arrivée sur la plateforme").contains("1 publiées");
        assertThat(premier)
                .as(
                        "S4-90 : aucun écart « sans ancrage », la publication est allée chercher l'identifiant."
                                + "%nLe compte rendu dit : %s",
                        premier)
                .doesNotContainIgnoringCase("sans ancrage")
                .doesNotContainIgnoringCase("hors référentiel")
                .doesNotContainIgnoringCase("refusée");
        Respiration.surLeMomentCle(robot);

        // ─── S4-92 · republier aussitôt ──────────────────────────────────────────────────────────
        GesteVisible.choisir(robot, MENU_ACTIONS, PUBLIER);
        attendreLaFinDeLaPublication(robot, 2, "la seconde publication");

        assertThat(recapitulatifs.get(1))
                .as(
                        "S4-92 : l'identifiant est resté en base, la seconde publication ne repasse pas par sa"
                                + " récupération.%nElle a dit : %s",
                        recapitulatifs.get(1))
                .doesNotContain(ANCRAGE_A_VENIR)
                .contains("Publier 1 correction(s)");
        assertThat(compteRendu(robot))
                .as("et elle arrive comme la première")
                .contains("1 publiées")
                .doesNotContainIgnoringCase("sans ancrage");
        Respiration.surLeMomentCle(robot);
    }

    /// Attend qu'une publication ait été confirmée PUIS soit finie.
    ///
    /// Le compte rendu ne suffit pas à le dire : à la seconde publication, la zone porte déjà celui de
    /// la première. Ce qui distingue « finie » de « pas encore partie » est l'entrée de menu, que
    /// l'écran grise tant qu'une publication est en cours.
    private void attendreLaFinDeLaPublication(FxRobot robot, int rang, String laquelle) {
        Attente.que(
                () -> recapitulatifs.size() == rang,
                laquelle + " n'a demandé aucune confirmation",
                REPONSE_DU_SERVEUR_MS);
        MenuButton menu = robot.lookup(MENU_ACTIONS).queryAs(MenuButton.class);
        Attente.queSurLeFil(
                () -> menu.getItems().stream()
                        .filter(entree -> "itemPublierCorrections".equals(entree.getId()))
                        .noneMatch(MenuItem::isDisable),
                laquelle + " ne s'est jamais terminée",
                REPONSE_DU_SERVEUR_MS);
        Attente.queSurLeFil(
                () -> compteRendu(robot).contains("publiées"),
                laquelle + " n'a rendu aucun compte",
                REPONSE_DU_SERVEUR_MS);
    }

    private SonsValidationController controleur() {
        Object courant =
                injecteur.getInstance(Navigateur.class).historique().getLast().controleur();
        assertThat(courant)
                .as("l'écran affiché doit être celui de la validation des sons")
                .isInstanceOf(SonsValidationController.class);
        return (SonsValidationController) courant;
    }

    /// Tout le texte de la zone où la publication rend compte, ou le vide tant qu'elle se tait.
    private static String compteRendu(FxRobot robot) {
        Node zone = robot.lookup("#zonePublierCorrections").tryQuery().orElse(null);
        if (!(zone instanceof Parent parent) || !zone.isVisible()) {
            return "";
        }
        StringBuilder dit = new StringBuilder();
        collecter(parent, dit);
        return dit.toString();
    }

    private static void collecter(Node noeud, StringBuilder dit) {
        if (noeud instanceof Labeled libelle
                && libelle.getText() != null
                && !libelle.getText().isBlank()) {
            dit.append(libelle.getText()).append('\n');
        }
        if (noeud instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(enfant -> collecter(enfant, dit));
        }
    }
}
