package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.DiscussionValidateur;
import fr.univ_amu.iut.audio.viewmodel.ExporteurAudio;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.VueSauvegardee;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ClesCriteres;
import fr.univ_amu.iut.commun.view.CritereListe;
import fr.univ_amu.iut.commun.view.NavigationDeTestModule;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.FenetreDuBanc;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Jugement;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.film.EnregistreurDeFilm;
import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import fr.univ_amu.iut.validation.model.ExportObservationsEtSons;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MarquageDouteux;
import fr.univ_amu.iut.validation.model.PlageNuitPassage;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.SaisieCertitude;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Les scénarios qui **jouent** `S6-25`, `S6-26`, `S6-27` et `S6-29`, pour qu'un humain les tranche
/// en regardant (#4055).
///
/// ## Pourquoi cet écran-ci
///
/// La session `s6-exploiter-piloter.md` le dit : ces faits sont « à dérouler sur n'importe lequel des
/// cinq écrans à barre, et **au moins une fois sur Sons & validation**, qui porte les dix critères ».
/// C'est donc celui-là.
///
/// ## Ce que ces tests prouvent, et ce qu'ils ne prouvent pas
///
/// Ils prouvent que l'état existe : la table garde son compte, le menu s'est resserré, la valeur
/// impossible est toujours cochée. Ils ne prouvent **pas** qu'on le **voit** - qu'une valeur hors jeu
/// se distingue à l'œil d'une valeur ordinaire, à taille d'écran habituelle, est un jugement d'humain
/// et pas une assertion sur une classe de style. D'où `Jugement.HUMAIN`.
///
/// Les assertions ne sont pas décoratives pour autant. Un scénario qui n'assert rien échouerait en
/// **silence** : robot mort, clip noir, et le contrôle de couverture du montage n'y verrait qu'une
/// fenêtre de moins.
///
/// ## Le jeu d'essai, et pourquoi il a cette forme
///
/// Quatre observations, deux critères qui se croisent. `Barbastelle d'Europe` est la seule espèce
/// dont **aucune** ligne n'est validée : filtrer sur « Statut = validée » la fait donc sortir du jeu
/// courant, ce qui est exactement l'état que `S6-26` et `S6-27` demandent de voir. Sans ce croisement,
/// resserrer un critère ne changerait rien au domaine de l'autre et les deux cas n'auraient rien à
/// montrer.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class})
class ScenarioPerceptifFiltresTest {

    /// L'espèce que « Statut = à revoir » fait sortir du jeu : elle n'a que des lignes **validées**.
    private static final String ESPECE_HORS_JEU = "Noctule de Leisler";

    /// Et le lieu, même raisonnement : ses deux lignes sont validées, aucune n'est à revoir.
    private static final String LIEU_HORS_JEU = "640380";

    private static final String LIEU_A_REVOIR = "640381";

    /// Un carré que la vue enregistrée réclame et que les données ne portent pas.
    private static final String LIEU_DISPARU = "649999";

    private static final String VUE_ENREGISTREE = "Mes carrés";

    @TempDir
    Path dossierReglages;

