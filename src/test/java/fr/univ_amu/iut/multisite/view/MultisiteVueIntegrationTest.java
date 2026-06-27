package fr.univ_amu.iut.multisite.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.multisite.model.CarreAgrege;
import fr.univ_amu.iut.multisite.model.FiltresMultisite;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.PointAgrege;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.model.TriMultisite;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import java.util.List;
import java.util.Set;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX **ciblant le câblage réel des contrôles** de l'écran M-Multisite via
/// un **lookup des `fx:id`** (et non une simple lecture des propriétés du ViewModel). L'audit
/// 2026-06-18 signale que la vue agrégée n'expose souvent que 2 des 4 filtres (verdict et année non
/// surfacés), sans sélecteur de tri, et que la modale « Vues » est parfois inatteignable (bouton
/// mort) : autant de manques **invisibles** aux tests canoniques qui passent par le VM.
///
/// Ces tests forcent donc une vraie **interaction** sur chaque contrôle (`#choixVerdict`,
/// `#champCarre`, `#champAnnee`, `#choixTri`, `#boutonGererVues`) et vérifient que le service est
/// ré-interrogé avec le bon critère (R2/R3) ou que la navigation vers la modale est déclenchée
/// (E5.S3). Complète [MultisiteViewTest] sans le dupliquer (statut, export, réinitialiser et
/// double-clic y sont déjà couverts). Pas de base de données.
@ExtendWith(ApplicationExtension.class)
class MultisiteVueIntegrationTest {

    private ServiceMultisite service;
    private NavigationMultisite navigation;

