package fr.univ_amu.iut.sites.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.view.ColonneBadge;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.DoubleClicLigne;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.MenuLigne;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.NotificateurModifiable;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.OuvrirImportation;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.RafraichirAuRetour;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.view.TableDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.viewmodel.LignePassage;
import fr.univ_amu.iut.sites.viewmodel.SiteDetailViewModel;
import fr.univ_amu.iut.sites.viewmodel.StatutPlateforme;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/// Controller de l'écran de détail **M-Site-detail** (`SiteDetail.fxml`).
///
/// Câble la fiche d'identité (bandeau) et le tableau des passages aux propriétés du
/// [SiteDetailViewModel] ; le tableau des passages est piloté par les `cellValueFactory`, avec un
/// badge coloré (couleur **dérivée** du statut/verdict) pour les colonnes Statut et Verdict. Le rendu
/// des cartes de points (nombre variable, reconstruites à chaque changement de la liste observable) est
/// délégué à [CartesPointsSite] (extrait, seuil de cohésion PMD, #1087).
///
/// L'écran délègue toute navigation à [NavigationSites] : retour à l'accueil (fil d'Ariane),
/// ouverture des modales de point, retour à l'accueil après suppression du site.
///
/// Implémente [RafraichirAuRetour] : quand on revient sur la fiche après avoir ouvert un passage et
/// l'avoir fait avancer (vérification, dépôt, validation), le tableau des passages est rechargé pour
/// refléter le nouveau statut/verdict (sinon il afficherait un état périmé, l'écran restant vivant).
///
/// Implémente aussi [ResumeStatut] (#693) : le titre (nom du site) et le sous-titre (commune, protocole),
/// jusqu'ici en en-tête, sont déportés en barre de statut (zones gauche = contexte, centre = résumé) ; le
/// gros titre était partiellement redondant avec le fil d'Ariane, et les actions restent en tête d'écran.
public class SiteDetailController implements RafraichirAuRetour, ResumeStatut {

    private final SiteDetailViewModel viewModel;
    private final NavigationSites navigation;
    private final OuvrirPassage ouvrirPassage;
    private final Optional<OuvrirImportation> ouvrirImportation;
    private final OuvrirMultisite ouvrirMultisite;
    private final DepotDispositionColonnes depotColonnes;
    private final OuvreurDeLien ouvreurDeLien;

    /// Contexte du site (nom en zone gauche, commune/protocole en zone centre) déporté en barre de statut
    /// (#693) au lieu d'un en-tête titre/sous-titre.
    private final ReadOnlyObjectWrapper<ZonesStatut> zonesStatut =
            new ReadOnlyObjectWrapper<>(this, "zonesStatut", ZonesStatut.VIDE);

    /// Confirmation d'action destructive : porteur partagé injectable (#1013), stub déterministe en test.
    private final ConfirmateurModifiable confirmateur = new ConfirmateurModifiable();

    /// Compte rendu de l'écran : porteur partagé injectable (#1405), double capturant en test. Sans lui,
    /// le refus se terminait par un `Alert.showAndWait()` qui **fige** TestFX headless : ni « Supprimer
    /// ce site », ni « Supprimer ce point » n'était cliquable dans un test.
    private final NotificateurModifiable notificateur = new NotificateurModifiable();

    /// Porteur de confirmation exposé aux tests (#1013) : `confirmateur().definir(stub)`.
    ConfirmateurModifiable confirmateur() {
        return confirmateur;
    }

    /// Porteur de compte rendu exposé aux tests (#1405) : `notificateur().definir(double)`.
    NotificateurModifiable notificateur() {
        return notificateur;
    }

    @FXML
    private Label valNumeroCarre;

    @FXML
    private Label valDepartement;

    @FXML
    private Label valProtocole;

    @FXML
    private Label valDateCreation;

    @FXML
    private Label valDerniereNuit;

    @FXML
    private Label valPassages;

