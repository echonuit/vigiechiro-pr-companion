package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.MenuCopier;
import fr.univ_amu.iut.lot.viewmodel.LigneDepot;
import java.nio.file.Path;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/// Controller de l'**étape 3 du dépôt** (`EtapeTeleversement.fxml`, #2745) : téléverser la nuit sur
/// Vigie-Chiro, avec son repli manuel par le dossier `depot/`.
///
/// Sous-vue de [LotController], à qui elle retire dix champs `@FXML`, quatre gestionnaires et le
/// câblage de la table de dépôt. La carte est une unité de sens à elle seule : une étape du workflow,
/// sa table, ses actions et son compte rendu de fin.
///
/// ⚠️ Elle reçoit ses appuis du parent ([#installer(AppuisTeleversement)]) et n'injecte rien : les
/// ViewModel du dépôt sont non-singleton, et se les procurer en donnerait de nouveaux, vides. La règle
/// est gardée par `DecisionsRespecteesTest#une_sous_vue_ne_s_injecte_pas_son_modele` (ADR 2745).
public class EtapeTeleversementController {

    private AppuisTeleversement appuis;

    /// Chemin du sous-dossier `depot/`, cible du téléversement manuel.
    @FXML
    private Label lblCheminDepot;

    /// Enveloppe du bouton de téléversement : porte l'infobulle du grisage (#789), qu'un Button
    /// désactivé n'afficherait pas, et disparaît avec lui hors application connectée.
    @FXML
    private StackPane enveloppeTeleverser;

    @FXML
    private Button btnTeleverser;

    @FXML
    private FontIcon iconeTeleverser;

    @FXML
    private StackPane enveloppeOuvrirDepot;

    @FXML
    private Button btnOuvrirDepot;

    /// Annulation coopérative (#1044) : visible seulement pendant un téléversement.
    @FXML
    private Button btnAnnulerDepot;

    @FXML
    private Button btnReinitialiserDepot;

    /// Table de dépôt (#983) : une ligne par unité téléversée, réhydratée à l'ouverture.
    @FXML
    private TableView<LigneDepot> tableDepot;

    /// Compte rendu chiffré de fin de dépôt (#2653), sous la table qu'il ne remplace pas.
    @FXML
    private VBox zoneCompteRenduDepot;

    /// Câble l'étape sur les appuis **du parent**, appelée depuis son `initialize()`.
    void installer(AppuisTeleversement appuis) {
        this.appuis = Objects.requireNonNull(appuis, "appuis");

        // Étape ③ : la cible du téléversement est le sous-dossier depot/ (archives ZIP), pas la session.
        lblCheminDepot.textProperty().bind(appuis.viewModel().cheminDepotProperty());

        cablerTeleversement();
        cablerOuvertureManuelle();
        cablerTableDepot();
    }

    /// Bouton de téléversement : visible seulement quand l'application est connectée, actif une fois le
    /// dépôt préparé, hors génération et hors envoi en cours.
    private void cablerTeleversement() {
        boolean disponible = appuis.depotViewModel().disponible();
        // L'étape ② n'est plus un passage obligé quand on est connecté (#1998) : le stepper doit le
        // savoir, et seul le controller connaît les deux ViewModels.
        appuis.viewModel().declarerDepotAutomatiqueDisponible(disponible);
        enveloppeTeleverser.setVisible(disponible);
        enveloppeTeleverser.setManaged(disponible);
        btnTeleverser
                .disableProperty()
                .bind(appuis.viewModel()
                        .peutDeposerProperty()
                        .not()
                        .or(appuis.depotViewModel().enCoursProperty())
                        .or(appuis.viewModel().generationEnCoursProperty()));
        // Explique le grisage (#789) au survol de l'enveloppe : cas « déjà déposé » distingué des autres.
        IndicateurBlocage.expliquer(
                enveloppeTeleverser,
                Bindings.when(appuis.viewModel().deposeProperty())
                        .then("Passage déjà déposé sur Vigie-Chiro : le téléversement est terminé.")
                        .otherwise(Bindings.when(btnTeleverser.disableProperty())
                                .then("Téléversement possible une fois le dépôt préparé (statut « Prêt à"
                                        + " déposer »), et hors génération ou envoi en cours. Générer les"
                                        + " archives n'est pas un préalable : le téléversement produit"
                                        + " lui-même ce dont il a besoin.")
                                .otherwise("Téléverser la nuit sur Vigie-Chiro (marque ensuite le passage"
                                        + " déposé).")));
    }

    /// « Ouvrir le dossier » : actif seulement quand les archives sont réellement prêtes (#259). Les ZIP
    /// sont écrits sous leur nom final pendant la génération : ouvrir avant la fin exposerait un partiel.
    private void cablerOuvertureManuelle() {
        btnOuvrirDepot
                .disableProperty()
                .bind(Bindings.isEmpty(appuis.viewModel().suiviLignes().lignes())
                        .or(appuis.viewModel().generationEnCoursProperty()));
        IndicateurBlocage.expliquer(
                enveloppeOuvrirDepot,
                Bindings.when(btnOuvrirDepot.disableProperty())
                        .then("Aucune archive de dépôt à ouvrir : générez d'abord les archives (ou patientez"
                                + " la fin de la génération en cours).")
                        .otherwise("Ouvrir le sous-dossier « depot/ » pour un dépôt manuel des archives ZIP."));
    }

