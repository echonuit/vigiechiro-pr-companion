package fr.univ_amu.iut.lot.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.view.PanneauCompteRendu;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.CauseRefus;
import fr.univ_amu.iut.lot.model.EchecUnite;
import fr.univ_amu.iut.lot.viewmodel.CompteRenduChiffreDepot;
import fr.univ_amu.iut.lot.viewmodel.CompteRenduChiffreDepot.Plan;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.Jugement;
import fr.univ_amu.iut.recette.Seance;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.Labeled;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le scénario qui **joue** `S4-33`, pour qu'un humain le tranche en regardant (#4055).
///
/// ## Ce que le cas demande, et ce qu'il refuse
///
/// La session est explicite : « c'est la **lisibilité** de la phrase qu'on juge, pas sa présence : une
/// assertion la trancherait mal ». Le clip doit donc montrer la phrase **telle que l'écran la rend**,
/// dans la typographie du produit, à la largeur où elle se replie.
///
/// Les assertions ci-dessous ne tranchent que le nécessaire : que le compte y soit, et que le geste
/// conseillé y soit. Qu'elle se lise d'un trait, qu'elle ne noie pas le conseil dans le constat, c'est
/// le regard qui le dit.
///
/// ## Pourquoi le panneau seul, et non un dépôt joué de bout en bout
///
/// Atteindre cet état par le vrai chemin demanderait un stub qui refuse en 403, une connexion, un plan,
/// un téléversement : quatre minutes de film pour une phrase. Le panneau de compte rendu est le
/// composant que l'écran emploie, nourri du même `CompteRenduChiffreDepot` : ce qui paraît à l'image est
/// donc ce que l'utilisateur voit, sans le chemin qui y mène.
///
/// ⚠️ Ce raccourci a une limite, et il faut la connaître : il ne prouve pas que l'écran **atteint** cet
/// état. `S4-30` à `S4-32` s'en chargent, et ils demandent le stub.
@ExtendWith(ApplicationExtension.class)
class ScenarioPerceptifRefusDepotTest {

    /// Le temps d'arrêt avant l'affichage : l'écran vide sert de référence à qui compare.
    private static final long AVANT_MS = 700;

    /// Et après : de quoi lire la phrase entière, qui est ce qu'on vient juger.
    private static final long APRES_MS = 3_000;

    /// Trois archives refusées, toutes pour la même cause : les droits. C'est l'état de la fixture
    /// `VIGIECHIRO_STUB_REFUS=403` que la session décrit, et le seul où le conseil de reconnexion
    /// s'applique à toutes.
    private static final int REFUSEES = 3;

    private PanneauCompteRendu panneau;

    @Start
    void start(Stage stage) {
        panneau = new PanneauCompteRendu();
        VBox racine = new VBox(panneau);
        racine.setPadding(new Insets(24));
        // ⚠️ `Habillage.scene`, et non `new Scene` : c'est lui qui embarque la typographie du produit.
        // Juger la lisibilité d'une phrase dans une police que l'application n'a jamais n'aurait
        // aucun sens.
        stage.setScene(Habillage.scene(racine, 900, 500));
        stage.show();
    }

    @Test
    @CasDeRecette(value = "S4-33", jugement = Jugement.HUMAIN)
    @DisplayName("S4-33 · le compte rendu dit le nombre de refus et conseille la reconnexion : à lire")
    void le_compte_rendu_dit_les_refus_et_conseille_la_reconnexion(FxRobot robot) {
        respirer(robot, AVANT_MS);

        robot.interact(() -> panneau.afficher(CompteRenduChiffreDepot.de(bilanRefuse(), plan(), List.of())));
        WaitForAsyncUtils.waitForFxEvents();
        respirer(robot, APRES_MS);

        String affiche = texteAffiche(robot);
        assertThat(affiche)
                .as("le titre dit l'état, et « Nuit déposée » serait faux : trois archives manquent")
                .contains("Dépôt incomplet");
        assertThat(affiche)
                .as("le nombre d'archives refusées est dit, sans quoi il faut compter les lignes de la"
                        + " table pour savoir l'ampleur")
                .contains(REFUSEES + " archive(s) ont été refusées");
        assertThat(affiche)
                .as("et le geste qui répare est nommé : ce refus-là tient aux droits, donc une" + " reconnexion suffit")
                .contains("Reconnectez-vous");
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

    private static Plan plan() {
        return new Plan(14, 11, false);
    }

    /// Tout ce qui porte du texte dans la fenêtre, recollé. Le compte rendu répartit sa phrase entre
    /// plusieurs libellés ; chercher dans un seul supposerait une répartition qui peut changer.
    private static String texteAffiche(FxRobot robot) {
        return robot.lookup(node -> node instanceof Labeled).queryAll().stream()
                .map(node -> ((Labeled) node).getText())
                .filter(texte -> texte != null && !texte.isBlank())
                .reduce("", (tout, texte) -> tout + " " + texte);
    }

    /// Ne s'arrête que si l'on filme : hors séance filmée, ces respirations n'allongeraient le build
    /// que pour personne.
    private static void respirer(FxRobot robot, long millis) {
        if (Seance.filmee()) {
            robot.sleep(millis);
        }
    }
}
