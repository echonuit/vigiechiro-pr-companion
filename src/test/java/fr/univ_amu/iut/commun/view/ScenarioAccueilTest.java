package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.recette.CadreVisible;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.FenetreDuBanc;
import fr.univ_amu.iut.recette.Respiration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuButton;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le **premier écran**, joué sur le vrai chrome : `S1-01` et `S1-28` (#4138).
///
/// ## Ce que ces cas n'avaient pas
///
/// Ils étaient portés par `AccueilApparenceTest`, `ContratCartesAccueilTest` et `ActionMenuWiringTest`,
/// dont aucun n'ouvre de fenêtre : leurs lecteurs sur la page de recette montraient un rectangle noir.
/// Or c'est le premier écran du produit, celui que quelqu'un voit avant tout le reste.
///
/// ## `S1-01` : la moitié qui manquait est le clic
///
/// > Les cartes sont **contribuées par les features** : ne pas figer leur liste ici, mais vérifier que
/// > chacune de celles qui s'affichent porte un intitulé, une destination annoncée, et **ouvre bien ce
/// > qu'elle annonce**.
///
/// L'intitulé et la destination étaient gardés par `ContratCartesAccueilTest`, sur le contrat Guice.
/// « Ouvre bien ce qu'elle annonce » ne l'était nulle part : c'est un **geste**, et il demande un écran.
///
/// ⚠️ La liste ne se fige pas. Le scénario parcourt les cartes **telles qu'elles s'affichent** : nommer
/// les sept d'aujourd'hui ferait rougir ce cas au premier module ajouté, ce que la session interdit
/// explicitement. Ce qui est vérifié est une **propriété de chaque carte**, quel que soit leur nombre.
///
/// ## Ce qui est vérifié, et ce qui ne peut pas l'être
///
/// Le premier jet exigeait que le fil d'Ariane **nomme la carte**. Mesuré, six des sept cartes le font :
///
/// | carte | fil d'Ariane |
/// |---|---|
/// | Mes sites, Carte & passages, Ma saison, Audit de cohérence, Espèces & observations | le même libellé |
/// | Sons & validation | « Sons de référence » |
///
/// La septième n'est pas en faute, et l'assertion l'était. La vue audio est **une** et s'ouvre sur
/// plusieurs corpus (un passage, un lot, une espèce, les références) : son fil nomme donc le **contenu**
/// qu'on regarde, pas l'écran qui le porte. Exiger l'égalité aurait figé une règle que le socle ne
/// promet pas, et il aurait fallu une liste d'exceptions - exactement ce que la session interdit.
///
/// Ce qui est vérifié est donc ce que le socle garantit vraiment : chaque carte **quitte l'accueil**,
/// mène à un endroit **qui se nomme**, et deux cartes ne mènent pas au même. Cette dernière attrape un
/// câblage manqué, qui est la façon dont ce cas peut réellement casser.
///
/// ## `S1-28` : une absence, et ce qui l'entoure
///
/// Le cas fait juger que « fiche espèce » **n'est plus** dans le menu (#1375). Une absence se prouve mal
/// seule : sur un menu vide, l'assertion serait verte et le produit cassé. Le cas vérifie donc aussi que
/// les autres entrées sont là - c'est le garde-fou, et il vaut plus que l'assertion qu'il protège.
@ExtendWith(ApplicationExtension.class)
class ScenarioAccueilTest {

    /// L'entrée retirée du menu par #1375 : sa source vit dans **Réglages ▸ Général**.
    private static final String ENTREE_RETIREE = "fiche espèce";

    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-scenario-accueil");
        System.setProperty("vigiechiro.workspace", workspace.toString());

        injector = RacineInjecteur.creer();
        new MigrationSchema(injector.getInstance(SourceDeDonnees.class)).migrer();

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        FenetreDuBanc.poser(stage, loader.load(), 1180, 900);
        FenetreDuBanc.afficher(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @CasDeRecette("S1-01")
    @DisplayName("S1-01 · chaque carte de l'accueil ouvre l'écran qu'elle annonce, et le fil d'Ariane le nomme")
    void chaque_carte_ouvre_ce_qu_elle_annonce(FxRobot robot) {
        WaitForAsyncUtils.waitForFxEvents();
        List<String> intitules = intitulesDesCartes(robot);
        assertThat(intitules)
                .as("un accueil sans carte rendrait ce cas vert sans rien parcourir")
                .isNotEmpty();

        Respiration.leTempsDeLire(robot);

        List<String> destinations = new ArrayList<>();
        for (String intitule : intitules) {
            Node carte = carteIntitulee(robot, intitule);
            CadreVisible.amener(carte, robot);
            Respiration.avantLeGeste(robot);

            robot.clickOn(carte);
            WaitForAsyncUtils.waitForFxEvents();
            Respiration.apresLeGeste(robot);

            assertThat(robot.lookup(".carte-activite").queryAll())
                    .as("la carte « %s » a-t-elle ouvert quoi que ce soit ? L'accueil est encore là", intitule)
                    .isEmpty();
            String destination = derniereEtape(robot);
            assertThat(destination)
                    .as("la carte « %s » mène quelque part, et cet endroit doit se nommer", intitule)
                    .isNotBlank();
            destinations.add(destination);

            robot.clickOn("#boutonRetour");
            WaitForAsyncUtils.waitForFxEvents();
        }

        assertThat(destinations)
                .as("deux cartes qui mènent au même endroit seraient un câblage manqué, et l'accueil"
                        + " promettrait deux entrées pour une")
                .doesNotHaveDuplicates();

        assertThat(intitulesDesCartes(robot))
                .as("le parcours revient d'où il est parti : l'accueil est là, avec ses cartes")
                .containsExactlyElementsOf(intitules);
    }

    @Test
    @CasDeRecette("S1-28")
    @DisplayName("S1-28 · le menu ☰ ne porte plus « fiche espèce », et porte toujours le reste")
    void le_menu_ne_porte_plus_la_fiche_espece(FxRobot robot) {
        MenuButton menu = robot.lookup("#menuOutils").queryAs(MenuButton.class);
        CadreVisible.amener(menu, robot);
        Respiration.avantLeGeste(robot);

        robot.clickOn(menu);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.leTempsDeLire(robot);

        List<String> entrees = menu.getItems().stream()
                .map(item -> item.getText())
                .filter(texte -> texte != null && !texte.isBlank())
                .toList();

        // ⚠️ D'abord ce qui rend l'absence significative. Sur un menu vide, l'assertion suivante serait
        // verte et le produit cassé : c'est le défaut « un test qui verrouille une erreur », et il se
        // prévient en nommant ce qui doit rester.
        assertThat(entrees)
                .as("le menu garde ce qu'on ne trouve pas ailleurs : une absence sur un menu vide ne"
                        + " prouverait rien")
                .hasSizeGreaterThan(4);
        assertThat(entrees)
                .as("#1375 : la source des fiches espèces vit dans Réglages ▸ Général, plus ici")
                .noneMatch(entree -> entree.toLowerCase(java.util.Locale.ROOT).contains(ENTREE_RETIREE));
    }

    /// Les intitulés des cartes **telles qu'elles s'affichent**, dans leur ordre d'affichage.
    private static List<String> intitulesDesCartes(FxRobot robot) {
        // ⚠️ Le titre d'une carte est un `Text`, non un `Label` : `CartesAccueil` l'a changé en #2046
        // pour que l'enroulement soit fiable, faute de quoi « Audit de cohére… » restait tronqué sur le
        // premier écran. Le lire comme un Label casse à la conversion.
        return robot.lookup(".carte-activite-titre").queryAllAs(Text.class).stream()
                .map(Text::getText)
                .toList();
    }

    /// La carte portant `intitule`, ou une erreur qui la nomme.
    ///
    /// ⚠️ Par le libellé, jamais par le rang. Viser « la troisième carte » rendrait un clip juste sous
    /// une légende fausse le jour où une feature s'intercale, et les cartes sont contribuées.
    private static Node carteIntitulee(FxRobot robot, String intitule) {
        return robot.lookup(".carte-activite").queryAll().stream()
                .filter(carte -> carte.lookupAll(".carte-activite-titre").stream()
                        .anyMatch(noeud -> noeud instanceof Text texte && intitule.equals(texte.getText())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune carte d'accueil intitulée « " + intitule + " »"));
    }

    /// La dernière étape du fil d'Ariane : l'endroit où l'on vient d'arriver.
    private static String derniereEtape(FxRobot robot) {
        List<String> etapes = segmentsDuFilAriane(robot).stream()
                .filter(texte -> !"\u203a".equals(texte) && !"\u2026".equals(texte))
                .toList();
        return etapes.isEmpty() ? "" : etapes.getLast();
    }

    /// Ce que le fil d'Ariane **écrit**, séparateurs et élisions compris.
    ///
    /// ⚠️ On ramasse tous les noeuds étiquetés plutôt que de viser une classe CSS. Un sélecteur à
    /// virgule (`.label, .hyperlink`) n'est pas interprété par le `lookup` de TestFX : il rendait une
    /// liste **vide**, et l'assertion échouait en accusant le produit alors que le fil disait bien
    /// « Accueil › … › Mes sites ».
    private static List<String> segmentsDuFilAriane(FxRobot robot) {
        return robot.lookup("#filAriane").query().lookupAll("*").stream()
                .filter(Labeled.class::isInstance)
                .map(noeud -> ((Labeled) noeud).getText())
                .filter(texte -> texte != null && !texte.isBlank())
                .toList();
    }
}
