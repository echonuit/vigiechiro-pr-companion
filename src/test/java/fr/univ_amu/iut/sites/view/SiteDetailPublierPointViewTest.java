package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.InfobulleDeBlocage;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.connexion.viewmodel.RefletDuJeton;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointPublieDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX de l'action **« Publier sur Vigie-Chiro »** de la fiche site (#3458).
///
/// La fixture pose les quatre provenances qu'un carré mélange, parce que c'est leur cohabitation qui
/// décide de ce que chaque carte affiche :
///
/// | Point | Provenance | Ce que la carte doit montrer |
/// |---|---|---|
/// | `A1` | ajouté à la main, géolocalisé | l'action, **grisée** (aucun jeton dans ce workspace) |
/// | `C3` | ajouté à la main, **déjà publié** | l'**état**, pas l'action |
/// | `Z9` | **rapatrié** de la plateforme | **rien** : l'y renvoyer n'a pas de sens |
///
/// L'injecteur est celui de l'application complète : `PublicationPointModule` y est chargé, donc la
/// publication est bien *installée*. Ce qui manque est le **jeton**, et c'est le premier motif de gris -
/// le seul refus que Companion sache prévoir. Le 403 de la plateforme, lui, ne se devine pas : il se rend
/// compte (cf. `PublicationPoint`).
@ExtendWith(ApplicationExtension.class)
class SiteDetailPublierPointViewTest {

    private static final String ID_USER = "u-1";
    private static final String LIBELLE_ACTION = "Publier sur Vigie-Chiro";
    private static final String LIBELLE_ETAT = "Publié sur Vigie-Chiro";

