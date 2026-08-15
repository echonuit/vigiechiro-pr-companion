package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.recette.CasDeRecette;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-mes-sites-vide");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector =
                Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ExecuteurTache.class)
                                .to(ExecuteurTacheSynchrone.class)
                                .in(Singleton.class);
                    }
                }));
        new MigrationSchema(injector.getInstance(SourceDeDonnees.class)).migrer();
        // ⚠️ AUCUN seed, et c'est tout le sujet : la base est migrée, donc saine, et vide.
        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1100, 720));
        injector.getInstance(NavigationSites.class).ouvrirAccueil();
        stage.show();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @CasDeRecette("S1-12")
    @DisplayName("Base vierge : l'état vide paraît, et la liste des cartes n'est pas là")
    void l_etat_vide_parait_sur_une_base_vierge(FxRobot robot) {
        assertThat(robot.lookup("#etatVide").queryAs(VBox.class).isVisible())
                .as("sans site, c'est l'état vide qui parle")
                .isTrue();
        assertThat(robot.lookup(".carte-site").queryAll())
                .as("et aucune carte ne subsiste")
                .isEmpty();
    }

    @Test
    @CasDeRecette("S1-12")
    @DisplayName("L'état vide porte sa porte de sortie : « + Ajouter mon premier site de suivi »")
    void l_etat_vide_porte_sa_porte_de_sortie(FxRobot robot) {
        // Le point qui compte pour l'utilisateur : un écran vide sans action est une impasse.
        assertThat(robot.lookup("#etatVide").lookup(".bouton-primaire-grand").queryAllAs(Button.class))
                .extracting(Button::getText)
                .containsExactly("+ Ajouter mon premier site de suivi");
    }

    @Test
    @CasDeRecette("S1-12")
    @DisplayName("L'état vide explique ce qui doit exister sur le portail AVANT d'exister ici")
    void l_etat_vide_explique_le_prerequis(FxRobot robot) {
        // La hint-box, qui évite la méprise coûteuse : croire que déclarer un site ici le crée sur
        // Vigie-Chiro. Sa disparition ne casserait aucun autre test.
        assertThat(robot.lookup("#etatVide").lookup(".hint-box").tryQuery())
                .as("l'encart d'aide fait partie de l'état vide, il n'en est pas la décoration")
                .isPresent();
        assertThat(robot.lookup("#etatVide")
                        .lookup(".hint-txt")
                        .queryAs(Label.class)
                        .getText())
                .contains("vigiechiro.herokuapp.com");
    }
}
