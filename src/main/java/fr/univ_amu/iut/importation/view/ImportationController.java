package fr.univ_amu.iut.importation.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.AuDepartEcran;
import fr.univ_amu.iut.commun.view.GardeQuitter;
import fr.univ_amu.iut.importation.model.AnnulationImportException;
import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.ExtracteurZip;
import fr.univ_amu.iut.importation.model.JetonAnnulation;
import fr.univ_amu.iut.importation.model.Progression;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.StatutImportFichier;
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
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import javafx.util.converter.NumberStringConverter;

/// Controller de l'assistant **M-Import** (`Importation.fxml`).
///
/// Pur câblage (patron CM4) : lie les contrôles des 4 sections (dossier / inspection /
/// rattachement / action) aux propriétés de l'[ImportationViewModel]. Aucun accès base de données
/// ni logique métier ici (règle ArchUnit `view_sans_jdbc`) : « Parcourir » délègue à
/// [ImportationViewModel#inspecter()] ; « Importer » lance le travail lourd hors du fil JavaFX.
public class ImportationController implements GardeQuitter, AuDepartEcran {

    /// Classe CSS du retour visuel de glisser-déposer (#139), posée sur la racine pendant le survol.
    private static final String CLASSE_ZONE_DEPOT_ACTIVE = "zone-depot-active";

    private final ImportationViewModel viewModel;

    @FXML
    private VBox racineImport;

    @FXML
    private TextField champDossier;

    @FXML
    private Button boutonZip;

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
    private Label labelNuitExistante;

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
    private Button boutonEcraser;

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

    @FXML
    private Button boutonAnnuler;

    @FXML
    private VBox zoneRejets;

    @FXML
    private ListView<String> listeRejets;

    /// Jeton d'annulation (#146) de l'opération longue **en cours** (décompression ou import), créé au
    /// lancement et déclenché par le bouton « Annuler ». `null` hors traitement. Accédé uniquement sur le
    /// fil JavaFX (lancement + clic « Annuler ») ; le travail hors-thread reçoit le jeton en paramètre.
    private JetonAnnulation jetonCourant;

    /// Confirmateur de l'écrasement destructif (#214). Par défaut une boîte de dialogue de confirmation ;
    /// **injectable** pour les tests (la double confirmation se vérifie alors sans dialogue natif),
    /// comme le `ConfirmateurQuitter` du Navigateur. Reçoit le message, renvoie `true` si l'utilisateur
    /// confirme.
    private final ConfirmationsImport confirmations = new ConfirmationsImport(this::confirmerParDialogue);

