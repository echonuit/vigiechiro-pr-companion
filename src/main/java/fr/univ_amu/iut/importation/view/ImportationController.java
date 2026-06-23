package fr.univ_amu.iut.importation.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.GardeQuitter;
import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.Progression;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.viewmodel.EtatImport;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel;
import fr.univ_amu.iut.importation.viewmodel.InspectionImportViewModel;
import fr.univ_amu.iut.importation.viewmodel.RattachementImportViewModel;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;
import javafx.util.converter.NumberStringConverter;

/// Controller de l'assistant **M-Import** (`Importation.fxml`).
///
/// Pur câblage (patron CM4) : lie les contrôles des 4 sections (dossier / inspection /
/// rattachement / action) aux propriétés de l'[ImportationViewModel]. Aucun accès base de données
/// ni logique métier ici (règle ArchUnit `view_sans_jdbc`) : « Parcourir » délègue à
/// [ImportationViewModel#inspecter()] ; « Importer » lance le travail lourd hors du fil JavaFX.
public class ImportationController implements GardeQuitter {

    private final ImportationViewModel viewModel;

    // TODO (M-Import) : déclarez les @FXML correspondant aux fx:id de Importation.fxml (champ dossier,
    //   section inspection, combos site/point, champs année/passage, aperçu, boutons, progression...),
    //   câblez-les à l'ImportationViewModel dans « @FXML private void initialize() » (chargement initial
    //   via viewModel.chargerSites()) et ajoutez les handlers @FXML (parcourir, importer hors-thread).
    //   Patron de référence : feature sites.
    // --solution--
    @FXML
    private TextField champDossier;

    @FXML
    private VBox sectionInspection;

    @FXML
    private Label labelJournal;

    @FXML
    private Label labelReleve;

    @FXML
    private Label labelOriginaux;

    @FXML
    private Label labelNommage;

    @FXML
    private Label labelMelange;

    @FXML
    private Label labelIncoherence;

    @FXML
    private ComboBox<Site> comboSites;

    @FXML
    private ComboBox<PointDEcoute> comboPoints;

    @FXML
    private TextField champAnnee;

    @FXML
    private TextField champPassage;

    @FXML
    private Label labelApercu;

    @FXML
    private Label labelPrefixeDiscordant;

    @FXML
    private HBox zonePassageExistant;

    @FXML
    private Label labelPassageExistant;

    @FXML
    private Button boutonNumeroLibre;

    @FXML
    private Button boutonParcourir;

    @FXML
    private Button boutonImporter;

    @FXML
    private VBox zoneProgression;

    @FXML
    private ProgressBar barreProgression;

    @FXML
    private Label labelProgression;

    @FXML
    private Label labelMessage;

    @FXML
    private Label labelStatut;

    // --end-solution--

    @Inject
    public ImportationController(ImportationViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    /// Garde de navigation : un dossier source a été choisi pour un import **préparé mais pas lancé**
    /// (état PRET). Quitter perdrait cette préparation → confirmation. L'import EN COURS, lui, est déjà
    /// bloqué par le verrou de navigation (#54) ; une fois TERMINE/ECHEC, il n'y a plus rien à perdre.
    @Override
    public boolean aSaisieNonEnregistree() {
        return viewModel.etatProperty().get() == EtatImport.PRET
                && viewModel.inspection().dossierSourceProperty().get() != null;
    }

    @Override
    public String messageConfirmationQuitter() {
        return "Un import préparé n'a pas été lancé. Quitter cet écran et abandonner cette préparation ?";
    }

    // --solution--
    @FXML
    private void initialize() {
        // La vue se lie directement aux sous-VM exposés par l'orchestrateur (inspection / rattachement),
        // plutôt qu'à des getters de délégation : la façade reste mince (cf. refonte #111).
        lierDossierEtInspection(viewModel.inspection());
        lierRattachement(viewModel.rattachement());
        lierAction();
        viewModel.chargerSites();
    }

    /// Sections 1-2 : chemin du dossier source + section d'inspection (journal, relevé, compte, nommage,
    /// avertissements #33), liées au sous-VM d'inspection.
    private void lierDossierEtInspection(InspectionImportViewModel inspection) {
        // 1. Dossier source (affichage en lecture seule du chemin choisi).
        champDossier
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> {
                            Path dossier = inspection.dossierSourceProperty().get();
                            return dossier == null ? "" : dossier.toString();
                        },
                        inspection.dossierSourceProperty()));

