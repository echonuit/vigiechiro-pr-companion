package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.outils.LisibiliteCapture;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.PastillesEntieres;
import fr.univ_amu.iut.commun.view.PastillesEntieres.Pastille;
import fr.univ_amu.iut.commun.view.TailleOuverture;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.BancDeRecette;
import java.io.IOException;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Les six cartes d'action de l'écran du passage se lisent en entier (#4834).
///
/// La première carte affichait « Vérifier ... » et « Sound check par ... ». Les bancs passaient : ils
/// lisent `accessibleText`, qui garde le texte entier quand le libellé peint se coupe. Cette classe lit
/// ce que la carte **dessine**, dans le chrome réel et sur une scène habillée, sans quoi elle
/// mesurerait avec la police de la machine.
///
/// ## Ce qui coupait
///
/// Les cartes se partagent la largeur. Quand elle manque, chacune cède la même part et ses libellés
/// passent à la ligne. La carte avait une hauteur fixe qui logeait **un** libellé sur deux lignes, pas
/// les deux : mesuré à 1 180 de large, la première carte est la seule dont le titre (174 px) et le
/// sous-titre (172 px) dépassent tous deux les 166 px disponibles. À 1 100 elles étaient trois, à 900
/// cinq.
///
/// ## Trois largeurs, et pourquoi
///
/// Celle des bancs filmés, où le défaut a été vu, puis les deux que `TailleOuverture` livre : la
/// largeur d'ouverture et la largeur minimale de la fenêtre.
@ExtendWith(ApplicationExtension.class)
class CartesActionEntieresTest {

    private static final double LARGEUR_DES_BANCS = 1180;

    private static final double[] LARGEURS = {
        LARGEUR_DES_BANCS, TailleOuverture.LARGEUR_VOULUE, TailleOuverture.LARGEUR_MINIMALE
    };

    /// Les six cartes, dans l'ordre de l'écran. Une carte retirée par un réglage rendrait le test
    /// muet sur elle : il la compte donc avant de la lire.
    private static final List<String> CARTES = List.of(
            "boutonVerifier",
            "boutonDiagnostic",
            "boutonDepot",
            "boutonValidation",
            "boutonSynthese",
            "boutonActivite");

    private static final long DELAI_MS = 5_000L;

    private long idPassage;

    /// Une fenêtre à soi : la redimensionner fige son dimensionnement, et celle du harnais est partagée
    /// par les classes du même fork (#4134).
    private Stage fenetre;

    @Start
    void start(Stage stage) throws IOException {
        fenetre = new Stage();
        fenetre.initOwner(stage);
        BancDeRecette.surLeChrome()
                .taille(LARGEUR_DES_BANCS, 900)
                .executeur(BancDeRecette.Executeur.SYNCHRONE)
                .semer(this::semerUnPassage)
                .ouvrir(injecteur -> injecteur
                        .getInstance(OuvrirPassage.class)
                        .ouvrir(idPassage, new ContexteSite("640380", "A1", "Étang de la Tuilière")))
                .montrer(fenetre);
    }

    @AfterEach
    void fermerLaFenetre(FxRobot robot) {
        robot.interact(fenetre::close);
    }

    private void semerUnPassage(Injector injecteur) {
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        idPassage = JeuDeDonneesPassage.dans(source).semer().idPassage();
    }

    @Test
    @DisplayName("#4834 : aux trois largeurs, chaque carte d'action dessine son titre et son sous-titre en entier")
    void chaque_carte_dessine_ses_deux_libelles_en_entier(FxRobot robot) {
        Scene scene = fenetre.getScene();
        for (double largeur : LARGEURS) {
            robot.interact(() -> fenetre.setWidth(largeur));
            Node cartes = Attente.surLeFil(
                    () -> {
                        scene.getRoot().applyCss();
                        scene.getRoot().layout();
                        return scene.getRoot().lookup(".cartes-actions");
                    },
                    "remettre l'écran en page à " + largeur + " de large",
                    DELAI_MS);

            assertThat(scene.getWidth())
                    .as("la scène n'a pas atteint %s : le test serait muet sur cette largeur", largeur)
                    .isEqualTo(largeur, within(1.0));
            assertThat(cartesVisibles(cartes))
                    .as("à %s de large, l'écran montre ses six cartes : à cinq elles tiennent sans céder", largeur)
                    .containsExactlyElementsOf(CARTES);

            List<Pastille> libelles = PastillesEntieres.lireLesLibelles(cartes);
            assertThat(libelles)
                    .as("à %s de large, un titre et un sous-titre par carte", largeur)
                    .hasSize(2 * CARTES.size());
            assertThat(libelles)
                    .as(
                            "à %s de large, une carte d'action coupe un libellé : la hauteur de"
                                    + " « .carte-action » dans passage.css ne loge plus deux libellés sur deux"
                                    + " lignes",
                            largeur)
                    .allMatch(Pastille::entiere);
            assertThatCode(() -> robot.interact(() -> LisibiliteCapture.refuserToutTexteIllisible(scene)))
                    .as("à %s de large, le garde des captures refuse l'écran du passage", largeur)
                    .doesNotThrowAnyException();
        }
    }

    private static List<String> cartesVisibles(Node cartes) {
        return Attente.surLeFil(
                () -> cartes.lookupAll(".carte-action").stream()
                        .filter(Node::isVisible)
                        .filter(carte -> carte.getParent().isVisible())
                        .map(Node::getId)
                        .toList(),
                "compter les cartes d'action visibles",
                DELAI_MS);
    }
}
