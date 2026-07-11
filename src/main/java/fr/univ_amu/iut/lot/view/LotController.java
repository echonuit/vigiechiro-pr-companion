package fr.univ_amu.iut.lot.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.ArchivePlanifiee;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.ControleCoherence;
import fr.univ_amu.iut.lot.model.StatutControle;
import fr.univ_amu.iut.lot.model.SuiviArchives;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.EtapeDepot;
import fr.univ_amu.iut.lot.viewmodel.LigneArchive;
import fr.univ_amu.iut.lot.viewmodel.LigneDepot;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/// Controller de l'écran **M-Lot** (`Lot.fxml`).
///
/// Pur câblage (patron CM4) : lie le récapitulatif, le dossier à téléverser, les alertes de cohérence
/// (R14) et les deux actions du dépôt au [LotViewModel]. « Préparer le lot » et « Marquer déposé » ne
/// sont actifs que dans l'état workflow adéquat ; la zone d'alertes n'apparaît qu'en présence d'alertes
/// bloquantes. Aucun accès base de données ni logique métier ici (règle ArchUnit `view_sans_jdbc`).
///
/// Implémente [ResumeStatut] (#693) : le statut du workflow, jusqu'ici en sous-titre d'en-tête, est
/// déporté en barre de statut (le titre « Préparer le dépôt » étant redondant avec le fil d'Ariane).
public class LotController implements EmplacementNavigation, ResumeStatut {

    private final LotViewModel viewModel;
    private final DepotViewModel depotViewModel;

    /// Chrome : pour signaler une **opération critique** en cours (génération d'archives, dépôt) et faire
    /// avertir avant de quitter/fermer (#906).
    private final NavigationViewModel navigation;

    private final OuvrirSite ouvrirSite;
    private final OuvrirPassage ouvrirPassage;

    /// Ouvre le sous-dossier `depot/` dans le gestionnaire de fichiers du système (#251) : le dépôt étant
    /// manuel, on amène l'observateur au bon endroit. Abstrait pour rester testable (faux en tête).
    private final OuvreurDeLien ouvreurDeLien;

    /// Contexte de navigation (passage + site), mémorisé pour reconstruire le fil d'Ariane du chrome.
    private ContextePassage contexte;

    /// Statut du workflow, déporté en zone centre de la barre de statut (#693) au lieu d'un sous-titre.
    private final ReadOnlyObjectWrapper<ZonesStatut> zonesStatut =
            new ReadOnlyObjectWrapper<>(this, "zonesStatut", ZonesStatut.VIDE);

    /// Confirmation d'une action destructive (suppression des archives). **Injectable** (patron #214) :
    /// par défaut un dialogue natif, remplacé en test par un prédicat pour éviter un `showAndWait` bloquant.
    private Predicate<String> confirmateur = this::confirmerParDialogue;

    @FXML
    private Label lblRecap;

    @FXML
    private HBox stepper;

    @FXML
    private Label lblCheminDepot;

    @FXML
    private VBox checklist;

    @FXML
    private Button btnPreparer;

    @FXML
    private Button btnDeposer;

    /// Enveloppes (non désactivées) des boutons d'étape : portent le tooltip d'explication du blocage,
    /// qu'un Button désactivé n'affiche pas. Cf. [IndicateurBlocage] (#789).
    @FXML
    private StackPane enveloppePreparer;

    @FXML
    private StackPane enveloppeDeposer;

    @FXML
    private Label lblTitreArchives;

    @FXML
    private Button btnGenererArchives;

    @FXML
    private ProgressBar barreGeneration;

    @FXML
    private Label lblProgressionGeneration;

    @FXML
    private Label lblEspaceInsuffisant;

    @FXML
    private TableView<LigneArchive> tableArchives;

    @FXML
    private MenuButton menuOutils;

    @FXML
    private StackPane enveloppeTeleverser;

    @FXML
    private Button btnTeleverser;

    @FXML
    private Label lblDepotMessage;

    @FXML
    private TableView<LigneDepot> tableDepot;

    @FXML
    private StackPane enveloppeOuvrirDepot;

    @FXML
    private Button btnOuvrirDepot;

    @FXML
    private Button btnSupprimerArchives;

    @FXML
    private Label lblMessage;

