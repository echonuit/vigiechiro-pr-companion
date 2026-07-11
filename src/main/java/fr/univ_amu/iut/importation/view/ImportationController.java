package fr.univ_amu.iut.importation.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.view.AuDepartEcran;
import fr.univ_amu.iut.commun.view.GardeQuitter;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.importation.model.AnnulationImportException;
import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.ExtracteurZip;
import fr.univ_amu.iut.importation.model.JetonAnnulation;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;
import fr.univ_amu.iut.importation.model.SuiviFichiers;
import fr.univ_amu.iut.importation.viewmodel.EtatImport;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel;
import fr.univ_amu.iut.importation.viewmodel.InspectionImportViewModel;
import fr.univ_amu.iut.importation.viewmodel.LigneFichierImport;
import fr.univ_amu.iut.importation.viewmodel.PreferenceConservation;
import fr.univ_amu.iut.importation.viewmodel.RattachementImportViewModel;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.converter.NumberStringConverter;

/// Controller de l'assistant **M-Import** (`Importation.fxml`).
///
/// Pur câblage (patron CM4) : lie les contrôles des 4 sections (dossier / inspection /
/// rattachement / action) aux propriétés de l'[ImportationViewModel]. Aucun accès base de données
/// ni logique métier ici (règle ArchUnit `view_sans_jdbc`) : « Parcourir » délègue à
/// [ImportationViewModel#inspecter()] ; « Importer » lance le travail lourd hors du fil JavaFX.
public class ImportationController implements GardeQuitter, AuDepartEcran {

    private final ImportationViewModel viewModel;

    /// Préférence « conserver les originaux » **partagée** (singleton) avec le ViewModel : la case s'y lie
    /// bidirectionnellement, et le ViewModel la relit au lancement de l'import.
    private final PreferenceConservation conservation;

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
    private StackPane zoneCarteRattachement;

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

    /// Enveloppe (non désactivée) du bouton « Importer » : porte le tooltip d'explication du blocage,
    /// qu'un Button désactivé n'affiche pas. Cf. [IndicateurBlocage] (#789).
    @FXML
    private StackPane enveloppeImporter;

    @FXML
    private CheckBox caseConserverOriginaux;

    @FXML
    private VBox zoneProgression;

    @FXML
    private ProgressBar barreProgression;

    @FXML
    private Label labelProgression;

    @FXML
    private TableView<LigneFichierImport> tableFichiers;

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

    @FXML
    private VBox zoneNuits;

    /// Jeton d'annulation (#146) de l'opération longue **en cours** (décompression ou import), créé au
    /// lancement et déclenché par le bouton « Annuler ». `null` hors traitement. Accédé uniquement sur le
    /// fil JavaFX (lancement + clic « Annuler ») ; le travail hors-thread reçoit le jeton en paramètre.
    private JetonAnnulation jetonCourant;

    /// Confirmateur de l'écrasement destructif (#214). Par défaut une boîte de dialogue de confirmation ;
    /// **injectable** pour les tests (la double confirmation se vérifie alors sans dialogue natif),
    /// comme le `ConfirmateurQuitter` du Navigateur. Reçoit le message, renvoie `true` si l'utilisateur
    /// confirme.
    private final ConfirmationsImport confirmations = new ConfirmationsImport(this::confirmerParDialogue);

    /// Carte de confirmation du rattachement (#154, lecture seule) : carré du site + point choisi.
    private final CarteRattachement carteRattachement = new CarteRattachement();

