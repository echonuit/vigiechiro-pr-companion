package fr.univ_amu.iut.lot.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.view.NavigationDeTestModule;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ModeDepot;
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
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Le **fil d'étapes** de M-Lot et ce qu'il doit à la dernière carte (#5859) : son nom, son état, et la
/// question que « Réinitialiser le dépôt » pose.
///
/// Même montage que [LotDepotConnecteViewTest], dont ces cas sont sortis pour ne pas lui faire franchir
/// le plafond de taille d'une classe de test (ADR 4617) : un [DepotVigieChiro] simulé, une participation
/// liée, et un passage en « Dépôt en cours » dont le plan est déjà rempli.
@ExtendWith(ApplicationExtension.class)
class LotFilDEtapesViewTest {

    private static final long ID_PASSAGE = 42L;
    private static final String COMPTE_RENDU_IMPORT =
            "Observations importées depuis Vigie-Chiro : 1284 observation(s).";
    private static final ContextePassage CONTEXTE =
            new ContextePassage(ID_PASSAGE, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière"));

    private ServiceLot service;
    private DepotVigieChiro depot;
    private SuiviTraitement suivi;
    private ImportObservations importation;
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
        importation = mock(ImportObservations.class);
        lenient().when(importation.importer(anyLong(), eq(false))).thenReturn(COMPTE_RENDU_IMPORT);

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
                        return new TraitementViewModel(Optional.of(suivi), Optional.of(importation), Horloge.systeme());
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

    /// Le lot 14 (#5676) a donné au titre et au bouton de la dernière étape le nom du geste. Le fil
    /// d'étapes ne l'a pas suivi : il disait « Marquer déposé » au-dessus de « Lancer la participation ».
    /// La puce est confrontée au BOUTON, pas à un texte recopié ici : c'est leur écart qui est le défaut.
    @Test
    @DisplayName("#5859 : la dernière puce du fil d'étapes porte le nom du bouton de la dernière étape")
    void la_derniere_puce_porte_le_nom_du_bouton(FxRobot robot) {
        assertThat(texte(robot, "#lblTitreDeposer")).endsWith("Lancer la participation");
        assertThat(dernierePuce(robot)).isEqualTo(pucesDuFil(robot) + " · " + texteDuBoutonDeposer(robot));

        when(depot.participationLiee(ID_PASSAGE)).thenReturn(false);
        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));

        assertThat(texteDuBoutonDeposer(robot)).isEqualTo("Marquer déposé");
        assertThat(dernierePuce(robot)).isEqualTo(pucesDuFil(robot) + " · " + texteDuBoutonDeposer(robot));
    }

    /// L'infobulle du bouton distingue les deux formes depuis #5824. La question qu'il pose ensuite
    /// disait encore « les archives ZIP sur disque » pour un dépôt en séquences.
    @Test
    @DisplayName("#5859 : la confirmation de « Réinitialiser le dépôt » ne nomme d'archives que s'il y en a")
    void la_confirmation_de_reinitialisation_suit_la_forme(FxRobot robot) {
        java.util.concurrent.atomic.AtomicReference<String> question =
                new java.util.concurrent.atomic.AtomicReference<>();
        controleur.confirmateur().definir(message -> {
            question.set(message);
            return false;
        });

        when(service.formeDuDepot(ID_PASSAGE)).thenReturn(ModeDepot.SEQUENCES_WAV);
        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));
        GesteVisible.cliquer(robot, "#btnReinitialiserDepot");
        assertThat(question.get())
                .contains("La participation Vigie-Chiro est conservée")
                .doesNotContain("archives");

        when(service.formeDuDepot(ID_PASSAGE)).thenReturn(ModeDepot.ARCHIVES_ZIP);
        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));
        GesteVisible.cliquer(robot, "#btnReinitialiserDepot");
        assertThat(question.get()).contains("archives ZIP sur disque");

        verify(service, never()).reinitialiserDepot(ID_PASSAGE);
    }

    /// Le fil ne doit pas dire le geste fait tant que le bouton l'offre (constat S4-C03), ni à faire
    /// quand l'analyse est demandée.
    @Test
    @DisplayName(
            "#5859 : la dernière puce est courante tant que le bouton est offert, franchie une fois l'analyse demandée")
    void la_derniere_puce_suit_l_etat_du_bouton(FxRobot robot) {
        // Le banc ouvre sur un dépôt partiel, où le téléversement est encore l'étape courante. Ici la nuit
        // est tout entière sur la plateforme : c'est l'état où le fil disait tout franchi.
        when(service.consulterLot(anyLong()))
                .thenReturn(new EtatLot(StatutWorkflow.DEPOSE, "/ws/session-42", 2, 8192L, List.of(), null));
        when(depot.lancerTraitement(ID_PASSAGE)).thenReturn(ResultatLancement.accepte());
        when(suivi.relever(ID_PASSAGE))
                .thenReturn(
                        new Traitement(EtatTraitement.PLANIFIE, "2026-07-13T09:00:00+00:00", null, null, null, null));
        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));
        assertThat(boutonDeposerOffert(robot)).isTrue();
        assertThat(classesDeLaDernierePuce(robot)).contains("etape-courante").doesNotContain("etape-franchie");

        GesteVisible.cliquer(robot, "#btnDeposer");

        Attente.queSurLeFil(
                () -> !boutonDeposerOffert(robot), "le bouton n'est plus offert une fois l'analyse planifiée", 5_000L);
        assertThat(classesDeLaDernierePuce(robot)).contains("etape-franchie").doesNotContain("etape-courante");
    }

    private static boolean boutonDeposerOffert(FxRobot robot) {
        return Attente.surLeFil(
                () -> !robot.lookup("#btnDeposer").queryAs(Button.class).isDisabled(),
                "lire si le bouton de la dernière étape est offert",
                5_000L);
    }

    private static List<String> classesDeLaDernierePuce(FxRobot robot) {
        return Attente.surLeFil(
                () -> {
                    List<Node> puces =
                            robot.lookup("#stepper").queryAs(HBox.class).getChildren();
                    return List.copyOf(puces.get(puces.size() - 1).getStyleClass());
                },
                "lire l'état de la dernière puce du fil d'étapes",
                5_000L);
    }

    private static int pucesDuFil(FxRobot robot) {
        return Attente.surLeFil(
                () -> robot.lookup("#stepper").queryAs(HBox.class).getChildren().size(),
                "compter les puces du fil d'étapes",
                5_000L);
    }

    private static String dernierePuce(FxRobot robot) {
        return Attente.surLeFil(
                () -> {
                    List<Node> puces =
                            robot.lookup("#stepper").queryAs(HBox.class).getChildren();
                    return ((Label) puces.get(puces.size() - 1)).getText();
                },
                "lire la dernière puce du fil d'étapes",
                5_000L);
    }

    private static String texteDuBoutonDeposer(FxRobot robot) {
        return Attente.surLeFil(
                () -> robot.lookup("#btnDeposer").queryAs(Button.class).getText(), "lire le bouton de l'étape", 5_000L);
    }

    private static String texte(FxRobot robot, String selecteur) {
        return Attente.surLeFil(
                () -> robot.lookup(selecteur).queryAs(Label.class).getText(), "lire " + selecteur, 5_000L);
    }

    private static DepotUnite unite(String identifiant, StatutDepotUnite statut) {
        return new DepotUnite(
                1L, ID_PASSAGE, identifiant, TypeDepotUnite.ZIP, statut, null, null, "2026-07-11T15:00:00");
    }
}
