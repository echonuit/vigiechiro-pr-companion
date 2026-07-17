package fr.univ_amu.iut.audit.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Écran **Audit de cohérence** : d'un constat au passage qu'il accuse (#1347).
///
/// L'audit nommait le coupable — « passage 3, préfixe non conforme » — et laissait l'utilisateur le
/// retrouver à la main, alors que partout ailleurs dans l'application une ligne de table s'ouvre au
/// double-clic. On vérifie ici que le **contrat socle** `OuvrirPassage` est bien appelé, **avec le contexte
/// du site** (carré, point) : sans lui, le fil d'Ariane de l'écran pivot serait vide.
///
/// La nuit de la fixture est **volontairement incohérente** (un fichier déclaré, aucun sur le disque) :
/// c'est ce qui produit un constat, et donc une ligne à double-cliquer.
@ExtendWith(ApplicationExtension.class)
class AuditNavigationViewTest {

    private static final String ID_USER = "u-audit";
    private static final String SERIE = "1925492";

    private final AtomicReference<Long> passageOuvert = new AtomicReference<>();
    private final AtomicReference<ContexteSite> contexteOuvert = new AtomicReference<>();

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-audit-nav");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Injector injector = Guice.createInjector(Modules.override(RacineInjecteur.modules())
                .with(liaison -> liaison.bind(OuvrirPassage.class).toInstance((idPassage, contexte) -> {
                    passageOuvert.set(idPassage);
                    contexteOuvert.set(contexte);
                })));
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "130711", "Étang", Protocole.STANDARD, null, "2026-01-01", ID_USER));
        Long idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "Z41", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, null, null));
        // Passage SANS session : l'audit émet un constat « aucune session : passage jamais importé ».
        new PassageDao(source)
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-07-03",
                        "22:00",
                        "06:00",
                        null,
                        StatutWorkflow.IMPORTE,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE));

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1100, 760));
        injector.getInstance(NavigationAudit.class).ouvrir();
        stage.show();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#1347 : double-clic sur un constat → le passage s'ouvre, avec le contexte de son site")
    void double_clic_ouvre_le_passage(FxRobot robot) {
        TableView<ConstatAudit> table = robot.lookup("#tableConstats").queryAs(TableView.class);
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(table.getItems())
                .as("la nuit sans session produit bien un constat : il y a une ligne à double-cliquer")
                .isNotEmpty();

        Node premiereLigne = robot.lookup(".table-row-cell").query();
        robot.doubleClickOn(premiereLigne);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(passageOuvert.get())
                .as("le constat accusait un passage : il s'ouvre, au lieu de laisser l'utilisateur le chercher")
                .isNotNull();
        assertThat(contexteOuvert.get())
                .as("avec le contexte de son site : sans lui, le fil d'Ariane de l'écran pivot serait vide")
                .isNotNull()
                .satisfies(contexte -> {
                    assertThat(contexte.numeroCarre()).isEqualTo("130711");
                    assertThat(contexte.codePoint()).isEqualTo("Z41");
                });
    }

    @Test
    @DisplayName("#1796 : « Ouvrir le passage » du menu de ligne ouvre le passage cité par le constat")
    void menu_de_ligne_ouvre_le_passage(FxRobot robot) {
        TableView<ConstatAudit> table = robot.lookup("#tableConstats").queryAs(TableView.class);
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> table.getSelectionModel().select(0));

        var items = table.getContextMenu().getItems();
        assertThat(items.get(0).getText()).isEqualTo("Ouvrir le passage");
        assertThat(items.get(1).getText()).isEqualTo("Auditer ce passage");

        robot.interact(() -> items.get(0).fire());
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(passageOuvert.get()).isNotNull();
    }

    @Test
    @DisplayName("#1347 : « Auditer ce passage » reste désactivé tant qu'aucun constat n'est sélectionné")
    void audit_cible_desactive_sans_selection(FxRobot robot) {
        Button bouton = robot.lookup("#boutonAuditerPassage").queryAs(Button.class);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(bouton.isDisabled())
                .as("un bouton qui ne ferait rien au clic doit le dire : il est désactivé, et son enveloppe"
                        + " porte l'explication (#789)")
                .isTrue();
    }
}
