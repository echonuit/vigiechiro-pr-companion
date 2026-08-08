package fr.univ_amu.iut.audit.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.audit.model.CategorieConstat;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.audit.model.NettoyageDossiersOrphelins;
import fr.univ_amu.iut.audit.model.RapportAudit;
import fr.univ_amu.iut.audit.model.ServiceAuditCoherence;
import fr.univ_amu.iut.audit.viewmodel.AuditViewModel;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Écran « Audit de cohérence » : chargé via Guice (service mocké), il liste les constats de l'audit
/// global et affiche le résumé chiffré. Même patron que `DiagnosticVueIntegrationTest`.
@ExtendWith(ApplicationExtension.class)
class AuditVueIntegrationTest {

    private ServiceAuditCoherence service;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceAuditCoherence.class);
        when(service.auditerTout())
                .thenReturn(new RapportAudit(List.of(
                        new ConstatAudit(
                                Severite.ERREUR,
                                CategorieConstat.DEPOT_DIVERGENT,
                                12L,
                                "Car040962-2026-Pass2-A1-1.zip",
                                "Renommage après dépôt, divergence base / serveur."),
                        new ConstatAudit(
                                Severite.AVERTISSEMENT,
                                CategorieConstat.DISQUE_ORPHELIN,
                                null,
                                "/ws/x.wav",
                                "Fichier orphelin."))));
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            AuditViewModel viewModel() {
                return new AuditViewModel(service, new NettoyageDossiersOrphelins());
            }

            /// Navigation constat → passage (#1347) : cet écran-ci ne la teste pas (c'est l'objet de
            /// `AuditNavigationViewTest`), mais le controller l'exige désormais.
            @Provides
            OuvrirPassage ouvrirPassage() {
                return (idPassage, contexte) -> {};
            }

            /// Dépôt de vues vide (#3100) : la barre de filtres n'affiche donc aucun onglet enregistré.
            @Provides
            DepotVues depotVues() {
                DepotVues depot = mock(DepotVues.class);
                when(depot.findByFeature("audit")).thenReturn(List.of());
                return depot;
            }
        });
        FXMLLoader loader = new FXMLLoader(AuditController.class.getResource("Audit.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        stage.setScene(new Scene(vue, 1000, 640));
        stage.show();
    }

    @Test
    @DisplayName("L'écran liste les constats de l'audit global et affiche le résumé chiffré")
    void ecran_liste_constats_et_resume(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableConstats").queryAs(TableView.class);
        assertThat(table.getItems()).hasSize(2);

        Label resume = robot.lookup("#lblResume").queryAs(Label.class);
        assertThat(resume.getText()).contains("2 écart").contains("1 erreur").contains("1 avertissement");
    }

    @Test
    @DisplayName("#1254 : « Vérifier en ligne » ajoute les constats serveur via le socle, bouton réactivé")
    void verifier_en_ligne_ajoute_les_constats_serveur(FxRobot robot) {
        when(service.auditerEnLigne())
                .thenReturn(new RapportAudit(List.of(new ConstatAudit(
                        Severite.ERREUR,
                        CategorieConstat.DEPOT_DIVERGENT,
                        14L,
                        "Car040962-2026-Pass2-B2-1.zip",
                        "Archive absente côté serveur."))));

        robot.clickOn("#boutonVerifierEnLigne");

        TableView<?> table = robot.lookup("#tableConstats").queryAs(TableView.class);
        assertThat(table.getItems())
                .as("audit hors ligne (2 constats) + vérification en ligne (1 constat)")
                .hasSize(3);
        assertThat(robot.lookup("#boutonVerifierEnLigne").queryAs(Button.class).isDisabled())
                .as("le binding sur enCoursProperty() relâche le bouton une fois la vérification finie")
                .isFalse();
    }

    @Test
    @DisplayName("#1254 : un échec de la vérification en ligne est restitué dans le résumé (filet #795)")
    void verifier_en_ligne_restitue_l_echec(FxRobot robot) {
        when(service.auditerEnLigne()).thenThrow(new RuntimeException("Vigie-Chiro injoignable"));

        robot.clickOn("#boutonVerifierEnLigne");

        Label resume = robot.lookup("#lblResume").queryAs(Label.class);
        assertThat(resume.getText())
                .contains("Vérification en ligne impossible")
                .contains("Vigie-Chiro injoignable");
        assertThat(robot.lookup("#tableConstats").queryAs(TableView.class).getItems())
                .as("les constats de l'audit hors ligne restent affichés")
                .hasSize(2);
        assertThat(robot.lookup("#boutonVerifierEnLigne").queryAs(Button.class).isDisabled())
                .isFalse();
    }

    @Test
    @DisplayName("#3100 : la recherche restreint la table, « Tout effacer » la rétablit")
    void la_recherche_restreint_la_table(FxRobot robot) {
        // Preuve de bout en bout du câblage : le champ du FXML, le GestionnaireFiltres, les Filtres du
        // view-model et la FilteredList de la table. Un test qui n'irait que jusqu'au view-model
        // passerait même si le champ n'était relié à rien.
        TableView<?> table = robot.lookup("#tableConstats").queryAs(TableView.class);

        robot.clickOn("#champRecherche").write("orphelin");

        assertThat(table.getItems())
                .as("« Fichier orphelin. » est dans le détail du second constat, pas du premier")
                .hasSize(1);

        robot.interact(() -> trierSurLaPremiereColonne(table));

        // Sans cette assertion, la suivante passerait pour une mauvaise raison. Une `FilteredList` posée
        // nue est **non modifiable** : `TableView` renonce alors à trier et vide le `sortOrder` de
        // lui-même. « Le tri est vide après le clic » ne prouve donc rien tant qu'on n'a pas montré
        // qu'il ne l'était pas avant. C'est ce montage-là qui a été trouvé cassé (#3100).
        assertThat(table.getSortOrder())
                .as("le tri doit d'abord prendre : la table est enveloppée dans une SortedList")
                .isNotEmpty();

        robot.clickOn("#boutonToutEffacer");

        assertThat(table.getItems())
                .as("« Tout effacer » (#3097) vide aussi la recherche, pas seulement les puces")
                .hasSize(2);
        assertThat(table.getSortOrder())
                .as("sur les écrans à table, le geste efface aussi le tri : le nôtre ne peut pas en différer")
                .isEmpty();
    }

    /// Nomme le paramètre de type de la table, que `queryAs` rend joker : sans lui, poser une colonne
    /// dans `getSortOrder()` ne compile pas, et le faire compiler demanderait un transtypage non vérifié.
    private static <T> void trierSurLaPremiereColonne(TableView<T> table) {
        table.getSortOrder().setAll(table.getColumns().get(0));
    }
}
