package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.recette.CadreVisible;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.FenetreDuBanc;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Les quatre zones de la **fiche d'un site**, jouées sur le vrai écran : `S1-18` à `S1-21` (#4132).
///
/// ## Ce que ces cas n'avaient pas
///
/// Ils étaient portés par `SiteDetailViewModelTest`, qui n'ouvre aucune fenêtre. Leurs lecteurs sur la
/// page de recette montraient donc un rectangle noir, et rien ne disait ce qu'un utilisateur voit ni
/// comment il y arrive. Les assertions du ViewModel restent où elles sont - elles gardent le calcul -
/// mais le **cas de recette** appartient au scénario qui le montre (EPIC #4133).
///
/// ## Le chemin fait partie du cas
///
/// Chaque test part de « Mes sites » et **clique la carte du carré**. C'est ce qu'un observateur fait,
/// et c'est la moitié qu'on saute quand on appelle la navigation directement : le clip s'ouvrirait sur
/// une fiche sans que rien n'explique d'où elle vient - reproche fait à `S1-26` puis à `S4-33`.
///
/// ## Trois carrés, et chacun sert
///
/// | carré | ce qu'il porte | ce qu'il montre |
/// |---|---|---|
/// | `640380` | des points, des passages, aucun lien plateforme | `S1-18`, `S1-19`, `S1-20`, `S1-21` |
/// | `752204` | un lien plateforme, aucun passage | `S1-35` |
/// | `013570` | rien | que « Mes sites » est une **liste** |
///
/// `S1-19` et `S1-35` font juger les **deux mêmes commandes** dans leurs deux états : empêchées sur un
/// carré non relié qui porte des passages, offertes sur un carré rattaché qui n'en porte pas. Les deux
/// clips se regardent ensemble, et c'est leur écart qui dit la règle - un seul des deux ne montrerait
/// qu'un écran, pas une garde.
///
/// ## ⚠️ Ce que `lookup` ne dit pas, et où en est ce contrôle-ci
///
/// `lookup` trouve un noeud **quelle que soit sa position** : un cas peut passer toutes ses assertions
/// et publier un clip qui ne montre pas son objet - vécu deux fois sur `S4-33` (#4126, #4128). Chaque
/// cas amène donc sa zone dans le cadre et vérifie qu'elle y est ([CadreVisible]).
///
/// Il faut dire dans quel état est ce contrôle **ici** : mesuré, à 1180 x 900 et avec cette fixture, la
/// fiche entière tient dans la zone d'affichage - contenu 805 px pour un cadre de 805. Le défilement ne
/// sert donc à rien aujourd'hui, et retirer les appels à `amener` ne fait rougir aucun des quatre cas.
/// Ce n'est pas une raison de les retirer : c'est ce qui garde vraie la phrase « tout est à l'image » le
/// jour où la fiche gagne une section, où la fixture gagne des passages, ou où l'écran rétrécit.
@ExtendWith(ApplicationExtension.class)
class ScenarioFicheSiteTest {

    private static final String ID_USER = "u-fiche";
    private static final String ENREGISTREUR = "1925492";

    /// Le carré que les quatre cas ouvrent : il porte des points, des passages, et aucun lien plateforme.
    private static final String CARRE = "640380";

    private static final String TITRE_CARRE = "Carré " + CARRE;

    /// Le carré **rattaché** : il porte un lien vers la plateforme, et aucun passage.
    private static final String CARRE_RATTACHE = "752204";

    private static final String TITRE_RATTACHE = "Carré " + CARRE_RATTACHE;

    /// L'identifiant que la plateforme donne au site, et par lequel « Ouvrir sur Vigie-Chiro » construit
    /// son adresse.
    private static final String OBJECTID = "5eb12120cbe7410011f0a97f";

    /// La date de la nuit la plus récente, **telle qu'elle est rendue** dans le tableau. C'est par elle
    /// que le double-clic vise sa ligne : viser « la première ligne » rendrait un clip juste sous une
    /// légende fausse le jour où le tri change.
    private static final String DATE_AFFICHEE = "22/06/2026";

    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-fiche-site");
        System.setProperty("vigiechiro.workspace", workspace.toString());