    private Injector injecteur;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-publier-point");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = Guice.createInjector(RacineInjecteur.modules());
        Injector injector = injecteur;
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        // Trois points du MÊME carré, semés par la fixture : le cliquet des semis de topologie (#2865)
        // tient la dette du semis à la main à zéro, et un test neuf ne la rouvre pas.
        long idSite = semerPoint(source, "A1", 43.52, 5.46, false).idSite();
        long idPublie = semerPoint(source, "C3", 43.53, 5.47, false).idPoint();
        new PointPublieDao(source).marquer(idPublie);
        // Rapatrié : `synchronise` dit « venu DE » la plateforme, l'inverse de « poussé VERS ».
        semerPoint(source, "Z9", 43.54, 5.48, true);

        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(
                        LienVigieChiro.ENTITE_SITE, String.valueOf(idSite), "6a4961f587bc8dba39481180", false));
        Site site = new SiteDao(source).findById(idSite).orElseThrow();

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent racine = loader.load();
        stage.setScene(new Scene(racine, 1100, 760));
        injector.getInstance(NavigationSites.class).ouvrirDetail(site);
        stage.show();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    /// Un point du carré d'essai, semé par la fixture. Les appels partagent le carré : ils retombent tous
    /// sur le **même** site, sans doublonner.
    private static JeuDeDonneesPassage semerPoint(
            SourceDeDonnees source, String code, double latitude, double longitude, boolean rapatrie) {
        JeuDeDonneesPassage semis = JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre("640380")
                .nomSite("Étang")
                .point(code)
                .position(latitude, longitude);
        if (rapatrie) {
            semis.pointRapatrie();
        }
        return semis.semerSiteEtPoint();
    }

    @Test
    @DisplayName("#4205 : le jeton posé, l'action se rouvre sans qu'on ait quitté la fiche")
    void le_jeton_pose_l_action_se_rouvre(FxRobot robot) {
        assertThat(lienPublier(robot, "A1").isDisable())
                .as("écran de départ : pas de jeton, geste fermé")
                .isTrue();

        // Se connecter, comme la modale de connexion le fait : le jeton est écrit, et le changement
        // est publié.
        injecteur
                .getInstance(StockageConnexion.class)
                .enregistrer("jeton-de-test", new ProfilVigieChiro(ID_USER, "chiro", "observateur"));
        injecteur.getInstance(RefletDuJeton.class).relire();
        WaitForAsyncUtils.waitForFxEvents();

        // `setDisable(...)` est posé UNE FOIS, à la construction de la carte : c'est un instantané,
        // pas une liaison. La fiche ne se reconstruit que sur une mutation de la base, et se connecter
        // n'en est pas une - le geste restait donc fermé, à conseiller un geste qu'on venait de faire.
        // Le même angle mort que #4205 sur « Mes sites », dans un troisième écran.
        assertThat(lienPublier(robot, "A1").isDisable())
                .as("le jeton posé, le geste se rouvre : sinon le motif conseille ce qu'on vient de faire")
                .isFalse();
    }

    @Test
    @DisplayName("#3458 : sans jeton, l'action est offerte mais GRISÉE, et dit d'aller se connecter")
    void sans_jeton_l_action_est_grisee_et_dit_quoi_faire(FxRobot robot) {
        Hyperlink publier = lienPublier(robot, "A1");

        assertThat(publier.isDisable())
                .as("cliquer pour apprendre qu'on n'est pas connecté serait un refus découvert après coup")
                .isTrue();
        assertThat(InfobulleDeBlocage.texteDe(publier.getParent()))
                .as("le gris porte son motif, et le motif dit le geste (#789)")
                .contains("Connectez-vous");
    }

    @Test
    @DisplayName("#3458 : un point DÉJÀ publié affiche un état, pas une action à recliquer")
    void un_point_deja_publie_affiche_un_etat(FxRobot robot) {
        assertThat(libellesDe(carte(robot, "C3")))
                .as("le geste n'a plus lieu d'être : le proposer encore ferait cliquer pour rien")
                .contains(LIBELLE_ETAT)
                .doesNotContain(LIBELLE_ACTION);
    }

    @Test
    @DisplayName("#3458 : un point RAPATRIÉ n'offre pas de publication : il vient de là")
    void un_point_rapatrie_n_offre_pas_de_publication(FxRobot robot) {
        // Rapatrié et sans passage, il est masqué par défaut (#1738). On le RÉVÈLE : sans cela,
        // « la carte ne porte pas l'action » serait vrai parce que la carte n'existe pas, ce qui ne
        // prouverait rien du tout.
        robot.clickOn("#lienPointsNonUtilises");

        assertThat(libellesDe(carte(robot, "Z9")))
                .as("`synchronise` et « publié » sont deux drapeaux opposés, pas deux noms du même")
                .doesNotContain(LIBELLE_ACTION, LIBELLE_ETAT);
    }

    /// Le lien « Publier » de la carte de ce code. Échoue si la carte ne le porte pas : une recherche qui
    /// ne trouve rien ne doit pas passer pour un vert.
    private Hyperlink lienPublier(FxRobot robot, String code) {
        return carte(robot, code).lookupAll(".hyperlink").stream()
                .map(Hyperlink.class::cast)
                .filter(lien -> LIBELLE_ACTION.equals(lien.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune action « " + LIBELLE_ACTION + " » sur la carte " + code));
    }

    /// La carte du point portant ce code, cherchée sur son **libellé** et non sur son rang : un test qui
    /// prend « la première carte » verdit encore quand l'ordre change.
    private Node carte(FxRobot robot, String code) {
        return robot.lookup(".carte-point").queryAll().stream()
                .map(Node.class::cast)
                .filter(carte -> libellesDe(carte).contains(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune carte de point « " + code + " » à l'écran"));
    }

    /// Les libellés portés par une carte : ce que l'utilisateur y lit réellement.
    private static Set<String> libellesDe(Node carte) {
        Set<String> textes = new LinkedHashSet<>();
        carte.lookupAll(".label").forEach(noeud -> textes.add(((Label) noeud).getText()));
        carte.lookupAll(".hyperlink").forEach(noeud -> textes.add(((Hyperlink) noeud).getText()));
        return textes;
    }
}
