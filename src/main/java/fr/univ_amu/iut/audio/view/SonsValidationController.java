package fr.univ_amu.iut.audio.view;

import com.google.inject.Inject;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.ComptageAudio;
import fr.univ_amu.iut.audio.viewmodel.FormatLigneAudio;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.Taxon;
import java.io.File;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/// Controller de la **vue audio unifiée** (`SonsValidation.fxml`, #audio).
///
/// Pur câblage (patron CM4) : lie la table des [LigneObservationAudio], la sélection, le panneau
/// d'écoute (détail + `AudioView`) et la revue au [AudioViewModel]. Les actions communes (valider /
/// corriger / basculer la référence) sont toujours offertes ; les actions propres à la source (import
/// CSV / export `_Vu` d'un passage, export bibliothèque des références) ne s'affichent dans le menu
/// « ☰ » que pour la source concernée, et les colonnes de **contexte** (passage / carré / point) sont
/// masquées quand la source est un **unique passage** (elles y seraient constantes). Aucun accès base
/// ni logique métier ici (règle ArchUnit `view_sans_jdbc`).
public class SonsValidationController implements EmplacementNavigation {

    private final AudioViewModel viewModel;
    private final OuvrirSite ouvrirSite;
    private final OuvrirPassage ouvrirPassage;
    private final OuvrirAnalyse ouvrirAnalyse;
    private final OuvrirMultisite ouvrirMultisite;

    /// Source courante, mémorisée pour adapter colonnes / actions / fil d'Ariane.
    private SourceObservations source;

    @FXML
    private VBox racine;

    @FXML
    private Label lblResume;

    @FXML
    private ComboBox<StatutObservation> choixFiltre;

    @FXML
    private MenuButton menuActions;

    @FXML
    private MenuItem itemImporter;

    @FXML
    private CheckMenuItem itemInclureMode;

    @FXML
    private MenuItem itemExporterVu;

    @FXML
    private MenuItem itemExporterBiblio;

    @FXML
    private TableView<LigneObservationAudio> tableObservations;

    @FXML
    private TableColumn<LigneObservationAudio, String> colTadarida;

    @FXML
    private TableColumn<LigneObservationAudio, String> colProba;

    @FXML
    private TableColumn<LigneObservationAudio, String> colObservateur;

    @FXML
    private TableColumn<LigneObservationAudio, String> colPassage;

    @FXML
    private TableColumn<LigneObservationAudio, String> colCarre;

    @FXML
    private TableColumn<LigneObservationAudio, String> colPoint;

    @FXML
    private TableColumn<LigneObservationAudio, String> colStatut;

    @FXML
    private TableColumn<LigneObservationAudio, String> colReference;

    @FXML
    private Label lblVide;

    @FXML
    private Label lblDetail;

    @FXML
    private AudioView audioView;

    @FXML
    private ComboBox<ModeRevue> choixMode;

    @FXML
    private Button btnValider;

    @FXML
    private ComboBox<Taxon> choixTaxon;

    @FXML
    private Button btnCorriger;

    @FXML
    private Button btnReference;

    @FXML
    private HBox bandeauRetour;

    @FXML
    private Label lblMessage;

    @FXML
    private Button btnFermerRetour;

