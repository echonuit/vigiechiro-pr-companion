package fr.univ_amu.iut.lot.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.view.NavigationDeTestModule;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import fr.univ_amu.iut.lot.viewmodel.TraitementViewModel;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test d'intégration TestFX de l'écran **M-Lot** : chargement du FXML via Guice (avec un
/// [ServiceLot] mocké), ouverture sur un passage Vérifié, vérification du câblage (statut, récap,
/// dossier, activation des actions) et délégation du clic « Préparer ». Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class LotViewTest {

    private ServiceLot service;
    private DepotVigieChiro depot;
    private LotController controleur;

    /// Le ViewModel de depot que l ecran a recu (#3546) : `@Provides` sans portee en fabrique un
    /// nouveau a chaque injection, et en redemander un ici n atteindrait pas celui du binding.
    private DepotViewModel depotObserve;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceLot.class);
        when(service.consulterLot(anyLong()))
                .thenReturn(new EtatLot(StatutWorkflow.VERIFIE, "/ws/session-42", 2, 8192L, List.of(), null));
        // Dépôt présent (mock) → le bouton « Téléverser sur Vigie-Chiro » est visible (#142).
        depot = mock(DepotVigieChiro.class);
        Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Provides
                    LotViewModel viewModel() {
                        return new LotViewModel(service);
                    }

                    @Provides
                    DepotViewModel depotViewModel() {
                        depotObserve = new DepotViewModel(service, Optional.of(depot));
                        return depotObserve;
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
                        return url -> {};
                    }
                },
                new NavigationDeTestModule());
        FXMLLoader loader = new FXMLLoader(LotController.class.getResource("Lot.fxml"));
        loader.setControllerFactory(DiagnosticGuice.pour(injector));
        Parent vue = loader.load();
        controleur = loader.getController();
        controleur.ouvrirSur(new ContextePassage(42L, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière")));
        FenetreAjustable.poser(stage, vue, 900, 640);
        FenetreAjustable.afficher(stage);
    }

    /// Les textes visibles d'une zone de restitution, lus dans le graphe de scène comme l'utilisateur
    /// les voit.
    private static java.util.List<String> textesDe(VBox zone) {
        return zone.lookupAll(".label").stream()
                .filter(Label.class::isInstance)
                .map(noeud -> ((Label) noeud).getText())
                .filter(texte -> texte != null && !texte.isBlank())
                .toList();
    }

    @Test
    @DisplayName("#3546 : un lancement pendant un dépôt s'annonce, même si « en cours » ne rebouge pas")
    void le_lancement_s_annonce_quand_en_cours_ne_change_pas(FxRobot robot) {
        // Un téléversement est en route : la zone droite annonce le dépôt.
        robot.interact(() -> depotObserve.marquerEnCours());
        assertThat(controleur.zonesStatutProperty().get().droite()).startsWith("Dépôt");

        // Puis un lancement démarre. `marquerLancementEnCours` pose `lancementEnCours` PUIS `enCours`,
        // mais `enCours` vaut déjà `true` : sa seconde écriture n'émet aucun événement. C'est le seul
        // chemin où la propriété manquante est observable **seule**, et il passe par l'API publique.
        //
        // Le test naïf a été essayé : sans le dépôt en cours ci-dessus, il est **vert alors que le défaut
        // est là**, parce que `enCours` change alors de valeur et invalide la liaison juste après.
        robot.interact(() -> depotObserve.marquerLancementEnCours());

        assertThat(controleur.zonesStatutProperty().get().droite())
                .as("le binding doit DÉCLARER `lancementEnCoursProperty`, qu'il lit en premier")
                .isEqualTo("Lancement de l'analyse sur Vigie-Chiro…");
    }

    @Test
    @DisplayName("Affiche statut/récap/dossier ; préparer actif, déposer désactivé (Vérifié)")
    void affiche_etat_verifie(FxRobot robot) {
        Label chemin = robot.lookup("#lblCheminDepot").queryAs(Label.class);
        Button preparer = robot.lookup("#btnPreparer").queryAs(Button.class);
        Button deposer = robot.lookup("#btnDeposer").queryAs(Button.class);

        // Le statut est déporté en barre de statut (#693) : plus de label d'en-tête, il vit dans les zones.
        assertThat(controleur.zonesStatutProperty().get().centre()).isEqualTo("Vérifié · 2 séquences · 8 Ko");
        assertThat(chemin.getText()).isEqualTo("/ws/session-42/depot");
        assertThat(preparer.isDisabled()).isFalse();
        assertThat(deposer.isDisabled()).isTrue();
    }

    @Test
    @DisplayName("« Préparer le dépôt » délègue au service")
    void preparer_delegue_au_service(FxRobot robot) {
        robot.clickOn("#btnPreparer");
        verify(service).preparerLot(42L);
    }

    @Test
    @DisplayName("#142 : le bouton « Téléverser sur Vigie-Chiro » est présent et visible (dépôt disponible)")
    void bouton_televerser_present(FxRobot robot) {
        Button televerser = robot.lookup("#btnTeleverser").queryAs(Button.class);

        assertThat(televerser.isVisible()).isTrue();
        assertThat(televerser.getText()).contains("Téléverser sur Vigie-Chiro");
    }

    @Test
    @DisplayName("#982/#983 : le clic « Téléverser sur Vigie-Chiro » délègue au moteur reprenable et restitue le bilan")
    void clic_televerser_delegue_au_moteur(FxRobot robot) {
        when(service.consulterLot(anyLong()))
                .thenReturn(new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 2, 8192L, List.of(), null));
        when(service.sequencesADeposer(42L)).thenReturn(List.of(java.nio.file.Path.of("/ws/a.wav")));
        when(depot.deposer(eq(42L), any(), any(), any())).thenReturn(new BilanDepot("part-1", 1, List.of()));
        robot.interact(() -> controleur.ouvrirSur(
                new ContextePassage(42L, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière"))));

        // Le dépôt passe par le socle (#1253), synchrone en test : le bilan est restitué au retour du clic.
        robot.interact(() -> robot.lookup("#btnTeleverser").queryButton().fire());

        // #1890 : les deux ViewModels de l'écran partagent un bandeau unique ; le bilan du dépôt y arrive
        // comme n'importe quel autre compte rendu.
        verify(depot).deposer(eq(42L), any(), any(), any());
        // #2653 : le bilan n'arrive plus dans le bandeau d'une ligne mais dans la bande chiffrée, qui
        // peut porter la proportion, le volume et l'action suivante - trois choses qu'une phrase ne peut
        // pas dire. Le bandeau reste partagé pour les ERREURS et les autres opérations de l'écran.
        VBox zone = robot.lookup("#zoneCompteRenduDepot").queryAs(VBox.class);
        assertThat(zone.isVisible()).isTrue();
        assertThat(textesDe(zone)).anyMatch(texte -> texte.contains("1 déposées"));
        assertThat(robot.lookup("#bandeauRetour").queryAs(HBox.class).isVisible())
                .as("un dépôt réussi ne laisse plus de bandeau : il redirait ce que la bande montre")
                .isFalse();
    }

    @Test
    @DisplayName("#1044 : « Annuler le dépôt » interrompt le téléversement en cours, message « interrompu »")
    void clic_annuler_interrompt_le_depot(FxRobot robot) {
        when(service.consulterLot(anyLong()))
                .thenReturn(new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 2, 8192L, List.of(), null));
        when(service.sequencesADeposer(42L)).thenReturn(List.of(java.nio.file.Path.of("/ws/a.wav")));
        // Contrat coopératif (#1252) : le moteur consulte le drapeau entre deux fichiers. Le socle étant
        // synchrone en test, le moteur mocké joue le clic « Annuler » à son premier point de contrôle
        // (le travail tourne sur le fil JavaFX : le clic y est légal), puis vérifie que le drapeau est
        // bien visible de son BooleanSupplier - c'est le câblage réel bouton → ViewModel → moteur qui
        // est exercé - et rend le bilan partiel de la tentative interrompue.
        when(depot.deposer(eq(42L), any(), any(), any())).thenAnswer(invocation -> {
            java.util.function.BooleanSupplier annule = invocation.getArgument(2);
            robot.lookup("#btnAnnulerDepot").queryButton().fire();
            assertThat(annule.getAsBoolean())
                    .as("le clic « Annuler » doit être visible du moteur au point de contrôle suivant")
                    .isTrue();
            return new BilanDepot("part-1", 0, List.of());
        });
        robot.interact(() -> controleur.ouvrirSur(
                new ContextePassage(42L, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière"))));

        robot.interact(() -> robot.lookup("#btnTeleverser").queryButton().fire());

        // #2653 : l'interruption se lit dans la bande. Ce qui compte reste le même - elle ne doit passer
        // ni pour un succes ni pour une erreur - mais c'est desormais la part « Restantes » de la barre
        // qui l'empeche, plutot qu'une couleur de bandeau.
        VBox zone = robot.lookup("#zoneCompteRenduDepot").queryAs(VBox.class);
        assertThat(textesDe(zone))
                .anyMatch(texte -> texte.contains("Dépôt interrompu"))
                .anyMatch(texte -> texte.contains("Reprendre le dépôt"));
        assertThat(robot.lookup("#btnAnnulerDepot").queryButton().isVisible())
                .as("le dépôt est fini : le bouton disparaît")
                .isFalse();
    }

    @Test
    @DisplayName("#1890 : la croix du bandeau efface le retour et le retire de la mise en page")
    void croix_efface_le_retour(FxRobot robot) {
        when(service.consulterLot(anyLong()))
                .thenReturn(new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 2, 8192L, List.of(), null));
        when(service.sequencesADeposer(42L)).thenReturn(List.of(java.nio.file.Path.of("/ws/a.wav")));
        // Un dépôt EN ÉCHEC : depuis #2653 le succès passe par la bande, et le bandeau ne porte plus que
        // les erreurs et les autres opérations de l'écran. Il en faut donc une pour éprouver sa croix.
        when(depot.deposer(eq(42L), any(), any(), any()))
                .thenThrow(new fr.univ_amu.iut.commun.model.RegleMetierException("Vigie-Chiro injoignable"));
        robot.interact(() -> controleur.ouvrirSur(
                new ContextePassage(42L, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière"))));
        robot.interact(() -> robot.lookup("#btnTeleverser").queryButton().fire());

        HBox bandeau = robot.lookup("#bandeauRetour").queryAs(HBox.class);
        assertThat(bandeau.isVisible()).isTrue();

        robot.clickOn("#btnFermerRetour");

        assertThat(bandeau.isVisible()).isFalse();
        assertThat(bandeau.isManaged())
                .as("le bandeau ne doit pas garder de place vide en tête de flux")
                .isFalse();
    }

    @Test
    @DisplayName("#1890 : le bandeau unique montre le compte rendu le plus récent des deux ViewModels")
    void le_bandeau_unique_montre_le_dernier_compte_rendu(FxRobot robot) {
        when(service.consulterLot(anyLong()))
                .thenReturn(new EtatLot(StatutWorkflow.PRET_A_DEPOSER, "/ws/session-42", 2, 8192L, List.of(), null));
        when(service.sequencesADeposer(42L)).thenReturn(List.of(java.nio.file.Path.of("/ws/a.wav")));
        when(depot.deposer(eq(42L), any(), any(), any()))
                .thenThrow(new fr.univ_amu.iut.commun.model.RegleMetierException("Vigie-Chiro injoignable"));
        when(service.supprimerArchivesDepot(42L)).thenReturn(4096L);
        // La suppression n'est offerte que s'il y a des archives en table (liaison vivante sur les lignes).
        when(service.archivesDepot("/ws/session-42"))
                .thenReturn(List.of(new ArchiveDepot(
                        java.nio.file.Path.of("/ws/session-42/depot/Car640380-2026-Pass2-A1-1.zip"), 1, 2048L, 2)));
        robot.interact(() -> controleur.ouvrirSur(
                new ContextePassage(42L, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière"))));

        // Compte rendu du DEPOT (DepotViewModel), puis compte rendu du LOT (LotViewModel). Depuis #2653
        // c'est l'ÉCHEC d'un dépôt qui emprunte le bandeau : son succès a sa propre bande. Le partage
        // qu'éprouve ce test - deux ViewModels, un bandeau, le dernier parle - est intact.
        robot.interact(() -> robot.lookup("#btnTeleverser").queryButton().fire());
        Label message = robot.lookup("#lblRetour").queryAs(Label.class);
        assertThat(message.getText()).contains("injoignable");

        // Sans stub, la confirmation ouvre un Alert.showAndWait() qui fige TestFX en headless.
        robot.interact(() -> controleur.confirmateur().definir(texte -> true));
        assertThat(robot.lookup("#btnSupprimerArchives").queryButton().isDisabled())
                .as("prérequis du test : des archives sont en table, donc la suppression est offerte")
                .isFalse();
        robot.interact(() -> robot.lookup("#btnSupprimerArchives").queryButton().fire());

        assertThat(message.getText())
                .as("deux ViewModels, un seul bandeau : c'est la dernière opération qui parle")
                .contains("libérés");
    }
}