    @Inject
    public LotController(
            LotViewModel viewModel,
            DepotViewModel depotViewModel,
            NavigationViewModel navigation,
            OuvrirSite ouvrirSite,
            OuvrirPassage ouvrirPassage,
            OuvreurDeLien ouvreurDeLien) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.depotViewModel = Objects.requireNonNull(depotViewModel, "depotViewModel");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.ouvreurDeLien = Objects.requireNonNull(ouvreurDeLien, "ouvreurDeLien");
    }

    @Override
    public ReadOnlyObjectProperty<ZonesStatut> zonesStatutProperty() {
        return zonesStatut.getReadOnlyProperty();
    }

    /// Reflète l'opération critique en cours du lot sur le chrome (#906) : génération d'archives, puis
    /// dépôt, sinon rien. Un état non vide fait avertir le [fr.univ_amu.iut.commun.view.Navigateur] / `App`
    /// avant une sortie d'écran ou une fermeture.
    private void majOperationCritique() {
        if (viewModel.generationEnCoursProperty().get()) {
            navigation.setOperationCritique("la génération des archives");
        } else if (depotViewModel.enCoursProperty().get()) {
            navigation.setOperationCritique("le dépôt");
        } else {
            navigation.setOperationCritique("");
        }
    }

    @FXML
    private void initialize() {
        // Opération critique en cours (#906) : la génération d'archives et le dépôt sont des tâches longues
        // qu'on ne doit pas abandonner en silence. On pose leur libellé sur le chrome (qui avertit avant de
        // quitter/fermer) et on l'efface à leur fin. Les écouteurs sont posés sur les propriétés des VM
        // (durables), pas sur une liaison locale, pour que l'effacement survienne même après une sortie.
        viewModel.generationEnCoursProperty().addListener((obs, avant, apres) -> majOperationCritique());
        depotViewModel.enCoursProperty().addListener((obs, avant, apres) -> majOperationCritique());
        majOperationCritique();

        // Statut du workflow déporté en zone centre de la barre de statut (#693), plus de sous-titre.
        zonesStatut.bind(Bindings.createObjectBinding(
                () -> ZonesStatut.centre(viewModel.statutProperty().get()), viewModel.statutProperty()));
        lblRecap.textProperty().bind(viewModel.recapProperty());
        // Étape ③ : la cible du téléversement est le sous-dossier depot/ (archives ZIP), pas la session.
        lblCheminDepot.textProperty().bind(viewModel.cheminDepotProperty());

        // Stepper du dépôt (#251), reconstruit à chaque changement d'étapes (mêmes styles que M-Passage).
        viewModel.etapes().addListener((ListChangeListener<EtapeDepot>) changement -> majStepper());
        majStepper();

        // Checklist de cohérence (#254), reconstruite à chaque changement (✓ / ✗ / ⚠), comme le stepper.
        viewModel.controles().addListener((ListChangeListener<ControleCoherence>) changement -> majChecklist());
        majChecklist();

        btnPreparer.disableProperty().bind(viewModel.peutPreparerProperty().not());
        // Explique le grisage (#789) sur l'enveloppe (un Button désactivé n'affiche pas de tooltip). La
        // préparation exige un passage vérifié ET tous les contrôles de cohérence au vert.
        IndicateurBlocage.expliquer(
                enveloppePreparer,
                Bindings.when(viewModel.peutPreparerProperty())
                        .then("Figer les séquences et préparer le lot à déposer.")
                        .otherwise("Préparation impossible : le passage doit être vérifié et tous les contrôles"
                                + " de cohérence au vert."));
        // « Marquer déposé » : pas pendant une génération en cours, sinon on marquerait le passage déposé
        // avant la fin de l'écriture des archives (#259).
        btnDeposer
                .disableProperty()
                .bind(viewModel.peutDeposerProperty().not().or(viewModel.generationEnCoursProperty()));
        IndicateurBlocage.expliquer(
                enveloppeDeposer,
                Bindings.when(viewModel
                                .peutDeposerProperty()
                                .and(viewModel.generationEnCoursProperty().not()))
                        .then("Marquer le passage comme déposé sur VigieChiro.")
                        .otherwise("À faire une fois le lot préparé, les archives générées et téléversées"
                                + " sur VigieChiro."));

        // Téléversement VigieChiro (#142), étape ③ : masqué hors application connectée (contexte de capture
        // sans `connexion`). Actif une fois le lot préparé, hors génération et hors téléversement en cours.
        // Un libellé restitue l'avancement puis le bilan (ou l'erreur). La visibilité porte sur l'ENVELOPPE
        // (et non le bouton), pour que l'infobulle du grisage (#789) et le bouton disparaissent ensemble.
        enveloppeTeleverser.setVisible(depotViewModel.disponible());
        enveloppeTeleverser.setManaged(depotViewModel.disponible());
        btnTeleverser
                .disableProperty()
                .bind(viewModel
                        .peutDeposerProperty()
                        .not()
                        .or(depotViewModel.enCoursProperty())
                        .or(viewModel.generationEnCoursProperty()));
        // Explique le grisage (#789) au survol de l'enveloppe : cas « déjà déposé » distingué des autres.
        IndicateurBlocage.expliquer(
                enveloppeTeleverser,
                Bindings.when(viewModel.deposeProperty())
                        .then("Passage déjà déposé sur VigieChiro : le téléversement est terminé.")
                        .otherwise(Bindings.when(btnTeleverser.disableProperty())
                                .then("Téléversement possible une fois le lot préparé (statut « Prêt à"
                                        + " déposer »), génération et envoi précédent terminés.")
                                .otherwise(
                                        "Téléverser la nuit sur VigieChiro (marque ensuite le passage" + " déposé).")));
        lierTableDepot();

        // Archives de dépôt (#110) : titre = plafond configuré ; bouton actif une fois le lot préparé et
        // hors génération en cours ; la liste reflète les ZIP produits.
        lblTitreArchives.textProperty().bind(viewModel.titreArchivesProperty());
        btnGenererArchives
                .disableProperty()
                .bind(viewModel
                        .peutGenererArchivesProperty()
                        .not()
                        .or(viewModel.generationEnCoursProperty())
                        .or(viewModel.espaceDepotSuffisantProperty().not()));
        // Alerte espace disque (#…) : bandeau rouge affiché sous le bouton quand la place manque, pour
        // expliquer AVANT le clic pourquoi « Générer » est désactivé (pas seulement un message discret après).
        lblEspaceInsuffisant.textProperty().bind(viewModel.raisonEspaceInsuffisantProperty());
        lblEspaceInsuffisant
                .visibleProperty()
                .bind(viewModel.raisonEspaceInsuffisantProperty().isNotEmpty());
        lblEspaceInsuffisant
                .managedProperty()
                .bind(viewModel.raisonEspaceInsuffisantProperty().isNotEmpty());
        // Progression déterminée (#769) : barre + libellé « Compression X/N · ETA », visibles seulement
        // pendant la génération hors-thread. La fraction et le libellé suivent le ProgressionOperation du VM.
        barreGeneration.progressProperty().bind(viewModel.progression().fractionProperty());
        barreGeneration.visibleProperty().bind(viewModel.generationEnCoursProperty());
        barreGeneration.managedProperty().bind(viewModel.generationEnCoursProperty());
        lblProgressionGeneration.textProperty().bind(viewModel.progression().messageProperty());
        lblProgressionGeneration.visibleProperty().bind(viewModel.generationEnCoursProperty());
        lblProgressionGeneration.managedProperty().bind(viewModel.generationEnCoursProperty());
        // Table de suivi (#820) : colonnes #/Fichiers/Taille/Progression + cellule état/barre + rangées
        // colorées selon l'état, alimentée par les lignes du VM (pré-remplies au plan, animées au fil de la
        // compression parallèle, réhydratées du disque à la réouverture d'un passage déjà généré).
        // Sélecteur de colonnes (#918, EPIC #914) : clic droit + ☰ « outils » offrent « Colonnes… » sur la
        // table de suivi comme sur les autres vues tabulaires. `#` (identité) et « Progression » (l'état) sont
        // verrouillées ; « Fichiers » et « Taille » masquables.
        GestionnaireColonnes.installer(tableArchives, menuOutils, TableSuiviArchives.configurer(tableArchives));
        tableArchives.setItems(viewModel.suiviLignes().lignes());

        // Étape ③ : « Ouvrir le dossier » seulement quand les archives sont réellement prêtes (#259), pas
        // dès qu'un chemin existe : les ZIP sont écrits sous leur nom final pendant la génération, ouvrir
        // (ou téléverser) avant la fin exposerait un fichier partiel. Donc activé après une génération
        // réussie (liste non vide) et hors génération en cours.
        btnOuvrirDepot
                .disableProperty()
                .bind(Bindings.isEmpty(viewModel.suiviLignes().lignes()).or(viewModel.generationEnCoursProperty()));
        // Explique le grisage (#789) : pas d'archives à ouvrir tant qu'elles ne sont pas générées.
        IndicateurBlocage.expliquer(
                enveloppeOuvrirDepot,
                Bindings.when(btnOuvrirDepot.disableProperty())
                        .then("Aucune archive de dépôt à ouvrir : générez d'abord les archives (ou patientez"
                                + " la fin de la génération en cours).")
                        .otherwise("Ouvrir le sous-dossier « depot/ » pour un dépôt manuel des archives ZIP."));

        // Nettoyage post-dépôt (#…) : « Supprimer les archives » actif seulement une fois le passage déposé
        // et s'il reste des archives ZIP sur disque (le VM lit le disque à chaque chargement d'état).
        btnSupprimerArchives
                .disableProperty()
                .bind(viewModel.peutSupprimerArchivesProperty().not());

        // Emphase de l'étape actionnable (#689) : parmi les trois actions applicatives du dépôt
        // (Préparer → Générer → Marquer déposé, l'étape ③ « Téléverser » étant manuelle), celle qui est
        // actionnable porte .bouton-primaire, les autres .bouton-secondaire — au plus une à la fois.
        // Recalculée à chaque évolution de l'état (peut*) ou des archives. « Ouvrir le dossier » reste
        // toujours secondaire (rôle fixé en FXML).
        InvalidationListener majRoles = observable -> majRolesEtapes();
        viewModel.peutPreparerProperty().addListener(majRoles);
        viewModel.peutGenererArchivesProperty().addListener(majRoles);
        viewModel.peutDeposerProperty().addListener(majRoles);
        viewModel.deposeProperty().addListener(majRoles);
        viewModel.suiviLignes().lignes().addListener((ListChangeListener<LigneArchive>) changement -> majRolesEtapes());
        majRolesEtapes();

        lblMessage.textProperty().bind(viewModel.messageProperty());
        var messagePresent = viewModel.messageProperty().isNotEmpty();
        lblMessage.visibleProperty().bind(messagePresent);
        lblMessage.managedProperty().bind(messagePresent);
    }

    /// Met en avant l'action **actionnable** du dépôt (#689) : `.bouton-primaire` sur l'unique étape
    /// courante parmi Préparer / Générer / Marquer déposé, `.bouton-secondaire` sur les autres. L'étape ③
    /// « Téléverser » est manuelle (« Ouvrir le dossier », toujours secondaire) : une fois les archives
    /// générées, c'est « Marquer déposé » qui devient primaire. Plus aucun primaire une fois le passage
    /// déposé. Le gating du VM garantit qu'au plus une des trois est actionnable à la fois.
    private void majRolesEtapes() {
        boolean archivesGenerees = !viewModel.suiviLignes().lignes().isEmpty();
        boolean deposeFait = viewModel.deposeProperty().get();
        appliquerRolePrimaire(btnPreparer, viewModel.peutPreparerProperty().get());
        appliquerRolePrimaire(
                btnGenererArchives, viewModel.peutGenererArchivesProperty().get() && !archivesGenerees && !deposeFait);
        appliquerRolePrimaire(btnDeposer, viewModel.peutDeposerProperty().get() && archivesGenerees);
    }

    /// Bascule le rôle du `bouton` entre `.bouton-primaire` (mis en avant) et `.bouton-secondaire`, sans
    /// dupliquer de classe si la méthode est rappelée. Les valeurs posées en FXML servent d'état initial.
    private static void appliquerRolePrimaire(Button bouton, boolean primaire) {
        bouton.getStyleClass().removeAll("bouton-primaire", "bouton-secondaire");
        bouton.getStyleClass().add(primaire ? "bouton-primaire" : "bouton-secondaire");
    }

    /// Reconstruit le stepper du dépôt (#251) depuis [LotViewModel#etapes()] : une puce par étape,
    /// stylée selon son état (franchie / courante / à venir), comme le stepper de M-Passage.
    private void majStepper() {
        stepper.getChildren().clear();
        for (EtapeDepot etape : viewModel.etapes()) {
            Label puce = new Label(etape.libelle());
            puce.getStyleClass().addAll("etape", "etape-" + etape.etat().name().toLowerCase(Locale.ROOT));
            stepper.getChildren().add(puce);
        }
    }

    /// Reconstruit la **checklist de cohérence** (#254) depuis [LotViewModel#controles()] : une ligne par
    /// contrôle, préfixée d'une icône ✓ / ✗ / ⚠ et stylée selon son statut. Un contrôle satisfait montre
    /// son libellé ; un contrôle en échec ou en avertissement montre aussi son détail.
    private void majChecklist() {
        checklist.getChildren().clear();
        for (ControleCoherence controle : viewModel.controles()) {
            String icone =
                    switch (controle.statut()) {
                        case OK -> "✓";
                        case ECHEC -> "✗";
                        case AVERTISSEMENT -> "⚠";
                    };
            // Satisfait : le libellé court suffit ; en échec/avertissement : le détail est déjà parlant
            // (et nomme le contrôle), on l'affiche tel quel pour ne pas répéter le libellé.
            String texte = controle.statut() == StatutControle.OK
                    ? icone + " " + controle.libelle()
                    : icone + " " + controle.detail();
            Label ligne = new Label(texte);
            ligne.setWrapText(true);
            ligne.getStyleClass()
                    .addAll(
                            "controle-ligne",
                            "controle-" + controle.statut().name().toLowerCase(Locale.ROOT));
            checklist.getChildren().add(ligne);
        }
    }

    /// Ouvre l'écran sur le passage `passage`. Appelée par [NavigationLot] après le chargement FXML ;
    /// mémorise le contexte pour le fil d'Ariane.
    public void ouvrirSur(ContextePassage passage) {
        this.contexte = passage;
        viewModel.ouvrirSur(passage.idPassage());
        // Réhydrate la table de dépôt (#983) depuis l'état persisté : un dépôt interrompu réaffiche ses
        // unités (déposées/échecs) et propose la reprise.
        depotViewModel.rehydrater(passage.idPassage());
    }

    /// Emplacement dans le fil d'Ariane : `Mes sites › Carré N › Détails du passage N° X › Préparer le
    /// dépôt` (rendu par le chrome). Le segment passage rouvre M-Passage.
    @Override
    public List<Lieu> emplacement() {
        return EmplacementPassage.emplacementEnfant(contexte, ouvrirSite, ouvrirPassage, "Préparer le dépôt");
    }

    @FXML
    private void preparer() {
        viewModel.preparer();
    }

    @FXML
    private void deposer() {
        viewModel.deposer();
    }

    /// Lance la génération des archives **hors fil JavaFX** (#251) : l'opération peut être longue sur une
    /// grosse nuit, on ne fige pas l'IHM. L'état « en cours » est posé sur le fil JavaFX, le calcul tourne
    /// sur un fil virtuel, puis le résultat (succès ou échec) est appliqué via `Platform.runLater`.
    @FXML
    private void genererArchives() {
        viewModel.marquerGenerationEnCours();
        // Callback de progression (#769) : le service l'appelle hors-thread, on relaie chaque point au fil
        // JavaFX pour mettre à jour la barre globale + l'estimation de durée.
        Consumer<Progression> progres =
                point -> Platform.runLater(() -> viewModel.progression().appliquer(point));
        // Suivi par archive (#820) : anime la table ligne par ligne (plan → en cours → terminée / échec).
        SuiviArchives suivi = relaisSuiviTable();
        Thread.ofVirtual().name("archives-depot-vigiechiro").start(() -> {
            try {
                var produites = viewModel.calculerArchivesDepot(progres, suivi);
                Platform.runLater(() -> viewModel.appliquerGeneration(produites));
            } catch (RuntimeException echec) {
                Platform.runLater(() -> viewModel.echecGeneration(echec.getMessage()));
            }
        });
    }

    /// Téléverse la nuit sur VigieChiro **hors fil JavaFX** (#142), étape ③ automatisée : même patron que la
    /// génération d'archives (état « en cours » posé au fil JavaFX, dépôt sur un fil virtuel, résultat via
    /// `Platform.runLater`). Les statuts (« Dépôt en cours » / « Déposé ») sont posés par le moteur
    /// reprenable (#982). L'IHM restitue l'avancement puis le bilan (ou l'erreur) via le libellé de l'étape.
    @FXML
    private void televerserVigieChiro() {
        Long idPassage = contexte.idPassage();
        depotViewModel.marquerEnCours();
        Thread.ofVirtual().name("depot-vigiechiro").start(() -> {
            try {
                BilanDepot bilan =
                        depotViewModel.televerser(idPassage, new RelaisSuiviDepot(depotViewModel.suiviLignes()));
                Platform.runLater(() -> {
                    depotViewModel.appliquerBilan(bilan);
                    // Statut honnête (#982) : le moteur de dépôt a déjà posé « Dépôt en cours » ou
                    // « Déposé » (jamais « Déposé » sur un dépôt partiel — l'ancien appel inconditionnel
                    // à deposer() était le bug). On recharge l'état pour refléter le statut réel.
                    viewModel.ouvrirSur(idPassage);
                });
            } catch (RuntimeException echec) {
                Platform.runLater(() -> depotViewModel.echec(echec.getMessage()));
            }
        });
    }

    /// Câble la table de dépôt (#983) : lignes persistées (`depot_unite` #981) + événements du moteur
    /// reprenable (#982). Visible seulement quand un dépôt a été entamé (liaison vivante sur la liste).
    /// Quand il reste des unités non déposées, l'action devient une reprise : « Retenter les échecs ».
    private void lierTableDepot() {
        lblDepotMessage.textProperty().bind(depotViewModel.messageProperty());
        lblDepotMessage.visibleProperty().bind(depotViewModel.messageProperty().isNotEmpty());
        lblDepotMessage.managedProperty().bind(depotViewModel.messageProperty().isNotEmpty());
        TableSuiviDepot.configurer(tableDepot);
        tableDepot.setItems(depotViewModel.suiviLignes().lignes());
        var depotEntame = Bindings.isNotEmpty(depotViewModel.suiviLignes().lignes());
        tableDepot.visibleProperty().bind(depotEntame);
        tableDepot.managedProperty().bind(depotEntame);
        btnTeleverser
                .textProperty()
                .bind(Bindings.when(depotViewModel.suiviLignes().resteAReprendreProperty())
                        .then("↻ Retenter les échecs")
                        .otherwise("☁ Téléverser sur Vigie-Chiro"));
    }

    /// Relais du suivi par archive (#820) vers la table : chaque événement, émis **hors fil JavaFX** et dans
    /// le désordre (compression parallèle #814), est rejoué sur le fil JavaFX via `Platform.runLater` pour
    /// muter les lignes observables du VM ([LotViewModel#suiviLignes()]).
    private SuiviArchives relaisSuiviTable() {
        return new SuiviArchives() {
            @Override
            public void planEtabli(List<ArchivePlanifiee> plan) {
                Platform.runLater(() -> viewModel.suiviLignes().planifier(plan));
            }

            @Override
            public void archiveDemarree(int numero) {
                Platform.runLater(() -> viewModel.suiviLignes().demarrer(numero));
            }

            @Override
            public void archiveProgresse(int numero, int faits, int total) {
                Platform.runLater(() -> viewModel.suiviLignes().progresser(numero, faits, total));
            }

            @Override
            public void archiveTerminee(ArchiveDepot archive) {
                Platform.runLater(() -> viewModel.suiviLignes().terminer(archive));
            }

            @Override
            public void archiveEchouee(int numero, String raison) {
                Platform.runLater(() -> viewModel.suiviLignes().echouer(numero, raison));
            }
        };
    }

    /// Ouvre le sous-dossier `depot/` dans le gestionnaire de fichiers du système (#251), pour aider au
    /// téléversement manuel (étape ③). Sans chemin, le bouton est désactivé ; l'ouverture ne lève jamais.
    @FXML
    private void ouvrirDossierDepot() {
        String chemin = viewModel.cheminDepotProperty().get();
        if (chemin != null && !chemin.isBlank()) {
            ouvreurDeLien.ouvrir(Path.of(chemin).toUri().toString());
        }
    }

    /// Supprime les archives ZIP de dépôt (#…) **après confirmation**, une fois le passage déposé, pour
    /// libérer l'espace disque (régénérables au besoin). La confirmation passe par [#confirmateur]
    /// (injectable), et le bouton n'est actif que dans l'état adéquat.
    @FXML
    private void supprimerArchives() {
        if (confirmateur.test("Supprimer définitivement les archives ZIP de dépôt du dossier « depot/ » ?\n\n"
                + "Elles ont déjà été téléversées sur Vigie-Chiro et pourront être régénérées si besoin.")) {
            viewModel.supprimerArchives();
        }
    }

    /// Dialogue natif de confirmation (OK / Annuler). Isolé pour être remplacé en test via
    /// [#definirConfirmateur(Predicate)] — un `showAndWait` bloquerait sinon un test TestFX.
    private boolean confirmerParDialogue(String message) {
        Alert alerte = new Alert(AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alerte.setTitle("Supprimer les archives de dépôt ?");
        alerte.setHeaderText(null);
        return alerte.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    /// Remplace le confirmateur (tests) pour éviter le dialogue natif bloquant sous TestFX.
    void definirConfirmateur(Predicate<String> confirmateur) {
        this.confirmateur = Objects.requireNonNull(confirmateur, "confirmateur");
    }
}
