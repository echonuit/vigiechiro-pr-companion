package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.InfobulleDeBlocage;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CadreVisible;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
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
/// Ces cas étaient portés par `SiteDetailViewModelTest`, qui n'ouvre aucune fenêtre : leurs lecteurs
/// montraient un rectangle noir. Les assertions du ViewModel gardent le calcul, mais le **cas de
/// recette** appartient au scénario qui le montre (EPIC #4133). Chaque test part donc de « Mes sites »
/// et **clique la carte du carré**, la moitié qu'on saute en appelant la navigation directement,
/// reproche fait à `S1-26` puis à `S4-33`.
///
/// | carré | ce qu'il porte | ce qu'il montre |
/// |---|---|---|
/// | `640380` | des points, des passages, aucun lien plateforme | `S1-18`, `S1-19`, `S1-20`, `S1-21` |
/// | `752204` | un lien plateforme, aucun passage | `S1-35` |
/// | `013570` | rien | que « Mes sites » est une **liste** |
///
/// `S1-19` et `S1-35` font juger les deux **mêmes** commandes dans leurs deux états : empêchées sur un
/// carré non relié qui porte des passages, offertes sur un carré rattaché qui n'en porte pas. C'est leur
/// écart qui dit la règle.
///
/// `lookup` trouve un nœud **quelle que soit sa position** : un cas peut passer toutes ses assertions et
/// publier un clip qui ne montre pas son objet, vécu deux fois sur `S4-33` (#4126, #4128). Chaque cas
/// amène donc sa zone dans le cadre et vérifie qu'elle y est ([CadreVisible]). Mesuré, à 1180 x 900 avec
/// cette fixture la fiche entière tient dans la zone : retirer les appels à `amener` ne fait rougir
/// aucun des quatre cas aujourd'hui, et c'est ce qui garde vraie la phrase le jour où la fiche gagne une
/// section.
@ExtendWith({ApplicationExtension.class, SansExceptionAvalee.class})
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
    void start(Stage stage) throws IOException {
        injector = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                // ASYNCHRONE, celui que cette classe avait déjà : `RacineInjecteur` lie l'exécuteur
                // de PRODUCTION, et « pas de surcharge » ne veut donc pas dire « synchrone ». Le premier
                // jet de la migration a posé SYNCHRONE par erreur, et trois cas ont rougi en « Not on FX
                // application thread » - le banc exige ce choix précisément parce qu'il ne se devine pas.
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .semer(this::semerLaFixture)
                .ouvrir(inj -> inj.getInstance(NavigationSites.class).ouvrirAccueil())
                .montrer(stage);
    }

    private void semerLaFixture(Injector inj) {
        SourceDeDonnees source = inj.getInstance(SourceDeDonnees.class);
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Observateur"));
        new EnregistreurDao(source).insert(new Enregistreur(ENREGISTREUR, "V1.01", null));
        semerLesDeuxCarres(inj, source);
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
        // D'ABORD le carré où les deux commandes sont OFFERTES. Un grisé ne se juge que contre son
        // contraire : le clip précédent montrait deux boutons ternes parmi quatre, et la revue l'a dit -
        // « ne montre pas ce qu'il doit » (#4173).
        ouvrirLaFiche(robot, TITRE_RATTACHE);
        Button portailOffert = bouton(robot, "#boutonOuvrirPortail");
        CadreVisible.amener(portailOffert, robot);
        assertThat(portailOffert.isDisabled())
                .as("point de comparaison : sur un carré relié, la commande est ouverte")
                .isFalse();
        assertThat(bouton(robot, "#boutonSupprimer").isDisabled())
                .as("et sans passage rattaché, la suppression aussi")
                .isFalse();
        Respiration.surLeMomentCle(robot);

        revenirAMesSites(robot);
        ouvrirLaFiche(robot, TITRE_CARRE);

        Button portail = bouton(robot, "#boutonOuvrirPortail");
        Button supprimer = bouton(robot, "#boutonSupprimer");
        CadreVisible.amener(supprimer, robot);
        Respiration.surLeMomentCle(robot);

        assertThat(portail.isDisabled())
                .as("aucun lien plateforme sur ce carré : le bouton ne peut mener nulle part")
                .isTrue();
        assertThat(supprimer.isDisabled())
                .as("des passages sont rattachés : supprimer le site les emporterait")
                .isTrue();
        assertThat(CadreVisible.contient(portail) && CadreVisible.contient(supprimer))
                .as("les deux boutons grisés sont ce que ce cas fait juger : ils doivent être à l'image")
                .isTrue();

        // Et les motifs PARAISSENT, un par un. Ce cas s'appelle « les boutons disent ce qui les
        // empêche » et ne montrait que le gris : les deux motifs étaient lus par programme, donc absents
        // de l'image. Un spectateur voyait deux boutons ternes et aucune explication - « ne montre pas
        // ce qu'il doit » (#4173). L'attente de l'infobulle est une assertion : si elle ne vient pas, le
        // test échoue au lieu de filmer un écran muet.

        // Et ce que ce cas fait juger, c'est ce qu'ils DISENT - donc les motifs PARAISSENT, un par
        // un. Le test lisait `isDisabled()` puis le texte PAR PROGRAMME : les deux motifs étaient donc
        // absents de l'image, et le clip montrait deux boutons ternes sans un mot d'explication. « Ne
        // montre pas ce qu'il doit » (#4173).
        //
        // L'attente de l'infobulle est une assertion : si elle ne vient pas, le test échoue au lieu de
        // filmer un écran muet. Un motif qu'on ne peut pas faire venir n'existe pas pour l'utilisateur.
        assertThat(InfobulleDeBlocage.montrerEtLire(
                        robot.lookup("#enveloppeOuvrirPortail").query(), robot))
                .as("le motif nomme ce qui manque, et le geste qui le répare")
                .contains("pas encore relié")
                .contains("synchronisez");
        Respiration.leTempsDeLire(robot);

        assertThat(InfobulleDeBlocage.montrerEtLire(
                        robot.lookup("#enveloppeSupprimer").query(), robot))
                .as("le motif nomme ce qui bloque, et ce qu'il faudrait retirer d'abord")
                .contains("porte des passages")
                .contains("Supprimez d'abord");
        Respiration.leTempsDeLire(robot);
    }

    /// Revient à « Mes sites » par le fil d'Ariane, comme le ferait un observateur.
    private void revenirAMesSites(FxRobot robot) throws TimeoutException {
        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, "#boutonRetour");
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> !robot.lookup(".carte-site").queryAll().isEmpty());
        Respiration.apresLeGeste(robot);
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
        // On compte les CARTES qui portent le repère, pas les noeuds qui portent la classe. Le badge
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

    @Test
    @CasDeRecette(
            value = {"S1-22", "S1-23"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S1-22, S1-23 · modifier depuis la fiche : la modale s'ouvre PRÉ-REMPLIE, et dit « Enregistrer »")
    void renommer_le_carre_met_a_jour_l_entete(FxRobot robot) throws TimeoutException {
        ouvrirLaFiche(robot, TITRE_CARRE);

        Label numero = etiquette(robot, "#valNumeroCarre");
        CadreVisible.amener(numero, robot);
        assertThat(numero.getText()).isEqualTo(CARRE);
        // L'en-tête AVANT : sans elle, on ne peut pas dire que le numéro a changé.
        Respiration.leTempsDeLire(robot);

        // La VRAIE modale d'édition, ouverte par le bouton. `SiteDetailRenommageViewTest` la
        // remplaçait par un double qui écrivait le nouveau numéro : le clip montrait un clic sur
        // « Modifier » puis un numéro qui change, sans qu'aucune modale ne paraisse (#4174).
        robot.clickOn("#boutonModifier");
        WaitForAsyncUtils.waitFor(
                10, TimeUnit.SECONDS, () -> robot.lookup("#champNom").tryQuery().isPresent());
        Respiration.leTempsDeLire(robot);

        // S1-23 · « création vs édition » : les champs portent déjà le site, et le bouton ne dit plus
        // « Créer ». C'est la moitié ÉDITION du cas ; la moitié création est dans `ScenarioModaleCarreTest`.
        TextField nom = robot.lookup("#champNom").queryAs(TextField.class);
        assertThat(robot.lookup("#champCarre").queryAs(TextField.class).getText())
                .as("la modale d'édition s'ouvre pré-remplie, elle ne redemande pas ce qu'on sait déjà")
                .isEqualTo(CARRE);
        assertThat(nom.getText()).isEqualTo("Étang de la Tuilière");
        assertThat(robot.lookup("#boutonValider").queryAs(Button.class).getText())
                .as("le bouton dit ce qu'il fait : on enregistre un site, on n'en crée pas un second")
                .isEqualTo("Enregistrer");

        robot.interact(nom::clear);
        robot.clickOn(nom).write("Étang de la Tuilière (rive nord)");
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.avantLeGeste(robot);

        robot.clickOn("#boutonValider");
        WaitForAsyncUtils.waitFor(
                10, TimeUnit.SECONDS, () -> robot.lookup("#champNom").tryQuery().isEmpty());
        Respiration.surLeMomentCle(robot);

        assertThat(robot.lookup("#barreStatut").queryAll())
                .as("la fiche est toujours là : le renommage ne fait pas quitter l'écran")
                .isNotEmpty();
        assertThat(etiquette(robot, "#valNumeroCarre").getText())
                .as("la fiche affiche encore le carré qu'elle vient d'enregistrer")
                .isEqualTo(CARRE);
    }

    @Test
    @CasDeRecette(value = "S1-24", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-24 · des coordonnées en degrés/minutes/secondes valent une position")
    void les_coordonnees_en_dms_valent_une_position(FxRobot robot) throws TimeoutException {
        ouvrirLaFiche(robot, TITRE_CARRE);
        int situesAvant = pointsSitues(robot);
        Respiration.avantLeGeste(robot);

        GesteVisible.cliquer(robot, "+ Ajouter un point");
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> robot.lookup("#champCode").tryQuery().isPresent());
        Respiration.leTempsDeLire(robot);

        robot.clickOn(robot.lookup("#champCode").queryAs(TextField.class)).write("E5");
        WaitForAsyncUtils.waitForFxEvents();

        // Le geste qu'un observateur de terrain fait vraiment : il relève sur son GPS de randonnée des
        // degrés/minutes/secondes, et les colle dans une application qui parle en décimal. Le script
        // promet que les deux formats se synchronisent ; c'est la moitié du cas que personne ne filmait,
        // couverte en unitaire seulement (#4232).
        robot.clickOn(robot.lookup("#champLatitude").queryAs(TextField.class)).write("43°31'47\"N");
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.entreDeuxGestes(robot);

        robot.clickOn(robot.lookup("#champLongitude").queryAs(TextField.class)).write("5°26'51\"E");
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);

        GesteVisible.cliquer(robot, "#boutonValider");
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> robot.lookup(".carte-point").queryAll().size() > 3);
        Respiration.surLeMomentCle(robot);

        // Le verdict se lit SUR L'ÉCRAN, et non dans le ViewModel : une carte de point porte
        // « GPS : voir sur la carte » quand elle a une position, et « GPS manquant : placer sur la
        // carte » sinon. Un point de PLUS qui se dit situé prouve que les degrés/minutes/secondes ont
        // été compris - et c'est ce que le spectateur peut juger.
        assertThat(pointsSitues(robot))
                .as("les degrés/minutes/secondes doivent valoir une position, sans quoi le champ accepte"
                        + " un texte qui ne mène nulle part")
                .isEqualTo(situesAvant + 1);
        Respiration.leTempsDeLire(robot);
    }

    /// Combien de points se disent situés.
    ///
    /// On compte les LIENS, pas les noeuds : `.gps-ok` est porté par l'hyperlien **et** par son
    /// icône, qui en hérite. Le premier jet comptait donc deux fois chaque point, et l'écart qu'il
    /// mesurait n'était pas celui qu'il croyait.
    private static int pointsSitues(FxRobot robot) {
        return (int) robot.lookup(".gps-ok").queryAll().stream()
                .filter(Hyperlink.class::isInstance)
                .count();
    }

    @Test
    @CasDeRecette(value = "S1-24", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-24 · ajouter un point depuis la fiche : sa carte paraît là où il n'y en avait pas")
    void ajouter_un_point_le_fait_paraitre_sur_la_fiche(FxRobot robot) throws TimeoutException {
        ouvrirLaFiche(robot, TITRE_CARRE);

        // La fiche AVANT, avec ses trois points et pas un de plus. C'est ce que la revue réclamait :
        // « montrer la fenêtre avant d'ouvrir la modale pour bien montrer que le point a été créé par
        // l'action de la modale » (#4175). Sans cet état de départ, la carte qui paraît ne se rattache
        // à rien.
        long avant = robot.lookup(".carte-point").queryAll().size();
        assertThat(avant).isEqualTo(3);
        Respiration.leTempsDeLire(robot);

        robot.clickOn("+ Ajouter un point");
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> robot.lookup("#champCode").tryQuery().isPresent());
        Respiration.leTempsDeLire(robot);

        robot.clickOn(robot.lookup("#champCode").queryAs(TextField.class)).write("D4");
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.avantLeGeste(robot);

        robot.clickOn("#boutonValider");
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> robot.lookup(".carte-point").queryAll().size() > avant);
        Respiration.surLeMomentCle(robot);

        assertThat(robot.lookup(".carte-point-code").queryAll().stream()
                        .filter(Label.class::isInstance)
                        .map(noeud -> ((Label) noeud).getText())
                        .toList())
                .as("le point que la modale vient de créer est sur la fiche, avec les trois autres")
                .contains("A1", "B2", "C3", "D4");
    }

    /// Le geste que fait un observateur : depuis « Mes sites », cliquer la carte du carré.
    ///
    /// La carte se cherche **par son titre**, et son absence est une erreur qui la nomme. Chercher
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
    /// A1 et B2 sont séparés d'environ cent mètres, ce qui est **sous le seuil de proximité** : c'est
    /// ce qui fait paraître l'alerte que `S1-20` fait juger. Un écart choisi au hasard rendrait un clip
    /// vert et muet sur la moitié du cas.
    private void semerLesDeuxCarres(Injector inj, SourceDeDonnees source) {
        ServiceSites service = inj.getInstance(ServiceSites.class);

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
        inj.getInstance(LienVigieChiroDao.class)
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
