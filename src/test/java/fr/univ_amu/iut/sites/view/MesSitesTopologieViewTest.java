package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.SansEcritureHorsDuFil;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import java.io.IOException;
import java.util.Locale;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// « Mes sites » ne construit pas plus de cartes à cent cinquante carrés qu'à quinze (#6172).
///
/// L'objectif O5 demande que l'ouverture des écrans de la topologie **ne croisse pas** avec le nombre
/// de carrés. Mesuré le 2026-10-13, l'ouverture de cet écran passait de 79 à 100 ms à quinze carrés à
/// 428 à 487 ms à cent cinquante, et ce qui croissait était la construction des cartes, puis leur CSS
/// et leur mise en page : une carte par site, toutes bâties, quand six ou sept tiennent dans la
/// fenêtre.
///
/// Ce garde compte des cartes, jamais des millisecondes, pour la raison que l'ADR 4251 donne aux
/// requêtes : un compte est déterministe, un chronomètre se noie dans la charge du poste. Les
/// millisecondes restent au banc de l'IHM (`BancIhmO5Test#o5_topologie`), lancé à la main.
@ExtendWith({ApplicationExtension.class, SansExceptionAvalee.class, SansEcritureHorsDuFil.class})
class MesSitesTopologieViewTest {

    private static final String ID_USER = "u-test";
    private static final int PEU = 15;
    private static final int BEAUCOUP = 150;

    private Injector injector;

    @Start
    void start(Stage stage) throws IOException {
        injector = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.SYNCHRONE)
                .semer(inj -> semerDesCarres(inj, 0, PEU))
                .ouvrir(inj -> inj.getInstance(NavigationSites.class).ouvrirAccueil())
                .montrer(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#6172 : à 150 carrés, Mes sites ne construit pas plus de cartes qu'à 15")
    void le_nombre_de_cartes_construites_ne_suit_pas_le_nombre_de_carres(FxRobot robot) {
        int aQuinze = cartesConstruites(robot);
        assertThat(aQuinze).as("à quinze carrés, l'écran montre des cartes").isPositive();

        semerDesCarres(injector, PEU, BEAUCOUP);
        robot.interact(() -> injector.getInstance(NavigationSites.class).ouvrirAccueil());
        WaitForAsyncUtils.waitForFxEvents();
        int aCentCinquante = cartesConstruites(robot);

        assertThat(aCentCinquante)
                .as("cartes construites à %d carrés, contre %d à %d carrés", BEAUCOUP, aQuinze, PEU)
                .isLessThanOrEqualTo(aQuinze)
                .isLessThan(BEAUCOUP);
        // Moins de cartes, mais pas moins de sites : la liste les porte tous, elle ne bâtit que ce
        // qu'on voit. Sans cette moitié, un écran qui n'en lirait que dix tiendrait le compte.
        assertThat(robot.lookup("#listeCartes").queryAs(ListView.class).getItems())
                .as("la liste porte tous les sites, construits ou non")
                .hasSize(BEAUCOUP);
    }

    private static int cartesConstruites(FxRobot robot) {
        return robot.lookup(".carte-site").queryAll().size();
    }

    /// Par la fixture et non par `ServiceSites` : le service annonce chaque création, et l'écran
    /// ouvert se relirait à chaque carré. La fixture écrit sans rien annoncer, et le test rouvre
    /// l'écran lui-même, comme le banc.
    private static void semerDesCarres(Injector inj, int depuis, int jusqua) {
        SourceDeDonnees source = inj.getInstance(SourceDeDonnees.class);
        for (int i = depuis; i < jusqua; i++) {
            String numero = String.format(Locale.ROOT, "%06d", 640380 + i);
            for (String code : new String[] {"A1", "A2"}) {
                JeuDeDonneesPassage.dans(source)
                        .utilisateur(ID_USER)
                        .carre(numero)
                        .nomSite("Carré " + i)
                        .point(code)
                        .semerSiteEtPoint();
            }
        }
    }
}
