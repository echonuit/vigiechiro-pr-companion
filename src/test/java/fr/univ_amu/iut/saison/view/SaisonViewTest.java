package fr.univ_amu.iut.saison.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.view.DoubleClicDeterministe;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.commun.viewmodel.RevisionDonnees;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.saison.model.CasePassage;
import fr.univ_amu.iut.saison.model.LigneSaison;
import fr.univ_amu.iut.saison.model.ServiceSoldeSaison;
import fr.univ_amu.iut.saison.model.SoldeSaison;
import fr.univ_amu.iut.saison.viewmodel.SaisonViewModel;
import java.time.LocalDate;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Test TestFX de l'écran **M-Saison** : le FXML est chargé avec un injecteur de test (ViewModel sur un
/// [ServiceSoldeSaison] simulé, contrats d'ouverture mockés), monté headless. On vérifie l'affichage
/// d'une ligne par point et le routage du double-clic (passage présent vs carré du point).
@ExtendWith(ApplicationExtension.class)
class SaisonViewTest {

    private OuvrirPassage ouvrirPassage;
    private OuvrirSite ouvrirSite;

    /// Le signal de mutation (#3591) : le test l'actionne comme le ferait un import ou une synchro.
    private RevisionDonnees revision;

    private ServiceSoldeSaison serviceObserve;
    /// Le chrome de navigation, bâti à la main : c'est LUI qui abonne l'écran à la révision et
    /// qui rend l'abonnement quand l'étape sort de l'historique.
    private Navigateur navigateur;

    private SaisonController controleur;