    private static LignePassage ligne(long id, String carre, String point, int annee, int numero, String date) {
        return new LignePassage(id, carre, point, annee, numero, date, StatutWorkflow.DEPOSE, Verdict.OK);
    }

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceMultisite.class);
        navigation = mock(NavigationMultisite.class);
        OuvrirPassage ouvrirPassage = mock(OuvrirPassage.class);
        when(service.listerPassages(anyString(), any(), any()))
                .thenReturn(List.of(
                        ligne(42L, "640380", "A1", 2026, 10, "2026-06-21"),
                        ligne(7L, "640381", "B2", 2025, 3, "2025-07-02")));
        // Carte (#152) : un carré avec un point géolocalisé → un marqueur attendu sur la carte.
        when(service.agregerPourCarte(anyString()))
                .thenReturn(List.of(new CarreAgrege(
                        "640380",
                        "Étang",
                        List.of(new PointAgrege("A1", 43.30, -0.36, 2, StatutWorkflow.VERIFIE)),
                        2)));
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(OuvrirPassage.class).toInstance(ouvrirPassage);
                bind(NavigationMultisite.class).toInstance(navigation);
            }

            @Provides
            MultisiteViewModel viewModel() {
                return new MultisiteViewModel(service, "u-1");
            }
        });
        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource("Multisite.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        stage.setScene(new Scene(vue, 1100, 680));
        stage.show();
    }

    @Test
    @DisplayName("Les quatre filtres ET le sélecteur de tri existent et sont peuplés (R2/R3)")
    void les_quatre_filtres_et_le_tri_sont_cables(FxRobot robot) {
        TextField champCarre = robot.lookup("#champCarre").queryAs(TextField.class);
        ComboBox<?> choixStatut = robot.lookup("#choixStatut").queryAs(ComboBox.class);
        ComboBox<?> choixVerdict = robot.lookup("#choixVerdict").queryAs(ComboBox.class);
        TextField champAnnee = robot.lookup("#champAnnee").queryAs(TextField.class);
        ComboBox<?> choixTri = robot.lookup("#choixTri").queryAs(ComboBox.class);

        assertThat(champCarre).as("le filtre carré doit être présent").isNotNull();
        assertThat(champAnnee)
                .as("le filtre année doit être présent (souvent manquant)")
                .isNotNull();
        // Statut : 1re entrée « Tous » (null) + les 5 valeurs de StatutWorkflow.
        assertThat(choixStatut.getItems()).hasSize(StatutWorkflow.values().length + 1);
        // Verdict (souvent non surfacé) : entrée « Tous » (null) + les 4 valeurs de Verdict.
        assertThat(choixVerdict.getItems()).hasSize(Verdict.values().length + 1);
        // Tri (souvent absent) : les 4 critères de TriMultisite.
        assertThat(choixTri.getItems()).hasSize(TriMultisite.values().length);
    }

    @Test
    @DisplayName("#145 : trier par la colonne N° de passage (clic en-tête) réordonne, de façon NUMÉRIQUE")
    void tri_par_colonne_numero_est_numerique(FxRobot robot) {
        @SuppressWarnings("unchecked")
        TableView<LignePassage> table = (TableView<LignePassage>)
                (TableView<?>) robot.lookup("#tableLignes").queryTableView();
        TableColumn<LignePassage, ?> colNumero = table.getColumns().get(3); // colonne « N° passage »

        robot.interact(() -> {
            colNumero.setSortType(TableColumn.SortType.DESCENDING);
            table.getSortOrder().setAll(colNumero);
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Données n° 10 et 3 : un tri NUMÉRIQUE décroissant donne 10 puis 3 (un tri alphabétique sur les
        // chaînes « 10 »/« 3 » donnerait l'inverse). Prouve le comparateur numérique de la colonne.
        assertThat(table.getItems()).extracting(LignePassage::numeroPassage).containsExactly(10, 3);
    }

    @Test
    @DisplayName("#152 : la carte est présente et affiche un marqueur par point géolocalisé")
    void la_carte_affiche_les_points(FxRobot robot) {
        assertThat(robot.lookup("#zoneCarte").queryAll())
                .as("la zone carte est dans la vue")
                .isNotEmpty();
        Set<Node> marqueurs = robot.lookup(".carte-point-libelle").queryAll();
        assertThat(marqueurs)
                .as("un marqueur pour le point géolocalisé du carré 640380")
                .hasSize(1);
    }

    @Test
    @DisplayName("Choisir un filtre de verdict ré-interroge le service avec ce critère")
    void filtre_verdict_re_interroge_le_service(FxRobot robot) {
        ComboBox<?> choixVerdict = robot.lookup("#choixVerdict").queryAs(ComboBox.class);
        // Items : [Tous(null), A_VERIFIER, OK, DOUTEUX, A_JETER] → index 3 = DOUTEUX.
        robot.interact(() -> choixVerdict.getSelectionModel().select(3));

        ArgumentCaptor<FiltresMultisite> capteur = ArgumentCaptor.forClass(FiltresMultisite.class);
        verify(service, atLeastOnce()).listerPassages(eq("u-1"), capteur.capture(), any());
        assertThat(capteur.getAllValues()).anyMatch(filtre -> filtre.verdict() == Verdict.DOUTEUX);
    }

    @Test
    @DisplayName("Saisir un n° de carré et valider (Entrée) ré-interroge le service avec ce critère")
    void filtre_carre_valide_re_interroge_le_service(FxRobot robot) {
        robot.clickOn("#champCarre").write("640380").type(KeyCode.ENTER);

        ArgumentCaptor<FiltresMultisite> capteur = ArgumentCaptor.forClass(FiltresMultisite.class);
        verify(service, atLeastOnce()).listerPassages(eq("u-1"), capteur.capture(), any());
        assertThat(capteur.getAllValues()).anyMatch(filtre -> "640380".equals(filtre.numeroCarre()));
    }

    @Test
    @DisplayName("Saisir une année et valider (Entrée) ré-interroge le service avec ce critère")
    void filtre_annee_valide_re_interroge_le_service(FxRobot robot) {
        robot.clickOn("#champAnnee").write("2025").type(KeyCode.ENTER);

        ArgumentCaptor<FiltresMultisite> capteur = ArgumentCaptor.forClass(FiltresMultisite.class);
        verify(service, atLeastOnce()).listerPassages(eq("u-1"), capteur.capture(), any());
        assertThat(capteur.getAllValues())
                .anyMatch(filtre -> filtre.annee() != null && filtre.annee().intValue() == 2025);
    }

    @Test
    @DisplayName("Changer le sélecteur de tri ré-interroge le service avec ce tri")
    void changer_le_tri_re_interroge_le_service(FxRobot robot) {
        ComboBox<?> choixTri = robot.lookup("#choixTri").queryAs(ComboBox.class);
        // Items = TriMultisite.values() : [PAR_SITE, PAR_ANNEE, ...] → index 1 = PAR_ANNEE.
        robot.interact(() -> choixTri.getSelectionModel().select(1));

        verify(service, atLeastOnce()).listerPassages(eq("u-1"), any(), eq(TriMultisite.PAR_ANNEE));
    }

    @Test
    @DisplayName("Le bouton « Vues… » ouvre la modale branchée sur le même ViewModel (E5.S3)")
    void bouton_vues_ouvre_la_modale(FxRobot robot) {
        robot.clickOn("#boutonGererVues");

        // La feature délègue à NavigationMultisite (contrat de navigation) : le bouton n'est pas mort.
        verify(navigation).ouvrirModaleVues(any(Window.class), any(MultisiteViewModel.class));
    }
}