    @Inject
    public ImportationController(ImportationViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    /// Remplace le confirmateur (#214), pour les tests (évite la boîte de dialogue native).
    void setConfirmateur(Predicate<String> confirmateur) {
        confirmations.definirConfirmateur(confirmateur);
    }

    /// Pré-sélectionne le site `idSite` dans le rattachement (raccourci « Importer une nuit » depuis
    /// la fiche d'un site). À appeler juste après le chargement FXML, une fois les sites listés.
    public void preselectionnerSite(Long idSite) {
        viewModel.preselectionnerSite(idSite);
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

    /// Départ de l'écran (#230) : si un `.zip` avait été décompressé puis abandonné (préparation jamais
    /// lancée), son dossier temporaire — potentiellement plusieurs Go — est supprimé maintenant.
    @Override
    public void auDepartEcran() {
        viewModel.nettoyerAuDepart();
    }

    @FXML
    private void initialize() {
        // La vue se lie directement aux sous-VM exposés par l'orchestrateur (inspection / rattachement),
        // plutôt qu'à des getters de délégation : la façade reste mince (cf. refonte #111).
        lierDossierEtInspection(viewModel.inspection());
        lierRattachement(viewModel.rattachement());
        lierAction();
        installerGlisserDeposer();
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
                                : "⚠ Aucun journal LogPR — import en mode dégradé (enregistreur déduit des"
                                        + " fichiers, paramètres limités)",
                        inspection.aUnJournalProperty(),
                        inspection.resumeJournalProperty()));
        labelReleve
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> inspection.aUnReleveClimatiqueProperty().get()
                                ? "✓ Relevé climatique détecté"
                                : "⚠ Relevé climatique absent",
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

        // Détection « nuit déjà importée » (#147) : même patron, visible seulement s'il y a un message.
        labelNuitExistante.textProperty().bind(inspection.avertissementNuitExistanteProperty());
        var nuitExistante = inspection.avertissementNuitExistanteProperty().isNotEmpty();
        labelNuitExistante.visibleProperty().bind(nuitExistante);
        labelNuitExistante.managedProperty().bind(nuitExistante);
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
        // Traitement en cours = import (EN_COURS) OU décompression d'un .zip (EXTRACTION, #139) : dans les
        // deux cas la barre de progression s'affiche et le formulaire est gelé.
        var traitement = viewModel
                .etatProperty()
                .isEqualTo(EtatImport.EN_COURS)
                .or(viewModel.etatProperty().isEqualTo(EtatImport.EXTRACTION));
        zoneProgression.visibleProperty().bind(traitement);
        zoneProgression.managedProperty().bind(traitement);
        barreProgression.progressProperty().bind(viewModel.progressionProperty());
        labelProgression.textProperty().bind(viewModel.messageProgressionProperty());
        boutonImporter.disableProperty().bind(viewModel.peutImporter().not().or(traitement));
        boutonParcourir.disableProperty().bind(traitement);
        boutonZip.disableProperty().bind(traitement);
        comboSites.disableProperty().bind(traitement);
        comboPoints.disableProperty().bind(traitement);
        champAnnee.disableProperty().bind(traitement);
        champPassage.disableProperty().bind(traitement);
        // Pré-contrôle R5 (#108) : la zone n'apparaît qu'en cas de doublon de n° de passage (avertissement
        // non vide) ; elle porte l'avertissement + un bouton pour adopter le prochain n° libre (gelé
        // pendant l'import). Même patron que les avertissements « mélange »/« incohérence » ci-dessus.
        labelPassageExistant.textProperty().bind(viewModel.avertissementNumeroPassageProperty());
        var aUnDoublon = viewModel.avertissementNumeroPassageProperty().isNotEmpty();
        zonePassageExistant.visibleProperty().bind(aUnDoublon);
        zonePassageExistant.managedProperty().bind(aUnDoublon);
        boutonNumeroLibre.disableProperty().bind(traitement);
        // « Écraser et réimporter » (#214) : possible seulement si une nuit est inspectée (sinon rien à
        // réimporter) et hors traitement. Visible dans la même zone que l'avertissement de doublon.
        boutonEcraser
                .disableProperty()
                .bind(traitement.or(viewModel.inspection().inspecteProperty().not()));
        labelMessage.textProperty().bind(viewModel.messageErreurProperty());
        labelStatut
                .textProperty()
                .bind(Bindings.createStringBinding(
                        this::libelleStatut, viewModel.etatProperty(), viewModel.resultatProperty()));

        // Rapport d'import (#155) : la liste des fichiers rejetés n'apparaît que s'il y en a.
        listeRejets.setItems(viewModel.rejetsImport());
        var aDesRejets = Bindings.isNotEmpty(viewModel.rejetsImport());
        zoneRejets.visibleProperty().bind(aDesRejets);
        zoneRejets.managedProperty().bind(aDesRejets);
    }

    /// « Parcourir » : ouvre le sélecteur de **dossier** natif puis charge la source.
    @FXML
    private void parcourir() {
        DirectoryChooser selecteur = new DirectoryChooser();
        selecteur.setTitle("Dossier de la nuit (carte SD ou copie sur disque)");
        File dossier = selecteur.showDialog(champDossier.getScene().getWindow());
        if (dossier != null) {
            chargerSource(dossier.toPath());
        }
    }

    /// « Choisir un .zip » : ouvre le sélecteur de **fichier** filtré sur `*.zip` puis charge la source
    /// (l'archive sera décompressée de façon transparente, #139).
    @FXML
    private void parcourirZip() {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle("Archive .zip de la nuit");
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archive ZIP", "*.zip"));
        File zip = selecteur.showOpenDialog(champDossier.getScene().getWindow());
        if (zip != null) {
            chargerSource(zip.toPath());
        }
    }

