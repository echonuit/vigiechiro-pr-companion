package fr.univ_amu.iut.importation.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.view.AuDepartEcran;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.GardeQuitter;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.LibelleRetour;
import fr.univ_amu.iut.commun.view.MenuCopier;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.view.SelecteurFichierJavaFx;
import fr.univ_amu.iut.commun.view.SelecteurFichierModifiable;
import fr.univ_amu.iut.commun.view.VueCompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.importation.model.ExtracteurZip;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.SuiviFichiers;
import fr.univ_amu.iut.importation.viewmodel.EtatImport;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel;
import fr.univ_amu.iut.importation.viewmodel.InspectionImportViewModel;
import fr.univ_amu.iut.importation.viewmodel.LigneFichierImport;
import fr.univ_amu.iut.importation.viewmodel.PreferenceConservation;
import fr.univ_amu.iut.importation.viewmodel.RattachementImportViewModel;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.converter.NumberStringConverter;
import org.kordamp.ikonli.javafx.FontIcon;

/// Controller de l'assistant **M-Import** (`Importation.fxml`).
///
/// Pur câblage (patron CM4) : lie les contrôles des 4 sections (dossier / inspection /
/// rattachement / action) aux propriétés de l'[ImportationViewModel]. Aucun accès base de données
/// ni logique métier ici (règle ArchUnit `view_sans_jdbc`) : « Parcourir » délègue à
/// [ImportationViewModel#inspecter()] ; « Importer » lance le travail lourd hors du fil JavaFX.
public class ImportationController implements GardeQuitter, AuDepartEcran, ResumeStatut {

    /// Zones de la barre de statut (#1024) : l'assistant (wizard racine, sans contexte passage) ne
    /// renseigne que le **centre** (statut du wizard) et la **droite** (progression + ETA d'un traitement
    /// en cours) ; la gauche reste au défaut du chrome.
    private final ReadOnlyObjectWrapper<ZonesStatut> zonesStatut =
            new ReadOnlyObjectWrapper<>(this, "zonesStatut", ZonesStatut.VIDE);

    private final ImportationViewModel viewModel;

    /// Préférence « conserver les originaux » **partagée** (singleton) avec le ViewModel : la case s'y lie
    /// bidirectionnellement, et le ViewModel la relit au lancement de l'import.
    private final PreferenceConservation conservation;

    /// Socle « travail lourd hors fil JavaFX » (#1014, étendu #1252) : décompression et imports passent
    /// par lui plutôt que par un `Thread.ofVirtual()` maison (#1256) — progression et suivi par fichier
    /// reviennent par ses relais, l'annulation coopérative conclut par `marquerAnnule`, et les tests
    /// sont **synchrones**.
    private final ExecuteurTache executeur;

    /// Fabrique du geste « importer des transformés déjà présents » (#2258) : elle porte ses collaborateurs
    /// durables (service, sites, utilisateur, espace de travail, exécuteur), pour que le contrôleur n'en
    /// garde qu'**un** champ et reste sous le plafond `NcssCount` (cf. [FabriqueActionImportTransformes]).
    private final FabriqueActionImportTransformes fabriqueImportTransformes;

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
    private FontIcon iconeJournal;

    @FXML
    private Label labelReleve;

    @FXML
    private FontIcon iconeReleve;

    @FXML
    private Label labelOriginaux;

    @FXML
    private FontIcon iconeOriginaux;

    @FXML
    private Label labelNommage;

    @FXML
    private VBox zoneAvertissements;

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

    /// Entrée « J'ai déjà les transformés… » (#2258) : ouvre le geste qui référence un dossier de séquences
    /// déjà transformées, gelée pendant un traitement comme les autres boutons de source.
    @FXML
    private Button boutonTransformes;

    @FXML
    private Button boutonImporter;

    /// Enveloppe (non désactivée) du bouton « Importer » : porte le tooltip d'explication du blocage,
    /// qu'un Button désactivé n'affiche pas. Cf. [IndicateurBlocage] (#789).
    @FXML
    private StackPane enveloppeImporter;

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
    private VBox zoneCompteRenduImport;

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