        // 2. Inspection : section visible une fois le dossier inspecté.
        sectionInspection.visibleProperty().bind(inspection.inspecteProperty());
        sectionInspection.managedProperty().bind(inspection.inspecteProperty());
        labelJournal
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> inspection.aUnJournalProperty().get()
                                ? "✓ Journal du capteur : "
                                        + inspection.resumeJournalProperty().get()
                                : "⚠ Aucun journal LogPR détecté",
                        inspection.aUnJournalProperty(),
                        inspection.resumeJournalProperty()));
        labelReleve
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> inspection.aUnReleveClimatiqueProperty().get()
                                ? "✓ Relevé climatique détecté"
                                : "⚠ Relevé climatique absent (R20)",
                        inspection.aUnReleveClimatiqueProperty()));
        labelOriginaux
                .textProperty()
                .bind(inspection.nombreOriginauxProperty().asString("✓ %d enregistrement(s) WAV détecté(s)"));
        labelNommage
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> "État du nommage : "
                                + libelleNommage(
                                        inspection.etatNommageProperty().get()),
                        inspection.etatNommageProperty()));
        // Avertissement « mélange » (#33) : visible seulement s'il y a un message.
        labelMelange.textProperty().bind(inspection.avertissementMelangeProperty());
        var aUnMelange = inspection.avertissementMelangeProperty().isNotEmpty();
        labelMelange.visibleProperty().bind(aUnMelange);
        labelMelange.managedProperty().bind(aUnMelange);

        labelIncoherence.textProperty().bind(inspection.avertissementIncoherenceProperty());
        var aUneIncoherence = inspection.avertissementIncoherenceProperty().isNotEmpty();
        labelIncoherence.visibleProperty().bind(aUneIncoherence);
        labelIncoherence.managedProperty().bind(aUneIncoherence);
    }

    /// Section 3 : combos site/point, champs année/n° de passage, aperçu du préfixe et avertissement de
    /// discordance (#111), liés au sous-VM de rattachement.
    private void lierRattachement(RattachementImportViewModel rattachement) {
        comboSites.setItems(rattachement.sites());
        comboSites.setConverter(convertisseur(this::libelleSite));
        comboSites.valueProperty().bindBidirectional(rattachement.siteSelectionneProperty());
        comboPoints.setItems(rattachement.points());
        comboPoints.setConverter(convertisseur(PointDEcoute::code));
        comboPoints.valueProperty().bindBidirectional(rattachement.pointSelectionneProperty());
        Bindings.bindBidirectional(
                champAnnee.textProperty(), rattachement.anneeProperty(), new NumberStringConverter("0"));
        Bindings.bindBidirectional(
                champPassage.textProperty(), rattachement.numeroPassageProperty(), new NumberStringConverter("0"));
        labelApercu.textProperty().bind(rattachement.apercuPrefixeProperty());
        // Discordance de préfixe (#111) : déjà-préfixés ne correspondant pas au rattachement (non bloquant).
        labelPrefixeDiscordant.textProperty().bind(rattachement.avertissementPrefixeProperty());
        var aDiscordance = rattachement.avertissementPrefixeProperty().isNotEmpty();
        labelPrefixeDiscordant.visibleProperty().bind(aDiscordance);
        labelPrefixeDiscordant.managedProperty().bind(aDiscordance);
    }

    /// Section 4 : pendant l'import (EN_COURS), la barre de progression s'affiche (avancement réel
    /// fichier X/N, #33) et le formulaire est gelé ; câble aussi l'avertissement de doublon (#108) et les
    /// messages d'erreur/statut. Tout est porté par l'orchestrateur (exécution + collaborateur n° passage).
    private void lierAction() {
        var enCours = viewModel.etatProperty().isEqualTo(EtatImport.EN_COURS);
        zoneProgression.visibleProperty().bind(enCours);
        zoneProgression.managedProperty().bind(enCours);
        barreProgression.progressProperty().bind(viewModel.progressionProperty());
        labelProgression.textProperty().bind(viewModel.messageProgressionProperty());
        boutonImporter.disableProperty().bind(viewModel.peutImporter().not().or(enCours));
        boutonParcourir.disableProperty().bind(enCours);
        comboSites.disableProperty().bind(enCours);
        comboPoints.disableProperty().bind(enCours);
        champAnnee.disableProperty().bind(enCours);
        champPassage.disableProperty().bind(enCours);
        // Pré-contrôle R5 (#108) : la zone n'apparaît qu'en cas de doublon de n° de passage (avertissement
        // non vide) ; elle porte l'avertissement + un bouton pour adopter le prochain n° libre (gelé
        // pendant l'import). Même patron que les avertissements « mélange »/« incohérence » ci-dessus.
        labelPassageExistant.textProperty().bind(viewModel.avertissementNumeroPassageProperty());
        var aUnDoublon = viewModel.avertissementNumeroPassageProperty().isNotEmpty();
        zonePassageExistant.visibleProperty().bind(aUnDoublon);
        zonePassageExistant.managedProperty().bind(aUnDoublon);
        boutonNumeroLibre.disableProperty().bind(enCours);
        labelMessage.textProperty().bind(viewModel.messageErreurProperty());
        labelStatut
                .textProperty()
                .bind(Bindings.createStringBinding(
                        this::libelleStatut, viewModel.etatProperty(), viewModel.resultatProperty()));
    }

    /// « Parcourir » : ouvre le sélecteur de dossier natif puis lance l'inspection (lecture seule).
    @FXML
    private void parcourir() {
        DirectoryChooser selecteur = new DirectoryChooser();
        selecteur.setTitle("Dossier de la nuit (carte SD ou copie sur disque)");
        File dossier = selecteur.showDialog(champDossier.getScene().getWindow());
        if (dossier != null) {
            viewModel.inspection().dossierSourceProperty().set(dossier.toPath());
            viewModel.inspecter();
        }
    }

    /// « Importer cette nuit » : exécute le travail lourd sur un **virtual thread** (Java 25) pour ne
    /// pas figer le fil JavaFX, puis applique le résultat (succès ou échec) via `Platform.runLater`.
    @FXML
    private void importer() {
        if (!viewModel.peutImporter().get()) {
            return;
        }
        var demande = viewModel.preparerImport();
        viewModel.marquerEnCours();
        // Progression (#33) : le service notifie hors-thread ; on relaie chaque point au fil JavaFX.
        Consumer<Progression> progres = p -> Platform.runLater(() -> viewModel.appliquerProgression(p));
        Thread.ofVirtual().name("import-vigiechiro").start(() -> {
            try {
                ResultatImport resultatImport = viewModel.executerImport(demande, progres);
                Platform.runLater(() -> viewModel.marquerTermine(resultatImport));
            } catch (RuntimeException echec) {
                Platform.runLater(() -> viewModel.marquerEchec(echec.getMessage()));
            }
        });
    }

    /// « Utiliser ce n° » : adopte le prochain n° de passage libre proposé par le pré-contrôle R5 (#108).
    @FXML
    private void utiliserNumeroLibre() {
        viewModel.utiliserProchainNumeroLibre();
    }

    private String libelleSite(Site site) {
        return site.nomConvivial() == null
                ? "Carré " + site.numeroCarre()
                : "Carré " + site.numeroCarre() + " — " + site.nomConvivial();
    }

    private static String libelleNommage(EtatNommage etat) {
        if (etat == null) {
            return "—";
        }
        return switch (etat) {
            case BRUT -> "fichiers bruts (seront renommés)";
            case PREFIXE -> "fichiers déjà préfixés";
            case VIDE -> "aucun fichier";
        };
    }

    private String libelleStatut() {
        if (viewModel.etatProperty().get() != EtatImport.TERMINE) {
            return "";
        }
        ResultatImport resultat = viewModel.resultatProperty().get();
        if (resultat == null) {
            return "";
        }
        return "✓ Import terminé : "
                + resultat.nombreSequences()
                + " séquence(s) produite(s) à partir de "
                + resultat.nombreOriginaux()
                + " original(aux).";
    }

    private static <T> StringConverter<T> convertisseur(Function<T, String> versTexte) {
        return new StringConverter<>() {
            @Override
            public String toString(T valeur) {
                return valeur == null ? "" : versTexte.apply(valeur);
            }

            @Override
            public T fromString(String texte) {
                return null;
            }
        };
    }
    // --end-solution--
}