    @Inject
    public ImportationController(ImportationViewModel viewModel, PreferenceConservation conservation) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.conservation = Objects.requireNonNull(conservation, "conservation");
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
        // Option « conserver les originaux » : la case reflète le choix persisté (défaut : conservation
        // activée) et le met à jour dans les deux sens ; il est mémorisé au lancement de l'import.
        caseConserverOriginaux.selectedProperty().bindBidirectional(conservation.conserverOriginauxProperty());
        // Glisser-déposer (#139) d'un dossier/.zip sur tout l'écran, gelé pendant un traitement.
        GlisserDeposerImport.installer(racineImport, this::traitementEnCours, this::chargerSource);
        viewModel.chargerSites();
    }

    /// Lie la **visibilité** d'un nœud à `condition` en gardant `managed` synchronisé avec `visible` : un
    /// nœud masqué ne doit pas occuper d'espace dans la mise en page (sinon un « trou » subsisterait). Le
    /// même couple visible/managed est appliqué à tous les éléments conditionnels de l'écran.
    private static void lierVisibiliteGeree(Node noeud, ObservableValue<? extends Boolean> condition) {
        noeud.visibleProperty().bind(condition);
        noeud.managedProperty().bind(condition);
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
        lierVisibiliteGeree(sectionInspection, inspection.inspecteProperty());
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
        lierVisibiliteGeree(labelMelange, aUnMelange);

        labelIncoherence.textProperty().bind(inspection.avertissementIncoherenceProperty());
        var aUneIncoherence = inspection.avertissementIncoherenceProperty().isNotEmpty();
        lierVisibiliteGeree(labelIncoherence, aUneIncoherence);

        // Détection « nuit déjà importée » (#147) : même patron, visible seulement s'il y a un message.
        labelNuitExistante.textProperty().bind(inspection.avertissementNuitExistanteProperty());
        var nuitExistante = inspection.avertissementNuitExistanteProperty().isNotEmpty();
        lierVisibiliteGeree(labelNuitExistante, nuitExistante);

        // Table des nuits (#…) : construite par programme ([TableNuits]) et insérée dans sa zone, visible
        // seulement quand la carte contient plusieurs nuits.
        lierVisibiliteGeree(zoneNuits, inspection.plusieursNuitsProperty());
        // Table + avertissement de blocage de la numérotation multi-nuits (#801), délégués à un helper
        // dédié pour garder ce contrôleur sous le plafond de taille.
        ZoneNuits.remplir(
                zoneNuits, inspection.nuits(), viewModel.coordinationNuits().avertissementProperty());
    }

    /// Section 3 : combos site/point, champs année/n° de passage, aperçu du préfixe et avertissement de
    /// discordance (#111), liés au sous-VM de rattachement.
    private void lierRattachement(RattachementImportViewModel rattachement) {
        comboSites.setItems(rattachement.sites());
        comboSites.setConverter(Convertisseurs.depuis(this::libelleSite));
        comboSites.valueProperty().bindBidirectional(rattachement.siteSelectionneProperty());
        comboPoints.setItems(rattachement.points());
        comboPoints.setConverter(Convertisseurs.depuis(PointDEcoute::code));
        comboPoints.valueProperty().bindBidirectional(rattachement.pointSelectionneProperty());
        // Carte de confirmation (#154, lecture seule) : reflète le carré du site et le point choisi.
        zoneCarteRattachement.getChildren().add(carteRattachement.vue());
        carteRattachement.lier(rattachement);
        Bindings.bindBidirectional(
                champAnnee.textProperty(), rattachement.anneeProperty(), new NumberStringConverter("0"));
        Bindings.bindBidirectional(
                champPassage.textProperty(), rattachement.numeroPassageProperty(), new NumberStringConverter("0"));
        labelApercu.textProperty().bind(rattachement.apercuPrefixeProperty());
        // Discordance de préfixe (#111) : déjà-préfixés ne correspondant pas au rattachement (non bloquant).
        labelPrefixeDiscordant.textProperty().bind(rattachement.avertissementPrefixeProperty());
        var aDiscordance = rattachement.avertissementPrefixeProperty().isNotEmpty();
        lierVisibiliteGeree(labelPrefixeDiscordant, aDiscordance);
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
        lierVisibiliteGeree(zoneProgression, traitement);
        barreProgression.progressProperty().bind(viewModel.progression().fractionProperty());
        labelProgression.textProperty().bind(viewModel.progression().messageProperty());
        // Table de suivi par fichier (#947) : visible seulement quand un plan de nuit est établi (liaison
        // vivante sur la liste, pas un drapeau figé) — donc masquée pendant la décompression d'un .zip.
        TableSuiviFichiers.configurer(tableFichiers);
        tableFichiers.setItems(viewModel.suiviFichiers().lignes());
        lierVisibiliteGeree(
                tableFichiers, Bindings.isNotEmpty(viewModel.suiviFichiers().lignes()));
        boutonImporter.disableProperty().bind(viewModel.peutImporter().not().or(traitement));
        // Explique le grisage (#789) sur l'enveloppe (un Button désactivé n'affiche pas de tooltip). Le
        // grisage pendant l'import est déjà signalé par la zone de progression ; on nomme surtout les
        // prérequis manquants (config incomplète) quand l'import n'est pas en cours.
        IndicateurBlocage.expliquer(
                enveloppeImporter,
                Bindings.when(traitement)
                        .then("Import en cours…")
                        .otherwise(Bindings.when(viewModel.peutImporter())
                                .then("Lancer l'import de la nuit inspectée.")
                                .otherwise("Pour lancer l'import : inspectez un dossier, choisissez le site"
                                        + " et le point, et renseignez un numéro de passage valide.")));
        boutonParcourir.disableProperty().bind(traitement);
        boutonZip.disableProperty().bind(traitement);
        comboSites.disableProperty().bind(traitement);
        // Désactivation guidée (#800) : choisir le point n'a de sens qu'une fois le site choisi (les points
        // dépendent du site). Le promptText « Choisissez d'abord un site » explique alors le grisage.
        comboPoints
                .disableProperty()
                .bind(traitement.or(comboSites.valueProperty().isNull()));
        champAnnee.disableProperty().bind(traitement);
        champPassage.disableProperty().bind(traitement);
        // Pré-contrôle R5 (#108) : la zone n'apparaît qu'en cas de doublon de n° de passage (avertissement
        // non vide) ; elle porte l'avertissement + un bouton pour adopter le prochain n° libre (gelé
        // pendant l'import). Même patron que les avertissements « mélange »/« incohérence » ci-dessus.
        labelPassageExistant.textProperty().bind(viewModel.avertissementNumeroPassageProperty());
        var aUnDoublon = viewModel.avertissementNumeroPassageProperty().isNotEmpty();
        lierVisibiliteGeree(zonePassageExistant, aUnDoublon);
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
                        this::libelleStatut,
                        viewModel.etatProperty(),
                        viewModel.resultatProperty(),
                        viewModel.resultatNuitsProperty()));

        // Rapport d'import (#155) : la liste des fichiers rejetés n'apparaît que s'il y en a.
        listeRejets.setItems(viewModel.rejetsImport());
        var aDesRejets = Bindings.isNotEmpty(viewModel.rejetsImport());
        lierVisibiliteGeree(zoneRejets, aDesRejets);
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
                        chemin,
                        p -> Platform.runLater(() -> viewModel.progression().appliquer(p)),
                        jeton);
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

    /// Vrai si un traitement est en cours (import `EN_COURS` ou décompression `EXTRACTION`) : le formulaire
    /// est alors gelé (utilisé pour ignorer un glisser-déposer, #139).
    private boolean traitementEnCours() {
        EtatImport etat = viewModel.etatProperty().get();
        return etat == EtatImport.EN_COURS || etat == EtatImport.EXTRACTION;
    }

    /// Signature d'une exécution d'import hors-thread : import normal ou écrasement (#214).
    @FunctionalInterface
    private interface ExecuteurImport {
        ResultatImport executer(
                ImportationViewModel.DemandeImport demande,
                Consumer<Progression> progres,
                JetonAnnulation jeton,
                SuiviFichiers suivi);
    }

    /// « Importer cette nuit » : exécute le travail lourd sur un **virtual thread** (Java 25) pour ne
    /// pas figer le fil JavaFX, puis applique le résultat (succès ou échec) via `Platform.runLater`.
    @FXML
    private void importer() {
        if (!viewModel.peutImporter().get()) {
            return;
        }
        // #214/#147 : si cette nuit a déjà été importée (possiblement à un autre point/n°), on demande une
        // confirmation explicite avant de créer un nouveau passage (« importer quand même »), plutôt que de
        // procéder en silence. La détection est **rafraîchie ici** : la base a pu changer depuis
        // l'inspection (un import précédent vient d'aboutir), sinon réimporter la même nuit sur un n° libre
        // passerait sans confirmation. L'écrasement d'un passage au quadruplet choisi reste distinct (#279).
        viewModel.inspection().rafraichirNuitExistante();
        if (!confirmations.confirmerImportNuitDejaImportee(
                viewModel.inspection().avertissementNuitExistanteProperty().get())) {
            return;
        }
        // Carte laissée tourner plusieurs nuits (#…) : un passage par nuit incluse (chemin multi-nuits),
        // sinon l'import mono-nuit historique.
        if (viewModel.inspection().plusieursNuits()) {
            lancerImportNuitsHorsThread();
        } else {
            lancerImportHorsThread(viewModel.execution()::executer);
        }
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
        if (!confirmations.confirmerEcrasement(viewModel.controleNumero().apercuEcrasement())) {
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
        Consumer<Progression> progres =
                p -> Platform.runLater(() -> viewModel.progression().appliquer(p));
        SuiviFichiers suivi = new RelaisSuiviFichiers(viewModel.suiviFichiers());
        surVirtualThread("import-vigiechiro", () -> {
            ResultatImport resultatImport = executeur.executer(demande, progres, jeton, suivi);
            Platform.runLater(() -> viewModel.marquerTermine(resultatImport));
        });
    }

    /// Variante **multi-nuits** de [#lancerImportHorsThread] (#…) : un passage par nuit incluse, sur un
    /// virtual thread, avec progression agrégée (« Nuit i/N · … ») et annulation entre deux nuits. Applique
    /// le résultat agrégé via `Platform.runLater`.
    private void lancerImportNuitsHorsThread() {
        // Mémorise le choix « conserver les originaux » (survit aux sessions) au lancement, puis capture les
        // nuits incluses + leurs n° dans un instantané immuable (fil JavaFX).
        conservation.memoriser();
        var demande = viewModel
                .coordinationNuits()
                .preparerDemande(viewModel.inspection().dossier(), conservation.valeur());
        viewModel.marquerEnCours();
        JetonAnnulation jeton = new JetonAnnulation();
        jetonCourant = jeton;
        Consumer<Progression> progres =
                p -> Platform.runLater(() -> viewModel.progression().appliquer(p));
        SuiviFichiers suivi = new RelaisSuiviFichiers(viewModel.suiviFichiers());
        surVirtualThread("import-nuits-vigiechiro", () -> {
            ResultatImportMultiNuits resultat = viewModel.coordinationNuits().executer(demande, progres, jeton, suivi);
            Platform.runLater(() -> viewModel.marquerTermineNuits(resultat));
        });
    }

    /// Exécute `travail` sur un **virtual thread** (Java 25) nommé `nom`, en traitant de façon uniforme
    /// l'**annulation** (#146 → `marquerAnnule`) et l'**échec** (`marquerEchec`) via `Platform.runLater`.
    /// Mutualise l'enveloppe des deux lanceurs (mono-nuit et multi-nuits) ; `travail` poste lui-même son
    /// succès (`marquerTermine`/`marquerTermineNuits`) sur le fil JavaFX.
    private void surVirtualThread(String nom, Runnable travail) {
        Thread.ofVirtual().name(nom).start(() -> {
            try {
                travail.run();
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

    /// Statut affiché sous le bouton « Importer » : issue de l'import (annulé / mono-nuit / multi-nuits),
    /// mise en phrase par [RecapImport] à partir de l'état et du (des) résultat(s) courant(s).
    private String libelleStatut() {
        return RecapImport.libelle(
                viewModel.etatProperty().get(),
                viewModel.resultatProperty().get(),
                viewModel.resultatNuitsProperty().get());
    }
}