    /// Confirmation d'action destructive : porteur partagé injectable (#1013), stub déterministe en test.
    private final ConfirmateurModifiable confirmateur = new ConfirmateurModifiable();

    /// Confirmations de l'écrasement destructif et de la nuit déjà importée (#214), déléguées à
    /// [ConfirmationsImport] avec le porteur partagé du contrôleur ([#confirmateur]).
    private final ConfirmationsImport confirmations = new ConfirmationsImport(confirmateur);

    /// Carte de confirmation du rattachement (#154, lecture seule) : carré du site + point choisi.
    private final CarteRattachement carteRattachement = new CarteRattachement();

    @Inject
    public ImportationController(
            ImportationViewModel viewModel,
            PreferenceConservation conservation,
            ExecuteurTache executeur,
            FabriqueActionImportTransformes fabriqueImportTransformes) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.conservation = Objects.requireNonNull(conservation, "conservation");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
        this.fabriqueImportTransformes = Objects.requireNonNull(fabriqueImportTransformes, "fabriqueImportTransformes");
    }

    @Override
    public ReadOnlyObjectProperty<ZonesStatut> zonesStatutProperty() {
        return zonesStatut.getReadOnlyProperty();
    }

    /// Porteur de confirmation exposé aux tests (#1013) : `confirmateur().definir(stub)`.
    ConfirmateurModifiable confirmateur() {
        return confirmateur;
    }

    /// Désignation de la source : porteur partagé injectable (#1431), double répondant en test. C'est
    /// par lui que **commence** l'import - un `DirectoryChooser` / `FileChooser` en dur y **figeait**
    /// tout test, de sorte que « Parcourir » n'était jamais cliqué : les tests posaient le dossier
    /// **directement sur le ViewModel**, en contournant l'écran.
    private final SelecteurFichierModifiable selecteur = new SelecteurFichierModifiable(
            // `this.champDossier` : le champ @FXML est déclaré plus bas (référence en avant interdite
            // dans un initialiseur). La fenêtre n'est lue qu'au clic.
            new SelecteurFichierJavaFx(() -> this.champDossier.getScene().getWindow()));

    /// Porteur de désignation exposé aux tests (#1431) : `selecteur().definir(double)`.
    SelecteurFichierModifiable selecteur() {
        return selecteur;
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
        // Présence dite par l'icône et la couleur, plus par un glyphe dans le texte (#2099, ADR 0035).
        DetailInspection.lier(
                labelJournal,
                iconeJournal,
                inspection.aUnJournalProperty(),
                Bindings.createStringBinding(
                        () -> inspection.aUnJournalProperty().get()
                                ? "Journal du capteur : "
                                        + inspection.resumeJournalProperty().get()
                                : "Aucun journal LogPR — import en mode dégradé (enregistreur déduit des"
                                        + " fichiers, paramètres limités)",
                        inspection.aUnJournalProperty(),
                        inspection.resumeJournalProperty()));
        DetailInspection.lier(
                labelReleve,
                iconeReleve,
                inspection.aUnReleveClimatiqueProperty(),
                Bindings.createStringBinding(
                        () -> inspection.aUnReleveClimatiqueProperty().get()
                                ? "Relevé climatique détecté"
                                : "Relevé climatique absent",
                        inspection.aUnReleveClimatiqueProperty()));
        DetailInspection.lierPresent(
                labelOriginaux,
                iconeOriginaux,
                inspection.nombreOriginauxProperty().asString("%d enregistrement(s) WAV détecté(s)"));
        labelNommage
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> "État du nommage : "
                                + FormatsImport.libelleNommage(
                                        inspection.etatNommageProperty().get()),
                        inspection.etatNommageProperty()));
        // Ce que l'inspection a relevé (#33, #147) : mélange d'enregistreurs, désaccord journal/fichiers,
        // nuit déjà importée. Trois libellés jusqu'ici, un compte rendu désormais - ils décrivent le même
        // dossier au même instant, et chacun joignait ses listes dans une phrase (#2050).
        inspection.avertissementsProperty().addListener((observable, avant, rendu) -> afficherAvertissements(rendu));
        afficherAvertissements(inspection.avertissementsProperty().get());

        // Table des nuits (#…) : construite par programme ([TableNuits]) et insérée dans sa zone, visible
        // seulement quand la carte contient plusieurs nuits.
        lierVisibiliteGeree(zoneNuits, inspection.plusieursNuitsProperty());
        // Table + avertissement de blocage de la numérotation multi-nuits (#801), délégués à un helper
        // dédié pour garder ce contrôleur sous le plafond de taille.
        ZoneNuits.remplir(
                zoneNuits, inspection.nuits(), viewModel.coordinationNuits().blocageProperty());
    }

    /// Section 3 : combos site/point, champs année/n° de passage, aperçu du préfixe et avertissement de
    /// discordance (#111), liés au sous-VM de rattachement.
    private void lierRattachement(RattachementImportViewModel rattachement) {
        comboSites.setItems(rattachement.sites());
        comboSites.setConverter(Convertisseurs.depuis(FormatsImport::libelleSite));
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
        // La visibilité est portée par le composant : un retour absent retire le libellé de la mise en page.
        LibelleRetour.installer(labelPrefixeDiscordant, rattachement.avertissementPrefixeProperty());
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
        // Sélecteur de colonnes (#1800) : cette table n'avait aucun menu contextuel. Pas de persistance ici,
        // l'écran d'import étant transitoire (la liste ne survit pas à l'import).
        GestionnaireColonnes.installerClicDroit(
                tableFichiers,
                GestionnaireColonnes.colonnesParDefaut(tableFichiers),
                MenuCopier.creer(
                        tableFichiers,
                        new MenuCopier.Entree<>(
                                "Nom du fichier", ligne -> ligne.nomFichier() == null ? "" : ligne.nomFichier())));
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
        boutonTransformes.disableProperty().bind(traitement);
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
        LibelleRetour.installer(labelPassageExistant, viewModel.avertissementNumeroPassageProperty());
        var aUnDoublon = Bindings.createBooleanBinding(
                () -> viewModel.avertissementNumeroPassageProperty().get().present(),
                viewModel.avertissementNumeroPassageProperty());
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
                        () -> FormatsImport.libelle(
                                viewModel.etatProperty().get(),
                                viewModel.resultatProperty().get(),
                                viewModel.resultatNuitsProperty().get()),
                        viewModel.etatProperty(),
                        viewModel.resultatProperty(),
                        viewModel.resultatNuitsProperty()));
        // Barre de statut (#1024) : statut du wizard au centre, progression + ETA à droite pendant un
        // traitement. Recomposée sur les mêmes sources que le statut, plus le message de progression.
        zonesStatut.bind(Bindings.createObjectBinding(
                () -> FormatsImport.zones(
                        viewModel.etatProperty().get(),
                        viewModel.resultatProperty().get(),
                        viewModel.resultatNuitsProperty().get(),
                        viewModel.progression().messageProperty().get()),
                viewModel.etatProperty(),
                viewModel.resultatProperty(),
                viewModel.resultatNuitsProperty(),
                viewModel.progression().messageProperty()));

        // Compte rendu d'import (#2004) : ce que le statut ne peut pas porter - doublon de nuit, fichiers
        // ignorés, cardinal des rejets, anomalies du journal. Reconstruit à chaque publication : un compte
        // rendu est immuable et publié d'un bloc.
        viewModel.compteRenduProperty().addListener((observable, avant, rendu) -> afficherCompteRenduImport(rendu));
        afficherCompteRenduImport(viewModel.compteRenduProperty().get());

        // Rapport d'import (#155) : la liste des fichiers rejetés n'apparaît que s'il y en a.
        listeRejets.setItems(viewModel.rejetsImport());
        var aDesRejets = Bindings.isNotEmpty(viewModel.rejetsImport());
        lierVisibiliteGeree(zoneRejets, aDesRejets);
    }

    /// Remplace les avertissements d'inspection affichés. Tous les détails sont montrés : ils nomment des
    /// séries, des dates et des passages, tous en petit nombre, et il n'y a pas de liste voisine vers
    /// laquelle renvoyer.
    private void afficherAvertissements(CompteRendu rendu) {
        zoneAvertissements
                .getChildren()
                .setAll(VueCompteRendu.rendre(rendu, VueCompteRendu.SANS_PLAFOND)
                        .getChildren());
        zoneAvertissements.getStyleClass().setAll(VueCompteRendu.CLASSE_RACINE);
        zoneAvertissements.setVisible(!rendu.estVide());
        zoneAvertissements.setManaged(!rendu.estVide());
    }

    /// Nombre de détails montrés par constat avant de résumer. L'écran en décide, pas le compte rendu
    /// (ADR 0031) : la place sous la barre de statut est comptée.
    private static final int DETAILS_MONTRES = 5;

    /// Remplace le compte rendu d'import affiché. On reconstruit plutôt qu'on ne met à jour : un compte
    /// rendu est immuable et publié d'un bloc, il n'y a rien à rafraîchir en place.
    private void afficherCompteRenduImport(CompteRendu rendu) {
        zoneCompteRenduImport
                .getChildren()
                .setAll(VueCompteRendu.rendre(rendu, DETAILS_MONTRES).getChildren());
        zoneCompteRenduImport.getStyleClass().setAll(VueCompteRendu.CLASSE_RACINE);
        zoneCompteRenduImport.setVisible(!rendu.estVide());
        zoneCompteRenduImport.setManaged(!rendu.estVide());
    }

    /// « Parcourir » : demande le **dossier** de la nuit puis charge la source.
    @FXML
    private void parcourir() {
        selecteur
                .choisirDossier("Dossier de la nuit (carte SD ou copie sur disque)", Optional.empty())
                .ifPresent(this::chargerSource);
    }

    /// « J'ai déjà les transformés… » (#2258) : délègue à [ActionImportTransformes] (composition de
    /// dialogues existants, exécution hors fil JavaFX). Le contrôleur ne fait que **construire** l'action
    /// avec ses collaborateurs de dialogue déjà en place et la lancer, comme `PassageController` délègue à
    /// `ActionReactivation`.
    @FXML
    private void importerTransformes() {
        fabriqueImportTransformes
                .creer(() -> champDossier.getScene().getWindow(), selecteur, viewModel::chargerSites)
                .importer();
    }

    /// « Choisir un .zip » : demande l'**archive** de la nuit puis charge la source (elle sera
    /// décompressée de façon transparente, #139).
    @FXML
    private void parcourirZip() {
        selecteur
                .choisirFichier("Archive .zip de la nuit", Optional.empty(), FiltreFichier.archiveZip())
                .ifPresent(this::chargerSource);
    }

    /// Charge une source d'import (dossier **ou** `.zip`, #139) : la décompression éventuelle du zip se
    /// fait **hors du fil JavaFX** (socle #1256) pour ne pas figer l'IHM sur une grosse archive ; le
    /// résultat est ensuite inspecté sur le fil JavaFX. Une archive illisible est signalée à la vue,
    /// une décompression annulée revient à l'état neutre (#146).
    ///
    /// Pour un `.zip`, l'état passe à `EXTRACTION` **avant** de démarrer (la barre de progression apparaît
    /// aussitôt), puis chaque fichier décompressé fait avancer la barre « X / N » (#146).
    private void chargerSource(Path chemin) {
        JetonAnnulation jeton = JetonAnnulation.neutre();
        if (ExtracteurZip.estZip(chemin)) {
            jetonCourant = jeton; // permet d'annuler la décompression (#146)
            viewModel.marquerExtractionEnCours(); // fil JavaFX : progression visible dès le clic/dépôt
        }
        Consumer<Progression> progres =
                executeur.relaisProgression(p -> viewModel.progression().appliquer(p));
        executeur.executer(
                () -> viewModel.extraireSiZip(chemin, progres, jeton),
                dossier -> {
                    viewModel.inspection().dossierSourceProperty().set(dossier);
                    viewModel.inspecter();
                },
                viewModel::marquerAnnule,
                echec -> viewModel.signalerSourceIllisible(echec.getMessage()));
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

    /// « Importer cette nuit » : exécute le travail lourd **hors du fil JavaFX** (socle #1256), puis
    /// applique le résultat (succès / annulation / échec) sur le fil JavaFX.
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
                viewModel.inspection().questionNuitDejaImportee())) {
            return;
        }
        // Carte laissée tourner plusieurs nuits (#…) : un passage par nuit incluse (chemin multi-nuits),
        // sinon l'import mono-nuit historique.
        if (viewModel.inspection().plusieursNuits()) {
            lancerImportNuitsHorsFil();
        } else {
            lancerImportHorsFil(viewModel.execution()::executer);
        }
    }

    /// « Écraser et réimporter » (#214) : quand le n° de passage choisi est déjà pris, l'utilisateur peut
    /// **remplacer** le passage existant. Destructif → **double confirmation** (choix puis suppression
    /// définitive de N séquences) avant de supprimer (cascade) et réimporter hors-thread.
    @FXML
    private void ecraserEtReimporter() {
        // Disponible seulement si le n° choisi est déjà pris (avertissement R5 non vide, #108) et une nuit
        // est inspectée. L'avertissement sert de signal de doublon, déjà exposé à la vue.
        if (!viewModel.avertissementNumeroPassageProperty().get().present()
                || !viewModel.inspection().estInspecte()) {
            return;
        }
        if (!confirmations.confirmerEcrasement(viewModel.controleNumero().apercuEcrasement())) {
            return;
        }
        lancerImportHorsFil(viewModel.controleNumero()::ecraserEtImporter);
    }

    /// Lance `execution` **hors du fil JavaFX** (socle #1256), puis applique le résultat sur le fil
    /// JavaFX : succès (`marquerTermine`), annulation coopérative (#146 → `marquerAnnule`, via
    /// [fr.univ_amu.iut.commun.model.OperationAnnuleeException]) ou échec (`marquerEchec`). Partagé par
    /// l'import normal et l'écrasement (#214).
    private void lancerImportHorsFil(ExecuteurImport execution) {
        var demande = viewModel.preparerImport();
        viewModel.marquerEnCours();
        JetonAnnulation jeton = new JetonAnnulation();
        jetonCourant = jeton;
        // Progression (#33) et suivi par fichier (#947) : le service notifie hors-thread, chaque
        // événement est relayé au fil JavaFX par le socle.
        Consumer<Progression> progres =
                executeur.relaisProgression(p -> viewModel.progression().appliquer(p));
        SuiviFichiers suivi = new RelaisSuiviFichiers(viewModel.suiviFichiers(), executeur.surFilJavaFx());
        executeur.executer(
                () -> execution.executer(demande, progres, jeton, suivi),
                viewModel::marquerTermine,
                viewModel::marquerAnnule,
                echec -> viewModel.marquerEchec(echec.getMessage()));
    }

    /// Variante **multi-nuits** de [#lancerImportHorsFil] : un passage par nuit incluse, avec progression
    /// agrégée (« Nuit i/N · … ») et annulation entre deux nuits. Même enveloppe socle, résultat agrégé.
    private void lancerImportNuitsHorsFil() {
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
                executeur.relaisProgression(p -> viewModel.progression().appliquer(p));
        SuiviFichiers suivi = new RelaisSuiviFichiers(viewModel.suiviFichiers(), executeur.surFilJavaFx());
        executeur.executer(
                () -> viewModel.coordinationNuits().executer(demande, progres, jeton, suivi),
                viewModel::marquerTermineNuits,
                viewModel::marquerAnnule,
                echec -> viewModel.marquerEchec(echec.getMessage()));
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
}
