package fr.univ_amu.iut.lot.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.CauseRefus;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.EchecUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CadreVisible;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.Jugement;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.recette.film.EnregistreurDeFilm;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Le scénario qui **joue** `S4-33`, pour qu'un humain le tranche en regardant (#4055, #4115).
///
/// ## Ce que le cas demande, et ce qu'il refuse
///
/// La session est explicite : « c'est la **lisibilité** de la phrase qu'on juge, pas sa présence : une
/// assertion la trancherait mal ». Le clip doit donc montrer la phrase **telle que l'écran la rend**.
///
/// ## Pourquoi le vrai écran, et non le panneau seul
///
/// La première version montait le seul `PanneauCompteRendu` dans un `VBox` nu. Le raccourci était
/// assumé - « quatre minutes de film pour une phrase » - mais il coûtait ce qui manquait le plus : le
/// **contexte**. On voyait une phrase flotter sur fond clair, sans chrome, sans écran autour, et on ne
/// savait pas ce qu'on regardait. Un cas perceptif se juge à l'oeil ; un panneau sans écran ne se juge
/// pas.
///
/// Ce scénario suit donc la forme des cas `S1` : le **vrai chrome**, le **vrai écran du lot**, le
/// **vrai clic**, et une seule frontière truquée - le port `DepotVigieChiro`, remplacé par un dépôt
/// qui refuse trois unités en 403. Le compte rendu paraît alors dans sa zone de l'écran, là où
/// l'utilisateur le verra.
///
/// ## Ce que la fixture doit réunir
///
/// Le bouton « Déposer » n'est vivant que si le lot est **cohérent** et **prêt**, et si des lignes de
/// suivi existent. La checklist de `VerificationCoherence` exige donc : un verdict qui n'est pas
/// « Inexploitable », des séquences dérivées de chaque original, des noms **préfixés**, et un
/// **journal du capteur** - ce dernier bloquant. Le relevé climatique, lui, ne fait qu'avertir.
///
/// Les fichiers posés sur le disque sont minuscules : la source ne lit que leur **taille**.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioPerceptifRefusDepotTest {

    private static final String ID_USER = "u-scenario";
    private static final String CARRE = "640380";
    private static final String SERIE = "1925492";
    private static final int NUMERO_PASSAGE = 2;
    private static final int ANNEE = 2026;
    private static final Prefixe PREFIXE = new Prefixe(CARRE, ANNEE, NUMERO_PASSAGE, "A1");
    private static final String NOM_ORIGINAL = PREFIXE.nommerOriginal("PaRecPR" + SERIE + "_20260705_213000.wav");

    /// Trois archives refusées, toutes pour la même cause : les droits. C'est l'état de la fixture
    /// `VIGIECHIRO_STUB_REFUS=403` que la session décrit, et le seul où le conseil de reconnexion
    /// s'applique à toutes.
    private static final int REFUSEES = 3;

    private static final String QUALIFIANT = "depotQuiRefuse";

    /// Marge au-dessus et au-dessous de ce qu'on vient lire : une phrase collée au bord se lit mal,
    /// et ce cas fait juger sa lisibilité.
    private static final double AIR_DE_LECTURE = 24;

    private Injector injector;
    private ContextePassage contexte;

    /// Ce que le port d'ouverture a reçu, geste par geste (#4982). Le gestionnaire de fichiers est
    /// l'autre frontière système truquée ici : en ouvrir un vrai sur un runner n'a pas de sens, et
    /// ce qui se vérifie n'est de toute façon pas la fenêtre mais le geste demandé.
    private final List<String> liensOuverts = new ArrayList<>();

    private final List<Path> dossiersOuverts = new ArrayList<>();

    @Start
    void start(Stage stage) throws IOException {
        injector = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                // Asynchrone, comme les autres scénarios perceptifs : en synchrone le dépôt se ferait sur
                // le fil JavaFX, aucune image ne serait rendue, et le passage à juger n'existerait sur
                // aucune trame.
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .remplacer(new AbstractModule() {
                    @Override
                    protected void configure() {
                        // Qualifiant intermédiaire, comme `DepotVigieChiroModule` : sans lui, la cible
                        // de l'`OptionalBinder` se référencerait elle-même.
                        OptionalBinder.newOptionalBinder(binder(), DepotVigieChiro.class)
                                .setBinding()
                                .to(Key.get(DepotVigieChiro.class, Names.named(QUALIFIANT)));
                    }

                    @Provides
                    @Singleton
                    OuvreurDeLien ouvreurDeLien() {
                        return new OuvreurDeLien() {
                            @Override
                            public void ouvrir(String url) {
                                liensOuverts.add(url);
                            }

                            @Override
                            public boolean ouvrirDossier(Path dossier) {
                                dossiersOuverts.add(dossier);
                                return true;
                            }
                        };
                    }

                    @Provides
                    @Singleton
                    @Named(QUALIFIANT)
                    DepotVigieChiro depotQuiRefuse() {
                        // `DepotVigieChiro` est final : pas de sous-classe possible. Le dépôt le mocke
                        // déjà ailleurs (`LotDepotConnecteViewTest`), et c'est la seule frontière truquée
                        // ici - tout le reste du chemin, écran compris, est celui de la production.
                        DepotVigieChiro faux = mock(DepotVigieChiro.class);
                        when(faux.deposer(any(), any(), any(), any())).thenReturn(bilanRefuse());
                        return faux;
                    }
                })
                .semer(inj -> contexte = semerUneNuitPreteADeposer(
                        inj.getInstance(SourceDeDonnees.class),
                        inj.getInstance(Workspace.class).racine()))
                // L'écran du lot est ouvert AVANT `show()`. Ouvert après, l'accueil paraissait une
                // fraction de seconde puis l'écran du lot surgissait sans qu'aucun geste ne l'explique :
                // le clip commençait sur un écran qui n'a rien à voir avec le cas.
                .ouvrir(inj -> inj.getInstance(NavigationLot.class).ouvrir(contexte))
                .montrer(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @CasDeRecette(value = "S4-33", jugement = Jugement.HUMAIN, portee = Portee.A_L_ECRAN)
    @DisplayName("S4-33 · le compte rendu dit le nombre de refus et conseille la reconnexion : à lire")
    void le_compte_rendu_dit_les_refus_et_conseille_la_reconnexion(FxRobot robot) throws Exception {
        // Le dépôt demande confirmation, et le dialogue réel fait `showAndWait`, qui fige TestFX
        // headless : le film s'arrêterait là. Même remède que `LotDepotConnecteViewTest`. Ce qui se voit
        // reste juste, à une chose près qui ne se voit pas : la confirmation n'a pas été demandée.
        ecranDuLot().confirmateur().definir(message -> true);
        Respiration.avantLeGeste(robot);

        CadreVisible.amener(robot.lookup("#btnTeleverser").query(), robot);
        Respiration.entreDeuxGestes(robot);

        robot.clickOn("#btnTeleverser");
        // L'exécuteur est asynchrone : le compte rendu n'est PAS là au retour du clic.
        // On attend LA PHRASE QU'ON AFFIRME, et non un texte voisin. Attendre « Dépôt incomplet »
        // rendait la main dès le titre du compte rendu, alors que les avertissements paraissent à la
        // passe suivante : le test passait sur mon poste et rougissait sur le runner, sur la seule
        // différence de rythme. Une attente qui ne porte pas sur l'assertion ne garde rien.
        Attente.que(
                () -> texteAffiche(robot).contains(REFUSEES + " archive(s) ont été refusées"),
                "le compte des archives refusées s'affiche",
                20 * 1000L);
        // Le moment que ce cas existe pour montrer : la phrase du compte rendu, dont c'est la
        // LISIBILITÉ qu'on juge. Elle demande d'être lue, pas aperçue.
        // Le compte rendu paraît SOUS la ligne de flottaison, et l'écran est revenu en haut quand
        // sa zone est apparue. Sans ce défilement, le clip se termine sur les étapes 1 et 2 et ne
        // montre jamais la phrase qu'il fait juger - c'est ce que la relecture du clip publié a dit.
        CadreVisible.amener(libellePortant(robot, "Reconnectez-vous"), robot);
        Respiration.surLeMomentCle(robot);

        String affiche = texteAffiche(robot);
        assertThat(affiche)
                .as("le titre dit l'état, et « Nuit déposée » serait faux : trois archives manquent")
                .contains("Dépôt incomplet");
        assertThat(affiche)
                .as("le nombre d'archives refusées est dit, sans quoi il faut compter les lignes de la"
                        + " table pour savoir l'ampleur")
                .contains(REFUSEES + " archive(s) ont été refusées");
        assertThat(affiche)
                .as("et le geste qui répare est nommé : ce refus-là tient aux droits, donc une reconnexion suffit")
                .contains("Reconnectez-vous");

        // Et la phrase doit être DANS LE CADRE, pas seulement dans la scène. `lookup` trouve un
        // noeud quelle que soit sa position : le clip publié se terminait sur les étapes 1 et 2, la
        // phrase vivant sous la ligne de flottaison, et toutes les assertions ci-dessus passaient.
        // Un cas perceptif dont le clip ne montre pas son objet ne fait rien juger.
        assertThat(CadreVisible.contient(libellePortant(robot, "Reconnectez-vous")))
                .as("la phrase que ce cas fait juger est visible à l'image, et non sous le pli")
                .isTrue();
    }

    @Test
    @CasDeRecette(
            value = "S4-18",
            portee = Portee.HORS_APPLICATION,
            reserve = "Aucun gestionnaire de fichiers ne s'ouvre sur le banc : ce clip montre le clic,"
                    + " pas la fenêtre qu'il ouvre. Ce qui se vérifie est le GESTE demandé au système -"
                    + " ouvrir un dossier, et non ouvrir un lien - et cela se lit dans l'assertion.")
    @DisplayName("S4-18 · « Ouvrir le dossier » ouvre un DOSSIER, et non un onglet de navigateur")
    void ouvrir_le_dossier_de_depot_demande_un_dossier(FxRobot robot) {
        // Ce cas se joue SUR CET ÉCRAN-LÀ, et c'est tout son sens : le repli manuel s'atteint quand
        // le téléversement vient d'échouer. C'est le moment où un observateur a cliqué ici, reçu un
        // listing de répertoire dans son navigateur, et est reparti chercher ses ZIP à la main
        // (#4982). Le clip le montre sur l'écran qui porte ce repli, et par ses vrais boutons.
        Respiration.avantLeGeste(robot);

        // On GÉNÈRE d'abord, par le vrai bouton : « Ouvrir le dossier » reste désactivé tant qu'il
        // n'y a pas d'archive à montrer, et les archives naissent de ce geste-là. Le premier jet
        // cliquait à l'ouverture de l'écran, sur un bouton grisé - le clic ne portait pas, et rien
        // ne le disait sinon une liste vide au moment d'asserter.
        CadreVisible.amener(robot.lookup("#btnGenererArchives").query(), robot);
        robot.clickOn("#btnGenererArchives");

        Button ouvrir = robot.lookup("#btnOuvrirDepot").queryAs(Button.class);
        Attente.que(
                () -> !ouvrir.isDisabled(), "les archives sont générées, donc il y a un dossier à ouvrir", 20 * 1000L);
        CadreVisible.amener(ouvrir, robot);
        Respiration.entreDeuxGestes(robot);

        robot.clickOn("#btnOuvrirDepot");
        Respiration.surLeMomentCle(robot);

        assertThat(dossiersOuverts)
                .as("le port reçoit un CHEMIN par le geste « dossier », et non une URI de navigateur")
                .hasSize(1);
        assertThat(dossiersOuverts.getFirst()).asString().endsWith("depot");
        assertThat(liensOuverts)
                .as("rien ne part au navigateur : c'est là que le listing de répertoire naissait")
                .isEmpty();
    }

    // --------------------------------------------------------------------------------------------

    /// Trois archives refusées en 403, toutes réarmables par une reconnexion.
    private static BilanDepot bilanRefuse() {
        return new BilanDepot(
                "p-1",
                11,
                List.of(
                        new EchecUnite("Car640380-2026-Pass2-A1-12.zip", "HTTP 403", true, CauseRefus.AUTHENTIFICATION),
                        new EchecUnite("Car640380-2026-Pass2-A1-13.zip", "HTTP 403", true, CauseRefus.AUTHENTIFICATION),
                        new EchecUnite(
                                "Car640380-2026-Pass2-A1-14.zip", "HTTP 403", true, CauseRefus.AUTHENTIFICATION)),
                3_400_000_000L);
    }

    /// Sème la nuit jusqu'à ce que « Déposer » soit vivant, et rend son contexte de navigation.
    private ContextePassage semerUneNuitPreteADeposer(SourceDeDonnees source, Path workspace) throws IOException {
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre(CARRE)
                .nomSite("Étang de la Tuilière")
                .point("A1")
                .enregistreur(SERIE)
                .nuit(NUMERO_PASSAGE, ANNEE, "2026-07-05")
                .statut(StatutWorkflow.PRET_A_DEPOSER)
                .verdict(Verdict.OK)
                .semerPassage();
        Long idPassage = jeu.idPassage();

        Path racine = workspace.resolve(PREFIXE.nomDossierSession());
        Files.createDirectories(racine.resolve("transformes"));
        Long idSession = new SessionDao(source)
                .insert(new SessionDEnregistrement(null, racine.toString(), null, 4096L, idPassage))
                .id();
        Long idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(
                        null, NOM_ORIGINAL, "bruts/" + NOM_ORIGINAL, 12.0, 384000, null, idSession))
                .id();
        SequenceDao sequences = new SequenceDao(source);
        for (int index = 0; index < REFUSEES; index++) {
            String nom = PREFIXE.nommerSequence(NOM_ORIGINAL, index);
            // Le fichier existe pour de vrai : la source de dépôt lit sa TAILLE (#1994). Son contenu,
            // lui, n'est jamais lu - quelques octets suffisent, et le clip n'attend pas.
            Files.writeString(racine.resolve("transformes").resolve(nom), "sequence");
            sequences.insert(new SequenceDEcoute(
                    null, nom, idOriginal, index, index * 5.0, 5.0, "transformes/" + nom, true, idSession));
        }
        // Bloquant s'il manque : « Journal du capteur (LogPR<n>.txt) absent ».
        new JournalDuCapteurDao(source)
                .insert(new JournalDuCapteur(null, "LogPR" + SERIE + ".txt", null, null, idSession));

        // Sans lignes de suivi, `archivesGenerees` est faux et le bouton « Déposer » n'est pas le
        // geste primaire de l'écran (`LotController:440`). Le premier essai s'est arrêté là : l'écran
        // était juste, complet, et le dépôt ne partait pas.
        DepotUniteDao unites = new DepotUniteDao(source);
        for (int index = 0; index < REFUSEES; index++) {
            unites.insert(DepotUnite.aDeposer(
                    idPassage, PREFIXE.nomDossierSession() + "-" + index + ".zip", TypeDepotUnite.ZIP, "2026-07-06"));
        }

        return new ContextePassage(idPassage, NUMERO_PASSAGE, new ContexteSite(CARRE, "A1", "Étang de la Tuilière"));
    }

    /// Le contrôleur de l'écran **en place**, pris sur la pile de navigation.
    private LotController ecranDuLot() {
        Object controleur =
                injector.getInstance(Navigateur.class).historique().getLast().controleur();
        return (LotController) controleur;
    }

    /// Le libellé qui porte `extrait`, ou une erreur qui le nomme.
    private static Node libellePortant(FxRobot robot, String extrait) {
        return robot
                .lookup(node -> node instanceof Labeled libelle
                        && libelle.getText() != null
                        && libelle.getText().contains(extrait))
                .queryAll()
                .stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucun libellé ne porte « " + extrait + " »"));
    }

    /// Tout ce qui porte du texte dans la fenêtre, recollé. Le compte rendu répartit sa phrase entre
    /// plusieurs libellés ; chercher dans un seul supposerait une répartition qui peut changer.
    private static String texteAffiche(FxRobot robot) {
        return robot.lookup(node -> node instanceof Labeled).queryAll().stream()
                .map(node -> ((Labeled) node).getText())
                .filter(texte -> texte != null && !texte.isBlank())
                .reduce("", (tout, texte) -> tout + " " + texte);
    }
}