    @Inject
    public SonsValidationController(
            AudioViewModel viewModel,
            OuvrirSite ouvrirSite,
            OuvrirPassage ouvrirPassage,
            OuvrirAnalyse ouvrirAnalyse,
            OuvrirMultisite ouvrirMultisite) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.ouvrirAnalyse = Objects.requireNonNull(ouvrirAnalyse, "ouvrirAnalyse");
        this.ouvrirMultisite = Objects.requireNonNull(ouvrirMultisite, "ouvrirMultisite");
    }

    @FXML
    private void initialize() {
        colTadarida.setCellValueFactory(c -> new ReadOnlyStringWrapper(FormatLigneAudio.tadarida(c.getValue())));
        colProba.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                FormatLigneAudio.probabilite(c.getValue().probTadarida())));
        colObservateur.setCellValueFactory(c -> new ReadOnlyStringWrapper(FormatLigneAudio.votreTaxon(c.getValue())));
        colPassage.setCellValueFactory(
                c -> new ReadOnlyStringWrapper("N°" + c.getValue().numeroPassage()));
        colCarre.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(ouTiret(c.getValue().numeroCarre())));
        colPoint.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(ouTiret(c.getValue().codePoint())));
        colStatut.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                FormatLigneAudio.libelleStatut(c.getValue().statut())));
        colReference.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(c.getValue().reference() ? "⭐" : ""));

        tableObservations.setItems(viewModel.observationsFiltrees());
        tableObservations
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, ancienne, nouvelle) ->
                        viewModel.selectionProperty().set(nouvelle));

        choixFiltre.getItems().add(null);
        choixFiltre.getItems().addAll(StatutObservation.values());
        choixFiltre.setConverter(libelleConverter(
                statut -> statut == null ? "Tous les statuts" : FormatLigneAudio.libelleStatut(statut)));
        choixFiltre.valueProperty().bindBidirectional(viewModel.filtreStatutProperty());

        lblDetail.textProperty().bind(viewModel.detailProperty());
        lblResume.textProperty().bind(Bindings.createStringBinding(this::resumeTexte, viewModel.comptageProperty()));

        // Vue audio (composant fourni, E7.S3) : la source suit l'observation sélectionnée ; le clip est
        // libéré quand la vue quitte la scène. Deux normalisations activées, complémentaires : celle du
        // NIVEAU à la lecture (#109) pour égaliser le volume d'un cri à l'autre, et la VISUELLE du
        // sonogramme (audio-view 1.13) pour que la forme d'onde des cris faibles remplisse la gouttière
        // au lieu de rester plate.
        audioView.setNormalisation(true);
        audioView.setWaveNormalisation(true);
        audioView.audioFileProperty().bind(viewModel.cheminAudioCourantProperty());
        audioView.sceneProperty().addListener((obs, avant, scene) -> {
            if (scene == null) {
                audioView.dispose();
            }
        });

        choixMode.getItems().setAll(ModeRevue.values());
        choixMode.setConverter(libelleConverter(mode -> mode == null ? "" : libelleMode(mode)));
        choixMode.valueProperty().bindBidirectional(viewModel.modeRevueProperty());

        choixTaxon.setItems(viewModel.taxons());
        choixTaxon.setConverter(libelleConverter(taxon -> taxon == null ? "" : libelleTaxon(taxon)));

        btnValider.disableProperty().bind(viewModel.selectionPresenteProperty().not());
        btnCorriger
                .disableProperty()
                .bind(viewModel
                        .selectionPresenteProperty()
                        .not()
                        .or(choixTaxon.valueProperty().isNull()));
        btnReference
                .disableProperty()
                .bind(viewModel.selectionPresenteProperty().not());
        // Libellé de la bascule selon l'état de l'observation sélectionnée (marquer vs retirer).
        btnReference
                .textProperty()
                .bind(Bindings.when(viewModel.selectionReferenceProperty())
                        .then("☆ Retirer la référence")
                        .otherwise("⭐ Marquer référence"));

        // Workflow Tadarida (source ParPassage) : toujours actif ; « Importer » tant qu'aucun résultat,
        // « Réimporter » (remplacement après confirmation) une fois un jeu chargé.
        itemImporter
                .textProperty()
                .bind(Bindings.when(viewModel.resultatsDisponiblesProperty())
                        .then("🔁 Réimporter un CSV Tadarida…")
                        .otherwise("📥 Importer un CSV Tadarida…"));
        itemExporterVu
                .disableProperty()
                .bind(viewModel.resultatsDisponiblesProperty().not());
        // Inclure (ou non) la colonne validation_mode dans l'export _Vu (R24), coché par défaut.
        itemInclureMode.selectedProperty().bindBidirectional(viewModel.inclureModeProperty());

        // État vide : placeholder gris superposé à la table, réservé au seul « aucune observation… ».
        var listeVide = Bindings.isEmpty(viewModel.observationsFiltrees());
        lblVide.textProperty().bind(viewModel.messageProperty());
        lblVide.visibleProperty().bind(listeVide);
        lblVide.managedProperty().bind(listeVide);

        // Bandeau de retour d'opération (import / export / valider / corriger) : libellé, visibilité,
        // couleur de sévérité et croix de fermeture, décorrélés de l'état vide pour qu'une erreur d'import
        // ne soit plus noyée dans le placeholder gris. Câblage isolé dans BandeauRetour.
        BandeauRetour.installer(
                bandeauRetour, lblMessage, btnFermerRetour, viewModel.retourProperty(), viewModel::effacerRetour);

        // Glisser-déposer d'un CSV Tadarida sur l'écran : alternative au FileChooser natif (qui coince
        // parfois en devcontainer / bureau distant). Actif seulement pour la source workflow (ParPassage).
        DepotFichier.installer(racine, () -> source != null && source.permetWorkflowTadarida(), this::deposerFichiers);
    }

    /// Importe le **premier** fichier glissé-déposé sur l'écran (workflow Tadarida). Délègue à
    /// [ImportTadarida] (import, ou réimport avec confirmation si un jeu existe déjà) et propage son
    /// résultat réel : `true` seulement si un import a abouti, pour ne pas marquer le dépôt complété
    /// quand l'utilisateur annule le remplacement ou que l'import échoue.
    boolean deposerFichiers(List<File> fichiers) {
        if (fichiers.isEmpty()) {
            return false;
        }
        return ImportTadarida.lancer(viewModel, fichiers.get(0).toPath());
    }

    /// Ouvre la vue audio sur `source`, en adaptant colonnes, actions et fil d'Ariane. Appelée par
    /// [NavigationAudio] après le chargement du FXML.
    public void ouvrirSur(SourceObservations source) {
        ouvrirSur(source, null);
    }

    /// Comme [#ouvrirSur(SourceObservations)] mais **pré-sélectionne** l'observation `idObservationCible`
    /// (si non nulle) une fois la table chargée, ce qui déclenche l'écoute de sa séquence. Le filtre de
    /// statut est remis à zéro avant le ciblage pour que la détection visée soit visible.
    public void ouvrirSur(SourceObservations source, Long idObservationCible) {
        this.source = Objects.requireNonNull(source, "source");
        adapterAffichage(source);
        viewModel.ouvrirSur(source);
        if (idObservationCible != null) {
            viewModel.filtreStatutProperty().set(null);
            selectionnerObservation(idObservationCible);
        }
    }

    /// Adapte l'affichage à la source : colonnes de contexte masquées si la source est un unique passage,
    /// items du menu « ☰ » et visibilité du menu selon les capacités de la source.
    private void adapterAffichage(SourceObservations source) {
        boolean passageUnique = source instanceof SourceObservations.ParPassage;
        colPassage.setVisible(!passageUnique);
        colCarre.setVisible(!passageUnique);
        colPoint.setVisible(!passageUnique);

        boolean workflow = source.permetWorkflowTadarida();
        boolean biblio = source.permetExportBibliotheque();
        itemImporter.setVisible(workflow);
        itemInclureMode.setVisible(workflow);
        itemExporterVu.setVisible(workflow);
        itemExporterBiblio.setVisible(biblio);
        menuActions.setVisible(workflow || biblio);
        menuActions.setManaged(workflow || biblio);
    }

    private void selectionnerObservation(Long idObservation) {
        for (LigneObservationAudio ligne : viewModel.observationsFiltrees()) {
            if (idObservation.equals(ligne.idObservation())) {
                tableObservations.getSelectionModel().select(ligne);
                tableObservations.scrollTo(ligne);
                return;
            }
        }
    }

    /// Emplacement dans le fil d'Ariane, **piloté par la source** : pour `ParPassage`, on reconstruit les
    /// ancêtres site/passage (retour au passage via les contrats socle, comme l'ancienne validation) ;
    /// pour `References` (et, à terme, les autres sources atteintes depuis un écran parent) l'écran est
    /// autonome (segment courant seul).
    @Override
    public List<Lieu> emplacement() {
        if (source instanceof SourceObservations.ParPassage parPassage) {
            return EmplacementPassage.emplacementEnfant(
                    parPassage.contexte(), ouvrirSite, ouvrirPassage, libelleEcran());
        }
        if (source instanceof SourceObservations.ParEspece) {
            // Accueil › Espèces & observations › Écoute : [espèce] — le segment analyse rouvre l'écran.
            return List.of(Lieu.vers("Espèces & observations", ouvrirAnalyse::ouvrir), Lieu.courant(libelleEcran()));
        }
        if (source instanceof SourceObservations.ParPassages) {
            // Accueil › Carte & passages › Écoute : lot — le segment multisite rouvre la vue agrégée.
            return List.of(
                    Lieu.vers("Carte & passages", () -> ouvrirMultisite.ouvrirSurCarre(null)),
                    Lieu.courant(libelleEcran()));
        }
        return List.of(Lieu.courant(libelleEcran()));
    }

    private String libelleEcran() {
        if (source instanceof SourceObservations.References) {
            return "Sons de référence";
        }
        if (source instanceof SourceObservations.ParEspece espece) {
            return "Écoute : " + espece.libelle();
        }
        if (source instanceof SourceObservations.ParPassages lot) {
            return "Écoute : " + lot.libelle();
        }
        return "Sons & validation";
    }

    private String resumeTexte() {
        if (source == null) {
            return "";
        }
        ComptageAudio comptage = viewModel.comptageProperty().get();
        if (comptage.total() == 0) {
            return libelleEcran();
        }
        // Total + avancement de la revue (« N / T revues » = validées + corrigées sur le total).
        return libelleEcran() + " — " + comptage.total() + " observation(s) · " + comptage.progression();
    }

    @FXML
    private void valider() {
        viewModel.valider();
    }

    @FXML
    private void corriger() {
        viewModel.corriger(choixTaxon.getValue());
    }

    @FXML
    private void basculerReference() {
        viewModel.basculerReference();
    }

    /// « Importer / Réimporter un CSV Tadarida » : sélecteur de fichier natif (ouverture) puis
    /// [#lancerImport(Path)] (import, ou réimport avec confirmation si un jeu existe déjà).
    @FXML
    private void importer() {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle("Importer un CSV Tadarida (observations ou _Vu)");
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File fichier = selecteur.showOpenDialog(fenetre());
        if (fichier != null) {
            ImportTadarida.lancer(viewModel, fichier.toPath());
        }
    }

    /// « Exporter _Vu » : sélecteur de fichier natif (enregistrement) puis délégation au VM.
    @FXML
    private void exporterVu() {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle("Exporter le fichier _Vu (réinjectable)");
        selecteur.setInitialFileName("resultats_Vu.csv");
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File fichier = selecteur.showSaveDialog(fenetre());
        if (fichier != null) {
            viewModel.exporterVu(fichier.toPath());
        }
    }

    /// « Exporter la bibliothèque » : sélecteur de dossier natif puis délégation au VM (copie des sons de
    /// référence + récapitulatif CSV).
    @FXML
    private void exporterBibliotheque() {
        DirectoryChooser selecteur = new DirectoryChooser();
        selecteur.setTitle("Exporter la bibliothèque de sons de référence");
        File dossier = selecteur.showDialog(fenetre());
        if (dossier != null) {
            viewModel.exporterBibliotheque(dossier.toPath());
        }
    }

    private javafx.stage.Window fenetre() {
        return tableObservations.getScene().getWindow();
    }

    private static String ouTiret(String valeur) {
        return valeur == null || valeur.isBlank() ? "—" : valeur;
    }

    private static <T> StringConverter<T> libelleConverter(java.util.function.Function<T, String> versLibelle) {
        return new StringConverter<>() {
            @Override
            public String toString(T valeur) {
                return versLibelle.apply(valeur);
            }

            @Override
            public T fromString(String libelle) {
                return null; // ComboBox non éditables : conversion inverse inutile
            }
        };
    }

    private static String libelleTaxon(Taxon taxon) {
        String nom = taxon.nomVernaculaireFr();
        return nom == null || nom.isBlank() ? taxon.code() : taxon.code() + " (" + nom + ")";
    }

    private static String libelleMode(ModeRevue mode) {
        return switch (mode) {
            case ACTIVITE -> "Activité (une par une)";
            case INVENTAIRE -> "Inventaire (propage l'espèce)";
        };
    }
}
