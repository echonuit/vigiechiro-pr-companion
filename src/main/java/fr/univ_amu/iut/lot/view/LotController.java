package fr.univ_amu.iut.lot.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.IconesSeverite;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.MenuCopier;
import fr.univ_amu.iut.commun.view.MenuLigne;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.view.Stepper;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.ArchivePlanifiee;
import fr.univ_amu.iut.lot.model.ControleCoherence;
import fr.univ_amu.iut.lot.model.StatutControle;
import fr.univ_amu.iut.lot.model.SuiviArchives;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.EtapeDepot;
import fr.univ_amu.iut.lot.viewmodel.LigneArchive;
import fr.univ_amu.iut.lot.viewmodel.LigneDepot;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import fr.univ_amu.iut.lot.viewmodel.TraitementViewModel;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

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
    private final DepotDispositionColonnes depotColonnes;

    /// Socle « travail lourd hors fil JavaFX » (#1014, étendu #1252) : les trois flux de l'écran
    /// (compute, génération d'archives, dépôt) passent par lui plutôt que par un `Thread.ofVirtual()`
    /// maison — l'IHM ne gèle pas, et les tests sont **synchrones** (#1253).
    private final ExecuteurTache executeur;

    /// Suivi du traitement serveur (#1263), séparé de [LotViewModel] (déjà au plafond de complexité).
    private final TraitementViewModel traitementViewModel;

    /// Calcul des 3 zones de la barre de statut (#823), extrait de ce contrôleur pour la cohésion (#984).
    private final ZonesStatutLot zonesStatutLot;

    /// Contexte de navigation (passage + site), mémorisé pour reconstruire le fil d'Ariane du chrome.
    private ContextePassage contexte;

    /// Statut du workflow, déporté en zone centre de la barre de statut (#693) au lieu d'un sous-titre.
    private final ReadOnlyObjectWrapper<ZonesStatut> zonesStatut =
            new ReadOnlyObjectWrapper<>(this, "zonesStatut", ZonesStatut.VIDE);

    /// Confirmation d'action destructive : porteur partagé injectable (#1013), stub déterministe en test.
    /// Le titre personnalisé du dialogue (« Supprimer les archives de dépôt ? ») est conservé, partagé par
    /// les deux confirmations de l'écran (suppression des archives, réinitialisation du dépôt).
    private final ConfirmateurModifiable confirmateur =
            new ConfirmateurModifiable(new ConfirmationNavigation("Supprimer les archives de dépôt ?"));

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

    /// Icône du bouton de dépôt : elle suit son libellé, qui change de sens (marquer / lancer).
    @FXML
    private FontIcon iconeDeposer;

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

    /// Icône du bouton de téléversement : elle suit son libellé, qui change de sens (téléverser / reprendre).
    @FXML
    private FontIcon iconeTeleverser;

    @FXML
    private TableView<LigneDepot> tableDepot;

    @FXML
    private Button btnAnnulerDepot;

    @FXML
    private StackPane enveloppeOuvrirDepot;

    @FXML
    private Button btnOuvrirDepot;

    @FXML
    private Button btnSupprimerArchives;

    /// Carte « Libérer l'espace disque » (#2028) : masquée tant qu'elle n'a rien à proposer, pour ne pas
    /// occuper le même rang visuel que les étapes avec un bouton grisé et une consigne sans objet.
    @FXML
    private VBox zoneLibererEspace;

    @FXML
    private Button btnReinitialiserDepot;

    @FXML
    private Label lblEtatLot;

    @FXML
    private HBox bandeauRetour;

    @FXML
    private Label lblRetour;

    @FXML
    private Button btnFermerRetour;

    /// Zone « Traitement Vigie-Chiro » (#1263) : visible une fois la nuit déposée par l'application.
    @FXML
    private VBox zoneTraitement;

    @FXML
    private Label lblEtatTraitement;

    @FXML
    private Label lblFraicheurTraitement;

    @FXML
    private Label lblAlerteTraitement;

    @FXML
    private Button btnActualiserTraitement;

    /// Câblage de la zone de suivi, extrait de ce contrôleur (#1263).
    private SuiviTraitementUI suiviTraitement;

    @Inject
    public LotController(
            LotViewModel viewModel,
            DepotViewModel depotViewModel,
            NavigationViewModel navigation,
            OuvrirSite ouvrirSite,
            OuvrirPassage ouvrirPassage,
            OuvreurDeLien ouvreurDeLien,
            DepotDispositionColonnes depotColonnes,
            ExecuteurTache executeur,
            TraitementViewModel traitementViewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.depotViewModel = Objects.requireNonNull(depotViewModel, "depotViewModel");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.ouvreurDeLien = Objects.requireNonNull(ouvreurDeLien, "ouvreurDeLien");
        this.depotColonnes = Objects.requireNonNull(depotColonnes, "depotColonnes");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
        this.traitementViewModel = Objects.requireNonNull(traitementViewModel, "traitementViewModel");
        this.zonesStatutLot = new ZonesStatutLot(viewModel, depotViewModel, () -> contexte);
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
        // Zone « Traitement Vigie-Chiro » (#1263) : ce qui se passe APRÈS le dépôt. Le composant décide
        // lui-même de son affichage (nuit déposée par l'application, et suivi disponible).
        suiviTraitement = SuiviTraitementUI.installer(
                traitementViewModel,
                executeur,
                () -> contexte.idPassage(),
                depotViewModel.participationLieeProperty(),
                zoneTraitement,
                lblEtatTraitement,
                lblFraicheurTraitement,
                lblAlerteTraitement,
                btnActualiserTraitement);

        // Opération critique en cours (#906) : la génération d'archives et le dépôt sont des tâches longues
        // qu'on ne doit pas abandonner en silence. On pose leur libellé sur le chrome (qui avertit avant de
        // quitter/fermer) et on l'efface à leur fin. Les écouteurs sont posés sur les propriétés des VM
        // (durables), pas sur une liaison locale, pour que l'effacement survienne même après une sortie.
        viewModel.generationEnCoursProperty().addListener((obs, avant, apres) -> majOperationCritique());
        depotViewModel.enCoursProperty().addListener((obs, avant, apres) -> majOperationCritique());
        majOperationCritique();

        // Statut du workflow déporté en zone centre de la barre de statut (#693), plus de sous-titre.
        // Barre de statut 3 zones (#823) : gauche = contexte du passage, centre = statut + récap, droite =
        // état vivant (par priorité : dépôt en cours > génération > espace insuffisant > bilan archives).
        zonesStatut.bind(Bindings.createObjectBinding(
                zonesStatutLot::calculer,
                viewModel.statutProperty(),
                viewModel.recapProperty(),
                viewModel.generationEnCoursProperty(),
                viewModel.progression().messageProperty(),
                viewModel.espaceDepotSuffisantProperty(),
                viewModel.raisonEspaceInsuffisantProperty(),
                viewModel.suiviLignes().lignes(),
                depotViewModel.enCoursProperty(),
                depotViewModel.suiviLignes().deposeesProperty(),
                depotViewModel.suiviLignes().enCoursProperty(),
                depotViewModel.suiviLignes().echecsProperty(),
                depotViewModel.suiviLignes().totalProperty()));
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
                        .then("Figer les séquences et préparer le dépôt.")
                        .otherwise("Préparation impossible : le passage doit être vérifié et tous les contrôles"
                                + " de cohérence au vert."));
        // Bouton de l'étape ④ : trois règles (libellé qui change de sens, cliquable après un dépôt
        // partiel, verrouillé si la nuit est déjà analysée), câblées à part (#1263).
        EtapeDeposerUI.cabler(
                btnDeposer, iconeDeposer, enveloppeDeposer, viewModel, depotViewModel, traitementViewModel);

        // Téléversement VigieChiro (#142), étape ③ : masqué hors application connectée (contexte de capture
        // sans `connexion`). Actif une fois le dépôt préparé, hors génération et hors téléversement en cours.
        //
        // Le grisage pendant la génération (#1998) : il pourrait sembler que le pipeline le rend inutile,
        // puisqu'il n'y a plus à attendre l'étape ② pour téléverser. Il reste **nécessaire**, mais pour une
        // autre raison que l'attente : les deux opérations écrivent le MÊME fichier `<préfixe>-N.zip`
        // (CompacteurDepot.ecrireArchive et SourceArchivesRegenerables.resoudre), donc les laisser se
        // recouvrir corromprait des archives. Ce qui a disparu, c'est l'obligation de lancer ② d'abord.
        // Un libellé restitue l'avancement puis le bilan (ou l'erreur). La visibilité porte sur l'ENVELOPPE
        // (et non le bouton), pour que l'infobulle du grisage (#789) et le bouton disparaissent ensemble.
        // L'étape ② n'est plus un passage obligé quand on est connecté (#1998) : le stepper doit le
        // savoir, et seul le controller connaît les deux ViewModels.
        viewModel.declarerDepotAutomatiqueDisponible(depotViewModel.disponible());
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
                        .then("Passage déjà déposé sur Vigie-Chiro : le téléversement est terminé.")
                        .otherwise(Bindings.when(btnTeleverser.disableProperty())
                                .then("Téléversement possible une fois le dépôt préparé (statut « Prêt à"
                                        + " déposer »), et hors génération ou envoi en cours. Générer les"
                                        + " archives n'est pas un préalable : le téléversement produit"
                                        + " lui-même ce dont il a besoin.")
                                .otherwise("Téléverser la nuit sur Vigie-Chiro (marque ensuite le passage"
                                        + " déposé).")));
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
        lierAffichage(barreGeneration, viewModel.generationEnCoursProperty());
        lblProgressionGeneration.textProperty().bind(viewModel.progression().messageProperty());
        lierAffichage(lblProgressionGeneration, viewModel.generationEnCoursProperty());
        // Table de suivi (#820) : colonnes #/Fichiers/Taille/Progression + cellule état/barre + rangées
        // colorées selon l'état, alimentée par les lignes du VM (pré-remplies au plan, animées au fil de la
        // compression parallèle, réhydratées du disque à la réouverture d'un passage déjà généré).
        // Sélecteur de colonnes (#918, EPIC #914) : clic droit + ☰ « outils » offrent « Colonnes… » sur la
        // table de suivi comme sur les autres vues tabulaires. `#` (identité) et « Progression » (l'état) sont
        // verrouillées ; « Fichiers » et « Taille » masquables.
        // Actions de ligne (#1796, #1798). Les archives d'un dépôt vivent toutes dans le **même** dossier
        // `depot/` : l'item ouvre donc ce dossier, miroir du bouton « Ouvrir le dossier », et « Copier ▸ »
        // en donne le chemin. La ligne ne porte pas le chemin de son propre ZIP (LigneArchive = numéro,
        // nombre de fichiers, taille) : on ne le reconstitue pas à la main, un chemin faux serait pire que
        // pas de chemin du tout.
        GestionnaireColonnes.installerEtPersister(
                tableArchives,
                menuOutils,
                TableSuiviArchives.configurer(tableArchives),
                depotColonnes,
                "lot",
                "principale",
                MenuLigne.item("Ouvrir le dossier", tableArchives, ligne -> ouvrirDossierDepot()),
                MenuCopier.creer(
                        tableArchives,
                        new MenuCopier.Entree<>(
                                "Chemin du dossier",
                                ligne -> chemin(viewModel.cheminDepotProperty().get()))));
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

        // La carte entière disparaît quand elle n'a rien à proposer (#2028) : plutôt qu'une 5e carte de
        // même rang que les étapes, portant un bouton grisé et une consigne sans objet. Elle réapparaît
        // dès que la suppression devient possible (déposé ET archives présentes).
        lierAffichage(zoneLibererEspace, viewModel.peutSupprimerArchivesProperty());

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

        // Ligne d'**état** du lot, adossée au stepper dont elle est la version en prose : permanente et
        // non fermable (#1890). Elle ne partage plus le canal des comptes rendus, qui la recouvraient ou
        // s'en faisaient recouvrir selon l'ordre des appels.
        lblEtatLot.textProperty().bind(viewModel.etatLotProperty());
        var etatPresent = viewModel.etatLotProperty().isNotEmpty();
        lierAffichage(lblEtatLot, etatPresent);

        BandeauLotUI.cabler(bandeauRetour, lblRetour, btnFermerRetour, viewModel, depotViewModel);
    }

    /// Met en avant l'action **actionnable** du dépôt (#689) : `.bouton-primaire` sur l'unique étape
    /// courante parmi Préparer / Générer / Marquer déposé, `.bouton-secondaire` sur les autres. L'étape ③
    /// « Téléverser » porte son rôle primaire depuis le FXML : hors connexion elle est masquée, et
    /// connectée elle EST l'action de la marche courante — « Générer » lui cède donc la place plutôt que
    /// de la concurrencer. Une fois les archives générées, c'est « Marquer déposé » qui devient primaire.
    /// Plus aucun primaire une fois le passage déposé. Le gating du VM garantit qu'au plus une des trois
    /// est actionnable à la fois.
    private void majRolesEtapes() {
        boolean archivesGenerees = !viewModel.suiviLignes().lignes().isEmpty();
        boolean deposeFait = viewModel.deposeProperty().get();
        // Connecté, « Téléverser » (primaire en FXML) est l'action de la marche courante : générer n'est
        // plus qu'une option pour le dépôt manuel (#1998). Sans cette condition, l'écran affiche DEUX
        // boutons primaires dès qu'aucune archive n'est sur le disque - état devenu courant depuis que le
        // téléversement produit les siennes. Défaut trouvé en regardant la capture, pas par un test.
        boolean genererEstLaMarcheCourante =
                viewModel.peutGenererArchivesProperty().get()
                        && !archivesGenerees
                        && !deposeFait
                        && !depotViewModel.disponible();
        appliquerRolePrimaire(btnPreparer, viewModel.peutPreparerProperty().get());
        appliquerRolePrimaire(btnGenererArchives, genererEstLaMarcheCourante);
        appliquerRolePrimaire(btnDeposer, viewModel.peutDeposerProperty().get() && archivesGenerees);
    }

    /// Bascule le rôle du `bouton` entre `.bouton-primaire` (mis en avant) et `.bouton-secondaire`, sans
    /// dupliquer de classe si la méthode est rappelée. Les valeurs posées en FXML servent d'état initial.
    private static void appliquerRolePrimaire(Button bouton, boolean primaire) {
        bouton.getStyleClass().removeAll("bouton-primaire", "bouton-secondaire");
        bouton.getStyleClass().add(primaire ? "bouton-primaire" : "bouton-secondaire");
    }

    /// Lie l'**affichage** d'un nœud à une condition : `visible` ET `managed` ensemble, pour qu'il
    /// disparaisse **sans laisser de trou** dans la mise en page quand la condition est fausse. Les deux
    /// liaisons vont toujours de pair ; les réunir évite de n'en poser qu'une (nœud invisible mais qui
    /// occupe encore sa place, ou l'inverse).
    private static void lierAffichage(Node noeud, ObservableValue<? extends Boolean> condition) {
        noeud.visibleProperty().bind(condition);
        noeud.managedProperty().bind(condition);
    }

    /// Reconstruit le stepper du dépôt (#251) depuis [LotViewModel#etapes()] : une puce par étape,
    /// stylée selon son état (franchie / courante / à venir), comme le stepper de M-Passage.
    private void majStepper() {
        Stepper.reconstruire(stepper, viewModel.etapes(), EtapeDepot::libelle, EtapeDepot::etat);
    }

    /// Reconstruit la **checklist de cohérence** (#254) depuis [LotViewModel#controles()] : une ligne par
    /// contrôle, préfixée d'une icône ✓ / ✗ / ⚠ et stylée selon son statut. Un contrôle satisfait montre
    /// son libellé ; un contrôle en échec ou en avertissement montre aussi son détail.
    private void majChecklist() {
        checklist.getChildren().clear();
        for (ControleCoherence controle : viewModel.controles()) {
            // Satisfait : le libellé court suffit ; en échec/avertissement : le détail est déjà parlant
            // (et nomme le contrôle), on l'affiche tel quel pour ne pas répéter le libellé.
            String texte = controle.statut() == StatutControle.OK ? controle.libelle() : controle.detail();
            Label ligne = new Label(texte);
            // La sévérité était dite DEUX fois : par la classe CSS `controle-*`, et par un glyphe
            // recopié dans un switch. Le glyphe devient une icône (#2099, ADR 0035) et vient de la
            // table partagée, donc un même statut a la même forme ici et dans le bandeau.
            ligne.setGraphic(IconesSeverite.icone(severite(controle.statut()), "controle-icone"));
            ligne.setWrapText(true);
            ligne.getStyleClass()
                    .addAll(
                            "controle-ligne",
                            "controle-" + controle.statut().name().toLowerCase(Locale.ROOT));
            checklist.getChildren().add(ligne);
        }
    }

    /// La sévérité d'un contrôle de cohérence. Les deux échelles disent la même chose : un contrôle
    /// satisfait est un succès, un contrôle en échec une erreur.
    private static Severite severite(StatutControle statut) {
        return switch (statut) {
            case OK -> Severite.SUCCES;
            case ECHEC -> Severite.ERREUR;
            case AVERTISSEMENT -> Severite.AVERTISSEMENT;
        };
    }

    /// Ouvre l'écran sur le passage `passage`. Appelée par [NavigationLot] après le chargement FXML ;
    /// mémorise le contexte pour le fil d'Ariane.
    public void ouvrirSur(ContextePassage passage) {
        this.contexte = passage;
        viewModel.ouvrirSur(passage.idPassage());
        // Réhydrate la table de dépôt (#983) depuis l'état persisté : un dépôt interrompu réaffiche ses
        // unités (déposées/échecs) et propose la reprise.
        depotViewModel.rehydrater(passage.idPassage());
        // Dernier état connu du traitement (#1262) : lu dans le cache, sans réseau — la zone n'est jamais
        // muette, même hors connexion. Le relevé frais reste à la demande (« Actualiser »).
        suiviTraitement.rehydrater();
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
        // #984 : quand une participation est liée (dépôt via l'API effectué), le bouton lance le
        // traitement serveur (compute) au lieu de marquer déposé — déjà fait par le dépôt API.
        if (depotViewModel.participationLieeProperty().get()) {
            // Le traitement serveur appartient à la zone de suivi (#1263), lancement compris.
            suiviTraitement.lancer(depotViewModel);
        } else {
            viewModel.deposer();
        }
    }

    /// Lance la génération des archives **hors fil JavaFX** (#251) via le socle étendu (#1252/#1253) :
    /// l'opération peut être longue sur une grosse nuit, on ne fige pas l'IHM. L'état « en cours » est
    /// posé sur le fil JavaFX, la progression (#769) et le suivi par archive (#820) reviennent par les
    /// relais du socle, puis le résultat (succès ou échec) est appliqué sur le fil JavaFX.
    @FXML
    private void genererArchives() {
        viewModel.marquerGenerationEnCours();
        // Progression (#769) : le service l'appelle hors-thread, chaque point est relayé au fil JavaFX
        // (barre globale + estimation de durée).
        Consumer<Progression> progres =
                executeur.relaisProgression(point -> viewModel.progression().appliquer(point));
        // Suivi par archive (#820) : anime la table ligne par ligne (plan → en cours → terminée / échec).
        SuiviArchives suivi = relaisSuiviTable();
        executeur.executer(
                () -> viewModel.calculerArchivesDepot(progres, suivi),
                viewModel::appliquerGeneration,
                erreur -> viewModel.echecGeneration(erreur.getMessage()));
    }

    /// Téléverse la nuit sur VigieChiro **hors fil JavaFX** (#142), étape ③ automatisée, via le socle
    /// étendu (#1252/#1253) : même patron que la génération d'archives. Les statuts (« Dépôt en cours » /
    /// « Déposé ») sont posés par le moteur reprenable (#982) ; l'IHM ne fait que les restituer.
    ///
    /// L'**annulation** (#1044) reste coopérative **côté ViewModel**, style « retour partiel » :
    /// « Annuler le dépôt » pose un drapeau que le moteur lit entre deux fichiers, termine l'unité en vol
    /// et rend un **bilan honnête par le chemin de succès** (jamais d'unité fantôme ; « Reprendre le
    /// dépôt » ne renverra que le manquant). En test synchrone, l'annulation se joue au premier point de
    /// contrôle du moteur (cf. `dev-docs/patterns.md`, § occupation).
    @FXML
    private void televerserVigieChiro() {
        Long idPassage = contexte.idPassage();
        depotViewModel.marquerEnCours();
        RelaisSuiviDepot suivi = new RelaisSuiviDepot(depotViewModel.suiviLignes(), executeur.surFilJavaFx());
        executeur.executer(
                () -> depotViewModel.televerser(idPassage, suivi),
                bilan -> {
                    depotViewModel.appliquerBilan(bilan);
                    // Statut honnête (#982) : le moteur a déjà posé le bon statut (jamais « Déposé » sur un
                    // dépôt partiel) ; on recharge l'état pour le refléter.
                    viewModel.ouvrirSur(idPassage);
                },
                erreur -> depotViewModel.echec(erreur.getMessage()));
    }

    /// Câble la table de dépôt (#983) : lignes persistées (`depot_unite` #981) + événements du moteur
    /// reprenable (#982). Visible seulement quand un dépôt a été entamé (liaison vivante sur la liste).
    /// Quand il reste des unités non déposées, l'action devient une reprise : « Retenter les échecs ».
    private void lierTableDepot() {
        TableSuiviDepot.configurer(tableDepot);
        // Sélecteur de colonnes (#1800) : la table de dépôt n'avait aucun menu contextuel, alors que sa
        // voisine (archives) en a un sur le même écran. Disposition retenue par écran (#994), clé « depot ».
        var colonnesDepot = GestionnaireColonnes.colonnesParDefaut(tableDepot);
        GestionnaireColonnes.installerClicDroit(
                tableDepot,
                colonnesDepot,
                // LigneDepot ne porte pas de chemin : son identifiant est la clé qu'on recoupe côté
                // plateforme, c'est donc lui qu'on offre à la copie.
                MenuCopier.creer(
                        tableDepot, new MenuCopier.Entree<>("Identifiant", ligne -> chemin(ligne.identifiant()))));
        GestionnaireColonnes.persister(tableDepot, colonnesDepot, depotColonnes, "lot", "depot");
        tableDepot.setItems(depotViewModel.suiviLignes().lignes());
        var depotEntame = Bindings.isNotEmpty(depotViewModel.suiviLignes().lignes());
        lierAffichage(tableDepot, depotEntame);
        // « Réinitialiser le dépôt » (#984) : visible dès qu'un plan existe, désactivé pendant un dépôt.
        lierAffichage(btnReinitialiserDepot, depotEntame);
        btnReinitialiserDepot.disableProperty().bind(depotViewModel.enCoursProperty());
        // Étape 3 (téléverser / reprendre, et l'annulation coopérative) : câblage déporté, comme l'étape 4.
        EtapeTeleverserUI.cabler(btnTeleverser, iconeTeleverser, btnAnnulerDepot, depotViewModel);
    }

    /// Demande l'annulation coopérative du dépôt en cours (#1044) : délégué au ViewModel, le moteur
    /// s'arrête entre deux fichiers.
    @FXML
    private void annulerDepotVigieChiro() {
        depotViewModel.demanderAnnulation();
    }

    /// Relais du suivi par archive (#820) vers la table : chaque événement, émis **hors fil JavaFX** et dans
    /// le désordre (compression parallèle #814), est rejoué sur le fil JavaFX (fourni par le socle,
    /// [ExecuteurTache#surFilJavaFx()] - immédiat en test synchrone) pour muter les lignes observables du VM
    /// ([LotViewModel#suiviLignes()]).
    private SuiviArchives relaisSuiviTable() {
        Executor filJavaFx = executeur.surFilJavaFx();
        return new SuiviArchives() {
            @Override
            public void planEtabli(List<ArchivePlanifiee> plan) {
                filJavaFx.execute(() -> viewModel.suiviLignes().planifier(plan));
            }

            @Override
            public void archiveDemarree(int numero) {
                filJavaFx.execute(() -> viewModel.suiviLignes().demarrer(numero));
            }

            @Override
            public void archiveProgresse(int numero, int faits, int total) {
                filJavaFx.execute(() -> viewModel.suiviLignes().progresser(numero, faits, total));
            }

            @Override
            public void archiveTerminee(ArchiveDepot archive) {
                filJavaFx.execute(() -> viewModel.suiviLignes().terminer(archive));
            }

            @Override
            public void archiveEchouee(int numero, String raison) {
                filJavaFx.execute(() -> viewModel.suiviLignes().echouer(numero, raison));
            }
        };
    }

    /// Chemin à copier, ou chaîne vide tant que le dossier de dépôt n'existe pas : on ne met jamais
    /// « null » dans le presse-papier de l'utilisateur.
    private static String chemin(String valeur) {
        return valeur == null ? "" : valeur;
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
        if (confirmateur.confirmer("Supprimer définitivement les archives ZIP de dépôt du dossier « depot/ » ?\n\n"
                + "Elles ont déjà été téléversées sur Vigie-Chiro et pourront être régénérées si besoin.")) {
            viewModel.supprimerArchives();
        }
    }

    /// Réinitialise le dépôt (#984) : efface le suivi local pour permettre un nouveau téléversement (ex.
    /// dépôt orphelin d'avant le rattachement `lien_participation`). Confirmation ([#confirmateur],
    /// injectable). Recharge la table (plan vidé) et l'état du passage (retour « Prêt à déposer »).
    @FXML
    private void reinitialiserDepot() {
        if (confirmateur.confirmer("Réinitialiser le dépôt de cette nuit ?\n\n"
                + "Le suivi local est effacé pour permettre un nouveau téléversement ; les archives ZIP"
                + " sur disque et la participation Vigie-Chiro sont conservées.")) {
            depotViewModel.reinitialiser(contexte.idPassage());
            viewModel.ouvrirSur(contexte.idPassage());
        }
    }

    /// Porteur de confirmation exposé aux tests (#1013) : `confirmateur().definir(stub)`.
    ConfirmateurModifiable confirmateur() {
        return confirmateur;
    }
}
