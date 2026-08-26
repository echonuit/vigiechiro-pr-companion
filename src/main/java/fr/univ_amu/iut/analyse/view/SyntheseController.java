package fr.univ_amu.iut.analyse.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.analyse.model.ExportSyntheseCsv;
import fr.univ_amu.iut.analyse.model.LigneSynthese;
import fr.univ_amu.iut.analyse.viewmodel.SyntheseViewModel;
import fr.univ_amu.iut.commun.model.LibellesReferentiel;
import fr.univ_amu.iut.commun.model.SeuilsActivite;
import fr.univ_amu.iut.commun.view.BandeauRetour;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.RafraichirAuRetour;
import fr.univ_amu.iut.commun.view.SelecteurFichierJavaFx;
import fr.univ_amu.iut.commun.view.SelecteurFichierModifiable;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import fr.univ_amu.iut.validation.model.MarqueurEspecesAEnjeu;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/// Contrôleur de l'écran **Synthèse de la nuit** (#2351). Pur câblage : tout vient du
/// [SyntheseViewModel].
///
/// **Le milieu par défaut est « national »**, et c'est une position, pas un manque : aucune donnée de
/// l'application ne dit si un point d'écoute est en forêt ou en ville. Une déclinaison devinée de
/// travers changerait la classe d'activité en silence.
public class SyntheseController implements EmplacementNavigation, RafraichirAuRetour {

    /// Entrée du sélecteur qui **ne décline rien** : la comparaison reste nationale.
    private static final String SANS_MILIEU = "National (aucun milieu)";

    private final SyntheseViewModel viewModel;
    private final OuvrirSite ouvrirSite;
    private final OuvrirPassage ouvrirPassage;
    private final MarqueurEspecesAEnjeu marqueurEnjeu;

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

    @FXML
    private Button boutonExporter;

    @FXML
    private HBox bandeauRetour;

    @FXML
    private Label lblRetour;

    @FXML
    private Button btnFermerRetour;

    /// Le sélecteur de fichier passe par un **port** : un `FileChooser` natif ouvert par `showAndWait()`
    /// fige un test TestFX headless dès la première ligne du geste. Les tests y branchent un double qui
    /// répond un chemin, ou rien du tout (l'utilisateur a annulé).
    private final SelecteurFichierModifiable selecteur = new SelecteurFichierModifiable(
            // Le champ @FXML est déclaré plus haut mais reste nul jusqu'au chargement : la fenêtre se
            // demande donc au clic, pas à la construction.
            new SelecteurFichierJavaFx(() -> this.boutonExporter.getScene().getWindow()));

    /// Le porteur du sélecteur, pour qu'un test y substitue son double.
    SelecteurFichierModifiable selecteur() {
        return selecteur;
    }

