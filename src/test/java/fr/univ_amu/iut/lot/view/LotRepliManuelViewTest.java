package fr.univ_amu.iut.lot.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
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
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.CauseRefus;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.EchecUnite;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ModeDepot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.StatutDepotUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import fr.univ_amu.iut.lot.viewmodel.TraitementViewModel;
import fr.univ_amu.iut.recette.Attente;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Le **repli manuel** de l'écran de lot (#5867), monté connecté comme [LotDepotConnecteViewTest] : même
/// montage, à part pour que chacun des deux bancs reste lisible d'un tenant.
///
/// Connecté en séquences WAV, l'écran n'offre ni archives ni dépôt manuel. Quand Vigie-Chiro refuse des
/// séquences sans recours, la carte des archives reparaît sous le téléversement, sans devenir une étape,
/// et porte le dernier geste : marquer le passage déposé.
@ExtendWith(ApplicationExtension.class)
class LotRepliManuelViewTest {

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

    /// Le repli manuel (#5867). Connecté en forme WAV, l'écran n'offre ni archives ni dépôt manuel
    /// (#5824) : quand le stockage refuse des séquences sans recours, il ne restait aucune voie. Le
    /// scénario fait refuser un téléversement et cherche le repli là où l'utilisateur le trouvera,
    /// sous l'étape qui vient d'échouer.
    @Test
    @DisplayName("#5867 : un téléversement en séquences refusé sans recours offre le repli sous l'étape 2, fil à trois")
    void un_televersement_refuse_offre_le_repli_sous_le_televersement(FxRobot robot) {
        when(service.formeDuDepot(ID_PASSAGE)).thenReturn(ModeDepot.SEQUENCES_WAV);
        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));
        assertThat(visible(robot, "#carteArchives"))
                .as("avant tout refus, aucun repli")
                .isFalse();
        // Le stockage refuse deux séquences : le plan enregistré les porte, et le service les compte.
        when(depot.deposer(eq(ID_PASSAGE), any(), any(), any()))
                .thenReturn(new BilanDepot(
                        "part-1",
                        0,
                        List.of(
                                new EchecUnite("Car_000.wav", "HTTP 403", true, CauseRefus.STOCKAGE),
                                new EchecUnite("Car_001.wav", "HTTP 403", true, CauseRefus.STOCKAGE))));
        when(service.sequencesRefuseesSansRecours(ID_PASSAGE)).thenReturn(2);

        robot.interact(() -> robot.lookup("#btnTeleverser").queryButton().fire());

        Attente.queSurLeFil(
                () -> {
                    Node carte = robot.lookup("#carteArchives").query();
                    return carte.isVisible() && carte.isManaged();
                },
                "la carte du repli paraît",
                5_000L);
        assertThat(texte(robot, "#lblTitreCarteArchives")).isEqualTo("Repli : déposer à la main");
        assertThat(texte(robot, "#lblConsigneCarteArchives"))
                .contains("Vigie-Chiro a refusé 2 séquence(s)")
                .contains("déposez-les à la main sur le portail")
                .contains("puis marquez le passage déposé");
        assertThat(visible(robot, "#enveloppeMarquerDeposeRepli"))
                .as("le dernier geste du repli a sa porte dans la carte")
                .isTrue();
        assertThat(Attente.surLeFil(
                        () -> robot.lookup("#btnMarquerDeposeRepli")
                                .queryAs(Button.class)
                                .isDisabled(),
                        "lire l'état du bouton du repli",
                        5_000L))
                .as("sans archive générée, il n'y a encore rien eu à déposer à la main")
                .isTrue();
        assertThat(visible(robot, "#ligneCheminDepot"))
                .as("le chemin du dossier où les archives seront écrites")
                .isTrue();
        assertThat(visible(robot, "#enveloppeOuvrirDepot"))
                .as("« Ouvrir le dossier (dépôt manuel) »")
                .isTrue();
        assertThat(texteDeLEtape2(robot))
                .as("en séquences rien ne produit d'archive à la place de l'utilisateur : la table vide ne"
                        + " renvoie ni à un téléversement qui en ferait, ni à une « étape 3 » qui n'est pas la sienne")
                .isEqualTo("Aucune archive de dépôt pour l'instant.");
        assertThat(rang(robot, "#carteArchives"))
                .as("le repli découle du téléversement : sa carte est dessous, pas avant")
                .isGreaterThan(rang(robot, "#lblTitreTeleversement"));
        assertThat(texte(robot, "#lblTitreTeleversement"))
                .as("le fil reste à trois : les numéros ne bougent pas")
                .isEqualTo("2. Téléverser sur Vigie-Chiro");
        assertThat(texte(robot, "#lblTitreDeposer")).isEqualTo("3. Lancer la participation");
        assertThat(Attente.surLeFil(
                        () -> robot.lookup("#stepper")
                                .queryAs(HBox.class)
                                .getChildren()
                                .size(),
                        "compter les puces du fil d'étapes",
                        5_000L))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("#5867 : en forme ZIP, la carte des archives garde son titre d'étape et sa place avant l'étape 3")
    void en_zip_la_carte_des_archives_garde_son_titre_et_sa_place(FxRobot robot) {
        when(service.formeDuDepot(ID_PASSAGE)).thenReturn(ModeDepot.ARCHIVES_ZIP);
        // Une archive refusée n'est pas une séquence ; et même si le service en comptait, la carte est
        // déjà une étape du fil dans cette forme.
        lenient().when(service.sequencesRefuseesSansRecours(ID_PASSAGE)).thenReturn(2);

        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));

        assertThat(texte(robot, "#lblTitreCarteArchives")).isEqualTo("2. Générer les archives de dépôt");
        assertThat(texte(robot, "#lblConsigneCarteArchives")).contains("Découpe les séquences en archives ZIP");
        assertThat(rang(robot, "#carteArchives")).isLessThan(rang(robot, "#lblTitreTeleversement"));
    }

    /// Le dernier geste du repli (#5867). Dès qu'une participation est liée, la dernière étape offre
    /// « Lancer la participation » et plus rien ne marque déposé : un dépôt fini à la main restait
    /// « Dépôt en cours » pour toujours. La carte du repli porte donc sa propre porte.
    @Test
    @DisplayName("#5867 : en repli, une fois les archives générées, la carte marque le passage déposé")
    void en_repli_la_carte_marque_le_passage_depose(FxRobot robot) {
        when(service.formeDuDepot(ID_PASSAGE)).thenReturn(ModeDepot.SEQUENCES_WAV);
        when(service.sequencesRefuseesSansRecours(ID_PASSAGE)).thenReturn(2);
        when(service.archivesDepot("/ws/session-42"))
                .thenReturn(List.of(new ArchiveDepot(Path.of("/ws/session-42/depot/Car-1.zip"), 1, 1_000L, 2)));
        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));
        Attente.queSurLeFil(
                () -> !robot.lookup("#btnMarquerDeposeRepli")
                        .queryAs(Button.class)
                        .isDisabled(),
                "le bouton du repli s'ouvre",
                5_000L);

        robot.interact(
                () -> robot.lookup("#btnMarquerDeposeRepli").queryButton().fire());

        verify(service).marquerDepose(ID_PASSAGE);
    }

    @Test
    @DisplayName("#5867 : sans repli, le bouton de la carte des archives qui marque déposé reste absent")
    void sans_repli_le_bouton_de_la_carte_est_absent(FxRobot robot) {
        when(service.formeDuDepot(ID_PASSAGE)).thenReturn(ModeDepot.ARCHIVES_ZIP);

        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));

        assertThat(visible(robot, "#carteArchives")).isTrue();
        // Les deux drapeaux, un par un : un nœud visible mais non géré se peint quand même, et un seul
        // « ni visible ni géré » se satisferait de l'un des deux.
        Node enveloppe = Attente.surLeFil(
                () -> robot.lookup("#enveloppeMarquerDeposeRepli").query(), "trouver l'enveloppe du bouton", 5_000L);
        assertThat(Attente.surLeFil(enveloppe::isVisible, "lire sa visibilité", 5_000L))
                .as("en forme ZIP la carte est une étape : marquer déposé reste l'affaire de la dernière")
                .isFalse();
        assertThat(Attente.surLeFil(enveloppe::isManaged, "lire sa prise en compte dans la mise en page", 5_000L))
                .as("et elle ne garde pas la place d'un bouton absent")
                .isFalse();
    }

    /// La table de dépôt se repose depuis le plan enregistré à chaque ouverture. Elle oubliait qu'un
    /// refus était définitif, et le bouton promettait une reprise au-dessus de la carte du repli.
    @Test
    @DisplayName("#5867 : à la réouverture sur des refus définitifs, le bouton ne promet plus de reprise")
    void a_la_reouverture_le_bouton_ne_promet_plus_de_reprise(FxRobot robot) {
        when(service.formeDuDepot(ID_PASSAGE)).thenReturn(ModeDepot.SEQUENCES_WAV);
        when(service.sequencesRefuseesSansRecours(ID_PASSAGE)).thenReturn(1);
        when(service.unitesDepot(ID_PASSAGE))
                .thenReturn(List.of(
                        new DepotUnite(
                                1L,
                                ID_PASSAGE,
                                "Car_000.wav",
                                TypeDepotUnite.WAV,
                                StatutDepotUnite.DEPOSE,
                                "fichier-1",
                                null,
                                false,
                                "2026-06-21T09:00:00"),
                        new DepotUnite(
                                2L,
                                ID_PASSAGE,
                                "Car_001.wav",
                                TypeDepotUnite.WAV,
                                StatutDepotUnite.ECHEC,
                                null,
                                "HTTP 403 : SignatureDoesNotMatch",
                                true,
                                "2026-06-21T09:00:00")));

        robot.interact(() -> controleur.ouvrirSur(CONTEXTE));

        assertThat(Attente.surLeFil(
                        () -> robot.lookup("#btnTeleverser").queryButton().getText(),
                        "lire le bouton de téléversement",
                        5_000L))
                .as("une séquence en ligne, une refusée sans recours : il n'y a rien à reprendre")
                .isEqualTo("Téléverser sur Vigie-Chiro");
        assertThat(texte(robot, "#lblTitreCarteArchives")).isEqualTo("Repli : déposer à la main");
    }

    /// Le rang, parmi les cartes de la page, de celle qui porte le nœud désigné.
    private static int rang(FxRobot robot, String selecteur) {
        return Attente.surLeFil(
                () -> {
                    Parent page = robot.lookup("#carteArchives").query().getParent();
                    Node noeud = robot.lookup(selecteur).query();
                    while (noeud.getParent() != page) {
                        noeud = noeud.getParent();
                    }
                    return page.getChildrenUnmodifiable().indexOf(noeud);
                },
                "situer " + selecteur + " dans la page",
                5_000L);
    }

    private static boolean visible(FxRobot robot, String selecteur) {
        return Attente.surLeFil(
                () -> {
                    Node noeud = robot.lookup(selecteur).query();
                    return noeud.isVisible() && noeud.isManaged();
                },
                "lire la présence de " + selecteur,
                5_000L);
    }

    private static String texte(FxRobot robot, String selecteur) {
        return Attente.surLeFil(
                () -> robot.lookup(selecteur).queryAs(Label.class).getText(), "lire " + selecteur, 5_000L);
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

    private static DepotUnite unite(String identifiant, StatutDepotUnite statut) {
        return new DepotUnite(
                1L, ID_PASSAGE, identifiant, TypeDepotUnite.ZIP, statut, null, null, "2026-07-11T15:00:00");
    }
}