    @Start
    void demarrer(Stage stage) throws Exception {
        revision = new RevisionDonnees(Runnable::run);
        ouvrirPassage = mock(OuvrirPassage.class);
        ouvrirSite = mock(OuvrirSite.class);
        ServiceSoldeSaison service = mock(ServiceSoldeSaison.class);
        serviceObserve = service;
        SoldeSaison solde = new SoldeSaison(
                2026,
                LocalDate.of(2026, 7, 20),
                List.of(
                        new LigneSaison(
                                "640001",
                                "A1",
                                1L,
                                new CasePassage(
                                        42L, StatutWorkflow.DEPOSE, Verdict.OK, LocalDate.of(2026, 6, 20), false, null),
                                CasePassage.absente(),
                                List.of(),
                                "Poser l'enregistreur avant le 30/09",
                                // #3289 : un carré NOMMÉ, pour que la colonne ait deux étiquettes à
                                // montrer. Les trois autres restent anonymes : c'est le contraste qui
                                // dit que la qualification n'invente pas de séparateur.
                                "Vallon des Sources",
                                "Ahetze"),
                        new LigneSaison(
                                "640002",
                                "B1",
                                2L,
                                CasePassage.absente(),
                                CasePassage.absente(),
                                List.of(),
                                "Poser l'enregistreur avant le 31/07",
                                null,
                                null),
                        // #2525 : la nuit opportuniste ne prend PAS la place du passage 1 protocolaire,
                        // qui reste manquant : elle vit dans la colonne « Hors protocole ».
                        new LigneSaison(
                                "640003",
                                "C1",
                                3L,
                                CasePassage.absente(),
                                CasePassage.absente(),
                                List.of(new CasePassage(
                                        99L, StatutWorkflow.DEPOSE, Verdict.OK, LocalDate.of(2026, 6, 25), true, null)),
                                "Poser l'enregistreur avant le 31/07",
                                null,
                                null),
                        // Un point À JOUR (#3103) : « reste à faire » vide. Sans lui, le filtre « Reste
                        // à faire » garderait les quatre lignes et ne discriminerait rien - la fixture
                        // ne portait que des points en retard.
                        new LigneSaison(
                                "640004",
                                "D1",
                                4L,
                                new CasePassage(
                                        77L, StatutWorkflow.DEPOSE, Verdict.OK, LocalDate.of(2026, 6, 10), false, null),
                                new CasePassage(
                                        78L, StatutWorkflow.DEPOSE, Verdict.OK, LocalDate.of(2026, 8, 12), false, null),
                                List.of(),
                                "",
                                null,
                                null)));
        when(service.soldeCourant(anyString(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(solde);
        when(service.soldePour(anyString(), anyInt())).thenReturn(solde);
        when(service.soldePour(anyString(), anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(solde);

        Injector injecteur = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(OuvrirPassage.class).toInstance(ouvrirPassage);
                bind(OuvrirSite.class).toInstance(ouvrirSite);
            }

            @Provides
            SaisonViewModel viewModel() {
                return new SaisonViewModel(service, "u-test");
            }

            @Provides
            RevisionDonnees revision() {
                return revision;
            }
        });

        FXMLLoader loader = new FXMLLoader(SaisonController.class.getResource("Saison.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        navigateur = new Navigateur(new NavigationViewModel(), revision);
        navigateur.empiler(vue, "saison", "Ma saison", controleur);
        FenetreAjustable.poser(stage, vue, 1000, 600);
        FenetreAjustable.afficher(stage);
    }

    @Test
    @DisplayName("#3591 : une mutation structurelle rafraîchit le solde SANS qu'on ait navigué")
    void une_mutation_rafraichit_sans_navigation(FxRobot robot) {
        // L'ouverture lit par `soldeCourant` ; on repart donc de zéro pour ne compter que ce que la
        // mutation provoque.
        clearInvocations(serviceObserve);

        // Une nuit arrive d'ailleurs : import, synchro, restauration. L'écran ne bouge pas.
        robot.interact(() -> revision.mutationStructurelleValidee());

        // Exactement une relecture. Sans le signal, l'écran attendait qu'on le quitte et qu'on y
        // revienne (`rafraichirAuRetour`), et la saison affichée restait celle d'avant.
        verify(serviceObserve, times(1)).soldePour(anyString(), anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("#3591 : un écran quitté ne recharge plus, l'abonnement est rendu")
    void un_ecran_quitte_ne_recharge_plus(FxRobot robot) {
        // Le depart REEL d'un ecran : le Navigateur le retire de l'historique. C'est lui qui rend
        // l'abonnement (contrat SuitLaRevision), l'ecran n'a plus rien a faire pour cela.
        robot.interact(() -> navigateur.ouvrirRacine(new Group(), "ailleurs", "Ailleurs", null));
        clearInvocations(serviceObserve);

        robot.interact(() -> revision.mutationStructurelleValidee());

        // `RevisionDonnees` est un SINGLETON, le ViewModel de cet écran ne l'est délibérément pas. Sans
        // ce retrait, chaque réouverture laisserait un listener accroché à une vue morte, et la dixième
        // mutation déclencherait dix rechargements dont neuf pour personne.
        verify(serviceObserve, never()).soldePour(anyString(), anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("une ligne par point suivi")
    void une_ligne_par_point(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSaison").queryAs(TableView.class);
        assertThat(table.getItems()).hasSize(4);
    }

    @Test
    @DisplayName("#3289 : la colonne « Carré » montre les deux étiquettes, celle par laquelle on cherche")
    void colonne_carre_montre_les_deux_etiquettes(FxRobot robot) {
        // Le défaut : la recherche de cet écran retient une ligne sur le nom du carré (#3219), et
        // l'écran n'ayant pas de puce « Lieu », ce nom n'apparaissait NULLE PART. On trouvait sans voir
        // pourquoi.
        TableView<?> table = robot.lookup("#tableSaison").queryAs(TableView.class);
        TableColumn<?, ?> carre = table.getColumns().getFirst();
        TableColumn<?, ?> nom = table.getColumns().get(1);

        assertThat(carre.getText()).isEqualTo("Carré");
        assertThat(nom.getText()).isEqualTo("Nom du carré");
        assertThat(carre.getCellData(0)).isEqualTo("640001");
        assertThat(nom.getCellData(0))
                .as("le nom a sa propre colonne : qualifié dans « Carré », il se faisait tronquer en "
                        + "« 640001 · … » - vérifié sur la capture régénérée")
                .isEqualTo("Vallon des Sources");
        assertThat(nom.getCellData(1))
                .as("un carré sans nom laisse la cellule VIDE, il n'invente pas d'étiquette")
                .isEqualTo("");
    }

    @Test
    @DisplayName("#3313 : la colonne « Commune » montre le lieu par lequel on peut aussi chercher")
    void colonne_commune(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSaison").queryAs(TableView.class);
        TableColumn<?, ?> commune = table.getColumns().stream()
                .filter(colonne -> "Commune".equals(colonne.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Colonne « Commune » absente du tableau."));

        assertThat(commune.isVisible())
                .as("visible : une colonne masquée ne montrerait rien, et la recherche retient sur elle")
                .isTrue();
        assertThat(commune.getCellData(0)).isEqualTo("Ahetze");
        assertThat(commune.getCellData(1))
                .as("commune non résolue : cellule vide, comme « Hors protocole » quand il n'y a rien")
                .isEqualTo("");
    }

    @Test
    @DisplayName("#2525 : une nuit opportuniste s'affiche en pastille « hors protocole »")
    void case_opportuniste_pastille(FxRobot robot) {
        Labeled pastille = robot.lookup(".badge-opportuniste").queryAs(Labeled.class);
        assertThat(pastille.getText()).contains("Opportuniste");
    }

    @Test
    @DisplayName("double-clic sur un point avec passage ouvre le passage concerné")
    void double_clic_ouvre_le_passage(FxRobot robot) {
        DoubleClicDeterministe.surLigneContenant(robot, "#tableSaison", "640001");
        verify(ouvrirPassage).ouvrir(eq(42L), any(ContexteSite.class));
    }

    @Test
    @DisplayName("double-clic sur un point sans passage ouvre le carré du point")
    void double_clic_sans_passage_ouvre_le_carre(FxRobot robot) {
        DoubleClicDeterministe.surLigneContenant(robot, "#tableSaison", "640002");
        verify(ouvrirSite).ouvrirDetail("640002");
    }

    @Test
    @DisplayName("#2610 : aucune campagne à proposer, le sélecteur est retiré de la mise en page")
    void selecteur_campagne_efface_sans_campagne(FxRobot robot) {
        // `setVisible(false)` seul laisserait un trou dans la barre : c'est `managed` qui retire le
        // contrôle du calcul de mise en page. Vérifier les deux, sinon on ne teste que la moitié.
        assertThat(robot.lookup("#choixCampagne").queryAs(ComboBox.class).isVisible())
                .isFalse();
        assertThat(robot.lookup("#choixCampagne").queryAs(ComboBox.class).isManaged())
                .isFalse();
        assertThat(robot.lookup("#lblCampagne").queryAs(Label.class).isManaged())
                .isFalse();
    }

    @Test
    @DisplayName("#3544 : une campagne créée APRÈS l'ouverture fait réapparaître le sélecteur au retour")
    void selecteur_campagne_reapparait_au_retour(FxRobot robot) {
        ComboBox<?> choix = robot.lookup("#choixCampagne").queryAs(ComboBox.class);
        assertThat(choix.isManaged())
                .as("à l'ouverture, aucune campagne : le sélecteur est retiré de la mise en page")
                .isFalse();

        // Le geste de l'issue : on descend sur un passage, on crée une campagne depuis la modale de
        // rattachement, et on revient. La liste n'était rechargée qu'à l'ouverture de l'écran.
        when(serviceObserve.campagnesProposables()).thenReturn(List.of(new Campagne(1L, "Suivi ENS 2026", 2026, null)));
        robot.interact(() -> controleur.rafraichirAuRetour());

        assertThat(choix.isVisible()).as("le sélecteur reparaît").isTrue();
        assertThat(choix.isManaged())
                .as("et il reprend sa place dans la mise en page, pas seulement sa visibilité")
                .isTrue();
        assertThat(robot.lookup("#lblCampagne").queryAs(Label.class).isManaged())
                .as("son libellé revient avec lui")
                .isTrue();
    }

    @Test
    @DisplayName("#3544 : recharger la liste ne DÉFAIT pas la campagne retenue (ADR 3095)")
    void recharger_les_campagnes_garde_le_choix(FxRobot robot) {
        Campagne ens = new Campagne(1L, "Suivi ENS 2026", 2026, null);
        when(serviceObserve.campagnesProposables()).thenReturn(List.of(ens));
        robot.interact(() -> controleur.rafraichirAuRetour());

        ComboBox<Campagne> choix = robot.lookup("#choixCampagne").queryAs(ComboBox.class);
        robot.interact(() -> choix.setValue(ens));
        assertThat(choix.getValue()).isEqualTo(ens);

        // Un second retour, la liste contenant toujours cette campagne. Ce qu'on mesure ici n'est PAS
        // `choix.getValue()` : il survit à un `clear()`, je l'ai vérifié en mutant. Le dégât d'un
        // rechargement qui défait le choix se lit **côté service** - une saison relue SANS filtre,
        // c'est-à-dire le tableau de l'utilisateur qui se rouvre en grand sous ses yeux.
        clearInvocations(serviceObserve);
        robot.interact(() -> controleur.rafraichirAuRetour());

        assertThat(choix.getValue())
                .as("la campagne retenue existe toujours : recharger ne doit pas la défaire")
                .isEqualTo(ens);
        verify(serviceObserve, never()).soldePour(anyString(), anyInt(), org.mockito.ArgumentMatchers.isNull());
        verify(serviceObserve, times(1)).soldePour(anyString(), anyInt(), eq("Suivi ENS 2026"));
    }

    @Test
    @DisplayName("#3103 : chercher un lieu restreint la table, vider la recherche la rétablit")
    void chercher_un_lieu_restreint_la_table(FxRobot robot) {
        // Preuve de bout en bout : le champ du FXML, le socle Filtres du view-model, la FilteredList et
        // la SortedList de la table. Un test qui n'irait que jusqu'au view-model passerait même si le
        // champ n'était relié à rien.
        TableView<?> table = robot.lookup("#tableSaison").queryAs(TableView.class);

        robot.clickOn("#champRechercheLieu").write("640002");

        assertThat(table.getItems()).hasSize(1);

        robot.doubleClickOn("#champRechercheLieu").eraseText("640002".length());

        assertThat(table.getItems())
                .as("vider la recherche rend la saison entière")
                .hasSize(4);
    }

    @Test
    @DisplayName("#3103 : « Reste à faire » écarte les points à jour, et les rend en se décochant")
    void reste_a_faire_ecarte_les_points_a_jour(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSaison").queryAs(TableView.class);

        robot.clickOn("#caseResteAFaire");

        assertThat(table.getItems())
                .as("le point 640004 est à jour : c'est le seul que le filtre doit écarter")
                .hasSize(3);

        robot.clickOn("#caseResteAFaire");

        assertThat(table.getItems()).hasSize(4);
    }

    @Test
    @DisplayName("#3103 : la table reste triable une fois posée sur la liste filtrée")
    void la_table_reste_triable(FxRobot robot) {
        // Une `FilteredList` posée nue est non modifiable : `TableView` renonce alors à trier et vide
        // son `sortOrder` de lui-même, sans rien dire. Le défaut ne se voit ni à la compilation, ni sur
        // une capture - seulement en essayant de trier.
        TableView<?> table = robot.lookup("#tableSaison").queryAs(TableView.class);

        robot.interact(() -> trierSurLaPremiereColonne(table));

        assertThat(table.getSortOrder()).isNotEmpty();
        assertThat(table.getItems()).as("trier ne perd aucune ligne").hasSize(4);
    }

    /// Nomme le paramètre de type de la table, que `queryAs` rend joker : sans lui, poser une colonne
    /// dans `getSortOrder()` ne compile pas, et le faire compiler demanderait un transtypage non vérifié.
    private static <T> void trierSurLaPremiereColonne(TableView<T> table) {
        table.getSortOrder().setAll(table.getColumns().get(0));
    }
}
