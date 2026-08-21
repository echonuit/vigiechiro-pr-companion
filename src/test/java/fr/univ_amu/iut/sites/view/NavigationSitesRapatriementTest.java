package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.sites.model.RapatriementCarre;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// La **couture** entre la fenêtre de déclaration et la fiche du carré, quand un carré vient d'être
/// récupéré (#3806), et le **véhicule** par lequel la fiche en rend compte (#4091).
///
/// ## Ce que ce test existe pour empêcher
///
/// [ModaleSiteVerifierCarreViewTest] s'arrête une étape plus tôt : il vérifie que l'appelant **reçoit**
/// le carré rapatrié. Ce que l'appelant en fait - ouvrir la fiche, et y porter le compte rendu -
/// n'était exercé nulle part. Mesuré à la clôture des suites de #3458 : le compte rendu retiré, **286
/// tests restaient verts**. On pouvait donc supprimer la seule phrase qui explique à l'utilisateur d'où
/// sortent les quarante et un points apparus sur sa fiche.
///
/// L'aperçu ne rattrapait pas ce trou non plus : `apercu-sites-carre-recupere.png` rend bien le
/// composant de production, mais il construit son `Alert` lui-même, et resterait donc identique.
///
/// ## Ce qu'il garde, et ce qu'il ne garde pas
///
/// Le compte rendu s'affichait dans une **fenêtre séparée**, alors que l'[ADR 0023] réserve le modal à
/// l'irréversible. Récupérer un carré ne détruit rien : le compte rendu va au bandeau de l'écran.
///
/// Le test l'exige **positivement** : le bandeau est là, et il porte le message. C'est ce qui le fait
/// rougir des deux côtés du défaut - bandeau absent, et bandeau vide.
///
/// Il ne vérifie **pas** qu'aucune fenêtre ne s'ouvre, et cette limite est délibérée. Le seul port par
/// lequel cette classe pouvait ouvrir une fenêtre était son [fr.univ_amu.iut.commun.view.Notificateur]
/// injecté, retiré avec le défaut : un double posé là ne serait plus atteint par rien, et l'assertion
/// « il n'a pas été appelé » serait vraie sans rien vérifier. Une assertion qu'aucun changement ne peut
/// faire rougir est pire qu'absente, puisqu'elle se lit comme une garde. Ce qui garde vraiment le
/// remède est l'assertion du bandeau : y revenir par une fenêtre laisserait le bandeau vide, et le
/// test rougirait là.
///
/// ## Pourquoi la composition complète
///
/// Les deux brins de la couture vivent dans [NavigationSites] : empiler la fiche, puis rendre compte. Le
/// premier a besoin du vrai chrome (le fil d'Ariane est peuplé par le [Navigateur] du socle), et le
/// second du vrai câblage d'injection. Un montage partiel prouverait le test, pas le produit.
@ExtendWith(ApplicationExtension.class)
class NavigationSitesRapatriementTest {

    private static final String ID_USER = "u-test";
    private static final String CARRE = "640380";
    private static final int POINTS_POSES = 41;

    private Injector injector;
    private Site carre;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-nav-rapatriement");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector =
                Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        // Exécuteur synchrone (#1212) : même raison que les autres tests de vue de la feature.
                        bind(ExecuteurTache.class)
                                .to(ExecuteurTacheSynchrone.class)
                                .in(Singleton.class);
                    }
                }));
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        carre = injector.getInstance(ServiceSites.class)
                .creerSite(CARRE, "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        stage.setScene(new Scene(loader.load(), 1100, 720));
        injector.getInstance(NavigationSites.class).ouvrirAccueil();
        stage.show();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @CasDeRecette("S1-34")
    @DisplayName("#4091 : récupérer un carré ouvre SA fiche, et y rend compte au bandeau")
    void le_rapatriement_ouvre_la_fiche_et_rend_compte(FxRobot robot) {
        RapatriementCarre.Resultat.Rapatrie rapatrie = new RapatriementCarre.Resultat.Rapatrie(carre, POINTS_POSES);

        robot.interact(() -> injector.getInstance(NavigationSites.class).ouvrirDetailRapatrie(rapatrie));

        // Premier brin : la fiche du carré est à l'écran, et le fil dit où l'on vient d'arriver.
        assertThat(robot.lookup("#boutonImporterNuit").tryQuery())
                .as("la fiche du carré récupéré s'est ouverte")
                .isPresent();
        assertThat(segmentsDuFil(robot))
                .as("le fil d'Ariane porte l'étape empilée")
                .endsWith("Carré " + CARRE);

        // Second brin : sans ce compte rendu, quarante et un points paraissent sans explication.
        HBox bandeau = robot.lookup("#bandeauRetour").queryAs(HBox.class);
        assertThat(bandeau.isVisible())
                .as("la fiche montre son bandeau de retour")
                .isTrue();
        assertThat(robot.lookup("#lblRetour").queryAs(Label.class).getText())
                .as("le compte rendu nomme le carré et compte ses points")
                .contains(CARRE)
                .contains(String.valueOf(POINTS_POSES));
        assertThat(bandeau.getStyleClass())
                .as("un carré récupéré est une bonne nouvelle, pas un avertissement")
                .contains("retour-succes");
    }

    /// Les libellés du fil, dans l'ordre, sur le modèle de `MainViewTest#fil_ariane_reflete_le_parcours`.
    private static List<String> segmentsDuFil(FxRobot robot) {
        HBox fil = robot.lookup("#filAriane").queryAs(HBox.class);
        return fil.getChildren().stream()
                .filter(noeud -> noeud.getStyleClass().contains("fil-ariane-segment")
                        || noeud.getStyleClass().contains("fil-ariane-courant"))
                .map(noeud -> ((Labeled) noeud).getText())
                .toList();
    }
}
