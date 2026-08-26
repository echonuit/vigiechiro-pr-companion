package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le chrome sait amener une zone dans le champ (#1486).
///
/// Le défilement appartient au **ScrollPane central du chrome**, pas aux écrans : une feature ne peut
/// pas l'atteindre, et ne doit pas. Ce port est le troisième de la famille `*Chrome`, après le voile
/// d'occupation et le bandeau d'annonce, et il en suit le patron - installé par [MainController],
/// consommé par injection, **sans effet** quand le chrome est absent (outils, captures).
@ExtendWith(ApplicationExtension.class)
class DefilementChromeTest {

    private ScrollPane defilement;
    private Label cible;
    private Label voisin;

    @Start
    void start(Stage stage) {
        cible = new Label("la zone qu'on veut voir");
        // Un voisin JUSTE SOUS la cible : quand la cible est amenée en haut du champ, il s'y trouve
        // aussi. C'est le seul montage qui distingue « ne rien faire » de « recalculer la même chose ».
        voisin = new Label("son voisin, visible en même temps");
        VBox contenu = new VBox(remplissage(), remplissage(), remplissage(), cible, voisin, remplissage());
        contenu.setPadding(new Insets(0));
        defilement = new ScrollPane(contenu);
        defilement.setFitToWidth(true);
        FenetreAjustable.poser(stage, defilement, 400, 200);
        FenetreAjustable.afficher(stage);
    }

    /// Un bloc assez haut pour que le contenu dépasse largement la fenêtre : sans dépassement, il n'y a
    /// rien à révéler et le port doit justement ne rien faire.
    private static Region remplissage() {
        Region bloc = new Region();
        bloc.setMinHeight(400);
        return bloc;
    }

    @Test
    @DisplayName("#1486 : révéler une zone du bas y amène le défilement")
    void revele_amene_la_zone_dans_le_champ(FxRobot robot) {
        DefilementChrome chrome = new DefilementChrome();
        robot.interact(() -> chrome.installer(defilement));

        assertThat(defilement.getVvalue()).as("au départ, en haut").isZero();

        robot.interact(() -> chrome.revele(cible));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(defilement.getVvalue())
                .as("la zone est tout en bas : le défilement doit avoir bougé")
                .isGreaterThan(0.5);
    }

    @Test
    @DisplayName("#1486 : révéler une zone DÉJÀ dans le champ ne déplace rien")
    void zone_deja_visible_ne_bouge_pas(FxRobot robot) {
        DefilementChrome chrome = new DefilementChrome();
        robot.interact(() -> chrome.installer(defilement));

        // On amène d'abord la cible dans le champ : c'est le seul moyen d'obtenir un cas où la zone
        // visée est visible ET le défilement ailleurs qu'en haut. Une cible en position zéro rendrait
        // ce test vide, puisque « ne pas bouger » et « défiler vers zéro » y donnent le même résultat.
        robot.interact(() -> chrome.revele(cible));
        WaitForAsyncUtils.waitForFxEvents();
        double apresPremierAppel = defilement.getVvalue();
        assertThat(apresPremierAppel).isGreaterThan(0.5);

        robot.interact(() -> chrome.revele(voisin));
        WaitForAsyncUtils.waitForFxEvents();

        // Le voisin est DÉJÀ dans le champ, mais ailleurs qu'à son sommet : sans garde, on le hisserait
        // en haut, donc on défilerait. Rappeler la même cible ne prouverait rien - le calcul rendrait
        // deux fois la même valeur, garde ou pas.
        //
        // Un défilement qui s'agite pour rien est plus déroutant qu'un défilement absent.
        assertThat(defilement.getVvalue()).isEqualTo(apresPremierAppel);
    }

    @Test
    @DisplayName("#1486 : révéler sans chrome installé ne fait rien et ne casse rien")
    void sans_chrome_revele_ne_fait_rien() {
        DefilementChrome chrome = new DefilementChrome();

        // Les outils de capture et la CLI montent des injecteurs partiels, sans chrome. Le port doit
        // s'y taire, comme le voile d'occupation le fait déjà.
        chrome.revele(cible);
        chrome.revele(null);
    }

    @Test
    @DisplayName("Révéler « rien » ne fait rien")
    void cible_nulle_ne_fait_rien(FxRobot robot) {
        DefilementChrome chrome = new DefilementChrome();
        robot.interact(() -> {
            chrome.installer(defilement);
            chrome.revele(null);
        });

        assertThat(defilement.getVvalue()).isZero();
    }
}