    /// Cellule « VIGIE-CHIRO » du bandeau (#734) : elle accueille le badge de statut plateforme, reconstruit
    /// à chaque changement d'état (le badge porte son infobulle, on le remplace plutôt que de le muter).
    @FXML
    private VBox celluleStatutPlateforme;

    @FXML
    private Button boutonModifier;

    @FXML
    private Button boutonSupprimer;

    @FXML
    private Button boutonImporterNuit;

    @FXML
    private StackPane enveloppeSupprimer;

    @FXML
    private Button boutonOuvrirPortail;

    @FXML
    private StackPane enveloppeOuvrirPortail;

    @FXML
    private FlowPane cartesPoints;

    /// Repli d'état vide des points d'écoute (#791) : affiché quand [#cartesPoints] ne contient aucune
    /// carte (un FlowPane n'a pas de placeholder). Visibilité liée à la liste des cartes dans initialize.
    @FXML
    private Label lblAucunPoint;

    /// Révélation des points rapatriés **non utilisés** (#1738) : masqué s'il n'y en a pas, sinon il montre
    /// ou replie les points sans passage. Libellé et visibilité liés au ViewModel dans initialize.
    @FXML
    private Hyperlink lienPointsNonUtilises;

    @FXML
    private TableView<LignePassage> tablePassages;

    /// Menu ☰ « outils » (#921) : porte l'entrée « Colonnes… » (le clic droit de la table la porte aussi).
    @FXML
    private MenuButton menuOutils;

    @FXML
    private TableColumn<LignePassage, String> colDate;

    @FXML
    private TableColumn<LignePassage, String> colPoint;

    @FXML
    private TableColumn<LignePassage, String> colNumero;

    @FXML
    private TableColumn<LignePassage, String> colStatut;

    @FXML
    private TableColumn<LignePassage, String> colVerdict;

    @FXML
    private TableColumn<LignePassage, String> colEnregistreur;

    @FXML
    private TableColumn<LignePassage, String> colDepose;

