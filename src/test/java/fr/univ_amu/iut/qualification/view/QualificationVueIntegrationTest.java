package fr.univ_amu.iut.qualification.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.qualification.model.ContexteVerification;
import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import fr.univ_amu.iut.qualification.model.PreCheckNuit.Feu;
import fr.univ_amu.iut.qualification.model.SelectionDEcoute;
import fr.univ_amu.iut.qualification.model.SequenceEnSelection;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import fr.univ_amu.iut.qualification.viewmodel.QualificationViewModel;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Test d'intégration TestFX **complémentaire** de l'écran M-Qualification, ciblant les câblages
/// Vue↔ViewModel que les tests de vue « lecture de propriétés » laissent passer : un écran non câblé
/// (feux non liés, colonnes sans `cellValueFactory`, bouton « Régénérer » mort) passerait pourtant
/// les tests qui se contentent de lire le ViewModel. Ici, chaque vérification fait un **vrai lookup
/// de contrôle par `fx:id`** (`robot.lookup`) ou inspecte un contrôle réellement rendu, puis vérifie
/// soit que son état reflète le ViewModel, soit qu'une interaction (`fire`, sélection de ligne,
/// lecture audio) produit bien l'effet attendu.
///
/// Couvre en priorité les manques récurrents de la feature relevés par l'audit (2026-06-18) :
/// pré-check à 3 feux R13 (couleur + anomalie), colonnes de la liste de sélection liées aux données,
/// et personnalisation/régénération R12 (recharge de la liste, remise à zéro de la progression,
/// verdict conservé).
///
/// Même harnais que `QualificationViewTest` : chargement du FXML par Guice avec un
/// [ServiceQualification] mocké, deux ViewModel sur le service mocké, ouverture sur un passage.
/// Le diagnostic de pré-check seedé porte ici un feu **rouge** (couverture) pour exercer le chemin
/// « anomalie signalée », et `creerSelection` est stubbé pour que la régénération R12 aboutisse.
@ExtendWith(ApplicationExtension.class)
class QualificationVueIntegrationTest {

    private static final long ID_PASSAGE = 42L;
    private static final long ID_SELECTION = 7L;

    private QualificationController controleur;

    /// Service mocké, promu en champ pour que les tests de geste puissent vérifier les appels
    /// (`verify`) qui traversent la Vue et le ViewModel (#1524, verdict par fichier).
    private ServiceQualification service;

