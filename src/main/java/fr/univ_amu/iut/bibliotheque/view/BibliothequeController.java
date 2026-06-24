package fr.univ_amu.iut.bibliotheque.view;

import com.google.inject.Inject;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.bibliotheque.model.EntreeBiblio;
import fr.univ_amu.iut.bibliotheque.viewmodel.BibliothequeViewModel;
import java.io.File;
import java.util.Objects;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.DirectoryChooser;

/// Controller de l'écran **M-Bibliotheque** (`Bibliotheque.fxml`).
///
/// Pur câblage (patron CM4) : lie la table des sons de référence, la sélection, le détail
/// (commentaire), l'écoute ([AudioView] réutilisé de M-Vision-Tadarida) et l'export au
/// [BibliothequeViewModel]. Le chargement initial est déclenché ici (écran sans paramètre), comme
/// `MesSitesController`. Aucun accès base de données ni logique métier (règle ArchUnit
/// `view_sans_jdbc`).
public class BibliothequeController {

    private final BibliothequeViewModel viewModel;

    @FXML
    private Label lblResume;

    @FXML
    private TableView<EntreeBiblio> tableEntrees;

    @FXML
    private TableColumn<EntreeBiblio, String> colTaxon;

    @FXML
    private TableColumn<EntreeBiblio, String> colSequence;

    @FXML
    private TableColumn<EntreeBiblio, String> colFrequence;

    @FXML
    private Label lblDetail;

    @FXML
    private AudioView audioView;

    @FXML
    private Button btnExporter;

    @FXML
    private Label lblMessage;

    @Inject
    public BibliothequeController(BibliothequeViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    @FXML
    private void initialize() {
        colTaxon.setCellValueFactory(
                cellule -> new ReadOnlyStringWrapper(cellule.getValue().taxon()));
        colSequence.setCellValueFactory(
                cellule -> new ReadOnlyStringWrapper(cellule.getValue().nomSequence()));
        colFrequence.setCellValueFactory(cellule -> {
            Integer hz = cellule.getValue().frequenceHz();
            return new ReadOnlyStringWrapper(hz == null ? "—" : String.valueOf(hz));
        });
        tableEntrees.setItems(viewModel.entrees());

        // La sélection de la table pilote le VM (un listener, pas un bind : selectedItemProperty est
        // en lecture seule, et le VM remet lui-même la sélection à null au rechargement) et alimente
        // le détail (commentaire) côté présentation.
        tableEntrees.getSelectionModel().selectedItemProperty().addListener((obs, ancienne, nouvelle) -> {
            viewModel.selectionProperty().set(nouvelle);
            majDetail(nouvelle);
        });

        lblResume.textProperty().bind(viewModel.resumeProperty());

        // Vue audio (composant fourni) : la source suit le son sélectionné ; le clip est libéré quand
        // la vue quitte la scène. AudioView tolère un chemin inexistant (aucune lecture déclenchée).
        audioView.audioFileProperty().bind(viewModel.cheminAudioCourantProperty());
        audioView.sceneProperty().addListener((obs, avant, scene) -> {
            if (scene == null) {
                audioView.dispose();
            }
        });

        // Export possible seulement si la bibliothèque contient au moins un son de référence.
        btnExporter.disableProperty().bind(viewModel.biblioNonVideProperty().not());

        lblMessage.textProperty().bind(viewModel.messageProperty());
        var messagePresent = viewModel.messageProperty().isNotEmpty();
        lblMessage.visibleProperty().bind(messagePresent);
        lblMessage.managedProperty().bind(messagePresent);

        viewModel.charger();
    }

    /// « Exporter la bibliothèque » : ouvre le sélecteur de dossier natif puis délègue au VM
    /// (écriture du CSV + copie des sons). Le dialog vit dans la vue (non testé en TestFX) ;
    /// l'écriture est testée côté ViewModel.
    @FXML
    private void exporter() {
        DirectoryChooser selecteur = new DirectoryChooser();
        selecteur.setTitle("Choisir le dossier où constituer la bibliothèque de sons");
        File dossier = selecteur.showDialog(btnExporter.getScene().getWindow());
        if (dossier != null) {
            viewModel.exporter(dossier.toPath());
        }
    }

    private void majDetail(EntreeBiblio entree) {
        if (entree == null) {
            lblDetail.setText("");
            return;
        }
        String commentaire = entree.commentaire();
        lblDetail.setText(
                commentaire == null || commentaire.isBlank()
                        ? "Aucun commentaire pour ce son."
                        : "Commentaire : " + commentaire);
    }
}