    @Start
    void start(Stage stage) throws Exception {
        ServiceValidation service = mock(ServiceValidation.class);
        ProjectionsAudioDao projections = mock(ProjectionsAudioDao.class);
        ServiceBibliotheque bibliotheque = mock(ServiceBibliotheque.class);
        when(service.taxonsDisponibles()).thenReturn(List.of());
        when(service.cheminAudio(anyLong())).thenReturn(Optional.empty());
        when(projections.lignesAudioReferences("u-1"))
                .thenReturn(List.of(
                        ligne(1, 10, "Pippip", "Pipistrelle commune", LIEU_HORS_JEU, StatutObservation.VALIDEE),
                        ligne(2, 11, "Nyclei", ESPECE_HORS_JEU, LIEU_HORS_JEU, StatutObservation.VALIDEE),
                        ligne(3, 12, "Barbar", "Barbastelle d'Europe", LIEU_A_REVOIR, StatutObservation.NON_TOUCHEE),
                        ligne(4, 13, "Pippip", "Pipistrelle commune", LIEU_A_REVOIR, StatutObservation.NON_TOUCHEE)));
        DepotVues depotVues = mock(DepotVues.class);
        // Une vue enregistrée qui référence un lieu ABSENT des données. C'est l'état de `S6-28` :
        // rejouer une vue dont une valeur a disparu doit faire paraître le bandeau, et la phrase doit
        // nommer la valeur manquante. Le carré est inventé pour cette raison précise, et c'est écrit
        // ici pour qu'on ne le « corrige » pas en croyant à une coquille.
        when(depotVues.findByFeature("audio"))
                .thenReturn(List.of(new VueSauvegardee(
                        1L,
                        "audio",
                        VUE_ENREGISTREE,
                        "{\"texte\":\"\",\"criteres\":[{\"nom\":\"" + ClesCriteres.LIEU + "\",\"valeurs\":[\""
                                + LIEU_DISPARU + "\"]}]}")));

        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Provides
                    EspecesPrioritaires especesPrioritaires() {
                        return () -> Set.of("Pippip");
                    }

                    @Provides
                    AudioViewModel viewModel() {
                        return new AudioViewModel(
                                service,
                                projections,
                                mock(PlageNuitPassage.class),
                                mock(ValidationManuelle.class),
                                mock(MarquageDouteux.class),
                                mock(SaisieCertitude.class),
                                mock(RevueEnLot.class),
                                new ExporteurAudio(
                                        service,
                                        bibliotheque,
                                        new ExportObservationsEtSons(mock(SequenceDao.class), mock(SessionDao.class))),
                                mock(ServiceDisponibiliteAudio.class),
                                chemin -> true,
                                mock(DiscussionValidateur.class));
                    }

                    @Provides
                    DepotVues depotVues() {
                        return depotVues;
                    }

                    @Provides
                    ImportVigieChiroViewModel importVigieChiro() {
                        return new ImportVigieChiroViewModel(Optional.empty());
                    }

                    @Provides
                    PublicationCorrectionsViewModel publicationCorrections() {
                        return new PublicationCorrectionsViewModel(Optional.empty());
                    }

                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        return url -> {};
                    }

                    @Provides
                    PortailVigieChiro portail() {
                        return mock(PortailVigieChiro.class);
                    }

