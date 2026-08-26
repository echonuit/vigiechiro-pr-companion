package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.TableDonnees;
import fr.univ_amu.iut.multisite.model.LignePassage;
import java.util.List;
import java.util.Objects;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/// Controller du **tableau des passages** de la vue multisite (`PanneauPassages.fxml`, #2745).
///
/// Sous-vue de [MultisiteController], à qui elle retire la table et ses onze colonnes. Elle ne connaît
/// que le comportement **propre** à la table : habillage, multi-sélection, configuration des colonnes,
/// tri par clic d'en-tête.
///
/// Ce qui a besoin de la table **et** d'un nœud du parent reste là-bas, et l'obtient par [#table()] :
/// le sélecteur de colonnes s'ancre sur le menu ☰ de la barre de filtres, et le menu de ligne comme le
/// double-clic appellent des gestes de l'écran (ouvrir le passage, l'écouter). Aucun nœud du parent
/// n'entre donc ici, et aucun rappel non plus.
///
/// Elle reçoit ce dont elle a besoin du parent et n'injecte rien (ADR 2745, gardé par
/// `DecisionsRespecteesTest#une_sous_vue_ne_se_procure_pas_ce_qui_doit_etre_unique`).
public class PanneauPassagesController {

    @FXML
    private TableView<LignePassage> tableLignes;

    @FXML
    private TableColumn<LignePassage, String> colCarre;

    @FXML
    private TableColumn<LignePassage, String> colNomSite;

    @FXML
    private TableColumn<LignePassage, String> colPoint;

    /// Commune du point (#3163) : vide tant que le GPS n'a pas résolu de commune.
    @FXML
    private TableColumn<LignePassage, String> colCommune;

    @FXML
    private TableColumn<LignePassage, String> colAnnee;

    @FXML
    private TableColumn<LignePassage, String> colNumero;

    @FXML
    private TableColumn<LignePassage, java.time.LocalDate> colDate;

    @FXML
    private TableColumn<LignePassage, String> colStatut;

    @FXML
    private TableColumn<LignePassage, String> colVerdict;

    /// État de l'analyse Tadarida de la nuit (#1338) : un état observé, pas une vérité.
    @FXML
    private TableColumn<LignePassage, String> colAnalyse;

    /// Campagne de rattachement (#2355) : vide si la nuit n'en a pas, ou si la feature est coupée.
    @FXML
    private TableColumn<LignePassage, String> colCampagne;

    /// Câble la table sur les lignes **du parent**, appelée depuis son `initialize()`.
    ///
    /// @param lignes les passages à montrer, déjà filtrés et ordonnés par le ViewModel
    void installer(ObservableList<LignePassage> lignes) {
        Objects.requireNonNull(lignes, "lignes");

        // Densité/habillage de table uniformes (#690) + table navigable au double-clic (#792).
        TableDonnees.uniformiserNavigable(tableLignes);
        // Multi-sélection (#2357, lot 3) : plusieurs lignes se cochent pour recevoir la même action.
        // Les gestes de ligne existants (double-clic, « Écouter le passage ») continuent de lire
        // `selectedItem`, qui reste la DERNIÈRE ligne cochée : rien ne change pour eux.
        tableLignes.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ColonnesMultisite.configurer(colonnes());

        // #145 : tri par clic en-tête. Un SortedList lié au comparateur de la table s'applique par-dessus
        // la liste (déjà filtrée/ordonnée par le VM) ; performant (~4000 lignes) et le tri colonne
        // persiste à travers les rafraîchissements de filtres.
        SortedList<LignePassage> lignesTriees = new SortedList<>(lignes);
        lignesTriees.comparatorProperty().bind(tableLignes.comparatorProperty());
        tableLignes.setItems(lignesTriees);
    }

    /// La table, pour ce qui a besoin d'elle **et** d'un nœud du parent : sélecteur de colonnes ancré au
    /// menu ☰, menu de ligne, double-clic, et les gestes qui lisent la sélection.
    TableView<LignePassage> table() {
        return tableLignes;
    }

    /// Les colonnes proposées au sélecteur d'affichage (#919), dans l'ordre voulu. Le parent installe
    /// ce sélecteur, qui s'ancre sur le menu ☰ de sa barre de filtres, et le rejoue pour les vues
    /// mémorisées : il lui faut donc la liste, pas les colonnes une à une.
    List<GestionnaireColonnes.Colonne> pourLeSelecteur() {
        return ColonnesMultisite.pourLeSelecteur(colonnes());
    }

    /// Les colonnes regroupées, telles que [ColonnesMultisite] les attend.
    private ColonnesMultisite.Colonnes colonnes() {
        return new ColonnesMultisite.Colonnes(
                colCommune,
                colCarre,
                colNomSite,
                colPoint,
                colAnnee,
                colNumero,
                colDate,
                colStatut,
                colVerdict,
                colAnalyse,
                colCampagne);
    }
}
