package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Les deux **suppressions** de la fiche site, cliquées pour de vrai (#1405) : supprimer le site (et
/// tous ses points), supprimer un point d'écoute.
///
/// Ni l'une ni l'autre n'était couverte, et pour la seconde c'était structurel : les cartes de points
/// fabriquaient leur **propre** confirmateur, que rien n'exposait. Personne ne pouvait le remplacer,
/// donc le clic finissait invariablement sur un vrai dialogue qui **fige** TestFX headless. L'écran
/// détient maintenant **une seule** paire de porteurs (confirmateur + notificateur), partagée avec ses
/// cartes : les deux gestes deviennent jouables.
///
/// Test d'intégration sur le **vrai** injecteur et une **vraie** base : ce qui est vérifié après le
/// clic, ce n'est pas qu'un mock a été appelé, c'est que la ligne a **disparu de la base** - ou qu'elle
/// y est **toujours** quand l'utilisateur a dit non.
@ExtendWith(ApplicationExtension.class)
class SiteDetailSuppressionsViewTest {

    private static final String ID_USER = "u-1";
    private static final String CARRE = "640380";
    private static final String CODE_POINT = "A1";

    /// L injecteur applicatif du test : sert a jouer, depuis le test, ce que fait la synchronisation.
    private Injector injecteur;

    /// Ce que le confirmateur a **demandé**.
    private final List<String> confirmations = new ArrayList<>();

    /// Ce que le notificateur a **dit**, au lieu de l'afficher (niveau compris).
    private final List<String> annonces = new ArrayList<>();

    /// Ce que le double de confirmation répondra : chaque test le pose avant de cliquer.
    private boolean confirme = true;

    private SourceDeDonnees source;
    private Site site;
    private Long idPoint;
    private SiteDetailController controleur;

    /// La vue de la fiche, gardée en champ : un écran QUITTÉ sort du graphe de scène, et
    /// `robot.lookup` ne le voit plus. Les requêtes partent donc de la vue, pas de la fenêtre.
    private Parent vue;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-suppressions-site");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = RacineInjecteur.creer();
        Injector injector = injecteur;
        source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        // Un site, un point, aucun passage : les deux suppressions sont ouvertes.
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre(CARRE)
                .nomSite("Étang")
                .point(CODE_POINT)
                .position(43.5, 5.4)
                .semerSiteEtPoint();
        site = jeu.leSite();
        idPoint = jeu.idPoint();

        // Le chrome, pour que le retour à l'accueil après suppression du site soit une vraie navigation.
        FXMLLoader chrome = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        chrome.setControllerFactory(DiagnosticGuice.pour(injector));
        FenetreAjustable.poser(stage, chrome.load(), 1100, 760);

        // On rejoue NavigationSites.ouvrirDetail(site) à la main : c'est le seul moyen de garder la main
        // sur le controller, donc de remplacer ses deux dialogues avant le premier clic.
        FXMLLoader loader = new FXMLLoader(SiteDetailController.class.getResource("SiteDetail.fxml"));
        loader.setControllerFactory(DiagnosticGuice.pour(injector));
        vue = loader.load();
        controleur = loader.getController();
        controleur.confirmateur().definir(message -> {
            confirmations.add(message);
            return confirme;
        });
        controleur
                .notificateur()
                .definir((niveau, entete, message) -> annonces.add(niveau + " | " + entete + " | " + message));
        controleur.afficher(site);
        injector.getInstance(Navigateur.class).empiler(vue, "site-detail", "Carré " + CARRE, controleur);
        FenetreAjustable.afficher(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    /// Rattache un passage au point : le site n'est alors plus supprimable (le service le refuse), et le
    /// lien « Supprimer » de la carte se ferme (#789). L'écran est rouvert pour refléter la nuit.
    private void rattacherUnPassage(FxRobot robot) {
        new EnregistreurDao(source).insert(new Enregistreur("1925492", "V1.01", null));
        JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .surLePoint(idPoint)
                .enregistreur("1925492")
                .nuit(2, 2026, "2026-06-22")
                .heures("20:25:00", "07:47:00")
                .statut(StatutWorkflow.TRANSFORME)
                .semerPassage();
        robot.interact(() -> controleur.afficher(site));
    }

    private List<Site> sitesEnBase() {
        return new SiteDao(source).findByUtilisateur(ID_USER);
    }

    private List<PointDEcoute> pointsEnBase() {
        return new PointDao(source).findBySite(site.id());
    }

    @Test
    @DisplayName("#3593 : un point rapatrié par la synchro paraît SANS qu'on ait navigué")
    void un_point_rapatrie_parait_sans_navigation(FxRobot robot) {
        revelerPointsNonUtilises(robot);
        assertThat(cartesDePoint(robot)).hasSize(1);

        // Ce que fait la synchronisation : elle rapatrie un point sur ce carré, et annonce sa mutation.
        robot.interact(() ->
                injecteur.getInstance(ServiceSites.class).ajouterPointSynchronise(site.id(), "B2", 43.6, 5.5, null));

        assertThat(cartesDePoint(robot))
                .as("la fiche montre le point rapatrié, sans qu'on l'ait quittée ni rouverte")
                .hasSize(2);
    }

    @Test
    @DisplayName("#3593 : un écran quitté ne recharge plus, l'abonnement est rendu")
    void un_ecran_quitte_ne_recharge_plus(FxRobot robot) {
        revelerPointsNonUtilises(robot);
        // Le départ RÉEL d'un écran : le Navigateur le retire de l'historique. C'est lui qui rend
        // l'abonnement (contrat SuitLaRevision), l'écran n'a plus rien à faire pour cela.
        robot.interact(() -> injecteur.getInstance(Navigateur.class).afficherAccueil());

        robot.interact(() ->
                injecteur.getInstance(ServiceSites.class).ajouterPointSynchronise(site.id(), "B2", 43.6, 5.5, null));

        // `RevisionDonnees` est un SINGLETON, `SiteDetailViewModel` ne l'est délibérément pas : sans ce
        // retrait, chaque réouverture laisserait une écoute accrochée à une vue morte.
        assertThat(cartesDePoint(robot)).hasSize(1);
    }

    /// Les cartes de point actuellement rendues, comptées par leur lien « Supprimer » : une par carte.
    private java.util.Set<Node> cartesDePoint(FxRobot robot) {
        return robot.from(vue)
                .lookup("#cartesPoints")
                .lookup((Node noeud) ->
                        noeud instanceof Hyperlink lien && lien.getText().contains("Supprimer"))
                .queryAll();
    }

    /// Le lien « Supprimer » **de la carte du point** (à ne pas confondre avec le bouton « Supprimer »
    /// du site, qui porte le même libellé dans l'en-tête).
    private Hyperlink lienSupprimerPoint(FxRobot robot) {
        return robot.lookup("#cartesPoints")
                .lookup((Node noeud) ->
                        noeud instanceof Hyperlink lien && lien.getText().contains("Supprimer"))
                .queryAs(Hyperlink.class);
    }

    /// Révèle les points rapatriés **non utilisés** (#1738) : le point de test ne porte aucun passage, il
    /// est donc masqué par défaut. On clique « Afficher les points non utilisés » pour rendre sa carte (et
    /// son lien « Supprimer ») accessible - exactement le geste qu'un utilisateur ferait pour élaguer un
    /// point rapatrié inutile.
    private void revelerPointsNonUtilises(FxRobot robot) {
        robot.interact(() ->
                robot.lookup("#lienPointsNonUtilises").queryAs(Hyperlink.class).fire());
    }

    @Test
    @DisplayName("#1405 : « Supprimer » le site, confirmé : le site disparaît de la base")
    void suppression_du_site_confirmee(FxRobot robot) {
        robot.interact(() -> robot.lookup("#boutonSupprimer").queryButton().fire());

        assertThat(confirmations)
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .as("la confirmation dit que la suppression emporte aussi les points d'écoute")
                        .contains("ses points d'écoute"));
        assertThat(sitesEnBase())
                .as("le site a réellement été supprimé, pas seulement masqué")
                .isEmpty();
    }

    @Test
    @DisplayName("#1405 : « Supprimer » le site, refusé : le site est toujours là")
    void suppression_du_site_refusee(FxRobot robot) {
        confirme = false;

        robot.interact(() -> robot.lookup("#boutonSupprimer").queryButton().fire());

        assertThat(sitesEnBase()).hasSize(1);
        assertThat(annonces).as("un refus n'a pas à être commenté").isEmpty();
    }

    @Test
    @DisplayName("#789 : site dont un point porte un passage : « Supprimer » est fermé, et le clic ne fait rien")
    void suppression_du_site_fermee_quand_un_point_porte_un_passage(FxRobot robot) {
        rattacherUnPassage(robot);

        // Le refus n'est pas annoncé après coup : il est prévenu avant. Le bouton est fermé (#789), et
        // JavaFX n'émet aucune action sur un bouton désactivé - le clic est donc réellement sans effet.
        assertThat(robot.lookup("#boutonSupprimer").queryButton().isDisabled()).isTrue();

        robot.interact(() -> robot.lookup("#boutonSupprimer").queryButton().fire());

        assertThat(confirmations)
                .as("on ne demande pas de confirmer un geste fermé")
                .isEmpty();
        assertThat(annonces).isEmpty();
        assertThat(sitesEnBase()).hasSize(1);
    }

    @Test
    @DisplayName("#1405 : « Supprimer » un point d'écoute, confirmé : le point disparaît de la base")
    void suppression_du_point_confirmee(FxRobot robot) {
        robot.interact(() -> lienSupprimerPoint(robot).fire());

        assertThat(confirmations)
                .singleElement()
                .satisfies(message -> assertThat(message).contains(CODE_POINT));
        assertThat(pointsEnBase()).isEmpty();
    }

    @Test
    @DisplayName("#1405 : « Supprimer » un point d'écoute, refusé : le point est toujours là")
    void suppression_du_point_refusee(FxRobot robot) {
        confirme = false;

        robot.interact(() -> lienSupprimerPoint(robot).fire());

        assertThat(pointsEnBase()).hasSize(1);
        assertThat(annonces).isEmpty();
    }

    @Test
    @DisplayName("#1738 : un point rapatrié non utilisé est masqué, puis révélé par un lien qui l'annonce")
    void point_rapatrie_masque_puis_revele(FxRobot robot) {
        // Un point RAPATRIÉ (synchronisé) et jamais utilisé s'ajoute au site : contrairement au point A1
        // (ajouté à la main, toujours visible), il n'apparaît pas d'emblée.
        JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre(CARRE)
                .point("Z9")
                .pointRapatrie()
                .semerSiteEtPoint();
        robot.interact(() -> controleur.afficher(site));

        assertThat(codesPointsAffiches(robot))
                .as("A1 (manuel) reste visible, Z9 (rapatrié non utilisé) est masqué")
                .containsExactly(CODE_POINT);
        Hyperlink reveler = robot.lookup("#lienPointsNonUtilises").queryAs(Hyperlink.class);
        assertThat(reveler.getText())
                .as("le lien annonce combien de points rapatriés sont masqués")
                .contains("Afficher")
                .contains("1");

        revelerPointsNonUtilises(robot);

        assertThat(codesPointsAffiches(robot))
                .as("révélé, le point rapatrié Z9 apparaît (le manuel A1 reste)")
                .containsExactlyInAnyOrder(CODE_POINT, "Z9");
        assertThat(robot.lookup("#lienPointsNonUtilises")
                        .queryAs(Hyperlink.class)
                        .getText())
                .as("révélé, le lien propose désormais de replier")
                .contains("Masquer");
    }

    private List<String> codesPointsAffiches(FxRobot robot) {
        return robot.lookup("#cartesPoints").lookup(".carte-point-code").queryAllAs(Label.class).stream()
                .map(Label::getText)
                .toList();
    }

    @Test
    @DisplayName("#789 : un point qui porte des passages : le lien « Supprimer » est fermé avant le clic")
    void point_avec_passages_le_lien_est_ferme(FxRobot robot) {
        rattacherUnPassage(robot);

        // L'utilisateur n'a pas à découvrir le refus après coup : le geste est fermé, et l'enveloppe
        // porte le tooltip qui explique pourquoi (#789).
        assertThat(lienSupprimerPoint(robot).isDisabled()).isTrue();
        assertThat(pointsEnBase()).hasSize(1);
    }
}