    @Start
    void start(Stage stage) throws Exception {
        service = mock(ServiceQualification.class);
        // Feu rouge sur la couverture → presenteUneAnomalie() vrai (exerce l'anomalie R13 visible).
        when(service.precheck(anyLong())).thenReturn(new PreCheckNuit.Diagnostic(Feu.ROUGE, Feu.ORANGE, Feu.VERT));
        when(service.chargerContexte(anyLong()))
                .thenReturn(new ContexteVerification(
                        "640380",
                        "A1",
                        "Étang de la Tuilière",
                        2,
                        2026,
                        "2026-06-22",
                        "20:25:00",
                        "07:47:00",
                        30,
                        18000.0,
                        StatutWorkflow.TRANSFORME,
                        null));
        when(service.ouvrirVerification(anyLong()))
                .thenReturn(new SelectionDEcoute(ID_SELECTION, MethodeSelection.REPARTITION_TEMPORELLE, 3, ID_PASSAGE));
        when(service.detaillerSelection(anyLong())).thenReturn(lignes(3));
        // R12 : la régénération doit aboutir (sinon NPE captée → liste inchangée).
        when(service.creerSelection(anyLong(), any(MethodeSelection.class), anyInt()))
                .thenReturn(new SelectionDEcoute(99L, MethodeSelection.ALEATOIRE, 3, ID_PASSAGE));

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            QualificationViewModel verdict() {
                return new QualificationViewModel(service);
            }

            @Provides
            SelectionEcouteViewModel selection() {
                return new SelectionEcouteViewModel(service);
            }

            @Provides
            OuvrirPassage ouvrirPassage() {
                return (id, contexte) -> {};
            }

            @Provides
            OuvrirSite ouvrirSite() {
                return new OuvrirSite() {
                    @Override
                    public void ouvrirListe() {}

                    @Override
                    public void ouvrirDetail(String numeroCarre) {}
                };
            }
        });
        FXMLLoader loader = new FXMLLoader(QualificationController.class.getResource("Qualification.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        // Ces tests vérifient le comportement R12 de « Régénérer », pas la confirmation (#798) : on accepte
        // d'office pour ne pas bloquer sur un Alert natif quand une progression d'écoute est en cours.
        controleur.confirmateur().definir(message -> true);
        // Compte rendu neutralisé par défaut (#1509) : un vrai dialogue figerait le headless.
        controleur.notificateur().definir((niveau, entete, message) -> {});
        controleur.ouvrirSur(
                new ContextePassage(ID_PASSAGE, 2, new ContexteSite("640380", "A1", "Étang de la Tuilière")));
        stage.setScene(new Scene(vue, 1100, 760));
        stage.show();
    }

    @Test
    @DisplayName(
            "Emplacement (fil d'Ariane) : Mes sites › Carré N › Détails du passage N° X › Vérifier l'enregistrement")
    void emplacement_reflete_le_passage() {
        assertThat(controleur.emplacement())
                .extracting(Lieu::libelle)
                .containsExactly("Mes sites", "Carré 640380", "Détails du passage N° 2", "Vérifier l'enregistrement");
        assertThat(controleur.emplacement().get(0).estCliquable()).isTrue();
        assertThat(controleur.emplacement().get(3).estCliquable()).isFalse();
    }

    private static List<SequenceEnSelection> lignes(int n) {
        List<SequenceEnSelection> lignes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            SequenceDEcoute sequence = new SequenceDEcoute(
                    (long) i, "PaRec_" + i + ".wav", null, i, 0.0, 5.0, "/ws/seq" + i + ".wav", true, 1L);
            lignes.add(new SequenceEnSelection(sequence, i, false));
        }
        return lignes;
    }

    @Test
    @DisplayName("R13 : les 3 feux du pré-check reflètent le diagnostic du ViewModel (lookup fx:id)")
    void les_trois_feux_refletent_le_precheck(FxRobot robot) {
        Label feuCouverture = robot.lookup("#feuCouverture").queryAs(Label.class);
        Label feuNombre = robot.lookup("#feuNombre").queryAs(Label.class);
        Label feuRenommage = robot.lookup("#feuRenommage").queryAs(Label.class);

        // La couleur du feu est portée par une classe CSS feu-vert / feu-orange / feu-rouge.
        assertThat(feuCouverture.getStyleClass()).contains("feu-rouge");
        assertThat(feuNombre.getStyleClass()).contains("feu-orange");
        assertThat(feuRenommage.getStyleClass()).contains("feu-vert");
        // Le libellé reste lisible (le câblage ne doit pas écraser le texte de l'indicateur).
        assertThat(feuCouverture.getText()).contains("Couverture");
        assertThat(feuNombre.getText()).contains("Nombre");
        assertThat(feuRenommage.getText()).contains("renommage");
        // #801 : différenciateur NON chromatique (pictogramme distinct par état) + infobulle explicative,
        // pour ne pas encoder l'état par la seule couleur.
        assertThat(feuCouverture.getText()).startsWith("✖");
        assertThat(feuNombre.getText()).startsWith("⚠");
        assertThat(feuRenommage.getText()).startsWith("✓");
        assertThat(feuCouverture.getTooltip()).isNotNull();
        assertThat(feuCouverture.getTooltip().getText()).contains("anomalie");
    }

    @Test
    @DisplayName("R13 : l'avertissement d'anomalie est visible quand un feu est rouge")
    void anomalie_visible_quand_feu_rouge(FxRobot robot) {
        Label anomalie = robot.lookup("#lblAnomalie").queryAs(Label.class);

        // Le diagnostic seedé porte un feu rouge (couverture) → preCheckAnomalieProperty vrai → affiché.
        assertThat(anomalie.isVisible()).isTrue();
        assertThat(anomalie.isManaged()).isTrue();
        // #1506 : la barre nomme le feu en cause plutôt qu'un « anomalie » anonyme.
        assertThat(anomalie.getText()).contains("couverture horaire");
    }

    @Test
    @DisplayName("Les colonnes de la liste de sélection sont liées aux données (cellValueFactory)")
    void colonnes_table_liees_aux_donnees(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);

        assertThat(table.getColumns()).hasSize(5);
        TableColumn<?, ?> colPosition = table.getColumns().get(0);
        TableColumn<?, ?> colFichier = table.getColumns().get(1);
        TableColumn<?, ?> colDuree = table.getColumns().get(2);
        TableColumn<?, ?> colEcoute = table.getColumns().get(3);
        TableColumn<?, ?> colVerdict = table.getColumns().get(4);

        // Une colonne non liée renverrait null : ces valeurs prouvent les cellValueFactory câblées.
        assertThat((String) colPosition.getCellData(0)).isEqualTo("1");
        assertThat((String) colFichier.getCellData(0)).isEqualTo("PaRec_0.wav");
        assertThat((String) colDuree.getCellData(0)).contains("5").endsWith("s");
        assertThat((String) colEcoute.getCellData(0)).isEqualTo("○"); // séquence non encore écoutée
        // #1524 : le verdict par fichier vaut NON_JUGE tant qu'aucune écoute n'a été rendue.
        assertThat((VerdictFichier) colVerdict.getCellData(0)).isEqualTo(VerdictFichier.NON_JUGE);
    }

    @Test
    @DisplayName("#1524 : juger « Bon » enregistre le verdict par fichier, met à jour la ligne et le bouton")
    void juger_bon_met_a_jour_la_ligne_et_le_bouton(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);
        Button bon = robot.lookup("#boutonBon").queryAs(Button.class);

        robot.interact(() -> table.getSelectionModel().select(0)); // séquence courante = ligne 0 (id 0)
        robot.interact(bon::fire);
        WaitForAsyncUtils.waitForFxEvents();

        // Le geste traverse bien le ViewModel jusqu'au service (verdict PAR FICHIER de la séquence).
        verify(service).enregistrerVerdictFichier(ID_PASSAGE, 0L, VerdictFichier.BON);
        // La ligne (colonne badge) reflète le verdict rendu...
        TableColumn<?, ?> colVerdict = table.getColumns().get(4);
        assertThat((VerdictFichier) colVerdict.getCellData(0)).isEqualTo(VerdictFichier.BON);
        // ...et le bouton correspondant est mis en évidence (classe verdict-fichier-actif).
        assertThat(bon.getStyleClass()).contains("verdict-fichier-actif");
    }

    @Test
    @DisplayName("#1524 : les boutons de verdict par fichier sont désactivés tant qu'aucune séquence n'est choisie")
    void boutons_par_fichier_desactives_sans_sequence(FxRobot robot) {
        Button bon = robot.lookup("#boutonBon").queryAs(Button.class);
        Button mauvais = robot.lookup("#boutonMauvais").queryAs(Button.class);
        Button inexploitable = robot.lookup("#boutonInexploitable").queryAs(Button.class);
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);

        // À l'ouverture, aucune séquence n'est sélectionnée : rien à juger → boutons désactivés.
        robot.interact(() -> table.getSelectionModel().clearSelection());
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(bon.isDisabled()).isTrue();
        assertThat(mauvais.isDisabled()).isTrue();
        assertThat(inexploitable.isDisabled()).isTrue();

        // Une fois une séquence sélectionnée, les trois deviennent actifs.
        robot.interact(() -> table.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(bon.isDisabled()).isFalse();
        assertThat(mauvais.isDisabled()).isFalse();
        assertThat(inexploitable.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Sélectionner une ligne met à jour le détail de la séquence courante (lookup fx:id)")
    void selection_ligne_met_a_jour_le_detail(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);
        Label seqNumero = robot.lookup("#lblSeqNumero").queryAs(Label.class);
        Label seqMeta = robot.lookup("#lblSeqMeta").queryAs(Label.class);

        robot.interact(() -> table.getSelectionModel().select(1));

        assertThat(seqNumero.getText()).contains("N° 2"); // position 1 → numéro 2
        assertThat(seqMeta.getText()).contains("PaRec_1.wav");
    }

    @Test
    @DisplayName("R10 : marquer une séquence écoutée coche la colonne « Écouté »")
    void ecoute_coche_la_colonne_ecoute(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);
        AudioView audio = robot.lookup("#audioView").queryAs(AudioView.class);
        TableColumn<?, ?> colEcoute = table.getColumns().get(3);
        assertThat((String) colEcoute.getCellData(0)).isEqualTo("○");

        robot.interact(() -> table.getSelectionModel().select(0));
        robot.interact(() -> audio.setPlaying(true)); // début de lecture → marquage écouté (R10)
        WaitForAsyncUtils.waitForFxEvents();

        assertThat((String) colEcoute.getCellData(0)).isEqualTo("✓");
    }

    @Test
    @DisplayName("R12 : « Régénérer » recharge la liste et remet l'écoute à zéro (colonne « Écouté »)")
    void regenerer_remet_l_ecoute_a_zero(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);
        AudioView audio = robot.lookup("#audioView").queryAs(AudioView.class);
        Button regenerer = robot.lookup("#boutonRegenerer").queryAs(Button.class);
        TableColumn<?, ?> colEcoute = table.getColumns().get(3);

        // On écoute une séquence...
        robot.interact(() -> table.getSelectionModel().select(0));
        robot.interact(() -> audio.setPlaying(true));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat((String) colEcoute.getCellData(0)).isEqualTo("✓");

        // ... puis on régénère : la sélection est rechargée, l'écoute repart de zéro (R12).
        robot.interact(regenerer::fire);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat((String) colEcoute.getCellData(0)).isEqualTo("○"); // liste rechargée, rien d'écouté
    }

    @Test
    @DisplayName("#1524 : la barre tricolore reflète la répartition des verdicts par fichier")
    void barre_tricolore_reflete_les_verdicts(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);
        BarreVerdicts barre = robot.lookup("#barreVerdicts").queryAs(BarreVerdicts.class);
        Label resume = robot.lookup("#lblRepartitionVerdicts").queryAs(Label.class);

        // 2 Bon (lignes 0, 1) + 1 Inexploitable (ligne 2), sur 3 séquences → aucune « non jugée ».
        jugerLigne(robot, table, 0, "#boutonBon");
        jugerLigne(robot, table, 1, "#boutonBon");
        jugerLigne(robot, table, 2, "#boutonInexploitable");
        WaitForAsyncUtils.waitForFxEvents();

        // Le résumé chiffré double la couleur en texte (accessibilité #801).
        assertThat(resume.getText()).contains("2 Bon").contains("0 Mauvais").contains("1 Inexploitable");
        // Le segment vert (2 Bon) fait ~2× le rouge (1 Inexploitable), tous deux non nuls.
        Region segBon = (Region) barre.lookup(".segment-bon");
        Region segInexploitable = (Region) barre.lookup(".segment-inexploitable");
        assertThat(segInexploitable.getPrefWidth()).isGreaterThan(0.0);
        assertThat(segBon.getPrefWidth()).isGreaterThan(segInexploitable.getPrefWidth());
    }

    @Test
    @DisplayName("#1524 : la puce « Proposé » affiche le verdict dérivé des verdicts par fichier")
    void puce_propose_affiche_le_verdict_derive(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);
        Label propose = robot.lookup("#lblVerdictPropose").queryAs(Label.class);
        // À l'ouverture, aucune séquence n'est jugée → puce masquée.
        assertThat(propose.isVisible()).isFalse();

        // 2 Bon + 1 Inexploitable (minorité) → Douteux.
        jugerLigne(robot, table, 0, "#boutonBon");
        jugerLigne(robot, table, 1, "#boutonBon");
        jugerLigne(robot, table, 2, "#boutonInexploitable");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(propose.isVisible()).isTrue();
        assertThat(propose.getText()).contains("Proposé").contains("Douteux");
        assertThat(propose.getStyleClass()).contains("propose-douteux");
    }

    @Test
    @DisplayName("#1524 : le proposé pré-remplit le verdict global, et la surcharge est signalée sur la puce")
    void propose_pre_remplit_le_verdict_global_et_signale_la_surcharge(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);
        Button boutonOk = robot.lookup("#boutonOk").queryAs(Button.class);
        Button boutonDouteux = robot.lookup("#boutonDouteux").queryAs(Button.class);
        Button enregistrer = robot.lookup("#boutonEnregistrer").queryAs(Button.class);
        Label propose = robot.lookup("#lblVerdictPropose").queryAs(Label.class);

        // 2 Bon + 1 Inexploitable → proposé Douteux, qui pré-remplit le verdict global.
        jugerLigne(robot, table, 0, "#boutonBon");
        jugerLigne(robot, table, 1, "#boutonBon");
        jugerLigne(robot, table, 2, "#boutonInexploitable");
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(boutonDouteux.getStyleClass()).contains("verdict-choisi");
        assertThat(enregistrer.isDisabled())
                .as("proposé décisif → enregistrable")
                .isFalse();
        assertThat(propose.getText()).doesNotContain("surchargé");

        // Surcharge : l'utilisateur clique OK → le choix bascule, la puce le signale.
        robot.interact(boutonOk::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(boutonOk.getStyleClass()).contains("verdict-choisi");
        assertThat(boutonDouteux.getStyleClass()).doesNotContain("verdict-choisi");
        assertThat(propose.getText()).contains("Proposé").contains("Douteux").contains("surchargé");
    }

    @Test
    @DisplayName("#1524 : « Enregistrer » persiste le verdict DÉRIVÉ (la dérivation fait autorité)")
    void enregistrer_persiste_le_verdict_derive(FxRobot robot) {
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);
        Button enregistrer = robot.lookup("#boutonEnregistrer").queryAs(Button.class);

        // 2 Bon + 1 Inexploitable → proposé Douteux, qui pré-remplit le verdict global (aucune surcharge).
        jugerLigne(robot, table, 0, "#boutonBon");
        jugerLigne(robot, table, 1, "#boutonBon");
        jugerLigne(robot, table, 2, "#boutonInexploitable");
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(enregistrer::fire);
        WaitForAsyncUtils.waitForFxEvents();

        // Le verdict persisté est le dérivé (Douteux), sans saisie manuelle : la dérivation fait autorité.
        verify(service).enregistrerVerdict(ID_PASSAGE, Verdict.DOUTEUX, null);
    }

    private static void jugerLigne(FxRobot robot, TableView<?> table, int index, String boutonId) {
        robot.interact(() -> table.getSelectionModel().select(index));
        robot.interact(() -> robot.lookup(boutonId).queryAs(Button.class).fire());
    }

    @Test
    @DisplayName("R12 : régénérer conserve le verdict déjà choisi (« Enregistrer » reste actif)")
    void regenerer_conserve_le_verdict_choisi(FxRobot robot) {
        Button ok = robot.lookup("#boutonOk").queryAs(Button.class);
        Button regenerer = robot.lookup("#boutonRegenerer").queryAs(Button.class);
        Button enregistrer = robot.lookup("#boutonEnregistrer").queryAs(Button.class);

        robot.interact(ok::fire); // verdict OK choisi → enregistrement possible
        assertThat(enregistrer.isDisabled()).isFalse();

        robot.interact(regenerer::fire); // régénérer la sélection ne doit pas effacer le verdict

        assertThat(enregistrer.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("#1509 : « Régénérer » rend compte de la sélection reconstruite")
    void regenerer_rend_compte(FxRobot robot) {
        Button regenerer = robot.lookup("#boutonRegenerer").queryAs(Button.class);
        List<String> comptes = new ArrayList<>();
        controleur.notificateur().definir((niveau, entete, message) -> comptes.add(entete + " | " + message));

        robot.interact(regenerer::fire);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(comptes).as("la régénération n'était plus muette").hasSize(1);
        assertThat(comptes.get(0)).contains("Sélection régénérée").contains("réparties sur la nuit");
    }

    @Test
    @DisplayName("#1509 : l'état de chargement suit la disponibilité de l'AudioView")
    void chargement_audio_suit_l_etat(FxRobot robot) {
        Label chargement = robot.lookup("#lblChargementAudio").queryAs(Label.class);
        AudioView audio = robot.lookup("#audioView").queryAs(AudioView.class);
        TableView<?> table = robot.lookup("#tableSequences").queryAs(TableView.class);

        // Aucune séquence sélectionnée → pas de source → pas d'indicateur de chargement.
        robot.interact(() -> table.getSelectionModel().clearSelection());
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(chargement.isVisible()).isFalse();

        // Séquence sélectionnée : l'indicateur reflète l'invariant (source posée ET pas encore prête).
        robot.interact(() -> table.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();
        boolean attendu = audio.getAudioFile() != null
                && !audio.isReady()
                && (audio.getErrorMessage() == null || audio.getErrorMessage().isEmpty());
        assertThat(chargement.isVisible()).isEqualTo(attendu);
    }
}
