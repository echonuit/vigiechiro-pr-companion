package fr.univ_amu.iut.lot.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.view.IconesSeverite;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.NavigationDeTestModule;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.ArchivePlanifiee;
import fr.univ_amu.iut.lot.model.ControleCoherence;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.StatutControle;
import fr.univ_amu.iut.lot.model.StatutDepotUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import fr.univ_amu.iut.lot.viewmodel.TraitementViewModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kordamp.ikonli.javafx.FontIcon;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Tests d'intégration TestFX **complémentaires** de l'écran **M-Lot** (`Lot.fxml` +
/// [LotController]).
///
/// Là où [LotViewTest] couvre l'affichage de base (statut/récap/dossier sur un passage `Vérifié` et
/// délégation du clic « Préparer »), ce fichier cible les comportements que l'audit 2026-06-18
/// signale comme souvent absents derrière un ViewModel pourtant complet : la **zone d'alertes** (R14)
/// qui n'apparaît qu'en présence d'alertes bloquantes, le **récapitulatif dérivé** de l'[EtatLot]
/// (volume lisible avec bascule Mo, garde `null`), le parcours **Marquer déposé** (état `Prêt à
/// déposer`) et le message de l'état terminal `Déposé`.
///
/// Chaque test fait un **vrai lookup** des contrôles par `fx:id` (`robot.lookup("#…")`) puis exerce
/// soit un toggle de visibilité, soit une **interaction** (clic). Un écran resté à l'état placeholder
/// (sans `@FXML` ni `onAction`) échoue donc, contrairement aux tests qui ne liraient que les
/// propriétés du ViewModel. L'état affiché provient du [ServiceLot] mocké : pour exercer un autre
/// état que la fixture de départ, on **re-stube** `consulterLot` puis on rouvre l'écran sur le fil
/// JavaFX via [#reouvrirAvec]. Aucune base de données.
@ExtendWith(ApplicationExtension.class)
class LotVueIntegrationTest {

    private static final long ID_PASSAGE = 42L;
    private static final ContextePassage CONTEXTE =
            new ContextePassage(ID_PASSAGE, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière"));

    private ServiceLot service;
    private LotController controleur;
    private LotViewModel viewModel;

    /// Faux ouvreur de lien (#251) : enregistre les URI demandées, sans ouvrir de gestionnaire de fichiers.
    private final List<String> liensOuverts = new ArrayList<>();

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceLot.class);
        // État de départ : passage Vérifié, aucune alerte → préparer actif, déposer inactif.
        when(service.consulterLot(anyLong()))
                .thenReturn(new EtatLot(StatutWorkflow.VERIFIE, "/ws/session-42", 2, 8192L, List.of(), null));
        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Provides
                    LotViewModel viewModel() {
                        if (viewModel == null) {
                            viewModel = new LotViewModel(service);
                        }
                        return viewModel;
                    }

                    @Provides
                    DepotViewModel depotViewModel() {
                        return new DepotViewModel(service, Optional.empty());
                    }

                    // Suivi du traitement serveur (#1263) : absent ici. Sans participation liee ni
                    // connexion, la zone « Traitement Vigie-Chiro » reste masquee, et l ecran est celui
                    // d avant.
                    @Provides
                    TraitementViewModel traitementViewModel() {
                        return new TraitementViewModel(Optional.empty(), Horloge.systeme());
                    }

                    @Provides
                    OuvreurDeLien ouvreurDeLien() {
                        return liensOuverts::add;
                    }
                },
                new NavigationDeTestModule());
        FXMLLoader loader = new FXMLLoader(LotController.class.getResource("Lot.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        controleur.ouvrirSur(CONTEXTE);
        // Scène haute : le flux ordonné à 4 étapes (#251) dépasse 640 px ; sans cela le bouton de
        // l'étape ④ (« Marquer déposé ») serait hors écran et non cliquable par le robot.
        stage.setScene(new Scene(vue, 980, 980));
        stage.show();
    }

    /// Re-stube `consulterLot` pour renvoyer `etat`, puis rouvre l'écran sur le même passage depuis
    /// le fil JavaFX. Les propriétés du ViewModel étant liées aux contrôles, la vue se met à jour.
    private void reouvrirAvec(FxRobot robot, EtatLot etat) {
        when(service.consulterLot(anyLong())).thenReturn(etat);
        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));
    }

    @Test
    @DisplayName("Emplacement (fil d'Ariane) : Mes sites › Carré N › Détails du passage N° X › Préparer le dépôt")
    void emplacement_reflete_le_passage() {
        assertThat(controleur.emplacement())
                .extracting(Lieu::libelle)
                .containsExactly("Mes sites", "Carré 640380", "Détails du passage N° 2", "Préparer le dépôt");
        assertThat(controleur.emplacement().get(0).estCliquable()).isTrue();
        assertThat(controleur.emplacement().get(3).estCliquable()).isFalse();
    }

    @Test
    @DisplayName("#254 : conforme → checklist tout satisfait, message masqué, préparer actif / déposer inactif")
    void conforme_checklist_tout_ok_et_preparer_actif(FxRobot robot) {
        // Checklist entièrement satisfaite (la fixture de départ en a une vide ; on re-stube).
        reouvrirAvec(
                robot,
                new EtatLot(
                        StatutWorkflow.VERIFIE,
                        "/ws/session-42",
                        2,
                        8192L,
                        List.of(
                                ok("Transformation des enregistrements"),
                                ok("Nommage des fichiers"),
                                ok("Journal du capteur")),
                        null));

        Label chemin = robot.lookup("#lblCheminDepot").queryAs(Label.class);
        VBox checklist = robot.lookup("#checklist").queryAs(VBox.class);
        // #1890 : l'état du lot a son propre libellé, permanent ; #lblRetour porte les comptes rendus.
        Label message = robot.lookup("#lblEtatLot").queryAs(Label.class);
        Button preparer = robot.lookup("#btnPreparer").queryAs(Button.class);
        Button deposer = robot.lookup("#btnDeposer").queryAs(Button.class);

        // Statut déporté en barre de statut (#693).
        assertThat(controleur.zonesStatutProperty().get().centre()).isEqualTo("Vérifié · 2 séquences · 8 Ko");
        // #251 : la cible du téléversement est le sous-dossier depot/ (archives ZIP), pas la session.
        assertThat(chemin.getText()).isEqualTo("/ws/session-42/depot");
        // #254 : la checklist reste affichée même quand tout est satisfait (chaque ligne cochée).
        assertThat(glyphesChecklist(checklist))
                .hasSize(3)
                .allMatch(glyphe -> glyphe.equals(IconesSeverite.glyphe(Severite.SUCCES)));
        // Pas de message d'état en fonctionnement nominal, et aucun compte rendu tant qu'on n'a rien fait.
        assertThat(message.isVisible()).isFalse();
        assertThat(robot.lookup("#bandeauRetour").queryAs(HBox.class).isVisible())
                .as("ouvrir un écran n'est pas une opération : rien à rapporter")
                .isFalse();
        assertThat(preparer.isDisabled()).isFalse();
        assertThat(deposer.isDisabled()).isTrue();
    }

    @Test
    @DisplayName("#254 : un contrôle en échec → checklist mixte, re-vérification possible, suite neutralisée")
    void controle_en_echec_checklist_reverifiable_suite_neutralisee(FxRobot robot) {
        reouvrirAvec(
                robot,
                new EtatLot(
                        StatutWorkflow.VERIFIE,
                        "/ws/session-42",
                        2,
                        8192L,
                        List.of(
                                ok("Verdict de vérification"),
                                echec("Transformation des enregistrements", "Transformation incomplète"),
                                echec("Nommage des fichiers", "Préfixe de fichier non conforme")),
                        null));

        VBox checklist = robot.lookup("#checklist").queryAs(VBox.class);
        // #1890 : l'état du lot a son propre libellé, permanent ; #lblRetour porte les comptes rendus.
        Label message = robot.lookup("#lblEtatLot").queryAs(Label.class);
        Button preparer = robot.lookup("#btnPreparer").queryAs(Button.class);
        Button genererArchives = robot.lookup("#btnGenererArchives").queryAs(Button.class);

        var textes = textesChecklist(checklist);
        var glyphes = glyphesChecklist(checklist);
        assertThat(textes).hasSize(3);
        assertThat(textes.get(0)).contains("Verdict de vérification");
        assertThat(glyphes.get(0)).isEqualTo(IconesSeverite.glyphe(Severite.SUCCES));
        // Le statut et son libellé se lisent ensemble : une icône juste sur la mauvaise ligne serait
        // aussi trompeuse qu'une icône fausse.
        for (int i = 0; i < textes.size(); i++) {
            if (textes.get(i).contains("Transformation incomplète")
                    || textes.get(i).contains("Préfixe de fichier non conforme")) {
                assertThat(glyphes.get(i)).isEqualTo(IconesSeverite.glyphe(Severite.ERREUR));
            }
        }
        assertThat(textes).anyMatch(t -> t.contains("Transformation incomplète"));
        assertThat(textes).anyMatch(t -> t.contains("Préfixe de fichier non conforme"));
        // Un contrôle bloque : « Vérifier et préparer » reste actionnable pour RELANCER la vérification, mais la
        // suite (génération des archives) est NEUTRALISÉE tant que la cohérence n'est pas rétablie.
        assertThat(preparer.isDisabled()).isFalse();
        assertThat(genererArchives.isDisabled()).isTrue();
        assertThat(message.isVisible()).isTrue();
        assertThat(message.getText()).contains("Cohérence");
    }

    @Test
    @DisplayName("Le récapitulatif est dérivé de l'EtatLot (volume avec bascule Mo)")
    void recap_derive_de_l_etat_lot_avec_bascule_mo(FxRobot robot) {
        // 5 séquences, ~3 Mo (3 × 1 048 576 octets) : le récap doit refléter ces valeurs, pas la
        // fixture de départ « 2 séquences · 8 Ko ».
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.VERIFIE, "/ws/session-42", 5, 3_145_728L, List.of(), null));

        assertThat(controleur.zonesStatutProperty().get().centre()).endsWith("5 séquences · 3 Mo");
    }

    @Test
    @DisplayName("Récapitulatif sans volume calculé : « volume inconnu », sans erreur")
    void recap_volume_inconnu_sans_erreur(FxRobot robot) {
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.VERIFIE, "/ws/session-42", 5, null, List.of(), null));

        assertThat(controleur.zonesStatutProperty().get().centre()).endsWith("5 séquences · volume inconnu");
    }

    @Test
    @DisplayName("« Prêt à déposer » : déposer actif, préparer inactif ; le clic délègue au service")
    void pret_a_deposer_active_deposer_et_clic_delegue(FxRobot robot) {
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 2, 8192L, List.of(), null));

        Button preparer = robot.lookup("#btnPreparer").queryAs(Button.class);
        Button deposer = robot.lookup("#btnDeposer").queryAs(Button.class);

        assertThat(preparer.isDisabled()).isTrue();
        assertThat(deposer.isDisabled()).isFalse();

        robot.clickOn("#btnDeposer");
        verify(service).marquerDepose(ID_PASSAGE);
    }

    @Test
    @DisplayName("État « Déposé » : statut affiché, message de dépôt visible, actions désactivées")
    void statut_depose_affiche_message_et_desactive_actions(FxRobot robot) {
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.DEPOSE, "/ws/session-42", 2, 8192L, List.of(), "2026-06-18"));

        // #1890 : l'état du lot a son propre libellé, permanent ; #lblRetour porte les comptes rendus.
        Label message = robot.lookup("#lblEtatLot").queryAs(Label.class);
        Button preparer = robot.lookup("#btnPreparer").queryAs(Button.class);
        Button deposer = robot.lookup("#btnDeposer").queryAs(Button.class);

        // Statut déporté en barre de statut (#693).
        assertThat(controleur.zonesStatutProperty().get().centre()).startsWith("Déposé");
        assertThat(message.isVisible()).isTrue();
        assertThat(message.getText()).contains("déposé");
        assertThat(preparer.isDisabled()).isTrue();
        assertThat(deposer.isDisabled()).isTrue();
    }

    @Test
    @DisplayName("#… : « Supprimer les archives » est désactivé quand il n'y a aucune archive sur disque")
    void supprimer_archives_desactive_sans_archives(FxRobot robot) {
        // État initial de start() = Vérifié, aucune archive sur disque → suppression indisponible.
        Button supprimer = robot.lookup("#btnSupprimerArchives").queryAs(Button.class);

        assertThat(supprimer.isDisabled()).isTrue();
    }

    @Test
    @DisplayName("#… : à la réouverture d'un passage déjà généré, la table se recharge et « Supprimer » s'active")
    void archives_rechargees_a_la_reouverture_et_suppression_active(FxRobot robot) {
        when(service.archivesDepot("/ws/session-42"))
                .thenReturn(List.of(
                        new ArchiveDepot(Path.of("/ws/session-42/depot/Car-1.zip"), 1, 2048L, 2),
                        new ArchiveDepot(Path.of("/ws/session-42/depot/Car-2.zip"), 2, 4096L, 3)));
        // Passage « Prêt à déposer » (pas encore déposé) avec des archives déjà présentes sur disque.
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 2, 8192L, List.of(), null));

        // La table de l'étape 2 est réhydratée depuis le disque (plus vide après navigation).
        TableView<?> table = robot.lookup("#tableArchives").queryAs(TableView.class);
        assertThat(table.getItems()).hasSize(2);
        // « Ouvrir le dossier » et « Supprimer les archives » sont actifs dès que des archives existent.
        assertThat(robot.lookup("#btnOuvrirDepot").queryAs(Button.class).isDisabled())
                .isFalse();
        assertThat(robot.lookup("#btnSupprimerArchives").queryAs(Button.class).isDisabled())
                .isFalse();
    }

    @Test
    @DisplayName("#2028 : la carte « Libérer l'espace disque » est masquée quand aucune archive n'est présente")
    void carte_liberer_espace_masquee_sans_archives(FxRobot robot) {
        // start() = Vérifié, aucune archive sur disque → la carte n'a rien à proposer, elle disparaît.
        VBox carte = robot.lookup("#zoneLibererEspace").queryAs(VBox.class);

        assertThat(carte.isVisible()).isFalse();
        assertThat(carte.isManaged()).isFalse();
    }

    @Test
    @DisplayName("#2028 : la carte « Libérer l'espace disque » réapparaît dès qu'il reste des archives à supprimer")
    void carte_liberer_espace_visible_avec_archives(FxRobot robot) {
        when(service.archivesDepot("/ws/session-42"))
                .thenReturn(List.of(new ArchiveDepot(Path.of("/ws/session-42/depot/Car-1.zip"), 1, 2048L, 2)));
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 2, 8192L, List.of(), null));

        VBox carte = robot.lookup("#zoneLibererEspace").queryAs(VBox.class);

        assertThat(carte.isVisible()).isTrue();
        assertThat(carte.isManaged()).isTrue();
    }

    @Test
    @DisplayName("#823 : barre de statut 3 zones — contexte à gauche, statut · récap au centre, bilan à droite")
    void barre_de_statut_trois_zones(FxRobot robot) {
        when(service.archivesDepot("/ws/session-42"))
                .thenReturn(List.of(
                        new ArchiveDepot(Path.of("/ws/session-42/depot/Car-1.zip"), 1, 2048L, 2),
                        new ArchiveDepot(Path.of("/ws/session-42/depot/Car-2.zip"), 2, 4096L, 3)));
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 5, 8192L, List.of(), null));

        var zones = controleur.zonesStatutProperty().get();
        assertThat(zones.gauche()).isEqualTo("Carré 640380 · A1 · N° 2");
        assertThat(zones.centre()).startsWith("Prêt à déposer").contains("5 séquences");
        assertThat(zones.droite()).contains("2 archive(s)").contains("dans depot/");
    }

    @Test
    @DisplayName("#983 : la table de dépôt se réhydrate depuis depot_unite et propose « Retenter les échecs »")
    void table_de_depot_rehydratee_et_reprise(FxRobot robot) {
        when(service.unitesDepot(ID_PASSAGE))
                .thenReturn(List.of(
                        new DepotUnite(
                                1L,
                                ID_PASSAGE,
                                "Car-1.zip",
                                TypeDepotUnite.ZIP,
                                StatutDepotUnite.DEPOSE,
                                "obj-1",
                                null,
                                "2026-07-11T14:00:00"),
                        new DepotUnite(
                                2L,
                                ID_PASSAGE,
                                "Car-2.zip",
                                TypeDepotUnite.ZIP,
                                StatutDepotUnite.ECHEC,
                                null,
                                "HTTP 503",
                                "2026-07-11T14:00:00")));
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.DEPOT_EN_COURS, "/ws/session-42", 2, 8192L, List.of(), null));

        // La table réapparaît avec l'état persisté (déposée + échec), sans dépôt en cours dans la session.
        TableView<?> table = robot.lookup("#tableDepot").queryAs(TableView.class);
        assertThat(table.isVisible()).isTrue();
        assertThat(table.getItems()).hasSize(2);
        // #1800 : cette table n'avait aucun menu contextuel ; elle offre désormais « Colonnes… ».
        assertThat(table.getContextMenu()).isNotNull();
        assertThat(table.getContextMenu().getItems().stream()
                        .map(i -> i.getText())
                        .toList())
                .contains("Colonnes…", "Copier");
        // #1798 : la ligne de dépôt ne porte pas de chemin ; son identifiant est la clé qu'on recoupe
        // côté plateforme, et c'est donc lui que « Copier ▸ » propose.
        javafx.scene.control.Menu copier = (javafx.scene.control.Menu) table.getContextMenu().getItems().stream()
                .filter(i -> "Copier".equals(i.getText()))
                .findFirst()
                .orElseThrow();
        robot.interact(() -> table.getSelectionModel().select(0));
        java.util.concurrent.atomic.AtomicReference<String> copie = new java.util.concurrent.atomic.AtomicReference<>();
        robot.interact(() -> {
            copier.getItems().get(0).fire();
            copie.set(javafx.scene.input.Clipboard.getSystemClipboard().getString());
        });
        assertThat(copie.get()).isEqualTo("Car-1.zip");
        // Il reste une unité non déposée : l'action bascule en reprise, active depuis « Dépôt en cours ».
        Button televerser = robot.lookup("#btnTeleverser").queryAs(Button.class);
        assertThat(televerser.getText()).contains("Reprendre le dépôt");
        assertThat(televerser.isDisabled())
                .as("la reprise doit être possible depuis « Dépôt en cours » (#980)")
                .isFalse();
    }

    @Test
    @DisplayName("#820 : la table rend les 4 états d'archive — attente, barre « en cours », terminée, échec")
    void table_rend_l_etat_de_chaque_archive(FxRobot robot) {
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 4, 8192L, List.of(), null));

        // Injecte un plan de 4 archives puis fait évoluer les états sur le fil JavaFX, comme le ferait le
        // relais de suivi pendant une compression parallèle : 1 en cours, 2 en attente, 3 terminée, 4 échec.
        robot.interact(() -> {
            var suivi = viewModel.suiviLignes();
            suivi.planifier(List.of(
                    new ArchivePlanifiee(1, 2, 1_000L),
                    new ArchivePlanifiee(2, 3, 2_000L),
                    new ArchivePlanifiee(3, 2, 1_500L),
                    new ArchivePlanifiee(4, 1, 800L)));
            suivi.demarrer(1);
            suivi.progresser(1, 1, 2);
            suivi.terminer(new ArchiveDepot(Path.of("/ws/session-42/depot/Car-3.zip"), 3, 1_400L, 2));
            suivi.echouer(4, "disque plein");
        });

        TableView<?> table = robot.lookup("#tableArchives").queryAs(TableView.class);
        assertThat(table.getItems()).hasSize(4);
        // Chaque état pose sa classe sur la ligne ; « en cours » affiche une barre de progression vive.
        assertThat(robot.lookup(".ligne-suivi.etat-attente").tryQuery()).isPresent();
        assertThat(robot.lookup(".ligne-suivi.etat-cours").tryQuery()).isPresent();
        assertThat(robot.lookup(".ligne-suivi.etat-terminee").tryQuery()).isPresent();
        assertThat(robot.lookup(".ligne-suivi.etat-echec").tryQuery()).isPresent();
        assertThat(robot.lookup(".progress-bar").tryQuery()).isPresent();
    }

    @Test
    @DisplayName("#… : en Déposé avec archives, « Supprimer les archives » déclenche la suppression (confirmée)")
    void supprimer_archives_declenche_la_suppression(FxRobot robot) {
        when(service.archivesDepot("/ws/session-42"))
                .thenReturn(List.of(new ArchiveDepot(Path.of("/ws/session-42/depot/Car-1.zip"), 1, 2048L, 2)));
        // Confirmateur injecté (pas de dialogue natif bloquant sous TestFX).
        robot.interact(() -> controleur.confirmateur().definir(message -> true));
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.DEPOSE, "/ws/session-42", 2, 8192L, List.of(), "2026-06-18"));
        Button supprimer = robot.lookup("#btnSupprimerArchives").queryAs(Button.class);
        assertThat(supprimer.isDisabled()).isFalse();

        robot.interact(supprimer::fire);

        verify(service).supprimerArchivesDepot(ID_PASSAGE);
    }

    @Test
    @DisplayName("#… : espace disque insuffisant → « Générer les archives » désactivé + alerte visible sous le bouton")
    void espace_insuffisant_desactive_generer_et_affiche_alerte(FxRobot robot) {
        when(service.espaceDisqueDisponible("/ws/session-42")).thenReturn(5_000_000_000L); // 5 Go dispo
        when(service.estimationTailleDepotOctets(anyLong())).thenReturn(9_000_000_000L); // 9 Go estimés
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 2, 8192L, List.of(), null));

        assertThat(robot.lookup("#btnGenererArchives").queryButton().isDisabled())
                .isTrue();
        Label alerte = robot.lookup("#lblEspaceInsuffisant").queryAs(Label.class);
        assertThat(alerte.isVisible()).isTrue();
        assertThat(alerte.getText()).contains("insuffisant");
    }

    @Test
    @DisplayName("#251 : stepper ordonné à 4 étapes, l'étape courante suit le statut du dépôt")
    void stepper_ordonne_quatre_etapes_etape_courante_selon_statut(FxRobot robot) {
        HBox stepper = robot.lookup("#stepper").queryAs(HBox.class);

        // Vérifié : les 4 étapes sont présentes, dans l'ordre, et l'étape courante est ① Préparer.
        assertThat(stepper.getChildren()).hasSize(4);
        assertThat(stepper.getChildren())
                .extracting(noeud -> ((Label) noeud).getText())
                .containsExactly("1 · Préparer", "2 · Générer les archives", "3 · Téléverser", "4 · Marquer déposé");
        assertThat(etapeCourante(stepper)).isEqualTo("1 · Préparer");

        // Prêt à déposer sans archives générées : l'étape courante avance à ② Générer les archives.
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 2, 8192L, List.of(), null));
        assertThat(etapeCourante(stepper)).isEqualTo("2 · Générer les archives");

        // Déposé : tout est franchi, aucune étape n'est « courante ».
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.DEPOSE, "/ws/session-42", 2, 8192L, List.of(), "2026-06-18"));
        assertThat(stepper.getChildren())
                .allMatch(noeud -> noeud.getStyleClass().contains("etape-franchie"));
    }

    @Test
    @DisplayName("#251 : plus de libellé trompeur « Dossier à téléverser » ni de nœud #lblCheminDossier")
    void plus_de_libelle_dossier_trompeur(FxRobot robot) {
        // Le nœud du dossier de session entier a été remplacé par #lblCheminDepot (sous-dossier depot/).
        assertThat(robot.lookup("#lblCheminDossier").tryQuery()).isEmpty();
        assertThat(robot.lookup("#lblCheminDepot").queryAs(Label.class)).isNotNull();
    }

    @Test
    @DisplayName("#259 : « Ouvrir le dossier » est désactivé tant que les archives ne sont pas générées")
    void ouvrir_dossier_desactive_tant_que_pas_genere(FxRobot robot) {
        Button ouvrir = robot.lookup("#btnOuvrirDepot").queryAs(Button.class);
        // État initial Vérifié : aucune archive produite → ouvrir un dossier partiel/inexistant est interdit.
        assertThat(ouvrir.isDisabled()).isTrue();

        // Même au statut Prêt à déposer, tant qu'on n'a pas généré, le bouton reste désactivé.
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 2, 8192L, List.of(), null));
        assertThat(ouvrir.isDisabled()).isTrue();
    }

    @Test
    @DisplayName("#259 : après une génération réussie, « Ouvrir le dossier » s'active et ouvre depot/")
    void ouvrir_dossier_depot_apres_generation(FxRobot robot) {
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 2, 8192L, List.of(), null));
        when(service.genererArchivesDepot(anyLong(), any(), any()))
                .thenReturn(List.of(
                        new ArchiveDepot(Path.of("/ws/session-42/depot/Car040962-2026-Pass1-A1-1.zip"), 1, 2048L, 2)));
        robot.interact(() -> viewModel.genererArchives());

        Button ouvrir = robot.lookup("#btnOuvrirDepot").queryAs(Button.class);
        assertThat(ouvrir.isDisabled()).isFalse();
        robot.clickOn("#btnOuvrirDepot");

        // L'ouvreur reçoit une URI fichier pointant le sous-dossier depot/ (cible du téléversement).
        assertThat(liensOuverts).hasSize(1);
        assertThat(liensOuverts.get(0)).startsWith("file:").contains("/ws/session-42/depot");
    }

    @Test
    @DisplayName("#259 : pendant la génération, « Ouvrir le dossier » et « Marquer déposé » sont désactivés")
    void ouvrir_et_deposer_desactives_pendant_generation(FxRobot robot) {
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 2, 8192L, List.of(), null));
        Button ouvrir = robot.lookup("#btnOuvrirDepot").queryAs(Button.class);
        Button deposer = robot.lookup("#btnDeposer").queryAs(Button.class);

        // Génération en cours : on ne doit ni ouvrir un dossier en cours d'écriture, ni marquer déposé.
        robot.interact(() -> viewModel.marquerGenerationEnCours());
        assertThat(ouvrir.isDisabled()).isTrue();
        assertThat(deposer.isDisabled()).isTrue();

        // Fin de génération (ici un échec) : « Marquer déposé » redevient possible (statut Prêt à déposer).
        robot.interact(() -> viewModel.echecGeneration("Interrompu."));
        assertThat(deposer.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("#769 : la barre de progression est présente mais masquée au repos")
    void barre_generation_presente_et_masquee_au_repos(FxRobot robot) {
        var barre = robot.lookup("#barreGeneration").queryAs(javafx.scene.control.ProgressBar.class);
        assertThat(barre).isNotNull();
        // Aucune génération en cours → la barre n'est ni visible ni gérée par le layout.
        assertThat(barre.isVisible()).isFalse();
        assertThat(barre.isManaged()).isFalse();
    }

    @Test
    @DisplayName("#769 : pendant la génération, la barre et le libellé de progression apparaissent")
    void barre_generation_visible_pendant_generation(FxRobot robot) {
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 2, 8192L, List.of(), null));
        var barre = robot.lookup("#barreGeneration").queryAs(javafx.scene.control.ProgressBar.class);
        var libelle = robot.lookup("#lblProgressionGeneration").queryAs(Label.class);

        robot.interact(() -> viewModel.marquerGenerationEnCours());

        assertThat(barre.isVisible()).isTrue();
        assertThat(libelle.isVisible()).isTrue();
        assertThat(libelle.getText()).contains("Préparation");
    }

    @Test
    @DisplayName("#689 : le primaire suit l'étape actionnable (Préparer → Générer → Marquer déposé)")
    void emphase_primaire_suit_l_etape_actionnable(FxRobot robot) {
        Button preparer = robot.lookup("#btnPreparer").queryAs(Button.class);
        Button generer = robot.lookup("#btnGenererArchives").queryAs(Button.class);
        Button deposer = robot.lookup("#btnDeposer").queryAs(Button.class);

        // Étape ① Vérifié (conforme) : « Préparer » est l'action mise en avant.
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.VERIFIE, "/ws/session-42", 2, 8192L, List.of(), null));
        assertThat(preparer.getStyleClass()).contains("bouton-primaire").doesNotContain("bouton-secondaire");
        assertThat(generer.getStyleClass()).contains("bouton-secondaire").doesNotContain("bouton-primaire");
        assertThat(deposer.getStyleClass()).contains("bouton-secondaire").doesNotContain("bouton-primaire");

        // Étape ② Prêt à déposer, avant génération : « Générer les archives » prend le relais.
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 2, 8192L, List.of(), null));
        assertThat(generer.getStyleClass()).contains("bouton-primaire").doesNotContain("bouton-secondaire");
        assertThat(preparer.getStyleClass()).contains("bouton-secondaire").doesNotContain("bouton-primaire");
        assertThat(deposer.getStyleClass()).contains("bouton-secondaire").doesNotContain("bouton-primaire");

        // Étape ④ après génération (l'étape ③ « Téléverser » est manuelle) : « Marquer déposé » devient primaire.
        when(service.genererArchivesDepot(anyLong(), any(), any()))
                .thenReturn(List.of(
                        new ArchiveDepot(Path.of("/ws/session-42/depot/Car040962-2026-Pass1-A1-1.zip"), 1, 2048L, 2)));
        robot.interact(() -> viewModel.genererArchives());
        assertThat(deposer.getStyleClass()).contains("bouton-primaire").doesNotContain("bouton-secondaire");
        assertThat(preparer.getStyleClass()).contains("bouton-secondaire").doesNotContain("bouton-primaire");
        assertThat(generer.getStyleClass()).contains("bouton-secondaire").doesNotContain("bouton-primaire");
    }

    @Test
    @DisplayName("#689 : une fois le passage déposé, aucune action n'est mise en avant")
    void emphase_aucun_primaire_une_fois_depose(FxRobot robot) {
        reouvrirAvec(robot, new EtatLot(StatutWorkflow.DEPOSE, "/ws/session-42", 2, 8192L, List.of(), "2026-06-18"));
        Button preparer = robot.lookup("#btnPreparer").queryAs(Button.class);
        Button generer = robot.lookup("#btnGenererArchives").queryAs(Button.class);
        Button deposer = robot.lookup("#btnDeposer").queryAs(Button.class);
        assertThat(preparer.getStyleClass()).contains("bouton-secondaire").doesNotContain("bouton-primaire");
        assertThat(generer.getStyleClass()).contains("bouton-secondaire").doesNotContain("bouton-primaire");
        assertThat(deposer.getStyleClass()).contains("bouton-secondaire").doesNotContain("bouton-primaire");
    }

    /// Libellé de l'étape stylée « courante » du stepper, ou `null` si aucune (tout franchi).
    private static String etapeCourante(HBox stepper) {
        return stepper.getChildren().stream()
                .filter(noeud -> noeud.getStyleClass().contains("etape-courante"))
                .map(noeud -> ((Label) noeud).getText())
                .findFirst()
                .orElse(null);
    }

    /// Textes des lignes de la checklist de cohérence (#254) rendues par le controller.
    private static List<String> textesChecklist(VBox checklist) {
        return checklist.getChildren().stream()
                .map(noeud -> ((Label) noeud).getText())
                .toList();
    }

    /// Les glyphes de la checklist. Depuis #2099 le statut n'est plus écrit en tête du texte mais posé
    /// en icône : le fait est le même, son porteur a changé.
    private static List<String> glyphesChecklist(VBox checklist) {
        return checklist.getChildren().stream()
                .map(noeud -> ((FontIcon) ((Label) noeud).getGraphic()).getIconLiteral())
                .toList();
    }

    private static ControleCoherence ok(String libelle) {
        return new ControleCoherence(libelle, StatutControle.OK, "Satisfait.");
    }

    private static ControleCoherence echec(String libelle, String detail) {
        return new ControleCoherence(libelle, StatutControle.ECHEC, detail);
    }
}
