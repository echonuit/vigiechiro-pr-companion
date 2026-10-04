package fr.univ_amu.iut.lot.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.ResultatLancement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ReleveTraitement;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.view.NavigationDeTestModule;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.StatutDepotUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import fr.univ_amu.iut.lot.viewmodel.TraitementViewModel;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.GesteVisible;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Écran **M-Lot en mode connecté** (#984) : les tests d'intégration existants
/// ([LotVueIntegrationTest]) montent l'écran **sans** dépôt VigieChiro (`Optional.empty()`), donc sans
/// participation liée : ils ne peuvent pas exercer l'étape ④ dans son mode « Lancer la participation »
/// ni le bouton « Réinitialiser le dépôt ». Ce fichier monte l'écran **avec** un [DepotVigieChiro]
/// mocké et couvre les liaisons que le chantier #984 a ajoutées.
///
/// Le passage de départ est en « **Dépôt en cours** » avec un plan de dépôt déjà rempli : c'est l'état
/// réel **après** un téléversement par l'API, et celui qui a fait apparaître la régression corrigée
/// (bouton ④ désactivé dès la fin de l'upload, alors que c'est précisément le moment de lancer le
/// traitement serveur).
@ExtendWith(ApplicationExtension.class)
class LotDepotConnecteViewTest {

    private static final long ID_PASSAGE = 42L;
    private static final ContextePassage CONTEXTE =
            new ContextePassage(ID_PASSAGE, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière"));

    private ServiceLot service;
    private DepotVigieChiro depot;
    private SuiviTraitement suivi;
    private LotController controleur;
    private DepotViewModel depotViewModel;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceLot.class);
        depot = mock(DepotVigieChiro.class);
        depotViewModel = new DepotViewModel(service, Optional.of(depot));
        suivi = mock(SuiviTraitement.class);
        // État réel après un dépôt par l'API : nuit téléversée (plan rempli), participation liée.
        when(service.consulterLot(anyLong()))
                .thenReturn(new EtatLot(StatutWorkflow.DEPOT_EN_COURS, "/ws/session-42", 2, 8192L, List.of(), null));
        when(service.unitesDepot(ID_PASSAGE)).thenReturn(List.of(unite("Car-1.zip", StatutDepotUnite.DEPOSE)));
        when(depot.participationLiee(ID_PASSAGE)).thenReturn(true);
        // Le vrai service ne renvoie jamais null : sans ce defaut, un test qui declenche un releve sans le
        // stubber ferait tomber l IHM sur un NPE etranger a ce qu il verifie.
        lenient().when(suivi.relever(anyLong())).thenReturn(Traitement.absent());
        lenient().when(suivi.dernierReleve(anyLong())).thenReturn(Optional.empty());

        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Provides
                    LotViewModel viewModel() {
                        return new LotViewModel(service);
                    }

                    @Provides
                    DepotViewModel depotViewModel() {
                        return depotViewModel;
                    }

                    @Provides
                    TraitementViewModel traitementViewModel() {
                        return new TraitementViewModel(Optional.of(suivi), Horloge.systeme());
                    }

                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        return lien -> {};
                    }
                },
                new NavigationDeTestModule());
        FXMLLoader loader = new FXMLLoader(LotController.class.getResource("Lot.fxml"));
        loader.setControllerFactory(DiagnosticGuice.pour(injector));
        Parent vue = loader.load();
        controleur = loader.getController();
        controleur.confirmateur().definir(message -> true); // pas de dialogue natif bloquant sous TestFX
        controleur.ouvrirSur(CONTEXTE);
        FenetreAjustable.poser(stage, vue, 980, 980);
        FenetreAjustable.afficher(stage);
    }

    /// Connecté, le téléversement produit ses ZIP et les supprime une fois en ligne : l'étape 2 vide
    /// disait « aucune archive » pendant que l'étape 3 les envoyait (#5679, recette de #5597).
    @Test
    @DisplayName(
            "#5679 : connecté sans envoi en cours, l'étape 2 dit que le téléversement produit et supprime ses archives")
    void connecte_l_etape_2_dit_ou_sont_les_archives(FxRobot robot) {
        assertThat(texteDeLEtape2(robot))
                .doesNotContain("pour l'instant")
                .contains("Aucune archive conservée sur ce poste")
                .contains("dépôt manuel");
    }

    @Test
    @DisplayName("#5679 : pendant un téléversement, l'étape 2 renvoie à l'étape 3 qui suit les archives")
    void pendant_un_televersement_l_etape_2_renvoie_a_l_etape_3(FxRobot robot) {
        Attente.surLeFil(depotViewModel::marquerEnCours, "un téléversement démarre", 5_000L);

        assertThat(texteDeLEtape2(robot)).contains("au fil de l'envoi").contains("suivez-les à l'étape 3");
    }

    /// Le texte que la table de l'étape 2 affiche quand elle est vide, lu sur le fil JavaFX.
    private static String texteDeLEtape2(FxRobot robot) {
        return Attente.surLeFil(
                () -> ((Label) robot.lookup("#tableArchives")
                                .queryAs(TableView.class)
                                .getPlaceholder())
                        .getText(),
                "lire le texte vide de l'étape 2",
                5_000L);
    }

    /// Après un dépôt connecté complet, deux boutons « Lancer la participation » coexistaient : celui du
    /// compte rendu et celui de l'étape 4, sous un titre resté « Marquer le passage déposé » (#5676,
    /// recette de #5597). Le compte rendu nomme la prochaine étape ; seule l'étape 4 la porte.
    @Test
    @DisplayName("#5676 : après un dépôt complet, un seul bouton lance la participation, sous un titre qui le dit")
    void apres_un_depot_complet_un_seul_bouton_lance_la_participation(FxRobot robot) {
        Attente.surLeFil(
                () -> depotViewModel.appliquerBilan(new BilanDepot("p-1", 1, List.of())),
                "un dépôt complet se termine",
                5_000L);

        assertThat(Attente.surLeFil(
                        () -> robot.lookup(LotDepotConnecteViewTest::lanceLaParticipation)
                                .queryAll()
                                .size(),
                        "compter les boutons qui lancent la participation",
                        5_000L))
                .as("le compte rendu et l'étape 4 ne doivent pas offrir le même geste")
                .isEqualTo(1);
        assertThat(Attente.surLeFil(
                        () -> robot.lookup(".section-titre").queryAllAs(Label.class).stream()
                                .map(Label::getText)
                                .filter(titre -> titre.startsWith("4."))
                                .findFirst()
                                .orElseThrow(),
                        "lire le titre de l'étape 4",
                        5_000L))
                .isEqualTo("4. Lancer la participation");
    }

    /// Un bouton « Lancer la participation » que l'on voit : lui et tous ses parents sont visibles.
    private static boolean lanceLaParticipation(Node noeud) {
        if (!(noeud instanceof ButtonBase bouton) || !"Lancer la participation".equals(bouton.getText())) {
            return false;
        }
        for (Node n = noeud; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return true;
    }

    @Test
    @DisplayName("#1998 : connecté et sans archives, un SEUL bouton primaire, « Téléverser », pas « Générer »")
    void un_seul_bouton_primaire_quand_connecte_sans_archives(FxRobot robot) {
        // Défaut trouvé en REGARDANT la capture, pas par un test : depuis que le téléversement produit
        // ses propres archives, « connecté sans archives » est devenu un état courant, et « Générer »
        // y reprenait le rôle primaire face à « Téléverser » qui le porte en FXML. Deux boutons mis en
        // avant, l'utilisateur ne sait plus lequel est la marche suivante.
        Button generer = robot.lookup("#btnGenererArchives").queryAs(Button.class);
        Button televerser = robot.lookup("#btnTeleverser").queryAs(Button.class);

        assertThat(televerser.getStyleClass())
                .as("l'action de la marche courante")
                .contains("bouton-primaire");
        assertThat(generer.getStyleClass())
                .as("générer n'est plus qu'une option pour le dépôt manuel")
                .contains("bouton-secondaire")
                .doesNotContain("bouton-primaire");
    }

    @Test
    @DisplayName("#984 : participation liée → l'étape ④ devient « Lancer la participation », cliquable même"
            + " en « Dépôt en cours »")
    void participation_liee_bascule_le_bouton_et_le_garde_actif(FxRobot robot) {
        Button deposer = robot.lookup("#btnDeposer").queryAs(Button.class);

        assertThat(deposer.getText()).isEqualTo("Lancer la participation");
        // Régression : la garde « Marquer déposé » (statut « Prêt à déposer ») désactivait le bouton dès la
        // fin de l'upload : or c'est justement là qu'il faut pouvoir lancer le traitement.
        assertThat(deposer.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("#984 : clic sur « Lancer la participation » → le compte rendu du compute est demandé au moteur")
    void clic_lance_le_traitement_serveur(FxRobot robot) {
        when(depot.lancerTraitement(ID_PASSAGE)).thenReturn(ResultatLancement.accepte());

        robot.clickOn("#btnDeposer");

        // Le compute part sur un fil de fond (executerEnFond) : on laisse le temps à l'appel d'arriver.
        verify(depot, timeout(5_000)).lancerTraitement(ID_PASSAGE);
    }

    /// Le bouton se grisait quelques secondes puis revenait à l'identique, le résultat étant parti dans le
    /// bandeau du haut de page, hors de vue (#5682, recette de #5597). Il se dit maintenant dans l'étape 4,
    /// pour chacune des issues, et un refus cite son motif comme la commande le fait déjà.
    @Test
    @DisplayName("#5682 : le résultat du lancement se dit dans l'étape 4, motif du refus compris, et pas au bandeau")
    void le_resultat_du_lancement_se_dit_dans_l_etape_4(FxRobot robot) {
        when(depot.lancerTraitement(ID_PASSAGE))
                .thenReturn(ResultatLancement.accepte())
                .thenReturn(ResultatLancement.dejaLance(Traitement.absent()))
                .thenReturn(ResultatLancement.refuse(403, "droits insuffisants"));

        lancerEtAttendre(robot, "Analyse demandée à Vigie-Chiro");
        assertThat(texteDuBandeau(robot))
                .as("le résultat ne se répète pas en haut de page")
                .isEmpty();
        lancerEtAttendre(robot, "L'analyse de cette nuit est déjà demandée");
        lancerEtAttendre(robot, "Vigie-Chiro a refusé de lancer l'analyse : HTTP 403 droits insuffisants.");
    }

    /// Clique « Lancer la participation » et attend que l'étape 4 dise `attendu`.
    private static void lancerEtAttendre(FxRobot robot, String attendu) {
        GesteVisible.cliquer(robot, "#btnDeposer");
        Attente.queSurLeFil(() -> texteDeLEtape4(robot).contains(attendu), "l'étape 4 dit « " + attendu + " »", 5_000L);
    }

    /// Tout le texte visible de la carte de l'étape 4, celle qui porte le bouton de lancement.
    private static String texteDeLEtape4(FxRobot robot) {
        Node carte = robot.lookup("#btnDeposer").query();
        while (carte != null && !carte.getStyleClass().contains("carte-section")) {
            carte = carte.getParent();
        }
        return robot
                .from(carte)
                .lookup((Node n) -> n instanceof Labeled && n.isVisible())
                .queryAllAs(Labeled.class)
                .stream()
                .map(Labeled::getText)
                .filter(texte -> texte != null && !texte.isBlank())
                .collect(Collectors.joining(" | "));
    }

    private static String texteDuBandeau(FxRobot robot) {
        return Attente.surLeFil(
                () -> robot.lookup("#lblRetour").queryAs(Label.class).getText(), "lire le bandeau", 5_000L);
    }

    @Test
    @DisplayName("#984 : « Réinitialiser le dépôt » visible dès qu'un plan existe et efface le suivi local")
    void reinitialiser_efface_le_suivi_local(FxRobot robot) {
        Button reinitialiser = robot.lookup("#btnReinitialiserDepot").queryAs(Button.class);
        assertThat(reinitialiser.isVisible()).isTrue();
        assertThat(reinitialiser.isDisabled()).isFalse();

        robot.clickOn("#btnReinitialiserDepot");

        verify(service).reinitialiserDepot(ID_PASSAGE);
    }

    @Test
    @DisplayName("#984, #5676 : sans participation liée, l'étape ④ reste « Marquer déposé », titre compris")
    void sans_participation_le_bouton_reste_marquer_depose(FxRobot robot) {
        when(depot.participationLiee(ID_PASSAGE)).thenReturn(false);
        robot.interact(() -> controleur.ouvrirSur(CONTEXTE)); // réhydrate depuis le lien local

        assertThat(robot.lookup("#btnDeposer").queryAs(Button.class).getText()).isEqualTo("Marquer déposé");
        assertThat(Attente.surLeFil(
                        () -> robot.lookup("#lblTitreDeposer")
                                .queryAs(Label.class)
                                .getText(),
                        "lire le titre de l'étape 4",
                        5_000L))
                .isEqualTo("4. Marquer le passage déposé");
    }

    @Test
    @DisplayName("#1263 : la zone « Traitement Vigie-Chiro » apparaît dès qu'une participation est liée")
    void zone_traitement_visible_une_fois_la_nuit_deposee(FxRobot robot) {
        assertThat(robot.lookup("#zoneTraitement").queryAs(VBox.class).isVisible())
                .as("nuit déposée par l'application : il y a désormais quelque chose à suivre")
                .isTrue();

        when(depot.participationLiee(ID_PASSAGE)).thenReturn(false);
        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));

        assertThat(robot.lookup("#zoneTraitement").queryAs(VBox.class).isVisible())
                .as("dépôt manuel : rien à suivre, la carte n'a pas lieu d'être")
                .isFalse();
    }

    @Test
    @DisplayName("#1263 : à l'ouverture, le dernier état connu est affiché SANS appel réseau (cache #1262)")
    void ouverture_affiche_le_dernier_etat_connu_sans_reseau(FxRobot robot) {
        // Hors connexion, l'écran doit dire ce qu'il sait (et de quand cela date) plutôt que rien.
        when(suivi.dernierReleve(ID_PASSAGE))
                .thenReturn(Optional.of(new ReleveTraitement(
                        ID_PASSAGE,
                        "part-1",
                        new Traitement(EtatTraitement.EN_COURS, null, "2026-07-13T09:00:00+00:00", null, null, null),
                        "2026-07-13T09:05:00")));

        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));

        assertThat(robot.lookup("#lblEtatTraitement").queryAs(Label.class).getText())
                .contains("Analyse en cours");
        assertThat(robot.lookup("#lblFraicheurTraitement").queryAs(Label.class).getText())
                .as("la fraîcheur de l'information est due à l'utilisateur")
                .contains("Dernier état connu");
        verify(suivi, never()).relever(anyLong()); // aucune requête : on a seulement relu le cache
    }

    @Test
    @DisplayName("#1263 : « Actualiser » interroge le serveur et restitue l'état frais")
    void actualiser_interroge_le_serveur(FxRobot robot) {
        when(suivi.relever(ID_PASSAGE))
                .thenReturn(new Traitement(EtatTraitement.FINI, null, null, "2026-07-13T10:05:00+00:00", null, null));

        actualiser(robot);

        verify(suivi, timeout(5_000)).relever(ID_PASSAGE);
        assertThat(robot.lookup("#lblEtatTraitement").queryAs(Label.class).getText())
                .contains("Analyse terminée", "prêtes à être importées");
    }

    @Test
    @DisplayName("#1263 : une nuit DÉJÀ analysée ne peut pas être relancée depuis l'IHM (ses observations"
            + " seraient détruites)")
    void nuit_analysee_le_bouton_de_lancement_est_garde(FxRobot robot) {
        // Le serveur, lui, accepterait : il supprimerait les observations pour recalculer, sans pouvoir les
        // régénérer (audio non conservé après un dépôt en archives, #1244). La garde est donc chez nous.
        when(suivi.relever(ID_PASSAGE))
                .thenReturn(new Traitement(EtatTraitement.FINI, null, null, "2026-07-13T10:05:00+00:00", null, null));

        actualiser(robot);
        verify(suivi, timeout(5_000)).relever(ID_PASSAGE);

        assertThat(robot.lookup("#btnDeposer").queryAs(Button.class).isDisabled())
                .as("relance interdite une fois la nuit analysée : ses observations seraient perdues")
                .isTrue();
        // Le bouton désactivé n'est pas muet (#789) : l'infobulle de l'enveloppe explique le refus et
        // renvoie vers l'import. Et la zone, elle, dit ce qu'il y a à faire.
        assertThat(robot.lookup("#lblEtatTraitement").queryAs(Label.class).getText())
                .contains("prêtes à être importées");
    }

    /// Une analyse planifiée laissait le bouton cliquable : deux clics ont reçu `400 Already PLANIFIE` dans
    /// le journal de #5597. Le relevé qui suit le lancement suffit à le griser (#5682).
    @Test
    @DisplayName("#5682 : une analyse demandée grise le bouton, et son explication renvoie à la carte du traitement")
    void une_analyse_demandee_grise_le_bouton(FxRobot robot) {
        when(depot.lancerTraitement(ID_PASSAGE)).thenReturn(ResultatLancement.accepte());
        when(suivi.relever(ID_PASSAGE))
                .thenReturn(
                        new Traitement(EtatTraitement.PLANIFIE, "2026-07-13T09:00:00+00:00", null, null, null, null));

        GesteVisible.cliquer(robot, "#btnDeposer");

        Attente.queSurLeFil(
                () -> robot.lookup("#btnDeposer").queryAs(Button.class).isDisabled(),
                "le bouton se grise une fois l'analyse planifiée",
                5_000L);
        assertThat(explicationDuBouton(robot))
                .isEqualTo("L'analyse de cette nuit est demandée à Vigie-Chiro : suivez-la dans la carte"
                        + " « Traitement Vigie-Chiro » ci-dessous.");
    }

    @Test
    @DisplayName("#5682 : à la réouverture, le dernier relevé « en cours » grise le bouton sans relevé réseau")
    void a_la_reouverture_le_dernier_releve_grise_le_bouton(FxRobot robot) {
        when(suivi.dernierReleve(ID_PASSAGE))
                .thenReturn(Optional.of(new ReleveTraitement(
                        ID_PASSAGE,
                        "part-1",
                        new Traitement(EtatTraitement.EN_COURS, null, "2026-07-13T09:00:00+00:00", null, null, null),
                        "2026-07-13T09:05:00")));

        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));

        assertThat(Attente.surLeFil(
                        () -> robot.lookup("#btnDeposer").queryAs(Button.class).isDisabled(),
                        "lire l'état du bouton",
                        5_000L))
                .as("une analyse en cours ne s'offre pas à être relancée, même après réouverture")
                .isTrue();
        verify(suivi, never()).relever(anyLong());
    }

    /// L'infobulle que l'enveloppe du bouton porte, là où `Tooltip.install` la range.
    private static String explicationDuBouton(FxRobot robot) {
        return Attente.surLeFil(
                () -> ((Tooltip) robot.lookup("#enveloppeDeposer")
                                .query()
                                .getProperties()
                                .get("javafx.scene.control.Tooltip"))
                        .getText(),
                "lire l'explication du bouton",
                5_000L);
    }

    /// Declenche « Actualiser » par son action plutot que par un clic : la carte « Traitement » est en bas
    /// d un flux plus long que l ecran headless (taille fixe), donc hors du cadre que le robot sait viser.
    private static void actualiser(FxRobot robot) {
        Button bouton = robot.lookup("#btnActualiserTraitement").queryAs(Button.class);
        robot.interact(bouton::fire);
    }

    private static DepotUnite unite(String identifiant, StatutDepotUnite statut) {
        return new DepotUnite(
                1L, ID_PASSAGE, identifiant, TypeDepotUnite.ZIP, statut, null, null, "2026-07-11T15:00:00");
    }
}