    /// Câble la table de dépôt (#983) : lignes persistées (`depot_unite` #981) + événements du moteur
    /// reprenable (#982). Visible seulement quand un dépôt a été entamé (liaison vivante sur la liste).
    private void cablerTableDepot() {
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
                        tableDepot, new MenuCopier.Entree<>("Identifiant", ligne -> texte(ligne.identifiant()))));
        GestionnaireColonnes.persister(tableDepot, colonnesDepot, appuis.depotColonnes(), "lot", "depot");
        tableDepot.setItems(appuis.depotViewModel().suiviLignes().lignes());
        var depotEntame =
                Bindings.isNotEmpty(appuis.depotViewModel().suiviLignes().lignes());
        lierAffichage(tableDepot, depotEntame);
        // « Réinitialiser le dépôt » (#984) : visible dès qu'un plan existe, désactivé pendant un dépôt.
        lierAffichage(btnReinitialiserDepot, depotEntame);
        btnReinitialiserDepot.disableProperty().bind(appuis.depotViewModel().enCoursProperty());
        // Étape 3 (téléverser / reprendre, et l'annulation coopérative) : câblage déporté, comme l'étape 4.
        EtapeTeleverserUI.cabler(btnTeleverser, iconeTeleverser, btnAnnulerDepot, appuis.depotViewModel());
        // Compte rendu de fin de dépôt (#2653) : l'action suivante est fournie par le parent, parce que
        // « Lancer la participation » est l'étape ④ de l'écran et que le ViewModel n'a pas à savoir où
        // mènent ses boutons. Même geste que le bouton de l'étape ④, pour qu'il n'y ait qu'un seul chemin.
        CompteRenduDepotUI.cabler(zoneCompteRenduDepot, appuis.depotViewModel(), appuis.lancerParticipation());
    }

    /// Téléverse la nuit sur Vigie-Chiro. Le statut (« Dépôt en cours », « Déposé ») est posé par le
    /// moteur reprenable (#982) ; l'IHM ne fait que le restituer, d'où le rechargement en fin de course.
    ///
    /// L'**annulation** (#1044) reste coopérative côté ViewModel : « Annuler le dépôt » pose un drapeau
    /// que le moteur lit entre deux fichiers, termine l'unité en vol et rend un bilan honnête par le
    /// chemin de succès (jamais d'unité fantôme ; « Reprendre le dépôt » ne renverra que le manquant).
    @FXML
    private void televerserVigieChiro() {
        Long idPassage = appuis.idPassage().get();
        appuis.depotViewModel().marquerEnCours();
        RelaisSuiviDepot suivi = new RelaisSuiviDepot(
                appuis.depotViewModel().suiviLignes(), appuis.executeur().surFilJavaFx());
        appuis.executeur()
                .executer(
                        () -> appuis.depotViewModel().televerser(idPassage, suivi),
                        bilan -> {
                            appuis.depotViewModel().appliquerBilan(bilan);
                            // Statut honnête (#982) : le moteur a déjà posé le bon statut (jamais
                            // « Déposé » sur un dépôt partiel) ; on recharge l'état pour le refléter.
                            appuis.viewModel().ouvrirSur(idPassage);
                        },
                        appuis.depotViewModel()::echec);
    }

    /// Demande l'annulation coopérative du dépôt en cours (#1044) : déléguée au ViewModel, le moteur
    /// s'arrête entre deux fichiers.
    @FXML
    private void annulerDepotVigieChiro() {
        appuis.depotViewModel().demanderAnnulation();
    }

    /// Ouvre le sous-dossier `depot/` dans le gestionnaire de fichiers (#251), pour aider au
    /// téléversement manuel. Sans chemin, le bouton est désactivé ; l'ouverture ne lève jamais.
    ///
    /// Visible pour le parent : le menu de ligne de la table des **archives** (étape ②) offre le même
    /// geste, les archives d'un dépôt vivant toutes dans ce dossier (#1796).
    @FXML
    void ouvrirDossierDepot() {
        String chemin = appuis.viewModel().cheminDepotProperty().get();
        if (chemin != null && !chemin.isBlank()) {
            appuis.ouvreurDeLien().ouvrir(Path.of(chemin).toUri().toString());
        }
    }

    /// Réinitialise le dépôt (#984) : efface le suivi local pour permettre un nouveau téléversement (ex.
    /// dépôt orphelin d'avant le rattachement `lien_participation`). Recharge la table (plan vidé) et
    /// l'état du passage (retour « Prêt à déposer »).
    @FXML
    private void reinitialiserDepot() {
        if (appuis.confirmateur().confirmer("""
                Réinitialiser le dépôt de cette nuit ?

                Le suivi local est effacé pour permettre un nouveau téléversement ; les archives ZIP sur disque et la participation Vigie-Chiro sont conservées.""")) {
            appuis.depotViewModel().reinitialiser(appuis.idPassage().get());
            appuis.viewModel().ouvrirSur(appuis.idPassage().get());
        }
    }

    /// Valeur à copier, ou chaîne vide : on ne met jamais « null » dans le presse-papier.
    private static String texte(String valeur) {
        return valeur == null ? "" : valeur;
    }

    /// Lie **ensemble** la visibilité et la prise de place, pour qu'un nœud masqué ne laisse pas de trou.
    /// Même idiome que dans [LotController] ; le remonter dans `commun.view` mérite sa propre issue,
    /// plusieurs écrans le réécrivant chacun de leur côté.
    private static void lierAffichage(Node noeud, ObservableValue<? extends Boolean> condition) {
        noeud.visibleProperty().bind(condition);
        noeud.managedProperty().bind(condition);
    }
}