                    @Provides
                    ReglagesReactifs reglagesReactifs() {
                        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossierReglages));
                        new MigrationSchema(source).migrer();
                        return new ReglagesReactifs(new Reglages(new ReglagesDao(source)));
                    }
                },
                new NavigationDeTestModule());
        FXMLLoader loader = new FXMLLoader(SonsValidationController.class.getResource("SonsValidation.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        SonsValidationController controleur = loader.getController();
        controleur.ouvrirSur(new SourceObservations.References("u-1"));
        // `Habillage.scene`, et non `new Scene` : c'est lui qui embarque la typographie du produit.
        // Un clip tourné sans lui montrerait l'application dans une police qu'elle n'a jamais.
        FenetreDuBanc.poser(stage, vue, 1000, 700);
        FenetreDuBanc.afficher(stage);
    }

    @Test
    @CasDeRecette(value = "S6-25", jugement = Jugement.HUMAIN, portee = Portee.A_L_ECRAN)
    @DisplayName("S6-25 · une puce fraîchement ajoutée n'écarte rien : à regarder, la table ne bouge pas")
    void une_puce_fraichement_ajoutee_n_ecarte_rien(FxRobot robot) {
        TableView<?> table = table(robot);
        int avant = table.getItems().size();
        Respiration.avantLeGeste(robot);

        ajouterLaPuce(robot, "Espèce");
        // Le moment que ce cas existe pour montrer : la puce est posée et la table n'a PAS bougé.
        // Un état qui ne change pas demande d'être tenu plus longtemps qu'un état qui change.
        Respiration.surLeMomentCle(robot);

        assertThat(table.getItems())
                .as("une puce sans valeur choisie ne filtre rien ; si la table maigrit ici, elle filtre"
                        + " avant qu'on lui ait rien demandé")
                .hasSize(avant);
        assertThat(puces(robot).getChildren())
                .as("la puce est-elle seulement posée ? Sans cette question, un robot mort rendrait un"
                        + " clip où rien n'a été ajouté et où la table, forcément, n'a pas bougé.")
                .hasSize(1);
    }

    @Test
    @CasDeRecette(value = "S6-26", jugement = Jugement.HUMAIN, portee = Portee.A_L_ECRAN)
    @DisplayName("S6-26 · rouvrir une liste après un autre filtre : à regarder, elle offre moins de valeurs")
    void rouvrir_une_liste_apres_un_autre_filtre_montre_moins_de_valeurs(FxRobot robot) {
        ajouterLaPuce(robot, "Espèce");
        ComboBox<?> especes = listeDeLaPuce(robot, 0);
        Respiration.avantLeGeste(robot);

        derouler(robot, especes);
        List<String> premiereOuverture = valeurs(especes);
        Respiration.leTempsDeLire(robot);
        replier(robot, especes);

        // « Statut » filtre DÈS SON AJOUT, et c'est voulu : il s'ouvre présélectionné sur « à revoir »
        // (ADR 3099). C'est justement ce qui resserre le domaine de l'autre critère sans qu'on ait rien
        // d'autre à faire, donc ce qui rend ce cas-ci observable.
        ajouterLaPuce(robot, "Statut");
        Respiration.apresLeGeste(robot);

        derouler(robot, especes);
        List<String> secondeOuverture = valeurs(especes);
        Respiration.leTempsDeLire(robot);

        assertThat(secondeOuverture)
                .as("la liste doit se resserrer : proposer une valeur qui ne ramènerait rien fait perdre"
                        + " du temps à qui la choisit")
                .hasSizeLessThan(premiereOuverture.size());
        assertThat(secondeOuverture)
                .as(
                        "et ce qui reste est bien ce que l'autre filtre laisse passer : « %s » n'a que des"
                                + " lignes validées, donc plus aucune une fois « à revoir » posé",
                        ESPECE_HORS_JEU)
                .noneMatch(valeur -> valeur.contains(ESPECE_HORS_JEU));
    }

    @Test
    @CasDeRecette(value = "S6-27", jugement = Jugement.HUMAIN, portee = Portee.A_L_ECRAN)
    @DisplayName("S6-27 · une valeur cochée devenue impossible reste cochée : à regarder, elle se distingue")
    void une_valeur_cochee_devenue_impossible_se_distingue(FxRobot robot) {
        // « Lieu » est le SEUL critère à cocher de cet écran ; les autres sont des listes déroulantes,
        // où une valeur devenue impossible n'aurait pas de case à garder. Mesuré en faisant dire à
        // l'écran, puce par puce, quel contrôle il pose - et non déduit du nom des critères.
        ajouterLaPuce(robot, "Lieu");
        MenuButton lieux = menuDeLaPuce(robot, 0);
        String lieuCoche = valeurCochable(lieux, LIEU_HORS_JEU);
        cocher(robot, lieux, lieuCoche);
        Respiration.avantLeGeste(robot);

        ajouterLaPuce(robot, "Statut");
        Respiration.apresLeGeste(robot);

        ouvrirLeMenu(robot, lieux);
        // Le moment que ce cas existe pour montrer : la valeur devenue impossible est toujours
        // cochée, rangée en fin de liste. C'est à l'oeil que se juge sa distinction.
        Respiration.surLeMomentCle(robot);

        assertThat(cochees(lieux))
                .as("le filtre posé ne doit pas se relâcher tout seul : l'écran montrerait alors plus"
                        + " que ce qu'il annonce")
                .contains(lieuCoche);
        assertThat(valeurs(lieux))
                .as("elle reste visible, sans quoi on ne saurait pas pourquoi la table est vide")
                .contains(lieuCoche);
        // L'assertion qui porte le cas, et qui manquait. Les deux ci-dessus sont vraies d'une valeur
        // qui n'est JAMAIS devenue impossible : mutées sur le lieu qui reste dans le jeu, elles
        // passaient toutes les deux. Le cas ne vérifiait donc pas ce que son nom annonce.
        //
        // C'est la marque « hors du jeu courant » qui distingue l'état, et c'est elle que l'humain
        // juge ensuite à l'œil - le test dit qu'elle est posée, pas qu'elle se voit.
        assertThat(marquees(lieux))
                .as("marquée comme ne ramenant rien, sinon elle se lit comme un choix ordinaire")
                .containsExactly(lieuCoche);
    }

    @Test
    @CasDeRecette(value = "S6-29", jugement = Jugement.HUMAIN, portee = Portee.A_L_ECRAN)
    @DisplayName("S6-29 · « Tout effacer » rend la table entière : à regarder, en un seul clic")
    void tout_effacer_rend_la_table_entiere(FxRobot robot) {
        TableView<?> table = table(robot);
        int entiere = table.getItems().size();

        ajouterLaPuce(robot, "Statut");
        WaitForAsyncUtils.waitForFxEvents();
        int filtree = table.getItems().size();
        Respiration.avantLeGeste(robot);

        robot.clickOn("#boutonToutEffacer");
        WaitForAsyncUtils.waitForFxEvents();
        // Le moment que ce cas existe pour montrer : un seul clic, et la table revient entière.
        Respiration.surLeMomentCle(robot);

        assertThat(filtree)
                .as("le filtre a-t-il seulement mordu ? Sans cette question, « Tout effacer » rendrait"
                        + " une table qui n'avait jamais maigri, et le clip ne montrerait rien.")
                .isLessThan(entiere);
        assertThat(table.getItems()).as("un seul clic rend la table entière").hasSize(entiere);
        assertThat(puces(robot).getChildren())
                .as("et la barre est vide : une puce qui survivrait ne filtrerait plus rien tout en"
                        + " prétendant le contraire")
                .isEmpty();
    }

    @Test
    @CasDeRecette(value = "S6-28", jugement = Jugement.HUMAIN, portee = Portee.A_L_ECRAN)
    @DisplayName("S6-28 · rejouer une vue dont une valeur a disparu : à regarder, la phrase la nomme")
    void rejouer_une_vue_dont_une_valeur_a_disparu_fait_paraitre_le_bandeau(FxRobot robot) {
        Label phrase = robot.lookup("#lblRetour").queryAs(Label.class);
        assertThat(robot.lookup("#bandeauRetour").queryAs(Node.class).isVisible())
                .as("rien n'a encore été rejoué : le bandeau ne doit pas être là d'avance, sinon le"
                        + " clip montrerait un message qui précède son geste")
                .isFalse();
        Respiration.avantLeGeste(robot);

        robot.clickOn(ongletDeLaVue(robot, VUE_ENREGISTREE));
        WaitForAsyncUtils.waitForFxEvents();
        // Le moment que ce cas existe pour montrer : le bandeau paraît, et c'est SA PHRASE qu'on
        // vient lire.
        Respiration.surLeMomentCle(robot);

        assertThat(robot.lookup("#bandeauRetour").queryAs(Node.class).isVisible())
                .as("un filtre ne s'élargit jamais en silence : la vue a été rejouée sans l'une de ses"
                        + " valeurs, il faut le dire")
                .isTrue();
        assertThat(phrase.getText())
                .as("la phrase nomme la vue rejouée et la valeur qui manque")
                .contains(VUE_ENREGISTREE)
                .contains(LIEU_DISPARU);
        // « sans jargon ni clé technique » : la clé de sérialisation du critère n'a rien à faire
        // dans une phrase lue par un utilisateur. C'est la moitié du cas, et sans cette assertion il
        // resterait vert sur « la vue Mes carrés a été rejouée sans lieu=649999 ».
        assertThat(phrase.getText())
                .as("la clé de sérialisation « %s » ne doit pas paraître", ClesCriteres.LIEU)
                .doesNotContain(ClesCriteres.LIEU + "=")
                .doesNotContain("\"" + ClesCriteres.LIEU + "\"");
    }

    // --------------------------------------------------------------------------------------------

    private static LigneObservationAudio ligne(
            long id, long seq, String codeTaxon, String nomEspece, String carre, StatutObservation statut) {
        return new LigneObservationAudio(
                id,
                seq,
                7L,
                1,
                "2026-06-20",
                carre,
                "A1",
                "Site " + carre,
                codeTaxon,
                0.9,
                codeTaxon,
                0.95,
                statut,
                true,
                "",
                45,
                nomEspece,
                nomEspece,
                null,
                "Chiroptères",
                "PaRec_" + seq + "_000.wav",
                0.20,
                0.32,
                LocalDateTime.of(2026, 4, 22, 22, 0).plusMinutes(seq),
                false,
                Certitude.PROBABLE,
                null,
                null,
                null,
                0,
                null);
    }

    private static TableView<?> table(FxRobot robot) {
        return robot.lookup("#tableObservations").queryAs(TableView.class);
    }

    private static FlowPane puces(FxRobot robot) {
        return robot.lookup("#pucesFiltres").queryAs(FlowPane.class);
    }

    /// L'éditeur de la n-ième puce. Structure d'une puce : `[Label, éditeur, bouton de retrait]`.
    private static Node editeurDeLaPuce(FxRobot robot, int rang) {
        return ((HBox) puces(robot).getChildren().get(rang)).getChildren().get(1);
    }

    private static ComboBox<?> listeDeLaPuce(FxRobot robot, int rang) {
        return (ComboBox<?>) editeurDeLaPuce(robot, rang);
    }

    private static MenuButton menuDeLaPuce(FxRobot robot, int rang) {
        return (MenuButton) editeurDeLaPuce(robot, rang);
    }

    /// L'onglet d'une vue enregistrée, dans la barre des onglets.
    private static Node ongletDeLaVue(FxRobot robot, String nom) {
        FlowPane barre = robot.lookup("#barreOnglets").queryAs(FlowPane.class);
        return robot.from(barre).lookup(nom).query();
    }

    /// Ajoute une puce comme un utilisateur : le bouton « + Filtre », puis l'entrée qui la nomme.
    ///
    /// La première version faisait `itemParLibelle(...).fire()`, et je l'avais choisi exprès pour
    /// ne pas dépendre de l'endroit où le système pose la fenêtre du menu. Ce raisonnement servait la
    /// robustesse du test et DÉTRUISAIT le film : les puces apparaissaient seules, sans qu'aucun
    /// geste ne les explique. Retour de la revue de `S1-26`, qui vaut pour ces cinq cas aussi.
    ///
    /// Le risque que je fuyais a été éprouvé avant d'écrire ceci : les cinq cas passent sous filmage
    /// avec de vrais clics. La prudence n'était pas fondée.
    private static void ajouterLaPuce(FxRobot robot, String libelle) {
        robot.clickOn("#menuAjoutFiltre");
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.entreDeuxGestes(robot);
        GesteVisible.cliquer(robot, libelle);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// On déroule par `show()` et non par un clic. Une liste déroulante s'affiche dans une **fenêtre
    /// à part** ; TestFX sait cliquer sur le contrôle, mais la position des entrées dépend alors de
    /// l'endroit où le système a posé cette fenêtre. `show()` déroule la même liste sans en dépendre.
    /// Déroule une liste par un CLIC, pour que le film montre le geste.
    private static void derouler(FxRobot robot, ComboBox<?> liste) {
        robot.clickOn(liste);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// Replie par la touche d'échappement : c'est le geste, et il évite de cliquer au hasard hors de
    /// la liste, ce qui pourrait atteindre un autre contrôle.
    private static void replier(FxRobot robot, ComboBox<?> liste) {
        robot.type(javafx.scene.input.KeyCode.ESCAPE);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void ouvrirLeMenu(FxRobot robot, MenuButton menu) {
        robot.clickOn(menu);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// Coche une valeur par un clic sur son entrée, menu ouvert.
    private static void cocher(FxRobot robot, MenuButton menu, String valeur) {
        ouvrirLeMenu(robot, menu);
        Respiration.entreDeuxGestes(robot);
        GesteVisible.cliquer(robot, valeur);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// Les valeurs offertes par une liste déroulante, telles qu'on peut les comparer.
    private static List<String> valeurs(ComboBox<?> liste) {
        return liste.getItems().stream().map(String::valueOf).toList();
    }

    /// Les entrées cochables d'un menu : ses en-têtes de section et ses séparateurs n'en sont pas.
    private static List<String> valeurs(MenuButton menu) {
        return menu.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(MenuItem::getText)
                .toList();
    }

    /// Les entrées marquées « hors du jeu courant » par leur classe de style.
    private static List<String> marquees(MenuButton menu) {
        return menu.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .filter(item -> item.getStyleClass().contains(CritereListe.CLASSE_VALEUR_HORS_JEU))
                .map(CheckMenuItem::getText)
                .toList();
    }

    private static List<String> cochees(MenuButton menu) {
        return menu.getItems().stream()
                .filter(item -> item instanceof CheckMenuItem coche && coche.isSelected())
                .map(MenuItem::getText)
                .toList();
    }

    /// L'entrée cochable qui porte `fragment`. Le libellé complet d'un lieu n'est pas connu d'avance :
    /// il compose le carré et son nom convivial (« 640380 · Site 640380 »).
    private static String valeurCochable(MenuButton menu, String fragment) {
        return valeurs(menu).stream()
                .filter(valeur -> valeur != null && valeur.contains(fragment))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "aucune entrée cochable contenant « " + fragment + " » ; le menu porte " + valeurs(menu)));
    }
}