    @Inject
    public SyntheseController(
            SyntheseViewModel viewModel,
            OuvrirSite ouvrirSite,
            OuvrirPassage ouvrirPassage,
            EspecesPrioritaires especesPrioritaires) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.marqueurEnjeu =
                new MarqueurEspecesAEnjeu(Objects.requireNonNull(especesPrioritaires, "especesPrioritaires"));
    }

    @FXML
    private void initialize() {
        configurerColonnes();

        tableSynthese.setItems(viewModel.lignes());
        // Une nuit sans espèce le DIT, dans les mots du domaine. Le texte par défaut de JavaFX
        // (« aucun contenu dans la table ») décrit le composant, pas la nuit : il laisse croire à un
        // écran qui n'a pas fini de charger.
        tableSynthese.setPlaceholder(placeholder());
        chkValideesSeulement.selectedProperty().bindBidirectional(viewModel.validesSeulementProperty());
        lblContexte.textProperty().bind(viewModel.contexteNuitProperty());

        // Permanent, jamais repliable : si l'avertissement ne voyage pas avec la donnée, il ne sert à
        // rien. La citation l'accompagne : la source est libre d'usage AVEC citation obligatoire.
        lblAvertissement.setText(viewModel.avertissement());
        lblCitation.setText("Source : " + viewModel.citation());

        // Rien à exporter, le bouton le dit en se grisant plutôt que d'écrire un fichier d'en-têtes seuls
        // que l'utilisateur croirait vide par erreur.
        boutonExporter.disableProperty().bind(Bindings.isEmpty(viewModel.lignes()));
        BandeauRetour.installer(
                bandeauRetour, lblRetour, btnFermerRetour, viewModel.retourProperty(), viewModel::effacerRetour);

        configurerMilieux();
    }

    /// Exporte le tableau **tel qu'il est affiché** (bascule et milieu compris) en CSV, le pendant à
    /// l'écran de `synthetiser-passage`, sur le même formateur pur.
    @FXML
    private void exporter() {
        selecteur
                .enregistrerFichier("Exporter la synthèse de la nuit en CSV", "synthese-nuit.csv", FiltreFichier.csv())
                .ifPresent(this::ecrire);
    }

    private void ecrire(Path fichier) {
        try {
            List<LigneSynthese> lignes = viewModel.lignesExport();
            // La disponibilité voyage avec le fichier (#3048) : l'écran retire ses colonnes quand le
            // référentiel manque, le CSV ne le peut pas (ses en-têtes sont un contrat), il le **dit**.
            ExportSyntheseCsv.ecrire(lignes, viewModel.contexteActivite(), viewModel.referentielDisponible(), fichier);
            viewModel.signalerExport(String.valueOf(fichier.getFileName()), lignes.size());
        } catch (IOException | RuntimeException echec) {
            // Sans ce rattrapage, l'exception remonte au fil JavaFX, qui l'avale : le bouton « ne fait
            // rien » et l'utilisateur croit son fichier écrit.
            viewModel.signalerEchecExport(motif(echec));
        }
    }

    /// Ce qu'affiche le tableau quand la nuit n'a produit aucune espèce. Un résultat vide **est** un
    /// résultat : il se dit dans les mots du domaine, avec son identifiant pour que le test le trouve.
    private static Label placeholder() {
        Label vide = new Label("Aucune espèce identifiée pour cette nuit.");
        vide.setId("lblTableauVide");
        vide.setWrapText(true);
        return vide;
    }

    /// Message d'un échec, à défaut de message une mention du type : une chaîne vide dans le bandeau ne
    /// vaudrait pas mieux que le silence qu'on corrige.
    private static String motif(Exception echec) {
        String message = echec.getMessage();
        return message == null || message.isBlank() ? echec.getClass().getSimpleName() : message;
    }

    /// Peuple le sélecteur de milieu, ou **efface toute la colonne d'activité** quand le référentiel
    /// n'est pas exploitable. Masquer plutôt qu'afficher des cellules vides : une colonne blanche se
    /// lirait comme une donnée manquante, alors que le tableau de comptages reste, lui, entier.
    ///
    /// C'est **ici**, et non dans `initialize`, que se décide le libellé du référentiel : les deux cas
    /// s'excluent, et lier la propriété d'abord pour la réécrire ensuite lève `A bound value cannot be
    /// set` : l'écran entier devenait alors inchargeable dès que le référentiel manquait.
    private void configurerMilieux() {
        if (!viewModel.referentielDisponible()) {
            colActivite.setVisible(false);
            colSeuils.setVisible(false);
            cbMilieu.setVisible(false);
            cbMilieu.setManaged(false);
            lblMilieu.setVisible(false);
            lblMilieu.setManaged(false);
            // Les colonnes retirées laisseraient leur largeur derrière elles : le tableau se terminerait
            // par une bande vide et sans en-tête, qui se lit comme un affichage cassé. Les colonnes
            // restantes se répartissent donc l'espace.
            tableSynthese.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            // Mettre en garde contre une lecture qu'on n'affiche pas, et créditer une source qu'on n'a
            // pas pu charger, serait du bruit trompeur. L'obligation de citer naît de l'usage : sans
            // seuils affichés, il n'y a rien à créditer.
            blocAvertissement.setVisible(false);
            blocAvertissement.setManaged(false);
            lblReferentiel.setText("Référentiel d'activité indisponible : le tableau reste exploitable.");
            return;
        }
        // Le référentiel employé, nommé en toutes lettres : une classe dont on ignore la référence est
        // un oracle.
        lblReferentiel
                .textProperty()
                .bind(viewModel.referentielEmployeProperty().map(r -> "Comparé au référentiel : " + r));
        List<String> milieux = new java.util.ArrayList<>();
        milieux.add(SANS_MILIEU);
        milieux.addAll(viewModel.milieuxDisponibles());
        cbMilieu.setItems(FXCollections.observableArrayList(milieux));
        // La liste porte les **clés** du référentiel (`Foret`, `Agricole-Foret`), parce que ce sont elles
        // qui joignent la donnée. Ce que l'utilisateur lit passe par LibellesReferentiel (#3049) : sans
        // ce convertisseur, le sélecteur affichait « Foret » et « Riviere ».
        cbMilieu.setConverter(new javafx.util.StringConverter<String>() {
            @Override
            public String toString(String cle) {
                return cle == null || SANS_MILIEU.equals(cle) ? cle : LibellesReferentiel.milieu(cle);
            }

            @Override
            public String fromString(String affiche) {
                // Le sélecteur n'est pas éditable : la conversion inverse ne sert qu'au contrat de la
                // classe. Rendre l'affichage tel quel est plus sûr qu'une recherche inversée qui
                // échouerait en silence sur une clé inconnue.
                return affiche;
            }
        });
        cbMilieu.getSelectionModel().selectFirst();
        cbMilieu.valueProperty()
                .addListener((observable, avant, choisi) ->
                        viewModel.milieuProperty().set(SANS_MILIEU.equals(choisi) ? null : choisi));
    }

    private void configurerColonnes() {
        colEspece.setCellValueFactory(c -> texte(c.getValue().nomEspece()));
        // Le bouclier des espèces prioritaires, dans la cellule du nom : ici une ligne EST une espèce.
        // Même repère, même infobulle que l'inventaire, la revue, l'activité et la recherche : le produit
        // ne peut pas désigner le même fait sur quatre surfaces et se taire sur la cinquième.
        colEspece.setCellFactory(colonne -> CelluleEspeceAEnjeu.cellule(marqueurEnjeu, LigneSynthese::codeTaxon));
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
        return ligne.seuils().map(SyntheseController::formater).orElse(Formats.VALEUR_ABSENTE);
    }

    private static String formater(SeuilsActivite seuils) {
        return "Q25 = " + seuils.q25() + " · Q75 = " + seuils.q75() + " · Q98 = " + seuils.q98();
    }

    private static String ouTiret(String valeur) {
        return valeur == null || valeur.isBlank() ? Formats.VALEUR_ABSENTE : valeur;
    }

    private static ObservableValue<String> texte(Object valeur) {
        return new ReadOnlyStringWrapper(String.valueOf(valeur));
    }

    /// Ouvre la synthèse d'un passage. Appelé par [NavigationSynthese] après le chargement du FXML.
    public void ouvrirSur(ContextePassage passage) {
        this.contexte = passage;
        viewModel.charger(passage.idPassage(), passage.site().numeroCarre());
    }

    /// Relit la synthèse au retour sur cet écran (#3964).
    ///
    /// ## Pourquoi ce contrat manquait
    ///
    /// La synthèse agrège des **observations**, et cet écran ouvre lui-même la fiche du site et celle du
    /// passage - d'où la validation est atteignable. Le chemin
    /// `Synthèse → Passage → Validation → retour → retour` corrige des observations et revient sur des
    /// chiffres calculés **avant** la correction, sans que rien ne le dise.
    ///
    /// Et `SuitLaRevision` ne conviendrait pas : la validation écrit des `update`, que l'ADR 3840
    /// exclut délibérément du signal - « l'élargir aux `update` ferait relire cinq écrans pour un
    /// changement qu'aucun compte ne reflète ». C'est bien le **retour** qui porte ce cas-là.
    @Override
    public void rafraichirAuRetour() {
        if (contexte != null) {
            viewModel.charger(contexte.idPassage(), contexte.site().numeroCarre());
        }
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
