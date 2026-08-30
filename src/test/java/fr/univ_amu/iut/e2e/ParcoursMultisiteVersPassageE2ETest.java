package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.fixture.JournalDeCapteur;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.multisite.view.NavigationMultisite;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// **Test E2E de parcours (P5)** : la **vue agrégée M-Multisite** et son **drill-down** vers
/// **M-Passage**, sur le vrai chrome. On ouvre l'écran multi-sites (navigation réelle), on vérifie
/// que le passage importé y figure, puis un **double-clic** sur sa ligne ouvre M-Passage via le
/// contrat socle `OuvrirPassage` (`multisite → passage`).
@ExtendWith(ApplicationExtension.class)
class ParcoursMultisiteVersPassageE2ETest {

    private static final String ID_USER = "u-e2e-ms";
    private static final String SERIE = "1925492";
    private static final int FREQUENCE_WAV = 384_000;
    private static final int TRAMES = 576_000;
    /// La nuit **telle que la colonne la rend** depuis #4019 : la date se lit en français, et
    /// c'est ce texte-là que vise le double-clic. La forme ISO ne désigne plus aucun nœud.
    private static final String DATE_NUIT = "22/04/2026"; // date du journal → cellule unique du tableau
    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-ms");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur E2E"));
        ServiceSites sites = injector.getInstance(ServiceSites.class);
        Site site = sites.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        PointDEcoute point = sites.ajouterPoint(site.id(), "A1", 43.4010, -1.5740, null);
        Path sd = creerNuitSynthetique(workspace.resolve("sd"));
        injector.getInstance(ServiceImport.class).importer(sd, point.id(), new Prefixe("640380", 2026, 1, "A1"));

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        // 900, et non 1280 : c'est `TailleOuverture.LARGEUR_MINIMALE`, le plancher que l'application
        // s'autorise et la largeur à laquelle les runners tournent réellement - l'écran headless étant
        // plus petit que la scène demandée, la fenêtre y est rabattue. Déclarer 1280 ne le donnait pas :
        // cela rendait seulement le défaut INTERMITTENT, selon que le rabattement s'appliquait ou non.
        //
        // À 900, la table déborde de façon reproductible et le test exerce ce que la CI exerce. C'est le
        // parti pris de `CarteHorsCadreAccueilTest` (#3929), transposé ici (#3932).
        FenetreAjustable.poser(stage, racine, 900, 860);
        FenetreAjustable.afficher(stage);
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("M-Multisite : le passage figure dans la vue agrégée, le double-clic ouvre M-Passage")
    void multisite_drill_vers_passage(FxRobot robot) throws TimeoutException {
        NavigationViewModel navigation = injector.getInstance(NavigationViewModel.class);

        // 1) Ouvrir la vue agrégée multi-sites (navigation socle réelle). Le chargement de l'agrégat
        // tourne hors du fil JavaFX (occupation.occuper, MultisiteController) : sans cette attente, la
        // table est encore vide quand l'assertion tombe, un échec qui ne se produit que sur une machine
        // lente, donc en CI.
        robot.interact(() -> injector.getInstance(NavigationMultisite.class).ouvrirAccueil());
        TableView<?> table = robot.lookup("#tableLignes").queryAs(TableView.class);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !table.getItems().isEmpty());
        assertThat(table.getItems()).hasSize(1);
        assertThat(navigation.getVueCourante()).isEqualTo("multisite");

        // 2) Double-clic sur la ligne (cellule date, unique) → drill-down vers M-Passage.
        doubleClicVersPassage(robot, navigation);

        assertThat(navigation.getVueCourante()).isEqualTo("passage");
        Button verifier = robot.lookup("#boutonVerifier").queryAs(Button.class);
        assertThat(verifier).isNotNull();
        // navigation.getVueCourante() bascule dès le changement d'écran, avant que le chargement du
        // passage (lui aussi asynchrone) n'ait appliqué ses données.
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !verifier.isDisabled());

        // 3) Le fil d'Ariane GLOBAL situe le passage sous son site, même atteint via multisite (#140) :
        // Accueil › Mes sites › Carré 640380 › Détails du passage N° 1 (emplacement, pas l'historique).
        HBox fil = robot.lookup("#filAriane").queryAs(HBox.class);
        // Le fil s'attend LUI-MÊME. Ce banc concluait qu'attendre le bouton suffisait, les deux venant
        // du « même callback » : c'est faux, et cela lui a coûté quatre rouges sur 1 150 passages
        // (#4813, mesuré par #4811). Le bouton suit `PassageViewModel.verificationDisponible`, posé à
        // la fin du chargement du passage ; le fil suit `MainController.rafraichirNavigation`, sur un
        // listener de `navigateur.historique()`. Deux chaînes, deux moments.
        Attente.que(() -> !segmentsDuFil(fil).isEmpty(), "que le fil d'Ariane porte ses segments");
        var libelles = segmentsDuFil(fil);
        assertThat(libelles)
                .contains("Carré 640380")
                .anySatisfy(t -> assertThat(t).startsWith("Détails du passage"));
    }

    @Test
    @DisplayName("Multisite → Passage → enfant : cliquer « Détails du passage » dans le fil rouvre M-Passage")
    void multisite_enfant_clic_fil_rouvre_le_passage(FxRobot robot) throws TimeoutException {
        NavigationViewModel navigation = injector.getInstance(NavigationViewModel.class);

        // 1) Multisite → double-clic → M-Passage (atteint sans passer par le site). Mêmes attentes que
        // multisite_drill_vers_passage : chargement de l'agrégat puis chargement du passage, tous deux
        // asynchrones.
        robot.interact(() -> injector.getInstance(NavigationMultisite.class).ouvrirAccueil());
        WaitForAsyncUtils.waitFor(
                5,
                TimeUnit.SECONDS,
                () -> !robot.lookup("#tableLignes")
                        .queryAs(TableView.class)
                        .getItems()
                        .isEmpty());
        doubleClicVersPassage(robot, navigation);
        assertThat(navigation.getVueCourante()).isEqualTo("passage");
        Button verifier = robot.lookup("#boutonVerifier").queryAs(Button.class);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !verifier.isDisabled());

        // 2) M-Passage → écran enfant (carte « Diagnostic matériel »).
        robot.interact(robot.lookup("#boutonDiagnostic").queryButton()::fire);
        assertThat(navigation.getVueCourante()).isEqualTo("diagnostic");

        // 3) Sur l'enfant, le fil situe le passage sous son site (#140) ; « Détails du passage N° 1 »
        // est un segment ANCÊTRE cliquable (Hyperlink), pas le segment courant.
        Hyperlink segmentPassage = filSegment(robot, "Détails du passage N° 1");

        // 4) Cliquer ce segment exerce le Runnable du Lieu (OuvrirPassage) et rouvre réellement
        // M-Passage : y compris depuis un enfant atteint via la vue multi-sites.
        robot.interact(segmentPassage::fire);
        assertThat(navigation.getVueCourante()).isEqualTo("passage");
        assertThat(robot.lookup("#boutonVerifier").tryQuery()).isPresent();
    }

    /// Segment ancêtre (cliquable) du fil d'Ariane du chrome portant le libellé donné.
    private static Hyperlink filSegment(FxRobot robot, String libelle) {
        HBox fil = robot.lookup("#filAriane").queryAs(HBox.class);
        return fil.getChildren().stream()
                .filter(Hyperlink.class::isInstance)
                .map(Hyperlink.class::cast)
                .filter(segment -> libelle.equals(segment.getText()))
                .findFirst()
                .orElseThrow();
    }

    /// Les libellés du fil d'Ariane : segments cliquables et segment courant. L'attente et l'assertion
    /// lisent la même chose, sans quoi la première rendrait la main sur un prédicat plus large.
    private static List<String> segmentsDuFil(HBox fil) {
        return fil.getChildren().stream()
                .filter(n -> n.getStyleClass().contains("fil-ariane-segment")
                        || n.getStyleClass().contains("fil-ariane-courant"))
                .map(n -> ((Labeled) n).getText())
                .toList();
    }

    /// Double-clic « robuste » de drill-down vers M-Passage. Sous charge, TestFX peut ne pas enregistrer le
    /// double-clic ou naviguer avec un différé, laissant l'écran sur la vue intermédiaire quand l'assertion
    /// tombe : on attend donc que la navigation aboutisse, avec quelques réessais.
    ///
    /// Entre le moment où `table.getItems()` n'est plus vide et celui où TestFX peut localiser la cellule, il
    /// reste une passe de layout à consommer, faute de quoi `doubleClickOn` lève `FxRobotException` avant le
    /// premier essai ; la cellule est donc attendue interrogeable à chaque essai.
    ///
    /// **Épuiser les essais lève, au lieu de rendre la main.** L'abandon silencieux présentait l'échec comme
    /// « attendu passage, obtenu multisite », un bug de navigation apparent là où le robot n'avait pas
    /// abouti : deux échecs ont été lus ainsi (#3823). Motif de l'ADR 2213.
    private static void doubleClicVersPassage(FxRobot robot, NavigationViewModel navigation) {
        TableView<?> table = robot.lookup("#tableLignes").queryAs(TableView.class);
        Throwable derniere = null;
        for (int essai = 1; essai <= 3; essai++) {
            try {
                // Attendre ce que le clic EXIGE, et non la simple présence du nœud (#3906, jumeau de
                // #3836). `doubleClickOn` filtre par `NodeQueryUtils.isVisible()`, qui demande en plus
                // que le nœud **intersecte le rectangle de la scène** - une cellule déjà dans le graphe
                // mais encore hors cadre passait donc cette attente, et le clic échouait.
                //
                // Attendre le bon prédicat ne suffisait pas : l'attente expirait sans que la cellule
                // entre jamais dans le cadre (#3932). Il faut donc l'y AMENER, et c'est le point de
                // détail qui a coûté un essai : le défilement n'est pas celui du chrome.
                amenerLaColonneDate(robot, table);
                // Le vrai geste, et non `DoubleClicDeterministe` (#4554), pour la même raison
                // qu'au parcours jumeau : c'est le chemin de l'utilisateur qui est éprouvé ici.
                AttenteAvantClic.attendreCliquable(robot, DATE_NUIT, 3, null);
                robot.doubleClickOn(DATE_NUIT);
                WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> "passage".equals(navigation.getVueCourante()));
                return;
            } catch (AssertionError | TimeoutException reessai) {
                derniere = reessai;
                // Cellule pas encore rendue ou navigation pas encore aboutie : on retente.
                //
                // `AssertionError` est rattrapée parce qu'`attendreCliquable` lève cela, et non une
                // `TimeoutException` : elle joint son rapport d'état à l'expiration. Ne rattraper que
                // la seconde ferait sortir la première du premier coup, et cette boucle de reprise
                // n'aurait plus que l'apparence d'une reprise. Aucune autre assertion ne vit dans ce
                // `try` : le seul verdict qui puisse en sortir est celui de l'attente.
            }
        }
        // Ce message rapportait un DÉLAI et concluait « c'est le robot qui n'a pas abouti sous charge ».
        // Une occurrence de plus, consignée dans #3911, a démenti cette conclusion : depuis que
        // l'attente exige le bon prédicat, elle expire au lieu de laisser partir un clic - donc la
        // cellule n'entre **jamais** dans le cadre en neuf secondes, et ce n'est pas une question de
        // patience. Il manquait la seule information qui départage : **où** est la cellule.
        //
        // Un dispositif qui ne peut pas conclure rapporte ce qu'il a vu (ADR 2213), et ne conclut donc
        // pas à sa place. Celui-ci concluait.
        throw new AssertionError(
                "Le double-clic vers le passage n'a pas abouti après 3 essais de 3 s.\n"
                        + "Vue courante : " + navigation.getVueCourante() + "\n"
                        + AttenteAvantClic.etatObserve(robot, DATE_NUIT) + "\n"
                        + "Deux causes possibles, que ce message ne tranche pas : la cellule n'est jamais entrée"
                        + " dans le cadre de la scène (lire ses bornes ci-dessus), ou la navigation n'a pas"
                        + " abouti après un double-clic pourtant parti.",
                derniere);
    }

    /// Amène la colonne « Date » dans le viewport de la table, sans quoi sa cellule reste hors cadre et le
    /// double-clic ne peut pas partir (#3932).
    ///
    /// Scène ramenée à 900, la largeur d'un runner headless, la table rend :
    ///
    /// | Ce qu'on lit | Valeur |
    /// |---|---|
    /// | largeur du viewport | **497** |
    /// | somme des onze colonnes | **1235** |
    /// | barre horizontale | **visible**, `max=590`, `valeur=0` |
    /// | « Date » | 7ᵉ colonne, elle commence vers 485 dans un viewport de 497 |
    ///
    /// Le contenu **défile** : c'est au test d'amener sa cible, même conclusion que #3925. Mais **pas le
    /// même défilement** : passer le port `DefilementChrome` (#1486) à [AttenteAvantClic#attendreCliquable]
    /// n'a rien changé ici. Le chrome fait défiler *sa* zone centrale, quand une colonne hors cadre vit dans
    /// le viewport **interne** de la `TableView`. Le geste juste est `scrollToColumn`.
    private static void amenerLaColonneDate(FxRobot robot, TableView<?> table) {
        // `scrollToColumnIndex` et non `scrollToColumn` : le second réclame un
        // `TableColumn<S, ?>` accordé au paramètre de la table, que `TableView<?>` ne peut pas
        // fournir sans capture. L'index dit la même chose sans le détour.
        robot.interact(() -> {
            for (int i = 0; i < table.getColumns().size(); i++) {
                if ("Date".equals(table.getColumns().get(i).getText())) {
                    table.scrollToColumnIndex(i);
                    return;
                }
            }
        });
    }

    private static Path creerNuitSynthetique(Path sd) throws Exception {
        Files.createDirectories(sd);
        // Journal et releve par la fixture (#2868) : le trace complet d'une nuit, la ou ce test
        // n'avait besoin que d'un journal valide. Il n'affirme rien sur son contenu.
        JournalDeCapteur.ecrire(sd, SERIE, LocalDate.of(2026, 4, 22));
        JournalDeCapteur.ecrireReleve(sd, SERIE);
        byte[] pcm = new byte[TRAMES * 2];
        for (int i = 0; i < TRAMES; i++) {
            short e = (short) (((i * 41) % 1000) - 500);
            pcm[2 * i] = (byte) (e & 0xFF);
            pcm[2 * i + 1] = (byte) ((e >> 8) & 0xFF);
        }
        // Writer de production (#2864) : memes octets, et c'est le format que l'application
        // saura relire.
        FichierWav.ecrire(
                sd.resolve("PaRecPR" + SERIE + "_20260422_203922.wav"), 1, FREQUENCE_WAV, 16, pcm, 0, pcm.length);
        return sd;
    }
}