        injector = RacineInjecteur.creer();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Observateur"));
        new EnregistreurDao(source).insert(new Enregistreur(ENREGISTREUR, "V1.01", null));
        semerLesDeuxCarres(source);

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        FenetreDuBanc.poser(stage, loader.load(), 1180, 900);
        injector.getInstance(NavigationSites.class).ouvrirAccueil();
        FenetreDuBanc.afficher(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @CasDeRecette(value = "S1-18", portee = Portee.A_L_ECRAN)
    @DisplayName(
            "S1-18 · le bandeau de la fiche dit carré, département, protocole, création, dernière nuit et passages")
    void le_bandeau_dit_l_identite_du_carre(FxRobot robot) throws TimeoutException {
        ouvrirLaFiche(robot, TITRE_CARRE);

        Label numero = etiquette(robot, "#valNumeroCarre");
        CadreVisible.amener(numero, robot);
        Respiration.leTempsDeLire(robot);

        assertThat(numero.getText()).isEqualTo(CARRE);
        assertThat(etiquette(robot, "#valDepartement").getText())
                .as("le département se dérive du numéro de carré, il n'est pas saisi")
                .isEqualTo("64");
        assertThat(etiquette(robot, "#valProtocole").getText()).isEqualTo("PointFixeStandard");
        assertThat(etiquette(robot, "#valDateCreation").getText()).isNotBlank();
        assertThat(etiquette(robot, "#valDerniereNuit").getText()).isNotBlank();
        assertThat(etiquette(robot, "#valPassages").getText()).isNotBlank();

        for (String cellule : new String[] {
            "#valNumeroCarre",
            "#valDepartement",
            "#valProtocole",
            "#valDateCreation",
            "#valDerniereNuit",
            "#valPassages"
        }) {
            assertThat(CadreVisible.contient(etiquette(robot, cellule)))
                    .as("%s est hors du cadre : le clip annoncerait un bandeau qu'on ne voit pas", cellule)
                    .isTrue();
        }
    }

    @Test
    @CasDeRecette(value = "S1-19", portee = Portee.A_L_ECRAN)
    @DisplayName(
            "S1-19 · les gardes des boutons : non relié grise « Ouvrir sur Vigie-Chiro », un passage grise « Supprimer »")
    void les_boutons_disent_ce_qui_les_empeche(FxRobot robot) throws TimeoutException {
        ouvrirLaFiche(robot, TITRE_CARRE);

        Button portail = bouton(robot, "#boutonOuvrirPortail");
        Button supprimer = bouton(robot, "#boutonSupprimer");
        CadreVisible.amener(supprimer, robot);
        Respiration.leTempsDeLire(robot);

        assertThat(portail.isDisabled())
                .as("aucun lien plateforme sur ce carré : le bouton ne peut mener nulle part")
                .isTrue();
        assertThat(supprimer.isDisabled())
                .as("des passages sont rattachés : supprimer le site les emporterait")
                .isTrue();
        assertThat(CadreVisible.contient(portail) && CadreVisible.contient(supprimer))
                .as("les deux boutons grisés sont ce que ce cas fait juger : ils doivent être à l'image")
                .isTrue();
    }

    @Test
    @CasDeRecette(value = "S1-20", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-20 · les cartes de points portent le badge GPS et la distance au plus proche")
    void les_cartes_de_points_portent_gps_et_distance(FxRobot robot) throws TimeoutException {
        ouvrirLaFiche(robot, TITRE_CARRE);

        Node premiereCarte =
                robot.lookup(".carte-point").queryAll().stream().findFirst().orElseThrow();
        CadreVisible.amener(premiereCarte, robot);
        Respiration.leTempsDeLire(robot);

        assertThat(robot.lookup(".carte-point").queryAll())
                .as("trois points sont posés : deux géolocalisés et un sans GPS")
                .hasSize(3);
        // ⚠️ On compte les CARTES qui portent le repère, pas les noeuds qui portent la classe. Le badge
        // la pose sur l'hyperlien ET sur son icône : compter les noeuds rendait deux par point, et
        // l'assertion aurait dit « quatre points géolocalisés » sur un carré qui n'en a que deux.
        assertThat(cartesPortant(robot, ".gps-ok"))
                .as("les deux points géolocalisés portent le repère qui le dit")
                .isEqualTo(2);
        assertThat(cartesPortant(robot, ".gps-manquant"))
                .as("le point sans GPS porte l'invite à le poser, et c'est ce qui se juge à l'oeil")
                .isEqualTo(1);
        assertThat(robot.lookup(".carte-point-alerte").queryAll())
                .as("A1 et B2 sont à une centaine de mètres, sous le seuil : l'alerte de proximité paraît")
                .isNotEmpty();
        assertThat(CadreVisible.contient(premiereCarte))
                .as("les cartes de points sont ce que ce cas fait juger")
                .isTrue();
    }

    @Test
    @CasDeRecette(value = "S1-21", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-21 · le tableau des passages porte ses sept colonnes, du plus récent au plus ancien")
    void le_tableau_des_passages_porte_ses_sept_colonnes(FxRobot robot) throws TimeoutException {
        ouvrirLaFiche(robot, TITRE_CARRE);

        TableView<?> table = robot.lookup("#tablePassages").queryAs(TableView.class);
        CadreVisible.amener(table, robot);
        Respiration.leTempsDeLire(robot);

        assertThat(table.getColumns())
                .as("sept colonnes, dont « Déposé le » que la session nomme explicitement")
                .hasSize(7);
        assertThat(table.getColumns().stream().map(colonne -> colonne.getText()).toList())
                .contains("Déposé le");
        assertThat(table.getItems())
                .as("deux passages sont semés : un tableau vide montrerait le repli, pas le cas")
                .hasSize(2);
        assertThat(CadreVisible.contient(table))
                .as("le tableau est ce que ce cas fait juger")
                .isTrue();

        // La session ne demande pas seulement un tableau : elle demande « double-clic vers le passage ».
        // Sans ce geste, le clip montrerait une table et s'arrêterait avant ce qu'elle sert à faire.
        Respiration.entreDeuxGestes(robot);
        robot.doubleClickOn(DATE_AFFICHEE);
        WaitForAsyncUtils.waitFor(
                10, TimeUnit.SECONDS, () -> robot.lookup("#stepper").tryQuery().isPresent());
        Respiration.surLeMomentCle(robot);

        assertThat(robot.lookup("#stepper").tryQuery())
                .as("le double-clic ouvre l'écran du passage : c'est là que la ligne mène")
                .isPresent();
    }

    @Test
    @CasDeRecette(value = "S1-35", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-35 · le carré rattaché porte son badge, et « Ouvrir sur Vigie-Chiro » n'est plus grisé")
    void le_carre_rattache_porte_son_badge(FxRobot robot) throws TimeoutException {
        ouvrirLaFiche(robot, TITRE_RATTACHE);

        Button portail = bouton(robot, "#boutonOuvrirPortail");
        Node cellule = robot.lookup("#celluleStatutPlateforme").query();
        CadreVisible.amener(cellule, robot);
        Respiration.leTempsDeLire(robot);

        assertThat(libellesDe(cellule))
                .as("le rattachement se lit sur la fiche, sans avoir à ouvrir quoi que ce soit")
                .contains("Enregistré sur Vigie-Chiro");
        assertThat(portail.isDisabled())
                .as("le carré est connu de la plateforme : le bouton mène quelque part")
                .isFalse();
        assertThat(CadreVisible.contient(cellule) && CadreVisible.contient(portail))
                .as("le badge et le bouton offert sont ce que ce cas fait juger")
                .isTrue();
    }

    /// Ce qu'une zone du bandeau **écrit**, libellé de cellule compris.
    private static List<String> libellesDe(Node zone) {
        return zone.lookupAll("*").stream()
                .filter(Labeled.class::isInstance)
                .map(noeud -> ((Labeled) noeud).getText())
                .filter(texte -> texte != null && !texte.isBlank())
                .toList();
    }

    /// Le geste que fait un observateur : depuis « Mes sites », cliquer la carte du carré.
    ///
    /// ⚠️ La carte se cherche **par son titre**, et son absence est une erreur qui la nomme. Chercher
    /// « la première carte » rendrait un clip juste sous une légende fausse le jour où la fixture en
    /// pose une seconde - c'est le défaut qu'`ApercuFx.exigerParLibelle` a corrigé côté aperçus.
    private void ouvrirLaFiche(FxRobot robot, String titre) throws TimeoutException {
        Respiration.avantLeGeste(robot);
        HBox carte = robot.lookup(".carte-site").queryAllAs(HBox.class).stream()
                .filter(candidate -> porteLeTitre(candidate, titre))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune carte de site intitulée « " + titre + " »"));
        robot.clickOn(carte);
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> robot.lookup("#valNumeroCarre").tryQuery().isPresent());
        Respiration.apresLeGeste(robot);
    }

    /// Le nombre de **cartes de points** qui portent `marque`, et non le nombre de noeuds qui la portent.
    private static long cartesPortant(FxRobot robot, String marque) {
        return robot.lookup(".carte-point").queryAll().stream()
                .filter(carte -> !carte.lookupAll(marque).isEmpty())
                .count();
    }

    private static boolean porteLeTitre(HBox carte, String titre) {
        return carte.lookupAll(".carte-titre").stream()
                .anyMatch(noeud -> noeud instanceof Label label && titre.equals(label.getText()));
    }

    private static Label etiquette(FxRobot robot, String selecteur) {
        return robot.lookup(selecteur).queryAs(Label.class);
    }

    private static Button bouton(FxRobot robot, String selecteur) {
        return robot.lookup(selecteur).queryAs(Button.class);
    }

    /// Le carré jugé, et son voisin de comparaison.
    ///
    /// ⚠️ A1 et B2 sont séparés d'environ cent mètres, ce qui est **sous le seuil de proximité** : c'est
    /// ce qui fait paraître l'alerte que `S1-20` fait juger. Un écart choisi au hasard rendrait un clip
    /// vert et muet sur la moitié du cas.
    private void semerLesDeuxCarres(SourceDeDonnees source) {
        ServiceSites service = injector.getInstance(ServiceSites.class);

        Site carre = service.creerSite(CARRE, "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        PointDEcoute a1 = service.ajouterPoint(carre.id(), "A1", 43.5000, 5.4000, "Près du grand chêne");
        service.ajouterPoint(carre.id(), "B2", 43.5009, 5.4000, "Lisière nord");
        service.ajouterPoint(carre.id(), "C3", null, null, "Coordonnées à relever sur place");
        semerPassage(source, a1, 1, "2026-04-22", Verdict.OK);
        semerPassage(source, a1, 2, "2026-06-22", null);

        Site rattache = service.creerSite(CARRE_RATTACHE, "Ruisseau des Aiguiers", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(rattache.id(), "A1", 43.62, 5.28, "Sous le pont");
        // Enregistré, non verrouillé : c'est l'état qu'un rapatriement laisse (#3806), et celui dont
        // `S1-35` parle. Verrouillé serait un autre badge, et un autre cas.
        injector.getInstance(LienVigieChiroDao.class)
                .upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(rattache.id()), OBJECTID, false));

        service.creerSite("013570", "Mare du Vallon", Protocole.STANDARD, null, ID_USER);
    }

    private void semerPassage(SourceDeDonnees source, PointDEcoute point, int numero, String date, Verdict verdict) {
        JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .surLePoint(point.id())
                .enregistreur(ENREGISTREUR)
                .nuit(numero, 2026, date)
                .heures("21:00:00", "05:00:00")
                .statut(StatutWorkflow.TRANSFORME)
                .verdict(verdict)
                .semerPassage();
    }
}