    @Inject
    public SiteDetailController(
            SiteDetailViewModel viewModel,
            NavigationSites navigation,
            OuvrirPassage ouvrirPassage,
            Optional<OuvrirImportation> ouvrirImportation,
            OuvrirMultisite ouvrirMultisite,
            DepotDispositionColonnes depotColonnes,
            OuvreurDeLien ouvreurDeLien) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.ouvrirImportation = Objects.requireNonNull(ouvrirImportation, "ouvrirImportation");
        this.ouvrirMultisite = Objects.requireNonNull(ouvrirMultisite, "ouvrirMultisite");
        this.depotColonnes = Objects.requireNonNull(depotColonnes, "depotColonnes");
        this.ouvreurDeLien = Objects.requireNonNull(ouvreurDeLien, "ouvreurDeLien");
    }

    /// Charge le site à afficher (appelée par [NavigationSites] juste après le chargement FXML).
    public void afficher(Site site) {
        viewModel.chargerSite(site);
    }

    /// Rechargé par le [fr.univ_amu.iut.commun.view.Navigateur] quand on **revient** sur la fiche
    /// (← Retour ou fil d'Ariane) : un passage ouvert depuis le tableau a pu avancer pendant qu'on
    /// était dessus. On recharge points et passages du site courant pour réafficher les statuts/
    /// verdicts réels plutôt qu'un état périmé.
    @Override
    public void rafraichirAuRetour() {
        if (viewModel.siteCourant() != null) {
            viewModel.rafraichir();
        }
    }

    @Override
    public ReadOnlyObjectProperty<ZonesStatut> zonesStatutProperty() {
        return zonesStatut.getReadOnlyProperty();
    }

    /// Remplace le badge de la cellule « VIGIE-CHIRO » par celui de `statut` (le libellé de la cellule,
    /// premier enfant, reste en place).
    private void afficherStatutPlateforme(StatutPlateforme statut) {
        celluleStatutPlateforme
                .getChildren()
                .removeIf(noeud -> noeud.getStyleClass().contains("badge"));
        celluleStatutPlateforme.getChildren().add(BadgeStatutPlateforme.creer(statut));
    }

    @FXML
    private void initialize() {
        // Densité/habillage de table uniformes (#690) + table navigable au double-clic (#792).
        TableDonnees.uniformiserNavigable(tablePassages);
        // Sélecteur de colonnes (#921) + menu de ligne « Ouvrir le passage » (#1796) au clic droit ; le ☰
        // « outils » garde « Colonnes… ». Disposition retenue par écran (#994).
        GestionnaireColonnes.installerEtPersister(
                tablePassages,
                menuOutils,
                colonnesPassages(),
                depotColonnes,
                "sites",
                "principale",
                MenuLigne.item("Ouvrir le passage", tablePassages, this::ouvrirPassageDeLaLigne));
        // Titre (nom du site) et sous-titre (commune/protocole) déportés en barre de statut (#693) :
        // contexte à gauche, résumé au centre.
        zonesStatut.bind(Bindings.createObjectBinding(
                () -> new ZonesStatut(
                        viewModel.titreProperty().get(),
                        viewModel.sousTitreProperty().get(),
                        ""),
                viewModel.titreProperty(),
                viewModel.sousTitreProperty()));
        valNumeroCarre.textProperty().bind(viewModel.numeroCarreProperty());
        valDepartement.textProperty().bind(viewModel.departementProperty());
        valProtocole.textProperty().bind(viewModel.protocoleProperty());
        valDateCreation.textProperty().bind(viewModel.dateCreationProperty());
        valDerniereNuit.textProperty().bind(viewModel.derniereNuitProperty());
        valPassages.textProperty().bind(viewModel.passagesDeLAnneeProperty());
        // Badge de statut plateforme (#734), unifié avec celui des cartes de « Mes sites ». Le détail
        // affiche les TROIS états, « non enregistré » compris : c'est ici qu'on se demande pourquoi le
        // dépôt reste impossible, et l'infobulle du badge le dit.
        afficherStatutPlateforme(viewModel.statutPlateformeProperty().get());
        viewModel.statutPlateformeProperty().addListener((observable, avant, apres) -> afficherStatutPlateforme(apres));
        boutonSupprimer
                .disableProperty()
                .bind(viewModel.suppressionPossibleProperty().not());
        // Tooltip d'explication du blocage, posé sur l'enveloppe (un Button désactivé n'en affiche pas) et
        // lié à l'état, via le composant partagé (#789, généralise ce patron d'origine).
        IndicateurBlocage.expliquer(
                enveloppeSupprimer,
                Bindings.when(viewModel.suppressionPossibleProperty())
                        .then("Supprimer ce site et ses points d'écoute.")
                        .otherwise("Suppression impossible : ce site porte des passages."
                                + " Supprimez d'abord les passages rattachés."));
        boutonModifier.setTooltip(new Tooltip("Modifier la fiche du site (carré, nom, protocole…)."));
        // « Ouvrir sur Vigie-Chiro » (#1124) : actif seulement quand le site est rattaché au portail ;
        // désactivé, il documente ce qui manque (affordance #789) plutôt que de disparaître.
        boutonOuvrirPortail
                .disableProperty()
                .bind(viewModel.lienPortailProperty().isEmpty());
        IndicateurBlocage.expliquer(
                enveloppeOuvrirPortail,
                Bindings.when(viewModel.lienPortailProperty().isNotEmpty())
                        .then("Ouvre la page de ce site sur le portail Vigie-Chiro (navigateur).")
                        .otherwise("Ce site n'est pas encore relié à Vigie-Chiro : connectez-vous ou"
                                + " synchronisez depuis « Mes sites »."));
        // « Importer une nuit » n'apparaît que si la feature `importation` est activée (feature-flag #1087).
        boolean importActif = ouvrirImportation.isPresent();
        boutonImporterNuit.setVisible(importActif);
        boutonImporterNuit.setManaged(importActif);
        configurerColonnes();
        tablePassages.setItems(viewModel.passages());
        // Double-clic → ouvre le passage ; clic droit sélectionne la ligne pour le menu de ligne (#1796).
        DoubleClicLigne.installer(tablePassages, this::ouvrirPassageDeLaLigne);
        // Cartes de points d'écoute + repli d'état vide (#791) : câblage extrait dans CartesPointsSite
        // pour alléger ce controller (seuil de cohésion PMD, #1087). Les deux porteurs de dialogue sont
        // ceux de l'écran (#1405) : les cartes fabriquaient jusqu'ici leur propre confirmateur, que
        // personne n'exposait - donc que personne ne pouvait remplacer en test.
        CartesPointsSite.installer(
                cartesPoints, lblAucunPoint, viewModel, navigation, ouvrirMultisite, confirmateur, notificateur);
        // Révélation des points rapatriés non utilisés (#1738) : la synchro ramène tous les points du carré,
        // mais la fiche ne montre par défaut que ceux qui SERVENT (au moins un passage). Ce lien n'apparaît
        // que s'il reste des points à révéler ; son libellé suit l'état (Afficher N… / Masquer…).
        lienPointsNonUtilises
                .visibleProperty()
                .bind(viewModel.nombrePointsMasquesProperty().greaterThan(0));
        lienPointsNonUtilises.managedProperty().bind(lienPointsNonUtilises.visibleProperty());
        lienPointsNonUtilises
                .textProperty()
                .bind(Bindings.when(viewModel.afficherTousLesPointsProperty())
                        .then("Masquer les points non utilisés")
                        .otherwise(Bindings.concat(
                                "Afficher ",
                                viewModel.nombrePointsMasquesProperty().asString(),
                                " point(s) rapatrié(s) non utilisé(s)")));
        lienPointsNonUtilises.setOnAction(evenement -> viewModel
                .afficherTousLesPointsProperty()
                .set(!viewModel.afficherTousLesPointsProperty().get()));
    }

    /// Contexte d'identité (carré/code/nom) transmis à M-Passage pour éviter une dépendance
    /// `passage → sites` : la vue passage affiche ces libellés sans rejoindre les tables `sites`.
    private ContexteSite contexteSite(LignePassage ligne) {
        Site site = viewModel.siteCourant();
        return new ContexteSite(site.numeroCarre(), ligne.codePoint(), site.nomConvivial());
    }

    /// Ouvre M-Passage sur `ligne` (double-clic et « Ouvrir le passage » du menu de ligne, #1796).
    private void ouvrirPassageDeLaLigne(LignePassage ligne) {
        ouvrirPassage.ouvrir(ligne.idPassage(), contexteSite(ligne));
    }

    @FXML
    private void ajouterPoint() {
        navigation.ouvrirModaleCreationPoint(fenetre(), viewModel.siteCourant(), viewModel::rafraichir);
    }

    /// Ouvre l'assistant « Importer une nuit » avec ce site déjà pré-rattaché (raccourci contextuel).
    /// Désactivable (bouton masqué si absente, #1087) : le contrat socle est neutralisé quand la
    /// feature `importation` est désactivée.
    @FXML
    private void importerNuit() {
        ouvrirImportation.ifPresent(
                ouvrir -> ouvrir.ouvrirPourSite(viewModel.siteCourant().id()));
    }

    /// « Voir sur la carte » : ouvre la vue multi-sites centrée et surlignée sur le carré de ce site.
    @FXML
    private void voirSurCarte() {
        Site site = viewModel.siteCourant();
        if (site != null) {
            ouvrirMultisite.ouvrirSurCarre(site.numeroCarre());
        }
    }

    /// Ouvre la boîte d'édition pré-remplie ; à la validation, applique la modification via le
    /// ViewModel (qui recharge la fiche). Un refus métier (carré déjà pris) ou un format invalide
    /// (R1) est rapporté sans quitter l'écran.
    /// Ouvre la page du site sur le portail Vigie-Chiro (#1124) — vérification visuelle du rattachement.
    @FXML
    private void ouvrirSurVigieChiro() {
        String lien = viewModel.lienPortailProperty().get();
        if (lien != null && !lien.isBlank()) {
            ouvreurDeLien.ouvrir(lien);
        }
    }

    @FXML
    private void modifierSite() {
        // La modale porte la saisie, la validation en direct et le refus métier (#1431). Le Dialog bâti
        // ici se terminait par un showAndWait : le geste était injouable dans un test, et sa capture de
        // documentation était une réplique reconstruite à la main.
        navigation.ouvrirModaleEditionSite(fenetre(), viewModel.siteCourant(), viewModel::rafraichir);
    }

    @FXML
    private void supprimerSite() {
        if (!confirmateur.confirmer("Supprimer ce site et ses points d'écoute ?")) {
            return;
        }
        try {
            viewModel.supprimerSite();
            navigation.ouvrirAccueil();
        } catch (RegleMetierException refus) {
            alerteErreur(refus.getMessage());
        }
    }

    /// Colonnes des passages du site proposées au sélecteur (#921). « Date » est l'identité (verrouillée).
    private List<GestionnaireColonnes.Colonne> colonnesPassages() {
        return List.of(
                new GestionnaireColonnes.Colonne(colDate, "Date", true),
                new GestionnaireColonnes.Colonne(colPoint, "Point", false),
                new GestionnaireColonnes.Colonne(colNumero, "N° passage", false),
                new GestionnaireColonnes.Colonne(colStatut, "Statut", false),
                new GestionnaireColonnes.Colonne(colVerdict, "Verdict", false),
                new GestionnaireColonnes.Colonne(colEnregistreur, "Enregistreur", false),
                new GestionnaireColonnes.Colonne(colDepose, "Déposé le", false));
    }

    private void configurerColonnes() {
        colDate.setCellValueFactory(cd -> valeur(cd.getValue().date()));
        colPoint.setCellValueFactory(cd -> valeur(cd.getValue().codePoint()));
        colNumero.setCellValueFactory(cd -> valeur(cd.getValue().numeroPassage()));
        colStatut.setCellValueFactory(cd -> valeur(cd.getValue().statutLibelle()));
        colVerdict.setCellValueFactory(cd -> valeur(cd.getValue().verdictLibelle()));
        colEnregistreur.setCellValueFactory(cd -> valeur(cd.getValue().enregistreur()));
        colDepose.setCellValueFactory(cd -> valeur(cd.getValue().deposeLe()));
        colStatut.setCellFactory(colonne -> ColonneBadge.cellule(LignePassage::statutClasseCss));
        colVerdict.setCellFactory(colonne -> ColonneBadge.cellule(LignePassage::verdictClasseCss));
    }

    /// Texte saisi → `null` si vide (champ optionnel non renseigné).
    private static String vide(String texte) {
        return texte == null || texte.isBlank() ? null : texte;
    }

    /// Valeur de champ → chaîne vide si `null` (pour pré-remplir un `TextField`).
    private static String ouVide(String texte) {
        return texte == null ? "" : texte;
    }

    /// Valeurs saisies dans la boîte d'édition d'un site (carré requis ; nom et commentaire
    /// optionnels ; protocole choisi dans la liste).

    private Window fenetre() {
        return cartesPoints.getScene().getWindow();
    }

    /// L'action n'a pas eu lieu : l'écran ne bouge pas, et l'utilisateur sait pourquoi.
    private void alerteErreur(String message) {
        notificateur.notifier(NiveauNotification.AVERTISSEMENT, "Action impossible", message);
    }

    private static ReadOnlyStringWrapper valeur(String texte) {
        return new ReadOnlyStringWrapper(texte);
    }
}
