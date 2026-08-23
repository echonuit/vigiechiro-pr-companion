package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CadreVisible;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import java.io.IOException;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Ce que « Mes sites » montre quand il n'y a **rien** : l'état vide de `S1-12`.
///
/// ## Pourquoi une classe à part
///
/// [MesSitesViewTest] **seede deux sites** dans son `@Start`, ce qui est juste pour tout ce qu'il
/// éprouve - et rend l'état vide invisible chez lui. Le cas `S1-12` n'était donc couvert par rien,
/// non parce qu'on l'avait oublié, mais parce qu'aucun harnais existant ne pouvait le montrer.
///
/// ## Ce qui est vérifié, et pourquoi ce n'est pas cosmétique
///
/// Un écran vide sans rien d'autre laisse l'utilisateur devant une page blanche : il ne sait pas si
/// l'application a fini de charger, si elle a perdu ses données, ni ce qu'il doit faire. Les trois
/// pièces ci-dessous répondent chacune à une de ces questions - un repère visuel, une **porte de
/// sortie**, et l'explication de ce qui doit exister ailleurs avant d'exister ici.
@ExtendWith(ApplicationExtension.class)
class MesSitesEtatVideViewTest {

    private Injector injector;

    @Start
    void start(Stage stage) throws IOException {
        // ⚠️ AUCUN semis, et c'est tout le sujet : la base est migrée, donc saine, et vide.
        injector = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.SYNCHRONE)
                .ouvrir(inj -> inj.getInstance(NavigationSites.class).ouvrirAccueil())
                .montrer(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @CasDeRecette(value = "S1-12", portee = Portee.A_L_ECRAN)
    @DisplayName("Base vierge : l'état vide paraît, et la liste des cartes n'est pas là")
    void l_etat_vide_parait_sur_une_base_vierge(FxRobot robot) {
        assertThat(robot.lookup("#etatVide").queryAs(VBox.class).isVisible())
                .as("sans site, c'est l'état vide qui parle")
                .isTrue();
        assertThat(robot.lookup(".carte-site").queryAll())
                .as("et aucune carte ne subsiste")
                .isEmpty();

        // L'état vide EST ce que ce cas fait juger : il doit être à l'image, et tenu le temps d'être lu.
        VBox etatVide = robot.lookup("#etatVide").queryAs(VBox.class);
        CadreVisible.amener(etatVide, robot);
        assertThat(CadreVisible.contient(etatVide))
                .as("un état vide hors du cadre est un écran dont le clip ne montre rien")
                .isTrue();
        Respiration.surLeMomentCle(robot);
    }

    @Test
    @CasDeRecette(value = "S1-12", portee = Portee.A_L_ECRAN)
    @DisplayName("L'état vide porte sa porte de sortie : « + Ajouter mon premier site de suivi »")
    void l_etat_vide_porte_sa_porte_de_sortie(FxRobot robot) {
        // Le point qui compte pour l'utilisateur : un écran vide sans action est une impasse.
        Button porteDeSortie =
                robot.lookup("#etatVide").lookup(".bouton-primaire-grand").queryAs(Button.class);
        assertThat(porteDeSortie.getText()).isEqualTo("+ Ajouter mon premier site de suivi");

        // C'est le bouton qu'on doit VOIR : un écran vide dont la sortie est sous le pli reste une
        // impasse pour qui regarde le clip.
        CadreVisible.amener(porteDeSortie, robot);
        assertThat(CadreVisible.contient(porteDeSortie)).isTrue();
        Respiration.surLeMomentCle(robot);
    }

    @Test
    @CasDeRecette(value = "S1-12", portee = Portee.A_L_ECRAN)
    @DisplayName("L'état vide explique ce qui doit exister sur le portail AVANT d'exister ici")
    void l_etat_vide_explique_le_prerequis(FxRobot robot) {
        // La hint-box, qui évite la méprise coûteuse : croire que déclarer un site ici le crée sur
        // Vigie-Chiro. Sa disparition ne casserait aucun autre test.
        assertThat(robot.lookup("#etatVide").lookup(".hint-box").tryQuery())
                .as("l'encart d'aide fait partie de l'état vide, il n'en est pas la décoration")
                .isPresent();
        Label aide = robot.lookup("#etatVide").lookup(".hint-txt").queryAs(Label.class);
        assertThat(aide.getText()).contains("vigiechiro.herokuapp.com");

        // L'encart d'aide se LIT : c'est une phrase, et une phrase demande qu'on s'arrête dessus.
        CadreVisible.amener(aide, robot);
        assertThat(CadreVisible.contient(aide)).isTrue();
        Respiration.leTempsDeLire(robot);
    }
}
