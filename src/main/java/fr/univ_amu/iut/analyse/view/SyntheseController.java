package fr.univ_amu.iut.analyse.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.analyse.model.LigneSynthese;
import fr.univ_amu.iut.analyse.viewmodel.SyntheseViewModel;
import fr.univ_amu.iut.commun.model.SeuilsActivite;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

/// Contrôleur de l'écran **Synthèse de la nuit** (#2351). Pur câblage : tout vient du
/// [SyntheseViewModel].
///
/// **Le milieu par défaut est « national »**, et c'est une position, pas un manque : aucune donnée de
/// l'application ne dit si un point d'écoute est en forêt ou en ville. Une déclinaison devinée de
/// travers changerait la classe d'activité en silence.
public class SyntheseController implements EmplacementNavigation {

    /// Entrée du sélecteur qui **ne décline rien** : la comparaison reste nationale.
    private static final String SANS_MILIEU = "National (aucun milieu)";

    private final SyntheseViewModel viewModel;
    private final OuvrirSite ouvrirSite;
    private final OuvrirPassage ouvrirPassage;

    private ContextePassage contexte;

    @FXML
    private Label lblContexte;

    @FXML
    private CheckBox chkValideesSeulement;

    @FXML
    private Label lblMilieu;

    @FXML
    private ChoiceBox<String> cbMilieu;

    @FXML
    private TableView<LigneSynthese> tableSynthese;

    @FXML
    private TableColumn<LigneSynthese, String> colEspece;

    @FXML
    private TableColumn<LigneSynthese, String> colGroupe;

    @FXML
    private TableColumn<LigneSynthese, String> colContacts;

    @FXML
    private TableColumn<LigneSynthese, String> colFichiers;

    @FXML
    private TableColumn<LigneSynthese, String> colActivite;

    @FXML
    private TableColumn<LigneSynthese, String> colSeuils;

    @FXML
    private Label lblReferentiel;

    @FXML
    private VBox blocAvertissement;

    @FXML
    private Label lblAvertissement;

    @FXML
    private Label lblCitation;

    @Inject
    public SyntheseController(SyntheseViewModel viewModel, OuvrirSite ouvrirSite, OuvrirPassage ouvrirPassage) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
    }

    @FXML
    private void initialize() {
        configurerColonnes();

        tableSynthese.setItems(viewModel.lignes());
        chkValideesSeulement.selectedProperty().bindBidirectional(viewModel.validesSeulementProperty());
        lblContexte.textProperty().bind(viewModel.contexteNuitProperty());

        // Le référentiel employé, nommé en toutes lettres : une classe dont on ignore la référence est
        // un oracle.
        lblReferentiel
                .textProperty()
                .bind(viewModel.referentielEmployeProperty().map(r -> "Comparé au référentiel : " + r));

        // Permanent, jamais repliable : si l'avertissement ne voyage pas avec la donnée, il ne sert à
        // rien. La citation l'accompagne — la source est libre d'usage AVEC citation obligatoire.
        lblAvertissement.setText(viewModel.avertissement());
        lblCitation.setText("Source : " + viewModel.citation());

        configurerMilieux();
    }

    /// Peuple le sélecteur de milieu, ou **efface toute la colonne d'activité** quand le référentiel
    /// n'est pas exploitable. Masquer plutôt qu'afficher des cellules vides : une colonne blanche se
    /// lirait comme une donnée manquante, alors que le tableau de comptages reste, lui, entier.
    private void configurerMilieux() {
        if (!viewModel.referentielDisponible()) {
            colActivite.setVisible(false);
            colSeuils.setVisible(false);
            cbMilieu.setVisible(false);
            cbMilieu.setManaged(false);
            lblMilieu.setVisible(false);
            lblMilieu.setManaged(false);
            lblReferentiel.setText("Référentiel d'activité indisponible : le tableau reste exploitable.");
            return;
        }
        List<String> milieux = new java.util.ArrayList<>();
        milieux.add(SANS_MILIEU);
        milieux.addAll(viewModel.milieuxDisponibles());
        cbMilieu.setItems(FXCollections.observableArrayList(milieux));
        cbMilieu.getSelectionModel().selectFirst();
        cbMilieu.valueProperty()
                .addListener((observable, avant, choisi) ->
                        viewModel.milieuProperty().set(SANS_MILIEU.equals(choisi) ? null : choisi));
    }

    private void configurerColonnes() {
        colEspece.setCellValueFactory(c -> texte(c.getValue().nomEspece()));
        colGroupe.setCellValueFactory(c -> texte(ouTiret(c.getValue().groupe())));
        colContacts.setCellValueFactory(c -> texte(c.getValue().contacts()));
        colFichiers.setCellValueFactory(c -> texte(c.getValue().fichiers()));
        // Jamais vide : l'absence de classe a plusieurs sens, et la cellule les distingue.
        colActivite.setCellValueFactory(c -> texte(c.getValue().libelleClasse()));
        colSeuils.setCellValueFactory(c -> texte(libelleSeuils(c.getValue())));
    }

    /// « Q25 = 10 · Q75 = 100 · Q98 = 1 000 », ou un tiret faute de seuils. Affichés **à côté** de la
    /// classe pour qu'elle reste contestable.
    private static String libelleSeuils(LigneSynthese ligne) {
        return ligne.seuils().map(SyntheseController::formater).orElse("—");
    }

    private static String formater(SeuilsActivite seuils) {
        return "Q25 = " + seuils.q25() + " · Q75 = " + seuils.q75() + " · Q98 = " + seuils.q98();
    }

    private static String ouTiret(String valeur) {
        return valeur == null || valeur.isBlank() ? "—" : valeur;
    }

    private static ObservableValue<String> texte(Object valeur) {
        return new ReadOnlyStringWrapper(String.valueOf(valeur));
    }

    /// Ouvre la synthèse d'un passage. Appelé par [NavigationSynthese] après le chargement du FXML.
    public void ouvrirSur(ContextePassage passage) {
        this.contexte = passage;
        viewModel.charger(passage.idPassage(), passage.site().numeroCarre());
    }

    /// Emplacement dans le fil d'Ariane : `Mes sites › Carré N › Passage N° X › Synthèse`.
    @Override
    public List<Lieu> emplacement() {
        if (contexte == null) {
            return List.of(Lieu.courant("Synthèse de la nuit"));
        }
        return EmplacementPassage.emplacementEnfant(contexte, ouvrirSite, ouvrirPassage, "Synthèse de la nuit");
    }
}