    /// Charge une source d'import (dossier **ou** `.zip`, #139) : la décompression éventuelle du zip se
    /// fait sur un **virtual thread** (Java 25) pour ne pas figer l'IHM sur une grosse archive ; le
    /// résultat est ensuite inspecté sur le fil JavaFX. Une archive illisible est signalée à la vue.
    ///
    /// Pour un `.zip`, l'état passe à `EXTRACTION` **avant** de démarrer (la barre de progression apparaît
    /// aussitôt), puis chaque fichier décompressé fait avancer la barre « X / N » (#146).
    private void chargerSource(Path chemin) {
        JetonAnnulation jeton = JetonAnnulation.neutre();
        if (ExtracteurZip.estZip(chemin)) {
            jetonCourant = jeton; // permet d'annuler la décompression (#146)
            viewModel.marquerExtractionEnCours(); // fil JavaFX : progression visible dès le clic/dépôt
        }
        Thread.ofVirtual().name("source-import-vigiechiro").start(() -> {
            try {
                Path dossier = viewModel.extraireSiZip(
                        chemin, p -> Platform.runLater(() -> viewModel.appliquerProgression(p)), jeton);
                Platform.runLater(() -> {
                    viewModel.inspection().dossierSourceProperty().set(dossier);
                    viewModel.inspecter();
                });
            } catch (AnnulationImportException annulation) {
                Platform.runLater(viewModel::marquerAnnule); // décompression annulée : retour neutre (#146)
            } catch (RuntimeException echec) {
                Platform.runLater(() -> viewModel.signalerSourceIllisible(echec.getMessage()));
            }
        });
    }

    /// Active le **glisser-déposer** (#139) d'un dossier ou d'un `.zip` sur tout l'écran d'import, avec un
    /// **retour visuel** pendant le survol (classe CSS `zone-depot-active`). Le dépôt charge la source via
    /// [#chargerSource]. Ignoré pendant un import en cours (formulaire gelé).
    private void installerGlisserDeposer() {
        racineImport.setOnDragOver(evenement -> {
            if (sourceGlisseeAcceptable(evenement.getDragboard())) {
                evenement.acceptTransferModes(TransferMode.COPY);
                if (!racineImport.getStyleClass().contains(CLASSE_ZONE_DEPOT_ACTIVE)) {
                    racineImport.getStyleClass().add(CLASSE_ZONE_DEPOT_ACTIVE);
                }
            }
            evenement.consume();
        });
        racineImport.setOnDragExited(evenement -> {
            racineImport.getStyleClass().remove(CLASSE_ZONE_DEPOT_ACTIVE);
            evenement.consume();
        });
        racineImport.setOnDragDropped(evenement -> {
            boolean accepte = sourceGlisseeAcceptable(evenement.getDragboard());
            if (accepte) {
                chargerSource(evenement.getDragboard().getFiles().get(0).toPath());
            }
            racineImport.getStyleClass().remove(CLASSE_ZONE_DEPOT_ACTIVE);
            evenement.setDropCompleted(accepte);
            evenement.consume();
        });
    }

    /// Vrai si le glisser porte un **dossier** ou un **.zip** (seules sources d'import acceptables) et
    /// qu'aucun traitement n'est en cours (import EN_COURS ou décompression EXTRACTION : le formulaire est
    /// alors gelé).
    private boolean sourceGlisseeAcceptable(Dragboard dragboard) {
        EtatImport etat = viewModel.etatProperty().get();
        if (etat == EtatImport.EN_COURS || etat == EtatImport.EXTRACTION || !dragboard.hasFiles()) {
            return false;
        }
        File premier = dragboard.getFiles().get(0);
        return premier.isDirectory() || ExtracteurZip.estZip(premier.toPath());
    }

    /// Signature d'une exécution d'import hors-thread : import normal ou écrasement (#214).
    @FunctionalInterface
    private interface ExecuteurImport {
        ResultatImport executer(
                ImportationViewModel.DemandeImport demande, Consumer<Progression> progres, JetonAnnulation jeton);
    }

    /// « Importer cette nuit » : exécute le travail lourd sur un **virtual thread** (Java 25) pour ne
    /// pas figer le fil JavaFX, puis applique le résultat (succès ou échec) via `Platform.runLater`.
    @FXML
    private void importer() {
        if (!viewModel.peutImporter().get()) {
            return;
        }
        // #214/#147 : si cette nuit a déjà été importée (avertissement non vide, possiblement à un autre
        // point/n°), on demande une confirmation explicite avant de créer un nouveau passage (« importer
        // quand même »), plutôt que de procéder en silence. L'écrasement d'un passage au quadruplet choisi
        // reste une action distincte (bouton « Écraser et réimporter », #279).
        if (!confirmations.confirmerImportNuitDejaImportee(
                viewModel.inspection().avertissementNuitExistanteProperty().get())) {
            return;
        }
        lancerImportHorsThread(viewModel::executerImport);
    }

    /// « Écraser et réimporter » (#214) : quand le n° de passage choisi est déjà pris, l'utilisateur peut
    /// **remplacer** le passage existant. Destructif → **double confirmation** (choix puis suppression
    /// définitive de N séquences) avant de supprimer (cascade) et réimporter hors-thread.
    @FXML
    private void ecraserEtReimporter() {
        // Disponible seulement si le n° choisi est déjà pris (avertissement R5 non vide, #108) et une nuit
        // est inspectée. L'avertissement sert de signal de doublon, déjà exposé à la vue.
        if (viewModel.avertissementNumeroPassageProperty().get().isEmpty()
                || !viewModel.inspection().estInspecte()) {
            return;
        }
        if (!confirmations.confirmerEcrasement(viewModel.controleNumero().compterSequencesAEcraser())) {
            return;
        }
        lancerImportHorsThread(viewModel.controleNumero()::ecraserEtImporter);
    }

    /// Lance `executeur` sur un **virtual thread** (Java 25) pour ne pas figer le fil JavaFX, puis applique
    /// le résultat (succès / annulation / échec) via `Platform.runLater`. Partagé par l'import normal et
    /// l'écrasement (#214).
    private void lancerImportHorsThread(ExecuteurImport executeur) {
        var demande = viewModel.preparerImport();
        viewModel.marquerEnCours();
        JetonAnnulation jeton = new JetonAnnulation();
        jetonCourant = jeton;
        // Progression (#33) : le service notifie hors-thread ; on relaie chaque point au fil JavaFX.
        Consumer<Progression> progres = p -> Platform.runLater(() -> viewModel.appliquerProgression(p));
        Thread.ofVirtual().name("import-vigiechiro").start(() -> {
            try {
                ResultatImport resultatImport = executeur.executer(demande, progres, jeton);
                Platform.runLater(() -> viewModel.marquerTermine(resultatImport));
            } catch (AnnulationImportException annulation) {
                Platform.runLater(viewModel::marquerAnnule); // annulation demandée : arrêt propre (#146)
            } catch (RuntimeException echec) {
                Platform.runLater(() -> viewModel.marquerEchec(echec.getMessage()));
            }
        });
    }

    /// Confirmation par boîte de dialogue native (défaut hors tests) : `true` si l'utilisateur clique OK.
    private boolean confirmerParDialogue(String message) {
        Alert alerte = new Alert(AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        return alerte.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    /// « Annuler » : demande l'arrêt de l'opération longue en cours (décompression ou import, #146). Le
    /// travail hors-thread s'arrête au prochain fichier et nettoie les fichiers partiels.
    @FXML
    private void annuler() {
        if (jetonCourant != null) {
            jetonCourant.annuler();
        }
    }

    /// « Utiliser ce n° » : adopte le prochain n° de passage libre proposé par le pré-contrôle R5 (#108).
    @FXML
    private void utiliserNumeroLibre() {
        viewModel.controleNumero().utiliserProchainNumeroLibre();
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
        if (viewModel.etatProperty().get() == EtatImport.ANNULE) {
            return "Opération annulée.";
        }
        if (viewModel.etatProperty().get() != EtatImport.TERMINE) {
            return "";
        }
        ResultatImport resultat = viewModel.resultatProperty().get();
        if (resultat == null) {
            return "";
        }
        String base = "✓ Import terminé : "
                + resultat.nombreSequences()
                + " séquence(s) produite(s) à partir de "
                + resultat.nombreOriginaux()
                + " original(aux).";
        // Rapport d'import (#155) : on signale les fichiers rejetés, le cas échéant.
        long rejetes = resultat.rapport().compte(StatutImportFichier.REJETE);
        return rejetes == 0 ? base : base + " ⚠ " + rejetes + " fichier(s) rejeté(s) — détail ci-dessous.";
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
}
